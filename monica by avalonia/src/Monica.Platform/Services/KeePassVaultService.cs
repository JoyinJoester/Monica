using Monica.Core.Models;

namespace Monica.Platform.Services;

public sealed class KeePassVaultService : IKeePassVaultService
{
    public Task<KeePassVaultSummary> InspectAsync(string path, string? password, CancellationToken cancellationToken = default)
    {
        var exists = File.Exists(path);
        var status = exists
            ? "KeePassLib is referenced; full KDBX unlock/import can be enabled against this service boundary."
            : "KDBX file not found.";

        return Task.FromResult(new KeePassVaultSummary(path, exists, status, 0, 0));
    }
}
