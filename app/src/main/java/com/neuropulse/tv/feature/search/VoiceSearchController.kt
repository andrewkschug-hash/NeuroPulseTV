package com.neuropulse.tv.feature.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceSearchController @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: () -> Unit
    ) {
        stop()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    onError()
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results?.text()?.orEmpty() ?: return
                onFinal(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.text()?.orEmpty() ?: return
                onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        recognizer.startListening(intent)
    }

    fun stop() {
        runCatching { speechRecognizer?.stopListening() }
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun Bundle.text(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }
}
