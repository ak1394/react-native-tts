//
//  TextToSpeech.h
//  TextToSpeech
//
//  Created by Anton Krasovsky on 27/09/2016.
//  Copyright © 2016 Anton Krasovsky. All rights reserved.
//

#import "RCTBridgeModule.h"
#import "RCTEventEmitter.h"

@import AVFoundation;

@interface TextToSpeech : RCTEventEmitter <RCTBridgeModule, AVSpeechSynthesizerDelegate>
@property (nonatomic, strong) AVSpeechSynthesizer *synthesizer;
@property (nonatomic, strong) AVSpeechSynthesisVoice *defaultVoice;
@end
