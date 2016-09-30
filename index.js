import { NativeModules, NativeAppEventEmitter } from 'react-native';

export function speak(text, lang) {
  return NativeModules.TextToSpeech.speak({text: text, language: lang});
}

export function stop(immediately) {
  return NativeModules.TextToSpeech.stop(immediately);
}

export function pause(immediately) {
  return NativeModules.TextToSpeech.pause(immediately);
}

export function resume() {
  return NativeModules.TextToSpeech.resume();
}

export function voices() {
  return NativeModules.TextToSpeech.voices();
}
