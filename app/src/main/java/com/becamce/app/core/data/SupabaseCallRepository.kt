package com.becamce.app.core.data

import android.content.Context
import com.becamce.app.BuildConfig
import com.becamce.app.core.domain.AuthRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class CallInfo(
    val id: String,
    val chatId: String,
    val callerId: String,
    val calleeId: String,
    val status: String,
    val offerSdp: String,
    val answerSdp: String?
)

internal data class RemoteIceCandidate(
    val id: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String
)

internal class SupabaseCallRepository(
    context: Context,
    private val authRepository: AuthRepository
) {
    private val config = BackendConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    private val sessions = SecureSessionStore(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    suspend fun peerInChat(chatId: String, currentUserId: String): Result<String> = result {
        val rows = JSONArray(api(
            "chat_members?select=user_id&chat_id=eq.$chatId&user_id=neq.$currentUserId",
            "GET"
        ))
        require(rows.length() == 1) { "Звонки пока доступны только в личных чатах" }
        rows.getJSONObject(0).getString("user_id")
    }

    suspend fun createCall(chatId: String, calleeId: String, offerSdp: String): Result<String> = result {
        val payload = JSONObject()
            .put("chat_id", chatId)
            .put("caller_id", requireUserId())
            .put("callee_id", calleeId)
            .put("status", "ringing")
            .put("offer_sdp", offerSdp)
        val rows = JSONArray(api("call_sessions?select=id", "POST", payload, "return=representation"))
        require(rows.length() == 1) { "Сервер не создал вызов" }
        rows.getJSONObject(0).getString("id")
    }

    suspend fun getCall(callId: String): Result<CallInfo> = result {
        parseCall(JSONArray(api("call_sessions?select=id,chat_id,caller_id,callee_id,status,offer_sdp,answer_sdp&id=eq.$callId&limit=1", "GET"))
            .getJSONObject(0))
    }

    suspend fun acceptCall(callId: String, answerSdp: String): Result<Unit> = result {
        api("call_sessions?id=eq.$callId", "PATCH",
            JSONObject().put("status", "accepted").put("answer_sdp", answerSdp))
        Unit
    }

    suspend fun setStatus(callId: String, status: String): Result<Unit> = result {
        require(status in setOf("rejected", "ended", "missed"))
        api("call_sessions?id=eq.$callId", "PATCH", JSONObject().put("status", status))
        Unit
    }

    suspend fun sendCandidate(callId: String, sdpMid: String?, sdpMLineIndex: Int, candidate: String): Result<Unit> = result {
        val payload = JSONObject()
            .put("call_id", callId)
            .put("sender_id", requireUserId())
            .put("candidate", JSONObject()
                .put("sdpMid", sdpMid ?: JSONObject.NULL)
                .put("sdpMLineIndex", sdpMLineIndex)
                .put("candidate", candidate))
        api("call_ice_candidates", "POST", payload, "return=minimal")
        Unit
    }

    suspend fun remoteCandidates(callId: String, knownIds: Set<String>): Result<List<RemoteIceCandidate>> = result {
        val userId = requireUserId()
        val rows = JSONArray(api(
            "call_ice_candidates?select=id,sender_id,candidate&call_id=eq.$callId&sender_id=neq.$userId&order=created_at.asc",
            "GET"
        ))
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val id = row.getString("id")
                if (id in knownIds) continue
                val candidate = row.getJSONObject("candidate")
                add(RemoteIceCandidate(
                    id = id,
                    sdpMid = candidate.optString("sdpMid").takeIf { it.isNotBlank() && it != "null" },
                    sdpMLineIndex = candidate.optInt("sdpMLineIndex"),
                    candidate = candidate.getString("candidate")
                ))
            }
        }
    }

    fun observeIncomingCalls(): Flow<List<CallInfo>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(loadIncomingCalls())
            delay(1200L)
        }
    }

    private suspend fun loadIncomingCalls(): List<CallInfo> {
        val userId = requireUserId()
        val rows = JSONArray(api(
            "call_sessions?select=id,chat_id,caller_id,callee_id,status,offer_sdp,answer_sdp&callee_id=eq.$userId&status=eq.ringing&order=created_at.desc&limit=10",
            "GET"
        ))
        return buildList { for (i in 0 until rows.length()) add(parseCall(rows.getJSONObject(i))) }
    }

    private fun parseCall(row: JSONObject) = CallInfo(
        id = row.getString("id"),
        chatId = row.getString("chat_id"),
        callerId = row.getString("caller_id"),
        calleeId = row.getString("callee_id"),
        status = row.getString("status"),
        offerSdp = row.getString("offer_sdp"),
        answerSdp = row.optString("answer_sdp").takeIf { it.isNotBlank() && it != "null" }
    )

    private suspend fun api(
        path: String,
        method: String,
        payload: JSONObject? = null,
        prefer: String? = null
    ): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        check(config.isConfigured) { "Supabase пока не подключён." }
        authRepository.refreshSessionIfNeeded().getOrElse { throw it }
        val session = sessions.read() ?: throw IOException("Сессия завершена. Войдите снова.")
        val url = config.projectUrl.trimEnd('/') + "/rest/v1/" + path
        val builder = Request.Builder().url(url)
            .header("apikey", config.publishableKey)
            .header("Authorization", "Bearer " + session.accessToken)
            .header("Accept", "application/json")
        if (prefer != null) builder.header("Prefer", prefer)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((payload?.toString() ?: "{}").toRequestBody(JSON))
            "PATCH" -> builder.patch((payload?.toString() ?: "{}").toRequestBody(JSON))
            else -> error("Unsupported request")
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

    private fun requireUserId(): String = sessions.read()?.userId
        ?: throw IOException("Сессия завершена. Войдите снова.")

    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        try { Result.success(block()) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
