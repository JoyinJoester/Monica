using Monica.Core.Models;

namespace Monica.Platform.Services;

public interface IClipboardService
{
    Task SetTextAsync(string text, CancellationToken cancellationToken = default);
}

public interface IPlatformCapabilityService
{
    IReadOnlyList<PlatformCapability> GetCapabilities();
}

public sealed class PlatformCapabilityService : IPlatformCapabilityService
{
    public IReadOnlyList<PlatformCapability> GetCapabilities() => FeatureCatalog.AndroidParityFeatures;
}

public sealed record RemoteFileEntry(string Path, bool IsDirectory, long? Length, DateTimeOffset? LastModified);

public interface IWebDavBackupService
{
    string NormalizeRemotePath(string rootPath, string relativePath);
    Task<IReadOnlyList<RemoteFileEntry>> ListAsync(WebDavProfile profile, string relativePath, CancellationToken cancellationToken = default);
}

public interface IOneDriveBackupService
{
    PlatformCapability GetCapability();
}

public interface IKeePassVaultService
{
    Task<KeePassVaultSummary> InspectAsync(string path, string? password, CancellationToken cancellationToken = default);
}

public sealed record KeePassVaultSummary(string Path, bool Exists, string Status, int GroupCount, int EntryCount);

public interface IMdbxVaultService
{
    Task<LocalMdbxDatabase> CreateLocalMetadataAsync(string name, string filePath, MdbxTigaMode mode = MdbxTigaMode.Multi, CancellationToken cancellationToken = default);
    Task<Stream> OpenLocalStreamAsync(LocalMdbxDatabase database, CancellationToken cancellationToken = default);
}
