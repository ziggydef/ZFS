package com.becamce.app.core.data

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.JavaAudioDeviceModule
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class VoiceCallEngine(
    context: Context,
    private val onLocalIceCandidate: (IceCandidate) -> Unit,
    private val onConnectionState: (String) -> Unit
) {
    private val audioDeviceModule: JavaAudioDeviceModule
    private val factory: PeerConnectionFactory
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private val peerConnection: PeerConnection
    private var closed = false

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions()
        )
        audioDeviceModule = JavaAudioDeviceModule.builder(context.applicationContext).createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                onConnectionState(state.name)
            }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) = onLocalIceCandidate(candidate)
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
        }) ?: error("Не удалось создать WebRTC-соединение")

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = factory.createAudioSource(constraints)
        audioTrack = factory.createAudioTrack("becamce-audio", audioSource)
        peerConnection.addTrack(audioTrack)
    }

    suspend fun createOffer(): String {
        val offer = createDescription { peerConnection.createOffer(it, MediaConstraints()) }
        setLocalDescription(offer)
        return offer.description
    }

    suspend fun acceptOfferAndCreateAnswer(offerSdp: String): String {
        setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        val answer = createDescription { peerConnection.createAnswer(it, MediaConstraints()) }
        setLocalDescription(answer)
        return answer.description
    }

    suspend fun applyAnswer(answerSdp: String) {
        setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
    }

    fun addRemoteCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        if (!closed) {
            peerConnection.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
        }
    }

    fun setMuted(muted: Boolean) {
        audioTrack.setEnabled(!muted)
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { peerConnection.close() }
        runCatching { peerConnection.dispose() }
        runCatching { audioTrack.dispose() }
        runCatching { audioSource.dispose() }
        runCatching { factory.dispose() }
        runCatching { audioDeviceModule.release() }
    }

    private suspend fun createDescription(
        create: (SdpObserver) -> Unit
    ): SessionDescription = suspendCancellableCoroutine { continuation ->
        create(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                if (continuation.isActive) continuation.resume(description)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
            }
            override fun onSetFailure(error: String) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
            }
        })
    }

    private suspend fun setLocalDescription(description: SessionDescription) {
        setDescription { peerConnection.setLocalDescription(it, description) }
    }

    private suspend fun setRemoteDescription(description: SessionDescription) {
        setDescription { peerConnection.setRemoteDescription(it, description) }
    }

    private suspend fun setDescription(set: (SdpObserver) -> Unit) {
        suspendCancellableCoroutine<Unit> { continuation ->
            set(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit
                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onCreateFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                }
                override fun onSetFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                }
            })
        }
    }
}
