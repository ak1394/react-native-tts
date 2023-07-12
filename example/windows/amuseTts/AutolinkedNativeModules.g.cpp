// AutolinkedNativeModules.g.cpp contents generated by "react-native autolink-windows"
// clang-format off
#include "pch.h"
#include "AutolinkedNativeModules.g.h"

// Includes from @react-native-community/slider
#include <winrt/SliderWindows.h>

// Includes from react-native-tts
#include <winrt/RNTTS.h>

namespace winrt::Microsoft::ReactNative
{

void RegisterAutolinkedNativeModulePackages(winrt::Windows::Foundation::Collections::IVector<winrt::Microsoft::ReactNative::IReactPackageProvider> const& packageProviders)
{ 
    // IReactPackageProviders from @react-native-community/slider
    packageProviders.Append(winrt::SliderWindows::ReactPackageProvider());
    // IReactPackageProviders from react-native-tts
    packageProviders.Append(winrt::RNTTS::ReactPackageProvider());
}

}
