require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "TextToSpeech"
  s.version      = package["version"]
  s.summary      = package["description"]

  s.homepage     = package["homepage"]

  s.license      = package["license"]
  s.authors      = package["author"]
  s.platform     = :ios, "9.0"

  s.source       = { :git => "https://github.com/Iternio-Planning-AB/react-native-tts.git" }

  s.source_files  = "ios/TextToSpeech/*.{h,m}"

  s.dependency 'React-Core'
end
