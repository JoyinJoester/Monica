using Monica.Core.Models;

namespace Monica.Platform.Services;

public sealed class OneDriveBackupService : IOneDriveBackupService
{
    public PlatformCapability GetCapability() => new(
        "onedrive",
        "OneDrive",
        "Microsoft Graph, MSAL and Azure.Identity are referenced; interactive sign-in and file sync are exposed as a desktop integration boundary.",
        PlatformFeatureStatus.DesktopEquivalent);
}
