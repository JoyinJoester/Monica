using System;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;

namespace Monica.Windows.Services
{
    public static class BackupEncryptionHelper
    {
        private const string FILE_MAGIC = "MONICA_ENC_V1";
        private const int PBKDF2_ITERATIONS = 100000;
        private const int KEY_SIZE_BYTES = 32; // 256 bits
        private const int SALT_LENGTH = 32;
        private const int GCM_IV_LENGTH = 12;
        private const int GCM_TAG_LENGTH = 16;

        public static void EncryptFile(string inputFile, string outputFile, string password)
        {
            byte[] inputBytes = File.ReadAllBytes(inputFile);

            // 1. Generate Salt and IV
            byte[] salt = new byte[SALT_LENGTH];
            RandomNumberGenerator.Fill(salt);

            byte[] iv = new byte[GCM_IV_LENGTH];
            RandomNumberGenerator.Fill(iv);

            // 2. Derive Key
            byte[] key = DeriveKey(password, salt);

            // 3. Encrypt
            byte[] cipherText = new byte[inputBytes.Length];
            byte[] tag = new byte[GCM_TAG_LENGTH];

            using (var aes = new AesGcm(key, GCM_TAG_LENGTH))
            {
                aes.Encrypt(iv, inputBytes, cipherText, tag);
            }

            // 4. Write Output: [MAGIC][SALT][IV][CIPHERTEXT][TAG]
            // Note: Java's doFinal returns CipherText + Tag. We emulate this by writing them sequentially.
            using (var fs = new FileStream(outputFile, FileMode.Create))
            using (var writer = new BinaryWriter(fs))
            {
                writer.Write(Encoding.UTF8.GetBytes(FILE_MAGIC));
                writer.Write(salt);
                writer.Write(iv);
                writer.Write(cipherText);
                writer.Write(tag);
            }
        }

        public static void DecryptFile(string inputFile, string outputFile, string password)
        {
            byte[] fileBytes = File.ReadAllBytes(inputFile);

            // 1. Validate Size
            int minSize = FILE_MAGIC.Length + SALT_LENGTH + GCM_IV_LENGTH + GCM_TAG_LENGTH;
            if (fileBytes.Length < minSize)
                throw new Exception("File too small or corrupted.");

            // 2. Validate Magic
            string magic = Encoding.UTF8.GetString(fileBytes, 0, FILE_MAGIC.Length);
            if (magic != FILE_MAGIC)
                throw new Exception("Invalid file format. Magic header mismatch.");

            int offset = FILE_MAGIC.Length;

            // 3. Extract Headers
            byte[] salt = fileBytes.AsSpan(offset, SALT_LENGTH).ToArray();
            offset += SALT_LENGTH;

            byte[] iv = fileBytes.AsSpan(offset, GCM_IV_LENGTH).ToArray();
            offset += GCM_IV_LENGTH;

            // 4. Extract Ciphertext and Tag
            // Remaining bytes are [Ciphertext + Tag]
            int cipherTextLen = fileBytes.Length - offset - GCM_TAG_LENGTH;
            if (cipherTextLen < 0) throw new Exception("File corrupted (payload too short).");

            byte[] cipherText = fileBytes.AsSpan(offset, cipherTextLen).ToArray();
            byte[] tag = fileBytes.AsSpan(fileBytes.Length - GCM_TAG_LENGTH, GCM_TAG_LENGTH).ToArray();

            // 5. Derive Key
            byte[] key = DeriveKey(password, salt);

            // 6. Decrypt
            byte[] plainText = new byte[cipherText.Length];
            using (var aes = new AesGcm(key, GCM_TAG_LENGTH))
            {
                try
                {
                    aes.Decrypt(iv, cipherText, tag, plainText);
                }
                catch (CryptographicException)
                {
                    throw new Exception("Decryption failed. Incorrect password or corrupted file.");
                }
            }

            File.WriteAllBytes(outputFile, plainText);
        }

        public static bool IsEncryptedFile(string filePath)
        {
            try
            {
                if (filePath.EndsWith(".enc.zip")) return true;

                using (var fs = File.OpenRead(filePath))
                {
                    if (fs.Length < FILE_MAGIC.Length) return false;
                    byte[] buffer = new byte[FILE_MAGIC.Length];
                    fs.Read(buffer, 0, buffer.Length);
                    string magic = Encoding.UTF8.GetString(buffer);
                    return magic == FILE_MAGIC;
                }
            }
            catch
            {
                return false;
            }
        }

        private static byte[] DeriveKey(string password, byte[] salt)
        {
            // PBKDF2 with HmacSHA256, 100,000 iterations
            using (var pbkdf2 = new Rfc2898DeriveBytes(password, salt, PBKDF2_ITERATIONS, HashAlgorithmName.SHA256))
            {
                return pbkdf2.GetBytes(KEY_SIZE_BYTES);
            }
        }
    }
}
