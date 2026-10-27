
package com.example.buddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private lateinit var actions: PhoneActions
    private var stopRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        log = findViewById(R.id.log)
        tts = TextToSpeech(this, this)
        actions = PhoneActions(this)

        // PermissionsActivity खोलने वाला नया कोड
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(this, PermissionsActivity::class.java)
            )
        }

        findViewById<Button>(R.id.talk).setOnClickListener {
            startListening()
        }

        val listenBtn = findViewById<Button>(R.id.listen)

        listenBtn.setOnClickListener {
            if (BuddyListeningService.running) {
                startService(
                    Intent(this, BuddyListeningService::class.java)
                        .setAction(BuddyListeningService.ACTION_STOP)
                )
                listenBtn.text = "हर वक्त सुनना चालू करो"
            } else {
                recognizer?.destroy()
                recognizer = null

                ContextCompat.startForegroundService(
                    this,
                    Intent(this, BuddyListeningService::class.java)
                        .setAction(BuddyListeningService.ACTION_START)
                )

                listenBtn.text = "सुनना बंद करो"
            }
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val perms = mutableListOf(
                Manifest.permission.RECORD_AUDIO
            )

            if (Build.VERSION.SDK_INT >= 33) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            ActivityCompat.requestPermissions(
                this,
                perms.toTypedArray(),
                1
            )
        }
    }

    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
        }
    }

    private fun say(text: String) {
        log.append("Buddy: $text\n")
        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "buddy"
        )
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("इस फ़ोन में आवाज़ पहचानने की सुविधा नहीं मिली")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {
                    status.text = "सुन रहा हूँ..."
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(v: Float) {}

                override fun onBufferReceived(b: ByteArray?) {}

                override fun onEndOfSpeech() {
                    status.text = "समझ रहा हूँ..."
                }

                override fun onError(e: Int) {
                    status.text = "Buddy तैयार है"
                    say("माफ़ कीजिए, मैं समझ नहीं पाया")
                }

                override fun onPartialResults(p: Bundle?) {}

                override fun onEvent(t: Int, p: Bundle?) {}

                override fun onResults(r: Bundle?) {
                    status.text = "Buddy तैयार है"

                    val text = r
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()

                    if (text != null) {
                        log.append("आप: $text\n")
                        handle(text)
                    }
                }
            }
        )

        val i = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "hi-IN"
            )
            putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                true
            )
        }

        recognizer?.startListening(i)
    }

    private fun handle(text: String) {
        val cmd = CommandParser.parse(text)

        when (cmd) {
            is Command.Stop -> {
                stopRequested = true
                say("ठीक है, रुक गया")
            }

            is Command.Alarm -> {
                say(actions.setAlarm(cmd.hour, cmd.minute))
            }

            is Command.Timer -> {
                say(actions.setTimer(cmd.seconds))
            }

            is Command.OpenApp -> {
                say(actions.openApp(cmd.name))
            }

            is Command.Unknown -> {
                say(
                    "मुझे ये निर्देश समझ नहीं आया। " +
                    "आप कह सकते हैं: सुबह 7 बजे का अलार्म लगा दो, " +
                    "या 5 मिनट का टाइमर लगा दो, या व्हाट्सऐप खोलो"
                )
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
