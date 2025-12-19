using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Windowing;
using Monica.Windows.Views;
using Monica.Windows.Data;
using Monica.Windows.Models;
using Windows.Graphics;
using System;
using System.Runtime.InteropServices;
using System.Linq;
using System.Threading.Tasks;
using WinRT.Interop;
using Microsoft.Extensions.DependencyInjection;
using Monica.Windows.Services;

namespace Monica.Windows
{
    public sealed partial class MainWindow : Window
    {
        private const int MinWidth = 800;
        private const int MinHeight = 500;

        // Win32 Interop for minimum size
        private delegate IntPtr WinProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
        private WinProc? _newWndProc;
        private IntPtr _oldWndProc = IntPtr.Zero;

        [DllImport("user32.dll", EntryPoint = "SetWindowLong")]
        private static extern IntPtr SetWindowLong32(IntPtr hWnd, int nIndex, IntPtr dwNewLong);

        [DllImport("user32.dll", EntryPoint = "SetWindowLongPtr")]
        private static extern IntPtr SetWindowLong64(IntPtr hWnd, int nIndex, IntPtr dwNewLong);

        private static IntPtr SetWindowLongPtr(IntPtr hWnd, int nIndex, IntPtr dwNewLong)
        {
            if (IntPtr.Size == 8)
                return SetWindowLong64(hWnd, nIndex, dwNewLong);
            else
                return SetWindowLong32(hWnd, nIndex, dwNewLong);
        }

        [DllImport("user32.dll")]
        private static extern IntPtr CallWindowProc(IntPtr lpPrevWndFunc, IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

        private const int GWLP_WNDPROC = -4;
        private const uint WM_GETMINMAXINFO = 0x0024;

        [StructLayout(LayoutKind.Sequential)]
        private struct POINT { public int x, y; }

        [StructLayout(LayoutKind.Sequential)]
        private struct MINMAXINFO
        {
            public POINT ptReserved;
            public POINT ptMaxSize;
            public POINT ptMaxPosition;
            public POINT ptMinTrackSize;
            public POINT ptMaxTrackSize;
        }

        public MainWindow()
        {
            this.InitializeComponent();
            Title = "Monica";
            
            // Enable custom title bar
            ExtendsContentIntoTitleBar = true;
            SetTitleBar(AppTitleBar);

            // Enable Mica backdrop
            SystemBackdrop = new MicaBackdrop();

            // Hook window procedure to set minimum size FIRST
            SetMinimumSize();

            // Set initial window size (must be >= minimum)
            var appWindow = this.AppWindow;
            appWindow.Resize(new SizeInt32(1000, 650));
            
            // Set Window Icon
            var iconPath = System.IO.Path.Combine(AppContext.BaseDirectory, "AppIcon.ico");
            if (System.IO.File.Exists(iconPath))
            {
                appWindow.SetIcon(iconPath);
            }

            // Start with login page
            RootFrame.Navigate(typeof(LoginPage));
        }

        private void SetMinimumSize()
        {
            IntPtr hWnd = WindowNative.GetWindowHandle(this);
            _newWndProc = new WinProc(NewWindowProc);
            _oldWndProc = SetWindowLongPtr(hWnd, GWLP_WNDPROC, Marshal.GetFunctionPointerForDelegate(_newWndProc));
        }

        private IntPtr NewWindowProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam)
        {
            if (msg == WM_GETMINMAXINFO)
            {
                var dpi = GetDpiForWindow(hWnd);
                float scaleFactor = dpi / 96f;

                var mmi = Marshal.PtrToStructure<MINMAXINFO>(lParam);
                mmi.ptMinTrackSize.x = (int)(MinWidth * scaleFactor);
                mmi.ptMinTrackSize.y = (int)(MinHeight * scaleFactor);
                Marshal.StructureToPtr(mmi, lParam, false);
                return IntPtr.Zero;
            }
            return CallWindowProc(_oldWndProc, hWnd, msg, wParam, lParam);
        }

        [DllImport("user32.dll")]
        private static extern uint GetDpiForWindow(IntPtr hWnd);

        public void NavigateToMain()
        {
            RootFrame.Visibility = Visibility.Collapsed;
            MainContent.Visibility = Visibility.Visible;
            
            // Load categories into sidebar
            _ = LoadCategoriesAsync();
            
            // Auto-collapse sidebar based on window size
            this.SizeChanged += MainWindow_SizeChanged;
            UpdatePaneState();
            
            NavView.SelectedItem = NavView.MenuItems[0];
            ContentFrame.Navigate(typeof(PasswordListPage));
        }

        private void MainWindow_SizeChanged(object sender, WindowSizeChangedEventArgs e)
        {
            UpdatePaneState();
        }

        private void UpdatePaneState()
        {
            // Collapse sidebar when window width < 1000
            var bounds = this.Bounds;
            NavView.IsPaneOpen = bounds.Width >= 1000;
        }

        private async Task LoadCategoriesAsync()
        {
            try
            {
                var dbContext = ((App)App.Current).Services.GetRequiredService<AppDbContext>();
                var categories = dbContext.Categories.OrderBy(c => c.SortOrder).ToList();
                
                // Find CategoriesHeader (the custom NavigationViewItem with name)
                int headerIndex = -1;
                for (int i = 0; i < NavView.MenuItems.Count; i++)
                {
                    if (NavView.MenuItems[i] == CategoriesHeader)
                    {
                        headerIndex = i;
                        break;
                    }
                }
                
                if (headerIndex >= 0)
                {
                    // Remove old dynamic categories (items after header that have Category_ tag)
                    while (headerIndex + 1 < NavView.MenuItems.Count)
                    {
                        var item = NavView.MenuItems[headerIndex + 1];
                        if (item is NavigationViewItem navItem && navItem.Tag?.ToString()?.StartsWith("Category_") == true)
                        {
                            NavView.MenuItems.RemoveAt(headerIndex + 1);
                        }
                        else
                        {
                            break;
                        }
                    }
                    
                    // Add categories with context menus
                    int insertIndex = headerIndex + 1;
                    foreach (var category in categories)
                    {
                        var categoryItem = new NavigationViewItem
                        {
                            Content = category.Name,
                            Tag = $"Category_{category.Id}",
                            Icon = new SymbolIcon(Symbol.Folder)
                        };
                        
                        // Add context menu for rename/delete
                        var contextMenu = new MenuFlyout();
                        
                        var renameItem = new MenuFlyoutItem { Text = "重命名", Tag = category.Id };
                        renameItem.Click += RenameCategory_Click;
                        contextMenu.Items.Add(renameItem);
                        
                        var deleteItem = new MenuFlyoutItem { Text = "删除分类", Tag = category.Id };
                        deleteItem.Click += DeleteCategory_Click;
                        contextMenu.Items.Add(deleteItem);
                        
                        categoryItem.ContextFlyout = contextMenu;
                        
                        NavView.MenuItems.Insert(insertIndex, categoryItem);
                        insertIndex++;
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Failed to load categories: {ex.Message}");
            }
        }

        private void CategoriesHeader_Tapped(object sender, Microsoft.UI.Xaml.Input.TappedRoutedEventArgs e)
        {
            AddCategory_Click(sender, new RoutedEventArgs());
        }

        private async void AddCategory_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new ContentDialog
            {
                Title = "新建分类",
                PrimaryButtonText = "创建",
                CloseButtonText = "取消",
                XamlRoot = this.Content.XamlRoot
            };

            var textBox = new TextBox { PlaceholderText = "分类名称" };
            dialog.Content = textBox;

            if (await dialog.ShowAsync() == ContentDialogResult.Primary && !string.IsNullOrWhiteSpace(textBox.Text))
            {
                try
                {
                    var dbContext = ((App)App.Current).Services.GetRequiredService<AppDbContext>();
                    var maxSortOrder = dbContext.Categories.Any() ? dbContext.Categories.Max(c => c.SortOrder) : 0;
                    
                    var newCategory = new Category
                    {
                        Name = textBox.Text.Trim(),
                        SortOrder = maxSortOrder + 1
                    };
                    dbContext.Categories.Add(newCategory);
                    await dbContext.SaveChangesAsync();
                    
                    await LoadCategoriesAsync();
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"Failed to create category: {ex.Message}");
                }
            }
        }

        private async void RenameCategory_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem menuItem && menuItem.Tag is int categoryId)
            {
                var dbContext = ((App)App.Current).Services.GetRequiredService<AppDbContext>();
                var category = await dbContext.Categories.FindAsync(categoryId);
                
                if (category == null) return;

                var dialog = new ContentDialog
                {
                    Title = "重命名分类",
                    PrimaryButtonText = "保存",
                    CloseButtonText = "取消",
                    XamlRoot = this.Content.XamlRoot
                };

                var textBox = new TextBox { Text = category.Name };
                dialog.Content = textBox;

                if (await dialog.ShowAsync() == ContentDialogResult.Primary && !string.IsNullOrWhiteSpace(textBox.Text))
                {
                    category.Name = textBox.Text.Trim();
                    await dbContext.SaveChangesAsync();
                    await LoadCategoriesAsync();
                }
            }
        }

        private async void DeleteCategory_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem menuItem && menuItem.Tag is int categoryId)
            {
                var dialog = new ContentDialog
                {
                    Title = "删除分类",
                    Content = "确定删除此分类吗？分类中的密码不会被删除，只会变为未分类状态。",
                    PrimaryButtonText = "删除",
                    CloseButtonText = "取消",
                    XamlRoot = this.Content.XamlRoot
                };

                if (await dialog.ShowAsync() == ContentDialogResult.Primary)
                {
                    try
                    {
                        var dbContext = ((App)App.Current).Services.GetRequiredService<AppDbContext>();
                        
                        // Set passwords in this category to uncategorized
                        var passwordsInCategory = dbContext.PasswordEntries.Where(p => p.CategoryId == categoryId);
                        foreach (var password in passwordsInCategory)
                        {
                            password.CategoryId = null;
                        }
                        
                        // Delete the category
                        var category = await dbContext.Categories.FindAsync(categoryId);
                        if (category != null)
                        {
                            dbContext.Categories.Remove(category);
                        }
                        
                        await dbContext.SaveChangesAsync();
                        await LoadCategoriesAsync();
                    }
                    catch (Exception ex)
                    {
                        System.Diagnostics.Debug.WriteLine($"Failed to delete category: {ex.Message}");
                    }
                }
            }
        }

        private void NavView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
        {
            if (args.SelectedItemContainer == null) return;

            if (args.IsSettingsSelected)
            {
                ContentFrame.Navigate(typeof(SettingsPage));
                return;
            }

            var tag = args.SelectedItemContainer?.Tag?.ToString();
            
            // Handle category navigation
            if (tag?.StartsWith("Category_") == true)
            {
                var categoryIdStr = tag.Substring("Category_".Length);
                if (int.TryParse(categoryIdStr, out int categoryId))
                {
                    ContentFrame.Navigate(typeof(PasswordListPage), categoryId);
                }
                return;
            }
            
            switch (tag)
            {
                case "Passwords":
                    ContentFrame.Navigate(typeof(PasswordListPage));
                    break;
                case "Notes":
                    ContentFrame.Navigate(typeof(SecureNotesPage));
                    break;
                case "TOTP":
                    ContentFrame.Navigate(typeof(TotpPage));
                    break;
                case "Cards":
                    ContentFrame.Navigate(typeof(CardsPage));
                    break;
                case "Favorites":
                    ContentFrame.Navigate(typeof(PasswordListPage), "Favorites");
                    break;
                case "WebDAV":
                    ContentFrame.Navigate(typeof(WebDavPage));
                    break;
                case "AddCategory":
                    AddCategory_Click(sender, new RoutedEventArgs());
                    break;
                case "Settings":
                    // ContentFrame.Navigate(typeof(SettingsPage));
                    break;
            }
        }

        private async void QuickBackup_Tapped(object sender, Microsoft.UI.Xaml.Input.TappedRoutedEventArgs e)
        {
            var webDavService = ((App)Application.Current).Services.GetService<IWebDavService>();
            if (webDavService == null) return;

            var progressDialog = new ContentDialog
            {
                Title = "快捷备份",
                Content = new StackPanel 
                { 
                    Spacing = 8, 
                    Children = 
                    { 
                        new TextBlock { Text = "正在上传数据..." },
                        new ProgressBar { IsIndeterminate = true } 
                    } 
                },
                XamlRoot = MainContent.XamlRoot
            };

            var _ = progressDialog.ShowAsync();
            
            try
            {
                // Full backup (default options)
                string result = await webDavService.CreateBackupAsync(false, null, null);
                progressDialog.Hide();

                var successDialog = new ContentDialog
                {
                    Title = "备份成功",
                    Content = $"备份文件已上传: {result}",
                    CloseButtonText = "确定",
                    XamlRoot = MainContent.XamlRoot
                };
                await successDialog.ShowAsync();
            }
            catch (Exception ex)
            {
                progressDialog.Hide();
                var failDialog = new ContentDialog
                {
                    Title = "备份失败",
                    Content = ex.Message,
                    CloseButtonText = "确定",
                    XamlRoot = MainContent.XamlRoot
                };
                await failDialog.ShowAsync();
            }
        }
    }
}
