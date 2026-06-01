namespace Monica.Core.Models;

public sealed class Category
{
    public long Id { get; set; }
    public string Name { get; set; } = "";
    public int SortOrder { get; set; }
    public long? MdbxDatabaseId { get; set; }
}

public sealed class OperationLog
{
    public long Id { get; set; }
    public string ItemType { get; set; } = "";
    public long ItemId { get; set; }
    public string ItemTitle { get; set; } = "";
    public string OperationType { get; set; } = "";
    public string ChangesJson { get; set; } = "";
    public string DeviceId { get; set; } = "";
    public string DeviceName { get; set; } = "";
    public DateTimeOffset Timestamp { get; set; } = DateTimeOffset.UtcNow;
    public bool IsReverted { get; set; }
}

public sealed class Attachment
{
    public long Id { get; set; }
    public string OwnerType { get; set; } = "";
    public long OwnerId { get; set; }
    public string FileName { get; set; } = "";
    public string ContentType { get; set; } = "";
    public string StoragePath { get; set; } = "";
    public long SizeBytes { get; set; }
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public long? BitwardenVaultId { get; set; }
    public string? KeepassBinaryRef { get; set; }
}

public sealed class CustomField
{
    public long Id { get; set; }
    public long EntryId { get; set; }
    public string Title { get; set; } = "";
    public string Value { get; set; } = "";
    public bool IsProtected { get; set; }
    public int SortOrder { get; set; }
}

public sealed class PasskeyEntry
{
    public long Id { get; set; }
    public string CredentialId { get; set; } = "";
    public string RpId { get; set; } = "";
    public string RpName { get; set; } = "";
    public string UserId { get; set; } = "";
    public string UserName { get; set; } = "";
    public string UserDisplayName { get; set; } = "";
    public int PublicKeyAlgorithm { get; set; } = -7;
    public string PublicKey { get; set; } = "";
    public string PrivateKeyAlias { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset LastUsedAt { get; set; } = DateTimeOffset.UtcNow;
    public int UseCount { get; set; }
    public string? IconUrl { get; set; }
    public bool IsDiscoverable { get; set; } = true;
    public bool IsUserVerificationRequired { get; set; } = true;
    public string Transports { get; set; } = "internal";
    public string Aaguid { get; set; } = "";
    public long SignCount { get; set; }
    public bool IsBackedUp { get; set; }
    public string Notes { get; set; } = "";
    public long? BoundPasswordId { get; set; }
    public long? CategoryId { get; set; }
    public long? KeepassDatabaseId { get; set; }
    public string? KeepassGroupPath { get; set; }
    public long? MdbxDatabaseId { get; set; }
    public string? MdbxFolderId { get; set; }
    public long? BitwardenVaultId { get; set; }
    public string? BitwardenFolderId { get; set; }
    public string? BitwardenCipherId { get; set; }
    public SyncStatus SyncStatus { get; set; } = SyncStatus.None;
    public string PasskeyMode { get; set; } = "LEGACY";
}

public sealed class LocalMdbxDatabase
{
    public long Id { get; set; }
    public string Name { get; set; } = "";
    public string FilePath { get; set; } = "";
    public MdbxStorageLocation StorageLocation { get; set; } = MdbxStorageLocation.RemoteWebDav;
    public string SourceType { get; set; } = "REMOTE_WEBDAV";
    public long? SourceId { get; set; }
    public MdbxTigaMode TigaMode { get; set; } = MdbxTigaMode.Multi;
    public string? EncryptedPassword { get; set; }
    public MdbxUnlockMethod UnlockMethod { get; set; } = MdbxUnlockMethod.MasterPassword;
    public string KdfProfile { get; set; } = "argon2id";
    public string? KeyFileName { get; set; }
    public string? KeyFileUri { get; set; }
    public string? KeyFileFingerprint { get; set; }
    public string? Description { get; set; }
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset LastAccessedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset? LastSyncedAt { get; set; }
    public bool IsDefault { get; set; }
    public int ProjectCount { get; set; }
    public int SortOrder { get; set; }
    public string? WorkingCopyPath { get; set; }
    public string? CacheCopyPath { get; set; }
    public bool IsOfflineAvailable { get; set; }
    public SyncStatus LastSyncStatus { get; set; } = SyncStatus.LocalOnly;
    public string? LastSyncError { get; set; }
}

public sealed class WebDavProfile
{
    public Uri BaseUri { get; init; } = new("https://example.com/");
    public string Username { get; init; } = "";
    public string Password { get; init; } = "";
    public string RootPath { get; init; } = "/";
}

public sealed record PlatformCapability(string Key, string Title, string Description, PlatformFeatureStatus Status);
