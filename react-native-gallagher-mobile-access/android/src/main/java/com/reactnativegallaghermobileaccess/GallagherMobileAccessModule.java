package com.reactnativegallaghermobileaccess;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.gallagher.security.mobileaccess.AccessMode;
import com.gallagher.security.mobileaccess.AccessResult;
import com.gallagher.security.mobileaccess.AutomaticAccessListener;
import com.gallagher.security.mobileaccess.BluetoothScanMode;
import com.gallagher.security.mobileaccess.CloudTlsValidationMode;
import com.gallagher.security.mobileaccess.CredentialDeleteListener;
import com.gallagher.security.mobileaccess.DeleteOption;
import com.gallagher.security.mobileaccess.FatalError;
import com.gallagher.security.mobileaccess.MobileAccess;
import com.gallagher.security.mobileaccess.MobileAccessProvider;
import com.gallagher.security.mobileaccess.MobileAccessState;
import com.gallagher.security.mobileaccess.MobileCredential;
import com.gallagher.security.mobileaccess.NotificationsConfiguration;
import com.gallagher.security.mobileaccess.Reader;
import com.gallagher.security.mobileaccess.ReaderAttributes;
import com.gallagher.security.mobileaccess.ReaderConnectionError;
import com.gallagher.security.mobileaccess.ReaderDistance;
import com.gallagher.security.mobileaccess.ReaderUpdateListener;
import com.gallagher.security.mobileaccess.ReaderUpdateType;
import com.gallagher.security.mobileaccess.RegistrationError;
import com.gallagher.security.mobileaccess.RegistrationListener;
import com.gallagher.security.mobileaccess.SdkFeature;
import com.gallagher.security.mobileaccess.SdkStateListener;
import com.gallagher.security.mobileaccess.SecondFactorAuthenticationType;
import com.gallagher.security.mobileaccess.SecondFactorAuthenticationTypeSelector;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.reactnativegallaghermobileaccess.GallagherMobileAccessModule.serializeCredential;

@ReactModule(name = GallagherMobileAccessModule.NAME)
public class GallagherMobileAccessModule extends ReactContextBaseJavaModule implements SdkStateListener, ReaderUpdateListener, AutomaticAccessListener {
  public static final String NAME = "GallagherMobileAccess";

  public GallagherMobileAccessModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Nullable
  private MobileAccess mInstance;

  @NonNull
  private final Map<String, AnonymousRegistrationListener> mPendingRegistrations = new HashMap<>();

  // neccessary when working directly on the module itself, as the example app pulls
  // in a copy from NPM, then we want our local dev copy to override it
  @Override
  public boolean canOverrideExistingModule() {
    return BuildConfig.DEBUG;
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  private void sendEvent(@NonNull String eventName, @NonNull Object body) {
    this.getReactApplicationContext()
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, body);
  }

  // ----- SdkStateListener ------------------------------------------------------------------------

  @Override
  public void onStateChanged(boolean isScanning, @NonNull Collection<MobileAccessState> states) {
    WritableMap body = new WritableNativeMap();
    body.putBoolean("isScanning", isScanning);
    body.putArray("states", serializeSdkStates(states));
    Log.i("RNGallagherMobileAccess", "sdkStateChanged: isScanning="+isScanning);
    sendEvent("sdkStateChanged", body);
  }

  // ----- ReaderUpdateListener --------------------------------------------------------------------

  @Override
  public void onReaderUpdated(@NonNull ReaderAttributes reader, @NonNull ReaderUpdateType readerUpdateType) {
    WritableMap body = new WritableNativeMap();
    body.putString("updateType", serializeReaderUpdateType(readerUpdateType));
    body.putMap("reader", serializeReaderAttributes(reader));
    sendEvent("readerUpdated", body);
  }

  // ----- AccessListener --------------------------------------------------------------------------

  @Override
  public void onReturnToReaderRequired(Reader reader) {
    WritableMap body = new WritableNativeMap();
    body.putString("event", "returnToReaderRequired");
    body.putMap("reader", serializeReader(reader));
    sendEvent("access", body);
  }

  @Override
  public void onReturnedToReader(Reader reader) {
    WritableMap body = new WritableNativeMap();
    body.putString("event", "returnToReaderComplete");
    body.putMap("reader", serializeReader(reader));
    sendEvent("access", body);
  }

  @Override
  public void onAccessStarted(@NonNull Reader reader) {
    WritableMap body = new WritableNativeMap();
    body.putString("event", "started");
    body.putMap("reader", serializeReader(reader));
    sendEvent("access", body);
  }

  @Override
  public void onAccessCompleted(@NonNull Reader reader, @Nullable AccessResult accessResult, @Nullable ReaderConnectionError error) {
    WritableMap body = new WritableNativeMap();
    if(error != null) {
      body.putString("event", "error");
      body.putString("message", error.getLocalizedMessage());
      body.putMap("reader", serializeReader(reader));
    } else if(accessResult != null) {
      body.putString("event", accessResult.isAccessGranted() ? "granted" : "denied");
      body.putString("message", accessResult.getAccessDecision().toString());
      body.putInt("code", accessResult.getAccessDecision().getValue());
      body.putMap("reader", serializeReader(reader));
    } else {
      throw new FatalError("onAccessCompleted invoked with both result and error set to null");
    }
    sendEvent("access", body);
  }

  // ----- MobileAccessProvider --------------------------------------------------------------------

  @ReactMethod
  public void configure(@Nullable String databaseFilePath, @Nullable String cloudTlsValidationMode, @Nullable ReadableArray enabledFeatures) {
    if (mInstance != null) {
      Log.d("GallagherMobileAccess", "GallagherMobileAccess already configured; assuming deveveloper reload of JS environment");
      return;
    }

    // TODO add support for unlock notifications
    NotificationsConfiguration notificationsConfiguration = new NotificationsConfiguration(null, null, null, null);

    CloudTlsValidationMode tlsMode = CloudTlsValidationMode.ANY_VALID_CERTIFICATE_REQUIRED;
    if (cloudTlsValidationMode != null) {
      switch (cloudTlsValidationMode) {
        case "anyValidCertificateRequired":
          tlsMode = CloudTlsValidationMode.ANY_VALID_CERTIFICATE_REQUIRED;
        case "gallagherCertificateRequired":
          tlsMode = CloudTlsValidationMode.GALLAGHER_CERTIFICATE_REQUIRED;
        case "allowInvalidCertificate":
          tlsMode = CloudTlsValidationMode.ALLOW_INVALID_CERTIFICATE;
      }
    }

    EnumSet<SdkFeature> sdkFeatures = EnumSet.noneOf(SdkFeature.class);
    if (enabledFeatures != null) {
      for (int i = 0; i < enabledFeatures.size(); i++) {
        String s = enabledFeatures.getString(i); // does this throw if it's not a String?
        switch (s) {
          case "salto":
            sdkFeatures.add(SdkFeature.SALTO);
            break;
          case "digitalId":
            sdkFeatures.add(SdkFeature.DIGITAL_ID);
            break;
        }
      }
    }

    Context appContext = getReactApplicationContext().getApplicationContext();
    MobileAccess instance = MobileAccessProvider.configure(
      (Application) appContext,
      databaseFilePath,
      notificationsConfiguration,
      sdkFeatures,
      tlsMode,
      null);

    mInstance = instance; // must assign mInstance before we hook listeners

    // android-specific:
    // on iOS, the event emitter has a startObserving and stopObserving, which we can use to
    // hook global SDK callbacks like addSdkStateDelegate. RN for Android doesn't seem to have
    // anything like that, so we just hook once upon configuration, and never unhook
    instance.addSdkStateListener(this);
    instance.addReaderUpdateListener(this);
    instance.addAutomaticAccessListener(this);
  }

  // ----- MobileAccess ----------------------------------------------------------------------------

  @ReactMethod
  public void setAutomaticAccessEnabled(boolean enabled) {
    if (mInstance != null) {
      mInstance.setAutomaticAccessEnabled(enabled);
    }
  }

  @ReactMethod
  public void setScanning(boolean enabled) {
    if (mInstance != null) {
      mInstance.setScanning(enabled);
    }
  }

  // TODO: The native android SDK has 6 options here, whilst iOS has two.
  // Because RN provides a single frontend, we need to figure out how best to expose the extra options to android, and map the differences
  @ReactMethod
  public void setBackgroundScanningMode(@NonNull String mode) {
    MobileAccess instance = mInstance;
    if (instance == null) {
      return;
    }
    switch (mode) {
      case "standard":
        instance.setBluetoothBackgroundScanMode(BluetoothScanMode.BACKGROUND_SCREEN_ON);
      case "extended":
        instance.setBluetoothBackgroundScanMode(BluetoothScanMode.BACKGROUND_LOW_LATENCY);
      default:
        instance.setBluetoothBackgroundScanMode(BluetoothScanMode.FOREGROUND_ONLY);
    }
  }

  @ReactMethod
  public void resolveInvitationUrl(@NonNull String host, @NonNull String invitationCode, @NonNull Promise promise) {
    MobileAccess instance = mInstance;
    if (instance == null) {
      promise.reject("not_configured", "GallagherMobileAccess.configure has not been called yet");
      return;
    }
    try {
      URI url = instance.resolveInvitationUri(host, invitationCode);
      promise.resolve(url.toString());
    } catch (URISyntaxException e) {
      promise.reject("invalid_arg", "host or invitationCode was invalid");
    }
  }

  @ReactMethod
  public void registerCredential(@NonNull String url, @NonNull Promise promise) {
    MobileAccess instance = mInstance;
    if (instance == null) {
      promise.reject("not_configured", "GallagherMobileAccess.configure has not been called yet");
      return;
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      promise.reject("invalid_arg", "url was invalid");
      return;
    }

    AnonymousRegistrationListener listener = new AnonymousRegistrationListener(promise) {
      @Override
      public void cleanup(@NonNull String continuationPoint) {
        GallagherMobileAccessModule.this.mPendingRegistrations.remove(continuationPoint);
      }
    };

    this.mPendingRegistrations.put(listener.getmContinuationPoint(), listener);
    instance.registerCredential(uri, listener);

    // asynchronously, one of three things happen

    // 1. The SDK fails (e.g. internet offline) and then calls back on onRegistrationCompletedWithCredential:error with an error set.
    //  - we reject the promise and abort

    // 2. The SDK completes the registration because second factor was not required, and calls back with onRegistrationCompletedWithCredential and a credential
    //  - we resolve the promise with { "completed":true, "credential": { ... } }

    // 3. The SDK needs to ask for second factor
    //  - we resolve the promise with { "completed":false, "continuationPoint": "<random>" }
    //  - JS then sees that, and calls registerCredentialContinue(continuationPoint, secondFactorType)
    //  - registerCredentialContinue uses the continuationPoint to find the correct underlying delegate, then calls the 2f callback
    //  - SDK then completes registration and resolves/rejects that second promise.
  }

  @ReactMethod
  public void registerCredentialContinue(@NonNull String continuationPoint, boolean secondFactorSelected, @NonNull String authenticationType, @NonNull Promise promise) {
    AnonymousRegistrationListener listener = mPendingRegistrations.get(continuationPoint);
    if(listener == null) {
      promise.reject("invalid_continuation_point", "continuationPoint was not valid");
      return;
    }

    SecondFactorAuthenticationType twoFactorType;
    switch (authenticationType) {
      case "pin":
        twoFactorType = SecondFactorAuthenticationType.PIN;
        break;
      case "fingerprint":
      case "faceId": // android doesn't have faceId, but keep the API consistent across platforms
      case "fingerprintOrFaceId":
      case "touchId":
        twoFactorType = SecondFactorAuthenticationType.FINGERPRINT;
        break;
      default:
        twoFactorType = SecondFactorAuthenticationType.PIN;
        break;
    }

    mPendingRegistrations.remove(continuationPoint); // don't need it anymore
    listener.continueRegistration(secondFactorSelected, twoFactorType, promise);
  }

  @ReactMethod
  public void getStates(@NonNull Promise promise) {
    MobileAccess instance = mInstance;
    if (instance == null) {
      promise.reject("not_configured", "GallagherMobileAccess.configure has not been called yet");
      return;
    }
    promise.resolve(serializeSdkStates(instance.getMobileAccessStates()));
  }

  @ReactMethod
  public void getCredentials(@NonNull Promise promise) {
    MobileAccess instance = mInstance;
    if (instance == null) {
      promise.reject("not_configured", "GallagherMobileAccess.configure has not been called yet");
      return;
    }
    Collection<MobileCredential> credentials = instance.getMobileCredentials();
    WritableArray result = new WritableNativeArray();
    for(MobileCredential credential : credentials) {
      result.pushMap(serializeCredential(credential));
    }
    promise.resolve(result);
  }

  @ReactMethod
  public void deleteCredential(@NonNull String credentialId, @NonNull Promise promise) {
    MobileAccess instance = mInstance;
    if (instance == null) {
      promise.reject("not_configured", "GallagherMobileAccess.configure has not been called yet");
      return;
    }
    @Nullable MobileCredential candidate = null;
    for(MobileCredential credential: instance.getMobileCredentials()) {
      if(credential.getId().equals(credentialId)) {
        candidate = credential;
        break;
      }
    }
    if (candidate == null) {
      promise.reject("invalid_arg", "credentialId was invalid");
      return;
    }
    instance.deleteMobileCredential(candidate, DeleteOption.DEFAULT, new CredentialDeleteListener() {
      @Override
      public void onCredentialDeleteCompleted(@Nullable MobileCredential credential, @Nullable Throwable error) {
        if (error != null) {
          promise.reject("delete_mobile_credential_failed", error.getLocalizedMessage(), error);
        } else if(credential != null) {
          promise.resolve(serializeCredential(credential));
        } else {
          throw new FatalError("unexpected args in onCredentialDeleteCompleted");
        }
      }
    });
  }

  @ReactMethod
  public void requestAccess(@NonNull ReadableMap reader) {

  }

  // ----- Serialization Helpers -------------------------------------------------------------------

  @NonNull
  static WritableArray serializeSdkStates(@NonNull Collection<MobileAccessState> states) {
    WritableArray result = new WritableNativeArray();
    for(MobileAccessState state: states) {
      switch (state) {
        case ERROR_DEVICE_NOT_SUPPORTED:
          result.pushString("errorDeviceNotSupported");
          break;
        case ERROR_NO_PASSCODE_SET:
          result.pushString("errorNoPasscodeSet");
          break;
        case ERROR_NO_CREDENTIALS:
          result.pushString("errorNoCredentials");
          break;
        case ERROR_UNSUPPORTED_OS_VERSION:
          result.pushString("errorUnsupportedOsVersion");
          break;
        case ERROR_NO_BLE_FEATURE:
          result.pushString("errorNoBleFeature");
          break;
        case BLE_ERROR_LOCATION_SERVICE_DISABLED:
          result.pushString("bleErrorLocationServiceDisabled");
          break;
        case BLE_ERROR_NO_LOCATION_PERMISSION:
          result.pushString("bleErrorNoLocationPermission");
          break;
//        case .bleWarningExtendedBackgroundScanningRequiresLocationServiceEnabled: return
        case EXTENDED_BACKGROUND_SCANNING_REQUIRES_LOCATION_SERVICES:
          result.pushString("bleWarningExtendedBackgroundScanningRequiresLocationServiceEnabled");
          break;
//        case .bleWarningExtendedBackgroundScanningRequiresLocationAlwaysPermission: return "bleWarningExtendedBackgroundScanningRequiresLocationAlwaysPermission"
        case BLE_ERROR_DISABLED:
          result.pushString("bleErrorDisabled");
          break;
        case BLE_ERROR_UNAUTHORIZED:
          result.pushString("bleErrorUnauthorized");
          break;
        case NFC_ERROR_DISABLED:
          result.pushString("nfcErrorDisabled");
          break;
        case NO_NFC_FEATURE:
          result.pushString("noNfcFeature");
          break;
        case CREDENTIAL_REQUIRES_BIOMETRICS_ENROLMENT:
          result.pushString("credentialRequiresBiometricsEnrolment");
          break;
//        case .credentialBiometricsLockedOut: return "credentialBiometricsLockedOut"
        case BLE_ERROR_NO_BACKGROUND_LOCATION_PERMISSION:
          result.pushString("bleErrorNoBackgroundLocationPermission");
          break;
        default:
          result.pushString("unknown:" + state.toString());
          break;
      }
    }
    return result;
  }

  @NonNull
  static String serializeAccessMode(@NonNull AccessMode mode) {
    switch (mode) {
      case EVAC: return "evac";
      case ACCESS: return "access";
      case CHALLENGE: return "challenge";
      case SEARCH: return "search";
      default: return "unknown";
    }
  }

  @NonNull
  static String serializeReaderUpdateType(@NonNull ReaderUpdateType updateType) {
    switch (updateType) {
      case ATTRIBUTES_CHANGED: return "attributesChanged";
      case READER_UNAVAILABLE: return "readerUnavailable";
      default: return "";
    }
  }

  @NonNull
  static String serializeReaderDistance(@NonNull ReaderDistance distance) {
    switch(distance) {
      case FAR: return "far";
      case MEDIUM: return "medium";
      case NEAR: return "near";
      default: return "";
    }
  }

  @NonNull
  static WritableMap serializeReader(@NonNull Reader reader) {
    WritableMap result = new WritableNativeMap();
    result.putString("id", reader.getId());
    result.putString("name", reader.getName());
    return result;
  }

  @NonNull
  static WritableMap serializeReaderAttributes(@NonNull ReaderAttributes reader) {
    WritableMap result = new WritableNativeMap();
    result.putString("id", reader.getId());
    result.putString("name", reader.getName());
    result.putDouble("measuredPathLoss", reader.getMeasuredPathLoss());
    result.putString("distance", serializeReaderDistance(reader.getReaderDistance()));
    result.putDouble("autoConnectPathLoss", reader.getAutoConnectPathLoss());
    result.putDouble("manualConnectPathLoss", reader.getManualConnectPathLoss());
    result.putBoolean("isBleManualConnectEnabled", reader.isBleManualConnectEnabled());
    result.putBoolean("isBleAutoConnectEnabled", reader.isBleAutoConnectEnabled());
    result.putBoolean("isSecondFactorRequired", reader.isSecondFactorRequired());
    result.putBoolean("isBleActionsEnabled", reader.isBleActionsEnabled());
    return result;
  }

  @NonNull
  static String credentialDateToString(@NonNull Date date) {
    // TODO a nicer string format. Should this be ISO8601 so JS can reformat?
    return date.toString();
  }

  @NonNull
  static WritableMap serializeCredential(@NonNull MobileCredential credential) {
    WritableMap result = new WritableNativeMap();
    result.putString("id", credential.getId());
    result.putInt("facilityId", credential.getFacilityId());
    result.putString("facilityName", credential.getFacilityName());
    result.putBoolean("isRevoked", credential.isRevoked());
    result.putString("registeredDate", credentialDateToString(credential.getRegisteredDate()));
    return result;
  }
}

abstract class AnonymousRegistrationListener implements RegistrationListener {
  @NonNull
  private Promise mPromise; // mutable

  @NonNull
  private final String mContinuationPoint;

  @Nullable
  private SecondFactorAuthenticationTypeSelector mTwoFactorSelector;

  public AnonymousRegistrationListener(@NonNull Promise promise) {
    this.mPromise = promise;
    this.mContinuationPoint = UUID.randomUUID().toString();
  }

  public abstract void cleanup(@NonNull String continuationPoint);

  @NonNull
  public String getmContinuationPoint() {
    return mContinuationPoint;
  }

  void continueRegistration(boolean secondFactorSelected, SecondFactorAuthenticationType authenticationType, @NonNull Promise promise) {
    // swap in the new promise for the second half of the registration process
    this.mPromise = promise;
    if (mTwoFactorSelector != null) {
      mTwoFactorSelector.select(secondFactorSelected, authenticationType);
    }
  }

  // RegistrationListener

  @Override
  public void onRegistrationCompleted(@Nullable MobileCredential credential, @Nullable RegistrationError error) {
    cleanup(getmContinuationPoint());

    if(error != null) {
      mPromise.reject("registration_failed", error.getLocalizedMessage(), error);
    } else if(credential != null) {
      WritableMap response = new WritableNativeMap();
      response.putBoolean("completed", true);
      response.putMap("credential", serializeCredential(credential));
      mPromise.resolve(response);
    } else {
      throw new FatalError("Unexpected result from onRegistrationCompletedWithCredential");
    }
  }

  @Override
  public void onAuthenticationTypeSelectionRequested(SecondFactorAuthenticationTypeSelector selector) {
    mTwoFactorSelector = selector;
    WritableMap response = new WritableNativeMap();
    response.putBoolean("completed", false);
    response.putString("continuationPoint", getmContinuationPoint());
    mPromise.resolve(response);
  }
};
