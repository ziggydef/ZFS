package com.becamce.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    var username by remember { mutableStateOf("") }
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
            Text(if (isSignUp) "СОЗДАЙ СВОЙ АККАУНТ" else "РАДЫ ТЕБЯ ВИДЕТЬ",
                color = AuthMuted, fontSize = 10.sp, letterSpacing = 1.7.sp)

            if (isSignUp) {
                OutlinedTextField(
                    value = displayName, onValueChange = { displayName = it; error = null },
                    label = { Text("Как тебя зовут") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                )
            }
            OutlinedTextField(
                value = username,
                onValueChange = { value -> username = value.removePrefix("@").lowercase()
                    .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(24); error = null },
                label = { Text("Ник") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(), colors = fieldColors()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it; error = null },
                label = { Text("Пароль") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), colors = fieldColors()
            )

            error?.let { Text(it, color = Color(0xFFFF8B9A), fontSize = 12.sp) }
            notice?.let { Text(it, color = Color(0xFF9ADDBA), fontSize = 12.sp) }

            Button(
                onClick = {
                    error = InputValidation.username(username)
                        ?: if (password.isBlank()) "Введите пароль"
                        else if (isSignUp) InputValidation.password(password)
                            ?: InputValidation.displayName(displayName)
                        else null
                    notice = null
                    if (error == null) scope.launch {
                        busy = true
                        val result = if (isSignUp) repository.signUp(username, password, displayName)
                            else repository.signIn(username, password).map { Unit }
                        busy = false
                        result.onSuccess {
                            if (isSignUp) {
                                isSignUp = false
                                password = ""
                                notice = "Аккаунт создан. Теперь войди по нику и паролю."
                            }
                        }.onFailure { error = it.message ?: "Не удалось выполнить запрос" }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuthViolet),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                else Text(if (isSignUp) "Создать аккаунт" else "Войти", color = Color.White)
            }

            TextButton(
                enabled = !busy,
                onClick = { isSignUp = !isSignUp; error = null; notice = null; password = "" },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(if (isSignUp) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться",
                    color = AuthViolet)
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
