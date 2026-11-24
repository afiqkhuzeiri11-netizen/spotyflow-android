package com.spotyflow.android.data.remote

import com.spotyflow.android.data.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-time Lyrics Translation (free Google Translate GTX endpoint + RAM cache)
 * and offline Hangul / Kana / Cyrillic Romanization helper.
 */
object LyricsEnhancer {
    private val translationCache = ConcurrentHashMap<String, List<String>>()

    suspend fun translateLines(lines: List<LyricLine>, targetLang: String = "id"): List<String> =
        withContext(Dispatchers.IO) {
            if (lines.isEmpty()) return@withContext emptyList()
            val joined = lines.joinToString("\n") { it.text.replace("\n", " ") }
            val cacheKey = "$targetLang|${joined.hashCode()}"
            translationCache[cacheKey]?.let { return@withContext it }

            return@withContext try {
                val encoded = URLEncoder.encode(joined, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()

                SpotyHttpClient.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val raw = resp.body?.string() ?: return@withContext emptyList()
                    val outer = JSONArray(raw)
                    val segments = outer.optJSONArray(0) ?: return@withContext emptyList()
                    val sb = StringBuilder()
                    for (i in 0 until segments.length()) {
                        val seg = segments.optJSONArray(i) ?: continue
                        sb.append(seg.optString(0))
                    }
                    val translated = sb.toString().split("\n")
                    val aligned = List(lines.size) { idx -> translated.getOrNull(idx)?.trim().orEmpty() }
                    translationCache[cacheKey] = aligned
                    aligned
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

    fun romanizeText(text: String): String {
        if (text.isBlank()) return ""
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            when (ch.code) {
                // Korean Hangul Syllables (AC00..D7A3)
                in 0xAC00..0xD7A3 -> sb.append(romanizeHangulSyllable(ch.code))
                // Japanese Hiragana & Katakana
                in 0x3041..0x3096 -> sb.append(KANA_MAP[ch] ?: ch)
                in 0x30A1..0x30F6 -> sb.append(KANA_MAP[(ch.code - 0x60).toChar()] ?: ch)
                // Cyrillic
                in 0x0400..0x04FF -> sb.append(CYRILLIC_MAP[ch.lowercaseChar()] ?: ch)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun needsRomanization(lines: List<LyricLine>): Boolean {
        return lines.any { line ->
            line.text.any { ch ->
                val c = ch.code
                c in 0xAC00..0xD7A3 || c in 0x3040..0x30FF || c in 0x0400..0x04FF
            }
        }
    }

    private val INITIALS = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )
    private val MEDIALS = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    )
    private val FINALS = arrayOf(
        "", "k", "k", "k", "n", "n", "n", "t", "l", "l", "l", "l", "l", "l", "l", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t"
    )

    private fun romanizeHangulSyllable(code: Int): String {
        val offset = code - 0xAC00
        val init = offset / 588
        val med = (offset % 588) / 28
        val fin = offset % 28
        return INITIALS[init] + MEDIALS[med] + FINALS[fin]
    }

    private val KANA_MAP = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "wo", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po"
    )

    private val CYRILLIC_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ы' to "y", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )
}
