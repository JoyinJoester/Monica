using Avalonia.Input.Platform;
using Monica.Platform.Services;

namespace Monica.App.Services;

public sealed class AvaloniaClipboardService(Func<Avalonia.Controls.TopLevel?> topLevelProvider) : IClipboardService
{
    public async Task SetTextAsync(string text, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var clipboard = topLevelProvider()?.Clipboard;
        if (clipboard is not null)
        {
            await clipboard.SetTextAsync(text);
        }
    }
}
