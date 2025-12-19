using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Monica.Windows.Services;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;

namespace Monica.Windows.Views
{
    public sealed partial class WebDavPage : Page
    {
        private readonly IWebDavService _webDavService;
        private readonly ISecurityService _securityService;
        private bool _isConfigured = false;

        public ObservableCollection<BackupHistoryItem> BackupHistory { get; } = new();

        public WebDavPage()
        {
            this.InitializeComponent();
            
            var services = ((App)App.Current).Services;
            _webDavService = services.GetRequiredService<IWebDavService>();
            _securityService = services.GetRequiredService<ISecurityService>();
            
            this.Loaded += WebDavPage_Loaded;
        }

        private async void WebDavPage_Loaded(object sender, RoutedEventArgs e)
        {
            await CheckConnectionStatus();
            await LoadBackupHistory();
        }

        private async Task CheckConnectionStatus()
        {
            var config = _webDavService.GetCurrentConfig();
            if (config != null && !string.IsNullOrEmpty(config.ServerUrl))
            {
                try
                {
                    bool connected = await _webDavService.TestConnectionAsync();
                    if (connected)
                    {
                        _isConfigured = true;
                        StatusIndicator.Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.LimeGreen);
                        ConnectionStatusText.Text = "已连接";
                        ServerUrlText.Text = config.ServerUrl;
                    }
                    else
                    {
                        StatusIndicator.Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Orange);
                        ConnectionStatusText.Text = "连接失败";
                        ServerUrlText.Text = config.ServerUrl;
                    }
                }
                catch
                {
                    StatusIndicator.Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Red);
                    ConnectionStatusText.Text = "连接错误";
                    ServerUrlText.Text = config.ServerUrl;
                }
            }
            else
            {
                _isConfigured = false;
                StatusIndicator.Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Gray);
                ConnectionStatusText.Text = "未配置";
                ServerUrlText.Text = "点击配置以设置 WebDAV 服务器";
            }
        }

        private async Task LoadBackupHistory()
        {
            try
            {
                if (!_isConfigured) return;
                
                var files = await _webDavService.ListBackupsAsync();
                BackupHistory.Clear();

                var parsedItems = new List<(string FileName, DateTime Date)>();

                foreach (var file in files)
                {
                    DateTime dt = DateTime.MinValue;
                    try
                    {
                        var parts = file.Replace(".zip", "").Replace(".enc", "").Split('_');
                        if (parts.Length >= 2) // At least date and time
                        {
                            var datePart = parts[^2];
                            var timePart = parts[^1];
                            if (datePart.Length == 8 && timePart.Length == 6)
                            {
                                int year = int.Parse(datePart.Substring(0, 4));
                                int month = int.Parse(datePart.Substring(4, 2));
                                int day = int.Parse(datePart.Substring(6, 2));
                                int hour = int.Parse(timePart.Substring(0, 2));
                                int minute = int.Parse(timePart.Substring(2, 2));
                                int second = int.Parse(timePart.Substring(4, 2));
                                dt = new DateTime(year, month, day, hour, minute, second);
                            }
                        }
                    }
                    catch { }
                    parsedItems.Add((file, dt));
                }
                
                foreach (var item in parsedItems.OrderByDescending(x => x.Date))
                {
                    string dateStr = item.Date == DateTime.MinValue ? "未知日期" : item.Date.ToString("yyyy/MM/dd HH:mm");
                    BackupHistory.Add(new BackupHistoryItem { FileName = item.FileName, DateString = dateStr });
                }
                
                BackupHistoryText.Visibility = BackupHistory.Count > 0 ? Visibility.Collapsed : Visibility.Visible;
                BackupHistoryList.ItemsSource = BackupHistory;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Failed to load backup history: {ex.Message}");
            }
        }

        private async void ConfigureButton_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new ContentDialog
            {
                Title = "配置 WebDAV 服务器",
                PrimaryButtonText = "保存",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = this.XamlRoot
            };

            var config = _webDavService.GetCurrentConfig() ?? new WebDavConfig();
            
            var stack = new StackPanel { Spacing = 12, MinWidth = 400 };
            
            var serverUrlBox = new TextBox 
            { 
                Header = "服务器地址", 
                PlaceholderText = "https://dav.example.com/path",
                Text = config.ServerUrl ?? ""
            };
            var usernameBox = new TextBox 
            { 
                Header = "用户名", 
                PlaceholderText = "输入用户名",
                Text = config.Username ?? ""
            };
            var passwordBox = new PasswordBox 
            { 
                Header = "密码", 
                PlaceholderText = "输入密码",
                Password = config.Password ?? ""
            };
            
            stack.Children.Add(serverUrlBox);
            stack.Children.Add(usernameBox);
            stack.Children.Add(passwordBox);
            stack.Children.Add(new TextBlock 
            { 
                Text = "提示: 坚果云用户请使用应用密码而非登录密码",
                Style = (Style)Application.Current.Resources["CaptionTextBlockStyle"],
                Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"],
                TextWrapping = TextWrapping.Wrap
            });
            
            dialog.Content = stack;

            var result = await dialog.ShowAsync();
            if (result == ContentDialogResult.Primary)
            {
                var newConfig = new WebDavConfig
                {
                    ServerUrl = serverUrlBox.Text.Trim(),
                    Username = usernameBox.Text.Trim(),
                    Password = passwordBox.Password
                };
                
                _webDavService.SaveConfig(newConfig);
                await CheckConnectionStatus();
                await LoadBackupHistory();
            }
        }

        private void EnableEncryptionToggle_Toggled(object sender, RoutedEventArgs e)
        {
            EncryptionPasswordBox.Visibility = EnableEncryptionToggle.IsOn ? Visibility.Visible : Visibility.Collapsed;
        }

        private async void BackupButton_Click(object sender, RoutedEventArgs e)
        {
            if (!_isConfigured)
            {
                await ShowMessageAsync("错误", "请先配置 WebDAV 服务器");
                return;
            }

            // Check if at least one data type is selected
            if (IncludePasswordsCheckBox.IsChecked != true &&
                IncludeTotpCheckBox.IsChecked != true &&
                IncludeNotesCheckBox.IsChecked != true &&
                IncludeCardsCheckBox.IsChecked != true &&
                IncludeDocumentsCheckBox.IsChecked != true &&
                IncludeImagesCheckBox.IsChecked != true &&
                IncludeCategoriesCheckBox.IsChecked != true)
            {
                await ShowMessageAsync("提示", "请至少选择一种要备份的数据类型");
                return;
            }

            // Check encryption password
            bool encrypt = EnableEncryptionToggle.IsOn;
            string encryptPassword = EncryptionPasswordBox.Password;
            
            if (encrypt && string.IsNullOrEmpty(encryptPassword))
            {
                await ShowMessageAsync("错误", "请输入加密密码");
                return;
            }

            try
            {
                SetProgress(true, "正在备份...");
                
                var options = new BackupOptions
                {
                    IncludePasswords = IncludePasswordsCheckBox.IsChecked == true,
                    IncludeTotp = IncludeTotpCheckBox.IsChecked == true,
                    IncludeNotes = IncludeNotesCheckBox.IsChecked == true,
                    IncludeCards = IncludeCardsCheckBox.IsChecked == true || IncludeDocumentsCheckBox.IsChecked == true,
                    IncludeImages = IncludeImagesCheckBox.IsChecked == true,
                    IncludeCategories = IncludeCategoriesCheckBox.IsChecked == true
                };

                var fileName = await _webDavService.CreateBackupAsync(encrypt, encryptPassword, options);
                
                SetProgress(false);
                await ShowMessageAsync("成功", $"备份已上传: {fileName}");
                await LoadBackupHistory();
            }
            catch (Exception ex)
            {
                SetProgress(false);
                await ShowMessageAsync("备份失败", ex.Message);
            }
        }

        private async void RestoreButton_Click(object sender, RoutedEventArgs e)
        {
            if (!_isConfigured)
            {
                await ShowMessageAsync("错误", "请先配置 WebDAV 服务器");
                return;
            }

            try
            {
                var files = await _webDavService.ListBackupsAsync();
                if (!files.Any())
                {
                    await ShowMessageAsync("提示", "云端没有找到备份文件");
                    return;
                }

                // Show file selection dialog
                var dialog = new ContentDialog
                {
                    Title = "选择要恢复的备份",
                    PrimaryButtonText = "恢复",
                    CloseButtonText = "取消",
                    DefaultButton = ContentDialogButton.Primary,
                    XamlRoot = this.XamlRoot
                };

                var listView = new ListView
                {
                    ItemsSource = files.OrderByDescending(f => f).ToList(),
                    SelectionMode = ListViewSelectionMode.Single,
                    MaxHeight = 300
                };
                listView.SelectedIndex = 0;

                var stack = new StackPanel { Spacing = 12 };
                stack.Children.Add(listView);
                
                var decryptPasswordBox = new PasswordBox
                {
                    Header = "解密密码（如果备份已加密）",
                    PlaceholderText = "留空表示未加密"
                };
                stack.Children.Add(decryptPasswordBox);
                
                dialog.Content = stack;

                var result = await dialog.ShowAsync();
                if (result == ContentDialogResult.Primary && listView.SelectedItem is string selectedFile)
                {
                    await PerformRestore(selectedFile, decryptPasswordBox.Password);
                }
            }
            catch (Exception ex)
            {
                await ShowMessageAsync("错误", ex.Message);
            }
        }

        private async void RestoreFromHistory_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string fileName)
            {
                // Ask for decryption password if file is encrypted
                string password = "";
                if (fileName.Contains(".enc"))
                {
                    var dialog = new ContentDialog
                    {
                        Title = "输入解密密码",
                        PrimaryButtonText = "确定",
                        CloseButtonText = "取消",
                        XamlRoot = this.XamlRoot
                    };
                    
                    var passwordBox = new PasswordBox { PlaceholderText = "输入加密密码" };
                    dialog.Content = passwordBox;
                    
                    var result = await dialog.ShowAsync();
                    if (result != ContentDialogResult.Primary) return;
                    
                    password = passwordBox.Password;
                }
                
                await PerformRestore(fileName, password);
            }
        }

        private async Task PerformRestore(string fileName, string decryptPassword)
        {
            try
            {
                SetProgress(true, "正在恢复...");
                
                string resultMessage = await _webDavService.RestoreBackupAsync(fileName, decryptPassword);
                
                SetProgress(false);
                await ShowMessageAsync("恢复完成", resultMessage);
            }
            catch (Exception ex)
            {
                SetProgress(false);
                await ShowMessageAsync("恢复失败", ex.Message);
            }
        }

        private async void DeleteBackup_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string fileName)
            {
                var dialog = new ContentDialog
                {
                    Title = "确认删除",
                    Content = $"确定要删除备份文件 \"{fileName}\" 吗？此操作不可撤销。",
                    PrimaryButtonText = "删除",
                    CloseButtonText = "取消",
                    DefaultButton = ContentDialogButton.Close,
                    XamlRoot = this.XamlRoot
                };

                if (await dialog.ShowAsync() == ContentDialogResult.Primary)
                {
                    try
                    {
                        SetProgress(true, "正在删除...");
                        await _webDavService.DeleteBackupAsync(fileName);
                        await LoadBackupHistory();
                        SetProgress(false);
                        await ShowMessageAsync("成功", "备份文件已删除");
                    }
                    catch (Exception ex)
                    {
                        SetProgress(false);
                        await ShowMessageAsync("删除失败", ex.Message);
                    }
                }
            }
        }

        private void SetProgress(bool show, string message = "")
        {
            ProgressSection.Visibility = show ? Visibility.Visible : Visibility.Collapsed;
            ProgressStatusText.Text = message;
            BackupButton.IsEnabled = !show;
            RestoreButton.IsEnabled = !show;
        }

        private async Task ShowMessageAsync(string title, string message)
        {
            var dialog = new ContentDialog
            {
                Title = title,
                Content = message,
                CloseButtonText = "确定",
                XamlRoot = this.XamlRoot
            };
            await dialog.ShowAsync();
        }
    }

    public class BackupHistoryItem
    {
        public string FileName { get; set; } = "";
        public string DateString { get; set; } = "";
    }
}
