using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Monica.Windows.Dialogs;
using Monica.Windows.Models;
using Monica.Windows.Services;
using Monica.Windows.ViewModels;
using Windows.ApplicationModel.DataTransfer;
using System;
using System.Collections.Generic;
using System.Linq;

namespace Monica.Windows.Views
{
    public sealed partial class PasswordListPage : Page
    {
        public PasswordListViewModel ViewModel { get; }
        private readonly ISecurityService _securityService;
        private object? _filterParameter;
        private IServiceScope _scope;

        public PasswordListPage()
        {
            this.InitializeComponent();
            
            // Create a scope for this page instance
            _scope = ((App)App.Current).Services.CreateScope();
            ViewModel = _scope.ServiceProvider.GetRequiredService<PasswordListViewModel>();
            _securityService = _scope.ServiceProvider.GetRequiredService<ISecurityService>();
            
            this.Loaded += PasswordListPage_Loaded;
            this.Unloaded += PasswordListPage_Unloaded;
        }

        private void PasswordListPage_Unloaded(object sender, RoutedEventArgs e)
        {
            _scope?.Dispose();
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            _filterParameter = e.Parameter;
        }

        private async void PasswordListPage_Loaded(object sender, RoutedEventArgs e)
        {
            LoadingRing.IsActive = true;
            await ViewModel.LoadDataAsync(_filterParameter);
            LoadingRing.IsActive = false;
            UpdateEmptyState();
        }

        private void UpdateEmptyState()
        {
            CountText.Text = $"{ViewModel.FilteredPasswords.Count} 项";
            EmptyState.Visibility = ViewModel.FilteredPasswords.Count == 0 ? 
                Visibility.Visible : Visibility.Collapsed;
        }

        private void SearchBox_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
        {
            ViewModel.SearchQuery = sender.Text;
            UpdateEmptyState();
        }

        private string _quickFilter = "All";
        
        private async void FilterOption_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem item && item.Tag is string tag)
            {
                _quickFilter = tag;
                
                // Update button text
                FilterButton.Content = tag switch
                {
                    "All" => "全部",
                    "Uncategorized" => "未分类",
                    "NotFavorite" => "未标星",
                    _ => "全部"
                };
                
                // Reload with quick filter
                await ViewModel.LoadDataAsync(_filterParameter, _quickFilter);
                UpdateEmptyState();
            }
        }

        private async void AddButton_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new AddPasswordDialog();
            dialog.XamlRoot = this.XamlRoot;
            
            var result = await dialog.ShowAsync();
            if (result == ContentDialogResult.Primary && dialog.Result != null)
            {
                var entry = dialog.Result;
                entry.EncryptedPassword = _securityService.Encrypt(entry.EncryptedPassword);
                await ViewModel.AddEntryAsync(entry);

                // If authenticator key was provided, create a linked TOTP entry
                if (!string.IsNullOrEmpty(dialog.AuthenticatorKey))
                {
                    var secureItemsVm = ((App)App.Current).Services.GetRequiredService<SecureItemsViewModel>();
                    
                    var totpData = new TotpData
                    {
                        Secret = dialog.AuthenticatorKey,
                        Issuer = entry.Title,
                        AccountName = entry.Username,
                        Period = 30,
                        Digits = 6,
                        Algorithm = "SHA1",
                        OtpType = "TOTP"
                    };

                    var json = System.Text.Json.JsonSerializer.Serialize(totpData);
                    var encryptedData = _securityService.Encrypt(json);

                    var totpItem = new SecureItem
                    {
                        Title = entry.Title,
                        Notes = $"关联密码: {entry.Username}",
                        ItemData = encryptedData,
                        ItemType = ItemType.Totp
                    };

                    secureItemsVm.Initialize(ItemType.Totp);
                    await secureItemsVm.AddItemAsync(totpItem);
                }

                UpdateEmptyState();
            }
        }

        private void CopyUsername_Click(object sender, RoutedEventArgs e)
        {
            PasswordEntry? entry = null;
            if (sender is Button btn && btn.Tag is PasswordEntry pe) entry = pe;
            else if (sender is MenuFlyoutItem mfi && mfi.Tag is PasswordEntry pe2) entry = pe2;
            
            if (entry != null)
            {
                var dataPackage = new DataPackage();
                dataPackage.SetText(entry.Username);
                Clipboard.SetContent(dataPackage);
            }
        }

        private async void CopyPassword_Click(object sender, RoutedEventArgs e)
        {
            PasswordEntry? entry = null;
            FrameworkElement? target = null;
            if (sender is Button btn && btn.Tag is PasswordEntry pe) { entry = pe; target = btn; }
            else if (sender is MenuFlyoutItem mfi && mfi.Tag is PasswordEntry pe2) { entry = pe2; }
            
            if (entry != null)
            {
                try
                {
                    System.Diagnostics.Debug.WriteLine($"CopyPassword: Encrypted = {entry.EncryptedPassword}");
                    var decrypted = _securityService.Decrypt(entry.EncryptedPassword);
                    System.Diagnostics.Debug.WriteLine($"CopyPassword: Decrypted = {decrypted}");
                    
                    string statusMessage;
                    if (string.IsNullOrEmpty(decrypted))
                    {
                        // Decryption failed
                        statusMessage = "无法解密：此密码来自Android备份，因密钥不同无法查看。请重置密码。";
                    }
                    else if (decrypted.Length > 50 || decrypted.Contains("=="))
                    {
                        // Looks like valid Base64 but shouldn't be
                        statusMessage = "无法解密：数据已加密 (Android备份数据)。请重置密码。";
                        
                        // Prevent copying the raw ciphertext to avoid confusion
                        decrypted = ""; 
                    }
                    else
                    {
                        statusMessage = $"已复制密码";
                    }
                    
                    if (!string.IsNullOrEmpty(decrypted))
                    {
                        var dataPackage = new DataPackage();
                        dataPackage.SetText(decrypted);
                        Clipboard.SetContent(dataPackage);
                    }
                    
                    // Show brief notification
                    var tip = new Microsoft.UI.Xaml.Controls.TeachingTip
                    {
                        Title = "密码",
                        Subtitle = statusMessage,
                        IsLightDismissEnabled = true,
                        PreferredPlacement = Microsoft.UI.Xaml.Controls.TeachingTipPlacementMode.Bottom
                    };
                    if (target != null) tip.Target = target;
                    (this.Content as Microsoft.UI.Xaml.Controls.Grid)?.Children.Add(tip);
                    tip.IsOpen = true;
                    
                    // Auto close after 2 seconds
                    await System.Threading.Tasks.Task.Delay(2000);
                    tip.IsOpen = false;
                    (this.Content as Microsoft.UI.Xaml.Controls.Grid)?.Children.Remove(tip);
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"CopyPassword error: {ex.Message}");
                }
            }
        }

        private async void EditEntry_Click(object sender, RoutedEventArgs e)
        {
            PasswordEntry? entry = null;
            if (sender is Button btn && btn.Tag is PasswordEntry pe) entry = pe;
            else if (sender is MenuFlyoutItem mfi && mfi.Tag is PasswordEntry pe2) entry = pe2;
            
            if (entry != null)
            {
                var dialog = new AddPasswordDialog();
                dialog.XamlRoot = this.XamlRoot;
                dialog.SetEditMode(entry, _securityService);
                
                var result = await dialog.ShowAsync();
                if (result == ContentDialogResult.Primary && dialog.Result != null)
                {
                    var updated = dialog.Result;
                    // If password was changed (not empty), encrypt it
                    if (!string.IsNullOrEmpty(updated.EncryptedPassword))
                    {
                        updated.EncryptedPassword = _securityService.Encrypt(updated.EncryptedPassword);
                    }
                    else
                    {
                        updated.EncryptedPassword = entry.EncryptedPassword; // Keep original
                    }
                    await ViewModel.UpdateEntryAsync(updated);
                }
            }
        }

        private async void DeleteEntry_Click(object sender, RoutedEventArgs e)
        {
            PasswordEntry? entry = null;
            if (sender is Button btn && btn.Tag is PasswordEntry pe) entry = pe;
            else if (sender is MenuFlyoutItem mfi && mfi.Tag is PasswordEntry pe2) entry = pe2;
            
            if (entry != null)
            {
                var confirmDialog = new ContentDialog
                {
                    Title = "确认删除",
                    Content = $"确定要删除 \"{entry.Title}\" 吗？此操作无法撤销。",
                    PrimaryButtonText = "删除",
                    CloseButtonText = "取消",
                    DefaultButton = ContentDialogButton.Close,
                    XamlRoot = this.XamlRoot
                };

                var result = await confirmDialog.ShowAsync();
                if (result == ContentDialogResult.Primary)
                {
                    await ViewModel.DeleteEntryAsync(entry);
                    UpdateEmptyState();
                }
            }
        }

        // Context menu opening - update favorite text dynamically
        private void ContextMenuFlyout_Opening(object sender, object e)
        {
            if (sender is MenuFlyout flyout && flyout.Items.Count > 0)
            {
                // Find the FavoriteMenuItem and update its text based on the item's current state
                foreach (var item in flyout.Items)
                {
                    if (item is MenuFlyoutItem mfi && mfi.Tag is PasswordEntry entry)
                    {
                        // Update favorite menu item text
                        if (mfi.Text.Contains("收藏"))
                        {
                            mfi.Text = entry.IsFavorite ? "取消收藏" : "收藏";
                        }
                    }
                }
            }
        }

        // Toggle favorite
        private async void ToggleFavorite_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem mfi && mfi.Tag is PasswordEntry entry)
            {
                entry.IsFavorite = !entry.IsFavorite;
                await ViewModel.UpdateEntryAsync(entry);
                await ViewModel.LoadDataAsync(_filterParameter);
            }
        }

        // Custom multi-select state
        private bool _isMultiSelectMode = false;
        private readonly HashSet<PasswordEntry> _selectedItems = new();
        private readonly Dictionary<PasswordEntry, Grid> _cardGrids = new();

        // Enter multi-select mode
        private void EnterMultiSelect_Click(object sender, RoutedEventArgs e)
        {
            _isMultiSelectMode = true;
            MultiSelectToolbar.Visibility = Visibility.Visible;
            _selectedItems.Clear();
            _cardGrids.Clear();
            
            // Select the clicked item and find its Grid
            if (sender is MenuFlyoutItem mfi && mfi.Tag is PasswordEntry entry)
            {
                _selectedItems.Add(entry);
                
                // Find the Grid in the ListView for this item
                var container = PasswordListView.ContainerFromItem(entry) as ListViewItem;
                if (container != null)
                {
                    var grid = FindChildGrid(container);
                    if (grid != null)
                    {
                        grid.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["AccentFillColorDefaultBrush"];
                        _cardGrids[entry] = grid;
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

        private void CardGrid_PointerPressed(object sender, Microsoft.UI.Xaml.Input.PointerRoutedEventArgs e)
        {
            if (!_isMultiSelectMode) return;
            
            // Only respond to left click
            var props = e.GetCurrentPoint(null).Properties;
            if (!props.IsLeftButtonPressed) return;
            
            if (sender is Grid grid && grid.DataContext is PasswordEntry entry)
            {
                ToggleItemSelection(entry, grid);
                e.Handled = true;
            }
        }

        private void ToggleItemSelection(PasswordEntry entry, Grid? grid = null)
        {
            if (_selectedItems.Contains(entry))
            {
                _selectedItems.Remove(entry);
                if (grid != null)
                {
                    grid.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardStrokeColorDefaultBrush"];
                    _cardGrids.Remove(entry);
                }
            }
            else
            {
                _selectedItems.Add(entry);
                if (grid != null)
                {
                    grid.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["AccentFillColorDefaultBrush"];
                    _cardGrids[entry] = grid;
                }
            }
            UpdateSelectedCount();
        }

        private void UpdateSelectedCount()
        {
            SelectedCountText.Text = $"已选 {_selectedItems.Count} 项";
        }

        private void PasswordListView_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            // No longer used - we manage selection manually
        }

        // Batch favorite
        private async void BatchFavorite_Click(object sender, RoutedEventArgs e)
        {
            int count = 0;
            foreach (var item in _selectedItems.ToList())
            {
                if (!item.IsFavorite)
                {
                    item.IsFavorite = true;
                    await ViewModel.UpdateEntryAsync(item);
                    count++;
                }
            }
            CancelMultiSelect_Click(sender, e);
            await ViewModel.LoadDataAsync(_filterParameter);
        }

        // Batch add to category
        private async void BatchAddToCategory_Click(object sender, RoutedEventArgs e)
        {
            var dbContext = ((App)App.Current).Services.GetRequiredService<Data.AppDbContext>();
            var categories = dbContext.Categories.OrderBy(c => c.SortOrder).ToList();

            if (categories.Count == 0)
            {
                var tip = new TeachingTip
                {
                    Title = "无分类",
                    Subtitle = "请先在侧边栏创建分类",
                    IsOpen = true
                };
                return;
            }

            var comboBox = new ComboBox { ItemsSource = categories.Select(c => c.Name).ToList(), SelectedIndex = 0 };
            var dialog = new ContentDialog
            {
                Title = "选择分类",
                Content = comboBox,
                PrimaryButtonText = "添加",
                CloseButtonText = "取消",
                XamlRoot = this.XamlRoot
            };

            if (await dialog.ShowAsync() == ContentDialogResult.Primary)
            {
                var selectedCategory = categories[comboBox.SelectedIndex];
                int count = 0;
                foreach (var item in _selectedItems.ToList())
                {
                    if (item.CategoryId != selectedCategory.Id)
                    {
                        item.CategoryId = selectedCategory.Id;
                        await ViewModel.UpdateEntryAsync(item);
                        count++;
                    }
                }
                CancelMultiSelect_Click(sender, e);
                await ViewModel.LoadDataAsync(_filterParameter);
            }
        }

        // Batch delete
        private async void BatchDelete_Click(object sender, RoutedEventArgs e)
        {
            var selectedItems = _selectedItems.ToList();
            if (selectedItems.Count == 0) return;

            var dialog = new ContentDialog
            {
                Title = "批量删除",
                Content = $"确定删除选中的 {selectedItems.Count} 项密码吗？此操作无法撤销。",
                PrimaryButtonText = "删除",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Close,
                XamlRoot = this.XamlRoot
            };

            if (await dialog.ShowAsync() == ContentDialogResult.Primary)
            {
                foreach (var item in selectedItems)
                {
                    await ViewModel.DeleteEntryAsync(item);
                }
                CancelMultiSelect_Click(sender, e);
                UpdateEmptyState();
            }
        }
    }
}
