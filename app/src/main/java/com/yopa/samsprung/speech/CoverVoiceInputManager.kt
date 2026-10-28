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
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
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
) : AccessibilityObserver.InputFocusListener {

    companion object {
        private const val TAG = "CoverVoiceInput"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Preferences.prefFloatingVoice) {
            val enabled = prefs.getBoolean(Preferences.prefFloatingVoice, true)
            Handler(Looper.getMainLooper()).post {
                if (enabled) {
                    attachFloatingButton()
                } else {
                    detachFloatingButton()
                }
            }
        }
    }

    init {
        try {
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
        } catch (_: Exception) {}
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
    private val effectHeavy = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var baseText = ""
    private var triedFallback = false

    // Floating Voice Bar View
    private var barView: View? = null
    private var micIcon: AppCompatImageView? = null
    private var backspaceIcon: AppCompatImageView? = null
    private var isBarAttached = false

    // Dragging state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun attachFloatingButton() {
        if (isBarAttached) return
        val enabled = prefs.getBoolean(Preferences.prefFloatingVoice, true)
        if (!enabled) return

        try {
            val view = LayoutInflater.from(context).inflate(R.layout.cover_voice_bar, null)

            micIcon = view.findViewById(R.id.bar_mic_icon)
            micIcon?.setImageResource(R.drawable.ic_mic_24dp)
            micIcon?.setColorFilter(Color.parseColor("#00E676"))

            backspaceIcon = view.findViewById(R.id.bar_backspace_icon)
            backspaceIcon?.setImageResource(R.drawable.ic_backspace_24dp)
            backspaceIcon?.setColorFilter(Color.WHITE)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Bottom-center, above the SamSprung nav bar (~64dp)
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                x = 0
                y = 72
            }

            // Dragging listener on the bar root
            view.setOnTouchListener { _, event ->
                handleDrag(view, params, event)
            }

            // 1. Mic button (🎙️)
            view.findViewById<View>(R.id.bar_button_mic).setOnClickListener {
                vibrateClick()
                if (isListening) {
                    stopListening()
                } else {
                    startListening()
                }
            }

            // 2. Backspace button (⌫): Tap = delete last word, Long-press = clear text
            val btnBackspace = view.findViewById<View>(R.id.bar_button_backspace)
            btnBackspace.setOnClickListener {
                vibrateClick()
                AccessibilityObserver.performBackspace()
                baseText = AccessibilityObserver.getCurrentInputText().trim()
            }
            btnBackspace.setOnLongClickListener {
                vibrateHeavy()
                AccessibilityObserver.performClear()
                baseText = ""
                true
            }

            // Always visible and fully opaque so user can always see it
            view.visibility = View.VISIBLE
            view.alpha = 1.0f

            windowManager.addView(view, params)
            barView = view
            isBarAttached = true

            // Register listener — if it fires, keep fully opaque
            AccessibilityObserver.setInputFocusListener(this)

            // Vibrate lightly so user knows bar appeared
            vibrateClick()

            Log.d(TAG, "Cover voice bar attached (always visible, size=126x64)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach cover voice bar", e)
        }
    }

    override fun onInputFocusChanged(isFocused: Boolean) {
        Handler(Looper.getMainLooper()).post {
            barView?.let { bar ->
                bar.visibility = View.VISIBLE
                bar.alpha = 1.0f
                if (isFocused) {
                    Log.d(TAG, "Input focused -> bar fully visible")
                } else {
                    Log.d(TAG, "Input unfocused -> bar still visible")
                }
            }
        }
    }

    private fun handleDrag(
        view: View,
        params: WindowManager.LayoutParams,
        event: MotionEvent
    ): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isMoving = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (abs(dx) > 10 || abs(dy) > 10) {
                    isMoving = true
                    params.x = initialX - dx
                    params.y = initialY - dy
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating voice bar layout", e)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isMoving) return true
            }
        }
        return false
    }

    fun detachFloatingButton() {
        AccessibilityObserver.setInputFocusListener(null)
        if (!isBarAttached) return
        stopListening()
        barView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing voice bar view", e)
            }
        }
        barView = null
        isBarAttached = false
    }

    fun showVoiceDialog(initialText: String? = null) {
        barView?.let {
            it.visibility = View.VISIBLE
            if (!isListening) {
                startListening()
            }
        }
    }

    fun dismissVoiceDialog() {
        stopListening()
        barView?.visibility = View.GONE
    }

    private fun startListening() {
        if (isListening) return
        com.yopa.samsprung.drawer.CoverScreenTimeoutManager.userActivity("voice_start")

        try {
            // Read currently existing text from WhatsApp
            baseText = AccessibilityObserver.getCurrentInputText().trim()

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, BuildConfig.APPLICATION_ID)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Use Indonesian as primary voice recognition language
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            updateMicUI(true)
            Log.d(TAG, "SpeechRecognizer started listening (id-ID)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer", e)
            updateMicUI(false)
        }
    }

    private fun startListeningFallback() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, BuildConfig.APPLICATION_ID)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            updateMicUI(true)
            Log.d(TAG, "SpeechRecognizer fallback listening started")
        } catch (e: Exception) {
            Log.e(TAG, "Fallback listening failed", e)
            updateMicUI(false)
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
        updateMicUI(false)
    }

    private fun updateMicUI(active: Boolean) {
        if (active) {
            micIcon?.setColorFilter(Color.parseColor("#FF5252"))
        } else {
            micIcon?.setColorFilter(Color.parseColor("#00E676"))
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateMicUI(true)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                updateMicUI(false)
                Log.w(TAG, "SpeechRecognizer onError: $error")

                // Error 13 = LANGUAGE_UNAVAILABLE -> Retry once with generic fallback intent
                if (error == 13 && !triedFallback) {
                    triedFallback = true
                    Handler(Looper.getMainLooper()).postDelayed({
                        startListeningFallback()
                    }, 150)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                updateMicUI(false)
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!data.isNullOrEmpty()) {
                    val spoken = data[0]?.trim() ?: ""
                    if (spoken.isNotEmpty()) {
                        val combined = if (baseText.isNotEmpty()) "$baseText $spoken" else spoken
                        val success = AccessibilityObserver.insertDirect(combined)
                        if (success) {
                            baseText = combined
                        }
                        Log.d(TAG, "Speech final result inserted: '$combined', success=$success")
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!data.isNullOrEmpty()) {
                    val partial = data[0]?.trim() ?: ""
                    if (partial.isNotEmpty()) {
                        val combined = if (baseText.isNotEmpty()) "$baseText $partial" else partial
                        AccessibilityObserver.insertDirect(combined)
                        Log.d(TAG, "Speech partial result inserted: '$combined'")
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

    private fun vibrateHeavy() {
        if (prefs.getBoolean(Preferences.prefReacts, true)) {
            vibrator.vibrate(effectHeavy)
        }
    }

    fun destroy() {
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (_: Exception) {}
        AccessibilityObserver.setInputFocusListener(null)
        detachFloatingButton()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
    }
}
