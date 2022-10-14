package net.no_mad.tts;

import android.annotation.SuppressLint;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TextToSpeechModule extends ReactContextBaseJavaModule {

    private static final String TAG = "TextToSpeechModule";

    private TextToSpeech tts;
    private boolean ready = false;
    private ArrayList<Promise> initStatusPromises;

    private boolean ducking = false;
    private int audioFocus = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private boolean useAudioFocus = true;

    private boolean forcePhoneSpeaker;
    private boolean isCarAudioSystem;

    private Map<String, Locale> localeCountryMap;
    private Map<String, Locale> localeLanguageMap;

    private String currentEngineName;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Condition conditionStopped = lock.newCondition();
    private boolean isTtsIdle = true;
    private boolean stopRequest = false;
    private String playingUtteranceId = "";
    private String playingUtterance = "";

    private static final long UNITY_GAIN_Q8p24 = (1 << 24);
    private static final long audioGainClipRegion = (long) (0.9441 * UNITY_GAIN_Q8p24); // -0.5dB = 0.9441 (10 ^ (-0.5/20))
    private long audioGain = (long) (1.6788 * UNITY_GAIN_Q8p24); // 4.5dB = 1.6788 (10 ^ (4.5/20))
    private long audioGainMax = (long) (2.8184 * UNITY_GAIN_Q8p24); // 9dB = 2.8184 (10 ^ (9/20))
    private long audioGainMin = (long) (1.0 * UNITY_GAIN_Q8p24); // 0dB = 1.0 (10 ^ (0/20))
    private long largestSample = 0;
    private AudioTrack audioTrack;

    private static final boolean enableTestCode = false;
    private int clippedSamplesCount = 0;
    private int sampleCount = 0;

    public TextToSpeechModule(ReactApplicationContext reactContext) {
        super(reactContext);
        audioManager = (AudioManager) reactContext.getApplicationContext().getSystemService(reactContext.AUDIO_SERVICE);
        audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                if (useAudioFocus && focusChange < 0) {
                    // Loss of Focus, stop TTS which will abandon focus (TTS should not be restarted)
                    stop();
                }
            }
        };

        initStatusPromises = new ArrayList<Promise>();
        //initialize ISO3, ISO2 languague country code mapping.
        initCountryLanguageCodeMapping();

        tts = new TextToSpeech(getReactApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                synchronized (initStatusPromises) {
                    ready = (status == TextToSpeech.SUCCESS);
                    for (Promise p : initStatusPromises) {
                        resolveReadyPromise(p);
                    }
                    initStatusPromises.clear();
                }
            }
        });
        currentEngineName = tts.getDefaultEngine();

        setInitialAudioGain();
        setUtteranceProgress();
    }

    private void setUtteranceProgress() {
        if (tts != null) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onBeginSynthesis(String utteranceId, int sampleRateInHz, int audioFormat, int channelCount) {
                    lock.lock();
                    if (stopRequest) {
                        lock.unlock();
                        return;
                    }
                    lock.unlock();
                    int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, audioFormat, AudioFormat.ENCODING_PCM_16BIT);
                    if (bufferSizeInBytes == AudioTrack.ERROR_BAD_VALUE || bufferSizeInBytes == AudioTrack.ERROR) {
                        bufferSizeInBytes = 4096;
                    }

                    int audioTrackUsage = AudioAttributes.USAGE_MEDIA;

                    if (isCarAudioSystem) {
                        audioTrackUsage = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
                    }

                    AudioAttributes audioTrackAudioAttributes = new AudioAttributes.Builder()
                            .setUsage(audioTrackUsage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();

                    AudioFormat audioTrackAudioFormat = new AudioFormat.Builder()
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setSampleRate(sampleRateInHz)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build();

                    lock.lock();
                    try {
                        audioTrack = new AudioTrack(audioTrackAudioAttributes, audioTrackAudioFormat, bufferSizeInBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
                        if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                            tts.stop();
                        } else {
                            try {
                                if (forcePhoneSpeaker) {
                                    forceSpeakerRoute(audioTrack);
                                }
                                audioTrack.play();
                            } catch (IllegalStateException e) {
                                tts.stop();
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        tts.stop();
                    } finally {
                        lock.unlock();
                    }
                }

                @Override
                public void onStart(String utteranceId) {
                    sendEvent("tts-start", utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    audioDone(false);
                    sendEvent("tts-finish", utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    audioDone(false);
                    sendEvent("tts-error", utteranceId);
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    audioDone(false);
                    sendEvent("tts-cancel", utteranceId);
                }

                @Override
                public void onAudioAvailable(String utteranceId, byte[] audio) {
                    lock.lock();
                    if (stopRequest) {
                        lock.unlock();
                        return;
                    }
                    if (audioTrack != null) {
                        processAudio(audio);

                        int offset = 0;
                        while (offset < audio.length) {
                            int written = audioTrack.write(audio, offset, audio.length - offset);
                            if (written <= 0) {
                                tts.stop();
                                break;
                            }
                            offset += written;
                        }
                    } else {
                        tts.stop();
                    }
                    lock.unlock();

                    if (enableTestCode) {
                        writeAudioToFile(audio);
                    }
                }

                @Override
                public void onRangeStart(String utteranceId, int start, int end, int frame) {
                    WritableMap params = Arguments.createMap();
                    params.putString("utteranceId", utteranceId);
                    params.putInt("start", start);
                    params.putInt("end", end);
                    params.putInt("frame", frame);
                    sendEvent("tts-progress", params);
                }
            });
        }
    }

    private void initCountryLanguageCodeMapping() {
        String[] countries = Locale.getISOCountries();
        localeCountryMap = new HashMap<String, Locale>(countries.length);
        for (String country : countries) {
            Locale locale = new Locale("", country);
            localeCountryMap.put(locale.getISO3Country().toUpperCase(), locale);
        }
        String[] languages = Locale.getISOLanguages();
        localeLanguageMap = new HashMap<String, Locale>(languages.length);
        for (String language : languages) {
            Locale locale = new Locale(language);
            localeLanguageMap.put(locale.getISO3Language(), locale);
        }
    }

    private String iso3CountryCodeToIso2CountryCode(String iso3CountryCode) {
        return localeCountryMap.get(iso3CountryCode).getCountry();
    }

    private String iso3LanguageCodeToIso2LanguageCode(String iso3LanguageCode) {
        return localeLanguageMap.get(iso3LanguageCode).getLanguage();
    }

    private void resolveReadyPromise(Promise promise) {
        if (ready) {
            promise.resolve("success");
        } else {
            promise.reject("not_ready", "TTS is not ready");
        }
    }

    private static void resolvePromiseWithStatusCode(int statusCode, Promise promise) {
        switch (statusCode) {
            case TextToSpeech.SUCCESS:
                promise.resolve("success");
                break;
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                promise.resolve("lang_country_available");
                break;
            case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                promise.resolve("lang_country_var_available");
                break;
            case TextToSpeech.ERROR_INVALID_REQUEST:
                promise.reject("invalid_request", "Failure caused by an invalid request");
                break;
            case TextToSpeech.ERROR_NETWORK:
                promise.reject("network_error", "Failure caused by a network connectivity problems");
                break;
            case TextToSpeech.ERROR_NETWORK_TIMEOUT:
                promise.reject("network_timeout", "Failure caused by network timeout.");
                break;
            case TextToSpeech.ERROR_NOT_INSTALLED_YET:
                promise.reject("not_installed_yet", "Unfinished download of voice data");
                break;
            case TextToSpeech.ERROR_OUTPUT:
                promise.reject("output_error", "Failure related to the output (audio device or a file)");
                break;
            case TextToSpeech.ERROR_SERVICE:
                promise.reject("service_error", "Failure of a TTS service");
                break;
            case TextToSpeech.ERROR_SYNTHESIS:
                promise.reject("synthesis_error", "Failure of a TTS engine to synthesize the given input");
                break;
            case TextToSpeech.LANG_MISSING_DATA:
                promise.reject("lang_missing_data", "Language data is missing");
                break;
            case TextToSpeech.LANG_NOT_SUPPORTED:
                promise.reject("lang_not_supported", "Language is not supported");
                break;
            default:
                promise.reject("error", "Unknown error code: " + statusCode);
                break;
        }
    }

    private boolean isPackageInstalled(String packageName) {
        PackageManager pm = getReactApplicationContext().getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "TextToSpeech";
    }

    @ReactMethod
    public void getInitStatus(Promise promise) {
        synchronized (initStatusPromises) {
            if (!ready) {
                initStatusPromises.add(promise);
            } else {
                resolveReadyPromise(promise);
            }
        }
    }

    @ReactMethod
    public void getHash(String utterance, Promise promise) {
        promise.resolve(Integer.toString(utterance.hashCode()));
    }

    @ReactMethod
    public void speak(String utterance, ReadableMap params, Promise promise) {
        if (!isTtsReady(promise)) return;

        String utteranceId = Integer.toString(utterance.hashCode());
        lock.lock();
        // Do not queue when bashing play button in voice download screen
        if (!isTtsIdle && utteranceId.equals(playingUtteranceId)) {
            lock.unlock();
            promise.reject("TTS is speaking: " + playingUtterance + ", requested: " + utterance);
        } else {
            lock.unlock();
            // Not idle race condition until lock in executor.execute ...
            // small chance of unwanted queued sentences

            // queue TTS utterance using single thread executor so that audio focus can be requested early
            // when requesting to speak an utterance instead of waiting for it to be realy started to avoid
            // nested requests when an utterance is requested to be spoken while another is still playing
            executor.execute(() -> {
                if (enableTestCode) {
                    testCodeReset();
                }

                lock.lock();
                isTtsIdle = false;
                stopRequest = false;
                int speakResult = speak(utterance, utteranceId, params);
                if (speakResult == TextToSpeech.SUCCESS) {
                    playingUtteranceId = utteranceId;
                    playingUtterance = utterance;
                    while (!isTtsIdle) {
                        try {
                            condition.await();
                        } catch (InterruptedException e) {
                        } finally {
                            if (isTtsIdle) {
                                if (stopRequest) {
                                    stopRequest = false;
                                    conditionStopped.signal();
                                } else {
                                    // update gain only after fully spoken utterance
                                    updateAudioGain();
                                }
                            }
                        }
                    }
                    playingUtteranceId = "";
                    playingUtterance = "";
                    lock.unlock();
                    promise.resolve(utteranceId);
                } else {
                    lock.unlock();
                    resolvePromiseWithStatusCode(speakResult, promise);
                }
            });
        }
    }

    @ReactMethod
    public void setDefaultLanguage(String language, Promise promise) {
        if (!isTtsReady(promise)) return;

        Locale locale = null;

        if (language.indexOf("-") != -1) {
            String[] parts = language.split("-");
            locale = new Locale(parts[0], parts[1]);
        } else {
            locale = new Locale(language);
        }

        try {
            int result = tts.setLanguage(locale);
            resolvePromiseWithStatusCode(result, promise);
        } catch (Exception e) {
            promise.reject("error", "Unknown error code");
        }
    }

    @ReactMethod
    public void setDucking(Boolean ducking, Promise promise) {
        if (!isTtsReady(promise)) return;
        this.ducking = ducking;
        promise.resolve("success");
    }

    @ReactMethod
    public void setAudioManagement(boolean useAudioFocus) {
        this.useAudioFocus = useAudioFocus;
    }

    @ReactMethod
    public void setDefaultRate(Float rate, Boolean skipTransform, Promise promise) {
        if (!isTtsReady(promise)) return;

        if (skipTransform) {
            int result = tts.setSpeechRate(rate);
            resolvePromiseWithStatusCode(result, promise);
        } else {
            // normalize android rate
            // rate value will be in the range 0.0 to 1.0
            // let's convert it to the range of values Android platform expects,
            // where 1.0 is no change of rate and 2.0 is the twice faster rate
            float androidRate = rate.floatValue() < 0.5f ?
                    rate.floatValue() * 2 : // linear fit {0, 0}, {0.25, 0.5}, {0.5, 1}
                    rate.floatValue() * 4 - 1; // linear fit {{0.5, 1}, {0.75, 2}, {1, 3}}
            int result = tts.setSpeechRate(androidRate);
            resolvePromiseWithStatusCode(result, promise);
        }
    }

    @ReactMethod
    public void setDefaultPitch(Float pitch, Promise promise) {
        if (!isTtsReady(promise)) return;
        int result = tts.setPitch(pitch);
        resolvePromiseWithStatusCode(result, promise);
    }

    @ReactMethod
    public void setDefaultVoice(String voiceId, Promise promise) {
        if (!isTtsReady(promise)) return;

        if (Build.VERSION.SDK_INT >= 21) {
            try {
                for (Voice voice : tts.getVoices()) {
                    if (voice.getName().equals(voiceId)) {
                        int result = tts.setVoice(voice);
                        setInitialAudioGain();
                        resolvePromiseWithStatusCode(result, promise);
                        return;
                    }
                }
            } catch (Exception e) {
                // Purposefully ignore exceptions here due to some buggy TTS engines.
                // See http://stackoverflow.com/questions/26730082/illegalargumentexception-invalid-int-os-with-samsung-tts
            }
            promise.reject("not_found", "The selected voice was not found");
        } else {
            promise.reject("not_available", "Android API 21 level or higher is required");
        }
    }

    @ReactMethod
    public void voices(Promise promise) {
        if (!isTtsReady(promise)) return;

        WritableArray voiceArray = Arguments.createArray();

        if (Build.VERSION.SDK_INT >= 21) {
            try {
                for (Voice voice : tts.getVoices()) {
                    WritableMap voiceMap = Arguments.createMap();
                    voiceMap.putString("id", voice.getName());
                    voiceMap.putString("name", voice.getName());

                    String language = iso3LanguageCodeToIso2LanguageCode(voice.getLocale().getISO3Language());
                    String country = voice.getLocale().getISO3Country();
                    if (country != "") {
                        language += "-" + iso3CountryCodeToIso2CountryCode(country);
                    }

                    voiceMap.putString("language", language);
                    voiceMap.putInt("quality", voice.getQuality());
                    voiceMap.putInt("latency", voice.getLatency());
                    voiceMap.putBoolean("networkConnectionRequired", voice.isNetworkConnectionRequired());
                    voiceMap.putBoolean("notInstalled", voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
                    voiceArray.pushMap(voiceMap);
                }
            } catch (Exception e) {
                // Purposefully ignore exceptions here due to some buggy TTS engines.
                // See http://stackoverflow.com/questions/26730082/illegalargumentexception-invalid-int-os-with-samsung-tts
            }
        }

        promise.resolve(voiceArray);
    }

    @ReactMethod
    public void setDefaultEngine(String engineName, final Promise promise) {
        if (!isTtsReady(promise)) return;

        if (engineName == null || engineName.equals(currentEngineName)) {
            // The engine we're going to activate is already active (or
            // no engine specified at all): return immediately
            promise.resolve(ready);
            return;
        }

        if (isPackageInstalled(engineName)) {
            // setDefaultEngine may be called while the engine is speaking,
            // fully stop before changing the engine.
            stop();

            ready = false;
            currentEngineName = engineName;
            onCatalystInstanceDestroy();
            tts = new TextToSpeech(getReactApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    synchronized (initStatusPromises) {
                        ready = (status == TextToSpeech.SUCCESS);
                        for (Promise p : initStatusPromises) {
                            resolveReadyPromise(p);
                        }
                        initStatusPromises.clear();
                        promise.resolve(ready);
                    }
                }
            }, engineName);

            setInitialAudioGain();
            setUtteranceProgress();
        } else {
            promise.reject("not_found", "The selected engine was not found");
        }
    }

    @ReactMethod
    public void engines(Promise promise) {
        if (!isTtsReady(promise)) return;

        WritableArray engineArray = Arguments.createArray();

        if (Build.VERSION.SDK_INT >= 14) {
            try {
                String defaultEngineName = tts.getDefaultEngine();
                for (TextToSpeech.EngineInfo engine : tts.getEngines()) {
                    WritableMap engineMap = Arguments.createMap();

                    engineMap.putString("name", engine.name);
                    engineMap.putString("label", engine.label);
                    engineMap.putBoolean("default", engine.name.equals(defaultEngineName));
                    engineMap.putInt("icon", engine.icon);

                    engineArray.pushMap(engineMap);
                }
            } catch (Exception e) {
                promise.reject("error", "Unknown error code");
            }
        }

        promise.resolve(engineArray);
    }

    private int stop() {
        lock.lock();
        int result = tts.stop();
        if (result == TextToSpeech.SUCCESS) {
            // Stop playing audio. The engine (most likely) keeps on producing
            // audio for the rest of the utterance ... wait until it is done.
            if (!isTtsIdle && !stopRequest) {
                // TTS is speaking / going to speak and no stop request queued yet.
                // Stop active audio, then wait for engine is fully done.
                stopRequest = true;
                audioDone(stopRequest);
                while (stopRequest) {
                    try {
                        conditionStopped.await();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        lock.unlock();
        return result;
    }

    @ReactMethod
    public void stop(Promise promise) {
        boolean resultValue = true;
        if (ready) {
            int result = stop();
            resultValue = (result == TextToSpeech.SUCCESS);
        }
        promise.resolve(resultValue);
    }

    @ReactMethod
    private void requestInstallEngine(Promise promise) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://details?id=com.google.android.tts"));
        try {
            getCurrentActivity().startActivity(intent);
            promise.resolve("success");
        } catch (Exception e) {
            promise.reject("error", "Could not open Google Text to Speech App in the Play Store");
        }
    }

    @ReactMethod
    private void requestInstallData(Promise promise) {
        Intent intent = new Intent();
        intent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        try {
            getCurrentActivity().startActivity(intent);
            promise.resolve("success");
        } catch (ActivityNotFoundException e) {
            promise.reject("no_engine", "No TTS engine installed");
        }
    }

    /**
     * called on React Native Reloading JavaScript
     * https://stackoverflow.com/questions/15563361/tts-leaked-serviceconnection
     */
    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private boolean isTtsReady(Promise promise) {
        if (!ready) {
            resolveReadyPromise(promise);
        }
        return ready;
    }

    @SuppressWarnings("deprecation")
    private int speak(String utterance, String utteranceId, ReadableMap options) {
        forcePhoneSpeaker = options.hasKey("KEY_OPTION_FORCE_PHONE_SPEAKER") ? options.getBoolean("KEY_OPTION_FORCE_PHONE_SPEAKER") : false;
        isCarAudioSystem = options.hasKey("KEY_OPTION_CAR_AUDIO_SYSTEM") ? options.getBoolean("KEY_OPTION_CAR_AUDIO_SYSTEM") : false;
        float volume = options.hasKey("KEY_OPTION_VOLUME") ? (float)options.getDouble("KEY_OPTION_VOLUME") : 1.0f;
        float pan = options.hasKey("KEY_OPTION_PAN") ? (float)options.getDouble("KEY_OPTION_PAN") : 0.0f;

        requestFocus();

        if (Build.VERSION.SDK_INT >= 21) {
            try {
                // synthesizeToFile requires a valid File, the audio written will not be used (audio is taken from onAudioAvailable)
                File devNull = new File("/dev/null");
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, pan);
                return tts.synthesizeToFile(utterance, params, devNull, utteranceId);
            } catch (NullPointerException e) {
                return TextToSpeech.ERROR;
            }
        } else {
            return TextToSpeech.ERROR;
        }
    }

    private void sendEvent(String eventName, String utteranceId) {
        WritableMap params = Arguments.createMap();
        params.putString("utteranceId", utteranceId);
        sendEvent(eventName, params);
    }

    private void sendEvent(String eventName, WritableMap params) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void requestFocus() {
        // Set TTS 'player' audio attribute usage to AudioAttributes.USAGE_MEDIA for playback on Android Auto music volume level,
        // set to AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE for playback on Android Auto music navigation guidance volume level.
        // Note 1: navigation guidance volume level only has effect on Android Auto so far and can only be changed when audio is playing on it.
        // Note 2: audio focus request audio attributes usage will be AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE to get
        // proper audio focus change for Android Auto.
        int ttsAudioTrackUsage = AudioAttributes.USAGE_MEDIA;
        
        if (isCarAudioSystem) {
            ttsAudioTrackUsage = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
        }

        AudioAttributes ttsAudioAttributes = new AudioAttributes.Builder()
                .setUsage(ttsAudioTrackUsage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        tts.setAudioAttributes(ttsAudioAttributes);

        if (useAudioFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes audioFocusAudioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                audioFocusRequest = new AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioFocusAudioAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build();

                audioFocus = audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                audioFocus = audioManager.requestAudioFocus(audioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                );
            }

            if (audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                if (audioManager.isMusicActive()) {
                    // Delay so that the ducking of music will have had the initial effect.
                    SystemClock.sleep(450);
                }
            } else {
                tts.stop();
            }
        }
    }

    private void audioDone(boolean interrupting) {
        // When interupting, called from stop(), the lock is already locked
        if (!interrupting) {
            lock.lock();
        }

        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }

        if (useAudioFocus && audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocus = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
                audioFocusRequest = null;
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        }

        if (!isTtsIdle && !interrupting) {
            isTtsIdle = true;
            condition.signal();
        }

        if (!interrupting) {
            lock.unlock();
        }
    }

    private String audioFocusResultToString(int audioFocusResult) {
        switch (audioFocusResult) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                return "AUDIOFOCUS_REQUEST_FAILED";
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                return "AUDIOFOCUS_REQUEST_GRANTED";
            case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                return "AUDIOFOCUS_REQUEST_DELAYED";
        }
        return "Unknown audio focus result: " + audioFocusResult;
    }



    private void processAudio(byte[] audio) {
        // convert to array of 64 bit samples in Q8.24 format
        long[] samples = new long[audio.length / 2];
        for (int i = 0, j = 0; i < samples.length; i++, j += 2) {
            short sample = (short)(((short)(audio[j+1]) << 8) | ((short)(audio[j+0]) & 0xff)) ;
            samples[i] = (long)(sample) << 9;
        }

        processGain(samples);

        // convert array of 64 bit samples back to 16 bit in byte array
        for (int i = 0, j = 0; i < samples.length; i++, j += 2) {
            int sample = (int)(samples[i] >> 9);

            // hard clamp to short
            if (((sample >> 15) ^ (sample >> 31)) != 0) {
                sample = 0x7FFF ^ (sample >> 31);

                if (enableTestCode) {
                    clippedSamplesCount++;
                }
            }

            audio[j+1] = (byte)((sample >> 8) & 0xff);
            audio[j+0] = (byte)(sample & 0xff);
        }
    }

    private void processGain(long[] audio) {
        if (enableTestCode) {
            sampleCount += audio.length;
        }

        for (int i = 0; i < audio.length; i++) {
            long sample = audio[i];
            audio[i] = (sample * audioGain) >> 24;

            // track largest sample for crude 'automatic' volume adjustements
            if (sample < 0) {
                sample = -sample;
            }
            if (sample > largestSample) {
                largestSample = sample;
            }
        }
    }

    private void setInitialAudioGain() {
        if (currentEngineName != null && currentEngineName.toLowerCase().contains("com.google")) {
            audioGain = (long)(1.6788 * UNITY_GAIN_Q8p24); // 4.5dB = 1.6788 (10 ^ (4.5/20))
        } else {
            audioGain = UNITY_GAIN_Q8p24; // 0dB = 1.0 (10 ^ (0/20))
        }
        largestSample = 0;

        if (enableTestCode) {
            Log.d(TAG, "Initial audio gain " + Math.log10((double)audioGain / (double)UNITY_GAIN_Q8p24) * 20.0 + "dB for engine " + currentEngineName);
        }
    }

    private void testCodeReset() {
        clippedSamplesCount = 0;
        sampleCount = 0;
    }

    private void updateAudioGain() {
        if (largestSample != 0) {
            // Sample and gain values are in Q8.24 format
            audioGain = (UNITY_GAIN_Q8p24 * UNITY_GAIN_Q8p24) / ((largestSample * audioGainClipRegion) >> 24);
            if (audioGain > audioGainMax) {
                audioGain = audioGainMax;
            } else if (audioGain < audioGainMin) {
                audioGain = audioGainMin;
            }
        }
        if (enableTestCode) {
            Log.d(TAG, "update gain " + (double)audioGain / (double)UNITY_GAIN_Q8p24 + " clipped: (" + sampleCount + " => " + clippedSamplesCount + " = " + (((float)clippedSamplesCount / (float)sampleCount) * 100.0f) + " %)");
            Log.d(TAG, "update gain " + Math.log10((double)audioGain / (double)UNITY_GAIN_Q8p24) * 20.0 + " dB, largest sample " + (double)largestSample / (double)UNITY_GAIN_Q8p24);
        }
    }

    @SuppressLint("NewApi")
    private void writeAudioToFile(byte[] audio) {
        String dirPath = getReactApplicationContext().getFilesDir().getPath();
        try {
            Files.createDirectory(Paths.get(dirPath));
        } catch (IOException e) {}
        try {
            Files.createFile(Paths.get(dirPath, "platformTTS.raw"));
        } catch (IOException e) {}
        try {
            Files.write(Paths.get(dirPath, "platformTTS.raw"), audio, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write audio to file e:" + e);
        }
    }

    private void forceSpeakerRoute(AudioTrack audioTrack)
    {
        AudioManager audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

        for (AudioDeviceInfo audioDevice : audioDevices) {
            if (audioDevice.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                audioTrack.setPreferredDevice(audioDevice);
                break;
            }
        }
    }
}
