package com.becamce.app.core.model

import java.time.Instant

enum class ChatKind { DIRECT, GROUP, SECRET }

enum class MemberRole { OWNER, ADMIN, MEMBER }

data class UserProfile(
    val id: String,
    val username: String?,
    val displayName: String,
    val avatarPath: String? = null,
    val bio: String = "",
    val isOnline: Boolean = false
)

data class ChatSummary(
    val id: String,
    val kind: ChatKind,
    val title: String?,
    val members: List<String>,
    val lastMessage: Message?,
    val unreadCount: Int = 0,
    val updatedAt: Instant
)

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val body: String?,
    val mediaPath: String? = null,
    val mediaMime: String? = null,
    val mediaFileName: String? = null,
    val replyTo: String? = null,
    val editedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val createdAt: Instant
)

data class ChannelSummary(
    val id: String,
    val title: String,
    val username: String?,
    val description: String,
    val isPublic: Boolean,
    val subscriberCount: Int = 0
)
