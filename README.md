# Overview

This community-supported software is provided to you under the terms of the [MIT License][license]. Accordingly, it is offered with no warranty.  

Gallagher moderates this source code repository, and at times Gallagher employees may contribute source code changes, but this does not constitute official support nor a commitment to maintain the software. 

If you encounter a problem with the software you are encouraged to submit a Pull Request containing a proposed fix to help ensure the success of this community project.

[license]: LICENSE

## Android Bindings

Android Bindings are not yet implemented. Work thus far has concentrated on iOS

## iOS Bindings

**Architecture Note:**

Ideally, a RN iOS app would be structured something like this:

**YourApp** -> **react-native-gallagher-mobile-access wrapper** -> **MobileConnectSdk**

Current versions of React-Native integrate everything as static libraries via CocoaPods.  
"Binding" libraries are also expected to be built as static libraries.

Traditionally, the Gallagher Mobile Connect SDK ships as a dynamically linked XCFramework. Xcode cannot link from a static -> dynamic library, so it is not possible to do the above.  
Rather, you would have to copy and paste the bridging/wrapper code directly into your app, which is not great.

Instead, Gallagher will need to provide a specially compiled static library version of the Mobile Connect SDK, enabling the app->wrapper->sdk pattern as above. Please contact Gallagher to obtain a copy.

To get started, please read [README.md][bindings-readme] under the **react-native-galllagher-mobile-access** directory.


# Status

Note: A tick means that code exists for the feature. It does not indicate quality or testing.

| API                      | Android Binding |iOS Binding | Sample App |
| ------------------------ | --------------- |----------- | -----------|
| SDK initialisation       | -               | ✅         | ✅          |
| localisation             | -               | -          | -          |
| unlock notification config| ?              | ?          | ?          |
| register Credential      | -               | ✅         | ✅         |
| delete Credential        | -               | ✅         | -          |
| list Credentials         | -               | ✅         | ✅         |
| sdk state feedback       | -               | ✅         | ✅         |
| permissions              | -               |            | -         | 
| enable BLE scanning      | -               | ✅         | ✅         |
| enable BLE background    | -               | ✅         | -          |
| automatic access         | -               | ✅         | ✅         |
| automatic access feedback| -               | ✅         | ✅         |
| nearby reader feedback   | -               | ✅         | ✅         |
| manual access            | -               | -          | -          |
| manual access feedback   | -               | -          | -          |
| SALTO integration        | -               | -          | -          |
| Digital ID               | -               | -          | -          |
| Cross-site credentials   | -               | -          | -          |
| Android NFC              | -               | n/a        | n/a        |


[license]: LICENSE
[bindings]: bindings/react-native-gallagher-mobile-access
[bindings-readme]: bindings/react-native-gallagher-mobile-access/README.md
