# React Native TTS

React Native TTS is a text-to-speech library for [React Native](https://facebook.github.io/react-native/) on iOS and Android.

## Documentation

- [Install](#install)
- [Usage](#usage)
- [License](#license)
- [Example project](#example)

## Install

```shell
npm install --save react-native-tts
react-native link
```

## Usage

### Imports

```js
import Tts from 'react-native-tts';
```

### Speaking

Add utterance to TTS queue and start speaking. Returns promise with utteranceId.

```js
Tts.speak('Hello, world!');
```

Additionally, speak() allows to pass platform-specific options 'voiceId' on IOS and 'params' on Android to underlying platform API:

```js
Tts.speak('Hello, world!', { iosVoiceId: 'com.apple.ttsbundle.Moira-compact' });
Tts.speak('Hello, world!', { androidParams: { KEY_PARAM_PAN: -1, KEY_PARAM_VOLUME: 0.5, KEY_PARAM_STREAM: 'STREAM_MUSIC' } });
```

For more detail on `androidParams` properties, please take a look at [official android documentation](https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine.html). Please note that there are still unsupported key with this wrapper library such as `KEY_PARAM_SESSION_ID`. The following are brief summarization of currently implemented keys:

- `KEY_PARAM_PAN` ranges from `-1` to `+1`. 

- `KEY_PARAM_VOLUME` ranges from `0` to `1`, where 0 means silence. Note that `1` is a default value for Android.

- For `KEY_PARAM_STREAM` property, you can currently use one of `STREAM_ALARM`, `STREAM_DTMF`, `STREAM_MUSIC`, `STREAM_NOTIFICATION`, `STREAM_RING`, `STREAM_SYSTEM`, `STREAM_VOICE_CALL`,

Stop speaking and flush the TTS queue.

```js
Tts.stop();
```

### Waiting for initialization

On some platforms it could take some time to initialize TTS engine, and Tts.speak() will fail to speak until the engine is ready.

To wait for successfull initialization you could use getInitStatus() call.

```js
Tts.getInitStatus().then(() => {
  Tts.speak('Hello, world!');
});
```

### Ducking

Enable lowering other applications output level while speaking (also referred to as "ducking").

```js
Tts.setDucking(true);
```

### List Voices

Returns list of available voices 

*(not supported on Android API Level < 21, returns empty list)*

```js
Tts.voices().then(voices => console.log(voices));

// Prints:
//
// [ { id: 'com.apple.ttsbundle.Moira-compact', name: 'Moira', language: 'en-IE', quality: 300 },
// ...
// { id: 'com.apple.ttsbundle.Samantha-compact', name: 'Samantha', language: 'en-US' } ]
```

|Voice field|Description|
|-----|-------|
|id   |Unique voice identifier (e.g. `com.apple.ttsbundle.Moira-compact`)|
|name |Name of the voice *(iOS only)*|
|language|BCP-47 language code (e.g. 'en-US')|
|quality|Voice quality (300 = normal, 500 = enhanced/very high)|
|latency|Expected synthesizer latency (100 = very low, 500 = very high) *(Android only)*|
|networkConnectionRequired|True when the voice requires an active network connection *(Android only)*|
|notInstalled|True when the voice may need to download additional data to be fully functional *(Android only)*|


### Set default Language

```js
Tts.setDefaultLanguage('en-IE');
```

### Set default Voice

Sets default voice, pass one of the voiceId as reported by a call to Tts.voices() 

*(not available on Android API Level < 21)*

```js
Tts.setDefaultVoice('com.apple.ttsbundle.Moira-compact');
```

### Set default Speech Rate

Sets default speech rate. The rate parameter is a float where where 0.01 is a slowest rate and 0.99 is the fastest rate.

```js
Tts.setDefaultRate(0.6);
```

There is a significant difference to how the rate value is interpreted by iOS and Android native TTS APIs. To provide unified cross-platform behaviour, translation is applied to the rate value. However, if you want to turn off the translation, you can provide optional `skipTransform` parameter to `Tts.setDefaultRate()` to pass rate value unmodified.

Do not translate rate parameter:

```js
Tts.setDefaultRate(0.6, true);
```

### Set default Pitch

Sets default pitch. The pitch parameter is a float where where 1.0 is a normal pitch. On iOS min pitch is 0.5 and max pitch is 2.0

```js
Tts.setDefaultPitch(1.5);
```

### Events

Subscribe to TTS events

```js
Tts.addEventListener('tts-start', (event) => console.log("start", event));
Tts.addEventListener('tts-finish', (event) => console.log("finish", event));
Tts.addEventListener('tts-cancel', (event) => console.log("cancel", event));
```

### Install (additional) language data

Shows the Android Activity to install additional language/voice data.

```js
Tts.requestInstallData();
```

## Troubleshooting

### No text to speech engine installed on Android

On Android, it may happen that the Text-to-Speech engine is not (yet) installed on the phone.
When this is the case, `Tts.getInitStatus()` returns an error with code `no_engine`.
You can use the following code to request the installation of the default Google Text to Speech App.
The app will need to be restarted afterwards before the changes take affect.

```js
Tts.getInitStatus().then(() => {
  // ...
}, (err) => {
  if (err.code === 'no_engine') {
    Tts.requestInstallEngine();
  }
});
```

## Example

There is an example project which shows use of react-native-tts on Android/iOS: https://github.com/themostaza/react-native-tts-example

## License

The MIT License (MIT)
=====================

Copyright © `2016` `Anton Krasovsky`

Permission is hereby granted, free of charge, to any person
obtaining a copy of this software and associated documentation
files (the “Software”), to deal in the Software without
restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following
conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
