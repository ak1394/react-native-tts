//
//  TextToSpeech.h
//  TextToSpeech
//
//  Created by Anton Krasovsky on 27/09/2016.
//  Copyright © 2016 Anton Krasovsky. All rights reserved.
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@import AVFoundation;

@interface TextToSpeech : RCTEventEmitter <RCTBridgeModule, AVSpeechSynthesizerDelegate>
@property (nonatomic, strong) AVSpeechSynthesizer *synthesizer;
@property (nonatomic, strong) AVSpeechSynthesisVoice *defaultVoice;
@property (nonatomic) float defaultRate;
@property (nonatomic) float defaultPitch;
@property (nonatomic) bool ducking;
@end
