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

export type AndroidOptions = {
  /** Parameter key to specify the audio stream type to be used when speaking text or playing back a file */
  KEY_PARAM_STREAM:
    | "STREAM_VOICE_CALL"
    | "STREAM_SYSTEM"
    | "STREAM_RING"
    | "STREAM_MUSIC"
    | "STREAM_MUSIC"
    | "STREAM_ALARM"
    | "STREAM_NOTIFICATION"
    | "STREAM_DTMF"
    | "STREAM_ACCESSIBILITY";
  /** Parameter key to specify the speech volume relative to the current stream type volume used when speaking text. Volume is specified as a float ranging from 0 to 1 where 0 is silence, and 1 is the maximum volume (the default behavior). */
  KEY_PARAM_VOLUME: number;
  /** Parameter key to specify how the speech is panned from left to right when speaking text. Pan is specified as a float ranging from -1 to +1 where -1 maps to a hard-left pan, 0 to center (the default behavior), and +1 to hard-right. */
  KEY_PARAM_PAN: number;
};

export type Options =
  | string
  | {
      iosVoiceId: string;
      rate: number;
      androidParams: AndroidOptions;
    };

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
  /** Read the sentence and return an id for the task. */
  speak: (utterance: string, options?: Options) => string | number;
  stop: (onWordBoundary?: boolean) => Promise<boolean>;
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
