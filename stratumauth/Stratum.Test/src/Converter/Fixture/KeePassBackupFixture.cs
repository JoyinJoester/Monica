// Copyright (C) 2025 jmh
// SPDX-License-Identifier: GPL-3.0-only

using System.IO;

namespace Stratum.Test.Converter.Fixture
{
    public class KeePassBackupFixture
    {
        public KeePassBackupFixture()
        {
            Aes256Argon2dData = File.ReadAllBytes(Path.Join("data", "keepass.aes256.argon2d.kdbx"));
            Aes256Argon2IdData = File.ReadAllBytes(Path.Join("data", "keepass.aes256.argon2id.kdbx"));
            Aes256AesKdfData = File.ReadAllBytes(Path.Join("data", "keepass.aes256.aeskdf.kdbx"));
            ChaCha20Argon2dData = File.ReadAllBytes(Path.Join("data", "keepass.chacha20.argon2d.kdbx"));
            TwoFishArgon2dData = File.ReadAllBytes(Path.Join("data", "keepass.twofish.argon2d.kdbx"));
            BigData = File.ReadAllBytes(Path.Join("data", "keepass.big.kdbx"));
            GroupsData = File.ReadAllBytes(Path.Join("data", "keepass.groups.kdbx"));
            NoCompressionData = File.ReadAllBytes(Path.Join("data", "keepass.nocompression.kdbx"));
            RecycleBinData = File.ReadAllBytes(Path.Join("data", "keepass.recyclebin.kdbx"));
            NoRecycleBinData = File.ReadAllBytes(Path.Join("data", "keepass.norecyclebin.kdbx"));
            Kdbx3Data = File.ReadAllBytes(Path.Join("data", "keepass.kdbx3.kdbx"));
        }

        public byte[] Aes256Argon2dData { get; }
        public byte[] Aes256Argon2IdData { get; }
        public byte[] Aes256AesKdfData { get; }
        public byte[] ChaCha20Argon2dData { get; }
        public byte[] TwoFishArgon2dData { get; }
        public byte[] BigData { get; }
        public byte[] GroupsData { get; }
        public byte[] NoCompressionData { get; }
        public byte[] RecycleBinData { get; }
        public byte[] NoRecycleBinData { get; }
        public byte[] Kdbx3Data { get; }
    }
}