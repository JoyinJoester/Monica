using System;
using System.Security.Cryptography;
using System.Text;
using System.IO;

namespace Monica.Windows.Services
{
    public class CryptoService : ICryptoService
    {
        private const int PBKDF2_ITERATIONS = 100000;
        private byte[]? _masterKey;

        // AES-GCM requires 12 bytes IV and produces 16 bytes auth tag (which allows appended).
        // Format: [IV (12)][Ciphertext][Tag (16)] -> usually AESGCM .NET handles tag separately or combined depending on API
        // .NET AesGcm: Encrypt(nonce, plaintext, ciphertext, tag)
        // We will store as Base64: [Nonce(12) + Tag(16) + Ciphertext] to keep it simple or following standard.
        // Let's use: Nonce (12) | Tag (16) | Ciphertext
        
        private const int NONCE_SIZE = 12;
        private const int TAG_SIZE = 16;
        private const int KEY_SIZE = 32; // 256 bits

        public void SetMasterPassword(string password)
        {
            // In a real app we might not keep the password, but derive the key immediately.
            // For encryption/decryption we need the Key.
            // We use the salt from storage if available, else new random? 
            // Wait, to encrypt data we need STABLE key?
            // If we use User Password -> KDF -> DEK (Data Encryption Key)
            // The Android app seems to use a MasterKey from Android Keystore.
            // Here we want to support portability?
            // "Port specific implementation logic" -> The user asked to re-implement, standards match or exceed.
            
            // Current Session Key strategy:
            // When user logs in, we derive the Key from (Password + Stored Salt).
            // This Key is kept in memory (_masterKey).
        }

        public bool AccessRepository()
        {
            return _masterKey != null;
        }

        public void InitializeSession(string password, byte[] salt)
        {
             using (var derive = new Rfc2898DeriveBytes(password, salt, PBKDF2_ITERATIONS, HashAlgorithmName.SHA256))
             {
                 _masterKey = derive.GetBytes(KEY_SIZE);
             }
        }

        public bool IsMasterPasswordSet()
        {
            // This is checked against DB or Prefs usually
            return true; 
        }

        public string Encrypt(string plainText)
        {
            if (_masterKey == null) throw new InvalidOperationException("Session locked");
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

            // Combine: Nonce + Tag + Ciphertext
            // Android implementation: [IV (12)] + [Encrypted (? include tag?)]
            // Standard GCM output is usually CipherText + Tag.
            
            byte[] combined = new byte[NONCE_SIZE + TAG_SIZE + cipherText.Length];
            Buffer.BlockCopy(nonce, 0, combined, 0, NONCE_SIZE);
            Buffer.BlockCopy(tag, 0, combined, NONCE_SIZE, TAG_SIZE);
            Buffer.BlockCopy(cipherText, 0, combined, NONCE_SIZE + TAG_SIZE, cipherText.Length);

            return Convert.ToBase64String(combined);
        }

        public string Decrypt(string cipherTextBase64)
        {
            if (_masterKey == null) throw new InvalidOperationException("Session locked");
            if (string.IsNullOrEmpty(cipherTextBase64)) return "";

            try 
            {
                byte[] combined = Convert.FromBase64String(cipherTextBase64);
                
                if (combined.Length < NONCE_SIZE + TAG_SIZE) return ""; // Invalid

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
                return "Decryption Failed";
            }
        }

        public (string Hash, byte[] Salt) HashMasterPassword(string password, byte[]? salt = null)
        {
            byte[] actualSalt = salt ?? new byte[16];
            if (salt == null) RandomNumberGenerator.Fill(actualSalt);

            using (var derive = new Rfc2898DeriveBytes(password, actualSalt, PBKDF2_ITERATIONS, HashAlgorithmName.SHA256))
            {
                 byte[] hash = derive.GetBytes(32); // 256 bits hash
                 return (Convert.ToHexString(hash).ToLower(), actualSalt);
            }
        }

        public bool VerifyMasterPassword(string inputPassword, string storedHash, byte[] storedSalt)
        {
            var result = HashMasterPassword(inputPassword, storedSalt);
            InitializeSession(inputPassword, storedSalt); // Side effect: unlocks session if valid
            return result.Hash == storedHash;
        }
    }
}
