import { windowsAppDriverCapabilities } from 'selenium-appium'

switch (platform) {
  case "windows":
    const webViewWindowsAppId = 'ReactNativeTtsWinExample_dz2e3dnknw1k2!App';
    module.exports = {
      capabilites: windowsAppDriverCapabilities(webViewWindowsAppId)
    }
    break;
  default:
    throw "Unknown platform: " + platform;
}
