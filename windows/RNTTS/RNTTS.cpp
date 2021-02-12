#include "pch.h"
#include "RNTTS.h"

#include <sstream>
#include <string>

using namespace winrt;
using namespace winrt::Windows::UI::Xaml::Media;
using namespace winrt::Windows::Media::Playback;

RNTTS::RNTTS::RNTTS() : defaultVoice(nullptr)
{

}

void RNTTS::RNTTS::Init(React::ReactContext const& _reactContext) noexcept
{
    reactContext = _reactContext;
    speechSynthesizer = SpeechSynthesizer{};
    mediaPlayer = MediaPlayer{};
}

void RNTTS::RNTTS::SetDefaultVoice(std::string voiceId, ReactPromise<JSValue> promise) noexcept
{
    bool wasFound = false;
    size_t numVoices = speechSynthesizer.AllVoices().Size();
    for (size_t i = 0; i < numVoices; i++)
    {
        VoiceInformation voiceInfo = speechSynthesizer.AllVoices().GetAt((uint32_t)i);
        if (winrt::to_string(voiceInfo.Id()) == voiceId || winrt::to_string(voiceInfo.DisplayName()) == voiceId)
        {
            wasFound = true;
            defaultVoice = voiceInfo;
            break;
        }
    }

    if (!wasFound)
    {
        promise.Reject("The selected voice was not found");
        return;
    }

    try
    {
        speechSynthesizer.Voice(defaultVoice);
        promise.Resolve("success");
    }
    catch (winrt::hresult_error const&)
    {
        promise.Reject("Error setting selected voice");
    }
}

void RNTTS::RNTTS::SetDefaultRate(double rate, bool skipTransform, ReactPromise<JSValue> promise) noexcept
{
    // This value can range from 0.5 (half the default rate) to 6.0 (6x the default rate), inclusive.
    // The default value is 1.0 (the "normal" speaking rate for the current voice).
    if (skipTransform)
    {
        if (rate < 0.5 || rate > 6.0)
        {
            promise.Reject("Failure caused by an invalid rate");
            return;
        }

        speechSynthesizer.Options().SpeakingRate(rate);
        promise.Resolve("success");
        return;
    }

    // Convert a number range to another range, maintaining ratio
    const double oldMin = 0.5;
    double oldRange = (1.0 - oldMin);
    const double newMin = 1.0;
    double newRange = (6.0 - newMin);

    double transformedRate = rate < 0.5f ?
        rate * 2 : // linear fit {0, 0}, {0.25, 0.5}, {0.5, 1}
        (((rate - oldMin) * newRange) / oldRange) + newMin;
    speechSynthesizer.Options().SpeakingRate(transformedRate);

    promise.Resolve("success");
}

void RNTTS::RNTTS::SetDefaultPitch(double pitch, ReactPromise<JSValue> promise) noexcept
{
    // This value can range from 0.0 (lowest pitch) to 2.0 (highest pitch), inclusive. The default value is 1.0.
    if (pitch < 0.0 || pitch > 2.0)
    {
        promise.Reject("Failure caused by an invalid pitch");
        return;
    }

    speechSynthesizer.Options().AudioPitch(pitch);

    promise.Resolve("success");
}

void RNTTS::RNTTS::SetDefaultLanguage(std::string language, ReactPromise<JSValue> promise) noexcept
{
    bool wasFound = false;
    size_t numVoices = speechSynthesizer.AllVoices().Size();
    for (size_t i = 0; i < numVoices; i++)
    {
        VoiceInformation voiceInfo = speechSynthesizer.AllVoices().GetAt((uint32_t)i);
        if (winrt::to_string(voiceInfo.Language()) == language)
        {
            wasFound = true;
            defaultVoice = voiceInfo;
            break;
        }
    }

    if (!wasFound)
    {
        promise.Reject("The selected voice was not found");
        return;
    }

    try
    {
        speechSynthesizer.Voice(defaultVoice);
        promise.Resolve("success");
    }
    catch(winrt::hresult_error const&)
    {
        promise.Reject("Error setting selected voice");
    }
}

void RNTTS::RNTTS::Voices(ReactPromise<JSValueArray> promise) noexcept
{
    JSValueArray voices;

    size_t numVoices = speechSynthesizer.AllVoices().Size();
    for (size_t i = 0; i < numVoices; i++)
    {
        VoiceInformation voiceInfo = speechSynthesizer.AllVoices().GetAt((uint32_t)i);

        JSValueObject voice;
        voice["id"] = winrt::to_string(voiceInfo.Id());
        voice["gender"] = winrt::to_string(voiceInfo.Gender() == VoiceGender::Male ? L"male" : L"female");
        voice["name"] = winrt::to_string(voiceInfo.DisplayName());
        voice["language"] = winrt::to_string(voiceInfo.Language());
        voice["quality"] = 300;

        voices.emplace_back(std::move(voice));
    }

    promise.Resolve(voices);
}

void RNTTS::RNTTS::Speak(std::string text, ReactPromise<JSValue> promise) noexcept
{
    winrt::hstring htext = winrt::to_hstring(text);
    SpeakAsync(htext, promise);
}

void RNTTS::RNTTS::Stop(ReactPromise<JSValue> promise) noexcept
{
    Pause(promise);
}

void RNTTS::RNTTS::Pause(ReactPromise<JSValue> promise) noexcept
{
    if (mediaPlayer.CanPause())
        mediaPlayer.Pause();

    promise.Resolve("success");
}

void RNTTS::RNTTS::Resume(ReactPromise<JSValue> promise) noexcept
{
    mediaPlayer.Play();

    promise.Resolve("success");
}

winrt::Windows::Foundation::IAsyncAction RNTTS::RNTTS::SpeakAsync(winrt::hstring text, ReactPromise<JSValue> promise) noexcept
{
    // Generate the audio stream from plain text.
    winrt::Windows::Foundation::IAsyncOperation<SpeechSynthesisStream> task{ speechSynthesizer.SynthesizeTextToStreamAsync(text) };

    SpeechSynthesisStream speechStream = co_await task;



    // Send the stream to the media object.
    MediaPlaybackSession mediaSession = mediaPlayer.PlaybackSession();
    mediaSession.PlaybackStateChanged({ this, &RNTTS::RNTTS::OnMediaElementStateChanged });

    mediaPlayer.SetStreamSource(speechStream);
    //mediaPlayer.Source(speechStream);
    mediaPlayer.AutoPlay(true);
    mediaPlayer.Volume(100);
    mediaPlayer.Play();

    promise.Resolve("success");
}

void RNTTS::RNTTS::OnMediaElementStateChanged(MediaPlaybackSession session,
    winrt::Windows::Foundation::IInspectable const&) noexcept
{
#if 1
    const void* address = static_cast<const void*>(&session);
    std::stringstream ss;
    ss << address;
    std::string id = ss.str();

    switch (session.PlaybackState())
    {
    case MediaPlaybackState::Buffering:
    case MediaPlaybackState::Opening:
        return;
    case MediaPlaybackState::Playing:
        OnStart(id);
        return;
    case MediaPlaybackState::Paused:
        OnFinish(id);
        return;
    }
#endif
}
