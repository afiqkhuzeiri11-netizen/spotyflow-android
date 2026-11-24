package com.spotyflow.android.data

import androidx.compose.ui.graphics.Color

data class LyricLine(
    val timeSec: Int,
    val text: String
)

data class TrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val mood: String,
    val accentColor: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val coverUrl: String,
    val lyrics: List<LyricLine>
)

data class SamplePlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val trackCount: Int,
    val accentColor: Color,
    val secondaryColor: Color
)

data class MoodCategory(
    val id: String,
    val label: String,
    val subtitle: String,
    val glowColor: Color,
    val accentColor: Color
)

object SampleCatalog {
    val moodPills: List<String> = listOf(
        "Semua",
        "Santai",
        "Fokus",
        "Semangat",
        "Perjalanan",
        "Malam"
    )

    val searchFilters: List<String> = listOf(
        "Semua",
        "Lagu",
        "Artis",
        "Album",
        "Playlist"
    )

    val moodCards: List<MoodCategory> = listOf(
        MoodCategory(
            id = "Santai",
            label = "Santai",
            subtitle = "Akustik lembut & hangat",
            glowColor = Color(0x48A7F3D0),
            accentColor = Color(0xFF10B981)
        ),
        MoodCategory(
            id = "Fokus",
            label = "Fokus",
            subtitle = "Instrumental & lo-fi bersih",
            glowColor = Color(0x48BAE6FD),
            accentColor = Color(0xFF0284C7)
        ),
        MoodCategory(
            id = "Semangat",
            label = "Semangat",
            subtitle = "Ritme cepat penuh energi",
            glowColor = Color(0x48FDE68A),
            accentColor = Color(0xFFD97706)
        ),
        MoodCategory(
            id = "Perjalanan",
            label = "Perjalanan",
            subtitle = "Teman jalan sore hari",
            glowColor = Color(0x48C7D2FE),
            accentColor = Color(0xFF4F46E5)
        ),
        MoodCategory(
            id = "Malam",
            label = "Malam",
            subtitle = "Tenang di bawah cahaya kota",
            glowColor = Color(0x48E9D5FF),
            accentColor = Color(0xFF7C3AED)
        ),
        MoodCategory(
            id = "Pop",
            label = "Pop Pilihan",
            subtitle = "Hits terbaru paling jernih",
            glowColor = Color(0x48FBCFE8),
            accentColor = Color(0xFFDB2777)
        )
    )

    val tracks: List<TrackItem> = listOf(
        TrackItem(
            id = "sf-01",
            title = "Cahaya Pagi di Kaca",
            artist = "Nadhif Basalamah",
            album = "Ruang Bening",
            durationSec = 212,
            mood = "Santai",
            accentColor = Color(0xFF10B981),
            orbPrimary = Color(0x42A7F3D0),
            orbSecondary = Color(0x46BAE6FD),
            orbTertiary = Color(0x38E9D5FF),
            coverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=600&q=80",
            lyrics = listOf(
                LyricLine(0, "Langit putih membuka pagi yang tenang"),
                LyricLine(14, "Embun menari di ujung jendela kaca"),
                LyricLine(29, "Setiap nada mengalir pelan tanpa tergesa"),
                LyricLine(45, "Menjaga detak waktu agar tetap terasa nyata"),
                LyricLine(62, "Di antara cahaya yang lembut menyapa"),
                LyricLine(79, "Kita temukan hangat di ruang sederhana"),
                LyricLine(98, "Biarkan melodi ini menemani langkahmu"),
                LyricLine(118, "Hingga senja pulang membawa rindu yang baru"),
                LyricLine(140, "Tenanglah, semua baik-baik saja hari ini"),
                LyricLine(165, "Musik berbisik halus di sisi hati")
            )
        ),
        TrackItem(
            id = "sf-02",
            title = "Seribu Langkah Senja",
            artist = "Tulus",
            album = "Manusia & Langit",
            durationSec = 238,
            mood = "Perjalanan",
            accentColor = Color(0xFF0EA5E9),
            orbPrimary = Color(0x45BAE6FD),
            orbSecondary = Color(0x40C7D2FE),
            orbTertiary = Color(0x38A7F3D0),
            coverUrl = "https://images.unsplash.com/photo-1557672172-298e090bd0f1?auto=format&fit=crop&w=600&q=80",
            lyrics = listOf(
                LyricLine(0, "Jalanan panjang memantulkan warna langit"),
                LyricLine(16, "Angin sore membawa cerita yang tak sempat terucap"),
                LyricLine(34, "Seribu langkah telah kita lewati bersama"),
                LyricLine(52, "Setiap persimpangan mengajarkan arti pulang"),
                LyricLine(72, "Lihat ke depan, cahaya masih menunggu"),
                LyricLine(94, "Irama lembut menyatu dengan deru kota"),
                LyricLine(120, "Kita melaju ringan menuju esok yang cerah")
            )
        ),
        TrackItem(
            id = "sf-03",
            title = "Biru Kristal",
            artist = "Bernadya",
            album = "Hening Semesta",
            durationSec = 196,
            mood = "Malam",
            accentColor = Color(0xFF6366F1),
            orbPrimary = Color(0x42C7D2FE),
            orbSecondary = Color(0x40E9D5FF),
            orbTertiary = Color(0x38BAE6FD),
            coverUrl = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?auto=format&fit=crop&w=600&q=80",
            lyrics = listOf(
                LyricLine(0, "Malam turun perlahan di balik tirai putih"),
                LyricLine(15, "Lampu kota berkilau seperti kristal bening"),
                LyricLine(32, "Suara gitar akustik mengisi ruang sunyi"),
                LyricLine(50, "Mengulang kenangan yang tak pernah pudar"),
                LyricLine(70, "Dalam biru kristal yang begitu jernih"),
                LyricLine(92, "Hati belajar berdamai dengan waktu")
            )
        ),
        TrackItem(
            id = "sf-04",
            title = "Ruang Fokus 120Hz",
            artist = "Lofi Jakarta",
            album = "Pure Minimalist",
            durationSec = 184,
            mood = "Fokus",
            accentColor = Color(0xFF059669),
            orbPrimary = Color(0x44A7F3D0),
            orbSecondary = Color(0x4099F6E4),
            orbTertiary = Color(0x36BAE6FD),
            coverUrl = "https://images.unsplash.com/photo-1614850523296-d8c1af93d400?auto=format&fit=crop&w=600&q=80",
            lyrics = listOf(
                LyricLine(0, "Instrumental fokus — tarik napas perlahan"),
                LyricLine(24, "Ketukan halus menjaga pikiran tetap jernih"),
                LyricLine(56, "Aliran nada stabil untuk menyelesaikan karya"),
                LyricLine(96, "Harmoni hangat di latar putih yang tenang")
            )
        ),
        TrackItem(
            id = "sf-05",
            title = "Berlari Menuju Matahari",
            artist = "Hindia",
            album = "Energi Baru",
            durationSec = 224,
            mood = "Semangat",
            accentColor = Color(0xFFF59E0B),
            orbPrimary = Color(0x44FDE68A),
            orbSecondary = Color(0x40FED7AA),
            orbTertiary = Color(0x38A7F3D0),
            coverUrl = "https://images.unsplash.com/photo-1618005198919-d3d4b5a92ead?auto=format&fit=crop&w=600&q=80",
            lyrics = listOf(
                LyricLine(0, "Pagi ini kita mulai dengan energi penuh"),
                LyricLine(18, "Jangan ragu melangkah lebih cepat dari kemarin"),
                LyricLine(36, "Detak bass mengangkat semangat yang sempat reda"),
                LyricLine(58, "Berlari menuju matahari yang baru terbit"),
                LyricLine(84, "Hari ini milikmu, buat jadi berarti")
            )
        ),
        TrackItem(
            id = "sf-06",
            title = "Sisa Rasa di Beranda",
            artist = "Mahalini",
            album = "Fabula Acoustic",
            durationSec = 248,
            mood = "Santai",
            accentColor = Color(0xFFEC4899),
            orbPrimary = Color(0x40FBCFE8),
            orbSecondary = Color(0x40E9D5FF),
            orbTertiary = Color(0x38BAE6FD),
            coverUrl = "https://images.unsplash.com/photo-1604076913837-52ab5629fba9?auto=format&fit=crop&w=600&q=80",
            lyrics = listOf(
                LyricLine(0, "Di beranda sore yang teduh dan sunyi"),
                LyricLine(20, "Masih tersimpan hangat nada yang dulu kita nyanyikan"),
                LyricLine(42, "Piano lembut mengalir menyentuh ingatan"),
                LyricLine(68, "Setiap kata tetap indah saat kembali terdengar"),
                LyricLine(96, "Biarkan rasa itu tinggal dengan damai")
            )
        ),
        TrackItem(
            id = "sf-07",
            title = "Lampu Jalan Tengah Malam",
            artist = "Sal Priadi",
            album = "Markers & Stars",
            durationSec = 219,
            mood = "Malam",
            accentColor = Color(0xFF8B5CF6),
            orbPrimary = Color(0x42DDD6FE),
            orbSecondary = Color(0x40BAE6FD),
            orbTertiary = Color(0x38FBCFE8),
            coverUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?auto=format&fit=crop&w=600&q=80",
            lyrics = listOf(
                LyricLine(0, "Lampu jalan menyala satu per satu"),
                LyricLine(19, "Menjadi saksi langkah kita pulang larut malam"),
                LyricLine(41, "Udara sejuk membawa ketenangan yang jujur"),
                LyricLine(66, "Dan musik tetap hidup sampai mata terpejam")
            )
        ),
        TrackItem(
            id = "sf-08",
            title = "Angin Pantai Selatan",
            artist = "Pamungkas",
            album = "Solipsism Pure",
            durationSec = 205,
            mood = "Perjalanan",
            accentColor = Color(0xFF14B8A6),
            orbPrimary = Color(0x4499F6E4),
            orbSecondary = Color(0x44BAE6FD),
            orbTertiary = Color(0x38A7F3D0),
            coverUrl = "https://images.unsplash.com/photo-1618172193622-ae2d025f4032?auto=format&fit=crop&w=600&q=80",
            lyrics = listOf(
                LyricLine(0, "Jendela terbuka menyambut angin laut"),
                LyricLine(17, "Garis cakrawala membentang luas di depan mata"),
                LyricLine(38, "Musik mengalir ringan seirama ombak"),
                LyricLine(63, "Kita nikmati setiap detik perjalanan ini")
            )
        )
    )

    val playlists: List<SamplePlaylist> = listOf(
        SamplePlaylist(
            id = "pl-1",
            title = "Pagi Bening & Akustik",
            subtitle = "Pilihan santai untuk memulai hari",
            trackCount = 18,
            accentColor = Color(0xFF10B981),
            secondaryColor = Color(0xFF38BDF8)
        ),
        SamplePlaylist(
            id = "pl-2",
            title = "Fokus Kerja 120 FPS",
            subtitle = "Instrumental bersih tanpa distraksi",
            trackCount = 24,
            accentColor = Color(0xFF0284C7),
            secondaryColor = Color(0xFF6366F1)
        ),
        SamplePlaylist(
            id = "pl-3",
            title = "Perjalanan Malam Kota",
            subtitle = "Synth hangat dan vokal lembut",
            trackCount = 15,
            accentColor = Color(0xFF7C3AED),
            secondaryColor = Color(0xFFEC4899)
        )
    )

    val initialSearchHistory: List<String> = listOf(
        "Nadhif Basalamah",
        "Tulus",
        "Bernadya",
        "Lofi Fokus"
    )
}
