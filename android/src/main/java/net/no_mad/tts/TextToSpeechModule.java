package net.no_mad.tts;

import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.GuardedAsyncTask;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Promise;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Locale;
import java.util.HashMap;

public class TextToSpeechModule extends ReactContextBaseJavaModule {

    private TextToSpeech tts;
    private boolean ready;

    public TextToSpeechModule(ReactApplicationContext reactContext) {
        super(reactContext);

        tts = new TextToSpeech(getReactApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status != TextToSpeech.SUCCESS) {
                        ready = false;
                    } else {
                        ready = true;
                    }
                }
            });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    sendEvent("tts-start", utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    sendEvent("tts-finish", utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    sendEvent("tts-error", utteranceId);
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    sendEvent("tts-cancel", utteranceId);
                }
            });
    }

    @Override
    public String getName() {
        return "TextToSpeech";
    }

    @ReactMethod
    public void speak(String utterance, Promise promise) {
        if(notReady(promise)) return;

        String utteranceId = Integer.toString(utterance.hashCode());
        int result = speak(utterance, utteranceId);
        if(result == TextToSpeech.SUCCESS) {
            promise.resolve(utteranceId);
        } else {
            promise.reject("unable to play");
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

        int result = tts.setLanguage(locale);

        promise.resolve(result); // todo turn result to string
    }

    @ReactMethod
    public void setDefaultVoice(String voiceId, Promise promise) {
        if(notReady(promise)) return;

        if (Build.VERSION.SDK_INT >= 21) {
            for(Voice voice: tts.getVoices()) {
                if(voice.getName().equals(voiceId)) {
                    int result = tts.setVoice(voice);
                    if(result == TextToSpeech.SUCCESS) {
                        promise.resolve("success");
                        return;
                    } else {
                        promise.reject("error");
                    }
                }
            }
            promise.reject("not found");
        } else {
            promise.reject("not available");
        }
    }

    @ReactMethod
    public void voices(Promise promise) {
        if(notReady(promise)) return;

        WritableArray voiceArray = Arguments.createArray();

        if (Build.VERSION.SDK_INT >= 21) {
            for(Voice voice: tts.getVoices()) {
                WritableMap voiceMap = Arguments.createMap();
                voiceMap.putString("id", voice.getName());
                voiceMap.putString("name", voice.getName());
                voiceMap.putString("language", voice.getLocale().toLanguageTag());
                voiceArray.pushMap(voiceMap);
            }
        }

        promise.resolve(voiceArray);
    }

    @ReactMethod
    public void stop(Promise promise) {
        if(notReady(promise)) return;

        int result = tts.stop();
        if(result == TextToSpeech.SUCCESS) {
            promise.resolve("success");
        } else {
            promise.reject("error");
        }
    }

    private boolean notReady(Promise promise) {
        if(!ready) {
            promise.reject("not_ready", "TTS is not ready");
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private int speak(String utterance, String utteranceId) {
        if (Build.VERSION.SDK_INT >= 21) {
            return tts.speak(utterance, TextToSpeech.QUEUE_ADD, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            return tts.speak(utterance, TextToSpeech.QUEUE_ADD, params);
        }
    }

    private void sendEvent(String eventName, String utteranceId) {
        WritableMap params = Arguments.createMap();
        params.putString("utteranceId", utteranceId);
        getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }
}
