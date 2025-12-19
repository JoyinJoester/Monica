using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using Monica.Windows.Models;
using Monica.Windows.Services;
using System;
using System.Collections.Generic;
using System.Text.Json;

namespace Monica.Windows.Views
{
    public sealed partial class CardDetailPage : Page
    {
        private readonly ISecurityService _securityService;
        private readonly IImageStorageService _imageStorageService;
        private SecureItem? _item;
        
        // Store original values for toggle
        private readonly Dictionary<string, (TextBlock textBlock, string original, string masked)> _maskedFields = new();

        public CardDetailPage()
        {
            this.InitializeComponent();
            
            var services = ((App)App.Current).Services;
            _securityService = services.GetRequiredService<ISecurityService>();
            _imageStorageService = services.GetRequiredService<IImageStorageService>();
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            
            if (e.Parameter is SecureItem item)
            {
                _item = item;
                LoadItemData();
            }
        }

        private async void LoadItemData()
        {
            if (_item == null) return;

            try
            {
                var json = _securityService.Decrypt(_item.ItemData);
                PageTitle.Text = _item.Title;

                if (_item.ItemType == ItemType.Document)
                {
                    var data = JsonSerializer.Deserialize<DocumentData>(json);
                    if (data != null)
                    {
                        // Card Visual
                        CardTypeText.Text = GetDocumentTypeName(data.DocumentTypeString);
                        CardMainNumber.Text = MaskMiddle(data.DocumentNumber);
                        CardHolderName.Text = data.FullName ?? "";
                        CardIssuedDate.Text = data.IssuedDate ?? "-";
                        CardExpiryDate.Text = data.ExpiryDate ?? "-";

                        if (string.IsNullOrEmpty(data.FullName))
                            CardHolderName.Visibility = Visibility.Collapsed;

                        // Info Fields
                        AddInfoField("证件号码", data.DocumentNumber, isSensitive: true);
                        AddInfoField("姓名", data.FullName);
                        AddInfoField("签发日期", data.IssuedDate);
                        AddInfoField("有效期至", data.ExpiryDate);
                        AddInfoField("签发机关", data.IssuedBy);

                        // Photos
                        bool hasPhotos = false;
                        if (data.ImagePaths != null && data.ImagePaths.Count > 0)
                        {
                            // Front image
                            if (!string.IsNullOrEmpty(data.ImagePaths[0]))
                            {
                                var frontBitmap = await _imageStorageService.LoadImageAsync(data.ImagePaths[0]);
                                if (frontBitmap != null)
                                {
                                    FrontImage.Source = frontBitmap;
                                    FrontPhotoPanel.Visibility = Visibility.Visible;
                                    hasPhotos = true;
                                }
                            }
                            
                            // Back image
                            if (data.ImagePaths.Count > 1 && !string.IsNullOrEmpty(data.ImagePaths[1]))
                            {
                                var backBitmap = await _imageStorageService.LoadImageAsync(data.ImagePaths[1]);
                                if (backBitmap != null)
                                {
                                    BackImage.Source = backBitmap;
                                    BackPhotoPanel.Visibility = Visibility.Visible;
                                    hasPhotos = true;
                                }
                            }
                        }
                        NoPhotosText.Visibility = hasPhotos ? Visibility.Collapsed : Visibility.Visible;
                    }
                }
                else if (_item.ItemType == ItemType.BankCard)
                {
                    var data = JsonSerializer.Deserialize<BankCardData>(json);
                    if (data != null)
                    {
                        // Card Visual
                        CardTypeText.Text = GetBankCardTypeName(data.CardTypeString);
                        CardMainNumber.Text = MaskBankCard(data.CardNumber);
                        CardHolderName.Text = data.CardholderName ?? "";
                        IssuedDatePanel.Visibility = Visibility.Collapsed;
                        CardExpiryDate.Text = $"{data.ExpiryMonth}/{data.ExpiryYear}";

                        if (string.IsNullOrEmpty(data.CardholderName))
                            CardHolderName.Visibility = Visibility.Collapsed;

                        // Info Fields
                        AddInfoField("卡号", FormatCardNumberFull(data.CardNumber), isSensitive: true);
                        AddInfoField("持卡人", data.CardholderName);
                        AddInfoField("有效期", $"{data.ExpiryMonth}/{data.ExpiryYear}");
                        AddInfoField("CVV", data.Cvv, isSensitive: true);
                        AddInfoField("发卡银行", data.BankName);
                        
                        // No photos for bank cards typically
                        NoPhotosText.Visibility = Visibility.Visible;
                    }
                }

                // Notes
                if (!string.IsNullOrEmpty(_item.Notes))
                {
                    NotesSection.Visibility = Visibility.Visible;
                    NotesText.Text = _item.Notes;
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error loading card: {ex.Message}");
            }
        }

        private void AddInfoField(string label, string? value, bool isSensitive = false)
        {
            if (string.IsNullOrEmpty(value)) return;

            var fieldPanel = new Grid { Margin = new Thickness(0, 0, 0, 4) };
            fieldPanel.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(90) });
            fieldPanel.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            // Label
            var labelBlock = new TextBlock
            {
                Text = label,
                FontSize = 14,
                Foreground = (Brush)Application.Current.Resources["TextFillColorSecondaryBrush"],
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(labelBlock, 0);
            fieldPanel.Children.Add(labelBlock);

            // Value + Eye button
            var valueStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
            
            var maskedValue = isSensitive ? MaskMiddle(value) : value;
            var valueBlock = new TextBlock
            {
                Text = maskedValue,
                FontSize = 14,
                VerticalAlignment = VerticalAlignment.Center,
                IsTextSelectionEnabled = true
            };
            valueStack.Children.Add(valueBlock);

            if (isSensitive)
            {
                var fieldId = Guid.NewGuid().ToString();
                _maskedFields[fieldId] = (valueBlock, value, maskedValue);
                
                var eyeButton = new Button
                {
                    Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
                    BorderThickness = new Thickness(0),
                    Padding = new Thickness(4, 2, 4, 2),
                    MinWidth = 24,
                    MinHeight = 24,
                    Tag = fieldId,
                    Content = new FontIcon { Glyph = "\uE7B3", FontSize = 12 }
                };
                eyeButton.Click += EyeButton_Click;
                valueStack.Children.Add(eyeButton);
            }

            Grid.SetColumn(valueStack, 1);
            fieldPanel.Children.Add(valueStack);

            InfoFieldsPanel.Children.Add(fieldPanel);
        }

        private void EyeButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string fieldId && _maskedFields.TryGetValue(fieldId, out var field))
            {
                var fontIcon = btn.Content as FontIcon;
                if (fontIcon == null) return;

                if (field.textBlock.Text == field.masked)
                {
                    field.textBlock.Text = field.original;
                    fontIcon.Glyph = "\uED1A";
                }
                else
                {
                    field.textBlock.Text = field.masked;
                    fontIcon.Glyph = "\uE7B3";
                }
            }
        }

        private string GetDocumentTypeName(string? type) => type switch
        {
            "ID_CARD" => "身份证",
            "PASSPORT" => "护照",
            "DRIVER_LICENSE" => "驾驶证",
            "SOCIAL_SECURITY" => "社保卡",
            _ => "证件"
        };

        private string GetBankCardTypeName(string? type) => type switch
        {
            "DEBIT" => "借记卡",
            "CREDIT" => "信用卡",
            "PREPAID" => "预付卡",
            _ => "银行卡"
        };

        private string MaskMiddle(string? value)
        {
            if (string.IsNullOrEmpty(value)) return "";
            if (value.Length <= 6) return value;
            
            int showStart = 3;
            int showEnd = 4;
            if (value.Length <= 10) { showStart = 2; showEnd = 2; }
            
            var start = value.Substring(0, showStart);
            var end = value.Substring(value.Length - showEnd);
            var middle = new string('*', value.Length - showStart - showEnd);
            return start + middle + end;
        }

        private string MaskBankCard(string? number)
        {
            if (string.IsNullOrEmpty(number)) return "";
            if (number.Length >= 4)
                return $"**** **** **** {number.Substring(number.Length - 4)}";
            return number;
        }

        private string FormatCardNumberFull(string? number)
        {
            if (string.IsNullOrEmpty(number)) return "";
            var result = "";
            for (int i = 0; i < number.Length; i++)
            {
                if (i > 0 && i % 4 == 0) result += " ";
                result += number[i];
            }
            return result;
        }

        private void BackButton_Click(object sender, RoutedEventArgs e)
        {
            if (Frame.CanGoBack)
            {
                Frame.GoBack();
            }
        }
    }
}
