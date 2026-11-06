package com.becamce.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.becamce.app.core.data.SupabaseCallRepository
import com.becamce.app.core.data.VoiceCallEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate

@Composable
internal fun VoiceCallScreen(
    modifier: Modifier = Modifier,
    repository: SupabaseCallRepository,
    currentUserId: String,
    peerTitle: String,
    outgoingChatId: String? = null,
    outgoingPeerId: String? = null,
    incomingCallId: String? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var callId by remember(incomingCallId) { mutableStateOf(incomingCallId) }
    var engine by remember { mutableStateOf<VoiceCallEngine?>(null) }
    var status by remember { mutableStateOf(if (incomingCallId == null) "Подготовка вызова…" else "Подключаемся…") }
    var error by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var answerApplied by remember { mutableStateOf(false) }
    val pendingLocalCandidates = remember { mutableStateListOf<IceCandidate>() }
    val seenRemoteCandidates = remember { mutableStateSetOf<String>() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted || started) return@LaunchedEffect
        started = true
        runCatching {
            val rtc = VoiceCallEngine(
                context = context,
                onLocalIceCandidate = { candidate ->
                    scope.launch {
                        val id = callId
                        if (id == null) pendingLocalCandidates.add(candidate)
                        else repository.sendCandidate(id, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                            .onFailure { error = it.message ?: "Не удалось отправить ICE-кандидат" }
                    }
                },
                onConnectionState = { state ->
                    status = when (state) {
                        "CONNECTED", "COMPLETED" -> "На связи"
                        "CONNECTING", "CHECKING" -> "Соединяем…"
                        "DISCONNECTED" -> "Связь прервалась"
                        "FAILED" -> "Не удалось соединиться"
                        "CLOSED" -> "Звонок завершён"
                        else -> "Вызов"
                    }
                }
            )
            engine = rtc

            if (incomingCallId != null) {
                val call = repository.getCall(incomingCallId).getOrThrow()
                rtc.acceptOfferAndCreateAnswer(call.offerSdp).let { answer ->
                    repository.acceptCall(incomingCallId, answer).getOrThrow()
                }
                status = "Соединяем…"
            } else {
                val chatId = requireNotNull(outgoingChatId)
                val peerId = requireNotNull(outgoingPeerId)
                status = "Вызов…"
                val offer = rtc.createOffer()
                callId = repository.createCall(chatId, peerId, offer).getOrThrow()
            }

            val createdId = callId
            if (createdId != null && pendingLocalCandidates.isNotEmpty()) {
                pendingLocalCandidates.toList().forEach { candidate ->
                    repository.sendCandidate(createdId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                        .onFailure { error = it.message ?: "Не удалось отправить ICE-кандидат" }
                }
                pendingLocalCandidates.clear()
            }
        }.onFailure {
            error = it.message ?: "Не удалось начать звонок"
            status = "Ошибка вызова"
        }
    }

    LaunchedEffect(callId, engine) {
        val id = callId ?: return@LaunchedEffect
        val rtc = engine ?: return@LaunchedEffect
        while (true) {
            repository.getCall(id).onSuccess { call ->
                if (call.status == "rejected" || call.status == "ended" || call.status == "missed") {
                    status = if (call.status == "rejected") "Вызов отклонён" else "Звонок завершён"
                } else if (call.callerId == currentUserId && call.answerSdp != null && !answerApplied) {
                    rtc.applyAnswer(call.answerSdp)
                    answerApplied = true
                    status = "Соединяем…"
                }
            }.onFailure { error = it.message ?: "Потеряна связь с сервером звонков" }

            repository.remoteCandidates(id, seenRemoteCandidates).onSuccess { candidates ->
                candidates.forEach { candidate ->
                    rtc.addRemoteCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate)
                    seenRemoteCandidates.add(candidate.id)
                }
            }.onFailure { error = it.message ?: "Не удалось получить данные соединения" }

            delay(900L)
        }
    }

    DisposableEffect(engine) {
        onDispose { engine?.close() }
    }

    Column(
        modifier.fillMaxSize().background(Color(0xFF09090E)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(116.dp).background(Color(0xFF30234A), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(peerTitle.take(1).uppercase(), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(22.dp))
        Text(peerTitle, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(9.dp))
        Text(
            when {
                permissionDenied -> "Нужен доступ к микрофону"
                error != null -> error!!
                else -> status
            },
            color = if (error != null || permissionDenied) Color(0xFFFF9BA8) else Color(0xFFAAA5BA),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(64.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    muted = !muted
                    engine?.setMuted(muted)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282333)),
                shape = CircleShape
            ) {
                Icon(if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, contentDescription = "Микрофон")
                Text(if (muted) "Вкл." else "Выкл.")
            }
            Button(
                onClick = {
                    scope.launch {
                        callId?.let { repository.setStatus(it, "ended") }
                        engine?.close()
                        onClose()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84C60)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = "Завершить звонок")
                Text("Завершить")
            }
        }
    }
}
