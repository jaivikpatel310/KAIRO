package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.overlay.OrbOverlayManager
import com.example.voice.IntentRouter
import com.example.voice.IntentType
import com.example.voice.ResponseTemplates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class KairoService : Service() {

    companion object {
        const val CHANNEL_ID = "kairo_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.kairo.ACTION_START"
        const val ACTION_STOP = "com.example.kairo.ACTION_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentStatus = MutableStateFlow("Standing by")
        val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, KairoService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KairoService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private enum class EngineState {
        IDLE,
        WAKE_WORD_LISTENING,
        COMMAND_LISTENING,
        SPEAKING,
        PAUSED
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var engineState = EngineState.IDLE
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private lateinit var overlayManager: OrbOverlayManager

    // Timeout runnable for follow-up command listening
    private val commandTimeoutRunnable = Runnable {
        if (engineState == EngineState.COMMAND_LISTENING) {
            handleNoCommandHeard()
        }
    }

    // Delayed restart for continuous listening loop
    private val restartListeningRunnable = Runnable {
        if (engineState == EngineState.WAKE_WORD_LISTENING) {
            startWakeWordListening()
        }
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        overlayManager = OrbOverlayManager(applicationContext)

        initNotificationChannel()
        startInForeground("Standing by. Say \"Wake up Kairo\"")
        initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (engineState == EngineState.IDLE || engineState == EngineState.PAUSED) {
                    mainHandler.post {
                        engineState = EngineState.WAKE_WORD_LISTENING
                        _currentStatus.value = "Listening for wake word..."
                        startWakeWordListening()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Foreground Notification
    // -------------------------------------------------------------------------

    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kairo Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Always-on on-device voice engine"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kairo")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startInForeground(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(statusText: String) {
        val notification = buildNotification(statusText)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    // -------------------------------------------------------------------------
    // Text To Speech
    // -------------------------------------------------------------------------

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                configureMaleVoice()
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                mainHandler.post { onSpeechCompleted(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { onSpeechCompleted(utteranceId) }
            }
        })
    }

    private fun configureMaleVoice() {
        val localTts = tts ?: return
        localTts.language = Locale.US

        // Tune rate & pitch for confident, calm, direct masculine presence
        localTts.setPitch(0.92f)
        localTts.setSpeechRate(1.02f)

        try {
            val voices = localTts.voices
            val maleVoice = voices?.firstOrNull { v ->
                v.locale.language.startsWith("en") &&
                        !v.isNetworkConnectionRequired &&
                        (v.name.lowercase().contains("male") || v.features.any { it.contains("male", ignoreCase = true) })
            } ?: voices?.firstOrNull { v ->
                v.locale.language.startsWith("en") &&
                        (v.name.lowercase().contains("male") || v.name.contains("#male", ignoreCase = true))
            }
            if (maleVoice != null) {
                localTts.voice = maleVoice
            }
        } catch (e: Exception) {
            // Safe fallback to default voice configured with lower pitch
        }
    }

    private fun speak(text: String, utteranceId: String) {
        if (!isTtsReady || tts == null) {
            onSpeechCompleted(utteranceId)
            return
        }
        engineState = EngineState.SPEAKING
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun onSpeechCompleted(utteranceId: String?) {
        if (utteranceId == "stop_listening") {
            engineState = EngineState.PAUSED
            _currentStatus.value = "Paused"
            overlayManager.hide()
            updateNotification("Paused. Tap to open Kairo.")
            return
        }

        // Return to continuous wake-word listening
        overlayManager.hide()
        engineState = EngineState.WAKE_WORD_LISTENING
        _currentStatus.value = "Listening for wake word..."
        updateNotification("Standing by. Say \"Wake up Kairo\"")
        scheduleWakeWordRestart(100L)
    }

    // -------------------------------------------------------------------------
    // Speech Recognition Loop
    // -------------------------------------------------------------------------

    private fun createRecognizerIfNeeded() {
        if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createRecognitionListener())
            }
        }
    }

    private fun startWakeWordListening() {
        if (engineState != EngineState.WAKE_WORD_LISTENING) return

        createRecognizerIfNeeded()
        val recognizer = speechRecognizer ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        try {
            recognizer.cancel()
            recognizer.startListening(intent)
        } catch (e: Exception) {
            scheduleWakeWordRestart(500L)
        }
    }

    private fun startCommandListening() {
        engineState = EngineState.COMMAND_LISTENING
        _currentStatus.value = "Listening for command..."
        updateNotification("Active — Listening to you...")

        // Show concentric pulsing orb system overlay
        overlayManager.show("Listening...", "KAIRO")

        // 7-second timeout for follow-up command
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.postDelayed(commandTimeoutRunnable, 7000L)

        createRecognizerIfNeeded()
        val recognizer = speechRecognizer ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        try {
            recognizer.cancel()
            recognizer.startListening(intent)
        } catch (e: Exception) {
            handleNoCommandHeard()
        }
    }

    private fun handleNoCommandHeard() {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        if (engineState != EngineState.COMMAND_LISTENING) return

        val response = ResponseTemplates.getResponse(IntentType.DID_NOT_HEAR)
        overlayManager.updateStatus("Didn't catch that", "KAIRO")
        speak(response, "did_not_hear")
    }

    private fun scheduleWakeWordRestart(delayMs: Long) {
        mainHandler.removeCallbacks(restartListeningRunnable)
        mainHandler.postDelayed(restartListeningRunnable, delayMs)
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (engineState == EngineState.COMMAND_LISTENING) {
                    overlayManager.updateRms(rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                when (engineState) {
                    EngineState.WAKE_WORD_LISTENING -> {
                        // Expected errors during silence/no-match in ambient listening
                        scheduleWakeWordRestart(200L)
                    }
                    EngineState.COMMAND_LISTENING -> {
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) {
                            handleNoCommandHeard()
                        } else {
                            // Retry or timeout
                            scheduleWakeWordRestart(300L)
                        }
                    }
                    else -> {}
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                when (engineState) {
                    EngineState.WAKE_WORD_LISTENING -> {
                        if (containsWakePhrase(matches)) {
                            // Wake word detected! Transition to follow-up command listening
                            startCommandListening()
                        } else {
                            scheduleWakeWordRestart(100L)
                        }
                    }
                    EngineState.COMMAND_LISTENING -> {
                        mainHandler.removeCallbacks(commandTimeoutRunnable)
                        val transcript = matches?.firstOrNull()?.trim() ?: ""
                        processCommandTranscript(transcript)
                    }
                    else -> {}
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                when (engineState) {
                    EngineState.WAKE_WORD_LISTENING -> {
                        if (containsWakePhrase(partials)) {
                            speechRecognizer?.cancel()
                            startCommandListening()
                        }
                    }
                    EngineState.COMMAND_LISTENING -> {
                        val partialText = partials?.firstOrNull() ?: ""
                        if (partialText.isNotBlank()) {
                            overlayManager.updateStatus(partialText, "LISTENING")
                        }
                    }
                    else -> {}
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun containsWakePhrase(matches: List<String>?): Boolean {
        if (matches == null) return false
        for (phrase in matches) {
            val lower = phrase.lowercase().trim()
            if (lower.contains("wake up kairo") ||
                lower.contains("wake up cairo") ||
                lower.contains("wake up kyro") ||
                lower.contains("kairo") ||
                lower.contains("hey kairo")
            ) {
                return true
            }
        }
        return false
    }

    private fun processCommandTranscript(transcript: String) {
        if (transcript.isBlank()) {
            handleNoCommandHeard()
            return
        }

        overlayManager.updateStatus("\"$transcript\"", "PROCESSING")
        val routeResult = IntentRouter.route(transcript)

        val utteranceId = if (routeResult.shouldPauseListening) "stop_listening" else "command_response"
        overlayManager.updateStatus(routeResult.spokenResponse, "KAIRO")
        speak(routeResult.spokenResponse, utteranceId)
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        engineState = EngineState.IDLE

        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.removeCallbacks(restartListeningRunnable)

        overlayManager.hide()

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
