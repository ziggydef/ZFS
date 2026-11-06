package com.becamce.app

import android.os.Bundle
import android.content.Intent
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.becamce.app.core.data.SupabaseCallRepository
import com.becamce.app.core.data.CallInfo
import com.becamce.app.core.data.SupabaseChatRepository
import com.becamce.app.core.data.SupabaseProfileRepository
import com.becamce.app.core.domain.AuthRepository
import com.becamce.app.core.domain.ChatRepository
import com.becamce.app.core.domain.ProfileRepository
import com.becamce.app.core.model.ChatKind
import com.becamce.app.core.model.UserProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen

private val Night = Color(0xFF09090E)
private val Panel = Color(0xFF171722)
private val Violet = Color(0xFF9B78F5)
private val Muted = Color(0xFF9291A4)
private val White = Color(0xFFF4F1FC)

private data class ActiveCall(
    val title: String,
    val chatId: String? = null,
    val peerId: String? = null,
    val callId: String? = null
)

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
    val profileRepository = remember { SupabaseProfileRepository(context.applicationContext, authRepository) }
    val chatRepository = remember { SupabaseChatRepository(context.applicationContext, authRepository) }
    val callRepository = remember { SupabaseCallRepository(context.applicationContext, authRepository) }
    LaunchedEffect(authRepository) { authRepository.refreshSessionIfNeeded() }
    val session by authRepository.session.collectAsState(initial = null)
    MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Panel, primary = Violet, onBackground = White, onSurface = White)) {
        if (session == null) AuthScreen(authRepository)
        else MessengerShell(authRepository, profileRepository, chatRepository, callRepository)
    }
}

@Composable
private fun MessengerShell(
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    chatRepository: ChatRepository,
    callRepository: SupabaseCallRepository
) {
    var selectedTab by remember { mutableStateOf("Чаты") }
    var search by remember { mutableStateOf("") }
    var openedChatId by remember { mutableStateOf<String?>(null) }
    var openedChatTitle by remember { mutableStateOf("Диалог") }
    var activeCall by remember { mutableStateOf<ActiveCall?>(null) }
    val currentSession by authRepository.session.collectAsState(initial = null)
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
                            onClick = { selectedTab = route; openedChatId = null; openedChatTitle = "Диалог" },
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
                openedChatId != null -> ConversationScreen(Modifier.padding(padding), openedChatId!!, openedChatTitle, chatRepository, callRepository, currentSession?.userId.orEmpty(), onStartCall = { peerId -> activeCall = ActiveCall(openedChatTitle, openedChatId, peerId) }) {
                    openedChatId = null
                    openedChatTitle = "Диалог"
                }
                selectedTab == "Чаты" -> ChatsScreen(
                    Modifier.padding(padding), chatRepository, profileRepository,
                    currentSession?.userId.orEmpty(), search, { search = it }
                ) { chatId, title ->
                    openedChatId = chatId
                    openedChatTitle = title
                }
                selectedTab == "Звонки" -> CallsScreen(Modifier.padding(padding), callRepository, onAccept = { call -> activeCall = ActiveCall("Входящий звонок", callId = call.id) })
                selectedTab == "Контакты" -> PeopleScreen(Modifier.padding(padding), profileRepository, chatRepository) { chatId, title ->
                    openedChatId = chatId
                    openedChatTitle = title
                    selectedTab = "Чаты"
                }
                selectedTab == "Профиль" -> ProfileScreen(Modifier.padding(padding), authRepository, profileRepository)
                else -> PlaceholderScreen(Modifier.padding(padding), selectedTab)
            }
        }
        activeCall?.let { call ->
            VoiceCallScreen(
                modifier = Modifier.fillMaxSize(),
                repository = callRepository,
                currentUserId = currentSession?.userId.orEmpty(),
                peerTitle = call.title,
                outgoingChatId = call.chatId,
                outgoingPeerId = call.peerId,
                incomingCallId = call.callId,
                onClose = { activeCall = null }
            )
        }
    }
}

@Composable
private fun CallsScreen(
    modifier: Modifier,
    repository: SupabaseCallRepository,
    onAccept: (CallInfo) -> Unit
) {
    var incoming by remember { mutableStateOf<List<CallInfo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository) {
        repository.observeIncomingCalls().catch { error = it.message ?: "Не удалось проверить входящие звонки" }.collect { incoming = it; error = null }
    }
    Column(modifier.fillMaxSize().background(Night).padding(20.dp)) {
        Text("Звонки", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        error?.let { Text(it, color = Color(0xFFFF9BA8), fontSize = 12.sp) }
        if (incoming.isEmpty()) {
            Text("Нет входящих звонков", color = Muted, fontSize = 14.sp)
        } else {
            incoming.forEach { call ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp))
                        .background(Panel).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Входящий звонок", color = White, fontWeight = FontWeight.SemiBold)
                        Text("От пользователя ${call.callerId.take(8)}…", color = Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        scope.launch { repository.setStatus(call.id, "rejected") }
                    }) { Text("Отклонить", color = Color(0xFFFF9BA8)) }
                    TextButton(onClick = { onAccept(call) }) { Text("Принять", color = Violet) }
                }
            }
        }
    }
}

@Composable
private fun ChatsScreen(
    modifier: Modifier,
    repository: ChatRepository,
    profiles: ProfileRepository,
    currentUserId: String,
    search: String,
    onSearchChange: (String) -> Unit,
    onOpen: (String, String) -> Unit
) {
    var chats by remember { mutableStateOf<List<com.becamce.app.core.model.ChatSummary>>(emptyList()) }
    var profilesById by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository, profiles, currentUserId) {
        repository.observeChats()
            .retryWhen { cause, attempt ->
                if (cause is kotlinx.coroutines.CancellationException) return@retryWhen false
                error = cause.message ?: "Связь потеряна. Повторяем подключение…"
                loading = false
                kotlinx.coroutines.delay((1_000L * (attempt + 1L)).coerceAtMost(30_000L))
                true
            }
            .catch { error = it.message ?: "Не удалось загрузить чаты"; loading = false }
            .collect { updatedChats ->
                chats = updatedChats
                if (currentUserId.isNotBlank()) {
                    val missingIds = updatedChats.filter { it.kind == ChatKind.DIRECT }
                        .flatMap { it.members }
                        .filter { it != currentUserId && it !in profilesById }
                        .distinct()
                    missingIds.chunked(100).forEach { batch ->
                        profiles.getProfilesByIds(batch).onSuccess { found ->
                            profilesById = profilesById + found.associateBy { it.id }
                        }
                    }
                }
                loading = false
                error = null
            }
    }
    fun displayTitle(chat: com.becamce.app.core.model.ChatSummary): String {
        if (!chat.title.isNullOrBlank()) return chat.title
        if (chat.kind == ChatKind.GROUP) return "Групповой чат"
        val peer = chat.members.firstOrNull { it != currentUserId }?.let { profilesById[it] }
        return peer?.displayName?.ifBlank { peer.username?.let { "@$it" } ?: "Личный чат" } ?: "Личный чат"
    }
    val filtered = chats.filter {
        displayTitle(it).contains(search, true) || (it.lastMessage?.body ?: "").contains(search, true)
    }
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171322), Night, Night))).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("becamce", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("ТВОЁ ПРОСТРАНСТВО ОБЩЕНИЯ", color = Muted, fontSize = 9.sp, letterSpacing = 1.6.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        SearchField(search, onSearchChange, "Поиск чатов")
        Spacer(Modifier.height(24.dp))
        Text("СООБЩЕНИЯ", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(10.dp))
        when {
            loading && chats.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Violet) }
            chats.isEmpty() && error != null -> Text(error!!, color = Color(0xFFFF8B9A))
            filtered.isEmpty() -> EmptyState("Пока нет чатов", "Найди человека во вкладке «Контакты», чтобы начать общение.")
            else -> {
                error?.let { Text(it, color = Color(0xFFFF8B9A), fontSize = 11.sp) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                    items(filtered, key = { it.id }) { chat ->
                        val title = displayTitle(chat)
                        ChatRow(title, chat.lastMessage?.body ?: chat.lastMessage?.mediaFileName?.let { "📎 $it" } ?: "Начните общение", title.take(2).uppercase()) { onOpen(chat.id, title) }
                    }
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
    onChatCreated: (String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var groupTitle by remember { mutableStateOf("") }
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
        if (selectedIds.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = groupTitle, onValueChange = { groupTitle = it.take(60) },
                label = { Text("Название группы") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = profileFieldColors()
            )
            Text("${selectedIds.size} выбрано · для группы выбери хотя бы одного человека", color = Muted, fontSize = 11.sp)
            Button(
                onClick = {
                    scope.launch {
                        busyId = "group"
                        val title = groupTitle.trim().ifBlank { "Новая группа" }
                        chats.createChat(ChatKind.GROUP, title, selectedIds.toList())
                            .onSuccess { selectedIds = emptySet(); groupTitle = ""; onChatCreated(it, title) }
                            .onFailure { message = it.message ?: "Не удалось создать группу" }
                        busyId = null
                    }
                },
                enabled = selectedIds.isNotEmpty() && busyId == null,
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            ) { Text(if (busyId == "group") "Создаём…" else "Создать группу") }
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
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
                    Checkbox(
                        checked = profile.id in selectedIds,
                        onCheckedChange = { checked ->
                            selectedIds = if (checked) selectedIds + profile.id else selectedIds - profile.id
                        },
                        colors = CheckboxDefaults.colors(checkedColor = Violet, uncheckedColor = Muted)
                    )
                    TextButton(enabled = busyId == null, onClick = {
                        scope.launch {
                            busyId = profile.id
                            chats.createChat(ChatKind.DIRECT, null, listOf(profile.id))
                                .onSuccess { chatId ->
                                    val title = profile.displayName.ifBlank { profile.username?.let { "@$it" } ?: "Личный чат" }
                                    onChatCreated(chatId, title)
                                }
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
private fun ConversationScreen(
    modifier: Modifier,
    chatId: String,
    chatTitle: String,
    repository: ChatRepository,
    callRepository: SupabaseCallRepository,
    currentUserId: String,
    onStartCall: (String) -> Unit,
    onBack: () -> Unit
) {
    var messages by remember { mutableStateOf<List<com.becamce.app.core.model.Message>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editingDraft by remember { mutableStateOf("") }
    var deleteConfirmMessageId by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember { mutableStateOf<com.becamce.app.core.model.Message?>(null) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                sending = true
                error = null
                runCatching {
                    val originalName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                        ?: "attachment"
                    val name = originalName.take(180)
                    val mime = resolveAttachmentMimeType(context.contentResolver.getType(uri), originalName)
                    val bytes = ByteArrayOutputStream().use { output ->
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Не удалось прочитать файл")
                        input.use { stream ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 20 * 1024 * 1024) { "Максимальный размер файла — 20 МБ" }
                                output.write(buffer, 0, count)
                            }
                        }
                        output.toByteArray()
                    }
                    require(bytes.isNotEmpty()) { "Файл пустой" }
                    repository.uploadAttachment(chatId, name, mime, bytes, replyingTo?.id).onFailure { throw it }
                    replyingTo = null
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    error = it.message ?: "Не удалось отправить файл"
                }
                sending = false
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(chatId) {
        repository.markRead(chatId)
        repository.observeMessages(chatId)
            .retryWhen { cause, attempt ->
                if (cause is kotlinx.coroutines.CancellationException) return@retryWhen false
                error = cause.message ?: "Связь потеряна. Повторяем подключение…"
                kotlinx.coroutines.delay((1_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30_000L))
                true
            }
            .catch { error = it.message ?: "Не удалось загрузить сообщения" }
            .collect { messages = it; error = null }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    val editingMessage = messages.firstOrNull { it.id == editingMessageId }
    val deleteConfirmMessage = messages.firstOrNull { it.id == deleteConfirmMessageId }
    Column(modifier.fillMaxSize().background(Night).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Назад", color = Violet) }
            Text(chatTitle, modifier = Modifier.weight(1f), color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            IconButton(onClick = {
                scope.launch {
                    callRepository.peerInChat(chatId, currentUserId)
                        .onSuccess { onStartCall(it) }
                        .onFailure { error = it.message ?: "Не удалось начать звонок" }
                }
            }) {
                Icon(Icons.Rounded.Call, contentDescription = "Позвонить", tint = Violet)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), reverseLayout = false, contentPadding = PaddingValues(vertical = 12.dp)) {
            items(messages, key = { it.id }) { msg ->
                val ownMessage = msg.senderId == currentUserId
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = if (ownMessage) Arrangement.End else Arrangement.Start
                ) {
                    Box {
                        Column(
                            Modifier.widthIn(max = 300.dp).clip(
                                RoundedCornerShape(
                                    topStart = 18.dp, topEnd = 18.dp,
                                    bottomEnd = if (ownMessage) 5.dp else 18.dp,
                                    bottomStart = if (ownMessage) 18.dp else 5.dp
                                )
                            ).background(if (ownMessage) Color(0xFF44336E) else Panel)
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { selectedMessageId = msg.id }
                                )
                                .pointerInput(msg.id) {
                                    var rightDrag = 0f
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { change, amount ->
                                            if (amount > 0) rightDrag += amount
                                            change.consume()
                                        },
                                        onDragEnd = {
                                            if (rightDrag > 64f) replyingTo = msg
                                            rightDrag = 0f
                                        },
                                        onDragCancel = { rightDrag = 0f }
                                    )
                                }
                                .padding(horizontal = 13.dp, vertical = 10.dp)
                        ) {
                            msg.replyTo?.let { replyId ->
                                messages.firstOrNull { it.id == replyId }?.let { replied ->
                                    Text("↳ ${replied.body.orEmpty().take(100)}", color = White.copy(alpha = .62f), fontSize = 10.sp)
                                }
                            }
                            msg.mediaPath?.let { path ->
                                Text(
                                    "📎 ${msg.mediaFileName ?: "Вложение"}",
                                    color = Color(0xFFD8C9FF),
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable(enabled = !sending) {
                                        scope.launch {
                                            sending = true
                                            repository.getAttachmentUrl(path).onSuccess { url ->
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                                    .onFailure { error = "Не удалось открыть файл" }
                                            }.onFailure { error = it.message ?: "Не удалось получить файл" }
                                            sending = false
                                        }
                                    }.padding(vertical = 4.dp)
                                )
                            }
                            msg.body?.let { Text(it, color = White, fontSize = 14.sp) }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (msg.editedAt != null) Text("изм. ", color = White.copy(alpha = .52f), fontSize = 9.sp)
                                Text(msg.createdAt.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                                    color = White.copy(alpha = .52f), fontSize = 9.sp)
                            }
                        }
                        DropdownMenu(
                            expanded = selectedMessageId == msg.id,
                            onDismissRequest = { selectedMessageId = null },
                            modifier = Modifier.background(Panel)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ответить", color = White) },
                                leadingIcon = { Icon(Icons.Rounded.ChatBubbleOutline, null, tint = Violet) },
                                onClick = { replyingTo = msg; selectedMessageId = null }
                            )
                            DropdownMenuItem(
                                text = { Text("Копировать", color = White) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = Violet) },
                                enabled = !msg.body.isNullOrBlank(),
                                onClick = {
                                    msg.body?.let { clipboard.setText(AnnotatedString(it)) }
                                    selectedMessageId = null
                                }
                            )
                            if (ownMessage) {
                                DropdownMenuItem(
                                    text = { Text("Изменить", color = White) },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = Violet) },
                                    enabled = !actionBusy && !msg.body.isNullOrBlank(),
                                    onClick = {
                                        editingMessageId = msg.id
                                        editingDraft = msg.body.orEmpty()
                                        selectedMessageId = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить", color = Color(0xFFFFA0AD)) },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFFA0AD)) },
                                    enabled = !actionBusy,
                                    onClick = { deleteConfirmMessageId = msg.id; selectedMessageId = null }
                                )
                            }
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8B9A), fontSize = 12.sp) }
        replyingTo?.let { target ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ответ на сообщение", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(target.body.orEmpty().take(100), color = Muted, fontSize = 11.sp, maxLines = 1)
                }
                TextButton(onClick = { replyingTo = null }) { Text("Отмена", color = Muted) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = !sending,
                onClick = {
                    attachmentPicker.launch(arrayOf(
                        "image/jpeg", "image/png", "image/webp", "image/gif",
                        "application/pdf", "text/plain", "application/zip",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    ))
                }
            ) { Icon(Icons.Rounded.AttachFile, contentDescription = "Прикрепить файл", tint = Violet) }
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
                    repository.sendMessage(chatId, draft, replyingTo?.id).onSuccess { draft = ""; replyingTo = null }.onFailure { error = it.message ?: "Не удалось отправить" }
                    sending = false
                }
            }) { Icon(Icons.Rounded.Send, contentDescription = "Отправить", tint = Violet) }
        }
    }
    if (editingMessage != null) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) editingMessageId = null },
            title = { Text("Изменить сообщение") },
            text = {
                OutlinedTextField(
                    value = editingDraft,
                    onValueChange = { editingDraft = it.take(8000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = profileFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editingDraft.isNotBlank() && !actionBusy,
                    onClick = {
                        scope.launch {
                            actionBusy = true
                            repository.editMessage(editingMessage.id, editingDraft)
                                .onSuccess { editingMessageId = null }
                                .onFailure { error = it.message ?: "Не удалось изменить сообщение" }
                            actionBusy = false
                        }
                    }
                ) { Text(if (actionBusy) "Сохраняем…" else "Сохранить", color = Violet) }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { editingMessageId = null }) {
                    Text("Отмена", color = Muted)
                }
            },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = White
        )
    }
    if (deleteConfirmMessage != null) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) deleteConfirmMessageId = null },
            title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение будет удалено для участников чата.", color = Muted) },
            confirmButton = {
                TextButton(
                    enabled = !actionBusy,
                    onClick = {
                        scope.launch {
                            actionBusy = true
                            repository.deleteMessage(deleteConfirmMessage.id)
                                .onSuccess { deleteConfirmMessageId = null }
                                .onFailure { error = it.message ?: "Не удалось удалить сообщение" }
                            actionBusy = false
                        }
                    }
                ) { Text(if (actionBusy) "Удаляем…" else "Удалить", color = Color(0xFFFFA0AD)) }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { deleteConfirmMessageId = null }) {
                    Text("Отмена", color = Muted)
                }
            },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = White
        )
    }
}

@Composable
private fun ProfileScreen(modifier: Modifier, authRepository: AuthRepository, repository: ProfileRepository) {
    val context = LocalContext.current
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
            .verticalScroll(rememberScrollState())
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
            value = username, onValueChange = { username = it.removePrefix("@").lowercase().filter { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '_' }.take(24) },
            enabled = profile?.username.isNullOrBlank(),
            label = { Text("Ник для входа") }, leadingIcon = { Text("@", color = Violet) }, singleLine = true,
            supportingText = { Text(if (profile?.username.isNullOrBlank()) "4–24 символа: латиница, цифры и _" else "Ник для входа нельзя изменить", color = Muted) },
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
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/l_am_ziggy")))
                }.onFailure {
                    message = "Не удалось открыть Telegram"
                    isError = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(15.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Violet.copy(alpha = .65f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Violet)
        ) {
            Text("Связаться с разработчиком")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Разработано ziGGy",
            color = Muted.copy(alpha = .72f),
            fontSize = 11.sp,
            letterSpacing = .35.sp
        )
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

private fun resolveAttachmentMimeType(resolved: String?, fileName: String): String {
    val allowed = setOf(
        "image/jpeg", "image/png", "image/webp", "image/gif",
        "application/pdf", "text/plain", "application/zip",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )
    if (resolved != null && resolved in allowed) return resolved
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "zip" -> "application/zip"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> resolved ?: "application/octet-stream"
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
