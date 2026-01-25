// Copyright (C) 2023 jmh
// SPDX-License-Identifier: GPL-3.0-only

using System;
using Stratum.Core;
using Stratum.Core.Util;
using Xunit;

namespace Stratum.Test.Util
{
    public class SecretUtilTest
    {
        [Theory]
        [InlineData("abcdefg", "ABCDEFG", AuthenticatorType.Totp, AuthenticatorType.Hotp,
            AuthenticatorType.SteamOtp)] // Make uppercase
        [InlineData("ABCD EFG", "ABCDEFG", AuthenticatorType.Totp, AuthenticatorType.Hotp, AuthenticatorType.SteamOtp,
            AuthenticatorType.MobileOtp)] // Remove spaces
        [InlineData("ABCD-EFG", "ABCDEFG", AuthenticatorType.Totp, AuthenticatorType.Hotp, AuthenticatorType.SteamOtp,
            AuthenticatorType.MobileOtp)] // Remove hyphens
        [InlineData("abcdefg", "abcdefg", AuthenticatorType.MobileOtp)] // Preserve case 1/2
        [InlineData("ABCDEFG", "ABCDEFG", AuthenticatorType.MobileOtp)] // Preserve case 2/2
        public void Normalise(string input, string output, params AuthenticatorType[] types)
        {
            foreach (var type in types)
            {
                Assert.Equal(output, SecretUtil.Normalise(input, type));
            }
        }

        [Theory]
        [InlineData(null, false, AuthenticatorType.Totp, AuthenticatorType.Hotp, AuthenticatorType.SteamOtp,
            AuthenticatorType.MobileOtp)] // Missing 1/2
        [InlineData("", false, AuthenticatorType.Totp, AuthenticatorType.Hotp, AuthenticatorType.SteamOtp,
            AuthenticatorType.MobileOtp)] // Missing 2/2
        [InlineData("ABCDEFG", true, AuthenticatorType.Totp, AuthenticatorType.Hotp,
            AuthenticatorType.SteamOtp)] // Valid base32 (uppercase)
        [InlineData("abcdefg", true, AuthenticatorType.Totp, AuthenticatorType.Hotp,
            AuthenticatorType.SteamOtp)] // Valid base32 (lowercase)
        [InlineData("abcdefg==", true, AuthenticatorType.Totp, AuthenticatorType.Hotp,
            AuthenticatorType.SteamOtp)] // Valid base32 (padding)
        [InlineData("a", false, AuthenticatorType.Totp, AuthenticatorType.Hotp,
            AuthenticatorType.SteamOtp)] // Too few bytes base32
        [InlineData("ABCDEFGHIJKLMNOP1", false, AuthenticatorType.Totp, AuthenticatorType.Hotp,
            AuthenticatorType.SteamOtp)] // Invalid base32
        [InlineData("7AC61D4736F51A2B", true, AuthenticatorType.MobileOtp)] // Valid (uppercase)
        [InlineData("7ac61d4736f51a2b", true, AuthenticatorType.MobileOtp)] // Valid (lowercase)
        [InlineData("abcdef1234", false, AuthenticatorType.MobileOtp)] // Too few characters
        [InlineData("aaaaaaaaaaaaaaa", false, AuthenticatorType.YandexOtp)] // Too few Yandex bytes
        public void Validate(string secret, bool isValid, params AuthenticatorType[] types)
        {
            foreach (var type in types)
            {
                if (!isValid)
                {
                    Assert.Throws<ArgumentException>(delegate { SecretUtil.Validate(secret, type); });
                }
            }
        }
    }
}