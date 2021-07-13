#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(GallagherMobileAccess, NSObject)

// MobileAccessProvider
RCT_EXTERN_METHOD(configure:(nullable NSString*)databaseFilePath
                  cloudTlsValidationMode:(nullable NSString*)cloudTlsValidationMode
                  enabledFeatures:(nullable NSArray<NSString*>*)enabledFeatures)

// MobileAccess
RCT_EXTERN_METHOD(setAutomaticAccessEnabled:(BOOL)enabled)
RCT_EXTERN_METHOD(setScanning:(BOOL)enabled)
RCT_EXTERN_METHOD(setBackgroundScanningMode:(nonnull NSString*)mode)

RCT_EXTERN_METHOD(resolveInvitationUrl:(nonnull NSString*)host
                  invitationCode:(nonnull NSString*)code
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(registerCredential:(nonnull NSString*)url
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(registerCredentialContinue:(nonnull NSString*)continuationPoint
                  secondFactorSelected:(BOOL)secondFactorSelected
                  authenticationType:(nonnull NSString*)authenticationType
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getStates:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getCredentials:(nonnull RCTPromiseResolveBlock)resolve reject:(nonnull RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(deleteCredential:(nonnull NSString*)credentialId resolve:(nonnull RCTPromiseResolveBlock)resolve reject:(nonnull RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(requestAccess:(nonnull NSDictionary*)reader)

@end
