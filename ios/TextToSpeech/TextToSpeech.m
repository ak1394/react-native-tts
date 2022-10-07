//
//  TextToSpeech.m
//  TextToSpeech
//
//  Created by Anton Krasovsky on 27/09/2016.
//  Copyright © 2016 Anton Krasovsky. All rights reserved.
//

#import <React/RCTBridge.h>
#import <React/RCTEventDispatcher.h>
#import <React/RCTLog.h>

#import "TextToSpeech.h"

@implementation TextToSpeech {
    NSString * _ignoreSilentSwitch;
}

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE()

-(NSArray<NSString *> *)supportedEvents
{
    return @[@"tts-start", @"tts-finish", @"tts-pause", @"tts-resume", @"tts-progress", @"tts-cancel"];
}

-(instancetype)init
{
    self = [super init];
    if (self) {
        _synthesizer = [AVSpeechSynthesizer new];
        _synthesizer.delegate = self;
        _ducking = false;
        _ignoreSilentSwitch = @"inherit"; // inherit, ignore, obey
        _useAudioSession = true;
    }

    return self;
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

RCT_EXPORT_METHOD(speak:(NSString *)text
                  params:(NSDictionary *)params
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    if(!text) {
        reject(@"no_text", @"No text to speak", nil);
        return;
    }

    AVSpeechUtterance *utterance = [[AVSpeechUtterance alloc] initWithString:text];

    if (_defaultVoice) {
        utterance.voice = _defaultVoice;
    }

     if (_defaultRate) {
        utterance.rate = _defaultRate;
    }

    if (_defaultPitch) {
        utterance.pitchMultiplier = _defaultPitch;
    }

    if (_useAudioSession) {
        // ensure that the audio session is always configured correct for TTS usage before playback
        // starts, another audio source with different setup may have been active just before this
        AVAudioSession * audioSession = [AVAudioSession sharedInstance];
        if (_ducking) {
          // Set both DuckOthers and InterruptSpokenAudioAndMixwithOthers for proper interaction with all types of audio that can be active
          [audioSession setCategory:AVAudioSessionCategoryPlayback
                        withOptions:AVAudioSessionCategoryOptionDuckOthers | AVAudioSessionCategoryOptionInterruptSpokenAudioAndMixWithOthers
                              error:nil];
        } else {
          [audioSession setCategory:AVAudioSessionCategoryPlayback
                        withOptions:AVAudioSessionCategoryOptionMixWithOthers
                              error:nil];
        }
        if (@available(iOS 12.0, *)) {
            BOOL carPlay = [[params valueForKey:@"KEY_OPTION_CAR_AUDIO_SYSTEM"] boolValue];
            if (carPlay) {
                [audioSession setMode:AVAudioSessionModeVoicePrompt error:nil];
            } else {
                if (audioSession.mode == AVAudioSessionModeVoicePrompt) {
                    [audioSession setMode:AVAudioSessionModeDefault error:nil];
                }
            }
        }
    }

    [self.synthesizer speakUtterance:utterance];
    resolve([NSNumber numberWithUnsignedLong:utterance.hash]);
}

RCT_EXPORT_METHOD(stop:(BOOL *)onWordBoundary resolve:(RCTPromiseResolveBlock)resolve reject:(__unused RCTPromiseRejectBlock)reject)
{
    AVSpeechBoundary boundary;

    if(onWordBoundary != NULL && onWordBoundary) {
        boundary = AVSpeechBoundaryWord;
    } else {
        boundary = AVSpeechBoundaryImmediate;
    }

    if (!_useAudioSession) {
        // stopping without pausing will give an error on AVAudioSession setActive NO
        // It will still make it inactive but since there is no specific error,
        // it can't be catched and handled separately from other errors.
        // Not calling it for the old useAudioSesson case as it doesn't check errors
        // and would otherwise try to deactivate for both both didPause and didFinish
        [self.synthesizer pauseSpeakingAtBoundary:boundary];
    }
    BOOL stopped = [self.synthesizer stopSpeakingAtBoundary:boundary];

    resolve([NSNumber numberWithBool:stopped]);
}

RCT_EXPORT_METHOD(pause:(BOOL *)onWordBoundary resolve:(RCTPromiseResolveBlock)resolve reject:(__unused RCTPromiseRejectBlock)reject)
{
    AVSpeechBoundary boundary;

    if(onWordBoundary != NULL && onWordBoundary) {
        boundary = AVSpeechBoundaryWord;
    } else {
        boundary = AVSpeechBoundaryImmediate;
    }

    BOOL paused = [self.synthesizer pauseSpeakingAtBoundary:boundary];

    resolve([NSNumber numberWithBool:paused]);
}

RCT_EXPORT_METHOD(resume:(RCTPromiseResolveBlock)resolve reject:(__unused RCTPromiseRejectBlock)reject)
{
    BOOL continued = [self.synthesizer continueSpeaking];

    resolve([NSNumber numberWithBool:continued]);
}


RCT_EXPORT_METHOD(setDucking:(BOOL *)ducking
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(__unused RCTPromiseRejectBlock)reject)
{
    _ducking = ducking;

    // do not set the audio session category here as this is only set just
    // before playback to ensure audio session is setup correctly when
    // another audio source that was active before has different setup

    resolve(@"success");
}

RCT_EXPORT_METHOD(setAudioManagement:(BOOL *)useAudioSession
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(__unused RCTPromiseRejectBlock)reject)
{
    _useAudioSession = useAudioSession;
    resolve(@"success");
}

RCT_EXPORT_METHOD(setDefaultLanguage:(NSString *)language
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    AVSpeechSynthesisVoice *voice = [AVSpeechSynthesisVoice voiceWithLanguage:language];

    if(voice) {
        _defaultVoice = voice;
        // The voice identifier contains the string com.apple.ttsbundle when the voice is a Nuance
        // voice and com.apple.speech.synthesis.voice when it is an Apple voice based on speech synthesis
        // technology from voices before the Nuance voices (Siri voices are not returned using these APIs)
        // Based on the resolve result the caller can decide whether to include Nuance phonemes if available
        if ([voice.identifier containsString:@"com.apple.ttsbundle"]) {
            resolve(@YES);
        } else {
            resolve(@NO);
        }
    } else {
        reject(@"not_found", @"Language not found", nil);
    }
}

RCT_EXPORT_METHOD(setDefaultVoice:(NSString *)identifier
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    AVSpeechSynthesisVoice *voice = [AVSpeechSynthesisVoice voiceWithIdentifier:identifier];

    if(voice) {
        _defaultVoice = voice;
        resolve(@"success");
    } else {
        reject(@"not_found", @"Voice not found", nil);
    }
}

RCT_EXPORT_METHOD(setDefaultRate:(float)rate
                  skipTransform:(BOOL *)skipTransform // not used, compatibility with Android native module signature
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    if(rate > AVSpeechUtteranceMinimumSpeechRate && rate < AVSpeechUtteranceMaximumSpeechRate) {
        _defaultRate = rate;
        resolve(@"success");
    } else {
        reject(@"bad_rate", @"Wrong rate value", nil);
    }
}

RCT_EXPORT_METHOD(setDefaultPitch:(float)pitch
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    if(pitch > 0.5 && pitch < 2.0) {
        _defaultPitch = pitch;
        resolve(@"success");
    } else {
        reject(@"bad_rate", @"Wrong pitch value", nil);
    }
}

RCT_EXPORT_METHOD(setIgnoreSilentSwitch:(NSString *)ignoreSilentSwitch
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    if(ignoreSilentSwitch) {
        _ignoreSilentSwitch = ignoreSilentSwitch;
        resolve(@"success");
    }
}

RCT_EXPORT_METHOD(voices:(RCTPromiseResolveBlock)resolve
                  reject:(__unused RCTPromiseRejectBlock)reject)
{
    NSMutableArray *voices = [NSMutableArray new];

    for (AVSpeechSynthesisVoice *voice in [AVSpeechSynthesisVoice speechVoices]) {
        [voices addObject:@{
            @"id": voice.identifier,
            @"name": voice.name,
            @"language": voice.language,
            @"quality": (voice.quality == AVSpeechSynthesisVoiceQualityEnhanced) ? @500 : @300
        }];
    }

    resolve(voices);
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didStartSpeechUtterance:(AVSpeechUtterance *)utterance
{
    if(_useAudioSession && _ducking) {
        [[AVAudioSession sharedInstance] setActive:YES error:nil];
    }

    [self sendEventWithName:@"tts-start" body:@{@"utteranceId":[NSNumber numberWithUnsignedLong:utterance.hash]}];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didFinishSpeechUtterance:(AVSpeechUtterance *)utterance
{
    if(_useAudioSession && _ducking) {
        // set option NotifyOthersOnDeactivation to ensure all audio that can be restarted will restart
        [[AVAudioSession sharedInstance] setActive:NO withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];
    }

    [self sendEventWithName:@"tts-finish" body:@{@"utteranceId":[NSNumber numberWithUnsignedLong:utterance.hash]}];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didPauseSpeechUtterance:(AVSpeechUtterance *)utterance
{
    if(_useAudioSession && _ducking) {
        // set option NotifyOthersOnDeactivation to ensure all audio that can be restarted will restart
        [[AVAudioSession sharedInstance] setActive:NO withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];
    }

    [self sendEventWithName:@"tts-pause" body:@{@"utteranceId":[NSNumber numberWithUnsignedLong:utterance.hash]}];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didContinueSpeechUtterance:(AVSpeechUtterance *)utterance
{
    if(_useAudioSession && _ducking) {
        [[AVAudioSession sharedInstance] setActive:YES error:nil];
    }

    [self sendEventWithName:@"tts-resume" body:@{@"utteranceId":[NSNumber numberWithUnsignedLong:utterance.hash]}];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer willSpeakRangeOfSpeechString:(NSRange)characterRange utterance:(AVSpeechUtterance *)utterance
{
    [self sendEventWithName:@"tts-progress"
                       body:@{@"location": [NSNumber numberWithUnsignedLong:characterRange.location],
                              @"length": [NSNumber numberWithUnsignedLong:characterRange.length],
                              @"utteranceId": [NSNumber numberWithUnsignedLong:utterance.hash]}];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didCancelSpeechUtterance:(AVSpeechUtterance *)utterance
{
    if(_useAudioSession && _ducking) {
        // set option NotifyOthersOnDeactivation to ensure all audio that can be restarted will restart
        [[AVAudioSession sharedInstance] setActive:NO withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];
    }

    [self sendEventWithName:@"tts-cancel" body:@{@"utteranceId":[NSNumber numberWithUnsignedLong:utterance.hash]}];
}

@end
