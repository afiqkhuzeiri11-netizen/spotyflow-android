package com.spotyflow.android.data

import androidx.compose.ui.graphics.Color

object SampleCatalog {
    val moodPills: List<String> = listOf(
        "Semua",
        "Santai",
        "Fokus",
        "Semangat",
        "Perjalanan",
        "Malam"
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
            id = "Pop Indonesia",
            label = "Pop Pilihan",
            subtitle = "Hits terbaru paling jernih",
            glowColor = Color(0x48FBCFE8),
            accentColor = Color(0xFFDB2777)
        )
    )
}
