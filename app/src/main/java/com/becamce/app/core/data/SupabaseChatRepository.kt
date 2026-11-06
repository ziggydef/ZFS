package com.becamce.app.core.data

import android.content.Context
import com.becamce.app.BuildConfig
import com.becamce.app.core.domain.ChatRepository
import com.becamce.app.core.model.ChatKind
import com.becamce.app.core.model.ChatSummary
import com.becamce.app.core.model.Message
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
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Supabase PostgREST implementation.
 * Polling is used as a dependable first transport; Realtime websocket delivery
 * will replace polling in a later milestone without changing the domain contract.
 */
class SupabaseChatRepository(context: Context) : ChatRepository {
    private val config = BackendConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    private val sessions = SecureSessionStore(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    override fun observeChats(): Flow<List<ChatSummary>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(fetchChats())
            delay(CHAT_REFRESH_MS)
        }
    }

    override fun observeMessages(chatId: String): Flow<List<Message>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(fetchMessages(chatId))
            delay(MESSAGE_REFRESH_MS)
        }
    }

    override suspend fun createChat(kind: ChatKind, title: String?, memberIds: List<String>): Result<String> =
        result {
            require(kind != ChatKind.SECRET) { "Секретные чаты пока не поддерживаются" }
            require(memberIds.isNotEmpty()) { "Для личного или группового чата нужен хотя бы один собеседник" }
            val payload = JSONObject()
                .put("p_kind", kind.name.lowercase())
                .put("p_title", title ?: JSONObject.NULL)
                .put("p_member_ids", JSONArray(memberIds.distinct()))
            val response = request("rpc/create_chat", "POST", payload)
            response.trim('"').takeIf { it.isNotBlank() }
                ?: throw IOException("Сервер не вернул идентификатор чата")
        }

    override suspend fun sendMessage(chatId: String, body: String, replyTo: String?): Result<Message> =
        result {
            require(body.isNotBlank() && body.length <= 8000) { "Сообщение должно содержать от 1 до 8000 символов" }
            val payload = JSONObject()
                .put("chat_id", chatId)
                .put("sender_id", requireUserId())
                .put("body", body.trim())
            if (!replyTo.isNullOrBlank()) payload.put("reply_to", replyTo)
            val response = request(
                "messages?select=id,chat_id,sender_id,body,media_path,media_mime,media_file_name,reply_to,edited_at,created_at",
                "POST", payload, prefer = "return=representation"
            )
            parseMessage(JSONArray(response).getJSONObject(0))
        }

    override suspend fun uploadAttachment(
        chatId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        replyTo: String?
    ): Result<Message> = result {
        requireValidUuid(chatId)
        require(bytes.isNotEmpty() && bytes.size <= MAX_ATTACHMENT_BYTES) { "Файл пустой или превышает 20 МБ" }
        require(fileName.isNotBlank() && fileName.length <= 180) { "Некорректное имя файла" }
        require(mimeType in ALLOWED_ATTACHMENT_TYPES) { "Этот тип файла не поддерживается" }
        val userId = requireUserId()
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "attachment" }
        val objectPath = "$userId/$chatId/${java.util.UUID.randomUUID()}-$safeName"
        val encodedPath = objectPath.split("/").joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val session = sessions.read() ?: throw IOException("Войдите в аккаунт, чтобы продолжить.")
            val url = config.projectUrl.trimEnd('/') + "/storage/v1/object/chat-attachments/" + encodedPath
            val body = bytes.toRequestBody(mimeType.toMediaType())
            val request = Request.Builder().url(url)
                .header("apikey", config.publishableKey)
                .header("Authorization", "Bearer " + session.accessToken)
                .header("x-upsert", "false")
                .post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string().orEmpty()
                    throw IOException(detail.ifBlank { "Не удалось загрузить файл (${response.code})" })
                }
            }
        }
        val payload = JSONObject()
            .put("chat_id", chatId)
            .put("sender_id", userId)
            .put("media_path", objectPath)
            .put("media_mime", mimeType)
            .put("media_file_name", fileName.take(180))
            .put("reply_to", replyTo ?: JSONObject.NULL)
        val raw = request(
            "messages?select=id,chat_id,sender_id,body,media_path,media_mime,media_file_name,reply_to,edited_at,created_at",
            "POST", payload, prefer = "return=representation"
        )
        parseMessage(JSONArray(raw).getJSONObject(0))
    }

    override suspend fun getAttachmentUrl(storagePath: String): Result<String> = result {
        require(storagePath.matches(Regex("^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/[A-Za-z0-9._-]{1,180}$"))) {
            "Некорректный путь вложения"
        }
        val encodedPath = storagePath.split("/").joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            check(config.isConfigured) { "Supabase пока не подключён." }
            val session = sessions.read() ?: throw IOException("Войдите в аккаунт, чтобы продолжить.")
            val url = config.projectUrl.trimEnd('/') + "/storage/v1/object/sign/chat-attachments/" +
                encodedPath
            val payload = JSONObject().put("expiresIn", 300)
                .toString().toRequestBody(JSON)
            val request = Request.Builder().url(url)
                .header("apikey", config.publishableKey)
                .header("Authorization", "Bearer " + session.accessToken)
                .post(payload).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException(text.ifBlank { "Не удалось открыть вложение" })
                val signed = JSONObject(text).optString("signedURL")
                require(signed.isNotBlank()) { "Сервер не вернул ссылку на файл" }
                if (signed.startsWith("http://") || signed.startsWith("https://")) signed
                else config.projectUrl.trimEnd('/') + "/storage/v1" + signed
            }
        }
    }

    override suspend fun editMessage(messageId: String, newBody: String): Result<Unit> = result {
        require(newBody.isNotBlank() && newBody.length <= 8000) { "Сообщение должно содержать от 1 до 8000 символов" }
        request("rpc/edit_message", "POST", JSONObject().put("p_message_id", messageId).put("p_body", newBody.trim()))
        Unit
    }

    override suspend fun deleteMessage(messageId: String): Result<Unit> = result {
        request("rpc/delete_message", "POST", JSONObject().put("p_message_id", messageId))
        Unit
    }

    override suspend fun markRead(chatId: String): Result<Unit> = result {
        request("rpc/mark_chat_read", "POST", JSONObject().put("p_chat_id", chatId))
        Unit
    }

    override suspend fun addGroupMember(chatId: String, userId: String): Result<Unit> = result {
        requireValidUuid(chatId)
        requireValidUuid(userId)
        request(
            "rpc/manage_group_member",
            "POST",
            JSONObject().put("p_chat_id", chatId).put("p_user_id", userId).put("p_action", "add")
        )
        Unit
    }

    override suspend fun removeGroupMember(chatId: String, userId: String): Result<Unit> = result {
        requireValidUuid(chatId)
        requireValidUuid(userId)
        request(
            "rpc/manage_group_member",
            "POST",
            JSONObject().put("p_chat_id", chatId).put("p_user_id", userId).put("p_action", "remove")
        )
        Unit
    }

    private fun requireValidUuid(value: String) {
        require(value.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))) {
            "Некорректный идентификатор"
        }
    }

    private suspend fun fetchChats(): List<ChatSummary> {
        val userId = requireUserId()
        val raw = request(
            "chats?select=id,kind,title,updated_at,chat_members!inner(user_id),messages(id,chat_id,sender_id,body,media_path,media_mime,media_file_name,reply_to,edited_at,created_at)&chat_members.user_id=eq.$userId&messages.order=created_at.desc&messages.limit=1&order=updated_at.desc",
            "GET"
        )
        val rows = JSONArray(raw)
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val memberRows = row.optJSONArray("chat_members") ?: JSONArray()
                val members = buildList {
                    for (j in 0 until memberRows.length()) add(memberRows.getJSONObject(j).optString("user_id"))
                }
                val messageRows = row.optJSONArray("messages") ?: JSONArray()
                val lastMessage = if (messageRows.length() > 0) parseMessage(messageRows.getJSONObject(0)) else null
                add(
                    ChatSummary(
                        id = row.getString("id"),
                        kind = ChatKind.valueOf(row.getString("kind").uppercase()),
                        title = row.optString("title").takeIf { it.isNotBlank() && it != "null" },
                        members = members,
                        lastMessage = lastMessage,
                        updatedAt = instant(row.optString("updated_at"))
                    )
                )
            }
        }
    }

    private suspend fun fetchMessages(chatId: String): List<Message> {
        require(chatId.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "Некорректный идентификатор чата" }
        val raw = request(
            "messages?select=id,chat_id,sender_id,body,media_path,media_mime,media_file_name,reply_to,edited_at,expires_at,created_at&chat_id=eq.$chatId&order=created_at.asc&limit=100",
            "GET"
        )
        val rows = JSONArray(raw)
        return buildList { for (i in 0 until rows.length()) add(parseMessage(rows.getJSONObject(i))) }
    }

    private fun parseMessage(row: JSONObject) = Message(
        id = row.getString("id"),
        chatId = row.getString("chat_id"),
        senderId = row.getString("sender_id"),
        body = row.optString("body").takeIf { it.isNotBlank() && it != "null" },
        mediaPath = row.optString("media_path").takeIf { it.isNotBlank() && it != "null" },
        mediaMime = row.optString("media_mime").takeIf { it.isNotBlank() && it != "null" },
        mediaFileName = row.optString("media_file_name").takeIf { it.isNotBlank() && it != "null" },
        replyTo = row.optString("reply_to").takeIf { it.isNotBlank() && it != "null" },
        editedAt = row.optString("edited_at").takeIf { it.isNotBlank() && it != "null" }?.let(::instant),
        expiresAt = row.optString("expires_at").takeIf { it.isNotBlank() && it != "null" }?.let(::instant),
        createdAt = instant(row.getString("created_at"))
    )

    private fun instant(value: String): Instant = runCatching { Instant.parse(value) }.getOrElse { Instant.EPOCH }

    private fun requireUserId(): String = sessions.read()?.userId
        ?: throw IOException("Сессия завершена. Войдите снова.")

    private suspend fun request(
        path: String,
        method: String,
        payload: JSONObject? = null,
        prefer: String? = null
    ): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        check(config.isConfigured) { "Supabase пока не подключён. Нужны URL и публичный ключ проекта." }
        val session = sessions.read() ?: throw IOException("Войдите в аккаунт, чтобы продолжить.")
        val url = config.projectUrl.trimEnd('/') + "/rest/v1/" + path
        val builder = Request.Builder().url(url)
            .header("apikey", config.publishableKey)
            .header("Authorization", "Bearer " + session.accessToken)
            .header("Accept", "application/json")
        if (prefer != null) builder.header("Prefer", prefer)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((payload?.toString() ?: "{}").toRequestBody(JSON))
            else -> throw IllegalArgumentException("Unsupported HTTP method")
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    val json = JSONObject(text)
                    json.optString("message").ifBlank { json.optString("details").ifBlank { json.optString("hint") } }
                }.getOrNull().orEmpty()
                throw IOException(detail.ifBlank { "Ошибка сервера (" + response.code + ")" })
            }
            text
        }
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        try { Result.success(block()) } catch (error: Exception) { Result.failure(error) }

    private companion object {
        const val CHAT_REFRESH_MS = 8000L
        const val MESSAGE_REFRESH_MS = 2500L
        const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
        val ALLOWED_ATTACHMENT_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "application/pdf", "text/plain", "application/zip",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
