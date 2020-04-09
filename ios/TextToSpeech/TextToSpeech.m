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

    NSString* voice = [params valueForKey:@"iosVoiceId"];
    if (voice) {
        utterance.voice = [AVSpeechSynthesisVoice voiceWithIdentifier:voice];
    } else if (_defaultVoice) {
        utterance.voice = _defaultVoice;
    }

    float rate = [[params valueForKey:@"rate"] floatValue];
    if (rate) {
        if(rate > AVSpeechUtteranceMinimumSpeechRate && rate < AVSpeechUtteranceMaximumSpeechRate) {
            utterance.rate = rate;
        } else {
            reject(@"bad_rate", @"Wrong rate value", nil);
            return;
        }
    } else if (_defaultRate) {
        utterance.rate = _defaultRate;
    }

    if (_defaultPitch) {
        utterance.pitchMultiplier = _defaultPitch;
    }

    if([_ignoreSilentSwitch isEqualToString:@"ignore"]) {
        [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:nil];
    } else if([_ignoreSilentSwitch isEqualToString:@"obey"]) {
        [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryAmbient error:nil];
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

    if(ducking) {
        AVAudioSession *session = [AVAudioSession sharedInstance];
        [session setCategory:AVAudioSessionCategoryPlayback
                 withOptions:AVAudioSessionCategoryOptionDuckOthers
                       error:nil];
    }

    resolve(@"success");
}


RCT_EXPORT_METHOD(setDefaultLanguage:(NSString *)language
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    AVSpeechSynthesisVoice *voice = [AVSpeechSynthesisVoice voiceWithLanguage:language];

    if(voice) {
        _defaultVoice = voice;
        resolve(@"success");
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
    if(_ducking) {
        [[AVAudioSession sharedInstance] setActive:YES error:nil];
    }

    [self sendEventWithName:@"tts-start" body:@{@"utteranceId":[NSNumber numberWithUnsignedLong:utterance.hash]}];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didFinishSpeechUtterance:(AVSpeechUtterance *)utterance
{
    if(_ducking) {
        [[AVAudioSession sharedInstance] setActive:NO error:nil];
    }

    [self sendEventWithName:@"tts-finish" body:@{@"utteranceId":[NSNumber numberWithUnsignedLong:utterance.hash]}];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didPauseSpeechUtterance:(AVSpeechUtterance *)utterance
{
    if(_ducking) {
        [[AVAudioSession sharedInstance] setActive:NO error:nil];
    }

    [self sendEventWithName:@"tts-pause" body:@{@"utteranceId":[NSNumber numberWithUnsignedLong:utterance.hash]}];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didContinueSpeechUtterance:(AVSpeechUtterance *)utterance
{
    if(_ducking) {
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
    if(_ducking) {
        [[AVAudioSession sharedInstance] setActive:NO error:nil];
    }

    [self sendEventWithName:@"tts-cancel" body:@{@"utteranceId":[NSNumber numberWithUnsignedLong:utterance.hash]}];
}

@end
