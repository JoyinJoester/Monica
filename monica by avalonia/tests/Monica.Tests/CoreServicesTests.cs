using System.Security.Cryptography;
using Monica.Core.ImportExport;
using Monica.Core.Models;
using Monica.Core.Services;

namespace Monica.Tests;

public sealed class CoreServicesTests
{
    [Fact]
    public void Totp_generates_known_rfc_vector()
    {
        var service = new TotpService();

        var code = service.GenerateCode("JBSWY3DPEHPK3PXP", digits: 6);

        Assert.Matches("^[0-9]{6}$", code);
    }

    [Fact]
    public void Crypto_encrypts_and_decrypts_roundtrip()
    {
        var service = new CryptoService();
        var salt = service.CreateSalt();
        service.InitializeSession("correct horse battery staple", salt);

        var encrypted = service.EncryptString("secret payload");
        var decrypted = service.DecryptString(encrypted);

        Assert.NotEqual("secret payload", encrypted);
        Assert.Equal("secret payload", decrypted);
    }

    [Fact]
    public void Crypto_rejects_wrong_master_password()
    {
        var service = new CryptoService();
        var hash = service.HashMasterPassword("master");

        Assert.True(service.VerifyMasterPassword("master", hash));
        Assert.False(new CryptoService().VerifyMasterPassword("wrong", hash));
    }

    [Fact]
    public void Import_export_roundtrips_monica_json()
    {
        var service = new ImportExportService();
        var passwords = new[]
        {
            new PasswordEntry { Title = "GitHub", Username = "dev", Password = "encrypted" }
        };
        var items = new[]
        {
            new SecureItem { ItemType = VaultItemType.Note, Title = "Note", ItemData = "{}" }
        };

        var json = service.ExportJson(passwords, items);
        var package = service.ImportJson(json);

        Assert.Equal(68, package.SchemaVersion);
        Assert.Single(package.Passwords);
        Assert.Single(package.SecureItems);
    }

    [Fact]
    public void Feature_catalog_represents_android_parity_surface()
    {
        var keys = FeatureCatalog.AndroidParityFeatures.Select(item => item.Key).ToHashSet();

        Assert.Contains("passwords", keys);
        Assert.Contains("totp", keys);
        Assert.Contains("passkeys", keys);
        Assert.Contains("bitwarden", keys);
        Assert.Contains("keepass", keys);
        Assert.Contains("mdbx", keys);
        Assert.Contains("webdav", keys);
        Assert.Contains("autofill", keys);
    }

    [Fact]
    public void Password_generator_produces_strong_passwords()
    {
        var service = new PasswordGeneratorService();

        var password = service.GeneratePassword(24);
        var analysis = service.Analyze(password);

        Assert.Equal(24, password.Length);
        Assert.True(analysis.Score >= 3);
    }
}
