package takagi.ru.monica.ui.cardwallet

import androidx.compose.ui.graphics.vector.PathParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.model.CardBrand

class CardBrandIconTest {

    @Test
    fun mapsBrandsToStableComposeLabels() {
        val expectedLabels = mapOf(
            CardBrand.VISA to "VISA",
            CardBrand.MASTERCARD to "MC",
            CardBrand.AMERICAN_EXPRESS to "AMEX",
            CardBrand.DINERS_CLUB to "DINERS",
            CardBrand.DISCOVER to "DISC",
            CardBrand.JCB to "JCB",
            CardBrand.UNIONPAY to "UP",
            CardBrand.MAESTRO to "MAE",
            CardBrand.MIR to "MIR",
            CardBrand.RUPAY to "RUPAY",
            CardBrand.ELO to "ELO",
            CardBrand.DANKORT to "DK",
            CardBrand.MADA to "MADA",
            CardBrand.MEEZA to "MEEZA",
            CardBrand.TROY to "TROY",
            CardBrand.UATP to "UATP",
            CardBrand.FORBRUGSFORENINGEN to "FF",
            CardBrand.UNKNOWN to ""
        )

        expectedLabels.forEach { (brand, label) ->
            assertEquals(brand.name, label, brand.iconLabel())
        }
    }

    @Test
    fun librarySvgPathsUseComposeParserSyntax() {
        var parsedPathCount = 0

        CardBrand.values().forEach { brand ->
            brand.libraryLogo()?.paths.orEmpty().forEach { logoPath ->
                PathParser().parsePathString(logoPath.pathData)
                parsedPathCount += 1
            }
        }

        assertTrue(parsedPathCount > 0)
    }

    @Test
    fun cardBrandIconAvoidsXmlVectorResourceLoadingOnWalletHotPath() {
        val source = projectFile(
            "src/main/java/takagi/ru/monica/ui/cardwallet/CardBrandIcon.kt"
        ).readText()
        val logoSource = projectFile(
            "src/main/java/takagi/ru/monica/ui/cardwallet/CardBrandLibraryLogo.kt"
        ).readText()

        assertTrue(
            "Card-brand icons should be rendered from library SVG paths in Compose so Android 16 cannot crash in XmlVectorParser.",
            source.contains("fun CardBrandIcon(") &&
                source.contains("Canvas(") &&
                source.contains("libraryLogo") &&
                source.contains("PathParser")
        )
        assertTrue(logoSource.contains("aaronfagan/svg-credit-card-payment-icons"))
        assertFalse(source.contains("painterResource"))
        assertFalse(source.contains("R.drawable.ic_card_brand_"))
        assertFalse(source.contains("iconResId"))
    }

    @Test
    fun cardBrandIconFrameFollowsAppThemeInsteadOfSystemTheme() {
        val source = projectFile(
            "src/main/java/takagi/ru/monica/ui/cardwallet/CardBrandIcon.kt"
        ).readText()

        assertTrue(
            "Card-brand frame should follow Monica's active Material theme so app light mode keeps a white frame even when the system is dark.",
            source.contains("MaterialTheme.colorScheme.surface.luminance()")
        )
        assertFalse(source.contains("isSystemInDarkTheme"))
    }

    @Test
    fun drawableResourcesDoNotContainCardBrandXmlIcons() {
        val drawableDir = projectFile("src/main/res/drawable")
        val staleBrandIcons = drawableDir
            .listFiles { file -> file.name.startsWith("ic_card_brand_") && file.extension == "xml" }
            .orEmpty()

        assertTrue(
            "Card-brand XML vector resources must stay out of the wallet hot path; render them with Compose primitives instead.",
            staleBrandIcons.isEmpty()
        )
    }

    private fun projectFile(relativePath: String): File {
        val fromModule = File(relativePath)
        if (fromModule.exists()) return fromModule
        val fromAndroidRoot = File("app", relativePath)
        if (fromAndroidRoot.exists()) return fromAndroidRoot
        return File("Monica for Android/app", relativePath)
    }
}
