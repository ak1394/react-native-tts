import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const TextToSpeech = NativeModules.TextToSpeech;

class Tts extends NativeEventEmitter {
  constructor() {
    super(TextToSpeech);
  }

  setDucking(enabled) {
    return TextToSpeech.setDucking(enabled);
  }

  setDefaultVoice(voiceId) {
    return TextToSpeech.setDefaultVoice(voiceId);
  }

  setDefaultRate(rate, skipTransform) {
    return TextToSpeech.setDefaultRate(rate, !!skipTransform);
  }

  setDefaultPitch(pitch) {
    return TextToSpeech.setDefaultPitch(pitch);
  }

  setDefaultLanguage(language) {
    return TextToSpeech.setDefaultLanguage(language);
  }

  voices() {
    return TextToSpeech.voices();
  }

  speak(utterance, voiceId) {
    if (Platform.OS === 'ios') {
      return TextToSpeech.speak(utterance, voiceId);
    } else {
      return TextToSpeech.speak(utterance);
    }
  }

  stop(onWordBoundary) {
    if (Platform.OS === 'ios') {
      return TextToSpeech.stop(onWordBoundary);
    } else {
      return TextToSpeech.stop();
    }
  }

  pause(onWordBoundary) {
    if (Platform.OS === 'ios') {
      return TextToSpeech.pause(onWordBoundary);
    }
  }

  resume() {
    if (Platform.OS === 'ios') {
      return TextToSpeech.resume();
    }
  }

  addEventListener(type, handler) {
    this.addListener(type, handler);
  }

  removeEventListener(type, handler) {
    this.removeListener(type, handler);
  }
}

export default new Tts();
