using System;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;

namespace Monica.Windows.Services
{
    /// <summary>
    /// Minimal Scrypt implementation for Aegis compatibility.
    /// Parameters: N (CPU cost), r (Block size), p (Parallelization)
    /// </summary>
    public class ScryptEncoder
    {
        public static byte[] DeriveKey(string password, byte[] salt, int N, int r, int p, int dkLen)
        {
            byte[] passwordBytes = Encoding.UTF8.GetBytes(password);
            return Scrypt(passwordBytes, salt, N, r, p, dkLen);
        }

        private static byte[] Scrypt(byte[] password, byte[] salt, int N, int r, int p, int dkLen)
        {
            if (N < 2 || (N & (N - 1)) != 0) throw new ArgumentException("N must be a power of 2 greater than 1", nameof(N));

            // 1. B = PBKDF2(P, S, 1, p * 128 * r)
            byte[] B = PBKDF2(password, salt, 1, p * 128 * r);

            // 2. Fixup length for SMix
            int blockSize = 128 * r;
            
            // 3. For i = 0 to p - 1 do: B[i] = SMix(B[i], 2r, N)
            // Can be parallelized strictly speaking, but sequential is fine for p=1 (Aegis default)
            // To handle p > 1, we treat B as chunks.
            
            var threads = new Task[p];
            for (int i = 0; i < p; i++)
            {
                int index = i;
                // Running synchronously for simplicity/safety unless p is huge (Aegis uses p=1)
                SMix(B, index * blockSize, r, N);
            }

            // 4. DK = PBKDF2(P, B, 1, dkLen)
            return PBKDF2(password, B, 1, dkLen);
        }

        private static byte[] PBKDF2(byte[] password, byte[] salt, int iterations, int outputBytes)
        {
            using (var derive = new Rfc2898DeriveBytes(password, salt, iterations, HashAlgorithmName.SHA256))
            {
                return derive.GetBytes(outputBytes);
            }
        }

        private static void SMix(byte[] B, int offset, int r, int N)
        {
            int blockSize = 128 * r;
            uint[] V = new uint[N * 32 * r];
            uint[] X = new uint[32 * r];
            uint[] Y = new uint[32 * r]; 
            
            // X = B[i]
            BlockCopy(B, offset, X, 0, blockSize);

            for (int i = 0; i < N; i++)
            {
                // V[i] = X
                Array.Copy(X, 0, V, i * (32 * r), 32 * r);
                BlockMix(X, Y, r);
            }

            for (int i = 0; i < N; i++)
            {
                // j = Integerify(X) mod N
                int j = (int)(X[X.Length - 16] & (N - 1)); // Integerify: X[2r-1] usually. X is uint array. 32*r len. Integerify(X) is result of interpreting X[2r-1] as integer.
                // In generic scrypt: Integerify(B) interprets the last 64 bytes of chunk B as little-endian integer.
                // Here X is uint array.
                // The integerify is actually picking indices.
                // Let's look at strict definition or reference impl for C#.
                
                // Usually: j = X[2*r - 1] & (N-1) if X treated as r 64-byte blocks?
                // Wait, X is 128*r bytes.
                // RFC 7914: Integerify(X): result of interpreting the last 64 bytes of X as a little-endian integer.
                // We only need the first 4 bytes of that to do mod N (since N fits in 32 bits).
                
                // X length in uints is 32*r.
                // Last 64 bytes -> starts at byte: 128*r - 64.
                // X array index: (128*r - 64) / 4 = 32*r - 16.
                // So X[32*r - 16] is the first 4 bytes of the last 64 bytes.
                
                j = (int)(X[(32 * r) - 16] & (N - 1));

                // T = X xor V[j]
                Xor(X, V, j * (32 * r), 32 * r);
                BlockMix(X, Y, r);
            }

            // B[i] = X
            BlockCopy(X, 0, B, offset, blockSize);
        }

        private static void BlockMix(uint[] B, uint[] Y, int r)
        {
            // B is input (2r 64-byte blocks)
            // Y is temp
            // X = B[2r - 1]
            
            // In uint array: B len = 32*r.
            // 64-byte block = 16 uints.
            // Total 2r "64-byte blocks".
            // B[2r-1] is the last 16 uints.
            
            int blockUints = 16;
            uint[] X = new uint[blockUints];
            Array.Copy(B, (2 * r - 1) * blockUints, X, 0, blockUints);

            for (int i = 0; i < 2 * r; i++)
            {
                // T = X xor B[i]
                Xor(X, B, i * blockUints, blockUints);
                
                // Y[i] = Salsa20/8(T)
                // We put result into Y. 
                // Logic: BlockMix puts output into Y in specifically shuffled order?
                // Scrypt BlockMix:
                // For i = 0 to 2r-1:
                //   T = X ^ B[i]
                //   Y[i] = Salsa20/8(T)
                //   X = Y[i]
                // 
                // But we need to place them in Y specifically?
                // No, RFC says: Output Y. And Y is then re-mapped to B?
                // Wait, typically BlockMix output is Y, where Y[2i] is one thing, Y[2i+1] another.
                // Actual placement in output array:
                // Y[i] = ...
                // The standard B output order is: Y[0], Y[2], ... Y[2r-2], Y[1], Y[3] ... Y[2r-1]
                
                Salsa20_8(X); // X is modified in place to be Salsa output
                
                // Map to Y
                int destIndex = (i % 2 == 0) ? (i / 2) : (r + i / 2);
                Array.Copy(X, 0, Y, destIndex * blockUints, blockUints);
            }
            
            // Swap B and Y (Copy Y back to B)
            Array.Copy(Y, 0, B, 0, Y.Length);
        }

        private static void Xor(uint[] dest, uint[] src, int srcOffset, int len)
        {
            for (int i = 0; i < len; i++)
                dest[i] ^= src[srcOffset + i];
        }

        private static void BlockCopy(byte[] src, int srcOffset, uint[] dest, int destOffset, int countBytes)
        {
            Buffer.BlockCopy(src, srcOffset, dest, destOffset * 4, countBytes);
        }

        private static void BlockCopy(uint[] src, int srcOffset, byte[] dest, int destOffset, int countBytes)
        {
            Buffer.BlockCopy(src, srcOffset * 4, dest, destOffset, countBytes);
        }
        
        // Salsa20/8 Implementation
        private static void Salsa20_8(uint[] B)
        {
            // B is 16 uints (64 bytes)
            uint x0 = B[0], x1 = B[1], x2 = B[2], x3 = B[3];
            uint x4 = B[4], x5 = B[5], x6 = B[6], x7 = B[7];
            uint x8 = B[8], x9 = B[9], x10 = B[10], x11 = B[11];
            uint x12 = B[12], x13 = B[13], x14 = B[14], x15 = B[15];
            
            for (int i = 0; i < 8; i += 2)
            {
                // Round 1
                x4 ^= R(x0 + x12, 7);  x8 ^= R(x4 + x0, 9);
                x12 ^= R(x8 + x4, 13); x0 ^= R(x12 + x8, 18);
                x9 ^= R(x5 + x1, 7);   x13 ^= R(x9 + x5, 9);
                x1 ^= R(x13 + x9, 13); x5 ^= R(x1 + x13, 18);
                x14 ^= R(x10 + x6, 7); x2 ^= R(x14 + x10, 9);
                x6 ^= R(x2 + x14, 13); x10 ^= R(x6 + x2, 18);
                x3 ^= R(x15 + x11, 7); x7 ^= R(x3 + x15, 9);
                x11 ^= R(x7 + x3, 13); x15 ^= R(x11 + x7, 18);
                
                // Round 2
                x1 ^= R(x0 + x3, 7);   x2 ^= R(x1 + x0, 9);
                x3 ^= R(x2 + x1, 13);  x0 ^= R(x3 + x2, 18);
                x6 ^= R(x5 + x4, 7);   x7 ^= R(x6 + x5, 9);
                x4 ^= R(x7 + x6, 13);  x5 ^= R(x4 + x7, 18);
                x11 ^= R(x10 + x9, 7); x8 ^= R(x11 + x10, 9);
                x9 ^= R(x8 + x11, 13); x10 ^= R(x9 + x8, 18);
                x12 ^= R(x15 + x14, 7); x13 ^= R(x12 + x15, 9);
                x14 ^= R(x13 + x12, 13); x15 ^= R(x14 + x13, 18);
            }

            B[0] += x0; B[1] += x1; B[2] += x2; B[3] += x3;
            B[4] += x4; B[5] += x5; B[6] += x6; B[7] += x7;
            B[8] += x8; B[9] += x9; B[10] += x10; B[11] += x11;
            B[12] += x12; B[13] += x13; B[14] += x14; B[15] += x15;
        }

        private static uint R(uint a, int b)
        {
            return (a << b) | (a >> (32 - b));
        }
    }
}
