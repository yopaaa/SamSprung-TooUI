/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
 *
 * See https://github.com/SamSprung/.github/blob/main/LICENSE#L5
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.yopa.samsprung.speech

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.yopa.os.Version
import com.yopa.samsprung.AccessibilityObserver
import com.yopa.samsprung.BuildConfig
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.Preferences
import java.util.Locale
import kotlin.math.abs

class CoverVoiceInputManager(
    private val context: Context,
    private val windowManager: WindowManager
) {

    companion object {
        private const val TAG = "CoverVoiceInput"
        private const val LANG_ID = "id-ID"
        private const val LANG_EN = "en-US"

        private val DEFAULT_QUICK_REPLIES = listOf(
            "👍 OK",
            "Siap!",
            "Lagi di jalan 🚗",
            "Iya",
            "Tidak",
            "Nanti saya hubungi 📞",
            "Sampai di mana?",
            "Terima kasih 🙏"
        )
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)
    }

    private val vibrator: Vibrator by lazy {
        if (Version.isSnowCone) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    private val effectClick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var currentLanguage = LANG_ID

    // Views
    private var floatingButtonView: View? = null
    private var isFloatingAttached = false

    private var dialogView: View? = null
    private var isDialogShowing = false

    // Dialog components
    private var statusIcon: ImageView? = null
    private var statusText: TextView? = null
    private var langToggle: TextView? = null
    private var textResult: EditText? = null
    private var buttonClear: ImageView? = null
    private var actionMic: TextView? = null

    // Floating Button Position
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun attachFloatingButton() {
        if (isFloatingAttached) return
        val enabled = prefs.getBoolean(Preferences.prefFloatingVoice, true)
        if (!enabled) return

        try {
            val view = LayoutInflater.from(context).inflate(R.layout.floating_voice_button, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = 16
                y = 0
            }

            view.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isMoving = true
                            params.x = initialX - dx
                            params.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(view, params)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error updating floating mic layout", e)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoving) {
                            vibrateClick()
                            showVoiceDialog()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(view, params)
            floatingButtonView = view
            isFloatingAttached = true
            Log.d(TAG, "Floating voice button attached successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach floating voice button", e)
        }
    }

    fun detachFloatingButton() {
        if (!isFloatingAttached) return
        floatingButtonView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing floating mic view", e)
            }
        }
        floatingButtonView = null
        isFloatingAttached = false
    }

    @SuppressLint("InflateParams", "SetTextI18n")
    fun showVoiceDialog(initialText: String? = null) {
        if (isDialogShowing) return

        try {
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.cover_voice_input_dialog, null)

            statusIcon = view.findViewById(R.id.voice_status_icon)
            statusText = view.findViewById(R.id.voice_status_text)
            langToggle = view.findViewById(R.id.voice_lang_toggle)
            textResult = view.findViewById(R.id.voice_text_result)
            buttonClear = view.findViewById(R.id.voice_button_clear)
            actionMic = view.findViewById(R.id.voice_action_mic)

            initialText?.let { textResult?.setText(it) }

            // Lang toggle
            updateLangDisplay()
            langToggle?.setOnClickListener {
                vibrateClick()
                currentLanguage = if (currentLanguage == LANG_ID) LANG_EN else LANG_ID
                updateLangDisplay()
                restartListening()
            }

            // Close button
            view.findViewById<View>(R.id.voice_button_close).setOnClickListener {
                vibrateClick()
                dismissVoiceDialog()
            }

            // Clear button
            buttonClear?.setOnClickListener {
                vibrateClick()
                textResult?.setText("")
            }

            textResult?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    buttonClear?.isVisible = !s.isNullOrEmpty()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            // Populate Quick Replies
            val quickContainer = view.findViewById<LinearLayout>(R.id.voice_quick_replies_container)
            quickContainer.removeAllViews()
            for (reply in DEFAULT_QUICK_REPLIES) {
                val chip = TextView(context).apply {
                    text = reply
                    setTextColor(Color.WHITE)
                    textSize = 11.5f
                    setBackgroundResource(R.drawable.bg_quick_chip)
                    setPadding(
                        (10 * resources.displayMetrics.density).toInt(),
                        (5 * resources.displayMetrics.density).toInt(),
                        (10 * resources.displayMetrics.density).toInt(),
                        (5 * resources.displayMetrics.density).toInt()
                    )
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = (6 * resources.displayMetrics.density).toInt()
                    }
                    layoutParams = lp

                    setOnClickListener {
                        vibrateClick()
                        textResult?.setText(reply)
                        // Directly insert and offer quick convenience
                        AccessibilityObserver.insertText(reply, autoSend = false)
                        Toast.makeText(context, "Ditempel: $reply", Toast.LENGTH_SHORT).show()
                    }
                }
                quickContainer.addView(chip)
            }

            // Action: Mic Toggle
            actionMic?.setOnClickListener {
                vibrateClick()
                if (isListening) {
                    stopListening()
                } else {
                    startListening()
                }
            }

            // Action: Tempel (Paste without auto-send)
            view.findViewById<View>(R.id.voice_action_paste).setOnClickListener {
                vibrateClick()
                val text = textResult?.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    val success = AccessibilityObserver.insertText(text, autoSend = false)
                    Toast.makeText(
                        context,
                        if (success) "Teks dimasukkan!" else "Disalin ke clipboard!",
                        Toast.LENGTH_SHORT
                    ).show()
                    dismissVoiceDialog()
                } else {
                    Toast.makeText(context, "Katakan atau ketik teks terlebih dahulu", Toast.LENGTH_SHORT).show()
                }
            }

            // Action: Kirim (Insert and click Send)
            view.findViewById<View>(R.id.voice_action_send).setOnClickListener {
                vibrateClick()
                val text = textResult?.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    val success = AccessibilityObserver.insertText(text, autoSend = true)
                    Toast.makeText(
                        context,
                        if (success) "Terkirim!" else "Teks dimasukkan!",
                        Toast.LENGTH_SHORT
                    ).show()
                    dismissVoiceDialog()
                } else {
                    Toast.makeText(context, "Katakan atau ketik teks terlebih dahulu", Toast.LENGTH_SHORT).show()
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 0
            }

            windowManager.addView(view, params)
            dialogView = view
            isDialogShowing = true
            Log.d(TAG, "Voice dialog shown successfully")

            startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show voice dialog", e)
        }
    }

    fun dismissVoiceDialog() {
        if (!isDialogShowing) return
        stopListening()
        dialogView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing voice dialog view", e)
            }
        }
        dialogView = null
        isDialogShowing = false
    }

    private fun updateLangDisplay() {
        langToggle?.text = if (currentLanguage == LANG_ID) "🌐 ID" else "🌐 EN"
    }

    private fun restartListening() {
        stopListening()
        Handler(Looper.getMainLooper()).postDelayed({
            startListening()
        }, 200)
    }

    private fun startListening() {
        if (isListening) return

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, BuildConfig.APPLICATION_ID)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            updateListeningUI(true)
            statusText?.text = "Mendengarkan (${if (currentLanguage == LANG_ID) "Indonesia" else "English"})..."
            Log.d(TAG, "SpeechRecognizer started listening with lang: $currentLanguage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer", e)
            updateListeningUI(false)
            statusText?.text = "Gagal memulai mikrofon"
        }
    }

    private fun stopListening() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping speech recognizer", e)
        }
        isListening = false
        updateListeningUI(false)
        statusText?.text = "Selesai mendengarkan"
    }

    private fun updateListeningUI(active: Boolean) {
        if (active) {
            statusIcon?.setColorFilter(Color.parseColor("#FF5252"))
            actionMic?.text = "Berhenti"
            actionMic?.setTextColor(Color.parseColor("#FF5252"))
        } else {
            statusIcon?.setColorFilter(Color.WHITE)
            actionMic?.text = "Bicara"
            actionMic?.setTextColor(Color.WHITE)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText?.text = "Silakan bicara sekarang..."
                updateListeningUI(true)
            }

            override fun onBeginningOfSpeech() {
                statusText?.text = "Mendengarkan suara Anda..."
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                statusText?.text = "Memproses ucapan..."
            }

            override fun onError(error: Int) {
                isListening = false
                updateListeningUI(false)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Tidak ada suara terdeteksi. Ketuk 'Bicara' untuk coba lagi."
                    SpeechRecognizer.ERROR_NETWORK -> "Koneksi offline: pastikan paket bahasa offline sudah terunduh."
                    SpeechRecognizer.ERROR_AUDIO -> "Mikrofon tidak dapat diakses."
                    SpeechRecognizer.ERROR_SERVER -> "Server suara tidak merespon."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Waktu bicara habis. Ketuk 'Bicara' untuk coba lagi."
                    else -> "Suara tidak terdeteksi (kode $error)."
                }
                statusText?.text = msg
                Log.w(TAG, "SpeechRecognizer onError: $error ($msg)")
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                updateListeningUI(false)
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!data.isNullOrEmpty()) {
                    val spoken = data[0]
                    val existing = textResult?.text?.toString() ?: ""
                    val combined = if (existing.isNotEmpty()) "$existing $spoken" else spoken
                    textResult?.setText(combined)
                    textResult?.setSelection(combined.length)
                    statusText?.text = "Selesai (Siap dikirim)"
                } else {
                    statusText?.text = "Selesai"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!data.isNullOrEmpty()) {
                    val partial = data[0]
                    val existing = textResult?.text?.toString() ?: ""
                    // Only display preview if empty
                    if (existing.isEmpty() || existing == partial) {
                        textResult?.setText(partial)
                        textResult?.setSelection(partial.length)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun vibrateClick() {
        if (prefs.getBoolean(Preferences.prefReacts, true)) {
            vibrator.vibrate(effectClick)
        }
    }

    fun destroy() {
        dismissVoiceDialog()
        detachFloatingButton()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
    }
}
