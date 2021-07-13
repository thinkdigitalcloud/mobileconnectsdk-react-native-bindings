declare module 'react-native-gallagher-mobile-access' {
  export interface MobileCredential {
    id: String;
    facilityId: Number;
    facilityName: String;
    isRevoked: Boolean;
    registeredDate: String;
  }

  export interface SdkStateChanged {
    [key: string]: any
  }

  export interface ReaderUpdated {
    [key: string]: any
  }

  export interface AccessEvent {
    [key: string]: any
  }

  export interface Reader {
    id: String;
    name: String;
  }

  export interface RegisterCredentialContinuation {
    completed: Boolean;
    continuationPoint: String;
  }

  export interface RegisterCredentialResult {
    completed: Boolean;
    credential: MobileCredential;
  }

  const GallagherMobileAccess: {
    // MobileAccessProvider
    configure(
      dbFilePath?: String,
      cloudTlsValidationMode?: String,
      enabledFeatures?: [String]
    ): void;

    // MobileAccess
    setAutomaticAccessEnabled(enabled: Boolean): void;
    setScanning(enabled: Boolean): void;
    setBackgroundScanningMode(mode: String): void;
    resolveInvitationUrl(host: String, invitationCode: String): Promise<String>;
    registerCredential(
      url: String
    ): Promise<RegisterCredentialContinuation | RegisterCredentialResult>;
    registerCredentialContinue(
      continuationPoint: String,
      secondFactorSelected: Boolean,
      authenticationType: String
    ): Promise<RegisterCredentialResult>;
    getStates(): Promise<[String]>;
    getCredentials(): Promise<[MobileCredential]>;
    deleteCredential(credentialId: String): Promise<[MobileCredential]>;
    requestAccess(reader: Reader): void;
  }

  export default GallagherMobileAccess;
}
