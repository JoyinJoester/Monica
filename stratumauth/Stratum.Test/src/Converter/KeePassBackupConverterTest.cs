// Copyright (C) 2025 jmh
// SPDX-License-Identifier: GPL-3.0-only

using System;
using System.Linq;
using System.Threading.Tasks;
using Stratum.Core;
using Stratum.Core.Backup;
using Stratum.Core.Converter;
using Moq;
using Stratum.Test.Converter.Fixture;
using Xunit;

namespace Stratum.Test.Converter
{
    public class KeePassBackupConverterTest : IClassFixture<KeePassBackupFixture>
    {
        private readonly KeePassBackupFixture _keePassBackupFixture;
        private readonly KeePassBackupConverter _keePassBackupConverter;

        public KeePassBackupConverterTest(KeePassBackupFixture keePassBackupFixture)
        {
            _keePassBackupFixture = keePassBackupFixture;

            var iconResolver = new Mock<IIconResolver>();
            iconResolver.Setup(r => r.FindServiceKeyByName(It.IsAny<string>())).Returns("icon");

            _keePassBackupConverter = new KeePassBackupConverter(iconResolver.Object);
        }
        
        private async Task AssertConvertAsyncOk(byte[] data)
        {
            var result = await _keePassBackupConverter.ConvertAsync(data, "test");

            Assert.Empty(result.Failures);

            Assert.Equal(7, result.Backup.Authenticators.Count());
            Assert.Null(result.Backup.Categories);
            Assert.Null(result.Backup.AuthenticatorCategories);
            Assert.Null(result.Backup.CustomIcons);
        }

        private async Task AssertConvertAsyncWrongPassword(byte[] data)
        {
            await Assert.ThrowsAsync<BackupPasswordException>(() =>
                _keePassBackupConverter.ConvertAsync(data, "testing"));
        }

        [Fact]
        public Task ConvertAsync_Aes256Argon2d_Ok()
        {
            return AssertConvertAsyncOk(_keePassBackupFixture.Aes256Argon2dData);
        }

        [Fact]
        public Task ConvertAsync_Aes256Argon2d_WrongPassword()
        {
            return AssertConvertAsyncWrongPassword(_keePassBackupFixture.Aes256Argon2dData);
        }
        
        [Fact]
        public Task ConvertAsync_Aes256Argon2Id_Ok()
        {
            return AssertConvertAsyncOk(_keePassBackupFixture.Aes256Argon2IdData);
        }

        [Fact]
        public Task ConvertAsync_Aes256Argon2Id_WrongPassword()
        {
            return AssertConvertAsyncWrongPassword(_keePassBackupFixture.Aes256Argon2IdData);
        }
        
        [Fact]
        public Task ConvertAsync_Aes256AesKdf_Ok()
        {
            return AssertConvertAsyncOk(_keePassBackupFixture.Aes256AesKdfData);
        }
        
        [Fact]
        public Task ConvertAsync_Aes256AesKdf_WrongPassword()
        {
            return AssertConvertAsyncWrongPassword(_keePassBackupFixture.Aes256AesKdfData);
        }
        
        [Fact]
        public Task ConvertAsync_ChaCha20Argon2d_Ok()
        {
            return AssertConvertAsyncOk(_keePassBackupFixture.ChaCha20Argon2dData);
        }
        
        [Fact]
        public Task ConvertAsync_ChaCha20Argon2d_WrongPassword()
        {
            return AssertConvertAsyncWrongPassword(_keePassBackupFixture.ChaCha20Argon2dData);
        }
        
        [Fact]
        public Task ConvertAsync_TwoFishArgon2d_Ok()
        {
            return AssertConvertAsyncOk(_keePassBackupFixture.TwoFishArgon2dData);
        }
        
        [Fact]
        public Task ConvertAsync_TwoFishArgon2d_WrongPassword()
        {
            return AssertConvertAsyncWrongPassword(_keePassBackupFixture.TwoFishArgon2dData);
        }
        
        [Fact]
        public Task ConvertAsync_NoCompression()
        {
            return AssertConvertAsyncOk(_keePassBackupFixture.NoCompressionData);
        }
        
        [Fact]
        public Task ConvertAsync_MultiBlock()
        {
            return AssertConvertAsyncOk(_keePassBackupFixture.BigData);
        }
        
        [Fact]
        public Task ConvertAsync_GroupsAndSubgroups()
        {
            return AssertConvertAsyncOk(_keePassBackupFixture.GroupsData);
        }
        
        [Fact]
        public Task ConvertAsync_NoRecycleBin()
        {
            return AssertConvertAsyncOk(_keePassBackupFixture.NoRecycleBinData);
        }

        [Fact]
        public async Task ConvertAsync_Kdbx3()
        {
            await Assert.ThrowsAsync<ArgumentException>(() =>
                _keePassBackupConverter.ConvertAsync(_keePassBackupFixture.Kdbx3Data, "test"));
        }

        [Fact]
        public async Task ConvertAsync_InRecycleBin()
        {
            var result = await _keePassBackupConverter.ConvertAsync(_keePassBackupFixture.RecycleBinData, "test");

            Assert.Empty(result.Failures);
            Assert.Empty(result.Backup.Authenticators);
            Assert.Null(result.Backup.Categories);
            Assert.Null(result.Backup.AuthenticatorCategories);
            Assert.Null(result.Backup.CustomIcons);
        }
    }
}