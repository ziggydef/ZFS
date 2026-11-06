package com.becamce.app.core.domain

object InputValidation {
    private val emailPattern = Regex("^[A-Za-z0-9.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$")
    private val phonePattern = Regex("^\\+[1-9][0-9]{7,14}$")
    private val usernamePattern = Regex("^[a-zA-Z0-9_]{4,24}$")

    fun email(value: String): String? = when {
        value.isBlank() -> "Введите электронную почту"
        !emailPattern.matches(value.trim()) -> "Проверьте адрес электронной почты"
        else -> null
    }

    fun phone(value: String): String? = when {
        value.isBlank() -> "Введите номер телефона в международном формате"
        !phonePattern.matches(value.trim()) -> "Формат номера: +4915112345678"
        else -> null
    }

    fun smsCode(value: String): String? = when {
        !value.matches(Regex("^[0-9]{6}$")) -> "Введите 6-значный код из SMS"
        else -> null
    }

    fun password(value: String): String? = when {
        value.length < 10 -> "Пароль должен содержать не менее 10 символов"
        value.none(Char::isLetter) || value.none(Char::isDigit) -> "Добавьте в пароль буквы и цифры"
        else -> null
    }

    fun displayName(value: String): String? = when {
        value.trim().length !in 2..40 -> "Имя должно содержать от 2 до 40 символов"
        else -> null
    }

    fun username(value: String): String? = when {
        !usernamePattern.matches(value) -> "Ник: 4–24 символа, латиница, цифры и _"
        else -> null
    }
}
