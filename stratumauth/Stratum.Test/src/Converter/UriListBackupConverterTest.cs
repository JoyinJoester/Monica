// Copyright (C) 2023 jmh
// SPDX-License-Identifier: GPL-3.0-only

using System;
using System.Linq;
using System.Threading.Tasks;
using Stratum.Core;
using Stratum.Core.Converter;
using Moq;
using Stratum.Test.Converter.Fixture;
using Xunit;

namespace Stratum.Test.Converter
{
    public class UriListBackupConverterTest : IClassFixture<UriListBackupFixture>
    {
        private readonly UriListBackupFixture _uriListBackupFixture;
        private readonly UriListBackupConverter _uriListBackupConverter;

        public UriListBackupConverterTest(UriListBackupFixture uriListBackupFixture)
        {
            _uriListBackupFixture = uriListBackupFixture;

            var iconResolver = new Mock<IIconResolver>();
            iconResolver.Setup(r => r.FindServiceKeyByName(It.IsAny<string>())).Returns("icon");

            _uriListBackupConverter = new UriListBackupConverter(iconResolver.Object);
        }

        [Fact]
        public async Task ConvertAsync_ok()
        {
            var result = await _uriListBackupConverter.ConvertAsync(_uriListBackupFixture.Data);

            Assert.Empty(result.Failures);

            Assert.Equal(7, result.Backup.Authenticators.Count());
            Assert.Null(result.Backup.Categories);
            Assert.Null(result.Backup.AuthenticatorCategories);
            Assert.Null(result.Backup.CustomIcons);
        }
        
        [Fact]
        public async Task ConvertAsync_unknown()
        {
            await Assert.ThrowsAsync<ArgumentException>(() => _uriListBackupConverter.ConvertAsync("testing 123"u8.ToArray()));
        }

        [Fact]
        public async Task ConvertAsync_pin()
        {
            var data = """
                        motp://MOTP:Username?secret=7ac61d4736f51a2b
                        otpauth://yaotp/Yandex%3AUsername?secret=AAAAAAVSVVVVVVVVVSASVVVVVSSSDSD&issuer=Yandex&pin_length=8\n
                        """u8;
            
            var result = await _uriListBackupConverter.ConvertAsync(data.ToArray());
            
            Assert.Equal(2, result.Failures.Count);
            
            Assert.Empty(result.Backup.Authenticators);
            Assert.Null(result.Backup.Categories);
            Assert.Null(result.Backup.AuthenticatorCategories);
            Assert.Null(result.Backup.CustomIcons);
        }
    }
}