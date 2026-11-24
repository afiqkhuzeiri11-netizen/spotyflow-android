package com.spotyflow.android.widget

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class WidgetStyle(val label: String) {
    GLASS("Kaca"),
    CLEAR_GLASS("Kaca bening"),
    LIGHT("Terang"),
    DARK("Gelap"),
    COVER_COLOR("Warna cover")
}

enum class WidgetAccentMode(val label: String) {
    COVER("Dari cover"),
    CUSTOM("Warna kustom")
}

enum class WidgetCoverShape(val label: String) {
    ROUNDED("Membulat"),
    CIRCLE("Lingkaran"),
    SQUARE("Kotak")
}

enum class WidgetTextSize(val label: String, val scale: Float) {
    SMALL("Kecil", 0.90f),
    MEDIUM("Sedang", 1.0f),
    LARGE("Besar", 1.12f)
}

enum class WidgetPlayBtnStyle(val label: String) {
    ROUND("Bulat"),
    PLAIN("Polos")
}

data class WidgetConfig(
    val style: WidgetStyle = WidgetStyle.GLASS,
    val accentMode: WidgetAccentMode = WidgetAccentMode.COVER,
    val customAccentArgb: Int = 0xFF10B981.toInt(),
    val glassShinePct: Int = 70, // 0..100
    val glassThicknessPct: Int = 60, // 0..100
    val cornerRadiusPct: Int = 66, // 0..100
    val coverShape: WidgetCoverShape = WidgetCoverShape.ROUNDED,
    val textSize: WidgetTextSize = WidgetTextSize.MEDIUM,
    val playBtnStyle: WidgetPlayBtnStyle = WidgetPlayBtnStyle.ROUND,
    val showCover: Boolean = true,
    val showProgress: Boolean = true,
    val showTime: Boolean = true,
    val showLike: Boolean = true,
    val showPrevNext: Boolean = true,
    val showStreak: Boolean = true
)

object WidgetConfigStore {
    private const val PREF_NAME = "sf_widget_prefs"
    private const val KEY_JSON = "widget_cfg_json"

    val ACCENT_PRESETS: List<Int> = listOf(
        0xFF10B981.toInt(),
        0xFF0EA5E9.toInt(),
        0xFF6366F1.toInt(),
        0xFF8B5CF6.toInt(),
        0xFFEC4899.toInt(),
        0xFFF97316.toInt(),
        0xFFEAB308.toInt(),
        0xFF0F172A.toInt()
    )

    private val _config = MutableStateFlow(WidgetConfig())
    val config: StateFlow<WidgetConfig> = _config.asStateFlow()

    fun load(context: Context): WidgetConfig {
        val raw = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return _config.value
        val parsed = try {
            val j = JSONObject(raw)
            WidgetConfig(
                style = runCatching { WidgetStyle.valueOf(j.optString("style", "GLASS")) }.getOrDefault(WidgetStyle.GLASS),
                accentMode = runCatching { WidgetAccentMode.valueOf(j.optString("accentMode", "COVER")) }.getOrDefault(WidgetAccentMode.COVER),
                customAccentArgb = j.optInt("customAccentArgb", 0xFF10B981.toInt()),
                glassShinePct = j.optInt("glassShinePct", 70).coerceIn(0, 100),
                glassThicknessPct = j.optInt("glassThicknessPct", 60).coerceIn(0, 100),
                cornerRadiusPct = j.optInt("cornerRadiusPct", 66).coerceIn(0, 100),
                coverShape = runCatching { WidgetCoverShape.valueOf(j.optString("coverShape", "ROUNDED")) }.getOrDefault(WidgetCoverShape.ROUNDED),
                textSize = runCatching { WidgetTextSize.valueOf(j.optString("textSize", "MEDIUM")) }.getOrDefault(WidgetTextSize.MEDIUM),
                playBtnStyle = runCatching { WidgetPlayBtnStyle.valueOf(j.optString("playBtnStyle", "ROUND")) }.getOrDefault(WidgetPlayBtnStyle.ROUND),
                showCover = j.optBoolean("showCover", true),
                showProgress = j.optBoolean("showProgress", true),
                showTime = j.optBoolean("showTime", true),
                showLike = j.optBoolean("showLike", true),
                showPrevNext = j.optBoolean("showPrevNext", true),
                showStreak = j.optBoolean("showStreak", true)
            )
        } catch (_: Throwable) {
            WidgetConfig()
        }
        _config.value = parsed
        return parsed
    }

    fun save(context: Context, cfg: WidgetConfig) {
        _config.value = cfg
        val j = JSONObject().apply {
            put("style", cfg.style.name)
            put("accentMode", cfg.accentMode.name)
            put("customAccentArgb", cfg.customAccentArgb)
            put("glassShinePct", cfg.glassShinePct)
            put("glassThicknessPct", cfg.glassThicknessPct)
            put("cornerRadiusPct", cfg.cornerRadiusPct)
            put("coverShape", cfg.coverShape.name)
            put("textSize", cfg.textSize.name)
            put("playBtnStyle", cfg.playBtnStyle.name)
            put("showCover", cfg.showCover)
            put("showProgress", cfg.showProgress)
            put("showTime", cfg.showTime)
            put("showLike", cfg.showLike)
            put("showPrevNext", cfg.showPrevNext)
            put("showStreak", cfg.showStreak)
        }
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, j.toString())
            .apply()
    }
}
