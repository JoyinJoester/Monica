using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Monica.Windows.Data;
using Monica.Windows.Dialogs;
using Monica.Windows.Services;
using System;
using System.Reflection;
using System.Threading.Tasks;
using Windows.ApplicationModel;
using Microsoft.EntityFrameworkCore;
using System.IO;
using System.Collections.Generic;
using System.Linq;


namespace Monica.Windows.ViewModels
{
    public partial class SettingsViewModel : ObservableObject
    {
        private readonly ISecurityService _securityService;

        [ObservableProperty]
        private string _appVersion;

        public SettingsViewModel(ISecurityService securityService)
        {
            _securityService = securityService;
            AppVersion = GetAppVersion();
        }

        private string GetAppVersion()
        {
            return "V1.0.19";
        }

        [RelayCommand]
        private async Task ChangeMasterPassword()
        {
            var dialog = new ContentDialog
            {
                Title = "修改主密码",
                PrimaryButtonText = "确认修改",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = App.MainWindow.Content.XamlRoot
            };

            var stack = new StackPanel { Spacing = 12, MinWidth = 300 };
            var currentPassword = new PasswordBox { Header = "当前密码", PlaceholderText = "输入当前主密码" };
            var newPassword = new PasswordBox { Header = "新密码", PlaceholderText = "输入新密码" };
            var confirmPassword = new PasswordBox { Header = "确认新密码", PlaceholderText = "再次输入新密码" };
            
            stack.Children.Add(currentPassword);
            stack.Children.Add(newPassword);
            stack.Children.Add(confirmPassword);
            dialog.Content = stack;

            var result = await dialog.ShowAsync();
            if (result != ContentDialogResult.Primary) return;

            if (string.IsNullOrEmpty(currentPassword.Password) || string.IsNullOrEmpty(newPassword.Password))
            {
                await ShowMessageAsync("错误", "密码不能为空");
                return;
            }

            if (newPassword.Password.Length < 6)
            {
                await ShowMessageAsync("错误", "新密码长度至少需要6位");
                return;
            }

            if (newPassword.Password != confirmPassword.Password)
            {
                await ShowMessageAsync("错误", "两次输入的新密码不一致");
                return;
            }

            // 1. Verify Old Password
            // Note: We deliberately use internal logic or retry unlock to verify.
            // Since we are likely already unlocked, we shouldn't lock ourselves out yet.
            // But we need to verify the input matches the CURRENT actual key/hash.
            // A simple way is to try to Unlock. If it returns true, it matches.
            // However, Unlock sets the master key. This is fine as it just refreshes it.
            if (!_securityService.Unlock(currentPassword.Password))
            {
                await ShowMessageAsync("错误", "当前密码错误");
                return;
            }

            // Show loading
            var loadingDialog = new ContentDialog
            {
                Title = "请稍候",
                Content = new ProgressRing { IsActive = true, Width = 50, Height = 50 },
                XamlRoot = App.MainWindow.Content.XamlRoot
            };
            var loadingTask = loadingDialog.ShowAsync();

            await ReEncryptDatabase(loadingDialog, newPassword.Password, currentPassword.Password);
        }

        [RelayCommand]
        private async Task ResetMasterPassword()
        {
            var question = _securityService.GetSecurityQuestion();
            if (string.IsNullOrEmpty(question))
            {
                await ShowMessageAsync("错误", "未设置密保问题");
                return;
            }

            var dialog = new ContentDialog
            {
                Title = "重设主密码",
                PrimaryButtonText = "验证并重设",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = App.MainWindow.Content.XamlRoot
            };

            var stack = new StackPanel { Spacing = 12, MinWidth = 350 };
            var info = new TextBlock 
            { 
                Text = $"请回答密保问题：\n{question}",
                TextWrapping = TextWrapping.Wrap,
                FontWeight = Microsoft.UI.Text.FontWeights.SemiBold
            };
            var answerInput = new PasswordBox { Header = "答案", PlaceholderText = "输入密保答案" };
            var newPassword = new PasswordBox { Header = "新密码", PlaceholderText = "输入新密码" };
            var confirmPassword = new PasswordBox { Header = "确认新密码", PlaceholderText = "再次输入新密码" };
            
            stack.Children.Add(info);
            stack.Children.Add(answerInput);
            stack.Children.Add(newPassword);
            stack.Children.Add(confirmPassword);
            dialog.Content = stack;

            var result = await dialog.ShowAsync();
            if (result != ContentDialogResult.Primary) return;

            if (string.IsNullOrEmpty(answerInput.Password) || string.IsNullOrEmpty(newPassword.Password))
            {
                await ShowMessageAsync("错误", "所有字段都必须填写");
                return;
            }

            if (!_securityService.ValidateSecurityQuestion(answerInput.Password))
            {
                await ShowMessageAsync("错误", "密保答案错误");
                return;
            }

            if (newPassword.Password.Length < 6)
            {
                await ShowMessageAsync("错误", "新密码长度至少需要6位");
                return;
            }

            if (newPassword.Password != confirmPassword.Password)
            {
                await ShowMessageAsync("错误", "两次输入的新密码不一致");
                return;
            }

            // Safe Reset: We are already unlocked, so we can preserve data.
            var loadingDialog = new ContentDialog
            {
                Title = "正在重置",
                Content = new ProgressRing { IsActive = true, Width = 50, Height = 50 },
                XamlRoot = App.MainWindow.Content.XamlRoot
            };
            var loadingTask = loadingDialog.ShowAsync();

            await ReEncryptDatabase(loadingDialog, newPassword.Password, null);
        }

        private async Task ReEncryptDatabase(ContentDialog loadingDialog, string newPassword, string? currentPasswordForRollback)
        {
            try
            {
                var dbContext = ((App)App.Current).Services.GetRequiredService<AppDbContext>();

                // 1. Load all encrypted data
                var passwords = await dbContext.PasswordEntries.ToListAsync();
                var secureItems = await dbContext.SecureItems.ToListAsync();

                // 2. Decrypt everything using CURRENT key (which is loaded in memory)
                var plainPasswords = new List<(long Id, string PlainPwd, string PlainCard, string PlainCVV)>();
                foreach (var p in passwords)
                {
                    plainPasswords.Add((
                        p.Id, 
                        _securityService.Decrypt(p.EncryptedPassword),
                        !string.IsNullOrEmpty(p.EncryptedCreditCardNumber) ? _securityService.Decrypt(p.EncryptedCreditCardNumber) : "",
                        !string.IsNullOrEmpty(p.EncryptedCreditCardCVV) ? _securityService.Decrypt(p.EncryptedCreditCardCVV) : ""
                    ));
                }

                var plainSecureItems = new List<(long Id, string ItemData)>();
                foreach (var i in secureItems)
                {
                    plainSecureItems.Add((i.Id, _securityService.Decrypt(i.ItemData)));
                }

                // 3. Backup security.json
                var folder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                var configPath = Path.Combine(folder, "Monica", "security.json");
                var backupPath = configPath + ".bak";
                File.Copy(configPath, backupPath, true);

                try
                {
                    // 4. Rotate Key
                    _securityService.SetMasterPassword(newPassword);
                    _securityService.Unlock(newPassword); // Generate new session key from new password

                    // 5. Re-Encrypt
                    using var transaction = await dbContext.Database.BeginTransactionAsync();
                    try
                    {
                        foreach (var p in passwords)
                        {
                            var plain = plainPasswords.First(x => x.Id == p.Id);
                            p.EncryptedPassword = _securityService.Encrypt(plain.PlainPwd);
                            if (!string.IsNullOrEmpty(plain.PlainCard)) 
                                p.EncryptedCreditCardNumber = _securityService.Encrypt(plain.PlainCard);
                            if (!string.IsNullOrEmpty(plain.PlainCVV)) 
                                p.EncryptedCreditCardCVV = _securityService.Encrypt(plain.PlainCVV);
                            
                            dbContext.Entry(p).State = EntityState.Modified;
                        }

                        foreach (var i in secureItems)
                        {
                            var plain = plainSecureItems.First(x => x.Id == i.Id);
                            i.ItemData = _securityService.Encrypt(plain.ItemData);
                            dbContext.Entry(i).State = EntityState.Modified;
                        }

                        await dbContext.SaveChangesAsync();
                        await transaction.CommitAsync();

                        // 6. Success Cleanup
                        File.Delete(backupPath);
                        loadingDialog.Hide();
                        await ShowMessageAsync("成功", "主密码已更新，所有数据已重新加密。");
                    }
                    catch (Exception ex)
                    {
                        await transaction.RollbackAsync();
                        throw; // Re-throw to trigger outer rollback
                    }
                }
                catch (Exception ex)
                {
                    // ROLLBACK EVERYTHING
                    if (File.Exists(backupPath))
                    {
                        File.Copy(backupPath, configPath, true);
                        File.Delete(backupPath);
                    }
                    
                    // Attempt to restore old key session IF we know the old password
                    if (!string.IsNullOrEmpty(currentPasswordForRollback))
                    {
                        _securityService.Unlock(currentPasswordForRollback);
                    }
                    else
                    {
                        // Sentinel: We lost the key state consistency.
                        // Ideally we shouldn't reach here easily unless filesystem/DB failure.
                    }
                    
                    throw new Exception("Re-encryption failed. Changes reverted. " + ex.Message);
                }
            }
            catch (Exception ex)
            {
                loadingDialog.Hide();
                await ShowMessageAsync("失败", $"操作失败: {ex.Message}");
            }
        }

        [RelayCommand]
        private async Task ClearDatabase()
        {
            // Create a dialog with selection options
            var stack = new StackPanel { Spacing = 16 };
            
            var radioAll = new RadioButton { Content = "清空所有数据 (密码、2FA、笔记、卡片)", GroupName = "ClearOption", IsChecked = true };
            var radioPasswords = new RadioButton { Content = "仅清空密码", GroupName = "ClearOption" };
            var radioSecure = new RadioButton { Content = "仅清空安全项目 (2FA、笔记、卡片)", GroupName = "ClearOption" };
            
            var confirmTextBox = new TextBox 
            { 
                Header = "请输入 \"我确认清空全部数据\" 以确认",
                PlaceholderText = "我确认清空全部数据"
            };
            
            stack.Children.Add(new TextBlock { Text = "选择要清空的数据类型:", TextWrapping = TextWrapping.Wrap });
            stack.Children.Add(radioAll);
            stack.Children.Add(radioPasswords);
            stack.Children.Add(radioSecure);
            stack.Children.Add(new TextBlock 
            { 
                Text = "⚠️ 此操作无法撤销！请确保已备份重要数据。", 
                Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Orange),
                FontSize = 12,
                TextWrapping = TextWrapping.Wrap
            });
            stack.Children.Add(confirmTextBox);

            var dialog = new ContentDialog
            {
                Title = "清空数据",
                Content = stack,
                PrimaryButtonText = "确认删除",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Close,
                XamlRoot = App.MainWindow.Content.XamlRoot
            };

            var result = await dialog.ShowAsync();
            if (result == ContentDialogResult.Primary)
            {
                // Validate confirmation text
                if (confirmTextBox.Text != "我确认清空全部数据")
                {
                    await ShowMessageAsync("验证失败", "请输入正确的确认文字");
                    return;
                }
                
                try
                {
                    var dbContext = ((App)App.Current).Services.GetRequiredService<AppDbContext>();
                    
                    if (radioAll.IsChecked == true)
                    {
                        // Clear all data
                        dbContext.PasswordEntries.RemoveRange(dbContext.PasswordEntries);
                        dbContext.SecureItems.RemoveRange(dbContext.SecureItems);
                        await dbContext.SaveChangesAsync();
                        
                        // Also clear security config and restart
                        var configPath = System.IO.Path.Combine(
                            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), 
                            "Monica", "security.json");
                        if (System.IO.File.Exists(configPath))
                        {
                            System.IO.File.Delete(configPath);
                        }
                        
                        await ShowMessageAsync("成功", "所有数据已清空。应用将重启。");
                        Application.Current.Exit();
                    }
                    else if (radioPasswords.IsChecked == true)
                    {
                        // Clear only passwords
                        dbContext.PasswordEntries.RemoveRange(dbContext.PasswordEntries);
                        await dbContext.SaveChangesAsync();
                        await ShowMessageAsync("成功", $"已清空所有密码条目。");
                    }
                    else if (radioSecure.IsChecked == true)
                    {
                        // Clear only secure items (TOTP, notes, cards)
                        dbContext.SecureItems.RemoveRange(dbContext.SecureItems);
                        await dbContext.SaveChangesAsync();
                        await ShowMessageAsync("成功", "已清空所有安全项目 (2FA、笔记、卡片)。");
                    }
                }
                catch (Exception ex)
                {
                    await ShowMessageAsync("错误", $"清空数据失败: {ex.Message}");
                }
            }
        }

        private async Task ShowMessageAsync(string title, string message)
        {
            var msgDialog = new ContentDialog
            {
                Title = title,
                Content = message,
                CloseButtonText = "确定",
                XamlRoot = App.MainWindow.Content.XamlRoot
            };
            await msgDialog.ShowAsync();
        }

        [RelayCommand]
        private async Task OpenGitHub()
        {
            await global::Windows.System.Launcher.LaunchUriAsync(new Uri("https://github.com/JoyinJoester/Monica"));
        }
    }
}
