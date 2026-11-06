package com.becamce.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.becamce.app.core.data.SupabaseAuthRepository
import com.becamce.app.core.data.SupabaseChatRepository
import com.becamce.app.core.data.SupabaseProfileRepository
import com.becamce.app.core.domain.AuthRepository
import com.becamce.app.core.domain.ChatRepository
import com.becamce.app.core.domain.ProfileRepository
import com.becamce.app.core.model.ChatKind
import com.becamce.app.core.model.UserProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch

private val Night = Color(0xFF09090E)
private val Panel = Color(0xFF171722)
private val Violet = Color(0xFF9B78F5)
private val Muted = Color(0xFF9291A4)
private val White = Color(0xFFF4F1FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BecamceApp() }
    }
}

@Composable
private fun BecamceApp() {
    val context = LocalContext.current
    val authRepository = remember { SupabaseAuthRepository(context.applicationContext) }
    val profileRepository = remember { SupabaseProfileRepository(context.applicationContext) }
    val chatRepository = remember { SupabaseChatRepository(context.applicationContext) }
    LaunchedEffect(authRepository) { authRepository.refreshSessionIfNeeded() }
    val session by authRepository.session.collectAsState(initial = null)
    MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Panel, primary = Violet, onBackground = White, onSurface = White)) {
        if (session == null) AuthScreen(authRepository)
        else MessengerShell(authRepository, profileRepository, chatRepository)
    }
}

@Composable
private fun MessengerShell(
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    chatRepository: ChatRepository
) {
    var selectedTab by remember { mutableStateOf("Чаты") }
    var search by remember { mutableStateOf("") }
    var openedChatId by remember { mutableStateOf<String?>(null) }
    MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Panel, primary = Violet, onBackground = White, onSurface = White)) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF11111A)) {
                    listOf(
                        Triple("Чаты", Icons.Rounded.ChatBubbleOutline, "Чаты"),
                        Triple("Звонки", Icons.Rounded.Call, "Звонки"),
                        Triple("Контакты", Icons.Rounded.Groups, "Контакты"),
                        Triple("Профиль", Icons.Rounded.Person, "Профиль")
                    ).forEach { (label, icon, route) ->
                        NavigationBarItem(
                            selected = selectedTab == route,
                            onClick = { selectedTab = route; openedChatId = null },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, selectedTextColor = Violet, indicatorColor = Color(0xFF28213A), unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == "Чаты" && openedChatId == null) {
                    FloatingActionButton(onClick = { selectedTab = "Контакты" }, containerColor = Violet, contentColor = Color.White, shape = CircleShape) {
                        Icon(Icons.Rounded.Add, contentDescription = "Найти собеседника")
                    }
                }
            }
        ) { padding ->
            when {
                openedChatId != null -> ConversationScreen(Modifier.padding(padding), openedChatId!!, chatRepository) { openedChatId = null }
                selectedTab == "Чаты" -> ChatsScreen(Modifier.padding(padding), chatRepository, search, { search = it }) { openedChatId = it }
                selectedTab == "Контакты" -> PeopleScreen(Modifier.padding(padding), profileRepository, chatRepository) { chatId ->
                    openedChatId = chatId
                    selectedTab = "Чаты"
                }
                selectedTab == "Профиль" -> ProfileScreen(Modifier.padding(padding), authRepository, profileRepository)
                else -> PlaceholderScreen(Modifier.padding(padding), selectedTab)
            }
        }
    }
}

@Composable
private fun ChatsScreen(
    modifier: Modifier,
    repository: ChatRepository,
    search: String,
    onSearchChange: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    var chats by remember { mutableStateOf<List<com.becamce.app.core.model.ChatSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) {
        repository.observeChats().catch { error = it.message ?: "Не удалось загрузить чаты"; loading = false }.collect { chats = it; loading = false; error = null }
    }
    val filtered = chats.filter { (it.title ?: "Личный чат").contains(search, true) || (it.lastMessage?.body ?: "").contains(search, true) }
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171322), Night, Night))).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("becamce", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("ТВОЁ ПРОСТРАНСТВО ОБЩЕНИЯ", color = Muted, fontSize = 9.sp, letterSpacing = 1.6.sp)
            }
            IconButton(onClick = {}) { Icon(Icons.Rounded.Settings, contentDescription = "Настройки", tint = Muted) }
        }
        Spacer(Modifier.height(24.dp))
        SearchField(search, onSearchChange, "Поиск чатов")
        Spacer(Modifier.height(24.dp))
        Text("СООБЩЕНИЯ", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(10.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Violet) }
            error != null -> Text(error!!, color = Color(0xFFFF8B9A))
            filtered.isEmpty() -> EmptyState("Пока нет чатов", "Найди человека во вкладке «Контакты», чтобы начать общение.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(filtered, key = { it.id }) { chat ->
                    ChatRow(chat.title ?: if (chat.kind == ChatKind.GROUP) "Групповой чат" else "Личный чат", chat.lastMessage?.body ?: "Начните общение", chat.id.take(2).uppercase()) { onOpen(chat.id) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel.copy(alpha = .88f))
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted)
        TextField(
            value = value, onValueChange = onChange, placeholder = { Text(hint, color = Muted) }, singleLine = true,
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = White, unfocusedTextColor = White, cursorColor = Violet),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChatRow(name: String, message: String, initials: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel.copy(alpha = .78f))
            .border(1.dp, Color.White.copy(alpha = .045f), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Color(0xFF44336E)))), contentAlignment = Alignment.Center) {
            Text(initials.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(message, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PeopleScreen(
    modifier: Modifier,
    profiles: ProfileRepository,
    chats: ChatRepository,
    onChatCreated: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().background(Night).padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Найти людей", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Ищи по юзернейму или имени", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        SearchField(query, { query = it; message = null }, "Имя или @username")
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    message = null
                    profiles.searchUsers(query).onSuccess { results = it }.onFailure { message = it.message ?: "Поиск не удался" }
                    loading = false
                }
            },
            enabled = query.trim().length >= 2 && !loading,
            colors = ButtonDefaults.buttonColors(containerColor = Violet),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(15.dp)
        ) { if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp) else Text("Найти") }
        message?.let { Text(it, color = Color(0xFFFF8B9A), modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(results, key = { it.id }) { profile ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Violet), contentAlignment = Alignment.Center) {
                        Text(profile.displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName.ifBlank { "Пользователь" }, color = White, fontWeight = FontWeight.SemiBold)
                        Text(profile.username?.let { "@$it" } ?: "Юзернейм не задан", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(enabled = busyId == null, onClick = {
                        scope.launch {
                            busyId = profile.id
                            chats.createChat(ChatKind.DIRECT, null, listOf(profile.id))
                                .onSuccess(onChatCreated)
                                .onFailure { message = it.message ?: "Не удалось создать чат" }
                            busyId = null
                        }
                    }) { Text(if (busyId == profile.id) "..." else "Написать", color = Violet) }
                }
            }
        }
    }
}

@Composable
private fun ConversationScreen(modifier: Modifier, chatId: String, repository: ChatRepository, onBack: () -> Unit) {
    var messages by remember { mutableStateOf<List<com.becamce.app.core.model.Message>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(chatId) { repository.markRead(chatId); repository.observeMessages(chatId).catch { error = it.message ?: "Не удалось загрузить сообщения" }.collect { messages = it } }
    Column(modifier.fillMaxSize().background(Night).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Назад", color = Violet) }
            Text("Диалог", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = false, contentPadding = PaddingValues(vertical = 12.dp)) {
            items(messages, key = { it.id }) { msg ->
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(msg.body.orEmpty(), color = White, modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp))
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8B9A), fontSize = 12.sp) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft, onValueChange = { draft = it; error = null }, modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение…", color = Muted) }, colors = TextFieldDefaults.colors(
                    focusedContainerColor = Panel, unfocusedContainerColor = Panel, focusedTextColor = White, unfocusedTextColor = White,
                    focusedIndicatorColor = Violet, unfocusedIndicatorColor = Color.Transparent, cursorColor = Violet
                )
            )
            IconButton(enabled = draft.isNotBlank() && !sending, onClick = {
                scope.launch {
                    sending = true
                    repository.sendMessage(chatId, draft).onSuccess { draft = "" }.onFailure { error = it.message ?: "Не удалось отправить" }
                    sending = false
                }
            }) { Icon(Icons.Rounded.Add, contentDescription = "Отправить", tint = Violet) }
        }
    }
}

@Composable
private fun ProfileScreen(modifier: Modifier, authRepository: AuthRepository, repository: ProfileRepository) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        repository.getMyProfile().onSuccess {
            profile = it; displayName = it.displayName; username = it.username.orEmpty(); bio = it.bio
        }.onFailure { message = it.message ?: "Не удалось загрузить профиль"; isError = true }
    }

    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171322), Night, Night)))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(78.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Color(0xFF44336E)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Твой профиль", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(profile?.username?.let { "@$it" } ?: "Задай свой уникальный юзернейм", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = profileFieldColors())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it.removePrefix("@").lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(24) },
            label = { Text("Юзернейм") }, leadingIcon = { Text("@", color = Violet) }, singleLine = true,
            supportingText = { Text("4–24 символа: латиница, цифры и _", color = Muted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(), colors = profileFieldColors()
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = bio, onValueChange = { bio = it.take(160) }, label = { Text("О себе") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = profileFieldColors())
        message?.let { Text(it, color = if (isError) Color(0xFFFF8B9A) else Color(0xFF9ADDBA), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy && profile != null,
            onClick = {
                scope.launch {
                    busy = true; message = null
                    val clean = username.trim().takeIf { it.isNotBlank() }
                    val valid = clean == null || Regex("^[a-z0-9_]{4,24}$").matches(clean)
                    if (!valid) {
                        message = "Юзернейм: 4–24 символа, латиница, цифры и _"; isError = true
                    } else {
                        var available = true
                        if (clean != null && clean != profile?.username) {
                            repository.isUsernameAvailable(clean).onSuccess { available = it }
                                .onFailure { message = it.message ?: "Не удалось проверить юзернейм"; isError = true; available = false }
                        }
                        if (available && message == null) {
                            repository.updateMyProfile(displayName, clean, bio).onSuccess {
                                profile = it; username = it.username.orEmpty(); message = "Профиль сохранён"; isError = false
                            }.onFailure {
                                message = it.message ?: "Не удалось сохранить профиль"; isError = true
                            }
                        } else if (!available && message == null) {
                            message = "Этот юзернейм уже занят"; isError = true
                        }
                    }
                    busy = false
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Violet),
            shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp) else Text("Сохранить профиль", color = Color.White) }
        Spacer(Modifier.height(8.dp))
        TextButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                authRepository.signOut().onFailure { message = it.message ?: "Не удалось выйти"; isError = true }
                busy = false
            }
        }) { Text("Выйти из аккаунта", color = Muted) }
    }
}

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Violet,
    unfocusedBorderColor = Color.White.copy(alpha = .14f), focusedLabelColor = Violet,
    unfocusedLabelColor = Muted, cursorColor = Violet
)

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun PlaceholderScreen(modifier: Modifier = Modifier, title: String) {
    Column(modifier.fillMaxSize().background(Night).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Этот раздел появится в следующих версиях", color = Muted)
    }
}
