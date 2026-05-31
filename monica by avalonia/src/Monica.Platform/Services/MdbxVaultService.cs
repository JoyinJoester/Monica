using Monica.Core.Models;

namespace Monica.Platform.Services;

public sealed class MdbxVaultService : IMdbxVaultService
{
    public Task<LocalMdbxDatabase> CreateLocalMetadataAsync(string name, string filePath, MdbxTigaMode mode = MdbxTigaMode.Multi, CancellationToken cancellationToken = default)
    {
        var fullPath = Path.GetFullPath(filePath);
        Directory.CreateDirectory(Path.GetDirectoryName(fullPath) ?? Environment.CurrentDirectory);

        var database = new LocalMdbxDatabase
        {
            Name = name,
            FilePath = fullPath,
            StorageLocation = MdbxStorageLocation.Internal,
            SourceType = "LOCAL_INTERNAL",
            TigaMode = mode,
            LastSyncStatus = SyncStatus.LocalOnly,
            WorkingCopyPath = fullPath,
            IsOfflineAvailable = true,
            CreatedAt = DateTimeOffset.UtcNow,
            LastAccessedAt = DateTimeOffset.UtcNow
        };

        return Task.FromResult(database);
    }

    public Task<Stream> OpenLocalStreamAsync(LocalMdbxDatabase database, CancellationToken cancellationToken = default)
    {
        var path = database.WorkingCopyPath ?? database.FilePath;
        Stream stream = File.Open(path, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.Read);
        return Task.FromResult(stream);
    }
}
