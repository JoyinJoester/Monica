using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Monica.App.Services;
using Monica.App.ViewModels;
using Monica.Core.ImportExport;
using Monica.Core.Services;
using Monica.Data;
using Monica.Data.Repositories;
using Monica.Platform.Services;

namespace Monica.App;

public partial class App : Application
{
    private ServiceProvider? _services;
    private MainWindow? _mainWindow;

    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            _mainWindow = new MainWindow();
            _services = ConfigureServices(_mainWindow);
            _mainWindow.DataContext = _services.GetRequiredService<MainWindowViewModel>();
            desktop.MainWindow = _mainWindow;
        }

        base.OnFrameworkInitializationCompleted();
    }

    private static ServiceProvider ConfigureServices(MainWindow mainWindow)
    {
        var services = new ServiceCollection();
        services.AddLogging();
        services.AddHttpClient();
        services.AddSingleton<ISqliteConnectionFactory, SqliteConnectionFactory>();
        services.AddSingleton<IDatabaseMigrator, DatabaseMigrator>();
        services.AddSingleton<IVaultCredentialStore, VaultCredentialStore>();
        services.AddSingleton<IMonicaRepository, MonicaRepository>();
        services.AddSingleton<ICryptoService, CryptoService>();
        services.AddSingleton<ITotpService, TotpService>();
        services.AddSingleton<IPasswordGeneratorService, PasswordGeneratorService>();
        services.AddSingleton<IImportExportService, ImportExportService>();
        services.AddSingleton<IPlatformCapabilityService, PlatformCapabilityService>();
        services.AddSingleton<IWebDavBackupService, WebDavBackupService>();
        services.AddSingleton<IOneDriveBackupService, OneDriveBackupService>();
        services.AddSingleton<IKeePassVaultService, KeePassVaultService>();
        services.AddSingleton<IMdbxVaultService, MdbxVaultService>();
        services.AddSingleton<IClipboardService>(_ => new AvaloniaClipboardService(() => mainWindow));
        services.AddSingleton<MainWindowViewModel>();
        return services.BuildServiceProvider();
    }
}
