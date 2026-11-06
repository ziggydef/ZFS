package com.becamce.app.core.data

import android.content.Context
import com.becamce.app.BuildConfig
import com.becamce.app.core.domain.AuthRepository
import com.becamce.app.core.domain.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseAuthRepository(context: Context) : AuthRepository {
    private val config = BackendConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    private val sessionStore = SecureSessionStore(context.applicationContext)
    private val refreshMutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val mutableSession = MutableStateFlow(sessionStore.read()?.toAuthSession())
    override val session = mutableSession.asStateFlow()

    override suspend fun signUp(email: String, password: String, displayName: String): Result<Unit> =
        requestResult {
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("data", JSONObject().put("display_name", displayName.trim()))
            execute("/auth/v1/signup", body, bearer = null)
            Unit
        }

    override suspend fun signIn(email: String, password: String): Result<AuthSession> =
        requestResult {
            val body = JSONObject().put("email", email.trim()).put("password", password)
            val response = execute("/auth/v1/token?grant_type=password", body, bearer = null)
            saveSessionFromAuthResponse(response)
        }

    override suspend fun requestPhoneCode(phone: String, displayName: String): Result<Unit> = requestResult {
        val body = JSONObject()
            .put("phone", phone.trim())
            .put("create_user", true)
            .put("data", JSONObject().put("display_name", displayName.trim()))
        execute("/auth/v1/otp", body, bearer = null)
        Unit
    }

    override suspend fun verifyPhoneCode(phone: String, code: String): Result<AuthSession> = requestResult {
        val response = execute(
            "/auth/v1/verify",
            JSONObject().put("phone", phone.trim()).put("token", code.trim()).put("type", "sms"),
            bearer = null
        )
        saveSessionFromAuthResponse(response)
    }

    override suspend fun refreshSessionIfNeeded(): Result<AuthSession?> = refreshMutex.withLock { requestResult {
        val saved = sessionStore.read() ?: return@requestResult null
        val now = System.currentTimeMillis() / 1000L
        if (saved.expiresAt > now + 90L) return@requestResult saved.toAuthSession()

        try {
            val response = execute(
                "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", saved.refreshToken),
                bearer = null
            )
            val accessToken = response.optString("access_token")
            val refreshToken = response.optString("refresh_token").ifBlank { saved.refreshToken }
            if (accessToken.isBlank()) error("Не удалось обновить сессию")
            val expiresAt = response.optLong("expires_at").takeIf { it > 0 }
                ?: (now + response.optLong("expires_in", 3600L))
            val userId = response.optJSONObject("user")?.optString("id").orEmpty().ifBlank { saved.userId }
            val renewed = StoredSession(userId, accessToken, refreshToken, expiresAt)
            sessionStore.write(renewed)
            renewed.toAuthSession().also { mutableSession.value = it }
        } catch (error: Exception) {
            // Keep the encrypted refresh token when the device is temporarily offline.
            // Only discard credentials when Supabase explicitly rejects the refresh token.
            val message = error.message.orEmpty()
            val refreshRejected =
                message.contains("(400)") ||
                message.contains("(401)") ||
                message.contains("invalid_grant", ignoreCase = true) ||
                message.contains("invalid refresh token", ignoreCase = true) ||
                message.contains("refresh token not found", ignoreCase = true) ||
                message.contains("already used", ignoreCase = true)
            if (refreshRejected) {
                sessionStore.clear()
                mutableSession.value = null
            }
            throw error
        }
    } }

    override suspend fun signOut(): Result<Unit> = refreshMutex.withLock { requestResult {
        val saved = sessionStore.read()
        try {
            if (saved != null) execute("/auth/v1/logout", JSONObject(), saved.accessToken)
        } finally {
            sessionStore.clear()
            mutableSession.value = null
        }
        Unit
    } }

    override suspend fun requestPasswordReset(email: String): Result<Unit> = requestResult {
        execute("/auth/v1/recover?redirect_to=becamce%3A%2F%2Fauth%2Fcallback", JSONObject().put("email", email.trim()), bearer = null)
        Unit
    }

    override suspend fun completePasswordRecovery(accessToken: String, newPassword: String): Result<Unit> = requestResult {
        require(accessToken.isNotBlank()) { "Ссылка восстановления недействительна или устарела" }
        require(newPassword.length >= 10 && newPassword.any(Char::isLetter) && newPassword.any(Char::isDigit)) {
            "Пароль должен содержать не менее 10 символов, буквы и цифры"
        }
        withContext(Dispatchers.IO) {
            check(config.isConfigured) { "Supabase пока не подключён." }
            val request = Request.Builder()
                .url(config.projectUrl.trimEnd('/') + "/auth/v1/user")
                .header("apikey", config.publishableKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .put(JSONObject().put("password", newPassword).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        val json = JSONObject(text)
                        json.optString("msg").ifBlank { json.optString("message").ifBlank { json.optString("error_description") } }
                    }.getOrNull().orEmpty()
                    throw IOException(message.ifBlank { "Не удалось изменить пароль (" + response.code + ")" })
                }
            }
        }
        Unit
    }

    private fun saveSessionFromAuthResponse(response: JSONObject): AuthSession {
        val user = response.optJSONObject("user") ?: error("Сервер не вернул профиль пользователя")
        val accessToken = response.optString("access_token")
        val refreshToken = response.optString("refresh_token")
        if (accessToken.isBlank() || refreshToken.isBlank()) error("Сервер не вернул токены сессии")
        val expiresAt = response.optLong("expires_at").takeIf { it > 0 }
            ?: (System.currentTimeMillis() / 1000L + response.optLong("expires_in", 3600L))
        val saved = StoredSession(user.getString("id"), accessToken, refreshToken, expiresAt)
        sessionStore.write(saved)
        return saved.toAuthSession().also { mutableSession.value = it }
    }

    private suspend fun <T> requestResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private suspend fun execute(path: String, payload: JSONObject, bearer: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            check(config.isConfigured) {
                "Supabase пока не подключён. Для проекта нужны URL и публичный ключ."
            }
            val requestBuilder = Request.Builder()
                .url(config.projectUrl.trimEnd('/') + path)
                .header("apikey", config.publishableKey)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (!bearer.isNullOrBlank()) requestBuilder.header("Authorization", "Bearer " + bearer)
            client.newCall(requestBuilder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        val json = JSONObject(text)
                        json.optString("msg").ifBlank {
                            json.optString("message").ifBlank {
                                json.optString("error_description").ifBlank { json.optString("error") }
                            }
                        }
                    }.getOrNull().orEmpty()
                    throw IOException(message.ifBlank { "Ошибка сервера (" + response.code + ")" })
                }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            }
        }

    private fun StoredSession.toAuthSession() =
        AuthSession(userId = userId, accessTokenExpiresAtEpochSeconds = expiresAt)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
