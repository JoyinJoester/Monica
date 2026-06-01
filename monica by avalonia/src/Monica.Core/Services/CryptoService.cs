using System.Security.Cryptography;
using System.Text;
using Konscious.Security.Cryptography;

namespace Monica.Core.Services;

public interface ICryptoService
{
    byte[] CreateSalt(int length = 16);
    MasterPasswordHash HashMasterPassword(string password, byte[]? salt = null);
    bool VerifyMasterPassword(string password, MasterPasswordHash storedHash);
    void InitializeSession(string password, byte[] salt);
    bool IsUnlocked { get; }
    string EncryptString(string plainText);
    string DecryptString(string protectedText);
}

public sealed record MasterPasswordHash(string Hash, byte[] Salt, string Kdf, int Iterations, int MemoryKiB, int Parallelism);

public sealed class CryptoService : ICryptoService
{
    private const int KeySize = 32;
    private const int NonceSize = 12;
    private const int TagSize = 16;
    private const int ArgonIterations = 3;
    private const int ArgonMemoryKiB = 64 * 1024;
    private const int ArgonParallelism = 2;
    private const int Pbkdf2Iterations = 600_000;
    private byte[]? _sessionKey;

    public bool IsUnlocked => _sessionKey is not null;

    public byte[] CreateSalt(int length = 16)
    {
        var salt = new byte[length];
        RandomNumberGenerator.Fill(salt);
        return salt;
    }

    public MasterPasswordHash HashMasterPassword(string password, byte[]? salt = null)
    {
        var actualSalt = salt ?? CreateSalt();
        var hash = DerivePbkdf2Key(password, actualSalt, Pbkdf2Iterations);
        return new MasterPasswordHash(Convert.ToBase64String(hash), actualSalt, "pbkdf2-sha256", Pbkdf2Iterations, 0, 1);
    }

    public bool VerifyMasterPassword(string password, MasterPasswordHash storedHash)
    {
        var candidate = DeriveKey(password, storedHash);
        var expected = Convert.FromBase64String(storedHash.Hash);
        var verified = CryptographicOperations.FixedTimeEquals(candidate, expected);
        if (verified)
        {
            _sessionKey = candidate;
        }

        return verified;
    }

    public void InitializeSession(string password, byte[] salt)
    {
        _sessionKey = DerivePbkdf2Key(password, salt, Pbkdf2Iterations);
    }

    public string EncryptString(string plainText)
    {
        if (string.IsNullOrEmpty(plainText))
        {
            return "";
        }

        var key = _sessionKey ?? throw new InvalidOperationException("Vault is locked.");
        var plainBytes = Encoding.UTF8.GetBytes(plainText);
        var nonce = new byte[NonceSize];
        var tag = new byte[TagSize];
        var cipherBytes = new byte[plainBytes.Length];
        RandomNumberGenerator.Fill(nonce);

        using var aes = new AesGcm(key, TagSize);
        aes.Encrypt(nonce, plainBytes, cipherBytes, tag);

        var payload = new byte[NonceSize + TagSize + cipherBytes.Length];
        Buffer.BlockCopy(nonce, 0, payload, 0, NonceSize);
        Buffer.BlockCopy(tag, 0, payload, NonceSize, TagSize);
        Buffer.BlockCopy(cipherBytes, 0, payload, NonceSize + TagSize, cipherBytes.Length);

        return Convert.ToBase64String(payload);
    }

    public string DecryptString(string protectedText)
    {
        if (string.IsNullOrEmpty(protectedText))
        {
            return "";
        }

        var key = _sessionKey ?? throw new InvalidOperationException("Vault is locked.");
        var payload = Convert.FromBase64String(protectedText);
        if (payload.Length < NonceSize + TagSize)
        {
            throw new CryptographicException("Invalid protected payload.");
        }

        var nonce = payload[..NonceSize];
        var tag = payload[NonceSize..(NonceSize + TagSize)];
        var cipherBytes = payload[(NonceSize + TagSize)..];
        var plainBytes = new byte[cipherBytes.Length];

        using var aes = new AesGcm(key, TagSize);
        aes.Decrypt(nonce, cipherBytes, tag, plainBytes);
        return Encoding.UTF8.GetString(plainBytes);
    }

    private static byte[] DeriveKey(string password, MasterPasswordHash storedHash)
    {
        return storedHash.Kdf.Equals("argon2id", StringComparison.OrdinalIgnoreCase)
            ? DeriveArgon2Key(password, storedHash.Salt, storedHash.Iterations, storedHash.MemoryKiB, storedHash.Parallelism)
            : DerivePbkdf2Key(password, storedHash.Salt, storedHash.Iterations > 0 ? storedHash.Iterations : Pbkdf2Iterations);
    }

    private static byte[] DerivePbkdf2Key(string password, byte[] salt, int iterations)
    {
        return Rfc2898DeriveBytes.Pbkdf2(
            Encoding.UTF8.GetBytes(password),
            salt,
            iterations,
            HashAlgorithmName.SHA256,
            KeySize);
    }

    private static byte[] DeriveArgon2Key(string password, byte[] salt, int iterations, int memoryKiB, int parallelism)
    {
        var passwordBytes = Encoding.UTF8.GetBytes(password);
        using var argon2 = new Argon2id(passwordBytes)
        {
            Salt = salt,
            DegreeOfParallelism = parallelism > 0 ? parallelism : ArgonParallelism,
            Iterations = iterations > 0 ? iterations : ArgonIterations,
            MemorySize = memoryKiB > 0 ? memoryKiB : ArgonMemoryKiB
        };

        return argon2.GetBytes(KeySize);
    }
}
