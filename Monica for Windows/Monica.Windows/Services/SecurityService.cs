using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace Monica.Windows.Services
{
    public class SecurityService : ISecurityService
    {
        private const int PBKDF2_ITERATIONS = 100000;
        private const int NONCE_SIZE = 12;
        private const int TAG_SIZE = 16;
        private const int KEY_SIZE = 32;
        private const int SALT_SIZE = 16;

        private byte[]? _masterKey;
        private readonly string _configPath;

        public bool IsUnlocked => _masterKey != null;

        public SecurityService()
        {
            var folder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            var appFolder = Path.Combine(folder, "Monica");
            Directory.CreateDirectory(appFolder);
            _configPath = Path.Combine(appFolder, "security.json");
        }

        public bool IsMasterPasswordSet()
        {
            return File.Exists(_configPath);
        }

        public void SetMasterPassword(string password)
        {
            var salt = new byte[SALT_SIZE];
            RandomNumberGenerator.Fill(salt);

            var hash = DeriveKey(password, salt);
            var hashHex = Convert.ToHexString(hash);

            var config = new SecurityConfig
            {
                PasswordHash = hashHex,
                Salt = Convert.ToBase64String(salt)
            };

            var json = JsonSerializer.Serialize(config);
            File.WriteAllText(_configPath, json);
        }

        public bool Unlock(string password)
        {
            if (!IsMasterPasswordSet()) return false;

            try
            {
                var json = File.ReadAllText(_configPath);
                var config = JsonSerializer.Deserialize<SecurityConfig>(json);
                if (config == null) return false;

                var salt = Convert.FromBase64String(config.Salt);
                var computedHash = DeriveKey(password, salt);
                var computedHashHex = Convert.ToHexString(computedHash);

                if (computedHashHex.Equals(config.PasswordHash, StringComparison.OrdinalIgnoreCase))
                {
                    _masterKey = computedHash;
                    return true;
                }
            }
            catch { }

            return false;
        }

        public void Lock()
        {
            _masterKey = null;
        }

        private byte[] DeriveKey(string password, byte[] salt)
        {
            using var derive = new Rfc2898DeriveBytes(password, salt, PBKDF2_ITERATIONS, HashAlgorithmName.SHA256);
            return derive.GetBytes(KEY_SIZE);
        }

        public string Encrypt(string plainText)
        {
            if (_masterKey == null) throw new InvalidOperationException("Vault is locked");
            if (string.IsNullOrEmpty(plainText)) return "";

            byte[] plainBytes = Encoding.UTF8.GetBytes(plainText);
            byte[] nonce = new byte[NONCE_SIZE];
            RandomNumberGenerator.Fill(nonce);

            byte[] tag = new byte[TAG_SIZE];
            byte[] cipherText = new byte[plainBytes.Length];

            using (var aes = new AesGcm(_masterKey, TAG_SIZE))
            {
                aes.Encrypt(nonce, plainBytes, cipherText, tag);
            }

            // Format: Nonce + Tag + Ciphertext
            byte[] combined = new byte[NONCE_SIZE + TAG_SIZE + cipherText.Length];
            Buffer.BlockCopy(nonce, 0, combined, 0, NONCE_SIZE);
            Buffer.BlockCopy(tag, 0, combined, NONCE_SIZE, TAG_SIZE);
            Buffer.BlockCopy(cipherText, 0, combined, NONCE_SIZE + TAG_SIZE, cipherText.Length);

            return Convert.ToBase64String(combined);
        }

        public string Decrypt(string cipherTextBase64)
        {
            if (_masterKey == null) throw new InvalidOperationException("Vault is locked");
            if (string.IsNullOrEmpty(cipherTextBase64)) return "";

            try
            {
                byte[] combined = Convert.FromBase64String(cipherTextBase64);
                if (combined.Length < NONCE_SIZE + TAG_SIZE) return "";

                byte[] nonce = new byte[NONCE_SIZE];
                byte[] tag = new byte[TAG_SIZE];
                byte[] cipherText = new byte[combined.Length - NONCE_SIZE - TAG_SIZE];

                Buffer.BlockCopy(combined, 0, nonce, 0, NONCE_SIZE);
                Buffer.BlockCopy(combined, NONCE_SIZE, tag, 0, TAG_SIZE);
                Buffer.BlockCopy(combined, NONCE_SIZE + TAG_SIZE, cipherText, 0, cipherText.Length);

                byte[] plainBytes = new byte[cipherText.Length];

                using (var aes = new AesGcm(_masterKey, TAG_SIZE))
                {
                    aes.Decrypt(nonce, cipherText, tag, plainBytes);
                }

                return Encoding.UTF8.GetString(plainBytes);
            }
            catch
            {
                return "";
            }
        }

        public bool IsSecurityQuestionSet()
        {
            try
            {
                if (!File.Exists(_configPath)) return false;
                var json = File.ReadAllText(_configPath);
                var config = JsonSerializer.Deserialize<SecurityConfig>(json);
                return !string.IsNullOrEmpty(config?.SecurityQuestion) && !string.IsNullOrEmpty(config?.SecurityAnswerHash);
            }
            catch
            {
                return false;
            }
        }

        public void SetSecurityQuestion(string question, string answer)
        {
            if (!File.Exists(_configPath)) return;

            var json = File.ReadAllText(_configPath);
            var config = JsonSerializer.Deserialize<SecurityConfig>(json);
            if (config == null) return;

            config.SecurityQuestion = question;

            // Hash the answer
            var salt = new byte[SALT_SIZE];
            RandomNumberGenerator.Fill(salt);
            var hash = DeriveKey(answer.ToLowerInvariant().Trim(), salt); // Normalize answer
            
            config.SecurityAnswerHash = Convert.ToHexString(hash);
            config.SecurityAnswerSalt = Convert.ToBase64String(salt);

            var newJson = JsonSerializer.Serialize(config);
            File.WriteAllText(_configPath, newJson);
        }

        public string? GetSecurityQuestion()
        {
            try
            {
                if (!File.Exists(_configPath)) return null;
                var json = File.ReadAllText(_configPath);
                var config = JsonSerializer.Deserialize<SecurityConfig>(json);
                return config?.SecurityQuestion;
            }
            catch
            {
                return null;
            }
        }

        public bool ValidateSecurityQuestion(string answer)
        {
            try
            {
                if (!File.Exists(_configPath)) return false;
                var json = File.ReadAllText(_configPath);
                var config = JsonSerializer.Deserialize<SecurityConfig>(json);
                if (config == null || string.IsNullOrEmpty(config.SecurityAnswerHash) || string.IsNullOrEmpty(config.SecurityAnswerSalt)) return false;

                var salt = Convert.FromBase64String(config.SecurityAnswerSalt);
                var computedHash = DeriveKey(answer.ToLowerInvariant().Trim(), salt);
                var computedHashHex = Convert.ToHexString(computedHash);

                return computedHashHex.Equals(config.SecurityAnswerHash, StringComparison.OrdinalIgnoreCase);
            }
            catch
            {
                return false;
            }
        }

        public void ClearAllData()
        {
            try
            {
                // Delete config
                if (File.Exists(_configPath)) File.Delete(_configPath);
                
                // Delete DB
                var folder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                var dbPath = Path.Combine(folder, "Monica", "monica.db");
                if (File.Exists(dbPath)) File.Delete(dbPath);

                // Delete MonicaAttachments folder (image storage)
                var attachmentsPath = Path.Combine(folder, "MonicaAttachments");
                if (Directory.Exists(attachmentsPath))
                {
                    Directory.Delete(attachmentsPath, true);
                }

                // Lock vault
                _masterKey = null;
            }
            catch (Exception)
            {
                // Log error potentially
                throw;
            }
        }

        private class SecurityConfig
        {
            public string PasswordHash { get; set; } = "";
            public string Salt { get; set; } = "";
            public string? SecurityQuestion { get; set; }
            public string? SecurityAnswerHash { get; set; }
            public string? SecurityAnswerSalt { get; set; }
        }
    }
}
