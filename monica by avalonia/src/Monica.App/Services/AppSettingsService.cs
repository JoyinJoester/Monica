using System.Text.Json;
using System.Text.Json.Serialization;

namespace Monica.App.Services;

public sealed class DesktopAppSettings
{
    public string Language { get; set; } = "system";
    public string Theme { get; set; } = "system";
    public string StartupSection { get; set; } = "Passwords";
    public bool AutoLockEnabled { get; set; } = true;
    public int AutoLockMinutes { get; set; } = 5;
    public bool ClearClipboardEnabled { get; set; } = true;
    public int ClipboardClearSeconds { get; set; } = 30;
    public bool RequirePasswordBeforeExport { get; set; } = true;
    public bool MinimizeToTray { get; set; }
    public bool QuickSearchEnabled { get; set; } = true;
    public string QuickSearchHotkey { get; set; } = "Ctrl+Shift+Space";
    public bool BrowserIntegrationEnabled { get; set; }
    public int BrowserIntegrationPort { get; set; } = 49152;
    public bool CompactPasswordList { get; set; }
    public bool WebDavEnabled { get; set; }
    public string WebDavServerUrl { get; set; } = "";
    public string WebDavUsername { get; set; } = "";
    public string WebDavRemotePath { get; set; } = "/Monica";
    public bool WebDavSyncOnStartup { get; set; }
    public bool WebDavSyncAfterChanges { get; set; }
    public string SyncConflictStrategy { get; set; } = "ask";
    public bool OneDriveEnabled { get; set; }
    public bool MdbxLocalCacheEnabled { get; set; } = true;
}

public interface IAppSettingsService
{
    DesktopAppSettings Current { get; }
    Task LoadAsync(CancellationToken cancellationToken = default);
    Task SaveAsync(CancellationToken cancellationToken = default);
}

public sealed class AppSettingsService : IAppSettingsService
{
    private readonly string _settingsPath;
    private readonly SemaphoreSlim _saveGate = new(1, 1);

    public AppSettingsService(string? settingsPath = null)
    {
        _settingsPath = settingsPath ?? GetDefaultSettingsPath();
    }

    public DesktopAppSettings Current { get; private set; } = new();

    public async Task LoadAsync(CancellationToken cancellationToken = default)
    {
        if (!File.Exists(_settingsPath))
        {
            Current = new DesktopAppSettings();
            return;
        }

        await using var stream = File.OpenRead(_settingsPath);
        Current = await JsonSerializer.DeserializeAsync(
            stream,
            AppSettingsJsonContext.Default.DesktopAppSettings,
            cancellationToken) ?? new DesktopAppSettings();

        Normalize(Current);
    }

    public async Task SaveAsync(CancellationToken cancellationToken = default)
    {
        await _saveGate.WaitAsync(cancellationToken);
        try
        {
            Normalize(Current);
            Directory.CreateDirectory(Path.GetDirectoryName(_settingsPath)!);
            await using var stream = File.Create(_settingsPath);
            await JsonSerializer.SerializeAsync(
                stream,
                Current,
                AppSettingsJsonContext.Default.DesktopAppSettings,
                cancellationToken);
        }
        finally
        {
            _saveGate.Release();
        }
    }

    private static string GetDefaultSettingsPath()
    {
        var root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Monica");
        return Path.Combine(root, "settings.json");
    }

    private static void Normalize(DesktopAppSettings settings)
    {
        settings.Language = NormalizeChoice(settings.Language, "system", "system", "en-US", "zh-CN");
        settings.Theme = NormalizeChoice(settings.Theme, "system", "system", "light", "dark");
        settings.StartupSection = NormalizeChoice(settings.StartupSection, "Passwords", "Passwords", "Notes", "Totp", "Cards", "Generator", "Sync", "Settings");
        settings.SyncConflictStrategy = NormalizeChoice(settings.SyncConflictStrategy, "ask", "ask", "local-wins", "remote-wins");
        settings.AutoLockMinutes = Clamp(settings.AutoLockMinutes, 1, 120);
        settings.ClipboardClearSeconds = Clamp(settings.ClipboardClearSeconds, 10, 600);
        settings.BrowserIntegrationPort = Clamp(settings.BrowserIntegrationPort, 1024, 65535);

        if (string.IsNullOrWhiteSpace(settings.WebDavRemotePath))
        {
            settings.WebDavRemotePath = "/Monica";
        }

        if (string.IsNullOrWhiteSpace(settings.QuickSearchHotkey))
        {
            settings.QuickSearchHotkey = "Ctrl+Shift+Space";
        }
    }

    private static int Clamp(int value, int min, int max) => Math.Min(Math.Max(value, min), max);

    private static string NormalizeChoice(string? value, string fallback, params string[] allowed)
    {
        return allowed.Any(item => string.Equals(item, value, StringComparison.OrdinalIgnoreCase))
            ? allowed.First(item => string.Equals(item, value, StringComparison.OrdinalIgnoreCase))
            : fallback;
    }
}

[JsonSerializable(typeof(DesktopAppSettings))]
internal sealed partial class AppSettingsJsonContext : JsonSerializerContext;
