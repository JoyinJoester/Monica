using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Monica.Windows.Models;
using Monica.Windows.Services;
using Monica.Windows.ViewModels;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Windows.ApplicationModel.DataTransfer;

namespace Monica.Windows.Views
{
    public sealed partial class TotpPage : Page
    {
        public SecureItemsViewModel ViewModel { get; }
        private readonly ISecurityService _securityService;
        private DispatcherTimer _timer;
        private IServiceScope _scope;

        // Custom multi-select state
        private bool _isMultiSelectMode = false;
        private readonly HashSet<SecureItem> _selectedItems = new();
        private readonly Dictionary<SecureItem, Grid> _cardGrids = new();
        
        public TotpPage()
        {
            this.InitializeComponent();
            
            // Create a scope for this page instance to ensure fresh DbContext
            _scope = ((App)App.Current).Services.CreateScope();
            ViewModel = _scope.ServiceProvider.GetRequiredService<SecureItemsViewModel>();
            _securityService = _scope.ServiceProvider.GetRequiredService<ISecurityService>();
            
            ViewModel.Initialize(ItemType.Totp);
            this.Loaded += TotpPage_Loaded;
            this.Unloaded += TotpPage_Unloaded;

            _timer = new DispatcherTimer();
            _timer.Interval = TimeSpan.FromMilliseconds(100); // Smooth 10fps updates
            _timer.Tick += Timer_Tick;
        }

        private async void TotpPage_Loaded(object sender, RoutedEventArgs e)
        {
            _timer.Start();
            
            LoadingRing.IsActive = true;
            await ViewModel.LoadDataAsync();
            LoadingRing.IsActive = false;
        }

        private void TotpPage_Unloaded(object sender, RoutedEventArgs e)
        {
            _timer.Stop();
            _scope?.Dispose();
        }

        private void Timer_Tick(object sender, object e)
        {
            if (ViewModel.FilteredItems == null) return;

            var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

            foreach (var item in ViewModel.FilteredItems)
            {
                UpdateItem(item, now);
            }
        }

        private void UpdateItem(SecureItem item, long nowMs)
        {
            try
            {
                var data = GetTotpData(item);
                if (data == null || string.IsNullOrEmpty(data.Secret)) return;

                if (data.OtpType == "HOTP")
                {
                    // HOTP: static code until counter increments
                    if (string.IsNullOrEmpty(item.TotpCode) || item.TotpCode == "------")
                    {
                        item.TotpCode = TotpHelper.GenerateCode(data.Secret, data.Period, data.Digits, data.OtpType, data.Counter);
                    }
                    item.TotpTimeRemaining = "";
                    item.TotpProgress = 100;
                    return;
                }

                // TOTP: time-based
                var period = data.Period > 0 ? data.Period : 30;
                var periodMs = period * 1000L;
                var currentPeriodStart = (nowMs / periodMs) * periodMs;
                var elapsedMs = nowMs - currentPeriodStart;
                var remainingMs = periodMs - elapsedMs;
                var remainingSec = (int)Math.Ceiling(remainingMs / 1000.0);

                // Update progress smoothly (percentage remaining)
                item.TotpProgress = (remainingMs * 100.0) / periodMs;
                item.TotpTimeRemaining = $"{remainingSec}s";

                // Only regenerate code at period boundary (when remaining resets to ~period)
                if (remainingSec >= period - 1 || string.IsNullOrEmpty(item.TotpCode) || item.TotpCode == "------")
                {
                    item.TotpCode = TotpHelper.GenerateCode(data.Secret, data.Period, data.Digits, data.OtpType);
                }
            }
            catch
            {
                item.TotpCode = "Error";
            }
        }

        private TotpData? GetTotpData(SecureItem item)
        {
            // Use cached data if available
            if (item.CachedTotpData is TotpData cached) return cached;

            try
            {
                var decrypted = _securityService.Decrypt(item.ItemData);
                if (string.IsNullOrEmpty(decrypted)) return null;

                TotpData data;
                try
                {
                    data = System.Text.Json.JsonSerializer.Deserialize<TotpData>(decrypted) ?? new TotpData();
                }
                catch
                {
                    // Legacy format
                    data = new TotpData { Secret = decrypted, Period = 30, Digits = 6, OtpType = "TOTP" };
                }
                
                // Cache it
                item.CachedTotpData = data;
                return data;
            }
            catch
            {
                return null;
            }
        }

        // Card click handling
        private void CardGrid_PointerPressed(object sender, PointerRoutedEventArgs e)
        {
            if (!_isMultiSelectMode) return;
            
            // Only respond to left click
            var props = e.GetCurrentPoint(null).Properties;
            if (!props.IsLeftButtonPressed) return;
            
            if (sender is Grid grid && grid.DataContext is SecureItem item)
            {
                ToggleItemSelection(item, grid);
                e.Handled = true;
            }
        }

        private void CardGrid_RightTapped(object sender, RightTappedRoutedEventArgs e)
        {
            // Context menu will show automatically via Grid.ContextFlyout
        }

        // Context menu opening - update favorite text dynamically
        private void ContextMenuFlyout_Opening(object sender, object e)
        {
            if (sender is MenuFlyout flyout && flyout.Items.Count > 0)
            {
                // Find the favorite menu item and update text based on item's state
                foreach (var item in flyout.Items)
                {
                    if (item is MenuFlyoutItem mfi && mfi.Tag is SecureItem secureItem)
                    {
                        // Update favorite menu item text (check for both "标星" and "取消标星")
                        if (mfi.Text.Contains("标星"))
                        {
                            mfi.Text = secureItem.IsFavorite ? "取消标星" : "标星";
                        }
                    }
                }
            }
        }

        // Context menu handlers
        private void CopyMenuItem_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem mfi && mfi.Tag is SecureItem item)
            {
                CopyCode(item);
            }
        }

        private async void EditMenuItem_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem mfi && mfi.Tag is SecureItem item)
            {
                await ShowEditDialog(item);
            }
        }

        private async Task ShowEditDialog(SecureItem item)
        {
            var dialog = new ContentDialog
            {
                Title = "编辑验证器",
                PrimaryButtonText = "保存",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = this.XamlRoot
            };

            // Get existing TOTP data
            var existingData = GetTotpData(item);
            if (existingData == null)
            {
                existingData = new TotpData { Secret = "", Period = 30, Digits = 6, OtpType = "TOTP" };
            }

            var stack = new StackPanel { Spacing = 12, MinWidth = 400 };
            
            // Basic fields
            var titleBox = new TextBox { Header = "名称 *", Text = item.Title };
            var secretBox = new TextBox { Header = "密钥 (Base32) *", Text = existingData.Secret };
            var issuerBox = new TextBox { Header = "发行者", Text = existingData.Issuer ?? "" };
            var accountBox = new TextBox { Header = "账户", Text = existingData.AccountName ?? "" };
            var notesBox = new TextBox { Header = "备注", Text = item.Notes, AcceptsReturn = true, Height = 60 };
            
            stack.Children.Add(titleBox);
            stack.Children.Add(secretBox);
            stack.Children.Add(issuerBox);
            stack.Children.Add(accountBox);
            stack.Children.Add(notesBox);

            // Advanced settings expander
            var advancedExpander = new Expander
            {
                Header = "高级选项",
                HorizontalAlignment = HorizontalAlignment.Stretch,
                HorizontalContentAlignment = HorizontalAlignment.Stretch
            };

            var advancedStack = new StackPanel { Spacing = 12 };

            // OTP Type selector
            var otpTypeCombo = new ComboBox { Header = "OTP 类型", HorizontalAlignment = HorizontalAlignment.Stretch };
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "TOTP (基于时间)", Tag = "TOTP" });
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "HOTP (基于计数器)", Tag = "HOTP" });
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "Steam Guard", Tag = "STEAM" });
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "Yandex", Tag = "YANDEX" });
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "mOTP (移动OTP)", Tag = "MOTP" });
            
            // Select the correct OTP type
            for (int i = 0; i < otpTypeCombo.Items.Count; i++)
            {
                if ((otpTypeCombo.Items[i] as ComboBoxItem)?.Tag?.ToString() == existingData.OtpType)
                {
                    otpTypeCombo.SelectedIndex = i;
                    break;
                }
            }
            if (otpTypeCombo.SelectedIndex < 0) otpTypeCombo.SelectedIndex = 0;

            // Period
            var periodBox = new NumberBox 
            { 
                Header = "时间周期 (秒)", 
                Value = existingData.Period > 0 ? existingData.Period : 30, 
                Minimum = 10, 
                Maximum = 120,
                SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact
            };

            // Digits
            var digitsBox = new NumberBox 
            { 
                Header = "验证码位数", 
                Value = existingData.Digits > 0 ? existingData.Digits : 6, 
                Minimum = 5, 
                Maximum = 8,
                SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact
            };

            // Algorithm
            var algorithmCombo = new ComboBox { Header = "算法" };
            algorithmCombo.Items.Add("SHA1");
            algorithmCombo.Items.Add("SHA256");
            algorithmCombo.Items.Add("SHA512");
            algorithmCombo.SelectedItem = existingData.Algorithm ?? "SHA1";
            if (algorithmCombo.SelectedIndex < 0) algorithmCombo.SelectedIndex = 0;

            // Counter (for HOTP)
            var counterBox = new NumberBox 
            { 
                Header = "计数器 (HOTP)", 
                Value = existingData.Counter, 
                Minimum = 0,
                Visibility = existingData.OtpType == "HOTP" ? Visibility.Visible : Visibility.Collapsed,
                SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact
            };

            // PIN (for mOTP)
            var pinBox = new PasswordBox 
            { 
                Header = "PIN 码 (mOTP)", 
                PlaceholderText = "4位数字",
                Password = existingData.Pin ?? "",
                Visibility = existingData.OtpType == "MOTP" ? Visibility.Visible : Visibility.Collapsed
            };

            // Show/hide fields based on OTP type
            otpTypeCombo.SelectionChanged += (s, args) =>
            {
                var selected = (otpTypeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "TOTP";
                counterBox.Visibility = selected == "HOTP" ? Visibility.Visible : Visibility.Collapsed;
                pinBox.Visibility = selected == "MOTP" ? Visibility.Visible : Visibility.Collapsed;
                periodBox.Visibility = selected == "HOTP" ? Visibility.Collapsed : Visibility.Visible;
                
                if (selected == "STEAM")
                {
                    digitsBox.Value = 5;
                    digitsBox.IsEnabled = false;
                }
                else if (selected == "YANDEX")
                {
                    digitsBox.Value = 8;
                    digitsBox.IsEnabled = false;
                }
                else
                {
                    digitsBox.IsEnabled = true;
                }
            };

            advancedStack.Children.Add(otpTypeCombo);
            advancedStack.Children.Add(periodBox);
            advancedStack.Children.Add(digitsBox);
            advancedStack.Children.Add(algorithmCombo);
            advancedStack.Children.Add(counterBox);
            advancedStack.Children.Add(pinBox);

            advancedExpander.Content = advancedStack;
            stack.Children.Add(advancedExpander);

            var scrollViewer = new ScrollViewer 
            { 
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto, 
                Content = stack,
                MaxHeight = 500
            };
            dialog.Content = scrollViewer;

            var result = await dialog.ShowAsync();
            if (result == ContentDialogResult.Primary)
            {
                if (string.IsNullOrWhiteSpace(titleBox.Text) || string.IsNullOrWhiteSpace(secretBox.Text)) return;

                var selectedOtpType = (otpTypeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "TOTP";

                var totpData = new TotpData
                {
                    Secret = secretBox.Text.Trim().ToUpperInvariant(),
                    Issuer = issuerBox.Text.Trim(),
                    AccountName = accountBox.Text.Trim(),
                    Period = (int)periodBox.Value,
                    Digits = (int)digitsBox.Value,
                    Algorithm = algorithmCombo.SelectedItem?.ToString() ?? "SHA1",
                    OtpType = selectedOtpType,
                    Counter = (long)counterBox.Value,
                    Pin = pinBox.Password
                };

                var json = System.Text.Json.JsonSerializer.Serialize(totpData);
                var encryptedData = _securityService.Encrypt(json);

                // Update the item
                item.Title = titleBox.Text.Trim();
                item.Notes = notesBox.Text.Trim();
                item.ItemData = encryptedData;
                item.CachedTotpData = null; // Clear cache so it reloads

                await ViewModel.UpdateItemAsync(item);
                await ViewModel.LoadDataAsync(); // Refresh the list
            }
        }

        private async void ToggleFavorite_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem mfi && mfi.Tag is SecureItem item)
            {
                item.IsFavorite = !item.IsFavorite;
                await ViewModel.UpdateItemAsync(item);
                await ViewModel.LoadDataAsync(); // Reload to refresh UI
            }
        }

        private void DeleteMenuItem_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem mfi && mfi.Tag is SecureItem item)
            {
                DeleteItem(item);
            }
        }

        // Multi-select logic
        private void EnterMultiSelect_Click(object sender, RoutedEventArgs e)
        {
            _isMultiSelectMode = true;
            MultiSelectToolbar.Visibility = Visibility.Visible;
            _selectedItems.Clear();
            _cardGrids.Clear();
            
            // Select the clicked item and find its Grid
            if (sender is MenuFlyoutItem mfi && mfi.Tag is SecureItem item)
            {
                _selectedItems.Add(item);
                
                // Find the Grid in the ListView for this item
                var container = TotpListView.ContainerFromItem(item) as ListViewItem;
                if (container != null)
                {
                    var grid = FindChildGrid(container);
                    if (grid != null)
                    {
                        grid.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["AccentFillColorDefaultBrush"];
                        _cardGrids[item] = grid;
                    }
                }
            }
            
            UpdateSelectedCount();
        }

        private Grid? FindChildGrid(DependencyObject parent)
        {
            for (int i = 0; i < Microsoft.UI.Xaml.Media.VisualTreeHelper.GetChildrenCount(parent); i++)
            {
                var child = Microsoft.UI.Xaml.Media.VisualTreeHelper.GetChild(parent, i);
                if (child is Grid grid && grid.Name == "CardGrid")
                {
                    return grid;
                }
                var result = FindChildGrid(child);
                if (result != null) return result;
            }
            return null;
        }

        private void CancelMultiSelect_Click(object sender, RoutedEventArgs e)
        {
            _isMultiSelectMode = false;
            MultiSelectToolbar.Visibility = Visibility.Collapsed;
            
            // Reset all card borders
            foreach (var kvp in _cardGrids)
            {
                kvp.Value.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardStrokeColorDefaultBrush"];
            }
            _selectedItems.Clear();
            _cardGrids.Clear();
        }

        private void ToggleItemSelection(SecureItem item, Grid? grid = null)
        {
            if (_selectedItems.Contains(item))
            {
                _selectedItems.Remove(item);
                if (grid != null)
                {
                    grid.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardStrokeColorDefaultBrush"];
                    _cardGrids.Remove(item);
                }
            }
            else
            {
                _selectedItems.Add(item);
                if (grid != null)
                {
                    grid.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["AccentFillColorDefaultBrush"];
                    _cardGrids[item] = grid;
                }
            }
            UpdateSelectedCount();
        }

        private void UpdateSelectedCount()
        {
            SelectedCountText.Text = $"已选 {_selectedItems.Count} 项";
        }

        // Batch operations
        private async void BatchFavorite_Click(object sender, RoutedEventArgs e)
        {
            foreach (var item in _selectedItems.ToList())
            {
                if (!item.IsFavorite)
                {
                    item.IsFavorite = true;
                    await ViewModel.UpdateItemAsync(item);
                }
            }
            CancelMultiSelect_Click(sender, e);
        }

        private async void BatchDelete_Click(object sender, RoutedEventArgs e)
        {
            var selectedItems = _selectedItems.ToList();
            if (selectedItems.Count == 0) return;

            var dialog = new ContentDialog
            {
                Title = "批量删除",
                Content = $"确定删除选中的 {selectedItems.Count} 项验证器吗？此操作无法撤销。",
                PrimaryButtonText = "删除",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Close,
                XamlRoot = this.XamlRoot
            };

            if (await dialog.ShowAsync() == ContentDialogResult.Primary)
            {
                foreach (var item in selectedItems)
                {
                    await ViewModel.DeleteItemAsync(item);
                }
                CancelMultiSelect_Click(sender, e);
            }
        }

        // Helper methods
        private void CopyCode(SecureItem item)
        {
            try
            {
                var data = GetTotpData(item);
                if (data != null && !string.IsNullOrEmpty(data.Secret))
                {
                    var code = TotpHelper.GenerateCode(data.Secret, data.Period, data.Digits, data.OtpType);
                    var pkg = new DataPackage();
                    pkg.SetText(code);
                    Clipboard.SetContent(pkg);
                }
            }
            catch {}
        }

        private async void DeleteItem(SecureItem item)
        {
            var dialog = new ContentDialog
            {
                Title = "删除确认",
                Content = "确定要删除此验证器吗？",
                PrimaryButtonText = "删除",
                CloseButtonText = "取消",
                XamlRoot = this.XamlRoot
            };
            if (await dialog.ShowAsync() == ContentDialogResult.Primary)
            {
                await ViewModel.DeleteItemAsync(item);
            }
        }

        private async void AddButton_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new ContentDialog
            {
                Title = "添加验证器",
                PrimaryButtonText = "保存",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = this.XamlRoot
            };

            var stack = new StackPanel { Spacing = 12, MinWidth = 400 };
            
            // Basic fields
            var titleBox = new TextBox { Header = "名称 *", PlaceholderText = "例如: Google" };
            var secretBox = new TextBox { Header = "密钥 (Base32) *", PlaceholderText = "JBSWY3DPEHPK3PXP" };
            var issuerBox = new TextBox { Header = "发行者", PlaceholderText = "例如: Google" };
            var accountBox = new TextBox { Header = "账户", PlaceholderText = "user@example.com" };
            var notesBox = new TextBox { Header = "备注", PlaceholderText = "可选备注...", AcceptsReturn = true, Height = 60 };
            
            stack.Children.Add(titleBox);
            stack.Children.Add(secretBox);
            stack.Children.Add(issuerBox);
            stack.Children.Add(accountBox);
            stack.Children.Add(notesBox);

            // Advanced settings expander
            var advancedExpander = new Expander
            {
                Header = "高级选项",
                HorizontalAlignment = HorizontalAlignment.Stretch,
                HorizontalContentAlignment = HorizontalAlignment.Stretch
            };

            var advancedStack = new StackPanel { Spacing = 12 };

            // OTP Type selector
            var otpTypeCombo = new ComboBox { Header = "OTP 类型", HorizontalAlignment = HorizontalAlignment.Stretch };
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "TOTP (基于时间)", Tag = "TOTP" });
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "HOTP (基于计数器)", Tag = "HOTP" });
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "Steam Guard", Tag = "STEAM" });
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "Yandex", Tag = "YANDEX" });
            otpTypeCombo.Items.Add(new ComboBoxItem { Content = "mOTP (移动OTP)", Tag = "MOTP" });
            otpTypeCombo.SelectedIndex = 0;

            // Period
            var periodBox = new NumberBox 
            { 
                Header = "时间周期 (秒)", 
                Value = 30, 
                Minimum = 10, 
                Maximum = 120,
                SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact
            };

            // Digits
            var digitsBox = new NumberBox 
            { 
                Header = "验证码位数", 
                Value = 6, 
                Minimum = 5, 
                Maximum = 8,
                SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact
            };

            // Algorithm
            var algorithmCombo = new ComboBox { Header = "算法" };
            algorithmCombo.Items.Add("SHA1");
            algorithmCombo.Items.Add("SHA256");
            algorithmCombo.Items.Add("SHA512");
            algorithmCombo.SelectedIndex = 0;

            // Counter (for HOTP)
            var counterBox = new NumberBox 
            { 
                Header = "初始计数器 (HOTP)", 
                Value = 0, 
                Minimum = 0,
                Visibility = Visibility.Collapsed,
                SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact
            };

            // PIN (for mOTP)
            var pinBox = new PasswordBox 
            { 
                Header = "PIN 码 (mOTP)", 
                PlaceholderText = "4位数字",
                Visibility = Visibility.Collapsed
            };

            // Show/hide fields based on OTP type
            otpTypeCombo.SelectionChanged += (s, args) =>
            {
                var selected = (otpTypeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "TOTP";
                counterBox.Visibility = selected == "HOTP" ? Visibility.Visible : Visibility.Collapsed;
                pinBox.Visibility = selected == "MOTP" ? Visibility.Visible : Visibility.Collapsed;
                periodBox.Visibility = selected == "HOTP" ? Visibility.Collapsed : Visibility.Visible;
                
                // Steam uses 5 digits, Yandex uses 8 digits
                if (selected == "STEAM")
                {
                    digitsBox.Value = 5;
                    digitsBox.IsEnabled = false;
                }
                else if (selected == "YANDEX")
                {
                    digitsBox.Value = 8;
                    digitsBox.IsEnabled = false;
                }
                else
                {
                    digitsBox.IsEnabled = true;
                    if (digitsBox.Value == 5 || digitsBox.Value == 8) digitsBox.Value = 6;
                }
            };

            advancedStack.Children.Add(otpTypeCombo);
            advancedStack.Children.Add(periodBox);
            advancedStack.Children.Add(digitsBox);
            advancedStack.Children.Add(algorithmCombo);
            advancedStack.Children.Add(counterBox);
            advancedStack.Children.Add(pinBox);

            advancedExpander.Content = advancedStack;
            stack.Children.Add(advancedExpander);

            var scrollViewer = new ScrollViewer 
            { 
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto, 
                Content = stack,
                MaxHeight = 500
            };
            dialog.Content = scrollViewer;

            var result = await dialog.ShowAsync();
            if (result == ContentDialogResult.Primary)
            {
                if (string.IsNullOrWhiteSpace(titleBox.Text) || string.IsNullOrWhiteSpace(secretBox.Text)) return;

                var selectedOtpType = (otpTypeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "TOTP";

                var totpData = new TotpData
                {
                    Secret = secretBox.Text.Trim().ToUpperInvariant(),
                    Issuer = issuerBox.Text.Trim(),
                    AccountName = accountBox.Text.Trim(),
                    Period = (int)periodBox.Value,
                    Digits = (int)digitsBox.Value,
                    Algorithm = algorithmCombo.SelectedItem?.ToString() ?? "SHA1",
                    OtpType = selectedOtpType,
                    Counter = (long)counterBox.Value,
                    Pin = pinBox.Password
                };

                var json = System.Text.Json.JsonSerializer.Serialize(totpData);
                var encryptedData = _securityService.Encrypt(json);

                var item = new SecureItem
                {
                    Title = titleBox.Text.Trim(),
                    Notes = notesBox.Text.Trim(),
                    ItemData = encryptedData,
                    ItemType = ItemType.Totp
                };

                await ViewModel.AddItemAsync(item);
            }
        }
        
        private void CopyButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is SecureItem item)
            {
                CopyCode(item);
            }
        }

        private async void DeleteButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is SecureItem item)
            {
                DeleteItem(item);
            }
        }
    }
}
