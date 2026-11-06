package com.becamce.app.core.data

import android.content.Context
import com.becamce.app.BuildConfig
import com.becamce.app.core.domain.ProfileRepository
import com.becamce.app.core.domain.AuthRepository
import com.becamce.app.core.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SupabaseProfileRepository(context: Context, private val authRepository: AuthRepository) : ProfileRepository {
    private val config = BackendConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    private val sessions = SecureSessionStore(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    override suspend fun getMyProfile(): Result<UserProfile> = result {
        val userId = requireUserId()
        val rows = JSONArray(request("profiles?select=id,username,display_name,avatar_path,bio&id=eq.$userId", "GET"))
        if (rows.length() == 0) throw IOException("Профиль не найден. Повторите вход позже.")
        parse(rows.getJSONObject(0))
    }

    override suspend fun updateMyProfile(displayName: String, username: String?, bio: String): Result<UserProfile> = result {
        require(displayName.trim().length in 2..40) { "Имя должно содержать от 2 до 40 символов" }
        require(bio.length <= 160) { "Описание не должно превышать 160 символов" }
        if (username != null) require(Regex("^[a-zA-Z0-9_]{4,24}$").matches(username)) { "Ник: 4–24 символа, латиница, цифры и _" }
        val userId = requireUserId()
        val payload = JSONObject()
            .put("display_name", displayName.trim())
            .put("username", username?.lowercase()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("bio", bio.trim())
            .put("updated_at", java.time.Instant.now().toString())
        val rows = JSONArray(request("profiles?id=eq.$userId&select=id,username,display_name,avatar_path,bio", "PATCH", payload))
        if (rows.length() == 0) throw IOException("Не удалось обновить профиль")
        parse(rows.getJSONObject(0))
    }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = result {
        require(Regex("^[a-zA-Z0-9_]{4,24}$").matches(username)) { "Ник: 4–24 символа, латиница, цифры и _" }
        val encoded = URLEncoder.encode(username.lowercase(), Charsets.UTF_8.name())
        val rows = JSONArray(request("profiles?select=id&username=eq.$encoded", "GET"))
        rows.length() == 0
    }

    override suspend fun searchUsers(query: String): Result<List<UserProfile>> = result {
        val term = query.trim().removePrefix("@")
        require(term.length in 2..32) { "Введите минимум 2 символа для поиска" }
        require(term.matches(Regex("^[\\p{L}\\p{N}_ .-]{2,32}$"))) { "Используйте буквы, цифры, пробел, точку или _" }
        val encoded = URLEncoder.encode("*$term*", Charsets.UTF_8.name())
        val rows = JSONArray(request(
            "profiles?select=id,username,display_name,avatar_path,bio&or=(username.ilike.$encoded,display_name.ilike.$encoded)&limit=30",
            "GET"
        ))
        buildList { for (i in 0 until rows.length()) add(parse(rows.getJSONObject(i))) }
            .filter { it.id != sessions.read()?.userId }
    }

    private fun parse(row: JSONObject) = UserProfile(
        id = row.getString("id"),
        username = row.optString("username").takeIf { it.isNotBlank() && it != "null" },
        displayName = row.optString("display_name"),
        avatarPath = row.optString("avatar_path").takeIf { it.isNotBlank() && it != "null" },
        bio = row.optString("bio")
    )

    private fun requireUserId() = sessions.read()?.userId ?: throw IOException("Войдите в аккаунт, чтобы продолжить.")

    private suspend fun request(path: String, method: String, payload: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            check(config.isConfigured) { "Supabase пока не подключён. Нужны URL и публичный ключ проекта." }
            authRepository.refreshSessionIfNeeded().getOrElse { throw it }
            val session = sessions.read() ?: throw IOException("Войдите в аккаунт, чтобы продолжить.")
            val builder = Request.Builder().url(config.projectUrl.trimEnd('/') + "/rest/v1/" + path)
                .header("apikey", config.publishableKey)
                .header("Authorization", "Bearer " + session.accessToken)
                .header("Accept", "application/json")
                .header("Prefer", "return=representation")
            when (method) {
                "GET" -> builder.get()
                "PATCH" -> builder.patch((payload ?: JSONObject()).toString().toRequestBody(JSON))
                else -> throw IllegalArgumentException("Unsupported HTTP method")
            }
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
                    throw IOException(detail.ifBlank { "Ошибка сервера (" + response.code + ")" })
                }
                body
            }
        }

    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}
