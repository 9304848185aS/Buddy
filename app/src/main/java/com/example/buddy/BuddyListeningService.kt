package com.example.buddy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.os.Build
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

class BuddyListeningService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingSpeech: String? = null
    private var afterSpeech: (() -> Unit)? = null
    private var restarting = false
    private var stopping = false
    private var pauseListening = false
    private var pendingActions: List<AiBrain.ActionStep> = emptyList()
    private var pendingActionIndex = 0
    private var wakeArmed = false
    private var wakeTimeout: Runnable? = null
    private val actionTimeoutHandler = Handler(Looper.getMainLooper())
    private var actionTimeout: Runnable? = null

    private fun clearPendingAction() {
        actionTimeout?.let { actionTimeoutHandler.removeCallbacks(it) }
        actionTimeout = null
        pendingActions = emptyList()
        pendingActionIndex = 0
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val stopIntent = Intent(this, BuddyListeningService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, "buddy_listening").setContentTitle("Buddy is listening")
                .setContentText("Voice control is active. Say stop or use the STOP button.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .addAction(android.R.drawable.ic_media_pause, "STOP", stopPendingIntent)
                .build()
        else Notification.Builder(this).setContentTitle("Buddy is listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPendingIntent)
            .build()
        startForeground(101, notification)
        tts = TextToSpeech(this, this)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            // Use Android's standard speech recognition service. Buddy does not
            // select or bundle a separate offline recognition engine.
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(this)
            }
            listen()
        } else stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("buddy_listening", "Buddy listening", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val engine = tts
            val hindiStatus = engine?.setLanguage(Locale("hi", "IN")) ?: TextToSpeech.ERROR
            if (hindiStatus == TextToSpeech.LANG_MISSING_DATA ||
                hindiStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine?.language = Locale.getDefault()
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { }
                override fun onDone(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post { finishSpeechTurn() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post { finishSpeechTurn() }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Handler(Looper.getMainLooper()).post { finishSpeechTurn() }
                }
            })
            val queued = pendingSpeech
            pendingSpeech = null
            if (!queued.isNullOrBlank()) speak(queued)
        } else {
            pendingSpeech = null
            pauseListening = false
            if (!stopping) listen()
        }
    }

    private fun listen() {
        if (restarting || stopping || pauseListening || recognizer == null) return
        restarting = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Keep a listening window open longer so quiet pauses do not trigger
            // rapid recognizer restarts (which cause repeated system beeps).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { restarting = false; stopSelf() }
    }

    private fun handle(text: String) {
        val wake = VoiceCommandMatcher.splitWakeWord(text)
        val raw = VoiceCommandMatcher.normalize(text)
        if (raw.isBlank() || stopping) return

        // Require the chosen wake name before acting. A standalone wake word
        // opens a short command window; "Buddy, open YouTube" works in one phrase.
        val hasWakePrefix = wake.recognized
        val command = if (hasWakePrefix) wake.command else raw

        if (hasWakePrefix && wake.wakeOnly) {
            wakeArmed = true
            wakeTimeout?.let { actionTimeoutHandler.removeCallbacks(it) }
            val timeout = Runnable { wakeArmed = false; wakeTimeout = null }
            wakeTimeout = timeout
            actionTimeoutHandler.postDelayed(timeout, 8000L)
            speak("जी, बताइए।")
            return
        }
        if (!hasWakePrefix && !wakeArmed) return
        wakeArmed = false
        wakeTimeout?.let { actionTimeoutHandler.removeCallbacks(it) }
        wakeTimeout = null
        val s = command.ifBlank { raw }
        if (VoiceCommandMatcher.isStopCommand(s)) {
            clearPendingAction()
            afterSpeech = null
            stopping = true
            speak("ठीक है, सुनना बंद कर रहा हूँ")
            Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 1500)
            return
        }
        val waiting = pendingActions.getOrNull(pendingActionIndex)
        if (waiting != null) {
            when {
                VoiceCommandMatcher.isCancellation(s) -> {
                    clearPendingAction()
                    speak("ठीक है, action रद्द कर दिया।")
                }
                VoiceCommandMatcher.isConfirmation(s) -> executePendingAction()
                else -> speak("इस action के लिए हाँ या नहीं बोलिए।")
            }
            return
        }
        when {
            VoiceCommandMatcher.isStopCommand(s) -> {
                stopping = true
                speak("ठीक है, सुनना बंद कर रहा हूँ")
                Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 1500)
            }
            s.contains("स्क्रीन पर क्या") || s.contains("स्क्रीन में क्या") ||
                s.contains("क्या दिख रहा") || s.contains("क्या लिखा है") ||
                s.contains("read screen") || s.contains("screen reading") ||
                s.contains("display पर क्या") || s.contains("डिस्प्ले पर क्या") ||
                s.contains("screen pe kya") -> {
                pauseListening = true
                BuddyTypingAccessibilityService.readScreen { visibleText -> speak(visibleText) }
            }
            s.contains("किसने message") || s.contains("किसका message") ||
                s.contains("किसने मैसेज") || s.contains("कौन है") ||
                s.contains("notification") || s.contains("notifications") ||
                s.contains("नोटिफिकेशन") || s.contains("मैसेज आया") ||
                s.contains("message आया") || s.contains("किसका संदेश") -> {
                val summary = BuddyNotificationListener.latestSummary()
                speak(summary)
            }
            s.contains("समय") || s.contains("time") || s.contains("टाइम") ||
                s.contains("कितने बजे") || s.contains("कितना बजा") ||
                s.contains("क्या समय") || (s.contains("क्या हुआ है") && (s.contains("टाइम") || s.contains("वक्त"))) -> {
                val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                speak("अभी समय है $now")
            }
            s.contains("तारीख") || s.contains("date") -> {
                val today = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("hi", "IN")).format(Date())
                speak("आज $today है")
            }
            s.contains("बैटरी") || s.contains("battery") -> {
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                speak("बैटरी " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " प्रतिशत है")
            }
            s.contains("आवाज बढ़ाओ") || s.contains("आवाज़ बढ़ाओ") ||
                s.contains("volume up") || s.contains("volume increase") ||
                s.contains("आवाज तेज") || s.contains("आवाज़ तेज") -> adjustMusicVolume(AudioManager.ADJUST_RAISE, "आवाज़ बढ़ा दी")
            s.contains("आवाज कम") || s.contains("आवाज़ कम") ||
                s.contains("volume down") || s.contains("volume decrease") -> adjustMusicVolume(AudioManager.ADJUST_LOWER, "आवाज़ कम कर दी")
            s == "mute" || s.contains("आवाज बंद") || s.contains("आवाज़ बंद") ||
                s.contains("mute volume") -> adjustMusicVolume(AudioManager.ADJUST_MUTE, "मीडिया आवाज़ mute कर दी")
            s == "unmute" || s.contains("आवाज चालू") || s.contains("आवाज़ चालू") ||
                s.contains("unmute volume") -> adjustMusicVolume(AudioManager.ADJUST_UNMUTE, "मीडिया आवाज़ चालू कर दी")
            s.contains("तुम क्या कर सकते") || s.contains("what can you do") ||
                s.contains("तुम्हारे काम") -> speak("मैं ऐप खोल सकता हूँ, समय तारीख और बैटरी बता सकता हूँ, अलार्म स्क्रीन खोल सकता हूँ, वेब search कर सकता हूँ, मीडिया आवाज़ बदल सकता हूँ और चुने हुए text box में आपकी अनुमति से text लिख सकता हूँ। Online AI के लिए AI settings चाहिए।")
            s.startsWith("type ") || s.startsWith("टाइप करो ") ||
                s.startsWith("लिखो ") || s.startsWith("लिख दो ") -> typeIntoFocusedField(s)
            s.contains("टाइमर") || s.contains("timer") || s.contains("countdown") -> setTimer(s)
            s.contains("अलार्म") || s.contains("alarm") -> setAlarm(s)
            s.contains("वाईफाई") || s.contains("wi-fi") || s.contains("wifi") -> openSettings(Settings.Panel.ACTION_WIFI, "वाई-फाई सेटिंग")
            s.contains("ब्लूटूथ") || s.contains("bluetooth") -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "ब्लूटूथ सेटिंग")
            s.contains("सेटिंग") || s.contains("settings") -> openSettings(Settings.ACTION_SETTINGS, "सेटिंग्स")
            s.contains("कैमरा") || s.contains("camera") -> openIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "कैमरा")
            s.contains("कॉल") || s.contains("फोन") || s.contains("dialer") -> openIntent(Intent(Intent.ACTION_DIAL), "फोन डायलर")
            s.contains("मैसेज") || s.contains("message") || s.contains("sms") -> openIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING), "मैसेज")
            s.contains("कॉन्टैक्ट") || s.contains("contacts") -> openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/")), "कॉन्टैक्ट")
            s.contains("मैप") || s.contains("maps") || s.contains("नेविगेशन") -> openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")), "मैप")
            s.contains("यूट्यूब") || s.contains("youtube") -> openApp("com.google.android.youtube", "YouTube")
            s.contains("क्रोम") || s.contains("chrome") || s.contains("ब्राउज़र") -> openApp("com.android.chrome", "Chrome")
            s.contains("जीमेल") || s.contains("gmail") -> openApp("com.google.android.gm", "Gmail")
            s.contains("प्ले स्टोर") || s.contains("play store") -> openApp("com.android.vending", "Play Store")
            s.contains("फोटो") || s.contains("गैलरी") || s.contains("gallery") || s.contains("photos") -> openApp("com.google.android.apps.photos", "Photos")
            s.contains("कैलकुलेटर") || s.contains("calculator") -> openIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR), "कैलकुलेटर")
            s.startsWith("search ") || s.startsWith("सर्च ") -> searchWeb(s)
            else -> openNamedAppOrReply(s)
        }
    }

    private fun typeIntoFocusedField(command: String) {
        val normalized = command.lowercase(Locale.ROOT)
        val text = when {
            normalized.startsWith("type ") -> command.substringAfter(" ").trim()
            normalized.startsWith("टाइप करो ") -> command.substring("टाइप करो ".length).trim()
            normalized.startsWith("लिख दो ") -> command.substring("लिख दो ".length).trim()
            else -> command.substring("लिखो ".length).trim()
        }
        if (text.isBlank()) {
            speak("जो लिखवाना है, वह बोलिए।")
            return
        }
        if (text.length > 1000) {
            speak("एक बार में अधिकतम एक हजार अक्षर लिख सकता हूँ।")
            return
        }
        pauseListening = true
        BuddyTypingAccessibilityService.typeIntoFocusedField(text) { success ->
            speak(if (success) "आपका text चुने हुए box में डाल दिया है। भेजने या search करने से पहले जाँच लें।"
                  else "पहले ऐप में text box पर tap करके cursor रखें और Buddy की Accessibility typing permission चालू करें।")
        }
    }

    private fun adjustMusicVolume(direction: Int, confirmation: String) {
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            speak(confirmation)
        } catch (_: Exception) {
            speak("मीडिया आवाज़ अभी नहीं बदल पाया।")
        }
    }

    private fun openRequestedWebsite(address: String) {
        if (!AgentToolRegistry.validate("OPEN_URL", address)) {
            speak("वेबसाइट का address सही नहीं है।")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(address.trim()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        openIntent(intent, "वेबसाइट")
    }

    private fun searchWeb(command: String) {
        val query = command.removePrefix("search").removePrefix("सर्च").trim()
        if (query.isBlank()) { speak("क्या search करना है?"); return }
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(packageManager) != null) openIntent(intent, "सर्च")
        else openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))), "सर्च")
    }

    private fun launchNamedApp(command: String): Boolean {
        val normalized = command.lowercase(Locale.ROOT)
            .replace("please", "")
            .replace("open", "")
            .replace("launch", "")
            .replace("खोलकर", "")
            .replace("खोलना", "")
            .replace("खोलो", "")
            .replace("खोल", "")
            .replace("चालू करो", "")
            .replace("चालू कर", "")
            .replace("ऐप", "")
            .replace("app", "")
            .replace("buddy", "")
            .trim()
            .replace(Regex("\\s+"), " ")

        if (normalized.isBlank()) return false
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = packageManager.queryIntentActivities(launcher, 0)
        val match = matches.firstOrNull { info ->
            info.loadLabel(packageManager).toString().trim().equals(normalized, ignoreCase = true)
        } ?: matches.firstOrNull { info ->
            val label = info.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
            label.contains(normalized) || normalized.contains(label)
        } ?: return false

        val launch = packageManager.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return false
        return try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
            speak(infoLabel(match.activityInfo.packageName))
            true
        } catch (_: Exception) {
            speak("यह ऐप अभी नहीं खुल पाया")
            true
        }
    }

    private fun openNamedAppOrReply(command: String) {
        if (launchNamedApp(command)) return
        when {
            command.contains("नमस्ते") || command.contains("hello") || command == "hi" -> speak("नमस्ते, बताइए मैं क्या मदद करूँ?")
            command.contains("तुम कौन") || command.contains("who are you") -> speak("मैं Buddy हूँ, आपका फोन voice assistant।")
            command.contains("धन्यवाद") || command.contains("thank you") -> speak("आपका स्वागत है")
            else -> requestAiPlannedAction(command)
        }
    }

    private fun requestAiPlannedAction(command: String) {
        pauseListening = true
        AiBrain.planAction(this, command) { plan ->
            if (plan.steps.isEmpty()) {
                AiBrain.ask(this, command) { answer -> speak(answer) }
                return@planAction
            }
            clearPendingAction()
            pendingActions = plan.steps
            pendingActionIndex = 0
            announcePendingAction()
        }
    }

    private fun announcePendingAction() {
        val step = pendingActions.getOrNull(pendingActionIndex) ?: run {
            clearPendingAction()
            speak("सुरक्षित steps पूरे हो गए।")
            return
        }
        val definition = AgentToolRegistry.find(step.action)
        if (definition?.requiresUserConfirmation == false) {
            executePendingAction()
            return
        }
        val description = when (step.action) {
            "OPEN_APP" -> "${step.value} ऐप खोलना"
            "OPEN_URL" -> "${step.value} वेबसाइट खोलना"
            "TYPE_TEXT" -> "चुने हुए text box में ${step.value} लिखना"
            "WEB_SEARCH" -> "${step.value} को वेब पर search करना"
            else -> "यह काम करना"
        }
        val stepNumber = pendingActionIndex + 1
        val total = pendingActions.size
        val timeout = Runnable {
            if (pendingActions.getOrNull(pendingActionIndex) === step) {
                clearPendingAction()
                speak("समय पूरा हुआ। सुरक्षा के लिए बाकी actions रद्द कर दिए।")
            }
        }
        actionTimeout = timeout
        actionTimeoutHandler.postDelayed(timeout, 15000L)
        speak("Step $stepNumber of $total: $description. करने के लिए हाँ बोलिए, रद्द करने के लिए नहीं।")
    }

    private fun executePendingAction() {
        val step = pendingActions.getOrNull(pendingActionIndex) ?: return
        // Defense in depth: re-check the allowlist at the execution boundary,
        // even though the planner output was already validated before queueing.
        // A queued action must never become executable merely because it was
        // present in memory; it must still be an explicitly registered tool.
        val definition = AgentToolRegistry.find(step.action)
        if (!AgentToolRegistry.validate(step.action, step.value) || definition == null) {
            clearPendingAction()
            afterSpeech = null
            speak("सुरक्षा जाँच में यह action मान्य नहीं रहा। इसे रद्द कर दिया।")
            return
        }
        actionTimeout?.let { actionTimeoutHandler.removeCallbacks(it) }
        actionTimeout = null
        pendingActionIndex += 1
        // Do not announce the next step until the current action's spoken
        // result has finished. This prevents TTS replies from interrupting
        // each other and keeps every step separately confirmed.
        afterSpeech = {
            if (!stopping) {
                if (pendingActionIndex < pendingActions.size) {
                    announcePendingAction()
                } else {
                    clearPendingAction()
                    speak("सभी सुरक्षित steps पूरे हो गए।")
                }
            }
        }
        when (step.action) {
            "OPEN_APP" -> if (!launchNamedApp(step.value)) speak("${step.value} ऐप नहीं मिला।")
            "OPEN_URL" -> openRequestedWebsite(step.value)
            "TYPE_TEXT" -> typeIntoFocusedField("type ${step.value}")
            "WEB_SEARCH" -> searchWeb("search ${step.value}")
            else -> speak("यह action उपलब्ध नहीं है।")
        }
    }

    private fun infoLabel(packageName: String): String {
        val label = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) { "ऐप" }
        return "$label खोल रहा हूँ"
    }

    private fun setAlarm(command: String) {
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?").find(command)
        if (match == null) { speak("कृपया समय बोलिए, जैसे अलार्म सात तीस"); return }
        val hourInput = match.groupValues[1].toIntOrNull() ?: return
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        if (hourInput !in 0..23 || minute !in 0..59) { speak("समय सही नहीं है"); return }
        val hour = if (command.contains("pm") && hourInput in 1..11) hourInput + 12 else if (command.contains("am") && hourInput == 12) 0 else hourInput
        val alarm = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Buddy alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        openIntent(alarm, "अलार्म")
    }

    private fun setTimer(command: String) {
        val match = Regex("(\\d{1,5})").find(command)
        val duration = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        val isSeconds = command.contains("सेकंड") || command.contains("second")
        val maximum = if (isSeconds) 86400 else 1440
        if (duration == null || duration !in 1..maximum) {
            speak(if (isSeconds) "सेकंड 1 से 86400 के बीच बताइए।" else "मिनट 1 से 1440 के बीच बताइए।")
            return
        }
        val seconds = if (isSeconds) duration else duration * 60
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Buddy timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        openIntent(intent, "टाइमर")
    }

    private fun openSettings(action: String, label: String) {
        openIntent(Intent(action), label)
    }

    private fun openIntent(intent: Intent, label: String) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                speak("$label खोल रहा हूँ")
            } else speak("$label इस फोन में उपलब्ध नहीं है")
        } catch (_: Exception) { speak("$label नहीं खुल पाया") }
    }

    private fun openApp(pkg: String, label: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
            speak("$label खोल रहा हूँ")
        } else speak("$label इस फोन में नहीं मिला")
    }

    private fun finishSpeechTurn() {
        if (stopping) return
        val continuation = afterSpeech
        afterSpeech = null
        if (continuation != null) {
            continuation()
        } else {
            pauseListening = false
            listen()
        }
    }

    private fun speak(message: String) {
        if (!ready || tts == null) {
            // Speech recognition can return a command before TextToSpeech finishes
            // initializing. Queue the reply instead of silently dropping it.
            pendingSpeech = message
            pauseListening = true
            return
        }
        pauseListening = true
        val result = tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "buddy_reply")
        if (result == TextToSpeech.ERROR) {
            finishSpeechTurn()
        }
    }

    override fun onReadyForSpeech(params: Bundle?) { restarting = false }
    override fun onBeginningOfSpeech() { }
    override fun onRmsChanged(rmsdB: Float) { }
    override fun onBufferReceived(buffer: ByteArray?) { }
    override fun onEndOfSpeech() { }
    override fun onError(error: Int) {
        restarting = false
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) stopSelf()
        else if (error != SpeechRecognizer.ERROR_CLIENT && !stopping && !pauseListening) {
            // Speech timeout/no-match can happen during a pause. Back off instead
            // of rapidly restarting every half-second and beeping over the user.
            val retryDelay = if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 2500L else 1500L
            Handler(Looper.getMainLooper()).postDelayed({ listen() }, retryDelay)
        }
    }
    override fun onResults(results: Bundle?) {
        restarting = false
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { handle(it) }
        if (!stopping && !pauseListening) listen()
    }
    override fun onPartialResults(partialResults: Bundle?) { }
    override fun onEvent(eventType: Int, params: Bundle?) { }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            clearPendingAction()
            afterSpeech = null
            stopping = true
            stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        // Never leave a delayed confirmation alive after the listening service stops.
        clearPendingAction()
        afterSpeech = null
        actionTimeoutHandler.removeCallbacksAndMessages(null)
        wakeTimeout = null
        wakeArmed = false
        stopping = true
        pauseListening = true
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
    companion object {
        private const val ACTION_STOP = "com.example.buddy.action.STOP_LISTENING"
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
