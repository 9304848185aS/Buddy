package com.example.buddy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import java.util.Locale

class BuddyListeningService : Service(), TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var actions: PhoneActions

    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isReady = false
    private var wakeDetected = false

    companion object {
        const val ACTION_START = "com.example.buddy.START_LISTENING"
        const val ACTION_STOP = "com.example.buddy.STOP_LISTENING"
        private const val CHANNEL_ID = "buddy_listening"
        private const val NOTIFICATION_ID = 101

        @Volatile
        var running = false
    }

    override fun onCreate() {
        super.onCreate()
        actions = PhoneActions(this)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("hi", "IN"))
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListeningService()
                return START_NOT_STICKY
            }
            else -> {
                createNotificationChannel()
                val notification = buildNotification()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                running = true
                if (!isListening) {
                    isListening = true
                    startRecognition()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Buddy voice assistant",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Buddy चालू है")
            .setContentText("आवाज़ सुनने की सुविधा सक्रिय है")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun startRecognition() {
        if (!isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("इस फोन में आवाज पहचानने की सुविधा उपलब्ध नहीं है")
            handler.postDelayed({ startRecognition() }, 3000)
            return
        }

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    scheduleNextListening()
                }

                override fun onResults(results: Bundle?) {
                    val spokenText = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.lowercase(Locale.ROOT)
                        ?.trim()

                    if (!spokenText.isNullOrBlank()) {
                        handleSpeech(spokenText)
                    } else {
                        scheduleNextListening()
                    }
                }
            })

            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            recognizer?.startListening(speechIntent)

        } catch (e: Exception) {
            scheduleNextListening()
        }
    }

    private fun handleSpeech(text: String) {
        if (!isListening) return

        // "रुक जा" कहने पर सर्विस बंद
        if (
            text.contains("रुक जा") ||
            text.contains("सुनना बंद") ||
            text.contains("stop listening")
        ) {
            speak("ठीक है, Buddy सुनना बंद कर रहा है")
            handler.postDelayed({ stopListeningService() }, 1800)
            return
        }

        // Wake phrase पहचानें
        val wakeWords = listOf(
            "हे बडी", "हे बड्डी", "है बडी", "है बड्डी",
            "हे buddy", "hey buddy"
        )
        val wakeWord = wakeWords.firstOrNull { text.contains(it) }

        if (wakeWord != null) {
            val commandText = text.replace(wakeWord, "").trim()
            if (commandText.isNotBlank()) {
                wakeDetected = false
                executeCommand(commandText)
            } else {
                // सिर्फ़ "हे बडी" कहा, अगला वाक्य कमांड माना जाएगा
                wakeDetected = true
                speak("हाँ, बताइए")
                scheduleNextListening()
            }
            return
        }

        // Wake phrase के बाद अगला वाक्य कमांड है
        if (wakeDetected) {
            wakeDetected = false
            executeCommand(text)
            return
        }

        // Wake phrase नहीं मिली, अनदेखा करके फिर सुनें
        scheduleNextListening()
    }

    private fun executeCommand(text: String) {
        when (val cmd = CommandParser.parse(text)) {
            is Command.Stop -> {
                speak("ठीक है, Buddy सुनना बंद कर रहा है")
                handler.postDelayed({ stopListeningService() }, 1800)
                return
            }
            is Command.Alarm -> speak(actions.setAlarm(cmd.hour, cmd.minute))
            is Command.Timer -> speak(actions.setTimer(cmd.seconds))
            is Command.OpenApp -> speak(actions.openApp(cmd.name))
            is Command.Unknown -> speak("मुझे ये निर्देश समझ नहीं आया")
        }
        scheduleNextListening(3000)
    }

    private fun scheduleNextListening(delayMs: Long = 1200) {
        if (!isListening) return
        handler.postDelayed({ startRecognition() }, delayMs)
    }

    private fun speak(text: String) {
        if (isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "buddy")
        }
    }

    private fun stopListeningService() {
        isListening = false
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isListening = false
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts.shutdown()
        super.onDestroy()
    }
}
