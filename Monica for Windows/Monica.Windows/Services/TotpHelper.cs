using System;
using System.Security.Cryptography;
using System.Text;

namespace Monica.Windows.Services
{
    public static class TotpHelper
    {
        private const string STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY";

        /// <summary>
        /// Generate OTP code with custom parameters
        /// </summary>
        public static string GenerateCode(string secretKey, int period = 30, int digits = 6, string otpType = "TOTP", long counter = 0)
        {
            try
            {
                if (string.IsNullOrEmpty(secretKey)) return "------";

                // Base32 Decode
                byte[] key = Base32Decode(secretKey);

                // Handle different OTP types
                return otpType?.ToUpperInvariant() switch
                {
                    "STEAM" => GenerateSteamCode(key),
                    "HOTP" => GenerateHotpCode(key, counter),
                    "MOTP" => "------", // mOTP requires PIN, handled separately
                    _ => GenerateTotpCode(key, period, digits)
                };
            }
            catch
            {
                return "------";
            }
        }

        /// <summary>
        /// Generate standard TOTP code
        /// </summary>
        private static string GenerateTotpCode(byte[] key, int period, int digits)
        {
            long counter = DateTimeOffset.UtcNow.ToUnixTimeSeconds() / period;
            return GenerateOtpFromCounter(key, counter, digits);
        }

        /// <summary>
        /// Generate HOTP code from counter
        /// </summary>
        private static string GenerateHotpCode(byte[] key, long counter)
        {
            return GenerateOtpFromCounter(key, counter, 6);
        }

        /// <summary>
        /// Generate Steam Guard code (5 alphanumeric characters)
        /// </summary>
        private static string GenerateSteamCode(byte[] key)
        {
            long counter = DateTimeOffset.UtcNow.ToUnixTimeSeconds() / 30;
            byte[] counterBytes = BitConverter.GetBytes(counter);
            if (BitConverter.IsLittleEndian) Array.Reverse(counterBytes);

            using var hmac = new HMACSHA1(key);
            byte[] hash = hmac.ComputeHash(counterBytes);

            int offset = hash[hash.Length - 1] & 0x0F;
            int binary =
                ((hash[offset] & 0x7F) << 24) |
                ((hash[offset + 1] & 0xFF) << 16) |
                ((hash[offset + 2] & 0xFF) << 8) |
                (hash[offset + 3] & 0xFF);

            var code = new char[5];
            for (int i = 0; i < 5; i++)
            {
                code[i] = STEAM_CHARS[binary % STEAM_CHARS.Length];
                binary /= STEAM_CHARS.Length;
            }

            return new string(code);
        }

        /// <summary>
        /// Core OTP generation from counter value
        /// </summary>
        private static string GenerateOtpFromCounter(byte[] key, long counter, int digits)
        {
            byte[] counterBytes = BitConverter.GetBytes(counter);
            if (BitConverter.IsLittleEndian) Array.Reverse(counterBytes);

            // HMAC-SHA1
            using var hmac = new HMACSHA1(key);
            byte[] hash = hmac.ComputeHash(counterBytes);

            // Dynamic Truncation
            int offset = hash[hash.Length - 1] & 0x0F;
            int binary =
                ((hash[offset] & 0x7F) << 24) |
                ((hash[offset + 1] & 0xFF) << 16) |
                ((hash[offset + 2] & 0xFF) << 8) |
                (hash[offset + 3] & 0xFF);

            int divisor = (int)Math.Pow(10, digits);
            int otp = binary % divisor;
            return otp.ToString($"D{digits}");
        }

        /// <summary>
        /// Get remaining seconds for a given period
        /// </summary>
        public static int GetRemainingSeconds(int period = 30)
        {
            return period - (int)(DateTimeOffset.UtcNow.ToUnixTimeSeconds() % period);
        }

        private static byte[] Base32Decode(string input)
        {
            input = input.ToUpper().Trim().Replace(" ", "").Replace("-", "");
            const string alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
            var output = new byte[input.Length * 5 / 8];
            var buffer = 0;
            var bitsLeft = 0;
            var outputIndex = 0;

            foreach (var c in input)
            {
                var index = alphabet.IndexOf(c);
                if (index < 0) continue;

                buffer = (buffer << 5) | index;
                bitsLeft += 5;

                if (bitsLeft >= 8)
                {
                    output[outputIndex++] = (byte)(buffer >> (bitsLeft - 8));
                    bitsLeft -= 8;
                }
            }
            return output;
        }
    }
}
