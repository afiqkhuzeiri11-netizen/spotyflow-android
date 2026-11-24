package com.spotyflow.android.data.local

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.spotyflow.android.data.AmbientPalette
import com.spotyflow.android.ui.theme.SpotyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Extracts soft Pure-White-compatible pastel ambient colors from album artwork on Dispatchers.Default.
 * Blends extracted swatch with Color.White (0.22f) so the canvas always remains crisp Pure White.
 */
object CoverPaletteExtractor {
    private val cache = ConcurrentHashMap<String, AmbientPalette>()

    suspend fun extract(context: Context, coverUrl: String, fallbackAccent: Color): AmbientPalette {
        if (coverUrl.isBlank()) {
            return buildSoftPalette(fallbackAccent, SpotyColors.DefaultSkyCrystal, SpotyColors.DefaultSoftLilac)
        }
        cache[coverUrl]?.let { return it }

        return withContext(Dispatchers.Default) {
            try {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .size(160)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).maximumColorCount(12).generate()
                    val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) }
                        ?: palette.dominantSwatch?.rgb?.let { Color(it) }
                        ?: fallbackAccent
                    val lightVibrant = palette.lightVibrantSwatch?.rgb?.let { Color(it) }
                        ?: palette.mutedSwatch?.rgb?.let { Color(it) }
                        ?: Color(0xFF38BDF8)
                    val lightMuted = palette.lightMutedSwatch?.rgb?.let { Color(it) }
                        ?: palette.darkVibrantSwatch?.rgb?.let { Color(it) }
                        ?: Color(0xFFA855F7)

                    val resolved = buildSoftPalette(vibrant, lightVibrant, lightMuted)
                    if (cache.size > 60) cache.clear()
                    cache[coverUrl] = resolved
                    resolved
                } else {
                    buildSoftPalette(fallbackAccent, Color(0xFF38BDF8), Color(0xFFA855F7))
                }
            } catch (_: Throwable) {
                buildSoftPalette(fallbackAccent, Color(0xFF38BDF8), Color(0xFFA855F7))
            }
        }
    }

    private fun buildSoftPalette(primary: Color, secondary: Color, tertiary: Color): AmbientPalette {
        val soft1 = lerp(Color.White, primary, 0.24f).copy(alpha = 0.44f)
        val soft2 = lerp(Color.White, secondary, 0.22f).copy(alpha = 0.42f)
        val soft3 = lerp(Color.White, tertiary, 0.20f).copy(alpha = 0.36f)
        return AmbientPalette(
            accent = primary,
            orbPrimary = soft1,
            orbSecondary = soft2,
            orbTertiary = soft3
        )
    }
}
