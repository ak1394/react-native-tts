package net.no_mad.tts;

import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.app.Activity;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TextToSpeechModule extends ReactContextBaseJavaModule {

    private TextToSpeech tts;
    private Boolean ready = null;
    private ArrayList<Promise> initStatusPromises;

    private boolean ducking = false;
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    private Map<String, Locale> localeCountryMap;
    private Map<String, Locale> localeLanguageMap;

    public TextToSpeechModule(ReactApplicationContext reactContext) {
        super(reactContext);
        audioManager = (AudioManager) reactContext.getApplicationContext().getSystemService(reactContext.AUDIO_SERVICE);
        initStatusPromises = new ArrayList<Promise>();
        //initialize ISO3, ISO2 languague country code mapping.
        initCountryLanguageCodeMapping();

        tts = new TextToSpeech(getReactApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                synchronized(initStatusPromises) {
                    ready = (status == TextToSpeech.SUCCESS) ? Boolean.TRUE : Boolean.FALSE;
                    for(Promise p: initStatusPromises) {
                        resolveReadyPromise(p);
                    }
                    initStatusPromises.clear();
                }
            }
        });

        setUtteranceProgress();
    }

    private void setUtteranceProgress() {
        if(tts != null)
        {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    sendEvent("tts-start", utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    if(ducking) {
                        audioManager.abandonAudioFocus(afChangeListener);
                    }
                    sendEvent("tts-finish", utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    if(ducking) {
                        audioManager.abandonAudioFocus(afChangeListener);
                    }
                    sendEvent("tts-error", utteranceId);
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    if(ducking) {
                        audioManager.abandonAudioFocus(afChangeListener);
                    }
                    sendEvent("tts-cancel", utteranceId);
                }

                @Override
                public void onRangeStart (String utteranceId, int start, int end, int frame) {
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
        for (String country: countries) {
            Locale locale = new Locale("", country);
            localeCountryMap.put(locale.getISO3Country().toUpperCase(), locale);
        }
        String[] languages = Locale.getISOLanguages();
        localeLanguageMap = new HashMap<String, Locale>(languages.length);
        for (String language: languages) {
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
        if (ready == Boolean.TRUE) {
            promise.resolve("success");
        }
        else {
            promise.reject("no_engine", "No TTS engine installed");
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
        synchronized(initStatusPromises) {
            if(ready == null) {
                initStatusPromises.add(promise);
            } else {
                resolveReadyPromise(promise);
            }
        }
    }

    @ReactMethod
    public void speak(String utterance, ReadableMap params, Promise promise) {
        if(notReady(promise)) return;

        if(ducking) {
            // Request audio focus for playback
            int amResult = audioManager.requestAudioFocus(afChangeListener,
                                                          // Use the music stream.
                                                          AudioManager.STREAM_MUSIC,
                                                          // Request permanent focus.
                                                          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

            if(amResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                promise.reject("Android AudioManager error, failed to request audio focus");
                return;
            }
        }

        String utteranceId = Integer.toString(utterance.hashCode());

        int speakResult = speak(utterance, utteranceId, params);
        if(speakResult == TextToSpeech.SUCCESS) {
            promise.resolve(utteranceId);
        } else {
            resolvePromiseWithStatusCode(speakResult, promise);
        }
    }

    @ReactMethod
    public void setDefaultLanguage(String language, Promise promise) {
        if(notReady(promise)) return;

        Locale locale = null;

        if(language.indexOf("-") != -1) {
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
        if(notReady(promise)) return;
        this.ducking = ducking;
        promise.resolve("success");
    }

    @ReactMethod
    public void setDefaultRate(Float rate, Boolean skipTransform, Promise promise) {
        if(notReady(promise)) return;

        if(skipTransform) {
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
        if(notReady(promise)) return;
        int result = tts.setPitch(pitch);
        resolvePromiseWithStatusCode(result, promise);
    }

    @ReactMethod
    public void setDefaultVoice(String voiceId, Promise promise) {
        if(notReady(promise)) return;

        if (Build.VERSION.SDK_INT >= 21) {
            try {
                for(Voice voice: tts.getVoices()) {
                    if(voice.getName().equals(voiceId)) {
                        int result = tts.setVoice(voice);
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
        if(notReady(promise)) return;

        WritableArray voiceArray = Arguments.createArray();

        if (Build.VERSION.SDK_INT >= 21) {
            try {
                for(Voice voice: tts.getVoices()) {
                    WritableMap voiceMap = Arguments.createMap();
                    voiceMap.putString("id", voice.getName());
                    voiceMap.putString("name", voice.getName());

                    String language = iso3LanguageCodeToIso2LanguageCode(voice.getLocale().getISO3Language());
                    String country = voice.getLocale().getISO3Country();
                    if(country != "") {
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
        if(notReady(promise)) return;

        if(isPackageInstalled(engineName)) {
            ready = null;
            onCatalystInstanceDestroy();
            tts = new TextToSpeech(getReactApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    synchronized(initStatusPromises) {
                        ready = (status == TextToSpeech.SUCCESS) ? Boolean.TRUE : Boolean.FALSE;
                        for(Promise p: initStatusPromises) {
                            resolveReadyPromise(p);
                        }
                        initStatusPromises.clear();
                        promise.resolve(ready);
                    }
                }
            }, engineName);

            setUtteranceProgress();
        } else {
            promise.reject("not_found", "The selected engine was not found");
        }
    }

    @ReactMethod
    public void engines(Promise promise) {
        if(notReady(promise)) return;

        WritableArray engineArray = Arguments.createArray();

        if (Build.VERSION.SDK_INT >= 14) {
            try {
                String defaultEngineName = tts.getDefaultEngine();
                for(TextToSpeech.EngineInfo engine: tts.getEngines()) {
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

    @ReactMethod
    public void stop(Promise promise) {
        if(notReady(promise)) return;

        int result = tts.stop();
        boolean resultValue = (result == TextToSpeech.SUCCESS) ? Boolean.TRUE : Boolean.FALSE;
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
        if(tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private boolean notReady(Promise promise) {
        if(ready == null) {
            promise.reject("not_ready", "TTS is not ready");
            return true;
        }
        else if(ready != Boolean.TRUE) {
            resolveReadyPromise(promise);
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private int speak(String utterance, String utteranceId, ReadableMap inputParams) {
        String audioStreamTypeString = inputParams.hasKey("KEY_PARAM_STREAM") ? inputParams.getString("KEY_PARAM_STREAM") : "";
        float volume = inputParams.hasKey("KEY_PARAM_VOLUME") ? (float) inputParams.getDouble("KEY_PARAM_VOLUME") : 1.0f;
        float pan = inputParams.hasKey("KEY_PARAM_PAN") ? (float) inputParams.getDouble("KEY_PARAM_PAN") : 0.0f;

        int audioStreamType;
        switch(audioStreamTypeString) {
            /*
            // This has been added in API level 26, commenting out for now

            case "STREAM_ACCESSIBILITY":
                audioStreamType = AudioManager.STREAM_ACCESSIBILITY;
                break;
            */
            case "STREAM_ALARM":
                audioStreamType = AudioManager.STREAM_ALARM;
                break;
            case "STREAM_DTMF":
                audioStreamType = AudioManager.STREAM_DTMF;
                break;
            case "STREAM_MUSIC":
                audioStreamType = AudioManager.STREAM_MUSIC;
                break;
            case "STREAM_NOTIFICATION":
                audioStreamType = AudioManager.STREAM_NOTIFICATION;
                break;
            case "STREAM_RING":
                audioStreamType = AudioManager.STREAM_RING;
                break;
            case "STREAM_SYSTEM":
                audioStreamType = AudioManager.STREAM_SYSTEM;
                break;
            case "STREAM_VOICE_CALL":
                audioStreamType = AudioManager.STREAM_VOICE_CALL;
                break;
            default:
                audioStreamType = AudioManager.USE_DEFAULT_STREAM_TYPE;
        }

        if (Build.VERSION.SDK_INT >= 21) {
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStreamType);
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, pan);
            return tts.speak(utterance, TextToSpeech.QUEUE_ADD, params, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(audioStreamType));
            params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, String.valueOf(volume));
            params.put(TextToSpeech.Engine.KEY_PARAM_PAN, String.valueOf(pan));
            return tts.speak(utterance, TextToSpeech.QUEUE_ADD, params);
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
    
    @ReactMethod
    public void removeListeners(Integer count) {
        // Keep: Required for RN built in Event Emitter Calls.
    }

    @ReactMethod
    public void addListener(String eventName) {
        // Keep: Required for RN built in Event Emitter Calls.
    }
}
