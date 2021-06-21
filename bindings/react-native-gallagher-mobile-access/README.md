# react-native-gallagher-mobile-access

React Native bindings for the Gallagher Mobile Connect SDK

### <span style='color:red'>Important</span>
These react bindings are provided without warranty or guarantee. They were created as an open source effort to help other developers get started with the SDK more quickly. 

<p style='font-weight: bold'>
Gallagher are not React-Native experts, which means the following:
</p>

1. There is no official support for this package. If you find bugs or things that don't work correctly, please file a Github Issue against the project outlining the problem, then ideally you might also submit a pull request to resolve it.

2. Should you encouter encounter problems compiling or running the react-native integration (such as with Xcode, CocoaPods, yarn, npm, or other tooling), Gallagher will almost certainly **not** be able to help you.

3. The quality of the code in the example application is not of production quality. It does not follow React-Native best practices, style, idioms, or possess any other such qualities.  
You should not use it as a reference for UI structure, program design, or for anything other than a learning vehicle.

4. If you are an experienced React Native developer, and have suggestions about how it would be better to re-structure the sample code and/or the bindings library, *please* file a Github issue/pull request to help improve this. It will be appreciated.

## Getting Started

First, make sure you can build and compile the example app.  
This will make sure your dev system is set up correctly.

*Prerequisite: Static Library*

1. Unzip the static library that you obtained from Gallagher. It should have a folder structure as follows:
```
GallagherMobileAccess.swiftmodule/
        *.swiftmodule, swiftinterface, etc
iphoneos/
        libGallagherMobileAccess.a
iphonesimulator/
        libGallagherMobileAccess.a
```

Edit the file `bindings/react-native-gallagher-mobile-access/react-native-gallagher-mobile-access.podspec`.
It wants to know where to find the static library files; Edit the podspec appropriately

```ruby
# Change this to point to the relative place where you've put the GallagherMobileAccess static library files
native_lib_path = "/Users/yourname/yourpath"
```

## Building the bindings and example app

```sh
cd bindings/react-native-gallagher-mobile-access 
yarn

# At this point you should see a bunch of log output:
# Resolving packages...
# Building fresh packages..
# bob build
# ...
# Wrote definition files to lib/typescript
# ...
# > pod install
# Auto-linking React Native modules for target `GallagherMobileAccessExample
# ...
# Pod installation complete! There are 55 dependencies from the Podfile and 46 total pods installed.
# ✨  Done in 34.45s.

cd ../../example
yarn
# ✨  Done in 8.23s.
```

Once you have used yarn to get everything configured, launch react native as follows:

1. In one terminal window, launch `npx react-native start`.  
You should see the Metro bundler splash screen appear. Leave it running and open a second terminal tab/window

2. In a second terminal window, launch `npx react-native run-ios`.  
This will launch the iPhone simulator, and then begin the Xcode build/compile process. The first time you run this, it will likely take several minutes, then you should see the example app launch in the simulator.  
**Unfortunately**, The Mobile Connect SDK will fail to register credentials on some mac computers. It uses the Apple Secure Enclave to store private key material, which many Intel macs do not have. We advise you therefore, to run and debug on a physical phone.

3. Connect a physical device, then Open Xcode (tested with Xcode 12.5), and open `example/ios/GallagherMobileAccessExample.xcworkspace`.  
Select the "GallagherMobileAccessExample" target, select the physical phone as the target device, and build/run.  
You will need to change the Apple development Team ID to your own, rather than Gallagher's.

If everything goes according to plan, you will see the example app launch on your phone.

You should then follow the [Developer Guide][ios-dev-guide] for the native SDK, translating method calls into JavaScript as appropriate. Look at the typescript bindings in `src/typescript/index.d.ts` for the translated method call names and signatures.

*Note: the ios developer guide mentions linking libraries, and the RxSwift dependency. You do not need to do any of this for the React Native integration, as it is managed by CocoaPods*

## Integrating the bindings into your own app

1. Copy or otherwise load the `bindings/react-native-gallagher-mobile-access` folder into your source control

2. In your app's `podfile`, add the following (adjusting the file path as appropriate):

```ruby
pod 'react-native-gallagher-mobile-access', :path => '../../bindings/react-native-gallagher-mobile-access'
```

3. From your app, do the following (adjusting the file path as appropriate):

```js
import GallagherMobileAccess from '../../bindings/react-native-gallagher-mobile-access';
```

4. As per the native iOS developer guide, you'll need to put some things in your `Info.plist`, etc.

## Usage

In your `App` file, before your exported `App()` function, you will need to import and configure the SDK.  
After which point you can enable BLE scanning and Automatic Access requests.

Look at `App.tsx` in the example project for more information.

```ts
import GallagherMobileAccess from "react-native-gallagher-mobile-access";

// ...

GallagherMobileAccess.configure(null, null, null);

function App() {
  useEffect(async () => {
    GallagherMobileAccess.setScanning(true);
    GallagherMobileAccess.setAutomaticAccessEnabled(true);

    const states = await GallagherMobileAccess.getStates();
    console.log("sdk states:" + JSON.stringify(states));
  }, []);
};
```

You will then need to register a credential, using the `registerCredential` method.

**NOTE:** The React Native SDK bindings diverge here from the native Swift/Java bindings.

In the native Swift/Java SDK, you call `registerCredential` and supply two callbacks, which may get called in sequence. The native SDK does this:

1. `onAuthenticationTypeSelectionRequested` callback - Optionally called if the SDK wants you to prompt the user to choose between PIN or Fingerprint as a second factor authentication method.
2. `onRegistrationCompleted` callback - called after registration completes, to inform you of success, or an error

This two-callback paradigm does not map well to JavaScript, which prefers to use promises and async/await, and in particular, React Native's bridging mechanism, which wants to have functions that return either a single promise, or invoke a single callback.

As such, the registration flow has been rewritten into a two-step method:

1. You call `registerCredential`, which returns a `Promise` that you can `await`
3. If the credential does not require a second factor, the reigstration should succeed, and the promise will resolve with a JS object with `{ "completed": true, "credential": { ...} }`
3. Alternatively, if the SDK wants you to ask the user to choose a second factor authentication method, it will resolve the promise with a JS object with `{ "completed": false, "continuationPoint": "<opaque string>"}`.  
At this point, you should display your user interface and ask the user to select a method.  
When they do, you must then call `registerCredentialContinue`, supplying the continuation point, and the result. This returns a second `Promise` which will then resolve with `{ "completed": true, "credential": { ...} }`

```ts
try {
    const url = "<invitation url from the Command Centre REST api>";
    const response = await GallagherMobileAccess.registerCredential(url);
    if(!response.completed && "continuationPoint" in response) {
        const authenticationType = await PromptUserToSelectMethod(); // your UI code goes here.
        
        await GallagherMobileAccess.registerCredentialContinue(response.continuationPoint, true, authenticationType);
    }
    // registered successfully
} catch (error) {
    // failed
}
```

[ios-dev-guide]: https://gallaghersecurity.github.io/mobileconnectsdk-docs/docs/ios/sdk-docs/developer-guide.html
