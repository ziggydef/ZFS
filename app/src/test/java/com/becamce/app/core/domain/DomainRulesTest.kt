package com.becamce.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputValidationTest {
    @Test fun acceptsValidEmail() {
        assertNull(InputValidation.email("person@example.com"))
    }

    @Test fun rejectsMalformedEmail() {
        assertEquals("Проверьте адрес электронной почты", InputValidation.email("not-an-email"))
    }

    @Test fun validatesInternationalPhoneNumbers() {
        assertNull(InputValidation.phone("+4915112345678"))
        assertEquals("Формат номера: +4915112345678", InputValidation.phone("0151 12345678"))
        assertEquals("Формат номера: +4915112345678", InputValidation.phone("+012345678"))
    }

    @Test fun validatesSmsCodeLengthAndDigits() {
        assertNull(InputValidation.smsCode("123456"))
        assertEquals("Введите 6-значный код из SMS", InputValidation.smsCode("12345"))
        assertEquals("Введите 6-значный код из SMS", InputValidation.smsCode("12a456"))
    }

    @Test fun requiresLongMixedPassword() {
        assertEquals("Пароль должен содержать не менее 10 символов", InputValidation.password("abc123"))
        assertNull(InputValidation.password("becamceSecure2026"))
    }

    @Test fun normalizesUsernameForLookup() {
        assertEquals("ziggy_2026", MessageRules.normalizeUsername(" @Ziggy_2026 "))
    }

    @Test fun validatesUsernameBoundsAndCharacters() {
        assertNull(InputValidation.username("ziggy_2026"))
        assertEquals("Ник: 4–24 символа, латиница, цифры и _", InputValidation.username("ab"))
        assertEquals("Ник: 4–24 символа, латиница, цифры и _", InputValidation.username("bad name"))
    }

    @Test fun validatesDisplayName() {
        assertNull(InputValidation.displayName("Alex"))
        assertEquals("Имя должно содержать от 2 до 40 символов", InputValidation.displayName(" "))
    }
}

class MessageRulesTest {
    @Test fun rejectsBlankAndOversizedMessages() {
        assertEquals("Сообщение не может быть пустым", MessageRules.validateText("  "))
        assertEquals("Сообщение слишком длинное", MessageRules.validateText("x".repeat(8_001)))
        assertNull(MessageRules.validateText("Привет!"))
    }
}
