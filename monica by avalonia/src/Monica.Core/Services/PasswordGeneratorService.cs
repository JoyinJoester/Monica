using System.Security.Cryptography;
using System.Text;

namespace Monica.Core.Services;

public interface IPasswordGeneratorService
{
    string GeneratePassword(int length = 20, bool includeSymbols = true);
    PasswordStrengthResult Analyze(string password);
}

public sealed record PasswordStrengthResult(int Score, string Label, IReadOnlyList<string> Warnings);

public sealed class PasswordGeneratorService : IPasswordGeneratorService
{
    private const string Letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private const string Digits = "0123456789";
    private const string Symbols = "!@#$%^&*()-_=+[]{};:,.?";

    public string GeneratePassword(int length = 20, bool includeSymbols = true)
    {
        length = Math.Clamp(length, 8, 128);
        var alphabet = Letters + Digits + (includeSymbols ? Symbols : "");
        var builder = new StringBuilder(length);
        for (var i = 0; i < length; i++)
        {
            builder.Append(alphabet[RandomNumberGenerator.GetInt32(alphabet.Length)]);
        }

        return builder.ToString();
    }

    public PasswordStrengthResult Analyze(string password)
    {
        var warnings = new List<string>();
        var score = 0;

        if (password.Length >= 12) score++;
        else warnings.Add("Password is shorter than 12 characters.");

        if (password.Any(char.IsLower) && password.Any(char.IsUpper)) score++;
        else warnings.Add("Use both upper and lower case letters.");

        if (password.Any(char.IsDigit)) score++;
        else warnings.Add("Add numbers.");

        if (password.Any(c => !char.IsLetterOrDigit(c))) score++;
        else warnings.Add("Add symbols.");

        if (password.Length >= 20) score++;

        var label = score switch
        {
            >= 5 => "Excellent",
            4 => "Strong",
            3 => "Fair",
            2 => "Weak",
            _ => "Very weak"
        };

        return new PasswordStrengthResult(score, label, warnings);
    }
}
