package takagi.ru.monica.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData

class TotpDataResolverTest {

    @Test
    fun bitwardenSteamUriIsParsedAsSteamGuardToken() {
        val rawSteamSharedSecret = "QUJDREVGR0hJSktMTU5PUFFSU1Q="

        val parsed = TotpDataResolver.fromAuthenticatorKey(
            rawKey = "steam://$rawSteamSharedSecret",
            fallbackIssuer = "Steam",
            fallbackAccountName = "account"
        )

        requireNotNull(parsed)
        assertEquals(OtpType.STEAM, parsed.otpType)
        assertEquals(5, parsed.digits)
        assertEquals(30, parsed.period)
        assertEquals("SHA1", parsed.algorithm)
        assertEquals("IFBEGRCFIZDUQSKKJNGE2TSPKBIVEU2U", parsed.secret)
        assertEquals(rawSteamSharedSecret, parsed.steamSharedSecretBase64)

        val generated = TotpGenerator.generateOtp(parsed, currentSeconds = 1_700_000_000L)
        assertEquals(5, generated.length)
        assertTrue(generated.all { it in "23456789BCDFGHJKMNPQRTVWXY" })
    }

    @Test
    fun yandexOtpPreservesEightDigitConfiguration() {
        val generated = TotpGenerator.generateOtp(
            TotpData(
                secret = "JBSWY3DPEHPK3PXP",
                issuer = "Yandex",
                otpType = OtpType.YANDEX,
                digits = 8
            ),
            currentSeconds = 1_700_000_000L
        )

        assertEquals(8, generated.length)
        assertTrue(generated.all { it.isDigit() })
    }

    @Test
    fun steamGuardDataSyncsBackToBitwardenSteamUri() {
        val rawSteamSharedSecret = "QUJDREVGR0hJSktMTU5PUFFSU1Q="

        val payload = TotpDataResolver.toBitwardenPayload(
            title = "Steam",
            data = TotpData(
                secret = "IFBEGRCFIZDUQSKKJNGE2TSPKBIVEU2U",
                issuer = "Steam",
                otpType = OtpType.STEAM,
                digits = 5,
                steamSharedSecretBase64 = rawSteamSharedSecret
            )
        )

        assertEquals("steam://$rawSteamSharedSecret", payload)
    }

    @Test
    fun bitwardenSteamUriPreservesPlusInBase64Secret() {
        val rawSteamSharedSecret = "ABCDEFGHIJKLMNOPQRS+"

        val parsed = TotpDataResolver.fromAuthenticatorKey(
            rawKey = "steam://$rawSteamSharedSecret",
            fallbackIssuer = "Steam"
        )

        requireNotNull(parsed)
        assertEquals(OtpType.STEAM, parsed.otpType)
        assertEquals("AAIIGECRQ4QJFCZQ2OHUCFF6", parsed.secret)
        assertEquals(rawSteamSharedSecret, parsed.steamSharedSecretBase64)
    }

    @Test
    fun normalizeTotpDataRepairsCorruptedSteamSecret() {
        // 模拟旧版本 bug：steam:// URI 整个被误存为 secret
        val corrupted = TotpData(
            secret = "steam://QUJDREVGR0hJSktMTU5PUFFSU1Q=",
            issuer = "Steam",
            otpType = OtpType.TOTP,
            digits = 6
        )

        val repaired = TotpDataResolver.normalizeTotpData(corrupted)

        assertEquals(OtpType.STEAM, repaired.otpType)
        assertEquals(5, repaired.digits)
        assertEquals("SHA1", repaired.algorithm)
        assertEquals("IFBEGRCFIZDUQSKKJNGE2TSPKBIVEU2U", repaired.secret)
        assertEquals("QUJDREVGR0hJSktMTU5PUFFSU1Q=", repaired.steamSharedSecretBase64)

        val generated = TotpGenerator.generateOtp(repaired, currentSeconds = 1_700_000_000L)
        assertEquals(5, generated.length)
        assertTrue(generated.all { it in "23456789BCDFGHJKMNPQRTVWXY" })
    }

    @Test
    fun normalizeTotpDataRepairsCorruptedOtpAuthSecret() {
        // 模拟 otpauth URI 被误存为 secret 的情况
        val corrupted = TotpData(
            secret = "otpauth://totp/Steam:user?secret=IFBEGRCFIZDUQSKKJNGE2TSPKBIVEU2U&issuer=Steam",
            issuer = "Steam",
            otpType = OtpType.TOTP,
            digits = 6
        )

        val repaired = TotpDataResolver.normalizeTotpData(corrupted)

        assertEquals(OtpType.STEAM, repaired.otpType)
        assertEquals(5, repaired.digits)
        assertEquals("IFBEGRCFIZDUQSKKJNGE2TSPKBIVEU2U", repaired.secret)
    }

    @Test
    fun normalizeTotpDataDoesNotModifyCleanData() {
        // 正常数据不应被修改
        val clean = TotpData(
            secret = "IFBEGRCFIZDUQSKKJNGE2TSPKBIVEU2U",
            issuer = "Steam",
            otpType = OtpType.STEAM,
            digits = 5,
            period = 30,
            algorithm = "SHA1"
        )

        val result = TotpDataResolver.normalizeTotpData(clean)

        assertEquals(clean.secret, result.secret)
        assertEquals(clean.otpType, result.otpType)
        assertEquals(clean.digits, result.digits)
    }

    @Test
    fun steamGuardBase32WithoutOriginalSharedSecretDoesNotSyncAsSteamScheme() {
        val payload = TotpDataResolver.toBitwardenPayload(
            title = "Steam",
            data = TotpData(
                secret = "IFBEGRCFIZDUQSKKJNGE2TSPKBIVEU2U",
                issuer = "Steam",
                otpType = OtpType.STEAM,
                digits = 5
            )
        )

        assertTrue(payload.startsWith("otpauth://totp/"))
        assertTrue(payload.contains("encoder=steam"))
    }
}
