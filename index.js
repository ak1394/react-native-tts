import { NativeModules, NativeAppEventEmitter } from 'react-native';

export function speak(text, lang) {
  return NativeModules.TextToSpeech.speak({text: text, language: lang});
}

export function voices() {
  return NativeModules.TextToSpeech.voices();
}
