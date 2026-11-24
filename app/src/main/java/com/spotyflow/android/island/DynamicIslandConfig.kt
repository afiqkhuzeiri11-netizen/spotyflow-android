package com.spotyflow.android.island

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class DynamicIslandStyle(val label: String, val subtitle: String) {
    IPHONE("iPhone Classic", "OLED hitam pekat menyatu kamera"),
    LIQUID_GLASS("Liquid Glass", "Kaca kristal berkilau iOS 26"),
    CLEAR_GLASS("Kaca Bening", "Transparan jernih bingkai prisma"),
    ADAPTIVE_COVER("Warna Cover", "Gradien adaptif sampul album"),
    MINIMAL_PILL("Minimal Ramping", "Kapsul tipis super bersih"),
    NEON_AURA("Neon Aura", "Gelap elegan tepi bercahaya")
}

enum class IslandLyricMode(val label: String) {
    REPLACE_TITLE("Lirik di kapsul ringkas"),
    SUBTITLE_EXPANDED("Judul di ringkas, lirik di mekar")
}

enum class IslandCoverShape(val label: String) {
    CIRCLE("Lingkaran"),
    ROUNDED("Membulat"),
    SQUARE("Kotak")
}

data class DynamicIslandConfig(
    val enabled: Boolean = true,
    val style: DynamicIslandStyle = DynamicIslandStyle.LIQUID_GLASS,
    val showLyrics: Boolean = true,
    val lyricMode: IslandLyricMode = IslandLyricMode.REPLACE_TITLE,
    val showVisualizer: Boolean = true,
    val coverShape: IslandCoverShape = IslandCoverShape.CIRCLE,
    val showInsideSpotyFlow: Boolean = false,
    val offsetYDp: Float = 8f,
    val offsetXDp: Float = 0f,
    val compactWidthDp: Float = 210f,
    val compactHeightDp: Float = 36f,
    val cornerRadiusDp: Float = 24f,
    val autoCollapseSec: Int = 5
)

object DynamicIslandConfigStore {
    private const val PREF_NAME = "spotyflow_island_cfg_v3"
    private const val KEY_JSON = "cfg_json"

    private val _config = MutableStateFlow(DynamicIslandConfig())
    val config: StateFlow<DynamicIslandConfig> = _config.asStateFlow()

    fun load(context: Context): DynamicIslandConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null) ?: return _config.value
        val parsed = try {
            val j = JSONObject(raw)
            DynamicIslandConfig(
                enabled = j.optBoolean("enabled", true),
                style = runCatching { DynamicIslandStyle.valueOf(j.optString("style", "LIQUID_GLASS")) }
                    .getOrDefault(DynamicIslandStyle.LIQUID_GLASS),
                showLyrics = j.optBoolean("showLyrics", true),
                lyricMode = runCatching { IslandLyricMode.valueOf(j.optString("lyricMode", "REPLACE_TITLE")) }
                    .getOrDefault(IslandLyricMode.REPLACE_TITLE),
                showVisualizer = j.optBoolean("showVisualizer", true),
                coverShape = runCatching { IslandCoverShape.valueOf(j.optString("coverShape", "CIRCLE")) }
                    .getOrDefault(IslandCoverShape.CIRCLE),
                showInsideSpotyFlow = j.optBoolean("showInsideSpotyFlow", false),
                offsetYDp = j.optDouble("offsetYDp", 8.0).toFloat().coerceIn(-12f, 48f),
                offsetXDp = j.optDouble("offsetXDp", 0.0).toFloat().coerceIn(-80f, 80f),
                compactWidthDp = j.optDouble("compactWidthDp", 210.0).toFloat().coerceIn(140f, 280f),
                compactHeightDp = j.optDouble("compactHeightDp", 36.0).toFloat().coerceIn(30f, 48f),
                cornerRadiusDp = j.optDouble("cornerRadiusDp", 24.0).toFloat().coerceIn(12f, 32f),
                autoCollapseSec = j.optInt("autoCollapseSec", 5).coerceIn(3, 10)
            )
        } catch (_: Throwable) {
            DynamicIslandConfig()
        }
        _config.value = parsed
        return parsed
    }

    fun save(context: Context, cfg: DynamicIslandConfig) {
        _config.value = cfg
        val j = JSONObject().apply {
            put("enabled", cfg.enabled)
            put("style", cfg.style.name)
            put("showLyrics", cfg.showLyrics)
            put("lyricMode", cfg.lyricMode.name)
            put("showVisualizer", cfg.showVisualizer)
            put("coverShape", cfg.coverShape.name)
            put("showInsideSpotyFlow", cfg.showInsideSpotyFlow)
            put("offsetYDp", cfg.offsetYDp.toDouble())
            put("offsetXDp", cfg.offsetXDp.toDouble())
            put("compactWidthDp", cfg.compactWidthDp.toDouble())
            put("compactHeightDp", cfg.compactHeightDp.toDouble())
            put("cornerRadiusDp", cfg.cornerRadiusDp.toDouble())
            put("autoCollapseSec", cfg.autoCollapseSec)
        }
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, j.toString())
            .apply()
    }

    fun reset(context: Context) {
        save(context, DynamicIslandConfig())
    }
}
