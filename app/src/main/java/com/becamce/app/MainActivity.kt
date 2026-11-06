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
import com.becamce.app.core.data.SupabaseAuthRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private data class ChatPreview(val name: String, val message: String, val time: String, val initials: String, val unread: Int = 0, val online: Boolean = false)

private val demoChats = listOf(
    ChatPreview("becamce team", "Добро пожаловать в becamce", "22:41", "B", 2, true),
    ChatPreview("Alex", "Отправил голосовое сообщение", "21:18", "A", 1, true),
    ChatPreview("Design lab", "Новый вариант интерфейса готов", "Вчера", "D"),
    ChatPreview("Mira", "Хорошо, договорились", "Вт", "M")
)

@Composable
private fun BecamceApp() {
    val context = LocalContext.current
    val authRepository = remember { SupabaseAuthRepository(context.applicationContext) }
    LaunchedEffect(authRepository) { authRepository.refreshSessionIfNeeded() }
    val session by authRepository.session.collectAsState()
    MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Panel, primary = Violet, onBackground = White, onSurface = White)) {
        if (session == null) AuthScreen(authRepository) else MessengerShell()
    }
}

@Composable
private fun MessengerShell() {
    var selectedTab by remember { mutableStateOf("Чаты") }
    var search by remember { mutableStateOf("") }
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
                            onClick = { selectedTab = route },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, selectedTextColor = Violet, indicatorColor = Color(0xFF28213A), unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { selectedTab = "Новый чат" }, containerColor = Violet, contentColor = Color.White, shape = CircleShape) {
                    Icon(Icons.Rounded.Add, contentDescription = "Новый чат")
                }
            }
        ) { padding ->
            if (selectedTab == "Чаты") ChatsScreen(Modifier.padding(padding), search) { search = it }
            else PlaceholderScreen(Modifier.padding(padding), selectedTab)
        }
    }
}

@Composable
private fun ChatsScreen(modifier: Modifier = Modifier, search: String, onSearchChange: (String) -> Unit) {
    val filtered = demoChats.filter { it.name.contains(search, true) || it.message.contains(search, true) }
    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171322), Night, Night))).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("becamce", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("ТВОЁ ПРОСТРАНСТВО ОБЩЕНИЯ", color = Muted, fontSize = 9.sp, letterSpacing = 1.6.sp)
            }
            IconButton(onClick = {}) { Icon(Icons.Rounded.Settings, contentDescription = "Настройки", tint = Muted) }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel.copy(alpha = .88f)).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted)
            TextField(
                value = search, onValueChange = onSearchChange, placeholder = { Text("Поиск чатов", color = Muted) }, singleLine = true,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = White, unfocusedTextColor = White, cursorColor = Violet),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("СООБЩЕНИЯ", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(filtered) { ChatRow(it) }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatPreview) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel.copy(alpha = .78f)).border(1.dp, Color.White.copy(alpha = .045f), RoundedCornerShape(20.dp)).clickable {}.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Violet, Color(0xFF44336E))),), contentAlignment = Alignment.Center) {
                Text(chat.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
            if (chat.online) Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF66D6A1)).border(2.dp, Panel, CircleShape))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(chat.message, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(chat.time, color = Muted, fontSize = 10.sp)
            if (chat.unread > 0) {
                Spacer(Modifier.height(7.dp))
                Box(Modifier.defaultMinSize(minWidth = 19.dp, minHeight = 19.dp).clip(CircleShape).background(Violet).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                    Text(chat.unread.toString(), color = Color.White, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(modifier: Modifier = Modifier, title: String) {
    Column(modifier.fillMaxSize().background(Night).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Раздел будет реализован на следующем этапе", color = Muted)
    }
}
