package com.becamce.app.core.domain

object MessageRules {
    const val MAX_TEXT_LENGTH = 8_000

    fun validateText(body: String): String? = when {
        body.isBlank() -> "Сообщение не может быть пустым"
        body.length > MAX_TEXT_LENGTH -> "Сообщение слишком длинное"
        else -> null
    }

    fun normalizeUsername(username: String): String =
        username.trim().removePrefix("@").lowercase()
}
