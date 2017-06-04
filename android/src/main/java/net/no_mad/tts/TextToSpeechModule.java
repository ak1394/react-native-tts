package net.no_mad.tts;

import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

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
import java.lang.Math;

public class TextToSpeechModule extends ReactContextBaseJavaModule {

    private TextToSpeech tts;
    private boolean ready;

    private boolean ducking = false;
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    public TextToSpeechModule(ReactApplicationContext reactContext) {
        super(reactContext);
        audioManager = (AudioManager) reactContext.getApplicationContext().getSystemService(reactContext.AUDIO_SERVICE);

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
        });

    }

    @Override
    public String getName() {
        return "TextToSpeech";
    }

    @ReactMethod
    public void speak(String utterance, Promise promise) {
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
        int speakResult = speak(utterance, utteranceId);
        if(speakResult == TextToSpeech.SUCCESS) {
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

        try {
          int result = tts.setLanguage(locale);
          switch (result) {
              case TextToSpeech.LANG_AVAILABLE:
              case TextToSpeech.LANG_COUNTRY_AVAILABLE:
              case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                  promise.resolve("success");
                  break;
              case TextToSpeech.LANG_MISSING_DATA:
                  promise.reject("not_found", "Language data is missing");
                  break;
              case TextToSpeech.LANG_NOT_SUPPORTED:
                  promise.reject("not_found", "Language is not supported");
                  break;
              default:
                  promise.reject("error", "Unknown error code");
                  break;
          }
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
            promise.resolve(tts.setSpeechRate(rate));
        } else {
            // normalize android rate
            // rate value will be in the range 0.0 to 1.0
            // let's convert it to the range of values Android platform expects,
            // where 1.0 is no change of rate and 2.0 is the twice faster rate
            float androidRate = rate.floatValue() < 0.5f ?
                    rate.floatValue() * 2 : // linear fit {0, 0}, {0.25, 0.5}, {0.5, 1}
                    rate.floatValue() * 4 - 1; // linear fit {{0.5, 1}, {0.75, 2}, {1, 3}}
            promise.resolve(tts.setSpeechRate(androidRate));
        }
    }

    @ReactMethod
    public void setDefaultPitch(Float pitch, Promise promise) {
        if(notReady(promise)) return;

        promise.resolve(tts.setPitch(pitch));
    }

    @ReactMethod
    public void setDefaultVoice(String voiceId, Promise promise) {
        if(notReady(promise)) return;

        if (Build.VERSION.SDK_INT >= 21) {
            try {
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
            } catch (Exception e) {
              // Purposefully ignore exceptions here due to some buggy TTS engines.
              // See http://stackoverflow.com/questions/26730082/illegalargumentexception-invalid-int-os-with-samsung-tts
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
            try {
                for(Voice voice: tts.getVoices()) {
                    WritableMap voiceMap = Arguments.createMap();
                    voiceMap.putString("id", voice.getName());
                    voiceMap.putString("name", voice.getName());
                    voiceMap.putString("language", voice.getLocale().toLanguageTag());
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
