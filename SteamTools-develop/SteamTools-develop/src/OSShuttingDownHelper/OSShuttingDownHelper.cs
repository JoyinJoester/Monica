namespace Windows.Win32;

/// <summary>
/// 操作系统关机时助手类
/// </summary>
public static partial class OSShuttingDownHelper
{
    [DllImport("user32.dll")]
    static extern int GetSystemMetrics(int nIndex);

    const int SM_SHUTTINGDOWN = 0x2000;

    /// <summary>
    /// 是否为操作系统正在关机进行中
    /// </summary>
    /// <returns></returns>
    public static bool IsSystemShuttingDown()
    {
        if (!OperatingSystem.IsWindows())
        {
            return false;
        }

        try
        {
            return Environment.HasShutdownStarted || GetSystemMetrics(SM_SHUTTINGDOWN) != 0;
        }
        catch
        {
            return Environment.HasShutdownStarted;
        }
    }
}
