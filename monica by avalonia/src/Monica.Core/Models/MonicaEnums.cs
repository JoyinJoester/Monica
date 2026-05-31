namespace Monica.Core.Models;

public enum VaultItemType
{
    Password,
    Totp,
    BankCard,
    Document,
    Note
}

public enum PasswordLoginType
{
    Password,
    Sso,
    Wifi,
    SshKey
}

public enum SyncStatus
{
    None,
    Pending,
    Syncing,
    Synced,
    Failed,
    Conflict,
    LocalOnly,
    InSync,
    PendingUpload,
    RemoteChanged
}

public enum PlatformFeatureStatus
{
    Available,
    DesktopEquivalent,
    PlatformLimited,
    Planned
}

public enum ImportExportFormat
{
    MonicaJson,
    Csv,
    BitwardenJson,
    KeePassKdbx,
    AegisJson
}

public enum MdbxStorageLocation
{
    Internal,
    External,
    RemoteWebDav,
    RemoteOneDrive
}

public enum MdbxTigaMode
{
    Power,
    Multi,
    Sky
}

public enum MdbxUnlockMethod
{
    MasterPassword,
    KeyFile,
    MasterPasswordAndKeyFile,
    DeviceKey
}
