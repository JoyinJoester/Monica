using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.Extensions.DependencyInjection;
using Monica.Windows.Services;
using System;
using System.Threading.Tasks;

namespace Monica.Windows.Views
{
    public sealed partial class LoginPage : Page
    {
        private readonly ISecurityService _securityService;
        private bool _isFirstTimeSetup;

        public LoginPage()
        {
            this.InitializeComponent();
            _securityService = ((App)App.Current).Services.GetRequiredService<ISecurityService>();
            CheckFirstTimeSetup();
        }

        private void CheckFirstTimeSetup()
        {
            _isFirstTimeSetup = !_securityService.IsMasterPasswordSet();
            if (_isFirstTimeSetup)
            {
                ShowSetup();
            }
            else
            {
                ShowLogin();
            }
        }

        private void ShowLogin()
        {
            LoginPanel.Visibility = Visibility.Visible;
            SetupPanel.Visibility = Visibility.Collapsed;
            ResetPanel.Visibility = Visibility.Collapsed;
            PasswordInput.Focus(FocusState.Programmatic);
        }

        private void ShowSetup()
        {
            LoginPanel.Visibility = Visibility.Collapsed;
            SetupPanel.Visibility = Visibility.Visible;
            ResetPanel.Visibility = Visibility.Collapsed;
            SetupPasswordInput.Focus(FocusState.Programmatic);
        }

        private void ShowReset()
        {
            var question = _securityService.GetSecurityQuestion();
            if (string.IsNullOrEmpty(question)) return;

            ResetQuestionText.Text = $"问题：{question}";
            LoginPanel.Visibility = Visibility.Collapsed;
            SetupPanel.Visibility = Visibility.Collapsed;
            ResetPanel.Visibility = Visibility.Visible;
            ResetAnswerInput.Focus(FocusState.Programmatic);
        }

        private void PasswordInput_KeyDown(object sender, KeyRoutedEventArgs e)
        {
            if (e.Key == global::Windows.System.VirtualKey.Enter)
            {
                UnlockButton_Click(sender, e);
            }
        }

        private void UnlockButton_Click(object sender, RoutedEventArgs e)
        {
            var password = PasswordInput.Password;
            if (string.IsNullOrEmpty(password))
            {
                ShowError(LoginErrorText, "请输入主密码");
                return;
            }

            if (_securityService.Unlock(password))
            {
                if (App.MainWindow is MainWindow mainWindow)
                {
                    mainWindow.NavigateToMain();
                }
            }
            else
            {
                ShowError(LoginErrorText, "密码错误，请重试");
                PasswordInput.Password = "";
            }
        }

        private void SetupButton_Click(object sender, RoutedEventArgs e)
        {
            var password = SetupPasswordInput.Password;
            var confirmPassword = SetupConfirmPasswordInput.Password;

            if (string.IsNullOrEmpty(password))
            {
                ShowError(SetupErrorText, "请输入主密码");
                return;
            }

            if (password.Length < 6)
            {
                ShowError(SetupErrorText, "密码长度至少需要6位");
                return;
            }

            if (password != confirmPassword)
            {
                ShowError(SetupErrorText, "两次输入的密码不一致");
                return;
            }

            _securityService.SetMasterPassword(password);

            _securityService.Unlock(password);

            if (App.MainWindow is MainWindow mainWindow)
            {
                mainWindow.NavigateToMain();
            }
        }

        private async void ForgotPassword_Click(object sender, RoutedEventArgs e)
        {
            if (_securityService.IsSecurityQuestionSet())
            {
                ShowReset();
            }
            else
            {
                await ShowClearDataDialog();
            }
        }

        private async Task ShowClearDataDialog()
        {
            var dialog = new ContentDialog
            {
                Title = "清空全部信息",
                Content = new StackPanel
                {
                    Spacing = 10,
                    Children = 
                    {
                        new TextBlock { Text = "您没有设置密保问题，无法重置密码。", TextWrapping = TextWrapping.Wrap },
                        new TextBlock { Text = "您可以选择清空全部信息并重新设置。此操作将永久删除所有保存的数据且无法恢复。", TextWrapping = TextWrapping.Wrap, Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Red) },
                        new TextBlock { Text = "请输入 '我确定要清空全部信息' 以确认：", Margin = new Thickness(0, 10, 0, 4) }
                    }
                },
                PrimaryButtonText = "清空",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Close,
                XamlRoot = this.XamlRoot
            };

            var inputTextBox = new TextBox();
            ((StackPanel)dialog.Content).Children.Add(inputTextBox);

            dialog.PrimaryButtonClick += (s, args) =>
            {
                if (inputTextBox.Text != "我确定要清空全部信息")
                {
                    args.Cancel = true;
                    inputTextBox.Header = "输入错误，请重新输入";
                    // Ideally show error state on textbox
                }
            };

            var result = await dialog.ShowAsync();
            if (result == ContentDialogResult.Primary)
            {
                _securityService.ClearAllData();
                CheckFirstTimeSetup();
                ShowError(LoginErrorText, "数据已清空，请重新设置"); // Re-using login error text just to show a message if we stay on login, but CheckFirstTimeSetup should move us to setup.
            }
        }

        private async void VerifyAnswerButton_Click(object sender, RoutedEventArgs e)
        {
            var answer = ResetAnswerInput.Password;
            if (string.IsNullOrEmpty(answer))
            {
                ShowError(ResetErrorText, "请输入答案");
                return;
            }

            if (_securityService.ValidateSecurityQuestion(answer))
            {
                // Warn user that data will be lost
                var dialog = new ContentDialog
                {
                    Title = "验证成功",
                    Content = "密保问题验证成功。你可以重设主密码。\n\n⚠️ 警告：由于主密码已丢失，无法解密现有数据。重设主密码将清空所有现有数据并开始新的安全存储。\n\n是否继续？",
                    PrimaryButtonText = "继续重设",
                    CloseButtonText = "取消",
                    DefaultButton = ContentDialogButton.Close,
                    XamlRoot = this.XamlRoot
                };

                var result = await dialog.ShowAsync();

                if (result == ContentDialogResult.Primary)
                {
                    _securityService.ClearAllData();
                    CheckFirstTimeSetup();
                }
            }
            else
            {
                ShowError(ResetErrorText, "答案错误");
            }
        }

        private void CancelReset_Click(object sender, RoutedEventArgs e)
        {
            ShowLogin();
        }

        private void ShowError(TextBlock textBlock, string message)
        {
            textBlock.Text = message;
            textBlock.Visibility = Visibility.Visible;
        }
    }
}
