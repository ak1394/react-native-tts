package net.no_mad.tts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class TextToSpeechModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var tts: TextToSpeech? = null
    private var ready: Boolean? = null
    private var initStatusPromises: ArrayList<Promise> = ArrayList()
    private var ducking = false
    private val audioManager = reactContext.applicationContext.getSystemService(ReactApplicationContext.AUDIO_SERVICE) as AudioManager
    private lateinit var afChangeListener: AudioManager.OnAudioFocusChangeListener

    private var localeCountryMap: Map<String, Locale> = HashMap()
    private var localeLanguageMap: Map<String, Locale> = HashMap()

    init {
        initCountryLanguageCodeMapping()

        tts = TextToSpeech(reactContext) { status ->
            synchronized(initStatusPromises) {
                ready = (status == TextToSpeech.SUCCESS)
                initStatusPromises.forEach { resolveReadyPromise(it) }
                initStatusPromises.clear()
            }
        }

        setUtteranceProgress()
    }

    private fun setUtteranceProgress() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                sendEvent("tts-start", utteranceId)
            }

            override fun onDone(utteranceId: String) {
                if (ducking) {
                    audioManager.abandonAudioFocus(afChangeListener)
                }
                sendEvent("tts-finish", utteranceId)
            }

            override fun onError(utteranceId: String) {
                if (ducking) {
                    audioManager.abandonAudioFocus(afChangeListener)
                }
                sendEvent("tts-error", utteranceId)
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                if (ducking) {
                    audioManager.abandonAudioFocus(afChangeListener)
                }
                sendEvent("tts-cancel", utteranceId)
            }

            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                val params = Arguments.createMap().apply {
                    putString("utteranceId", utteranceId)
                    putInt("start", start)
                    putInt("end", end)
                    putInt("frame", frame)
                }
                sendEvent("tts-progress", params)
            }
        })
    }

    private fun initCountryLanguageCodeMapping() {
        Locale.getISOCountries().forEach { country ->
            localeCountryMap = localeCountryMap.plus(Pair(Locale("", country).isO3Country.toUpperCase(), Locale("", country)))
        }
        Locale.getISOLanguages().forEach { language ->
            localeLanguageMap = localeLanguageMap.plus(Pair(Locale(language).isO3Language, Locale(language)))
        }
    }

    private fun iso3CountryCodeToIso2CountryCode(iso3CountryCode: String): String {
        return localeCountryMap[iso3CountryCode]?.country ?: ""
    }

    private fun iso3LanguageCodeToIso2LanguageCode(iso3LanguageCode: String): String {
        return localeLanguageMap[iso3LanguageCode]?.language ?: ""
    }

    private fun resolveReadyPromise(promise: Promise) {
        if (ready == true) {
            promise.resolve("success")
        } else {
            promise.reject("no_engine", "No TTS engine installed")
        }
    }

    private fun resolvePromiseWithStatusCode(statusCode: Int, promise: Promise) {
        when (statusCode) {
            TextToSpeech.SUCCESS -> promise.resolve("success")
            TextToSpeech.LANG_COUNTRY_AVAILABLE -> promise.resolve("lang_country_available")
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> promise.resolve("lang_country_var_available")
            TextToSpeech.ERROR_INVALID_REQUEST -> promise.reject("invalid_request", "Failure caused by an invalid request")
            TextToSpeech.ERROR_NETWORK -> promise.reject("network_error", "Failure caused by a network connectivity problems")
            TextToSpeech.ERROR_NETWORK_TIMEOUT -> promise.reject("network_timeout", "Failure caused by network timeout.")
            TextToSpeech.ERROR_NOT_INSTALLED_YET -> promise.reject("not_installed_yet", "Unfinished download of voice data")
            TextToSpeech.ERROR_OUTPUT -> promise.reject("output_error", "Failure related to the output (audio device or a file)")
            TextToSpeech.ERROR_SERVICE -> promise.reject("service_error", "Failure of a TTS service")
            TextToSpeech.ERROR_SYNTHESIS -> promise.reject("synthesis_error", "Failure of a TTS engine to synthesize the given input")
            TextToSpeech.LANG_MISSING_DATA -> promise.reject("lang_missing_data", "Language data is missing")
            TextToSpeech.LANG_NOT_SUPPORTED -> promise.reject("lang_not_supported", "Language is not supported")
            else -> promise.reject("error", "Unknown error code: $statusCode")
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        val pm = reactApplicationContext.packageManager
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun getName(): String {
        return "TextToSpeech"
    }

    @ReactMethod
    fun getInitStatus(promise: Promise) {
        synchronized(initStatusPromises) {
            if (ready == null) {
                initStatusPromises.add(promise)
            } else {
                resolveReadyPromise(promise)
            }
        }
    }

    @ReactMethod
    fun speak(utterance: String, params: ReadableMap, promise: Promise) {
        if (notReady(promise)) return

        if (ducking) {
            val amResult = audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )

            if (amResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                promise.reject("Android AudioManager error, failed to request audio focus")
                return
            }
        }

        val utteranceId = utterance.hashCode().toString()

        val speakResult = speak(utterance, utteranceId, params)
        if (speakResult == TextToSpeech.SUCCESS) {
            promise.resolve(utteranceId)
        } else {
            resolvePromiseWithStatusCode(speakResult, promise)
        }
    }

    @ReactMethod
    fun setDefaultLanguage(language: String, promise: Promise) {
        if (notReady(promise)) return

        val locale = if (language.contains("-")) {
            val parts = language.split("-")
            Locale(parts[0], parts[1])
        } else {
            Locale(language)
        }

        try {
            val result = tts?.setLanguage(locale)
            resolvePromiseWithStatusCode(result ?: -1, promise)
        } catch (e: Exception) {
            promise.reject("error", "Unknown error code")
        }
    }

    @ReactMethod
    fun setDucking(ducking: Boolean, promise: Promise) {
        if (notReady(promise)) return
        this.ducking = ducking
        promise.resolve("success")
    }

    @ReactMethod
    fun setDefaultRate(rate: Float, skipTransform: Boolean, promise: Promise) {
        if (notReady(promise)) return

        if (skipTransform) {
            val result = tts?.setSpeechRate(rate)
            resolvePromiseWithStatusCode(result ?: -1, promise)
        } else {
            val androidRate = if (rate < 0.5f) rate * 2 else rate * 4 - 1
            val result = tts?.setSpeechRate(androidRate)
            resolvePromiseWithStatusCode(result ?: -1, promise)
        }
    }

    @ReactMethod
    fun setDefaultPitch(pitch: Float, promise: Promise) {
        if (notReady(promise)) return
        val result = tts?.setPitch(pitch)
        resolvePromiseWithStatusCode(result ?: -1, promise)
    }

    @ReactMethod
    fun setDefaultVoice(voiceId: String, promise: Promise) {
        if (notReady(promise)) return

        if (Build.VERSION.SDK_INT >= 21) {
            try {
                tts?.voices?.forEach { voice ->
                    if (voice.name == voiceId) {
                        val result = tts?.setVoice(voice)
                        resolvePromiseWithStatusCode(result ?: -1, promise)
                        return
                    }
                }
            } catch (e: Exception) {
                // Purposefully ignore exceptions here due to some buggy TTS engines.
                // See http://stackoverflow.com/questions/26730082/illegalargumentexception-invalid-int-os-with-samsung-tts
            }
            promise.reject("not_found", "The selected voice was not found")
        } else {
            promise.reject("not_available", "Android API 21 level or higher is required")
        }
    }

    @ReactMethod
    fun voices(promise: Promise) {
        if (notReady(promise)) return

        val voiceArray = Arguments.createArray()

        if (Build.VERSION.SDK_INT >= 21) {
            try {
                tts?.voices?.forEach { voice ->
                    val voiceMap = Arguments.createMap().apply {
                        putString("id", voice.name)
                        putString("name", voice.name)

                        var language = iso3LanguageCodeToIso2LanguageCode(voice.locale.isO3Language)
                        val country = voice.locale.isO3Country
                        if (country.isNotEmpty()) {
                            language += "-${iso3CountryCodeToIso2CountryCode(country)}"
                        }

                        putString("language", language)
                        putInt("quality", voice.quality)
                        putInt("latency", voice.latency)
                        putBoolean("networkConnectionRequired", voice.isNetworkConnectionRequired)
                        putBoolean("notInstalled", voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
                    }
                    voiceArray.pushMap(voiceMap)
                }
            } catch (e: Exception) {
                // Purposefully ignore exceptions here due to some buggy TTS engines.
                // See http://stackoverflow.com/questions/26730082/illegalargumentexception-invalid-int-os-with-samsung-tts
            }
        }

        promise.resolve(voiceArray)
    }

    @ReactMethod
    fun setDefaultEngine(engineName: String, promise: Promise) {
        if (notReady(promise)) return

        if (isPackageInstalled(engineName)) {
            ready = null
            onCatalystInstanceDestroy()
            tts = TextToSpeech(reactApplicationContext) { status ->
                synchronized(initStatusPromises) {
                    ready = (status == TextToSpeech.SUCCESS)
                    initStatusPromises.forEach { resolveReadyPromise(it) }
                    initStatusPromises.clear()
                    promise.resolve(ready)
                }
            }

            setUtteranceProgress()
        } else {
            promise.reject("not_found", "The selected engine was not found")
        }
    }

    @ReactMethod
    fun engines(promise: Promise) {
        if (notReady(promise)) return

        val engineArray = Arguments.createArray()

        if (Build.VERSION.SDK_INT >= 14) {
            try {
                val defaultEngineName = tts?.defaultEngine
                tts?.engines?.forEach { engine ->
                    val engineMap = Arguments.createMap().apply {
                        putString("name", engine.name)
                        putString("label", engine.label)
                        putBoolean("default", engine.name == defaultEngineName)
                        putInt("icon", engine.icon)
                    }
                    engineArray.pushMap(engineMap)
                }
            } catch (e: Exception) {
                promise.reject("error", "Unknown error code")
            }
        }

        promise.resolve(engineArray)
    }

    @ReactMethod
    fun stop(promise: Promise) {
        if (notReady(promise)) return

        val result = tts?.stop() ?: TextToSpeech.ERROR
        val resultValue = result == TextToSpeech.SUCCESS
        promise.resolve(resultValue)
    }

    @ReactMethod
    private fun requestInstallEngine(promise: Promise) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=com.google.android.tts")
        }
        try {
            currentActivity?.startActivity(intent)
            promise.resolve("success")
        } catch (e: Exception) {
            promise.reject("error", "Could not open Google Text to Speech App in the Play Store")
        }
    }

    @ReactMethod
    private fun requestInstallData(promise: Promise) {
        val intent = Intent().apply {
            action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
        }
        try {
            currentActivity?.startActivity(intent)
            promise.resolve("success")
        } catch (e: ActivityNotFoundException) {
            promise.reject("no_engine", "No TTS engine installed")
        }
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        tts?.apply {
            stop()
            shutdown()
        }
    }

    private fun notReady(promise: Promise): Boolean {
        return when {
            ready == null -> {
                promise.reject("not_ready", "TTS is not ready")
                true
            }
            ready != true -> {
                resolveReadyPromise(promise)
                true
            }
            else -> false
        }
    }

    @Suppress("DEPRECATION")
    private fun speak(utterance: String, utteranceId: String, inputParams: ReadableMap): Int {
        val audioStreamTypeString = inputParams.getString("KEY_PARAM_STREAM") ?: ""
        val volume = inputParams.getDouble("KEY_PARAM_VOLUME").toFloat()
        val pan = inputParams.getDouble("KEY_PARAM_PAN").toFloat()

        val audioStreamType = when (audioStreamTypeString) {
            "STREAM_ALARM" -> AudioManager.STREAM_ALARM
            "STREAM_DTMF" -> AudioManager.STREAM_DTMF
            "STREAM_MUSIC" -> AudioManager.STREAM_MUSIC
            "STREAM_NOTIFICATION" -> AudioManager.STREAM_NOTIFICATION
            "STREAM_RING" -> AudioManager.STREAM_RING
            "STREAM_SYSTEM" -> AudioManager.STREAM_SYSTEM
            "STREAM_VOICE_CALL" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.USE_DEFAULT_STREAM_TYPE
        }

        return if (Build.VERSION.SDK_INT >= 21) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStreamType)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, pan)
            }
            tts?.speak(utterance, TextToSpeech.QUEUE_ADD, params, utteranceId) ?: TextToSpeech.ERROR
        } else {
            val params = HashMap<String, String>().apply {
                put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                put(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStreamType.toString())
                put(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.toString())
                put(TextToSpeech.Engine.KEY_PARAM_PAN, pan.toString())
            }
            tts?.speak(utterance, TextToSpeech.QUEUE_ADD, params) ?: TextToSpeech.ERROR
        }
    }

    private fun sendEvent(eventName: String, utteranceId: String) {
        val params = Arguments.createMap().apply {
            putString("utteranceId", utteranceId)
        }
        sendEvent(eventName, params)
    }

    private fun sendEvent(eventName: String, params: WritableMap) {
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    /**
     * Update to React Native 0.74.2
     */
    @ReactMethod
    fun removeListeners(count: Int) {
        // Keep: Required for RN built in Event Emitter Calls.
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Implement your logic to add the listener
        // You can store the handler in a Map or List if necessary
    }

    @ReactMethod
    fun removeListener(eventName: String) {
        // Implement your logic to remove the listener
        // You can remove the handler from the Map or List if necessary
    }
}
