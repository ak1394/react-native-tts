//
//  TextToSpeech.m
//  TextToSpeech
//
//  Created by Anton Krasovsky on 27/09/2016.
//  Copyright © 2016 Anton Krasovsky. All rights reserved.
//

#import "RCTBridge.h"
#import "RCTEventDispatcher.h"
#import "RCTLog.h"

#import "TextToSpeech.h"

@implementation TextToSpeech

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE()

-(NSArray<NSString *> *)supportedEvents
{
    return @[@"started", @"finished", @"paused", @"resumed", @"speaking", @"cancelled"];
}

-(instancetype)init
{
    self = [super init];
    if (self) {
        _synthesizer = [AVSpeechSynthesizer new];
        _synthesizer.delegate = self;
    }
    
    return self;
}

RCT_EXPORT_METHOD(speak:(NSDictionary *)args
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    NSString *text = args[@"text"];
    NSString *language = args[@"language"];

    if(!text) {
        reject(@"no_text", @"No text to speak", nil);
        return;
    }

    if(!language) {
        language = @"en-US";
    }

    AVSpeechUtterance *utterance = [[AVSpeechUtterance alloc] initWithString:text];

    utterance.voice = [AVSpeechSynthesisVoice voiceWithLanguage:language];
    
    [self.synthesizer speakUtterance:utterance];
    resolve([NSNumber numberWithUnsignedLong:utterance.hash]);
}

RCT_EXPORT_METHOD(stop:(BOOL *)immediately resolve:(RCTPromiseResolveBlock)resolve reject:(__unused RCTPromiseRejectBlock)reject)
{
    AVSpeechBoundary *boundary;
    if(!immediately) {
        boundary = AVSpeechBoundaryWord;
    } else {
        boundary = AVSpeechBoundaryImmediate;
    }
    BOOL *stopped = [self.synthesizer stopSpeakingAtBoundary:boundary];

    resolve([NSNumber numberWithBool:stopped]);
}

RCT_EXPORT_METHOD(pause:(BOOL *)immediately resolve:(RCTPromiseResolveBlock)resolve reject:(__unused RCTPromiseRejectBlock)reject)
{
    AVSpeechBoundary *boundary;
    if(!immediately) {
        boundary = AVSpeechBoundaryWord;
    } else {
        boundary = AVSpeechBoundaryImmediate;
    }
    BOOL *paused = [self.synthesizer pauseSpeakingAtBoundary:boundary];

    resolve([NSNumber numberWithBool:paused]);
}

RCT_EXPORT_METHOD(resume:(RCTPromiseResolveBlock)resolve reject:(__unused RCTPromiseRejectBlock)reject)
{
    BOOL *continued = [self.synthesizer continueSpeaking];

    resolve([NSNumber numberWithBool:continued]);
}

RCT_EXPORT_METHOD(voices:(RCTPromiseResolveBlock)resolve
                  reject:(__unused RCTPromiseRejectBlock)reject)
{
    NSMutableArray *voices = [NSMutableArray new];
    for (AVSpeechSynthesisVoice *voice in [AVSpeechSynthesisVoice speechVoices]) {
        [voices addObject:@{@"id": voice.identifier, @"name": voice.name, @"language": voice.language}];
    }
    resolve(voices);
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didStartSpeechUtterance:(AVSpeechUtterance *)utterance
{
    [self sendEventWithName:@"started" body:[NSNumber numberWithUnsignedLong:utterance.hash]];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didFinishSpeechUtterance:(AVSpeechUtterance *)utterance
{
    [self sendEventWithName:@"finished" body:[NSNumber numberWithUnsignedLong:utterance.hash]];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didPauseSpeechUtterance:(AVSpeechUtterance *)utterance
{
    [self sendEventWithName:@"paused" body:[NSNumber numberWithUnsignedLong:utterance.hash]];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didContinueSpeechUtterance:(AVSpeechUtterance *)utterance
{
    [self sendEventWithName:@"resumed" body:[NSNumber numberWithUnsignedLong:utterance.hash]];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer willSpeakRangeOfSpeechString:(NSRange)characterRange utterance:(AVSpeechUtterance *)utterance
{
    [self sendEventWithName:@"speaking"
                       body:@{@"location": [NSNumber numberWithUnsignedLong:characterRange.location],
                              @"length": [NSNumber numberWithUnsignedLong:characterRange.length],
                              @"utterance": [NSNumber numberWithUnsignedLong:utterance.hash]}];
}

-(void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didCancelSpeechUtterance:(AVSpeechUtterance *)utterance
{
    [self sendEventWithName:@"cancelled" body:[NSNumber numberWithUnsignedLong:utterance.hash]];
}

@end
