# React Native TTS

React Native TTS is a text-to-speech library for [React Native](https://facebook.github.io/react-native/) on iOS and Android.

## Documentation

- [Install](https://github.com/ak1394/react-native-speech#install)
- [Usage](https://github.com/ak1394/react-native-speech#usage)
- [License](https://github.com/ak1394/react-native-speech#license)

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
Stop speaking and flush the TTS queue.

```js
Tts.stop();
```

### List Voices

Returns list of available voices 

*(not supported on Android API Level < 21, returns empty list)*

```js
Tts.voices().then(voices => console.log(voices));

// Prints:
//
// [ { id: 'com.apple.ttsbundle.Moira-compact', name: 'Moira', language: 'en-IE' },
// ...
// { id: 'com.apple.ttsbundle.Samantha-compact', name: 'Samantha', language: 'en-US' } ]
```

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

### Events

Subscribe to TTS events

```js
Tts.addEventListener('tts-start', (event) => console.log("start", event));
Tts.addEventListener('tts-finish', (event) => console.log("finish", event));
Tts.addEventListener('tts-cancel', (event) => console.log("cancel", event));
```

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
