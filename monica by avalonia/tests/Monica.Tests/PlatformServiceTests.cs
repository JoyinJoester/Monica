using Monica.Core.Models;
using Monica.Platform.Services;
using Microsoft.Extensions.DependencyInjection;

namespace Monica.Tests;

public sealed class PlatformServiceTests
{
    [Fact]
    public void Webdav_paths_are_normalized_for_sync()
    {
        var services = new ServiceCollection();
        services.AddHttpClient();
        using var provider = services.BuildServiceProvider();
        var service = new WebDavBackupService(provider.GetRequiredService<IHttpClientFactory>());

        var path = service.NormalizeRemotePath("/Monica/", "/backup/vault.json");

        Assert.Equal("/Monica/backup/vault.json", path);
    }

    [Fact]
    public async Task Mdbx_service_creates_metadata_and_stream()
    {
        var service = new MdbxVaultService();
        var path = Path.Combine(Path.GetTempPath(), "monica-tests", $"{Guid.NewGuid():N}.mdbx");

        var metadata = await service.CreateLocalMetadataAsync("Test", path, MdbxTigaMode.Sky);
        await using var stream = await service.OpenLocalStreamAsync(metadata);

        Assert.Equal("Test", metadata.Name);
        Assert.True(stream.CanWrite);
    }

    [Fact]
    public async Task KeePass_service_reports_missing_file()
    {
        var service = new KeePassVaultService();

        var summary = await service.InspectAsync(Path.Combine(Path.GetTempPath(), $"{Guid.NewGuid():N}.kdbx"), null);

        Assert.False(summary.Exists);
        Assert.Contains("not found", summary.Status, StringComparison.OrdinalIgnoreCase);
    }
}
