using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Monica.Windows.Services;
using Monica.Windows.ViewModels;
using Monica.Windows.Data;
using System;
using System.IO;
using System.Threading.Tasks;

namespace Monica.Windows
{
    public partial class App : Application
    {
        public static Window? MainWindow { get; set; }
        public IServiceProvider Services { get; }

        
        // Version marker for schema changes - increment when adding new tables
        private const int DB_SCHEMA_VERSION = 2;

        public App()
        {
            this.InitializeComponent();
            Services = ConfigureServices();
        }

        private static IServiceProvider ConfigureServices()
        {
            var services = new ServiceCollection();

            // Services
            services.AddSingleton<ISecurityService, SecurityService>();
            services.AddDbContext<AppDbContext>();
            services.AddSingleton<IImageStorageService, ImageStorageService>();
            services.AddTransient<DataExportImportService>();

            // ViewModels
            services.AddTransient<MainViewModel>();
            services.AddTransient<PasswordListViewModel>();
            services.AddSingleton<DataExportImportService>();
            services.AddSingleton<IWebDavService, WebDavService>();
            services.AddTransient<SettingsViewModel>();
            services.AddTransient<SecureItemsViewModel>();

            // Local API Server for browser sync


            return services.BuildServiceProvider();
        }

        protected override async void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
        {
            // Initialize Database
            await InitializeDatabaseAsync();

            MainWindow = new MainWindow();
            MainWindow.Activate();

            // Start local API server for browser extension sync



        }

        private async Task InitializeDatabaseAsync()
        {
            var folder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            var appFolder = Path.Combine(folder, "Monica");
            Directory.CreateDirectory(appFolder);
            
            var versionFile = Path.Combine(appFolder, "db_version.txt");
            var dbPath = Path.Combine(appFolder, "monica.db");
            
            // Check if schema version matches
            bool needsRecreate = false;
            if (File.Exists(versionFile))
            {
                var storedVersion = int.TryParse(File.ReadAllText(versionFile), out var v) ? v : 0;
                if (storedVersion < DB_SCHEMA_VERSION)
                {
                    needsRecreate = true;
                }
            }
            else
            {
                // No version file = old DB, needs recreation
                if (File.Exists(dbPath))
                {
                    needsRecreate = true;
                }
            }
            
            if (needsRecreate)
            {
                // Delete old database to force schema recreation
                try
                {
                    if (File.Exists(dbPath)) File.Delete(dbPath);
                    if (File.Exists(dbPath + "-shm")) File.Delete(dbPath + "-shm");
                    if (File.Exists(dbPath + "-wal")) File.Delete(dbPath + "-wal");
                }
                catch { }
            }

            using var scope = Services.CreateScope();
            var dbContext = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            
            try
            {
                await dbContext.Database.EnsureCreatedAsync();
                // Write current version
                File.WriteAllText(versionFile, DB_SCHEMA_VERSION.ToString());
            }
            catch (Exception)
            {
                // Failsafe: delete and retry
                try 
                {
                    if (File.Exists(dbPath)) File.Delete(dbPath);
                    await dbContext.Database.EnsureCreatedAsync();
                    File.WriteAllText(versionFile, DB_SCHEMA_VERSION.ToString());
                }
                catch { }
            }
        }
    }
}
