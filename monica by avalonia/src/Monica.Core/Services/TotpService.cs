using OtpNet;

namespace Monica.Core.Services;

public interface ITotpService
{
    string GenerateCode(string secretKey, int period = 30, int digits = 6, string otpType = "TOTP", long counter = 0);
    int GetRemainingSeconds(int period = 30, DateTimeOffset? now = null);
    double GetProgress(int period = 30, DateTimeOffset? now = null);
}

public sealed class TotpService : ITotpService
{
    private const string SteamChars = "23456789BCDFGHJKMNPQRTVWXY";

    public string GenerateCode(string secretKey, int period = 30, int digits = 6, string otpType = "TOTP", long counter = 0)
    {
        if (string.IsNullOrWhiteSpace(secretKey))
        {
            return "------";
        }

        try
        {
            var key = Base32Encoding.ToBytes(NormalizeSecret(secretKey));
            return otpType.ToUpperInvariant() switch
            {
                "HOTP" => new Hotp(key, mode: OtpHashMode.Sha1, hotpSize: digits).ComputeHOTP(counter),
                "STEAM" => GenerateSteamCode(key),
                "MOTP" => "------",
                _ => new Totp(key, step: period, mode: OtpHashMode.Sha1, totpSize: digits).ComputeTotp()
            };
        }
        catch
        {
            return "------";
        }
    }

    public int GetRemainingSeconds(int period = 30, DateTimeOffset? now = null)
    {
        var timestamp = (now ?? DateTimeOffset.UtcNow).ToUnixTimeSeconds();
        return period - (int)(timestamp % period);
    }

    public double GetProgress(int period = 30, DateTimeOffset? now = null)
    {
        var remaining = GetRemainingSeconds(period, now);
        return Math.Clamp((period - remaining) * 100d / period, 0d, 100d);
    }

    private static string NormalizeSecret(string secretKey) =>
        secretKey.Trim().Replace(" ", "", StringComparison.Ordinal).Replace("-", "", StringComparison.Ordinal).ToUpperInvariant();

    private static string GenerateSteamCode(byte[] key)
    {
        var counter = DateTimeOffset.UtcNow.ToUnixTimeSeconds() / 30;
        var hotp = new Hotp(key, mode: OtpHashMode.Sha1, hotpSize: 10);
        var numeric = long.Parse(hotp.ComputeHOTP(counter));
        var chars = new char[5];
        for (var i = 0; i < chars.Length; i++)
        {
            chars[i] = SteamChars[(int)(numeric % SteamChars.Length)];
            numeric /= SteamChars.Length;
        }

        return new string(chars);
    }
}
