using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Monica.Windows.Models;
using Monica.Windows.Services;
using Monica.Windows.ViewModels;
using System;
using System.Text.Json;
using System.Collections.Generic;
using Microsoft.UI.Xaml.Media.Imaging;
using System.IO;
using System.Threading.Tasks;
using System.Linq;

namespace Monica.Windows.Views
{
    public sealed partial class CardsPage : Page
    {
        public SecureItemsViewModel ViewModel { get; }
        private readonly ISecurityService _securityService;
        private readonly IImageStorageService _imageStorageService;
        private IServiceScope _scope;

        public CardsPage()
        {
            this.InitializeComponent();
            
            // Create a scope for this page instance
            _scope = ((App)App.Current).Services.CreateScope();
            ViewModel = _scope.ServiceProvider.GetRequiredService<SecureItemsViewModel>();
            _securityService = _scope.ServiceProvider.GetRequiredService<ISecurityService>();
            _imageStorageService = _scope.ServiceProvider.GetRequiredService<IImageStorageService>();
            
            // Load both Document and BankCard types
            ViewModel.Initialize(ItemType.BankCard, ItemType.Document);
            this.Loaded += CardsPage_Loaded;
            this.Unloaded += CardsPage_Unloaded;
        }

        private void CardsPage_Unloaded(object sender, RoutedEventArgs e)
        {
            _scope?.Dispose();
        }

        private async void CardsPage_Loaded(object sender, RoutedEventArgs e)
        {
            LoadingRing.IsActive = true;
            await ViewModel.LoadDataAsync();
            LoadingRing.IsActive = false;
        }

        private async void AddButton_Click(object sender, RoutedEventArgs e)
        {
            // First, show a dialog to select card type
            var typeDialog = new ContentDialog
            {
                Title = "选择类型",
                CloseButtonText = "取消",
                XamlRoot = this.XamlRoot
            };

            var typeStack = new StackPanel { Spacing = 8 };
            
            var documentBtn = new Button 
            { 
                Content = "证件 (身份证、护照、驾照等)",
                HorizontalAlignment = HorizontalAlignment.Stretch,
                Padding = new Thickness(16, 12, 16, 12)
            };
            
            var bankCardBtn = new Button 
            { 
                Content = "银行卡",
                HorizontalAlignment = HorizontalAlignment.Stretch,
                Padding = new Thickness(16, 12, 16, 12)
            };

            string selectedType = "";
            documentBtn.Click += (s, args) => { selectedType = "document"; typeDialog.Hide(); };
            bankCardBtn.Click += (s, args) => { selectedType = "bankcard"; typeDialog.Hide(); };

            typeStack.Children.Add(documentBtn);
            typeStack.Children.Add(bankCardBtn);
            typeDialog.Content = typeStack;

            await typeDialog.ShowAsync();

            if (selectedType == "document")
            {
                await ShowDocumentDialog();
            }
            else if (selectedType == "bankcard")
            {
                await ShowBankCardDialog();
            }
        }
        private async void EditButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is FrameworkElement btn && btn.Tag is SecureItem item)
            {
                if (item.ItemType == ItemType.Document)
                {
                    await ShowDocumentDialog(item);
                }
                else if (item.ItemType == ItemType.BankCard)
                {
                    await ShowBankCardDialog(item);
                }
            }
        }

        private async Task ShowDocumentDialog(SecureItem? existingItem = null)
        {
            var isEdit = existingItem != null;
            var dialog = new ContentDialog
            {
                Title = isEdit ? "编辑证件" : "添加证件",
                PrimaryButtonText = "保存",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = this.XamlRoot
            };

            var stack = new StackPanel { Spacing = 12, MinWidth = 400 };
            
            // Document Type Selector
            var typeCombo = new ComboBox 
            { 
                Header = "证件类型",
                HorizontalAlignment = HorizontalAlignment.Stretch
            };
            typeCombo.Items.Add(new ComboBoxItem { Content = "身份证", Tag = "ID_CARD" });
            typeCombo.Items.Add(new ComboBoxItem { Content = "护照", Tag = "PASSPORT" });
            typeCombo.Items.Add(new ComboBoxItem { Content = "驾驶证", Tag = "DRIVER_LICENSE" });
            typeCombo.Items.Add(new ComboBoxItem { Content = "社保卡", Tag = "SOCIAL_SECURITY" });
            typeCombo.Items.Add(new ComboBoxItem { Content = "其他", Tag = "OTHER" });
            typeCombo.SelectedIndex = 0;
            
            var titleBox = new TextBox { Header = "名称", PlaceholderText = "例如: 我的身份证" };
            var numberBox = new TextBox { Header = "证件号码 *", PlaceholderText = "110101199001011234" };
            var nameBox = new TextBox { Header = "持有人姓名", PlaceholderText = "张三" };
            var issuedDateBox = new TextBox { Header = "签发日期", PlaceholderText = "2020-01-01" };
            var expiryDateBox = new TextBox { Header = "有效期至", PlaceholderText = "2030-01-01 或 长期" };
            var issuedByBox = new TextBox { Header = "签发机关", PlaceholderText = "XX公安局" };
            var notesBox = new TextBox { Header = "备注", PlaceholderText = "可选备注...", AcceptsReturn = true, Height = 60 };
            
            // Image Upload Section - Front and Back
            var imageHeader = new TextBlock { Text = "证件照片", Style = (Style)Application.Current.Resources["BodyStrongTextBlockStyle"] };
            var imageGrid = new Grid { Margin = new Thickness(0, 8, 0, 0) };
            imageGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            imageGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            
            // Front image panel
            var frontPanel = new StackPanel { Spacing = 8, Margin = new Thickness(0, 0, 8, 0) };
            var frontLabel = new TextBlock { Text = "正面", FontSize = 12, HorizontalAlignment = HorizontalAlignment.Center,
                Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"] };
            var frontImageBorder = new Border 
            { 
                CornerRadius = new CornerRadius(8), 
                BorderThickness = new Thickness(1),
                BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardStrokeColorDefaultBrush"],
                Background = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardBackgroundFillColorSecondaryBrush"],
                Height = 100,
                Child = new FontIcon { Glyph = "\uE8B9", FontSize = 24, Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"] }
            };
            var frontImage = new Image { Stretch = Microsoft.UI.Xaml.Media.Stretch.Uniform };
            frontPanel.Children.Add(frontLabel);
            frontPanel.Children.Add(frontImageBorder);
            Grid.SetColumn(frontPanel, 0);
            imageGrid.Children.Add(frontPanel);
            
            // Back image panel
            var backPanel = new StackPanel { Spacing = 8, Margin = new Thickness(8, 0, 0, 0) };
            var backLabel = new TextBlock { Text = "背面", FontSize = 12, HorizontalAlignment = HorizontalAlignment.Center,
                Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"] };
            var backImageBorder = new Border 
            { 
                CornerRadius = new CornerRadius(8), 
                BorderThickness = new Thickness(1),
                BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardStrokeColorDefaultBrush"],
                Background = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardBackgroundFillColorSecondaryBrush"],
                Height = 100,
                Child = new FontIcon { Glyph = "\uE8B9", FontSize = 24, Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"] }
            };
            var backImage = new Image { Stretch = Microsoft.UI.Xaml.Media.Stretch.Uniform };
            backPanel.Children.Add(backLabel);
            backPanel.Children.Add(backImageBorder);
            Grid.SetColumn(backPanel, 1);
            imageGrid.Children.Add(backPanel);
            
            global::Windows.Storage.StorageFile? frontFile = null;
            global::Windows.Storage.StorageFile? backFile = null;
            DocumentData? existingData = null;

            // Pre-fill if editing
            if (isEdit) 
            {
                 try {
                    var json = _securityService.Decrypt(existingItem!.ItemData);
                    existingData = JsonSerializer.Deserialize<DocumentData>(json);
                 } catch {}
            }
            
            if (existingData != null)
            {
                 foreach(ComboBoxItem item in typeCombo.Items) {
                    if (item.Tag?.ToString() == existingData.DocumentTypeString) {
                        typeCombo.SelectedItem = item;
                        break;
                    }
                 }
                 titleBox.Text = existingItem.Title;
                 numberBox.Text = existingData.DocumentNumber;
                 nameBox.Text = existingData.FullName ?? "";
                 issuedDateBox.Text = existingData.IssuedDate ?? "";
                 expiryDateBox.Text = existingData.ExpiryDate ?? "";
                 issuedByBox.Text = existingData.IssuedBy ?? "";
                 notesBox.Text = existingItem.Notes ?? "";

                 // Load existing images if available
                 if (existingData.ImagePaths != null) 
                 {
                    if (existingData.ImagePaths.Count > 0 && !string.IsNullOrEmpty(existingData.ImagePaths[0])) 
                    {
                       try {
                           var bitmap = await _imageStorageService.LoadImageAsync(existingData.ImagePaths[0]);
                           if (bitmap != null) {
                               frontImage.Source = bitmap;
                               frontImageBorder.Child = frontImage;
                           }
                       } catch {}
                    }
                    if (existingData.ImagePaths.Count > 1 && !string.IsNullOrEmpty(existingData.ImagePaths[1])) 
                    {
                       try {
                           var bitmap = await _imageStorageService.LoadImageAsync(existingData.ImagePaths[1]);
                           if (bitmap != null) {
                               backImage.Source = bitmap;
                               backImageBorder.Child = backImage;
                           }
                       } catch {}
                    }
                 }
            }
            
            // Update back panel visibility based on document type
            typeCombo.SelectionChanged += (s, e) =>
            {
                var selected = (typeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
                // Passport only needs 1 image (front only)
                backPanel.Visibility = (selected == "PASSPORT") ? Visibility.Collapsed : Visibility.Visible;
            };
            // Trigger selection changed manual update if edit
            if (existingData != null) {
                var selected = (typeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
                 backPanel.Visibility = (selected == "PASSPORT") ? Visibility.Collapsed : Visibility.Visible;
            }
            
            frontImageBorder.Tapped += async (s, e) =>
            {
                var picker = new global::Windows.Storage.Pickers.FileOpenPicker();
                var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
                WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
                picker.ViewMode = global::Windows.Storage.Pickers.PickerViewMode.Thumbnail;
                picker.SuggestedStartLocation = global::Windows.Storage.Pickers.PickerLocationId.PicturesLibrary;
                picker.FileTypeFilter.Add(".jpg");
                picker.FileTypeFilter.Add(".jpeg");
                picker.FileTypeFilter.Add(".png");

                var file = await picker.PickSingleFileAsync();
                if (file != null)
                {
                    frontFile = file;
                    using var stream = await file.OpenReadAsync();
                    var bitmap = new BitmapImage();
                    await bitmap.SetSourceAsync(stream);
                    frontImage.Source = bitmap;
                    frontImageBorder.Child = frontImage;
                }
            };
            
            backImageBorder.Tapped += async (s, e) =>
            {
                var picker = new global::Windows.Storage.Pickers.FileOpenPicker();
                var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
                WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
                picker.ViewMode = global::Windows.Storage.Pickers.PickerViewMode.Thumbnail;
                picker.SuggestedStartLocation = global::Windows.Storage.Pickers.PickerLocationId.PicturesLibrary;
                picker.FileTypeFilter.Add(".jpg");
                picker.FileTypeFilter.Add(".jpeg");
                picker.FileTypeFilter.Add(".png");

                var file = await picker.PickSingleFileAsync();
                if (file != null)
                {
                    backFile = file;
                    using var stream = await file.OpenReadAsync();
                    var bitmap = new BitmapImage();
                    await bitmap.SetSourceAsync(stream);
                    backImage.Source = bitmap;
                    backImageBorder.Child = backImage;
                }
            };
            
            stack.Children.Add(typeCombo);
            stack.Children.Add(titleBox);
            stack.Children.Add(numberBox);
            stack.Children.Add(nameBox);
            stack.Children.Add(issuedDateBox);
            stack.Children.Add(expiryDateBox);
            stack.Children.Add(issuedByBox);
            stack.Children.Add(notesBox);
            stack.Children.Add(imageHeader);
            stack.Children.Add(imageGrid);
            
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
                if (string.IsNullOrWhiteSpace(numberBox.Text)) return;

                var selectedType = (typeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "ID_CARD";
                
                // Save images (front and optionally back)
                var savedImagePaths = new List<string>();
                
                // Front Image Logic
                if (frontFile != null)
                {
                    var path = await _imageStorageService.SaveImageAsync(frontFile);
                    savedImagePaths.Add(path);
                }
                else if (isEdit && existingData?.ImagePaths?.Count > 0)
                {
                    savedImagePaths.Add(existingData.ImagePaths[0]);
                }

                // Back Image Logic
                if (selectedType != "PASSPORT")
                {
                    if (backFile != null)
                    {
                        var path = await _imageStorageService.SaveImageAsync(backFile);
                        savedImagePaths.Add(path);
                    }
                    else if (isEdit && existingData?.ImagePaths?.Count > 1) 
                    {
                        savedImagePaths.Add(existingData.ImagePaths[1]);
                    }
                }

                var docData = new DocumentData
                {
                    DocumentNumber = numberBox.Text.Trim(),
                    FullName = nameBox.Text.Trim(),
                    IssuedDate = issuedDateBox.Text.Trim(),
                    ExpiryDate = expiryDateBox.Text.Trim(),
                    IssuedBy = issuedByBox.Text.Trim(),
                    DocumentTypeString = selectedType,
                    ImagePaths = savedImagePaths
                };

                var json = JsonSerializer.Serialize(docData);
                var encryptedData = _securityService.Encrypt(json);

                var title = titleBox.Text.Trim();
                if (string.IsNullOrEmpty(title))
                {
                    title = selectedType switch
                    {
                        "ID_CARD" => "身份证",
                        "PASSPORT" => "护照",
                        "DRIVER_LICENSE" => "驾驶证",
                        "SOCIAL_SECURITY" => "社保卡",
                        _ => "证件"
                    };
                }

                if (isEdit)
                {
                    existingItem!.Title = title;
                    existingItem.Notes = notesBox.Text.Trim();
                    existingItem.ItemData = encryptedData;
                    existingItem.UpdatedAt = DateTime.Now;
                    await ViewModel.UpdateItemAsync(existingItem);
                }
                else
                {
                    var item = new SecureItem
                    {
                        Title = title,
                        Notes = notesBox.Text.Trim(),
                        ItemData = encryptedData,
                        ItemType = ItemType.Document
                    };
                    await ViewModel.AddItemAsync(item);
                }
            }
        }

        private async Task ShowBankCardDialog(SecureItem? existingItem = null)
        {
            var isEdit = existingItem != null;
            var dialog = new ContentDialog
            {
                Title = isEdit ? "编辑银行卡" : "添加银行卡",
                PrimaryButtonText = "保存",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = this.XamlRoot
            };

            var stack = new StackPanel { Spacing = 12, MinWidth = 400 };
            
            // Card Type Selector
            var cardTypeCombo = new ComboBox 
            { 
                Header = "卡类型",
                HorizontalAlignment = HorizontalAlignment.Stretch
            };
            cardTypeCombo.Items.Add(new ComboBoxItem { Content = "借记卡", Tag = "DEBIT" });
            cardTypeCombo.Items.Add(new ComboBoxItem { Content = "信用卡", Tag = "CREDIT" });
            cardTypeCombo.Items.Add(new ComboBoxItem { Content = "预付卡", Tag = "PREPAID" });
            cardTypeCombo.SelectedIndex = 0;
            
            var titleBox = new TextBox { Header = "名称", PlaceholderText = "例如: 工行储蓄卡" };
            var bankNameBox = new TextBox { Header = "银行名称", PlaceholderText = "例如: 中国工商银行" };
            var cardNumberBox = new TextBox { Header = "卡号 *", PlaceholderText = "6222 0000 0000 0000" };
            var holderNameBox = new TextBox { Header = "持卡人姓名", PlaceholderText = "ZHANG SAN" };
            
            // Expiry Month/Year
            var expiryPanel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
            var expiryMonthBox = new NumberBox 
            { 
                Header = "有效期月", 
                Value = 1, 
                Minimum = 1, 
                Maximum = 12,
                SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact,
                Width = 100
            };
            var expiryYearBox = new NumberBox 
            { 
                Header = "有效期年", 
                Value = DateTime.Now.Year + 3, 
                Minimum = DateTime.Now.Year, 
                Maximum = DateTime.Now.Year + 20,
                SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact,
                Width = 120
            };
            expiryPanel.Children.Add(expiryMonthBox);
            expiryPanel.Children.Add(expiryYearBox);
            
            var cvvBox = new PasswordBox { Header = "CVV/安全码", PlaceholderText = "3位数字" };
            var notesBox = new TextBox { Header = "备注", PlaceholderText = "可选备注...", AcceptsReturn = true, Height = 60 };
            
            // Image Upload Section - Front and Back
            var imageHeader = new TextBlock { Text = "银行卡照片", Style = (Style)Application.Current.Resources["BodyStrongTextBlockStyle"] };
            var imageGrid = new Grid { Margin = new Thickness(0, 8, 0, 0) };
            imageGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            imageGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            
            // Front image panel
            var bcFrontPanel = new StackPanel { Spacing = 8, Margin = new Thickness(0, 0, 8, 0) };
            var bcFrontLabel = new TextBlock { Text = "正面", FontSize = 12, HorizontalAlignment = HorizontalAlignment.Center,
                Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"] };
            var bcFrontImageBorder = new Border 
            { 
                CornerRadius = new CornerRadius(8), 
                BorderThickness = new Thickness(1),
                BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardStrokeColorDefaultBrush"],
                Background = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardBackgroundFillColorSecondaryBrush"],
                Height = 100,
                Child = new FontIcon { Glyph = "\uE8B9", FontSize = 24, Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"] }
            };
            var bcFrontImage = new Image { Stretch = Microsoft.UI.Xaml.Media.Stretch.Uniform };
            bcFrontPanel.Children.Add(bcFrontLabel);
            bcFrontPanel.Children.Add(bcFrontImageBorder);
            Grid.SetColumn(bcFrontPanel, 0);
            imageGrid.Children.Add(bcFrontPanel);
            
            // Back image panel
            var bcBackPanel = new StackPanel { Spacing = 8, Margin = new Thickness(8, 0, 0, 0) };
            var bcBackLabel = new TextBlock { Text = "背面", FontSize = 12, HorizontalAlignment = HorizontalAlignment.Center,
                Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"] };
            var bcBackImageBorder = new Border 
            { 
                CornerRadius = new CornerRadius(8), 
                BorderThickness = new Thickness(1),
                BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardStrokeColorDefaultBrush"],
                Background = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardBackgroundFillColorSecondaryBrush"],
                Height = 100,
                Child = new FontIcon { Glyph = "\uE8B9", FontSize = 24, Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"] }
            };
            var bcBackImage = new Image { Stretch = Microsoft.UI.Xaml.Media.Stretch.Uniform };
            bcBackPanel.Children.Add(bcBackLabel);
            bcBackPanel.Children.Add(bcBackImageBorder);
            Grid.SetColumn(bcBackPanel, 1);
            imageGrid.Children.Add(bcBackPanel);
            
            global::Windows.Storage.StorageFile? bcFrontFile = null;
            global::Windows.Storage.StorageFile? bcBackFile = null;
            BankCardData? existingData = null;

            // Pre-fill if editing
            if (isEdit) 
            {
                 try {
                    var json = _securityService.Decrypt(existingItem!.ItemData);
                    existingData = JsonSerializer.Deserialize<BankCardData>(json);
                 } catch {}
            }

            if (existingData != null)
            {
                 foreach(ComboBoxItem item in cardTypeCombo.Items) {
                    if (item.Tag?.ToString() == existingData.CardTypeString) {
                        cardTypeCombo.SelectedItem = item;
                        break;
                    }
                 }
                 titleBox.Text = existingItem.Title;
                 bankNameBox.Text = existingData.BankName ?? "";
                 cardNumberBox.Text = existingData.CardNumber ?? "";
                 holderNameBox.Text = existingData.CardholderName ?? "";
                 
                 if (int.TryParse(existingData.ExpiryMonth, out int m)) expiryMonthBox.Value = m;
                 if (int.TryParse(existingData.ExpiryYear, out int y)) expiryYearBox.Value = y;
                 
                 cvvBox.Password = existingData.Cvv ?? "";
                 notesBox.Text = existingItem.Notes ?? "";

                 // Load existing images if available
                 if (existingData.ImagePaths != null) 
                 {
                    if (existingData.ImagePaths.Count > 0 && !string.IsNullOrEmpty(existingData.ImagePaths[0])) 
                    {
                       try {
                           var bitmap = await _imageStorageService.LoadImageAsync(existingData.ImagePaths[0]);
                           if (bitmap != null) {
                               bcFrontImage.Source = bitmap;
                               bcFrontImageBorder.Child = bcFrontImage;
                           }
                       } catch {}
                    }
                    if (existingData.ImagePaths.Count > 1 && !string.IsNullOrEmpty(existingData.ImagePaths[1])) 
                    {
                       try {
                           var bitmap = await _imageStorageService.LoadImageAsync(existingData.ImagePaths[1]);
                           if (bitmap != null) {
                               bcBackImage.Source = bitmap;
                               bcBackImageBorder.Child = bcBackImage;
                           }
                       } catch {}
                    }
                 }
            }
            
            bcFrontImageBorder.Tapped += async (s, ev) =>
            {
                var picker = new global::Windows.Storage.Pickers.FileOpenPicker();
                var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
                WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
                picker.ViewMode = global::Windows.Storage.Pickers.PickerViewMode.Thumbnail;
                picker.SuggestedStartLocation = global::Windows.Storage.Pickers.PickerLocationId.PicturesLibrary;
                picker.FileTypeFilter.Add(".jpg");
                picker.FileTypeFilter.Add(".jpeg");
                picker.FileTypeFilter.Add(".png");

                var file = await picker.PickSingleFileAsync();
                if (file != null)
                {
                    bcFrontFile = file;
                    using var stream = await file.OpenReadAsync();
                    var bitmap = new BitmapImage();
                    await bitmap.SetSourceAsync(stream);
                    bcFrontImage.Source = bitmap;
                    bcFrontImageBorder.Child = bcFrontImage;
                }
            };
            
            bcBackImageBorder.Tapped += async (s, ev) =>
            {
                var picker = new global::Windows.Storage.Pickers.FileOpenPicker();
                var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
                WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
                picker.ViewMode = global::Windows.Storage.Pickers.PickerViewMode.Thumbnail;
                picker.SuggestedStartLocation = global::Windows.Storage.Pickers.PickerLocationId.PicturesLibrary;
                picker.FileTypeFilter.Add(".jpg");
                picker.FileTypeFilter.Add(".jpeg");
                picker.FileTypeFilter.Add(".png");

                var file = await picker.PickSingleFileAsync();
                if (file != null)
                {
                    bcBackFile = file;
                    using var stream = await file.OpenReadAsync();
                    var bitmap = new BitmapImage();
                    await bitmap.SetSourceAsync(stream);
                    bcBackImage.Source = bitmap;
                    bcBackImageBorder.Child = bcBackImage;
                }
            };
            
            stack.Children.Add(cardTypeCombo);
            stack.Children.Add(titleBox);
            stack.Children.Add(bankNameBox);
            stack.Children.Add(cardNumberBox);
            stack.Children.Add(holderNameBox);
            stack.Children.Add(expiryPanel);
            stack.Children.Add(cvvBox);
            stack.Children.Add(notesBox);
            stack.Children.Add(imageHeader);
            stack.Children.Add(imageGrid);
            
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
                if (string.IsNullOrWhiteSpace(cardNumberBox.Text)) return;

                var selectedType = (cardTypeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "DEBIT";
                
                // Save images
                var savedImagePaths = new List<string>();
                if (bcFrontFile != null)
                {
                    var path = await _imageStorageService.SaveImageAsync(bcFrontFile);
                    savedImagePaths.Add(path);
                }
                else if (isEdit && existingData?.ImagePaths?.Count > 0)
                {
                    savedImagePaths.Add(existingData.ImagePaths[0]);
                }

                if (bcBackFile != null)
                {
                    var path = await _imageStorageService.SaveImageAsync(bcBackFile);
                    savedImagePaths.Add(path);
                }
                else if (isEdit && existingData?.ImagePaths?.Count > 1) 
                {
                    savedImagePaths.Add(existingData.ImagePaths[1]);
                }
                
                var cardData = new BankCardData
                {
                    CardNumber = cardNumberBox.Text.Trim().Replace(" ", ""),
                    CardholderName = holderNameBox.Text.Trim(),
                    ExpiryMonth = ((int)expiryMonthBox.Value).ToString("D2"),
                    ExpiryYear = ((int)expiryYearBox.Value).ToString(),
                    Cvv = cvvBox.Password,
                    BankName = bankNameBox.Text.Trim(),
                    CardTypeString = selectedType,
                    ImagePaths = savedImagePaths
                };

                var json = JsonSerializer.Serialize(cardData);
                var encryptedData = _securityService.Encrypt(json);

                var title = titleBox.Text.Trim();
                if (string.IsNullOrEmpty(title))
                {
                    title = bankNameBox.Text.Trim();
                    if (string.IsNullOrEmpty(title)) title = "银行卡";
                }

                if (isEdit)
                {
                    existingItem!.Title = title;
                    existingItem.Notes = notesBox.Text.Trim();
                    existingItem.ItemData = encryptedData;
                    existingItem.UpdatedAt = DateTime.Now;
                    await ViewModel.UpdateItemAsync(existingItem);
                }
                else
                {
                    var item = new SecureItem
                    {
                        Title = title,
                        Notes = notesBox.Text.Trim(),
                        ItemData = encryptedData,
                        ItemType = ItemType.BankCard
                    };
                    await ViewModel.AddItemAsync(item);
                }
            }
        }

        private async void DeleteButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is FrameworkElement btn && btn.Tag is SecureItem item)
            {
                var dialog = new ContentDialog
                {
                    Title = "删除确认",
                    Content = "确定要删除吗？",
                    PrimaryButtonText = "删除",
                    CloseButtonText = "取消",
                    XamlRoot = this.XamlRoot
                };
                if (await dialog.ShowAsync() == ContentDialogResult.Primary)
                {
                    await ViewModel.DeleteItemAsync(item);
                }
            }
        }

        // Custom multi-select state
        private bool _isMultiSelectMode = false;
        private readonly HashSet<SecureItem> _selectedItems = new();
        private readonly Dictionary<SecureItem, Grid> _cardGrids = new();

        private void UpdateSelectedCount()
        {
            SelectedCountText.Text = $"已选 {_selectedItems.Count} 项";
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

        private void CardsListView_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (CardsListView.SelectionMode == ListViewSelectionMode.Multiple)
            {
                SelectedCountText.Text = $"已选 {CardsListView.SelectedItems.Count} 项";
            }
        }

        private void MultiSelect_Click(object sender, RoutedEventArgs e)
        {
            _isMultiSelectMode = true;
            CardsListView.SelectionMode = ListViewSelectionMode.None;
            CardsListView.IsItemClickEnabled = true;
            MultiSelectToolbar.Visibility = Visibility.Visible;
            _selectedItems.Clear();
            _cardGrids.Clear();
            
            if (sender is MenuFlyoutItem item && item.Tag is SecureItem secureItem)
            {
                _selectedItems.Add(secureItem);
                
                var container = CardsListView.ContainerFromItem(secureItem) as ListViewItem;
                if (container != null)
                {
                    var grid = FindChildGrid(container);
                    if (grid != null)
                    {
                        grid.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["AccentFillColorDefaultBrush"];
                        _cardGrids[secureItem] = grid;
                    }
                }
                UpdateSelectedCount();
            }
        }

        private void CancelMultiSelect_Click(object sender, RoutedEventArgs e)
        {
            _isMultiSelectMode = false;
            MultiSelectToolbar.Visibility = Visibility.Collapsed;
            
            // Reset visuals
            foreach(var kvp in _cardGrids)
            {
                kvp.Value.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardStrokeColorDefaultBrush"];
            }
            _selectedItems.Clear();
            _cardGrids.Clear();
        }

        private void CardGrid_PointerPressed(object sender, Microsoft.UI.Xaml.Input.PointerRoutedEventArgs e)
        {
            if (!_isMultiSelectMode) return;
            
            var props = e.GetCurrentPoint(null).Properties;
            if (!props.IsLeftButtonPressed) return;

            if (sender is Grid grid && grid.DataContext is SecureItem item)
            {
                if (_selectedItems.Contains(item))
                {
                    _selectedItems.Remove(item);
                    grid.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["CardStrokeColorDefaultBrush"];
                    _cardGrids.Remove(item);
                }
                else
                {
                    _selectedItems.Add(item);
                    grid.BorderBrush = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["AccentFillColorDefaultBrush"];
                    _cardGrids[item] = grid;
                }
                UpdateSelectedCount();
                e.Handled = true; // Prevent ItemClick
            }
        }

        private async void BatchDelete_Click(object sender, RoutedEventArgs e)
        {
            var items = _selectedItems.ToList(); // Use manual selection
            if (items.Count == 0) return;

            var dialog = new ContentDialog
            {
                Title = "批量删除",
                Content = $"确定要删除选中的 {items.Count} 个项目吗？",
                PrimaryButtonText = "删除",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Close,
                XamlRoot = this.XamlRoot
            };

            if (await dialog.ShowAsync() == ContentDialogResult.Primary)
            {
                foreach (var item in items)
                {
                    await ViewModel.DeleteItemAsync(item);
                }
                CancelMultiSelect_Click(null, null);
            }
        }
        


        private void CardsListView_ItemClick(object sender, ItemClickEventArgs e)
        {
            if (_isMultiSelectMode) return; // Ignore click in multi-select mode
            
            if (e.ClickedItem is SecureItem item)
            {
                // Navigate to CardDetailPage instead of showing a dialog
                Frame.Navigate(typeof(CardDetailPage), item);
            }
        }
    }
}

