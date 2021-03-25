Pod::Spec.new do |s|
  s.name         = "TextToSpeech"
  s.version      = "4.1.1"
  s.summary      = "React Native Text-To-Speech library for Android and iOS"

  s.homepage     = "https://github.com/ak1394/react-native-tts"

  s.license      = "MIT"
  s.authors      = "Anton Krasovsky"
  s.platform     = :ios, "9.0"

  s.source       = { :git => "https://github.com/ak1394/react-native-tts.git" }

  s.source_files  = "ios/TextToSpeech/*.{h,m}"

  s.dependency 'React-Core'
end
