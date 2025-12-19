using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Monica.Windows.Services;
using Monica.Windows.ViewModels;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace Monica.Windows.Views
{
    public sealed partial class SettingsPage : Page
    {
        public SettingsViewModel ViewModel { get; }

        public SettingsPage()
        {
            this.InitializeComponent();
            ViewModel = ((App)App.Current).Services.GetRequiredService<SettingsViewModel>();
        }

        private async void SecurityAnalysis_Click(object sender, RoutedEventArgs e)
        {
            // Create analysis dialog with progress
            var analysisDialog = new ContentDialog
            {
                Title = "安全分析",
                CloseButtonText = "关闭",
                XamlRoot = this.XamlRoot
            };

            var mainStack = new StackPanel { Spacing = 16, MinWidth = 500 };
            var progressRing = new ProgressRing { IsActive = true, Width = 60, Height = 60 };
            var statusText = new TextBlock { Text = "正在分析密码...", HorizontalAlignment = HorizontalAlignment.Center };
            
            var progressStack = new StackPanel { Spacing = 12, HorizontalAlignment = HorizontalAlignment.Center };
            progressStack.Children.Add(progressRing);
            progressStack.Children.Add(statusText);
            mainStack.Children.Add(progressStack);

            // Results section (initially hidden)
            var resultsStack = new StackPanel { Spacing = 12, Visibility = Visibility.Collapsed };
            mainStack.Children.Add(resultsStack);

            analysisDialog.Content = new ScrollViewer { Content = mainStack, MaxHeight = 500 };

            // Start analysis in background
            _ = Task.Run(async () =>
            {
                try
                {
                    var dbContext = ((App)App.Current).Services.GetRequiredService<Data.AppDbContext>();
                    var securityService = ((App)App.Current).Services.GetRequiredService<ISecurityService>();
                    var passwords = await Microsoft.EntityFrameworkCore.EntityFrameworkQueryableExtensions.ToListAsync(dbContext.PasswordEntries);
                    var totpItems = await Microsoft.EntityFrameworkCore.EntityFrameworkQueryableExtensions.ToListAsync(dbContext.SecureItems.Where(i => i.ItemType == Models.ItemType.Totp));

                    int weakCount = 0;
                    int duplicateCount = 0;
                    int breachedCount = 0;
                    int no2faCount = 0;
                    var passwordHashes = new Dictionary<string, int>();
                    var results = new List<(string title, string issue, string severity)>();

                    // Known domains supporting 2FA (from Android implementation)
                    var supports2FA = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
                    {
                        "google.com", "gmail.com", "facebook.com", "twitter.com", "x.com",
                        "github.com", "microsoft.com", "apple.com", "amazon.com",
                        "dropbox.com", "linkedin.com", "instagram.com", "reddit.com",
                        "slack.com", "discord.com", "paypal.com", "netflix.com",
                        "yahoo.com", "outlook.com", "icloud.com", "twitch.tv",
                        "steam.com", "epic.com", "battle.net", "riot.com"
                    };

                    int total = passwords.Count;
                    int current = 0;

                    foreach (var pwd in passwords)
                    {
                        current++;
                        DispatcherQueue.TryEnqueue(() => 
                        {
                            statusText.Text = $"正在分析 ({current}/{total}): {pwd.Title}";
                        });

                        var decryptedPassword = securityService.Decrypt(pwd.EncryptedPassword);
                        
                        // Check password strength
                        if (IsWeakPassword(decryptedPassword))
                        {
                            weakCount++;
                            results.Add((pwd.Title, "弱密码", "warning"));
                        }

                        // Check for duplicates
                        if (!string.IsNullOrEmpty(decryptedPassword))
                        {
                            if (passwordHashes.ContainsKey(decryptedPassword))
                            {
                                duplicateCount++;
                                results.Add((pwd.Title, "重复密码", "info"));
                            }
                            else
                            {
                                passwordHashes[decryptedPassword] = 1;
                            }

                            // Check Have I Been Pwned API (k-Anonymity)
                            try
                            {
                                if (await CheckPasswordBreached(decryptedPassword))
                                {
                                    breachedCount++;
                                    results.Add((pwd.Title, "密码已泄露", "critical"));
                                }
                            }
                            catch { /* Ignore API errors */ }
                        }

                        // Check No 2FA
                        if (!string.IsNullOrEmpty(pwd.Website))
                        {
                            var domain = GetDomain(pwd.Website);
                            if (!string.IsNullOrEmpty(domain) && supports2FA.Any(d => domain.Contains(d, StringComparison.OrdinalIgnoreCase)))
                            {
                                // Check if we have a TOTP for this
                                bool hasTotp = totpItems.Any(t => t.Title.Contains(pwd.Title, StringComparison.OrdinalIgnoreCase) || 
                                                                  t.Title.Contains(domain, StringComparison.OrdinalIgnoreCase));
                                
                                if (!hasTotp)
                                {
                                    no2faCount++;
                                    results.Add((pwd.Title, "未启用 2FA", "warning"));
                                }
                            }
                        }
                    }

                    // Show results
                    DispatcherQueue.TryEnqueue(() =>
                    {
                        progressStack.Visibility = Visibility.Collapsed;
                        resultsStack.Visibility = Visibility.Visible;

                        // Summary cards
                        var summaryGrid = new Grid();
                        summaryGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                        summaryGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                        summaryGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                        summaryGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

                        summaryGrid.Children.Add(CreateStatCard("弱密码", weakCount, 0));
                        summaryGrid.Children.Add(CreateStatCard("重复", duplicateCount, 1));
                        summaryGrid.Children.Add(CreateStatCard("未开2FA", no2faCount, 2));
                        summaryGrid.Children.Add(CreateStatCard("已泄露", breachedCount, 3));

                        resultsStack.Children.Add(summaryGrid);

                        // Overall score
                        int totalIssues = weakCount + duplicateCount + breachedCount + no2faCount;
                        var scoreText = new TextBlock
                        {
                            Text = totalIssues == 0 ? "🎉 太棒了！您的密码非常安全！" : $"发现 {totalIssues} 个安全问题需要处理",
                            FontWeight = Microsoft.UI.Text.FontWeights.Bold,
                            HorizontalAlignment = HorizontalAlignment.Center,
                            Margin = new Thickness(0, 8, 0, 8)
                        };
                        resultsStack.Children.Add(scoreText);

                        // Detailed results
                        if (results.Count > 0)
                        {
                            var detailsHeader = new TextBlock { Text = "详细结果:", Margin = new Thickness(0, 8, 0, 4) };
                            resultsStack.Children.Add(detailsHeader);

                            foreach (var (title, issue, severity) in results.Take(10))
                            {
                                var itemStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 4, 0, 0) };
                                var icon = severity switch
                                {
                                    "critical" => "⚠️",
                                    "warning" => "⚡",
                                    _ => "ℹ️"
                                };
                                itemStack.Children.Add(new TextBlock { Text = icon });
                                itemStack.Children.Add(new TextBlock { Text = $"{title}: {issue}" });
                                resultsStack.Children.Add(itemStack);
                            }

                            if (results.Count > 10)
                            {
                                resultsStack.Children.Add(new TextBlock 
                                { 
                                    Text = $"...还有 {results.Count - 10} 个问题",
                                    Opacity = 0.7 
                                });
                            }
                        }
                    });
                }
                catch (Exception ex)
                {
                    DispatcherQueue.TryEnqueue(() =>
                    {
                        progressStack.Visibility = Visibility.Collapsed;
                        resultsStack.Visibility = Visibility.Visible;
                        resultsStack.Children.Add(new TextBlock { Text = $"分析出错: {ex.Message}" });
                    });
                }
            });

            await analysisDialog.ShowAsync();
        }

        private static Border CreateStatCard(string label, int count, int column)
        {
            var stack = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(8) };
            stack.Children.Add(new TextBlock 
            { 
                Text = count.ToString(), 
                FontSize = 28, 
                FontWeight = Microsoft.UI.Text.FontWeights.Bold,
                HorizontalAlignment = HorizontalAlignment.Center
            });
            stack.Children.Add(new TextBlock { Text = label, HorizontalAlignment = HorizontalAlignment.Center });
            
            var border = new Border 
            { 
                Child = stack, 
                Padding = new Thickness(16, 12, 16, 12),
                CornerRadius = new CornerRadius(8)
            };
            Grid.SetColumn(border, column);
            return border;
        }

        private static bool IsWeakPassword(string password)
        {
            if (string.IsNullOrEmpty(password)) return true;
            if (password.Length < 8) return true;
            
            bool hasUpper = password.Any(char.IsUpper);
            bool hasLower = password.Any(char.IsLower);
            bool hasDigit = password.Any(char.IsDigit);
            bool hasSpecial = password.Any(c => !char.IsLetterOrDigit(c));
            
            int complexity = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
            return complexity < 3;
        }

        private static async Task<bool> CheckPasswordBreached(string password)
        {
            using var sha1 = System.Security.Cryptography.SHA1.Create();
            var hash = sha1.ComputeHash(System.Text.Encoding.UTF8.GetBytes(password));
            var hashString = BitConverter.ToString(hash).Replace("-", "");
            
            var prefix = hashString.Substring(0, 5);
            var suffix = hashString.Substring(5);
            
            using var client = new System.Net.Http.HttpClient();
            client.DefaultRequestHeaders.Add("User-Agent", "Monica-Windows");
            
            var response = await client.GetStringAsync($"https://api.pwnedpasswords.com/range/{prefix}");
            return response.Contains(suffix, StringComparison.OrdinalIgnoreCase);
        }

        private static string GetDomain(string url)
        {
            if (string.IsNullOrWhiteSpace(url)) return string.Empty;
            try
            {
                if (!url.StartsWith("http://") && !url.StartsWith("https://"))
                {
                    url = "https://" + url;
                }
                var uri = new Uri(url);
                return uri.Host;
            }
            catch
            {
                return string.Empty;
            }
        }





        private async void SecurityQuestions_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new ContentDialog
            {
                Title = "设置密保问题",
                PrimaryButtonText = "保存",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = this.XamlRoot
            };

            var stack = new StackPanel { Spacing = 12, MinWidth = 350 };
            
            var info = new TextBlock 
            { 
                Text = "设置密保问题主要用于忘记主密码时重置应用。注意：重置主密码会清除所有旧数据。",
                TextWrapping = TextWrapping.Wrap,
                Opacity = 0.7
            };
            
            var q1 = new TextBox { Header = "密保问题", PlaceholderText = "例如：您的出生城市是？" };
            var a1 = new TextBox { Header = "答案", PlaceholderText = "输入答案" };
            
            // Pre-fill existing question if set
            try
            {
                var securityService = ((App)App.Current).Services.GetRequiredService<ISecurityService>();
                var existingQ = securityService.GetSecurityQuestion();
                if (!string.IsNullOrEmpty(existingQ))
                {
                    q1.Text = existingQ;
                    a1.PlaceholderText = "留空保持原答案不变"; 
                }
            }
            catch {}

            stack.Children.Add(info);
            stack.Children.Add(q1);
            stack.Children.Add(a1);
            
            dialog.Content = stack;

            var result = await dialog.ShowAsync();
            if (result == ContentDialogResult.Primary)
            {
                if (string.IsNullOrWhiteSpace(q1.Text))
                {
                    await ShowMessageAsync("错误", "密保问题不能为空");
                    return;
                }

                var securityService = ((App)App.Current).Services.GetRequiredService<ISecurityService>();
                
                // If answer is empty but they are editing an existing question, maybe we should block? 
                // Best to require answer always for simplicity and security.
                if (string.IsNullOrWhiteSpace(a1.Text))
                {
                     // If it was a placeholder "keep unchanged", we might need extra logic, 
                     // but since we don't store the raw answer to compare, let's just Require it again to be safe.
                     await ShowMessageAsync("错误", "答案不能为空");
                     return;
                }

                securityService.SetSecurityQuestion(q1.Text, a1.Text);
                await ShowMessageAsync("成功", "密保问题已保存");
            }
        }



        private async void Import_Click(object sender, RoutedEventArgs e)
        {
            var picker = new FileOpenPicker();
            picker.FileTypeFilter.Add(".csv");
            picker.FileTypeFilter.Add(".json"); // Aegis
            picker.SuggestedStartLocation = PickerLocationId.DocumentsLibrary;
            
            var hwnd = WindowNative.GetWindowHandle(App.MainWindow);
            InitializeWithWindow.Initialize(picker, hwnd);

            var file = await picker.PickSingleFileAsync();
            if (file != null)
            {
                var service = ((App)App.Current).Services.GetRequiredService<DataExportImportService>();
                string? password = null;
                bool retry = true;

                while (retry)
                {
                    var progressDialog = new ContentDialog
                    {
                        Title = "导入中",
                        Content = new StackPanel
                        {
                            Children = { new ProgressRing { IsActive = true, HorizontalAlignment = HorizontalAlignment.Center }, new TextBlock { Text = "正在导入数据，请稍候...", HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 10, 0, 0) } }
                        },
                        XamlRoot = this.XamlRoot
                    };
                    
                    var showProgressTask = progressDialog.ShowAsync();

                    try
                    {
                        // Pass password (null first try)
                        var result = await service.ImportDataAsync(file, password);
                        
                        progressDialog.Hide();
                        await ShowMessageAsync("导入完成", result);
                        retry = false;
                    }
                    catch (Exception ex)
                    {
                        progressDialog.Hide(); // Ensure hidden
                        
                        // Handle Password Requirement
                        if (ex.Message.Contains("Password_Required") || ex.Message.Contains("Wrong_Password"))
                        {
                             var msg = ex.Message.Contains("Wrong_Password") ? "密码错误，请重试：" : "此文件已加密，请输入密码：";
                             var pwdDialog = new ContentDialog
                             {
                                 Title = "解密文件",
                                 PrimaryButtonText = "确认",
                                 CloseButtonText = "取消",
                                 DefaultButton = ContentDialogButton.Primary,
                                 XamlRoot = this.XamlRoot
                             };
                             var pwdBox = new PasswordBox { Header = msg };
                             pwdDialog.Content = pwdBox;

                             if (await pwdDialog.ShowAsync() == ContentDialogResult.Primary)
                             {
                                 password = pwdBox.Password;
                                 retry = true; // Loop back to try again
                             }
                             else
                             {
                                 retry = false;
                             }
                        }
                        else
                        {
                            await ShowMessageAsync("导入失败", ex.Message);
                            retry = false;
                        }
                    }
                }
            }
        }

        private async void Export_Click(object sender, RoutedEventArgs e)
        {
            // 1. Configuration Dialog
            var dialog = new ContentDialog
            {
                Title = "导出数据",
                PrimaryButtonText = "下一步",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = this.XamlRoot
            };

            var stack = new StackPanel { Spacing = 16, MinWidth = 350 };
            
            // Category Selection
            var categoryCombo = new ComboBox 
            { 
                Header = "导出内容", 
                HorizontalAlignment = HorizontalAlignment.Stretch,
                ItemsSource = new[] { "全部数据 (CSV)", "仅密码 (CSV)", "仅 TOTP", "银行卡与证件 (CSV)", "仅笔记 (CSV)" },
                SelectedIndex = 0
            };

            // Format Selection (Only for TOTP)
            var formatStack = new StackPanel { Spacing = 8, Visibility = Visibility.Collapsed };
            var formatCombo = new ComboBox
            {
                Header = "TOTP 格式",
                HorizontalAlignment = HorizontalAlignment.Stretch,
                ItemsSource = new[] { "CSV (Monica 兼容)", "Aegis JSON (通用)" },
                SelectedIndex = 0 
            };
            
            // Encrypt Option (Only for Aegis)
            var encryptCheck = new CheckBox { Content = "加密导出 (推荐)", IsChecked = false };
            var passwordBox = new PasswordBox { Header = "设置导出密码", Visibility = Visibility.Collapsed };
            var confirmBox = new PasswordBox { Header = "确认密码", Visibility = Visibility.Collapsed };
            
            formatStack.Children.Add(formatCombo);
            formatStack.Children.Add(encryptCheck);
            formatStack.Children.Add(passwordBox);
            formatStack.Children.Add(confirmBox);

            // Aegis warning
            var aegisInfo = new TextBlock
            {
                Text = "注意: 请妥善保管导出的 JSON 文件。",
                FontSize = 12,
                Opacity = 0.7,
                TextWrapping = TextWrapping.Wrap,
                Visibility = Visibility.Collapsed
            };
            formatStack.Children.Add(aegisInfo);

            stack.Children.Add(categoryCombo);
            stack.Children.Add(formatStack);

            // Event handling logic
            categoryCombo.SelectionChanged += (s, args) =>
            {
                bool isTotp = categoryCombo.SelectedIndex == 2;
                formatStack.Visibility = isTotp ? Visibility.Visible : Visibility.Collapsed;
            };

            formatCombo.SelectionChanged += (s, args) =>
            {
                bool isAegis = formatCombo.SelectedIndex == 1;
                aegisInfo.Visibility = isAegis ? Visibility.Visible : Visibility.Collapsed;
                encryptCheck.Visibility = isAegis ? Visibility.Visible : Visibility.Collapsed;
                
                // Reset password fields visibility based on check
                if (!isAegis)
                {
                    passwordBox.Visibility = Visibility.Collapsed;
                    confirmBox.Visibility = Visibility.Collapsed;
                }
                else
                {
                    // If checked, show
                    passwordBox.Visibility = (encryptCheck.IsChecked == true) ? Visibility.Visible : Visibility.Collapsed;
                    confirmBox.Visibility = (encryptCheck.IsChecked == true) ? Visibility.Visible : Visibility.Collapsed;
                }
            };

            encryptCheck.Checked += (s, args) => 
            {
                 passwordBox.Visibility = Visibility.Visible;
                 confirmBox.Visibility = Visibility.Visible;
                 aegisInfo.Text = "注意: 导出后请牢记密码，丢失无法找回。";
            };
            
            encryptCheck.Unchecked += (s, args) => 
            {
                 passwordBox.Visibility = Visibility.Collapsed;
                 confirmBox.Visibility = Visibility.Collapsed;
                 aegisInfo.Text = "注意: 未加密导出包含明文密钥，请确保文件安全。";
            };

            dialog.Content = stack;

            var result = await dialog.ShowAsync();
            if (result != ContentDialogResult.Primary) return;

            // 2. Determine Options
            ExportOption option = ExportOption.All;
            ExportFormat format = ExportFormat.Csv;
            string defaultFileName = "monica_backup";
            string ext = ".csv";
            string? encryptionPassword = null;

            switch (categoryCombo.SelectedIndex)
            {
                case 0: option = ExportOption.All; defaultFileName = "monica_all"; break;
                case 1: option = ExportOption.Passwords; defaultFileName = "monica_passwords"; break;
                case 2: 
                    option = ExportOption.Totp; 
                    defaultFileName = "monica_totp";
                    if (formatCombo.SelectedIndex == 1)
                    {
                        format = ExportFormat.Aegis;
                        ext = ".json";
                        defaultFileName += "_aegis";
                        
                        if (encryptCheck.IsChecked == true)
                        {
                            if (string.IsNullOrEmpty(passwordBox.Password))
                            {
                                await ShowMessageAsync("错误", "请输入导出密码");
                                return;
                            }
                            if (passwordBox.Password != confirmBox.Password)
                            {
                                await ShowMessageAsync("错误", "两次输入的密码不一致");
                                return;
                            }
                            encryptionPassword = passwordBox.Password;
                            defaultFileName += "_encrypted";
                        }
                    }
                    else
                    {
                         // CSV TOTP - Force unencrypted usage or warn? Warn is below.
                    }
                    break;
                case 3: option = ExportOption.CardsDocs; defaultFileName = "monica_cards_docs"; break;
                case 4: option = ExportOption.Notes; defaultFileName = "monica_notes"; break;
            }

            // 3. File Picker
            var picker = new FileSavePicker();
            picker.SuggestedStartLocation = PickerLocationId.DocumentsLibrary;
            
            if (format == ExportFormat.Aegis) picker.FileTypeChoices.Add("JSON 文件", new[] { ".json" });
            else picker.FileTypeChoices.Add("CSV 文件", new[] { ".csv" });
            
            picker.SuggestedFileName = $"{defaultFileName}_{DateTime.Now:yyyyMMdd}";

            var hwnd = WindowNative.GetWindowHandle(App.MainWindow);
            InitializeWithWindow.Initialize(picker, hwnd);

            var file = await picker.PickSaveFileAsync();
            if (file != null)
            {
                // 4. Security Warning
                string warningMsg = "导出文件包含您的敏感数据（明文格式）。\n\n请务必妥善保管此文件，切勿发送给他人或上传到不安全的地方。\n\n是否继续导出？";
                
                if (format == ExportFormat.Aegis)
                {
                    if (encryptionPassword != null)
                    {
                        warningMsg = "正在导出加密的 Aegis JSON 文件。\n请务必牢记您的密码，否则无法恢复数据。\n\n是否继续？";
                    }
                    else
                    {
                        warningMsg = "正在导出为 Aegis JSON 格式（未加密）。\n文件包含您的 TOTP 密钥明文。\n\n是否继续？";
                    }
                }

                var warningDialog = new ContentDialog
                {
                    Title = "安全警告",
                    Content = warningMsg,
                    PrimaryButtonText = "继续导出",
                    CloseButtonText = "取消",
                    DefaultButton = ContentDialogButton.Primary,
                    XamlRoot = this.XamlRoot
                };

                if (await warningDialog.ShowAsync() != ContentDialogResult.Primary) return;

                try
                {
                    var service = ((App)App.Current).Services.GetRequiredService<DataExportImportService>();
                    var res = await service.ExportDataAsync(file, option, format, encryptionPassword);
                    await ShowMessageAsync("导出成功", $"{res}\n文件已保存至: {file.Path}");
                }
                catch (Exception ex)
                {
                    await ShowMessageAsync("导出失败", ex.Message);
                }
            }
        }

        private void WebDAV_Click(object sender, RoutedEventArgs e)
        {
            this.Frame.Navigate(typeof(WebDavPage));
            #if false
            var webDavService = ((App)App.Current).Services.GetRequiredService<IWebDavService>();
            // Unpackaged apps cannot use ApplicationData.Current. Using LocalSettingsHelper.
            // var localSettings = global::Windows.Storage.ApplicationData.Current.LocalSettings;

            // Load saved settings
            string savedUrl = LocalSettingsHelper.Get<string>("WebDavUrl") ?? "https://dav.jianguoyun.com/dav/";
            string savedUser = LocalSettingsHelper.Get<string>("WebDavUser") ?? "";
            string savedPass = LocalSettingsHelper.Get<string>("WebDavPass") ?? "";
            bool savedEncrypt = LocalSettingsHelper.Get<bool>("WebDavEncrypt", false);
            string savedEncPass = LocalSettingsHelper.Get<string>("WebDavEncPass") ?? "";

            var dialog = new ContentDialog
            {
                Title = "云端备份 (WebDAV)",
                CloseButtonText = "关闭",
                XamlRoot = this.XamlRoot
            };

            var mainStack = new StackPanel { Spacing = 16, MinWidth = 500 };

            // -- Config Section --
            var configExpander = new Expander 
            { 
                Header = "服务器配置", 
                IsExpanded = string.IsNullOrEmpty(savedUser),
                HorizontalAlignment = HorizontalAlignment.Stretch,
                HorizontalContentAlignment = HorizontalAlignment.Stretch
            };
            var configStack = new StackPanel { Spacing = 12, Padding = new Thickness(0, 12, 0, 0) }; // Remove side padding to maximize width
            
            var serverUrl = new TextBox { Header = "服务器地址", Text = savedUrl, PlaceholderText = "https://example.com/webdav/", HorizontalAlignment = HorizontalAlignment.Stretch };
            var username = new TextBox { Header = "用户名", Text = savedUser, HorizontalAlignment = HorizontalAlignment.Stretch };
            var password = new PasswordBox { Header = "密码", Password = savedPass, HorizontalAlignment = HorizontalAlignment.Stretch };
            var testBtn = new Button { Content = "测试连接 & 保存", HorizontalAlignment = HorizontalAlignment.Right };
            var statusText = new TextBlock { FontSize = 12, Opacity = 0.7, Margin = new Thickness(0, 4, 0, 0), HorizontalAlignment = HorizontalAlignment.Left, TextWrapping = TextWrapping.Wrap };

            configStack.Children.Add(serverUrl);
            configStack.Children.Add(username);
            configStack.Children.Add(password);
            
            var btnStack = new Grid();
            btnStack.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            btnStack.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Auto) });
            
            // Put status text on left, button on right
            Grid.SetColumn(statusText, 0);
            Grid.SetColumn(testBtn, 1);
            btnStack.Children.Add(statusText);
            btnStack.Children.Add(testBtn);
            
            configStack.Children.Add(btnStack);
            configExpander.Content = configStack;

            // -- Actions Section --
            var actionStack = new StackPanel { Spacing = 12 };
            
            // Encrypt Option
            var encryptCheck = new CheckBox { Content = "启用加密 (推荐)", IsChecked = savedEncrypt };
            var encPassBox = new PasswordBox { Header = "加密密码", Password = savedEncPass, Visibility = savedEncrypt ? Visibility.Visible : Visibility.Collapsed };
            
            encryptCheck.Checked += (s, args) => { encPassBox.Visibility = Visibility.Visible; };
            encryptCheck.Unchecked += (s, args) => { encPassBox.Visibility = Visibility.Collapsed; };

            var backupBtn = new Button { Content = "立即备份", HorizontalAlignment = HorizontalAlignment.Stretch };
            var restoreBtn = new Button { Content = "从云端恢复", HorizontalAlignment = HorizontalAlignment.Stretch };

            var actionStatusText = new TextBlock { FontSize = 12, Opacity = 0.7, Margin = new Thickness(0, 4, 0, 0), HorizontalAlignment = HorizontalAlignment.Left, TextWrapping = TextWrapping.Wrap };
            
            actionStack.Children.Add(new TextBlock { Text = "备份操作", FontWeight = Microsoft.UI.Text.FontWeights.Bold });
            actionStack.Children.Add(encryptCheck);
            actionStack.Children.Add(encPassBox);
            actionStack.Children.Add(backupBtn);
            actionStack.Children.Add(restoreBtn);
            actionStack.Children.Add(actionStatusText);

            mainStack.Children.Add(configExpander);
            mainStack.Children.Add(new Border { Height = 1, Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(50, 127, 127, 127)) });
            mainStack.Children.Add(actionStack);

            dialog.Content = new ScrollViewer { Content = mainStack, MaxHeight = 600 };

            // Logic
            testBtn.Click += async (s, args) =>
            {
                statusText.Text = "正在连接...";
                testBtn.IsEnabled = false;
                try
                {
                    webDavService.Configure(serverUrl.Text, username.Text, password.Password);
                    var (ok, msg) = await webDavService.TestConnectionAsync();
                    if (ok)
                    {
                        statusText.Text = "连接成功！配置已保存。";
                        statusText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 0, 128, 0)); // Green
                        
                        // Save settings
                        LocalSettingsHelper.Set("WebDavUrl", serverUrl.Text);
                        LocalSettingsHelper.Set("WebDavUser", username.Text);
                        LocalSettingsHelper.Set("WebDavPass", password.Password);
                        configExpander.IsExpanded = false;
                    }
                    else
                    {
                        statusText.Text = $"连接失败: {msg}";
                        statusText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 255, 0, 0)); // Red
                    }
                }
                catch (Exception ex)
                {
                    statusText.Text = $"错误: {ex.Message}";
                }
                finally
                {
                    testBtn.IsEnabled = true;
                }
            };

            backupBtn.Click += async (s, args) =>
            {
                backupBtn.IsEnabled = false;
                actionStatusText.Text = "正在备份...";
                actionStatusText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 128, 128, 128));

                try
                {
                    // Ensure configured
                    webDavService.Configure(serverUrl.Text, username.Text, password.Password);
                    
                    bool doEncrypt = encryptCheck.IsChecked == true;
                    string pass = encPassBox.Password;

                    if (doEncrypt && string.IsNullOrEmpty(pass))
                    {
                        actionStatusText.Text = "错误: 请设置加密密码";
                        actionStatusText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 255, 0, 0));
                        return;
                    }

                    // Save Encryption settings
                    LocalSettingsHelper.Set("WebDavEncrypt", doEncrypt);
                    LocalSettingsHelper.Set("WebDavEncPass", pass);

                    actionStatusText.Text = "正在生成并上传备份...";
                    string fileName = await webDavService.CreateBackupAsync(doEncrypt, pass);
                    
                    actionStatusText.Text = $"备份成功! 文件: {fileName}";
                    actionStatusText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 0, 128, 0));
                }
                catch (Exception ex)
                {
                    actionStatusText.Text = $"备份失败: {ex.Message}";
                    actionStatusText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 255, 0, 0));
                }
                finally
                {
                    backupBtn.IsEnabled = true;
                }
            };

            restoreBtn.Click += async (s, args) =>
            {
                restoreBtn.IsEnabled = false;
                actionStatusText.Text = "正在获取备份列表...";
                actionStatusText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 128, 128, 128));

                try
                {
                    webDavService.Configure(serverUrl.Text, username.Text, password.Password);
                    var (files, listError) = await webDavService.ListBackupsAsync();

                    if (listError != null)
                    {
                        actionStatusText.Text = $"错误: {listError}";
                        actionStatusText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 255, 0, 0));
                        return;
                    }

                    if (files.Count == 0)
                    {
                        actionStatusText.Text = "提示: 云端没有找到备份文件";
                        return;
                    }

                    // Cannot show another ContentDialog. Must hide current one or use a popup.
                    // To simplify, we'll hide the main dialog, show selection, then restore main dialog if cancelled.
                    // But 'dialog' variable scope is tricky. 
                    // Alternate: Use inline list in the current dialog?
                    // Better: Hide current dialog, Show Select Dialog.
                    
                    dialog.Hide(); 

                    var fileList = new ListView { ItemsSource = files, SelectionMode = ListViewSelectionMode.Single };
                    var selectDialog = new ContentDialog
                    {
                        Title = "选择备份文件恢复",
                        Content = fileList,
                        PrimaryButtonText = "恢复",
                        CloseButtonText = "取消",
                        XamlRoot = this.XamlRoot,
                        IsPrimaryButtonEnabled = false
                    };

                    fileList.SelectionChanged += (sender, e) => selectDialog.IsPrimaryButtonEnabled = fileList.SelectedItem != null;

                    var result = await selectDialog.ShowAsync();
                    if (result == ContentDialogResult.Primary)
                    {
                        string selectedFile = fileList.SelectedItem as string;
                        
                        // Show Progress Dialog (Now safe as Select Dialog is closed)
                        var restoreProgress = new ContentDialog { Title = "恢复中", Content = new ProgressRing { IsActive = true }, XamlRoot = this.XamlRoot };
                        _ = restoreProgress.ShowAsync();

                        string restorePass = encPassBox.Password; // Try current password
                        
                        try 
                        {
                            string resultMsg = await webDavService.RestoreBackupAsync(selectedFile, restorePass);
                            restoreProgress.Hide();
                            await ShowMessageAsync("恢复成功", resultMsg);
                        }
                        catch (Exception ex)
                        {
                            restoreProgress.Hide();
                            // Retry with password prompt if needed
                            if (ex.Message.Contains("Password_Required") || ex.Message.Contains("Wrong_Password"))
                            {
                                var msg = ex.Message.Contains("Wrong_Password") ? "密码错误，请重试：" : "请输入备份密码：";
                                var pwdDialog = new ContentDialog
                                {
                                    Title = "解密备份",
                                    Content = new PasswordBox { Header = msg },
                                    PrimaryButtonText = "确认",
                                    CloseButtonText = "取消",
                                    XamlRoot = this.XamlRoot
                                };
                                var box = pwdDialog.Content as PasswordBox;

                                if (await pwdDialog.ShowAsync() == ContentDialogResult.Primary)
                                {
                                    restoreProgress = new ContentDialog { Title = "恢复中", Content = new ProgressRing { IsActive = true }, XamlRoot = this.XamlRoot };
                                    _ = restoreProgress.ShowAsync();
                                    try
                                    {
                                        string succMsg = await webDavService.RestoreBackupAsync(selectedFile, box.Password);
                                        restoreProgress.Hide();
                                        await ShowMessageAsync("恢复成功", succMsg);
                                    }
                                    catch (Exception finalEx)
                                    {
                                        restoreProgress.Hide();
                                        await ShowMessageAsync("恢复失败", finalEx.Message);
                                    }
                                }
                            }
                            else
                            {
                                await ShowMessageAsync("恢复失败", ex.Message);
                            }
                        }
                    }
                    else
                    {
                        // User cancelled selection, re-show main dialog?
                        // A bit complex with async flow. Just let it close for now.
                    }
                }
                catch (Exception ex)
                {
                    actionStatusText.Text = $"错误: {ex.Message}";
                    actionStatusText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 255, 0, 0));
                }
                finally
                {
                    restoreBtn.IsEnabled = true;
                }
            };

            await dialog.ShowAsync();
            #endif
        }

        private void Language_Changed(object sender, SelectionChangedEventArgs e)
        {
            // TODO: Implement language change
            // For now, just show a message that restart is required
        }

        private async System.Threading.Tasks.Task ShowMessageAsync(string title, string message)
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
}
