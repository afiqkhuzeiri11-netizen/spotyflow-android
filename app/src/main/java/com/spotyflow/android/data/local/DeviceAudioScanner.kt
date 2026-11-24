package com.spotyflow.android.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.deterministicAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queries device local music library (MP3/FLAC/M4A/WAV) via MediaStore.Audio.Media.
 */
object DeviceAudioScanner {
    private val ALBUM_ART_BASE_URI = Uri.parse("content://media/external/audio/albumart")

    suspend fun scanDeviceAudio(context: Context, limit: Int = 500): List<TrackItem> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<TrackItem>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 15000"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext() && result.size < limit) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol).orEmpty().ifBlank { "Audio Lokal" }
                        val artistRaw = cursor.getString(artistCol).orEmpty()
                        val artist = if (artistRaw.isBlank() || artistRaw == "<unknown>") "Artis Lokal" else artistRaw
                        val album = cursor.getString(albumCol).orEmpty()
                        val albumId = cursor.getLong(albumIdCol)
                        val durationSec = (cursor.getLong(durCol) / 1000L).toInt().coerceAtLeast(1)

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()
                        val artUri = if (albumId > 0L) {
                            ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId).toString()
                        } else {
                            ""
                        }

                        result.add(
                            TrackItem(
                                id = "local_$id",
                                videoId = "",
                                title = title,
                                artist = artist,
                                album = album,
                                durationSec = durationSec,
                                coverUrl = artUri,
                                localUri = contentUri,
                                accentColor = deterministicAccent(title)
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
                // Permission denied or MediaStore unavailable
            }
            result
        }
}
