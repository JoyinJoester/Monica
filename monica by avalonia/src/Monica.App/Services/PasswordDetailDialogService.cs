using Avalonia.Controls;
using FluentAvalonia.UI.Controls;
using Monica.App.ViewModels;
using Monica.Core.Models;
using Monica.Core.Services;
using Monica.Platform.Services;

namespace Monica.App.Services;

public interface IPasswordDetailDialogService
{
    Task ShowAsync(
        PasswordEntry entry,
        IReadOnlyList<PasswordEntry> siblings,
        Category? category,
        SecureItem? boundNote,
        IReadOnlyList<CustomField> customFields,
        CancellationToken cancellationToken = default);
}

public sealed class PasswordDetailDialogService(
    Func<Window> ownerProvider,
    ILocalizationService localization,
    IClipboardService clipboardService,
    ICryptoService cryptoService,
    ITotpService totpService) : IPasswordDetailDialogService
{
    public async Task ShowAsync(
        PasswordEntry entry,
        IReadOnlyList<PasswordEntry> siblings,
        Category? category,
        SecureItem? boundNote,
        IReadOnlyList<CustomField> customFields,
        CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var details = new PasswordDetailViewModel(
            localization,
            clipboardService,
            cryptoService,
            totpService,
            entry,
            siblings,
            category,
            boundNote,
            customFields);

        var dialog = new FAContentDialog
        {
            Title = details.DialogTitle,
            Content = new PasswordDetailDialog { DataContext = details },
            CloseButtonText = localization.Get("Close"),
            DefaultButton = FAContentDialogButton.Close
        };

        await dialog.ShowAsync(ownerProvider());
    }
}
