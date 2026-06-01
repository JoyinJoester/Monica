namespace Monica.Core.Models;

public sealed record TotpData(
    string Secret,
    string Issuer = "",
    string AccountName = "",
    int Period = 30,
    int Digits = 6,
    string Algorithm = "SHA1",
    string OtpType = "TOTP",
    long Counter = 0);
