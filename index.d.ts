import * as RN from "react-native";

type SimpleEvents = "tts-start" | "tts-finish" | "tts-error" | "tts-cancel";
type SimpleEvent = {
  utteranceId: string | number;
};

type ProgressEventName = "tts-progress";
type ProgressEvent = {
  utteranceId: string | number;
  location: number;
  length: number;
};

export type TtsEvents = SimpleEvents | ProgressEventName;
export type TtsEvent<
  T extends TtsEvents = TtsEvents
> = T extends ProgressEventName ? ProgressEvent : SimpleEvent;
export type TtsEventHandler<T extends TtsEvents = TtsEvents> = (
  event: TtsEvent<T>
) => any;

export type TtsError = {
  code:
    | "no_engine"
    | "error"
    | "not_ready"
    | "invalid_request"
    | "network_error"
    | "network_timeout"
    | "not_installed_yet"
    | "output_error"
    | "service_error"
    | "synthesis_error"
    | "lang_missing_data"
    | "lang_not_supported"
    | "Android AudioManager error"
    | "not_available"
    | "not_found"
    | "bad_rate";
  message: string;
};

export type Voice = {
  id: string;
  name: string;
  language: string;
  quality: number;
  latency: number;
  networkConnectionRequired: boolean;
  notInstalled: boolean;
};

export type Engine = {
  name: string;
  label: string;
  default: boolean;
  icon: number;
};

export type Options = {
  KEY_OPTION_FORCE_PHONE_SPEAKER?: boolean;
  KEY_OPTION_CAR_AUDIO_SYSTEM?: boolean;
  KEY_OPTION_VOLUME?: number;
  KEY_OPTION_AUDIO_MANAGEMENT?: boolean;
}

export class ReactNativeTts extends RN.NativeEventEmitter {
  getInitStatus: () => Promise<"success">;
  requestInstallEngine: () => Promise<"success">;
  requestInstallData: () => Promise<"success">;
  setDucking: (enabled: boolean) => Promise<"success">;
  setDefaultEngine: (engineName: string) => Promise<boolean>;
  setDefaultVoice: (voiceId: string) => Promise<"success">;
  setDefaultRate: (rate: number, skipTransform?: boolean) => Promise<"success">;
  setDefaultPitch: (pitch: number) => Promise<"success">;
  setDefaultLanguage: (language: string) => Promise<"success">;
  setIgnoreSilentSwitch: (ignoreSilentSwitch: boolean) => Promise<boolean>;
  voices: () => Promise<Voice[]>;
  engines: () => Promise<Engine[]>;
  getHash: (utterance: string) => string | number;
  /** Read the sentence and return an id for the task. */
  speak: (utterance: string, options?: Options) => string | number;
  async stop: (onWordBoundary?: boolean) => Promise<boolean>;
  pause: (onWordBoundary?: boolean) => Promise<boolean>;
  resume: () => Promise<boolean>;
  addEventListener: <T extends TtsEvents>(
    type: T,
    handler: TtsEventHandler<T>
  ) => void;
  removeEventListener: <T extends TtsEvents>(
    type: T,
    handler: TtsEventHandler<T>
  ) => void;
}

declare const Tts: ReactNativeTts;

export default Tts;
