package com.zfs.app

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val Bg = Color(0xFF09090D)
private val Panel = Color(0xFF14131C)
private val Line = Color(0xFF302C3C)
private val Muted = Color(0xFF9B98AA)
private val TextMain = Color(0xFFF5F2FF)
private val Accent = Color(0xFF9B7CFF)
private val AccentSoft = Color(0xFFB9A5FF)
private val Formats = listOf("ztxt", "zdoc", "zdata", "zlog", "zimg", "zvid", "zaud", "zpdf", "zarc", "zbin")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 9, 13)
        window.navigationBarColor = android.graphics.Color.rgb(9, 9, 13)
        setContent { ZfsApp() }
    }
}

@Composable
private fun ZfsApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var textMode by remember { mutableStateOf(false) }
    var sourceFile by remember { mutableStateOf<ByteArray?>(null) }
    var sourceName by remember { mutableStateOf<String?>(null) }
    var containerFile by remember { mutableStateOf<ByteArray?>(null) }
    var containerName by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("private-note") }
    var noteText by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("ztxt") }
    var formatMenu by remember { mutableStateOf(false) }
    var encryptPassword by remember { mutableStateOf("") }
    var decryptPassword by remember { mutableStateOf("") }
    var showEncryptPassword by remember { mutableStateOf(false) }
    var showDecryptPassword by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Выбери файл или введи текст, чтобы создать защищённый контейнер.") }
    var statusGood by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var restored by remember { mutableStateOf<ByteArray?>(null) }
    var restoredName by remember { mutableStateOf("restored-file") }
    var restoredText by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    fun displayName(uri: Uri): String {
        var name = "imported-file"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        return name
    }
    fun report(message: String, good: Boolean = false) {
        status = message
        statusGood = good
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) report("Выбор файла отменён.")
        else try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось прочитать файл")
            val name = displayName(uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val ext = name.substringAfterLast('.', "").lowercase()
            format = when {
                mime.startsWith("image/") -> "zimg"
                mime.startsWith("video/") -> "zvid"
                mime.startsWith("audio/") -> "zaud"
                mime == "application/pdf" || ext == "pdf" -> "zpdf"
                ext in listOf("zip", "7z", "rar", "tar", "gz") -> "zarc"
                mime.startsWith("text/") -> "ztxt"
                mime.contains("word") || mime.contains("document") -> "zdoc"
                else -> "zbin"
            }
            sourceFile = bytes
            sourceName = name
            fileName = name
            noteText = ""
            preview = if (format == "zimg") BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
            report("Файл добавлен: " + name + " · " + humanSize(bytes.size.toLong()))
        } catch (e: Exception) {
            report("Ошибка импорта: " + (e.message ?: "неизвестная ошибка"))
        }
    }

    val saveEncrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) report("Сохранение отменено.")
        else try {
            val bytes = pending ?: error("Нет подготовленного контейнера")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Нет доступа к файлу")
            report("Контейнер ZFSF успешно сохранён. Не забудь сохранить пароль отдельно.", true)
        } catch (e: Exception) {
            report("Ошибка сохранения: " + (e.message ?: "ошибка"))
        }
    }

    val openEncrypted = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) report("Открытие отменено.")
        else try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось прочитать контейнер")
            containerFile = bytes
            containerName = displayName(uri)
            report("Контейнер выбран: " + containerName + " · " + humanSize(bytes.size.toLong()))
        } catch (e: Exception) {
            report("Ошибка открытия: " + (e.message ?: "ошибка"))
        }
    }

    val saveRestored = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri == null) report("Восстановление отменено.")
        else try {
            val bytes = restored ?: error("Нет восстановленных данных")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Нет доступа к файлу")
            report("Оригинальный файл восстановлен: " + restoredName, true)
        } catch (e: Exception) {
            report("Ошибка восстановления: " + (e.message ?: "ошибка"))
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Bg, surface = Panel, onSurface = TextMain,
        onBackground = TextMain, secondary = AccentSoft
    )) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(colors = listOf(Color(0xFF33204E), Bg, Bg), radius = 1050f)
            )
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2B2140))
                                .border(1.dp, Color(0xFF584582), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Security, null, tint = AccentSoft, modifier = Modifier.size(22.dp)) }
                        Text("ZFS VAULT", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
                    }
                    Box(
                        Modifier.clip(CircleShape).background(Color(0xFF211B32))
                            .border(1.dp, Color(0xFF584582), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp)
                    ) { Text("WEB VAULT · 0.2", color = AccentSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                }

                Column(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("YOUR FILES. YOUR PRIVACY.", color = AccentSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Private by design.", color = TextMain, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Шифруй и открывай файлы в формате ZFSF прямо на устройстве. Содержимое и пароль остаются у тебя.",
                        color = Muted, fontSize = 13.sp, lineHeight = 20.sp
                    )
                }

                GlassCard {
                    CardHeading("01", "Зашифровать")
                    Text("Выбери файл или создай защищённую текстовую заметку.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0B11))
                            .border(1.dp, Line, RoundedCornerShape(12.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ModeTab("Файл", !textMode, Modifier.weight(1f)) { textMode = false; report("Режим шифрования файла.") }
                        ModeTab("Текст", textMode, Modifier.weight(1f)) { textMode = true; report("Режим защищённой текстовой заметки.") }
                    }
                    if (textMode) {
                        VaultField("Текст заметки", noteText, { noteText = it }, "Текст, который нужно защитить…", 5)
                        VaultField("Имя заметки", fileName, { fileName = it }, "private-note")
                    } else {
                        FilePickerPanel(
                            if (sourceName == null) "＋ Выбрать файл" else sourceName!!,
                            if (sourceName == null) "Нажми, чтобы выбрать файл на устройстве" else "Файл готов к шифрованию · " + humanSize((sourceFile?.size ?: 0).toLong()),
                            sourceName != null
                        ) { importLauncher.launch(arrayOf("*/*")) }
                        if (sourceName != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF8EE0B8), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(sourceName!!, color = AccentSoft, fontSize = 11.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Тип контейнера", color = Color(0xFFC9C5D4), fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Box {
                                TextButton(onClick = { formatMenu = true }) { Text("." + format + "  ▾", color = AccentSoft, fontWeight = FontWeight.SemiBold) }
                                DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                                    Formats.forEach { item ->
                                        DropdownMenuItem(text = { Text("." + item) }, onClick = { format = item; formatMenu = false })
                                    }
                                }
                            }
                        }
                        VaultField("Имя выходного файла", fileName, { fileName = it }, "photo.jpg")
                    }
                    PasswordField(
                        "Пароль шифрования", encryptPassword, { encryptPassword = it },
                        showEncryptPassword, { showEncryptPassword = !showEncryptPassword }, "Минимум 12 символов"
                    )
                    Text("Используй длинный уникальный пароль. Забытый пароль восстановить нельзя.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    PrimaryAction("Зашифровать " + if (textMode) "заметку" else "файл", Icons.Rounded.Lock) {
                        try {
                            require(encryptPassword.length >= 12) { "Пароль должен содержать минимум 12 символов." }
                            val raw: ByteArray
                            val outputName: String
                            if (textMode) {
                                require(noteText.isNotBlank()) { "Введи текст заметки." }
                                require(fileName.isNotBlank()) { "Укажи имя заметки." }
                                raw = noteText.toByteArray(Charsets.UTF_8)
                                outputName = fileName.trim().removeSuffix(".ztxt") + ".ztxt"
                                format = "ztxt"
                            } else {
                                require(sourceFile != null) { "Сначала выбери файл." }
                                require(fileName.isNotBlank()) { "Укажи имя выходного файла." }
                                raw = sourceFile!!
                                outputName = fileName.trim() + "." + format
                            }
                            pending = Zfs.encrypt(format, raw, encryptPassword)
                            saveEncrypted.launch(outputName.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
                            report("Контейнер подготовлен. Выбери место сохранения.", true)
                        } catch (e: Exception) { report(e.message ?: "Не удалось зашифровать файл.") }
                    }
                }

                GlassCard {
                    CardHeading("02", "Расшифровать")
                    Text("Открой контейнер ZFSF, созданный совместимой версией ZFS Vault.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    FilePickerPanel(
                        containerName ?: "↗ Выбрать ZFS-файл",
                        if (containerName == null) "Например: photo.jpg.zimg" else "Контейнер выбран · " + humanSize((containerFile?.size ?: 0).toLong()),
                        containerName != null
                    ) { openEncrypted.launch(arrayOf("*/*")) }
                    PasswordField(
                        "Пароль контейнера", decryptPassword, { decryptPassword = it },
                        showDecryptPassword, { showDecryptPassword = !showDecryptPassword }, "Введи пароль"
                    )
                    PrimaryAction("Расшифровать и проверить", Icons.Rounded.LockOpen) {
                        try {
                            require(containerFile != null) { "Сначала выбери ZFS-контейнер." }
                            require(decryptPassword.isNotEmpty()) { "Введи пароль контейнера." }
                            val result = Zfs.decrypt(containerFile!!, decryptPassword)
                            format = result.first
                            restored = result.second
                            restoredText = if (format in listOf("ztxt", "zdoc", "zdata", "zlog")) String(result.second, Charsets.UTF_8) else null
                            restoredName = (containerName ?: "restored-file").let { name ->
                                val suffix = "." + format
                                if (name.lowercase().endsWith(suffix)) name.dropLast(suffix.length) else name
                            }
                            preview = if (format == "zimg") BitmapFactory.decodeByteArray(result.second, 0, result.second.size) else null
                            report("Контейнер успешно расшифрован и проверен.", true)
                        } catch (e: Exception) {
                            restored = null
                            restoredText = null
                            report("Не удалось расшифровать. Проверь пароль и целостность контейнера.")
                        }
                    }
                    if (restoredText != null) {
                        Text("Расшифрованный текст", color = AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0B11))
                                .border(1.dp, Line, RoundedCornerShape(12.dp)).padding(12.dp)
                        ) { Text(restoredText!!, color = TextMain, fontSize = 12.sp, lineHeight = 18.sp) }
                        SecondaryAction("Сохранить текстовый файл", Icons.Rounded.ArrowDownward) {
                            saveRestored.launch(restoredName.ifBlank { "restored-note.txt" })
                        }
                    } else if (restored != null) {
                        if (preview != null) {
                            Image(preview!!.asImageBitmap(), "Предпросмотр расшифрованного изображения",
                                Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp)))
                        }
                        SecondaryAction("Восстановить оригинал", Icons.Rounded.ArrowDownward) {
                            saveRestored.launch(restoredName.ifBlank { "restored-file" })
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FeatureCard("AES-256-GCM", "Аутентифицированное шифрование", Modifier.weight(1f))
                    FeatureCard("PBKDF2 · SHA-256", "600 000 итераций", Modifier.weight(1f))
                    FeatureCard("LOCAL", "Обработка на устройстве", Modifier.weight(1f))
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF15121D))
                        .border(1.dp, Color(0xFF46345F), RoundedCornerShape(12.dp)).padding(13.dp)
                ) {
                    Text("ВАЖНО", color = AccentSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Файлы обрабатываются в памяти устройства. Очень большие файлы могут не поместиться в RAM. ZFS Vault — развивающийся проект, не проходивший независимый аудит безопасности.",
                        color = Muted, fontSize = 10.sp, lineHeight = 16.sp
                    )
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (statusGood) Color(0xFF12251E) else Color(0xFF211B32)).padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (statusGood) Icons.Rounded.CheckCircle else Icons.Rounded.Shield, null,
                            tint = if (statusGood) Color(0xFFA9EFCF) else AccentSoft, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("СОСТОЯНИЕ", color = if (statusGood) Color(0xFFA9EFCF) else AccentSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Text(status, color = TextMain, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("© ZFS VAULT · ZFSF v1", color = Muted, fontSize = 9.sp)
                    Text("YOUR FILES. YOUR PRIVACY.", color = AccentSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF191722), Color(0xFF101017))))
            .border(1.dp, Line, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content
    )
}

@Composable
private fun CardHeading(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFF2B2140)).padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(number, color = Color(0xFFCDBDFF), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(title, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeTab(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp)).background(if (active) Color(0xFF2A223B) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (active) TextMain else Muted, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun FilePickerPanel(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF1C1728) else Color(0xFF171321))
            .border(1.dp, if (selected) Color(0xFF8D70D8) else Color(0xFF66528E), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen, null,
            tint = if (selected) Color(0xFF8EE0B8) else AccentSoft, modifier = Modifier.size(25.dp))
        Text(title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun VaultField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, minLines: Int = 1) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = Color(0xFFC9C5D4), fontSize = 12.sp)
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Muted.copy(alpha = 0.7f), fontSize = 12.sp) },
            minLines = minLines, maxLines = if (minLines > 1) 8 else 1, shape = RoundedCornerShape(11.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = Line,
                focusedTextColor = TextMain, unfocusedTextColor = TextMain, cursorColor = Accent,
                focusedContainerColor = Color(0xFF0B0B11), unfocusedContainerColor = Color(0xFF0B0B11)
            )
        )
    }
}

@Composable
private fun PasswordField(
    label: String, value: String, onValueChange: (String) -> Unit,
    visible: Boolean, toggle: () -> Unit, placeholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = Color(0xFFC9C5D4), fontSize = 12.sp)
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Muted.copy(alpha = 0.7f), fontSize = 12.sp) },
            singleLine = true, shape = RoundedCornerShape(11.dp),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = toggle) {
                    Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = Muted)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = Line,
                focusedTextColor = TextMain, unfocusedTextColor = TextMain, cursorColor = Accent,
                focusedContainerColor = Color(0xFF0B0B11), unfocusedContainerColor = Color(0xFF0B0B11)
            )
        )
    }
}

@Composable
private fun PrimaryAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(49.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun SecondaryAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF66528E)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentSoft)
    ) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(13.dp)).background(Color(0xFF101017))
            .border(1.dp, Line, RoundedCornerShape(13.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

private fun humanSize(bytes: Long): String =
    if (bytes < 1024) bytes.toString() + " B"
    else String.format(java.util.Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)

private object Zfs {
    private val magic = byteArrayOf(0x5A, 0x46, 0x53, 0x46)
    private const val version = 1
    private const val rounds = 600_000
    private const val headerSize = 38

    fun encrypt(format: String, data: ByteArray, password: String): ByteArray {
        require(format in Formats) { "Неизвестный тип контейнера" }
        val type = Formats.indexOf(format) + 1
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val header = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN)
            .put(magic).put(version.toByte()).put(type.toByte()).putInt(rounds).put(salt).put(iv).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, rounds), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(data)
    }

    fun decrypt(data: ByteArray, password: String): Pair<String, ByteArray> {
        require(data.size > headerSize + 16) { "Файл слишком короткий" }
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val fileMagic = ByteArray(4).also { buffer.get(it) }
        require(fileMagic.contentEquals(magic)) { "Это не контейнер ZFSF" }
        require(buffer.get().toInt() == version) { "Версия не поддерживается" }
        val type = buffer.get().toInt()
        require(type in 1..Formats.size) { "Неизвестный тип" }
        val iterations = buffer.int
        require(iterations in 100_000..2_000_000) { "Некорректные параметры KDF" }
        val salt = ByteArray(16).also { buffer.get(it) }
        val iv = ByteArray(12).also { buffer.get(it) }
        val header = data.copyOfRange(0, headerSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return Formats[type - 1] to cipher.doFinal(data.copyOfRange(headerSize, data.size))
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}