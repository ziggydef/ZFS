package com.becamce.app.core.domain

import com.becamce.app.core.model.ChatKind
import com.becamce.app.core.model.ChatSummary
import com.becamce.app.core.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract. Implementations must enforce membership on the server;
 * UI visibility is never an authorization boundary.
 */
interface ChatRepository {
    fun observeChats(): Flow<List<ChatSummary>>
    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun createChat(kind: ChatKind, title: String?, memberIds: List<String>): Result<String>
    suspend fun sendMessage(chatId: String, body: String, replyTo: String? = null): Result<Message>
    suspend fun uploadAttachment(chatId: String, fileName: String, mimeType: String, bytes: ByteArray, replyTo: String? = null): Result<Message>
    suspend fun getAttachmentUrl(storagePath: String): Result<String>
    suspend fun editMessage(messageId: String, newBody: String): Result<Unit>
    suspend fun deleteMessage(messageId: String): Result<Unit>
    suspend fun markRead(chatId: String): Result<Unit>
    suspend fun addGroupMember(chatId: String, userId: String): Result<Unit>
    suspend fun removeGroupMember(chatId: String, userId: String): Result<Unit>
}
