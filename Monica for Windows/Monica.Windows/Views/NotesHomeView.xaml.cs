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

namespace Monica.Windows.Views
{
    public sealed partial class NotesHomeView : Page
    {
        public SecureItemsViewModel ViewModel { get; }
        private readonly ISecurityService _securityService;
        private IServiceScope _scope;
        
        // Event to notify parent (SecureNotesPage) to open a note tab
        public event Action<SecureItem>? OpenNoteRequested;
        public event Action? CreateNoteRequested;

        // Custom multi-select state
        private bool _isMultiSelectMode = false;
        private readonly HashSet<SecureItem> _selectedItems = new();
        private readonly Dictionary<SecureItem, Grid> _cardGrids = new();

        public NotesHomeView()
        {
            this.InitializeComponent();
            
            _scope = ((App)App.Current).Services.CreateScope();
            ViewModel = _scope.ServiceProvider.GetRequiredService<SecureItemsViewModel>();
            _securityService = _scope.ServiceProvider.GetRequiredService<ISecurityService>();
            
            ViewModel.Initialize(ItemType.Note);
            this.Loaded += NotesHomeView_Loaded;
        }

        private async void NotesHomeView_Loaded(object sender, RoutedEventArgs e)
        {
            LoadingRing.IsActive = true;
            await ViewModel.LoadDataAsync();
            LoadingRing.IsActive = false;
        }

        public async void RefreshData()
        {
            try
            {
                await ViewModel.LoadDataAsync();
            }
            catch { }
        }

        private void AddButton_Click(object sender, RoutedEventArgs e)
        {
            CreateNoteRequested?.Invoke();
        }

        private void NoteCard_PointerPressed(object sender, PointerRoutedEventArgs e)
        {
            // Only respond to left-click
            var props = e.GetCurrentPoint(null).Properties;
            if (!props.IsLeftButtonPressed) return;
            
            if (sender is Grid grid && grid.DataContext is SecureItem item)
            {
                if (_isMultiSelectMode)
                {
                    ToggleItemSelection(item, grid);
                    e.Handled = true;
                }
                else
                {
                    OpenNoteRequested?.Invoke(item);
                }
            }
        }

        private void NoteCard_RightTapped(object sender, RightTappedRoutedEventArgs e)
        {
            // Context menu will show automatically via Grid.ContextFlyout
        }

        private void OpenMenuItem_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem item && item.Tag is SecureItem note)
            {
                OpenNoteRequested?.Invoke(note);
            }
        }

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
                var container = NotesListView.ContainerFromItem(item) as ListViewItem;
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

        private async void DeleteMenuItem_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuFlyoutItem item && item.Tag is SecureItem note)
            {
                var dialog = new ContentDialog
                {
                    Title = "删除确认",
                    Content = "确定要删除此笔记吗？此操作不可撤销。",
                    PrimaryButtonText = "删除",
                    CloseButtonText = "取消",
                    XamlRoot = this.XamlRoot
                };
                if (await dialog.ShowAsync() == ContentDialogResult.Primary)
                {
                    await ViewModel.DeleteItemAsync(note);
                }
            }
        }

        private async void BatchDelete_Click(object sender, RoutedEventArgs e)
        {
            var selectedItems = _selectedItems.ToList();
            if (selectedItems.Count == 0) return;

            var dialog = new ContentDialog
            {
                Title = "批量删除",
                Content = $"确定删除选中的 {selectedItems.Count} 项笔记吗？此操作无法撤销。",
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
    }
}
