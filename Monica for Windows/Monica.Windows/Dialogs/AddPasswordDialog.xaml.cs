using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Monica.Windows.Models;
using Monica.Windows.Services;
using System;
using System.Security.Cryptography;

namespace Monica.Windows.Dialogs
{
    public sealed partial class AddPasswordDialog : ContentDialog
    {
        public PasswordEntry? Result { get; private set; }
        public string? AuthenticatorKey { get; private set; }
        public bool IsEditMode { get; private set; }
        private long _editId;

        public AddPasswordDialog()
        {
            this.InitializeComponent();
        }

        private string? _originalEncryptedPassword;
        private bool _isDecryptedView;

        public void SetEditMode(PasswordEntry entry, ISecurityService? securityService = null)
        {
            IsEditMode = true;
            _editId = entry.Id;
            _originalEncryptedPassword = entry.EncryptedPassword;
            Title = "编辑密码";

            TitleInput.Text = entry.Title;
            WebsiteInput.Text = entry.Website;
            UsernameInput.Text = entry.Username;
            
            // Try to decrypt and show password
            bool passwordShown = false;
            _isDecryptedView = false;
            
            if (securityService != null && !string.IsNullOrEmpty(entry.EncryptedPassword))
            {
                var decrypted = securityService.Decrypt(entry.EncryptedPassword);
                if (!string.IsNullOrEmpty(decrypted) && !decrypted.Contains("==") && decrypted.Length < 50)
                {
                    // Success case: Show decrypted password
                    PasswordInput.Password = decrypted;
                    passwordShown = true;
                    _isDecryptedView = true;
                }
            }
            
            if (!passwordShown)
            {
                // Failure case: Show raw encrypted data (as requested by user)
                PasswordInput.Password = entry.EncryptedPassword;
                
                // Show a warning indicator
                if (!string.IsNullOrEmpty(entry.EncryptedPassword))
                {
                    // We can reuse the ErrorText to show a warning initially
                    ErrorText.Text = "⚠️ 显示原始加密数据 (无法解密)";
                    ErrorText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Orange);
                    ErrorText.Visibility = Visibility.Visible;
                }
                else
                {
                    PasswordInput.PlaceholderText = "留空保持原密码";
                }
            }
            
            NotesInput.Text = entry.Notes;
        }

        private void GeneratePassword_Click(object sender, RoutedEventArgs e)
        {
            PasswordInput.Password = GenerateSecurePassword(16);
            _isDecryptedView = true; // Treating generated password as plaintext
        }

        private static string GenerateSecurePassword(int length)
        {
            const string chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*";
            var random = RandomNumberGenerator.Create();
            var bytes = new byte[length];
            random.GetBytes(bytes);
            var result = new char[length];
            for (int i = 0; i < length; i++)
            {
                result[i] = chars[bytes[i] % chars.Length];
            }
            return new string(result);
        }

        private void ContentDialog_PrimaryButtonClick(ContentDialog sender, ContentDialogButtonClickEventArgs args)
        {
            if (string.IsNullOrWhiteSpace(TitleInput.Text))
            {
                ErrorText.Text = "请输入标题";
                ErrorText.Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Red);
                ErrorText.Visibility = Visibility.Visible;
                args.Cancel = true;
                return;
            }

            // Determine the password to save
            string? passwordToSave = PasswordInput.Password;

            // Special handling if we were showing raw encrypted data
            if (IsEditMode && !_isDecryptedView)
            {
                if (passwordToSave == _originalEncryptedPassword)
                {
                    // User didn't change the raw encrypted string -> Treat as "Unchanged"
                    // Return null/empty so caller keeps original
                    passwordToSave = null;
                }
                else
                {
                    // User CHANGED the raw string -> Treat as NEW plaintext password
                    // (Caller will encrypt it)
                }
            }

            // Standard empty check
            if (!IsEditMode && string.IsNullOrEmpty(passwordToSave))
            {
                ErrorText.Text = "请输入密码";
                ErrorText.Visibility = Visibility.Visible;
                args.Cancel = true;
                return;
            }

            Result = new PasswordEntry
            {
                Id = IsEditMode ? _editId : 0,
                Title = TitleInput.Text.Trim(),
                Website = WebsiteInput.Text.Trim(),
                Username = UsernameInput.Text.Trim(),
                EncryptedPassword = passwordToSave, // Caller handles null (keep original) or string (encrypt)
                Notes = NotesInput.Text.Trim(),
                CreatedAt = DateTime.Now,
                UpdatedAt = DateTime.Now
            };

            // Capture authenticator key if provided
            if (!string.IsNullOrWhiteSpace(AuthenticatorKeyInput.Text))
            {
                AuthenticatorKey = AuthenticatorKeyInput.Text.Trim().ToUpperInvariant();
            }
        }
    }
}
