import GallagherMobileAccess
import React

@objc(GallagherMobileAccess)
class RNGallagherMobileAccess: RCTEventEmitter, SdkStateDelegate, ReaderUpdateDelegate, AccessDelegate {
    private var _instance: MobileAccess? = nil
    
    private var _pendingRegistrations:[String: AnonymousRegistrationDelegate] = [:]
    
    // It'd be nice if the Gallagher Mobile Connect SDK didn't just run everything on the UI thread, but rather was threadsafe.
    // For now though, it'll have to do
    override var methodQueue: DispatchQueue! {
        .main
    }
    
    override class func requiresMainQueueSetup() -> Bool {
        true
    }
    
    // MARK: - RCTEventEmitter
    
    override func supportedEvents() -> [String]! {
        [
            "sdkStateChanged",
            "readerUpdated",
            "skdFeatureStateChanged",
            "access", // funnel both automatic and manual access through the one global event channel because EventEmitter seems to want to be global all the time
        ]
    }
    
    override func startObserving() {
        if let instance = _instance {
            instance.addSdkStateDelegate(self)
            instance.addReaderUpdateDelegate(self)
            instance.addAutomaticAccessDelegate(self)
        }
    }
    
    override func stopObserving() {
        if let instance = _instance {
            instance.removeSdkStateDelegate(self)
            instance.removeReaderUpdateDelegate(self)
            instance.removeAutomaticAccessDelegate(self)
        }
    }
    
    // MARK: - SdkStateDelegate
    
    func onStateChange(isScanning: Bool, states: [MobileAccessState]) {
        sendEvent(withName: "sdkStateChanged", body: [
                    "isScanning": isScanning,
                    "states": serializeSdkStates(states)
        ] as [String: Any])
    }
    
    // MARK: - ReaderUpdateDelegate
    
    func onReaderUpdated(_ reader: ReaderAttributes, updateType: ReaderUpdateType) {
        sendEvent(withName: "readerUpdated", body: [
                    "updateType": serializeReaderUpdateType(updateType),
                    "reader": serializeReaderAttributes(reader)
        ] as [String: Any])
    }
    
    // MARK: - AccessDelegate
    
    func onAccessStarted(reader: Reader) {
        sendEvent(withName: "access", body: [
                    "event": "started",
                    "reader": serializeReader(reader)
        ] as [String: Any])
    }
    
    func onAccessCompleted(reader: Reader, result: AccessResult?, error: ReaderConnectionError?) {
        let body: [String: Any]
        if let e = error {
            body = [
                "event": "error",
                "message": e.localizedDescription,
                "reader": serializeReader(reader)
            ]
        } else if let r = result {
            body = [
                "event": (r.isAccessGranted() ? "granted": "denied"),
                "message": r.accessDecision.description,
                "code": r.accessDecision.rawValue,
                "accessMode": serializeAccessMode(r.accessMode),
                "reader": serializeReader(reader)
            ]
        } else {
            fatalError("onAccessCompleted invoked with both result and error set to nil?")
        }
        sendEvent(withName: "access", body: body)
    }
    
    // MARK: - MobileAccessProvider methods

    @objc
    func configure(_ dbFilePath: String?, cloudTlsValidationMode: String?, enabledFeatures: [String]?) {
        guard _instance == nil else {
            print("GallagherMobileAccess already configured; assuming deveveloper reload of JS environment")
            return
        }
        
        let dbFileUrl: URL?
        if let p = dbFilePath {
            dbFileUrl = URL(string: p)
        } else {
            dbFileUrl = nil
        }
        
        let tlsMode: CloudTlsValidationMode
        switch cloudTlsValidationMode {
        case "anyValidCertificateRequired":
            tlsMode = .anyValidCertificateRequired
        case "gallagherCertificateRequired":
            tlsMode = .gallagherCertificateRequired
        case "allowInvalidCertificate":
            tlsMode = .allowInvalidCertificate
        default:
            tlsMode = .anyValidCertificateRequired
        }
        
        var sdkFeatures: [SdkFeature] = []
        if let strFeatures = enabledFeatures {
            for str in strFeatures {
                switch str {
                case "salto":
                    sdkFeatures.append(.salto)
                case "digitalId":
                    sdkFeatures.append(.digitalId)
                default:
                    break
                }
            }
        }
        
        _instance = MobileAccessProvider.configure(databaseFilePath: dbFileUrl, localization: nil, cloudTlsValidationMode: tlsMode, enabledFeatures: sdkFeatures)
    }
    
    // MARK: - MobileAccess methods
    
    @objc
    func setAutomaticAccessEnabled(_ enabled: Bool) {
        _instance?.isAutomaticAccessEnabled = enabled
    }
    
    @objc
    func setScanning(_ enabled: Bool) {
        _instance?.setScanning(enabled: enabled)
    }
    
    @objc
    func setBackgroundScanningMode(_ mode: String!) {
        switch mode {
        case "standard":
            _instance?.backgroundScanningMode = .standard
        case "extended":
            _instance?.backgroundScanningMode = .extended
        default:
            break
        }
    }
    
    @objc(resolveInvitationUrl:invitationCode:resolve:reject:)
    func resolveInvitationUrl(_ host: String, invitationCode: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        guard let instance = _instance else {
            reject("not_configured", "GallagherMobileAccess.configure has not been called yet", nil)
            return
        }
        guard let url = instance.resolveInvitationUrl(host, invitation: invitationCode) else {
            reject("invalid_arg", "host or invitationCode was invalid", nil)
            return
        }
        resolve(url.absoluteString)
    }
    
    @objc(registerCredential:resolve:reject:)
    func registerCredential(_ url: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        guard let instance = _instance else {
            reject("not_configured", "GallagherMobileAccess.configure has not been called yet", nil)
            return
        }
        guard let url = URL(string: url) else {
            reject("invalid_arg", "url was invalid", nil)
            return
        }
        let delegate = AnonymousRegistrationDelegate(resolve: resolve, reject: reject) { cpoint in
            self._pendingRegistrations.removeValue(forKey: cpoint)
        }
        _pendingRegistrations[delegate.continuationPoint] = delegate // stash for later
        instance.registerCredential(url: url, delegate: delegate)
        
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
    
    @objc(registerCredentialContinue:secondFactorSelected:authenticationType:resolve:reject:)
    func registerCredentialContinue(_ continuationPoint: String,
                                    secondFactorSelected: Bool,
                                    authenticationType: String,
                                    resolve: @escaping RCTPromiseResolveBlock,
                                    reject: @escaping RCTPromiseRejectBlock)
    {
        guard let delegate = _pendingRegistrations[continuationPoint] else {
            reject("invalid_continuation_point", "continuationPoint was not valid", nil)
            return
        }
        
        let twoFactorType: SecondFactorAuthenticationType
        switch authenticationType {
        case "pin":
            twoFactorType = .pin
        case "fingerprint", "faceId", "fingerprintOrFaceId", "touchId":
            twoFactorType = .fingerprintOrFaceId
        default:
            twoFactorType = .pin
        }
        
        _pendingRegistrations.removeValue(forKey: continuationPoint)// don't need it anymore
        delegate.continue(secondFactorSelected: secondFactorSelected, authenticationType: twoFactorType, resolve: resolve, reject: reject)
    }
    
    @objc
    func getStates(_ resolve: RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) {
        guard let instance = _instance else {
            reject("not_configured", "GallagherMobileAccess.configure has not been called yet", nil)
            return
        }
        resolve(serializeSdkStates(instance.states))
    }
    
    @objc
    func getCredentials(_ resolve: RCTPromiseResolveBlock, reject:RCTPromiseRejectBlock) {
        guard let instance = _instance else {
            reject("not_configured", "GallagherMobileAccess.configure has not been called yet", nil)
            return
        }
        resolve(instance.mobileCredentials.map{ serializeCredential($0) })
    }
    
    @objc
    func deleteCredential(_ credentialId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        guard let instance = _instance else {
            reject("not_configured", "GallagherMobileAccess.configure has not been called yet", nil)
            return
        }
        guard let candidate = instance.mobileCredentials.first(where: {$0.id == credentialId}) else {
            reject("invalid_arg", "credentialId was invalid", nil)
            return
        }
        instance.deleteMobileCredential(candidate, deleteOption: .default) { credential, error in
            if let e = error {
                reject("delete_mobile_credential_failed", e.localizedDescription, e)
            } else {
                resolve(serializeCredential(credential))
            }
        }
    }
    
    @objc
    func requestAccess(_ reader: [String: Any]) {
        
    }
}

fileprivate func serializeSdkStates(_ states: [MobileAccessState]) -> [String] {
    states.map { (state:MobileAccessState) -> String in
        switch state {
        case .errorDeviceNotSupported: return "errorDeviceNotSupported"
        case .errorNoPasscodeSet: return "errorNoPasscodeSet"
        case .errorNoCredentials: return "errorNoCredentials"
        case .errorUnsupportedOsVersion: return "errorUnsupportedOsVersion"
        case .errorNoBleFeature: return "errorNoBleFeature"
        case .bleErrorLocationServiceDisabled: return "bleErrorLocationServiceDisabled"
        case .bleErrorNoLocationPermission: return "bleErrorNoLocationPermission"
        case .bleWarningExtendedBackgroundScanningRequiresLocationServiceEnabled: return "bleWarningExtendedBackgroundScanningRequiresLocationServiceEnabled"
        case .bleWarningExtendedBackgroundScanningRequiresLocationAlwaysPermission: return "bleWarningExtendedBackgroundScanningRequiresLocationAlwaysPermission"
        case .bleErrorDisabled: return "bleErrorDisabled"
        case .bleErrorUnauthorized: return "bleErrorUnauthorized"
        case .nfcErrorDisabled: return "nfcErrorDisabled"
        case .noNfcFeature: return "noNfcFeature"
        case .credentialRequiresBiometricsEnrolment: return "credentialRequiresBiometricsEnrolment"
        case .credentialBiometricsLockedOut: return "credentialBiometricsLockedOut"
        case .bleErrorNoBackgroundLocationPermission: return "bleErrorNoBackgroundLocationPermission"
        @unknown default: return "unknown:\(String(describing: state))"
        }
    }
}

fileprivate func serializeAccessMode(_ mode: AccessMode) -> String {
    switch mode {
    case .evac: return "evac"
    case .access: return "access"
    case .challenge: return "challenge"
    case .search: return "search"
    @unknown default: return "unknown"
    }
}

fileprivate func serializeReaderUpdateType(_ updateType: ReaderUpdateType) -> String {
    switch updateType {
    case .attributesChanged: return "attributesChanged"
    case .readerUnavailable: return "readerUnavailable"
    @unknown default: return "";
    }
}

fileprivate func serializeReaderDistance(_ distance: ReaderDistance) -> String {
    switch distance {
    case .far: return "far"
    case .medium: return "medium"
    case .near: return "near"
    @unknown default: return "";
    }
}

fileprivate func serializeReader(_ reader: Reader) -> [String: Any] {
  return [
    "id": reader.id.uuidString,
    "name": reader.name
  ]
}

fileprivate func serializeReaderAttributes(_ reader: ReaderAttributes) -> [String:Any] {
  return [
    "id": reader.id.uuidString,
    "name": reader.name,
    "measuredPathLoss": reader.measuredPathLoss,
    "distance": serializeReaderDistance(reader.distance),
    "autoConnectPathLoss": reader.autoConnectPathLoss,
    "manualConnectPathLoss": reader.manualConnectPathLoss,
    "isBleManualConnectEnabled": reader.isBleManualConnectEnabled,
    "isBleAutoConnectEnabled": reader.isBleAutoConnectEnabled,
    "isSecondFactorRequired": reader.isSecondFactorRequired,
    "isBleActionsEnabled": reader.isBleActionsEnabled,
  ]
}

fileprivate let _credentialDateFormatter: DateFormatter = {
    let f = DateFormatter()
    f.dateStyle = .short
    f.timeStyle = .short
    return f
}()

fileprivate func credentialDateToString(_ date: Date) -> String {
    return _credentialDateFormatter.string(from: date)
}

fileprivate func serializeCredential(_ credential: MobileCredential) -> [String: Any] {
    return [
        "id": credential.id,
        "facilityId": credential.facilityId,
        "facilityName": credential.facilityName,
        "isRevoked": credential.isRevoked,
        "registeredDate": credentialDateToString(credential.registeredDate)
    ]
}

class AnonymousRegistrationDelegate : NSObject, RegistrationDelegate {
    private let _cleanup: (String) -> Void

    private var _resolve: RCTPromiseResolveBlock
    private var _reject: RCTPromiseRejectBlock
    private var _twoFactorSelector: ((Bool, SecondFactorAuthenticationType) -> Void)? = nil
    
    let continuationPoint: String
    
    init(resolve: @escaping RCTPromiseResolveBlock, reject: @escaping  RCTPromiseRejectBlock, cleanup: @escaping (String) -> Void) {
        _resolve = resolve
        _reject = reject
        _cleanup = cleanup
        continuationPoint = UUID().uuidString
    }
    
    func `continue`(secondFactorSelected:Bool, authenticationType:SecondFactorAuthenticationType, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        // swap in the new promise for the second half of the registration process
        _resolve = resolve
        _reject = reject
        _twoFactorSelector?(secondFactorSelected, authenticationType)
    }
    
    // MARK: - RegistrationDelegate
    
    func onRegistrationCompleted(credential: MobileCredential?, error: RegistrationError?) {
        _cleanup(continuationPoint)
        
        if let e = error {
            _reject("registration_failed", e.localizedDescription, e)
            return
        } else if let c = credential {
            let response: [String: Any] = ["completed": true, "credential": serializeCredential(c)]
            _resolve(response as NSDictionary)
            return
        } else {
            fatalError("Unexpected result from onRegistrationCompletedWithCredential")
        }
    }
    
    func onAuthenticationTypeSelectionRequested(selector: @escaping SecondFactorAuthenticationTypeSelector) {
        _twoFactorSelector = selector
        let response: [String: Any] = ["completed": false, "continuationPoint": continuationPoint]
        _resolve(response as NSDictionary)
    }
}

