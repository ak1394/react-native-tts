import { NativeModules, Platform } from 'react-native';

export function set_default_voice(voiceId) {
  return NativeModules.TextToSpeech.setDefaultVoice(voiceId);
}

export function set_default_language(language) {
  return NativeModules.TextToSpeech.setDefaultLanguage(language);
}

export function voices() {
  return NativeModules.TextToSpeech.voices();
}

export function speak(utterance, voiceId) {
  if(Platform.OS === 'ios') {
    return NativeModules.TextToSpeech.speak(utterance, voiceId);
  } else {
    return NativeModules.TextToSpeech.speak(utterance)
  }
}

export function stop(onWordBoundary) {
  if(Platform.OS === 'ios') {
    return NativeModules.TextToSpeech.stop(onWordBoundary);
  } else {
    return NativeModules.TextToSpeech.stop();
  }
}

export function pause(immediately) {
  if(Platform.OS === 'ios') {
    return NativeModules.TextToSpeech.pause(!immediately);
  }
}

export function resume() {
  if(Platform.OS === 'ios') {
    return NativeModules.TextToSpeech.resume();
  }
}
