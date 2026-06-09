package takagi.ru.monica.passkey

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyDiscoverabilityResolverTest {

    @Test
    fun `boolean requireResidentKey true is discoverable`() {
        assertTrue(
            PasskeyDiscoverabilityResolver.isDiscoverableAuthenticatorSelection(
                residentKey = null,
                requireResidentKey = true,
            )
        )
    }

    @Test
    fun `string requireResidentKey true is discoverable`() {
        assertTrue(
            PasskeyDiscoverabilityResolver.isDiscoverableAuthenticatorSelection(
                residentKey = null,
                requireResidentKey = "true",
            )
        )
    }

    @Test
    fun `string requireResidentKey true tolerates case and whitespace`() {
        assertTrue(
            PasskeyDiscoverabilityResolver.isDiscoverableAuthenticatorSelection(
                residentKey = null,
                requireResidentKey = " TRUE ",
            )
        )
    }

    @Test
    fun `residentKey required is discoverable`() {
        assertTrue(
            PasskeyDiscoverabilityResolver.isDiscoverableAuthenticatorSelection(
                residentKey = "required",
                requireResidentKey = null,
            )
        )
    }

    @Test
    fun `residentKey preferred is discoverable`() {
        assertTrue(
            PasskeyDiscoverabilityResolver.isDiscoverableAuthenticatorSelection(
                residentKey = "preferred",
                requireResidentKey = null,
            )
        )
    }

    @Test
    fun `residentKey value tolerates case and whitespace`() {
        assertTrue(
            PasskeyDiscoverabilityResolver.isDiscoverableAuthenticatorSelection(
                residentKey = " Preferred ",
                requireResidentKey = null,
            )
        )
    }

    @Test
    fun `false and discouraged are not discoverable`() {
        assertFalse(
            PasskeyDiscoverabilityResolver.isDiscoverableAuthenticatorSelection(
                residentKey = "discouraged",
                requireResidentKey = false,
            )
        )
        assertFalse(
            PasskeyDiscoverabilityResolver.isDiscoverableAuthenticatorSelection(
                residentKey = null,
                requireResidentKey = "false",
            )
        )
        assertFalse(
            PasskeyDiscoverabilityResolver.isDiscoverableAuthenticatorSelection(
                residentKey = null,
                requireResidentKey = "",
            )
        )
    }

    @Test
    fun `missing authenticator selection is not discoverable`() {
        assertFalse(PasskeyDiscoverabilityResolver.isDiscoverableCreationRequest("{}"))
    }

    @Test
    fun `root request json is parsed for discoverable state`() {
        val requestJson = """
            {
              "authenticatorSelection": {
                "requireResidentKey": "true"
              }
            }
        """.trimIndent()

        assertTrue(PasskeyDiscoverabilityResolver.isDiscoverableCreationRequest(requestJson))
    }

    @Test
    fun `publicKey wrapped request json is parsed for discoverable state`() {
        val requestJson = """
            {
              "publicKey": {
                "authenticatorSelection": {
                  "residentKey": "preferred"
                }
              }
            }
        """.trimIndent()

        assertTrue(PasskeyDiscoverabilityResolver.isDiscoverableCreationRequest(requestJson))
    }

    @Test
    fun `credProps boolean true is requested`() {
        assertTrue(PasskeyDiscoverabilityResolver.isCredPropsValueRequested(true))
    }

    @Test
    fun `credProps string true is requested`() {
        assertTrue(PasskeyDiscoverabilityResolver.isCredPropsValueRequested("true"))
        assertTrue(PasskeyDiscoverabilityResolver.isCredPropsValueRequested(" TRUE "))
    }

    @Test
    fun `credProps false values are not requested`() {
        assertFalse(PasskeyDiscoverabilityResolver.isCredPropsValueRequested(false))
        assertFalse(PasskeyDiscoverabilityResolver.isCredPropsValueRequested("false"))
        assertFalse(PasskeyDiscoverabilityResolver.isCredPropsValueRequested(""))
        assertFalse(PasskeyDiscoverabilityResolver.isCredPropsValueRequested(null))
    }

    @Test
    fun `root request json is parsed for credProps`() {
        val requestJson = """
            {
              "extensions": {
                "credProps": true
              }
            }
        """.trimIndent()

        assertTrue(PasskeyDiscoverabilityResolver.isCredPropsRequested(requestJson))
    }

    @Test
    fun `publicKey wrapped request json is parsed for credProps`() {
        val requestJson = """
            {
              "publicKey": {
                "extensions": {
                  "credProps": "true"
                }
              }
            }
        """.trimIndent()

        assertTrue(PasskeyDiscoverabilityResolver.isCredPropsRequested(requestJson))
    }
}
