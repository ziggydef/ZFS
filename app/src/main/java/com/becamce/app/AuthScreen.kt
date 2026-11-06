package com.becamce.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.becamce.app.core.domain.AuthRepository
import com.becamce.app.core.domain.InputValidation
import kotlinx.coroutines.launch

private val AuthNight = Color(0xFF09090E)
private val AuthPanel = Color(0xFF171722)
private val AuthViolet = Color(0xFF9B78F5)
private val AuthMuted = Color(0xFF9291A4)
private val AuthWhite = Color(0xFFF4F1FC)

@Composable
fun AuthScreen(repository: AuthRepository) {
    var isSignUp by remember { mutableStateOf(false) }
    var phoneMode by remember { mutableStateOf(false) }
    var phoneCodeSent by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF211833), AuthNight, AuthNight))
        ).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().background(AuthPanel.copy(alpha = 0.82f), RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text("becamce", color = AuthWhite, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    phoneMode -> if (phoneCodeSent) "ПОДТВЕРДИ НОМЕР" else "ВХОД И РЕГИСТРАЦИЯ ПО SMS"
                    isSignUp -> "СОЗДАЙ СВОЙ АККАУНТ"
                    else -> "РАДЫ ТЕБЯ ВИДЕТЬ"
                },
                color = AuthMuted, fontSize = 10.sp, letterSpacing = 1.7.sp
            )

            if (phoneMode) {
                Text(
                    "Бесплатный тест: добавь номер и фиксированный код в Supabase → Authentication → Providers → Phone → Test phone numbers. SMS отправляться не будет.",
                    color = AuthMuted, fontSize = 12.sp
                )
                if (!phoneCodeSent) {
                    OutlinedTextField(
                        value = displayName, onValueChange = { displayName = it; error = null },
                        label = { Text("Твоё имя") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                    )
                }
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it; error = null },
                    label = { Text("Номер телефона (+4915112345678)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                )
                if (phoneCodeSent) {
                    OutlinedTextField(
                        value = smsCode,
                        onValueChange = { smsCode = it.filter(Char::isDigit).take(6); error = null },
                        label = { Text("Код из SMS") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                    )
                }
            } else {
                if (isSignUp) {
                    OutlinedTextField(
                        value = displayName, onValueChange = { displayName = it; error = null },
                        label = { Text("Как тебя зовут") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                    )
                }
                OutlinedTextField(
                    value = email, onValueChange = { email = it; error = null },
                    label = { Text("Электронная почта") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = null },
                    label = { Text("Пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                )
            }

            error?.let { Text(it, color = Color(0xFFFF8B9A), fontSize = 12.sp) }
            notice?.let { Text(it, color = Color(0xFF9ADDBA), fontSize = 12.sp) }

            Button(
                onClick = {
                    error = null
                    notice = null
                    if (phoneMode) {
                        error = if (phoneCodeSent) {
                            InputValidation.phone(phone) ?: InputValidation.smsCode(smsCode)
                        } else {
                            InputValidation.phone(phone) ?: InputValidation.displayName(displayName)
                        }
                        if (error == null) scope.launch {
                            busy = true
                            val result = if (phoneCodeSent) {
                                repository.verifyPhoneCode(phone, smsCode).map { Unit }
                            } else {
                                repository.requestPhoneCode(phone, displayName)
                            }
                            busy = false
                            result.onSuccess {
                                if (phoneCodeSent) {
                                    notice = "Номер подтверждён. Добро пожаловать в becamce!"
                                } else {
                                    phoneCodeSent = true
                                    notice = "Код отправлен по SMS. Введи его ниже."
                                }
                            }.onFailure { error = it.message ?: "Не удалось выполнить запрос" }
                        }
                    } else {
                        val emailError = InputValidation.email(email)
                        val passwordError = InputValidation.password(password)
                        val nameError = if (isSignUp) InputValidation.displayName(displayName) else null
                        error = emailError ?: passwordError ?: nameError
                        if (error == null) scope.launch {
                            busy = true
                            val result = if (isSignUp) repository.signUp(email, password, displayName)
                                else repository.signIn(email, password).map { Unit }
                            busy = false
                            result.onSuccess {
                                if (isSignUp) {
                                    isSignUp = false
                                    notice = "Аккаунт создан. Если включено подтверждение почты, проверь входящие письма."
                                    password = ""
                                }
                            }.onFailure { error = it.message ?: "Не удалось выполнить запрос" }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuthViolet),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                else Text(
                    when {
                        phoneMode && phoneCodeSent -> "Подтвердить код"
                        phoneMode -> "Отправить SMS-код"
                        isSignUp -> "Создать аккаунт"
                        else -> "Войти"
                    },
                    color = Color.White
                )
            }

            if (phoneMode && phoneCodeSent) {
                TextButton(
                    enabled = !busy,
                    onClick = { phoneCodeSent = false; smsCode = ""; error = null; notice = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Изменить номер", color = AuthMuted) }
            }

            if (!phoneMode && !isSignUp) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        error = InputValidation.email(email)
                        notice = null
                        if (error == null) scope.launch {
                            busy = true
                            repository.requestPasswordReset(email)
                                .onSuccess { notice = "Если адрес зарегистрирован, письмо для сброса отправлено." }
                                .onFailure { error = it.message ?: "Не удалось отправить письмо" }
                            busy = false
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Забыли пароль?", color = AuthMuted) }
            }

            TextButton(
                enabled = !busy,
                onClick = {
                    phoneMode = !phoneMode
                    phoneCodeSent = false
                    smsCode = ""
                    error = null
                    notice = null
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(if (phoneMode) "Продолжить по электронной почте" else "Войти или создать аккаунт по телефону", color = AuthViolet)
            }

            if (!phoneMode) {
                TextButton(
                    enabled = !busy,
                    onClick = { isSignUp = !isSignUp; error = null; notice = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (isSignUp) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться", color = AuthViolet)
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AuthWhite,
    unfocusedTextColor = AuthWhite,
    focusedBorderColor = AuthViolet,
    unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
    focusedLabelColor = AuthViolet,
    unfocusedLabelColor = AuthMuted,
    cursorColor = AuthViolet
)
