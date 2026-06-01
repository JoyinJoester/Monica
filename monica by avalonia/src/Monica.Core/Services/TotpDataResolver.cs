using System.Text.Json;
using Monica.Core.Models;

namespace Monica.Core.Services;

public static class TotpDataResolver
{
    private const int DefaultPeriod = 30;
    private const int DefaultDigits = 6;
    private const string DefaultAlgorithm = "SHA1";

    public static TotpData? FromAuthenticatorKey(string rawKey, string fallbackIssuer = "", string fallbackAccountName = "")
    {
        var normalizedKey = rawKey.Trim();
        if (string.IsNullOrWhiteSpace(normalizedKey))
        {
            return null;
        }

        var parsed = ParseUri(normalizedKey);
        var data = parsed ?? new TotpData(
            normalizedKey.Contains("://", StringComparison.Ordinal) ? "" : normalizedKey,
            fallbackIssuer.Trim(),
            fallbackAccountName.Trim());

        return Normalize(data with
        {
            Issuer = string.IsNullOrWhiteSpace(data.Issuer) ? fallbackIssuer.Trim() : data.Issuer,
            AccountName = string.IsNullOrWhiteSpace(data.AccountName) ? fallbackAccountName.Trim() : data.AccountName
        });
    }

    public static TotpData? ParseStoredItemData(string itemData, string fallbackIssuer = "", string fallbackAccountName = "")
    {
        if (string.IsNullOrWhiteSpace(itemData))
        {
            return null;
        }

        try
        {
            using var document = JsonDocument.Parse(itemData);
            var root = document.RootElement;
            var secret = GetString(root, "secret") ?? GetString(root, "key") ?? "";
            var issuer = GetString(root, "issuer") ?? GetString(root, "serviceName") ?? fallbackIssuer;
            var accountName = GetString(root, "accountName") ?? GetString(root, "account") ?? fallbackAccountName;
            var otpType = GetString(root, "otpType") ?? GetString(root, "type") ?? "TOTP";
            var algorithm = GetString(root, "algorithm") ?? DefaultAlgorithm;
            var period = GetInt(root, "period") ?? DefaultPeriod;
            var digits = GetInt(root, "digits") ?? DefaultDigits;
            var counter = GetLong(root, "counter") ?? 0;

            if (!string.IsNullOrWhiteSpace(secret) || !string.IsNullOrWhiteSpace(issuer) || !string.IsNullOrWhiteSpace(accountName))
            {
                return Normalize(new TotpData(secret, issuer, accountName, period, digits, algorithm, otpType, counter));
            }
        }
        catch (JsonException)
        {
        }

        return FromAuthenticatorKey(itemData, fallbackIssuer, fallbackAccountName);
    }

    public static TotpData Normalize(TotpData data)
    {
        var reparsed = ParseUri(data.Secret);
        if (reparsed is not null)
        {
            data = data with
            {
                Secret = reparsed.Secret,
                Issuer = string.IsNullOrWhiteSpace(data.Issuer) ? reparsed.Issuer : data.Issuer,
                AccountName = string.IsNullOrWhiteSpace(data.AccountName) ? reparsed.AccountName : data.AccountName,
                Period = data.Period <= 0 || data.Period == DefaultPeriod ? reparsed.Period : data.Period,
                Digits = data.Digits <= 0 || data.Digits == DefaultDigits ? reparsed.Digits : data.Digits,
                Algorithm = string.Equals(data.Algorithm, DefaultAlgorithm, StringComparison.OrdinalIgnoreCase) ? reparsed.Algorithm : data.Algorithm,
                OtpType = string.Equals(data.OtpType, "TOTP", StringComparison.OrdinalIgnoreCase) ? reparsed.OtpType : data.OtpType,
                Counter = data.Counter <= 0 ? reparsed.Counter : data.Counter
            };
        }

        var otpType = NormalizeOtpType(data.OtpType);
        var issuer = data.Issuer.Trim();
        var accountName = data.AccountName.Trim();
        if (otpType == "TOTP" && LooksLikeSteam(issuer, accountName))
        {
            otpType = "STEAM";
        }

        var digits = otpType == "STEAM"
            ? 5
            : Math.Clamp(data.Digits <= 0 ? DefaultDigits : data.Digits, 4, 10);

        return data with
        {
            Secret = otpType == "MOTP" ? data.Secret.Trim() : NormalizeBase32Secret(data.Secret),
            Issuer = issuer,
            AccountName = accountName,
            Period = otpType == "STEAM" || data.Period <= 0 ? DefaultPeriod : data.Period,
            Digits = digits,
            Algorithm = string.IsNullOrWhiteSpace(data.Algorithm) ? DefaultAlgorithm : data.Algorithm.Trim().ToUpperInvariant(),
            OtpType = otpType,
            Counter = Math.Max(0, data.Counter)
        };
    }

    public static string ToItemData(TotpData data)
    {
        var normalized = Normalize(data);
        return JsonSerializer.Serialize(new
        {
            secret = normalized.Secret,
            issuer = normalized.Issuer,
            accountName = normalized.AccountName,
            period = normalized.Period,
            digits = normalized.Digits,
            algorithm = normalized.Algorithm,
            otpType = normalized.OtpType,
            counter = normalized.Counter
        });
    }

    private static TotpData? ParseUri(string raw)
    {
        if (!raw.Contains("://", StringComparison.Ordinal))
        {
            return null;
        }

        if (raw.StartsWith("steam://", StringComparison.OrdinalIgnoreCase))
        {
            var steamSecret = Uri.UnescapeDataString(raw["steam://".Length..].Split(['?', '#'], 2)[0].Trim('/'));
            return string.IsNullOrWhiteSpace(steamSecret)
                ? null
                : new TotpData(steamSecret, "Steam", "", DefaultPeriod, 5, DefaultAlgorithm, "STEAM");
        }

        if (!Uri.TryCreate(raw, UriKind.Absolute, out var uri) ||
            !string.Equals(uri.Scheme, "otpauth", StringComparison.OrdinalIgnoreCase))
        {
            return null;
        }

        var otpType = NormalizeOtpType(uri.Host);
        var query = ParseQuery(uri.Query);
        query.TryGetValue("secret", out var secret);
        if (string.IsNullOrWhiteSpace(secret))
        {
            return null;
        }

        var label = Uri.UnescapeDataString(uri.AbsolutePath.Trim('/'));
        var issuer = query.GetValueOrDefault("issuer", "");
        var accountName = "";
        var separator = label.IndexOf(':', StringComparison.Ordinal);
        if (separator >= 0)
        {
            issuer = string.IsNullOrWhiteSpace(issuer) ? label[..separator] : issuer;
            accountName = label[(separator + 1)..];
        }
        else
        {
            accountName = label;
        }

        var encoder = query.GetValueOrDefault("encoder", "");
        if (string.Equals(encoder, "steam", StringComparison.OrdinalIgnoreCase))
        {
            otpType = "STEAM";
        }

        return new TotpData(
            secret,
            issuer,
            accountName,
            GetInt(query, "period") ?? DefaultPeriod,
            GetInt(query, "digits") ?? (otpType == "STEAM" ? 5 : DefaultDigits),
            query.GetValueOrDefault("algorithm", DefaultAlgorithm),
            otpType,
            GetLong(query, "counter") ?? 0);
    }

    private static Dictionary<string, string> ParseQuery(string query)
    {
        var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var trimmed = query.TrimStart('?');
        if (string.IsNullOrWhiteSpace(trimmed))
        {
            return result;
        }

        foreach (var pair in trimmed.Split('&', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            var separator = pair.IndexOf('=');
            var key = separator >= 0 ? pair[..separator] : pair;
            var value = separator >= 0 ? pair[(separator + 1)..] : "";
            if (!string.IsNullOrWhiteSpace(key))
            {
                result[Uri.UnescapeDataString(key)] = Uri.UnescapeDataString(value.Replace("+", "%20", StringComparison.Ordinal));
            }
        }

        return result;
    }

    private static string NormalizeBase32Secret(string secret) =>
        secret.Trim()
            .Replace(" ", "", StringComparison.Ordinal)
            .Replace("-", "", StringComparison.Ordinal)
            .ToUpperInvariant();

    private static string NormalizeOtpType(string value)
    {
        return value.Trim().Replace("-", "", StringComparison.Ordinal).Replace("_", "", StringComparison.Ordinal).ToUpperInvariant() switch
        {
            "HOTP" => "HOTP",
            "STEAM" => "STEAM",
            "MOTP" => "MOTP",
            _ => "TOTP"
        };
    }

    private static bool LooksLikeSteam(string issuer, string accountName)
    {
        var context = $"{issuer} {accountName}".ToLowerInvariant();
        return context.Contains("steam", StringComparison.Ordinal);
    }

    private static string? GetString(JsonElement root, string name) =>
        root.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    private static int? GetInt(JsonElement root, string name) =>
        root.TryGetProperty(name, out var value) && value.TryGetInt32(out var result)
            ? result
            : null;

    private static long? GetLong(JsonElement root, string name) =>
        root.TryGetProperty(name, out var value) && value.TryGetInt64(out var result)
            ? result
            : null;

    private static int? GetInt(IReadOnlyDictionary<string, string> values, string name) =>
        values.TryGetValue(name, out var value) && int.TryParse(value, out var result)
            ? result
            : null;

    private static long? GetLong(IReadOnlyDictionary<string, string> values, string name) =>
        values.TryGetValue(name, out var value) && long.TryParse(value, out var result)
            ? result
            : null;
}
