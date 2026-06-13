package takagi.ru.monica.ui.cardwallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.data.model.CardBrand

fun CardBrand.iconLabel(): String =
    when (this) {
        CardBrand.VISA -> "VISA"
        CardBrand.MASTERCARD -> "MC"
        CardBrand.AMERICAN_EXPRESS -> "AMEX"
        CardBrand.DINERS_CLUB -> "DINERS"
        CardBrand.DISCOVER -> "DISC"
        CardBrand.JCB -> "JCB"
        CardBrand.UNIONPAY -> "UP"
        CardBrand.MAESTRO -> "MAE"
        CardBrand.MIR -> "MIR"
        CardBrand.RUPAY -> "RUPAY"
        CardBrand.ELO -> "ELO"
        CardBrand.DANKORT -> "DK"
        CardBrand.MADA -> "MADA"
        CardBrand.MEEZA -> "MEEZA"
        CardBrand.TROY -> "TROY"
        CardBrand.UATP -> "UATP"
        CardBrand.FORBRUGSFORENINGEN -> "FF"
        CardBrand.UNKNOWN -> ""
    }

private fun CardBrand.shouldDrawInterlockingMark(): Boolean =
    this == CardBrand.MASTERCARD || this == CardBrand.MAESTRO

private val CardBrandIconLightContainer = Color.White
private val CardBrandIconDarkContainer = Color(0xFFE7E9ED)
private val CardBrandIconContent = Color(0xFF111111)

@Composable
fun CardBrandIcon(
    brand: CardBrand,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val frameContentColor = CardBrandIconContent
    val logo = brand.libraryLogo()
    if (logo != null) {
        CardBrandIconFrame(
            isDarkTheme = isDarkTheme,
            modifier = modifier
        ) {
            CardBrandLibraryLogo(
                logo = logo,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        return
    }

    val label = brand.iconLabel()
    if (label.isBlank()) {
        CardBrandIconFrame(
            isDarkTheme = isDarkTheme,
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = brand.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                tint = frameContentColor
            )
        }
        return
    }

    CardBrandIconFrame(
        isDarkTheme = isDarkTheme,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (brand.shouldDrawInterlockingMark()) {
                InterlockingCirclesMark(tint = frameContentColor)
            }
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 3.dp),
                color = frameContentColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = when {
                        label.length <= 2 -> 10.sp
                        label.length <= 4 -> 9.sp
                        else -> 7.sp
                    },
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CardBrandIconFrame(
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        color = if (isDarkTheme) CardBrandIconDarkContainer else CardBrandIconLightContainer,
        border = BorderStroke(
            width = 0.5.dp,
            color = if (isDarkTheme) {
                Color.Black.copy(alpha = 0.12f)
            } else {
                Color.Black.copy(alpha = 0.08f)
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun CardBrandLibraryLogo(
    logo: CardBrandLogo,
    modifier: Modifier = Modifier
) {
    val parsedPaths = remember(logo) {
        logo.paths.mapNotNull { logoPath ->
            runCatching {
                ParsedLogoPath(
                    path = PathParser().parsePathString(logoPath.pathData).toPath(),
                    color = logoPath.color
                )
            }.getOrNull()
        }
    }

    if (parsedPaths.isEmpty()) {
        return
    }

    Canvas(modifier = modifier) {
        val scale = minOf(
            size.width / logo.viewportWidth,
            size.height / logo.viewportHeight
        )
        val scaledWidth = logo.viewportWidth * scale
        val scaledHeight = logo.viewportHeight * scale
        val left = (size.width - scaledWidth) / 2f
        val top = (size.height - scaledHeight) / 2f

        withTransform({
            translate(left = left, top = top)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            parsedPaths.forEach { parsedPath ->
                drawPath(
                    path = parsedPath.path,
                    color = parsedPath.color
                )
            }
        }
    }
}

private data class ParsedLogoPath(
    val path: Path,
    val color: Color
)

@Composable
private fun InterlockingCirclesMark(tint: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = size.minDimension * 0.26f
        val centerY = size.height / 2f
        val leftCenter = Offset(size.width * 0.42f, centerY)
        val rightCenter = Offset(size.width * 0.58f, centerY)
        drawCircle(
            color = tint.copy(alpha = 0.18f),
            radius = radius,
            center = leftCenter
        )
        drawCircle(
            color = tint.copy(alpha = 0.18f),
            radius = radius,
            center = rightCenter
        )
        drawCircle(
            color = tint.copy(alpha = 0.36f),
            radius = radius,
            center = leftCenter,
            style = Stroke(width = size.minDimension * 0.04f)
        )
        drawCircle(
            color = tint.copy(alpha = 0.36f),
            radius = radius,
            center = rightCenter,
            style = Stroke(width = size.minDimension * 0.04f)
        )
    }
}
