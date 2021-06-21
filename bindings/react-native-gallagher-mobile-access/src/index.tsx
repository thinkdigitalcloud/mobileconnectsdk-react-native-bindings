import { NativeModules, NativeEventEmitter } from "react-native";
const { GallagherMobileAccess } = NativeModules;

console.log("Creating new GallagherMobileAccessEvents emitter!");

interface MobileCredential {
  id: String;
  facilityId: Number;
  facilityName: String;
  isRevoked: Boolean;
  registeredDate: String;
};

interface Reader {
  id: String;
  name: String;
}

interface ReaderAttributes extends Reader {
  "measuredPathLoss": Number;
  "distance": String;
  "autoConnectPathLoss": Number;
  "manualConnectPathLoss": Number;
  "isBleManualConnectEnabled": Boolean;
  "isBleAutoConnectEnabled": Boolean;
  "isSecondFactorRequired": Boolean;
  "isBleActionsEnabled": Boolean;
}

interface RegisterCredentialContinuation {
  completed: Boolean;
  continuationPoint: String;
};

interface RegisterCredentialResult {
  completed: Boolean;
  credential: MobileCredential;
};

interface MobileAccessInterface {
  // MobileAccessProvider
  configure(dbFilePath?: String, cloudTlsValidationMode?: String, enabledFeatures?: [String]): void;

  // MobileAccess
  setAutomaticAccessEnabled(enabled: Boolean): void;
  setScanning(enabled: Boolean): void;
  setBackgroundScanningMode(mode: String): void;
  resolveInvitationUrl(host: String, invitationCode: String): Promise<String>;
  registerCredential(url: String): Promise<RegisterCredentialContinuation | RegisterCredentialResult>;
  registerCredentialContinue(continuationPoint: String, secondFactorSelected:Boolean, authenticationType:String): Promise<RegisterCredentialResult>;
  getStates() : Promise<[String]>;
  getCredentials() : Promise<[MobileCredential]>;
  deleteCredential(credentialId: String) : Promise<[MobileCredential]>;
  requestAccess(reader: Reader) : void;
};

export default GallagherMobileAccess as MobileAccessInterface;