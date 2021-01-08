import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const TextToSpeech = NativeModules.TextToSpeech;

class Tts extends NativeEventEmitter {
  constructor() {
    super(TextToSpeech);
  }

  getInitStatus() {
    if (Platform.OS === 'ios' || Platform.OS === 'windows') {
      return Promise.resolve(true);
    }
    return TextToSpeech.getInitStatus();
  }

  requestInstallEngine() {
    if (Platform.OS === 'ios' || Platform.OS === 'windows') {
      return Promise.resolve(true);
    }
    return TextToSpeech.requestInstallEngine();
  }

  requestInstallData() {
    if (Platform.OS === 'ios' || Platform.OS === 'windows') {
      return Promise.resolve(true);
    }
    return TextToSpeech.requestInstallData();
  }

  setDucking(enabled) {
    if (Platform.OS === 'windows') {
      return Promise.resolve(true);
    }
    return TextToSpeech.setDucking(enabled);
  }

  setDefaultEngine(engineName) {
    if (Platform.OS === 'ios' || Platform.OS === 'windows') {
      return Promise.resolve(true);
    }
    return TextToSpeech.setDefaultEngine(engineName);
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

  setIgnoreSilentSwitch(ignoreSilentSwitch) {
    if (Platform.OS === 'ios' || Platform.OS === 'windows') {
      return TextToSpeech.setIgnoreSilentSwitch(ignoreSilentSwitch);
    }
    return Promise.resolve(true);
  }

  voices() {
    return TextToSpeech.voices();
  }

  engines() {
    if (Platform.OS === 'ios' || Platform.OS === 'windows') {
      return Promise.resolve([]);
    }
    return TextToSpeech.engines();
  }

  speak(utterance, options = {}) {
    // compatibility with old-style voiceId argument passing
    if (typeof options === 'string') {
      if (Platform.OS === 'ios') {
        return TextToSpeech.speak(utterance, { iosVoiceId: options });
      } else {
        return TextToSpeech.speak(utterance, {});
      }
    } else {
      if (Platform.OS === 'ios' || Platform.OS === 'windows') {
        return TextToSpeech.speak(utterance, options);
      } else {
        return TextToSpeech.speak(utterance, options.androidParams || {});
      }
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
    return Promise.resolve(false);
  }

  resume() {
    if (Platform.OS === 'ios') {
      return TextToSpeech.resume();
    }
    return Promise.resolve(false);
  }

  addEventListener(type, handler) {
    return this.addListener(type, handler);
  }

  removeEventListener(type, handler) {
    this.removeListener(type, handler);
  }
}

export default new Tts();
