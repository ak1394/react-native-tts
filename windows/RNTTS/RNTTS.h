#pragma once

#include "pch.h"
#include "NativeModules.h"

using namespace winrt::Microsoft::ReactNative;
using namespace winrt::Windows::Media::Playback;
using namespace winrt::Windows::Media::SpeechSynthesis;
using namespace winrt::Windows::UI::Xaml::Controls;

#ifdef RNW61
#define JSVALUEOBJECTPARAMETER
#else
#define JSVALUEOBJECTPARAMETER const &
#endif

namespace winrt::RNTTS {
  REACT_MODULE(RNTTS, L"TextToSpeech");
  struct RNTTS {
    const std::string Name = "TextToSpeech";

    SpeechSynthesizer speechSynthesizer;
    MediaPlayer mediaPlayer;
    VoiceInformation defaultVoice;
    React::ReactContext reactContext;

    RNTTS();

    winrt::Windows::Foundation::IAsyncAction SpeakAsync(winrt::hstring text, ReactPromise<JSValue> promise) noexcept;
    void OnMediaElementStateChanged(MediaPlaybackSession session,
        winrt::Windows::Foundation::IInspectable const& result) noexcept;

    REACT_INIT(Init);
    void Init(React::ReactContext const& reactContext) noexcept;

    REACT_METHOD(SetDefaultVoice, L"setDefaultVoice")
    void SetDefaultVoice(std::string voiceId, ReactPromise<JSValue> promise) noexcept;

    REACT_METHOD(SetDefaultRate, L"setDefaultRate")
    void SetDefaultRate(double rate, bool skipTransform, ReactPromise<JSValue> promise) noexcept;

    REACT_METHOD(SetDefaultPitch, L"setDefaultPitch")
    void SetDefaultPitch(double rate, ReactPromise<JSValue> promise) noexcept;

    REACT_METHOD(SetDefaultLanguage, L"setDefaultLanguage")
    void SetDefaultLanguage(std::string language, ReactPromise<JSValue> promise) noexcept;

    REACT_METHOD(Voices, L"voices")
    void Voices(ReactPromise<JSValueArray> promise) noexcept;

    REACT_METHOD(Speak, L"speak")
    void Speak(std::string text, ReactPromise<JSValue> promise) noexcept;

    REACT_METHOD(Stop, L"stop")
    void Stop(ReactPromise<JSValue> promise) noexcept;

    REACT_METHOD(Pause, L"pause")
    void Pause(ReactPromise<JSValue> promise) noexcept;

    REACT_METHOD(Resume, L"resume")
    void Resume(ReactPromise<JSValue> promise) noexcept;

    REACT_EVENT(OnStart, L"tts-start");
    std::function<void(std::string)> OnStart;

    REACT_EVENT(OnFinish, L"tts-finish");
    std::function<void(std::string)> OnFinish;

    REACT_EVENT(OnError, L"tts-error");
    std::function<void(std::string)> OnError;

    REACT_EVENT(OnCancel, L"tts-cancel");
    std::function<void(std::string)> OnCancel;
  };
}
