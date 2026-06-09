package takagi.ru.monica.ui.effects.blur

import android.graphics.Bitmap
import android.graphics.Color
import takagi.ru.monica.data.MonicaBlurIntensity

object MonicaBitmapBlur {
    fun blur(
        input: Bitmap,
        intensity: MonicaBlurIntensity,
        maxDimension: Int = MonicaBlurDefaults.PREVIEW_BITMAP_MAX_DIMENSION,
        restoreOriginalSize: Boolean = true
    ): Bitmap {
        val radius = MonicaBlurDefaults.bitmapRadiusFor(intensity).coerceIn(1, 25)
        val source = downsample(input, maxDimension)
        val width = source.width
        val height = source.height
        val srcPixels = IntArray(width * height)
        val tempPixels = IntArray(width * height)
        val outPixels = IntArray(width * height)

        source.getPixels(srcPixels, 0, width, 0, 0, width, height)
        boxBlurHorizontal(srcPixels, tempPixels, width, height, radius)
        boxBlurVertical(tempPixels, outPixels, width, height, radius)

        val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        blurred.setPixels(outPixels, 0, width, 0, 0, width, height)

        return if (
            restoreOriginalSize &&
            (blurred.width != input.width || blurred.height != input.height)
        ) {
            Bitmap.createScaledBitmap(blurred, input.width, input.height, true)
        } else {
            blurred
        }
    }

    private fun downsample(input: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = maxOf(input.width, input.height)
        if (largestSide <= maxDimension) {
            return input.copy(Bitmap.Config.ARGB_8888, false)
        }
        val scale = maxDimension.toFloat() / largestSide.toFloat()
        val targetWidth = (input.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (input.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(input, targetWidth, targetHeight, true)
    }

    private fun boxBlurHorizontal(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val windowSize = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                for (offset in -radius..radius) {
                    val sampleX = (x + offset).coerceIn(0, width - 1)
                    val color = source[row + sampleX]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                }
                target[row + x] = Color.argb(
                    alpha / windowSize,
                    red / windowSize,
                    green / windowSize,
                    blue / windowSize
                )
            }
        }
    }

    private fun boxBlurVertical(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val windowSize = radius * 2 + 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                for (offset in -radius..radius) {
                    val sampleY = (y + offset).coerceIn(0, height - 1)
                    val color = source[sampleY * width + x]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                }
                target[y * width + x] = Color.argb(
                    alpha / windowSize,
                    red / windowSize,
                    green / windowSize,
                    blue / windowSize
                )
            }
        }
    }
}
