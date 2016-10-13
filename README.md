# React Native TTS

React Native TTS is a text-to-speech library for [React Native](https://facebook.github.io/react-native/) for iOS and Android.

## Documentation

- [Install](https://github.com/naoufal/react-native-speech#install)
- [Usage](https://github.com/naoufal/react-native-speech#usage)
- [License](https://github.com/naoufal/react-native-speech#license)

## Install

```shell
npm install --save react-native-tts
react-native link
```

## Usage

### Imports

```js
import * as tts from 'react-native-tts';
# import these two modules if required to subscribe to TTS events
import { NativeModules, NativeEventEmitter, } from 'react-native';

```

### Speaking

Add utterance to TTS queue and start speaking. Returns promise with utteranceId.

```js
tts.speak('Hello, world!');
```
Stop speaking and flush the TTS queue.

```js
tts.stop();
```

### List Voices

List available voices

```js
tts.voices().then(voices => console.log(voices));
```
Prints 

```js
[ { id: 'com.apple.ttsbundle.Moira-compact', name: 'Moira', language: 'en-IE' },
...
{ id: 'com.apple.ttsbundle.Samantha-compact', name: 'Samantha', language: 'en-US' } ]
```
(not available on Android API Level < 21).

### Set default Language

```js
tts.set_default_language('en-IE')
```

### Set default Voice

Use Voice id as reported by tts.voices()

```js
tts.set_default_voice('com.apple.ttsbundle.Moira-compact')
```

(not available on Android API Level < 21).

### Events

```js
const ee = new NativeEventEmitter(NativeModules.TextToSpeech);
ee.addListener('tts-start', (utteranceId) => console.log("start", utteranceId));
ee.addListener('tts-finish', (utteranceId) => console.log("finish", utteranceId));
ee.addListener('tts-finish', (utteranceId) => console.log("cancel", utteranceId));
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
