from __future__ import annotations

from datetime import datetime, timedelta, timezone

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import hash_password
from models import Comment, Subscription, User, Video

# Valid public CORS-enabled HLS (.m3u8) adaptive streams & MP4 fallbacks
HLS_MUX = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
HLS_SINTEL = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
HLS_TOS = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
HLS_BIPBOP = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"

MP4_BBB = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
MP4_ELEPHANTS = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
MP4_BLAZES = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
MP4_ESCAPES = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
MP4_JOYRIDES = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"


async def seed_database(db: AsyncSession) -> bool:
    """
    Idempotent initial seeder:
    Populates 5 creator channels, 12 regular videos, 4 vertical Shorts, and realistic comments + replies
    ONLY when the users table is empty.
    """
    existing_count = (await db.execute(select(func.count(User.id)))).scalar_one()
    if existing_count > 0:
        return False

    now = datetime.now(timezone.utc)
    default_pw = hash_password("veloxtube123")

    # 1. Seed 5 Creator Channels
    channels = [
        User(
            username="gadgetlab_id",
            email="gadgetlab@veloxtube.id",
            display_name="GadgetLab ID",
            handle="@gadgetlabid",
            password_hash=default_pw,
            avatar_url="https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=120&h=120&fit=crop&q=80",
            verified=True,
            provider="local",
            base_subscribers=2400000,
            created_at=now - timedelta(days=400),
        ),
        User(
            username="pixelarena",
            email="pixelarena@veloxtube.id",
            display_name="PixelArena Gaming",
            handle="@pixelarena",
            password_hash=default_pw,
            avatar_url="https://images.unsplash.com/photo-1527980965255-d3b416303d12?w=120&h=120&fit=crop&q=80",
            verified=True,
            provider="local",
            base_subscribers=1850000,
            created_at=now - timedelta(days=360),
        ),
        User(
            username="nadiasessions",
            email="nadia@veloxtube.id",
            display_name="Nadia Sessions",
            handle="@nadiasessions",
            password_hash=default_pw,
            avatar_url="https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=120&h=120&fit=crop&q=80",
            verified=True,
            provider="local",
            base_subscribers=940000,
            created_at=now - timedelta(days=310),
        ),
        User(
            username="kodenusantara",
            email="kode@veloxtube.id",
            display_name="Kode Nusantara",
            handle="@kodenusantara",
            password_hash=default_pw,
            avatar_url="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=120&h=120&fit=crop&q=80",
            verified=True,
            provider="local",
            base_subscribers=620000,
            created_at=now - timedelta(days=280),
        ),
        User(
            username="dapurrasa",
            email="dapur@veloxtube.id",
            display_name="Dapur Rasa Studio",
            handle="@dapurrasa",
            password_hash=default_pw,
            avatar_url="https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=120&h=120&fit=crop&q=80",
            verified=True,
            provider="local",
            base_subscribers=1120000,
            created_at=now - timedelta(days=240),
        ),
    ]
    db.add_all(channels)
    await db.flush()

    ch_tech, ch_game, ch_music, ch_edu, ch_food = channels

    # 2. Seed 12 Curated Full-Length Videos
    regular_videos = [
        Video(
            channel_id=ch_tech.id,
            title="Review Laptop Tipis 2026: Baterai 22 Jam & Layar OLED 120Hz Tanpa Panas!",
            description="Pengujian menyeluruh performa harian, rendering 4K, dan daya tahan baterai nyata selama 7 hari pemakaian penuh.",
            category="tech",
            tags="laptop,review,oled,teknologi,2026",
            thumbnail_url="https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_MUX,
            mp4_url=MP4_BBB,
            duration_sec=942,
            views=842100,
            base_likes=38400,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(hours=5),
        ),
        Video(
            channel_id=ch_game.id,
            title="LIVE: Turnamen Grand Final Valorant Asia — Match Point Map 3!",
            description="Siaran langsung Grand Final dengan bitrate adaptif 1080p60 tanpa delay. Dukung tim favoritmu!",
            category="live",
            tags="gaming,live,esports,valorant,turnamen",
            thumbnail_url="https://images.unsplash.com/photo-1542751371-adc38448a05e?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_SINTEL,
            mp4_url=MP4_BLAZES,
            duration_sec=0,
            views=64320,
            base_likes=12800,
            is_short=False,
            is_live=True,
            processing_status="ready",
            created_at=now - timedelta(hours=1),
        ),
        Video(
            channel_id=ch_music.id,
            title="Sesi Akustik Tengah Malam — Lo-Fi & Indie Indonesia (Live Studio 4K)",
            description="Konser mini ruang tertutup direkam menggunakan mikrofon kondensor stereo untuk menemani fokus belajar & coding.",
            category="music",
            tags="musik,akustik,lofi,indie,studio",
            thumbnail_url="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_TOS,
            mp4_url=MP4_ESCAPES,
            duration_sec=2318,
            views=1254000,
            base_likes=74200,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=1),
        ),
        Video(
            channel_id=ch_edu.id,
            title="Arsitektur Video Streaming 60 FPS dengan FastAPI, FFmpeg HLS & React Modern",
            description="Bedah arsitektur transcoding HLS multi-resolusi 720p/480p, HTTP Byte-Range streaming, dan manajemen memori browser.",
            category="education",
            tags="fastapi,python,ffmpeg,hls,react,edukasi",
            thumbnail_url="https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_BIPBOP,
            mp4_url=MP4_JOYRIDES,
            duration_sec=1685,
            views=318900,
            base_likes=19400,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=2),
        ),
        Video(
            channel_id=ch_food.id,
            title="Rahasia Ramen Kaldu 14 Jam Ala Tokyo — Empuk & Gurih Tanpa MSG!",
            description="Langkah demi langkah membuat ramen otentik di rumah lengkap dengan chashu bakar dan telur ajitama setengah matang.",
            category="culinary",
            tags="kuliner,ramen,masak,resep,jepang",
            thumbnail_url="https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_MUX,
            mp4_url=MP4_BBB,
            duration_sec=734,
            views=970400,
            base_likes=45100,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=3),
        ),
        Video(
            channel_id=ch_tech.id,
            title="Perbandingan Kamera HP Flagship Malam Hari: Mana yang Paling Natural?",
            description="Tes foto & video 4K 60fps kondisi minim cahaya di jalanan kota Jakarta tanpa gimbal tambahan.",
            category="tech",
            tags="smartphone,kamera,review,teknologi",
            thumbnail_url="https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_TOS,
            mp4_url=MP4_JOYRIDES,
            duration_sec=1095,
            views=625300,
            base_likes=28600,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=4),
        ),
        Video(
            channel_id=ch_game.id,
            title="Setup Meja Gaming Minimalis Dark Mode 2026 — Rapi Tanpa Kabel Terlihat!",
            description="Tur meja kerja dan gaming dengan monitor OLED 240Hz, audio studio, dan manajemen kabel tersembunyi.",
            category="gaming",
            tags="gaming,setup,pc,oled,minimalis",
            thumbnail_url="https://images.unsplash.com/photo-1598550476439-6847785fcea6?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_SINTEL,
            mp4_url=MP4_BBB,
            duration_sec=812,
            views=489200,
            base_likes=23100,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=5),
        ),
        Video(
            channel_id=ch_music.id,
            title="LIVE Radio Chill Beats 24/7 — Musik Fokus Coding, Belajar & Santai",
            description="Alunan lo-fi hip hop dan ambient synth tanpa jeda yang terus mengalir 24 jam nonstop.",
            category="live",
            tags="live,musik,lofi,chill,belajar",
            thumbnail_url="https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_MUX,
            mp4_url=MP4_ESCAPES,
            duration_sec=0,
            views=19450,
            base_likes=8900,
            is_short=False,
            is_live=True,
            processing_status="ready",
            created_at=now - timedelta(hours=3),
        ),
        Video(
            channel_id=ch_edu.id,
            title="Podcast Tech #48: Kenapa Animasi CSS Sering Patah-Patah & Cara Tembus 60 FPS",
            description="Obrolan santai tapi mendalam soal Composite Layer, GPU acceleration, dan anti-memory leak di aplikasi streaming.",
            category="podcast",
            tags="podcast,frontend,css,performa,60fps",
            thumbnail_url="https://images.unsplash.com/photo-1478737270239-2f02b77fc618?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_BIPBOP,
            mp4_url=MP4_BLAZES,
            duration_sec=2840,
            views=241600,
            base_likes=14700,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=6),
        ),
        Video(
            channel_id=ch_food.id,
            title="Street Food Malam Bandung: 7 Jajanan Legendaris yang Selalu Antre!",
            description="Keliling pusat kuliner malam Bandung dari jam 7 malam sampai tengah malam mencicipi sate, bakmi, dan ronde hangat.",
            category="culinary",
            tags="kuliner,bandung,streetfood,jajanan,vlog",
            thumbnail_url="https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_SINTEL,
            mp4_url=MP4_ESCAPES,
            duration_sec=1410,
            views=1182000,
            base_likes=56800,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=8),
        ),
        Video(
            channel_id=ch_tech.id,
            title="Rakit PC Mini-ITX Super Hening di Casing Sebesar Kotak Sepatu!",
            description="Komponen hemat daya performa tinggi yang muat di ransel namun sanggup melibas game AAA 1440p.",
            category="tech",
            tags="pc,itx,rakitpc,teknologi,hardware",
            thumbnail_url="https://images.unsplash.com/photo-1587202372775-e229f172b9d7?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_TOS,
            mp4_url=MP4_ELEPHANTS,
            duration_sec=1244,
            views=775100,
            base_likes=34900,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=10),
        ),
        Video(
            channel_id=ch_game.id,
            title="100 Hari Bertahan di Dunia Survival Hardcore — Bangun Kota Melayang!",
            description="Tantangan bertahan hidup tanpa mati sekalipun sambil membangun kastil melayang raksasa.",
            category="gaming",
            tags="gaming,survival,hardcore,challenge",
            thumbnail_url="https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=640&h=360&fit=crop&q=80",
            hls_url=HLS_MUX,
            mp4_url=MP4_BBB,
            duration_sec=2650,
            views=1934000,
            base_likes=92300,
            is_short=False,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=12),
        ),
    ]

    # 3. Seed 4 Vertical Shorts Videos (is_short = True)
    shorts_videos = [
        Video(
            channel_id=ch_tech.id,
            title="Trik Rahasia Layar 120Hz Biar Baterai Awet 2x Lipat! #shorts",
            description="Aktifkan pengaturan refresh rate adaptif ini dalam 10 detik.",
            category="tech",
            tags="shorts,tips,hp,baterai",
            thumbnail_url="https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=540&h=960&fit=crop&q=80",
            hls_url=HLS_MUX,
            mp4_url=MP4_BLAZES,
            duration_sec=42,
            views=1540000,
            base_likes=128000,
            is_short=True,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(hours=4),
        ),
        Video(
            channel_id=ch_food.id,
            title="Telur Ajitama Lumer Sempurna Cuma 6 Menit 30 Detik! #shorts",
            description="Rahasia air es dan marinasi kecap asin biar kuning telur lembut seperti di restoran Jepang.",
            category="culinary",
            tags="shorts,masak,ramen,telur",
            thumbnail_url="https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=540&h=960&fit=crop&q=80",
            hls_url=HLS_SINTEL,
            mp4_url=MP4_ESCAPES,
            duration_sec=38,
            views=2190000,
            base_likes=186000,
            is_short=True,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(hours=11),
        ),
        Video(
            channel_id=ch_game.id,
            title="Clutch 1v5 Paling Mustahil di Detik Terakhir! #shorts",
            description="Reaksi instan pakai refleks flick shot 0.2 detik.",
            category="gaming",
            tags="shorts,gaming,clutch,fps",
            thumbnail_url="https://images.unsplash.com/photo-1542751371-adc38448a05e?w=540&h=960&fit=crop&q=80",
            hls_url=HLS_TOS,
            mp4_url=MP4_JOYRIDES,
            duration_sec=29,
            views=980000,
            base_likes=89400,
            is_short=True,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=1),
        ),
        Video(
            channel_id=ch_music.id,
            title="Chord Gitar Melow yang Langsung Bikin Merinding di Malam Hari #shorts",
            description="Coba progresi Maj7 ini untuk intro lagu indie favoritmu.",
            category="music",
            tags="shorts,musik,gitar,akustik",
            thumbnail_url="https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=540&h=960&fit=crop&q=80",
            hls_url=HLS_BIPBOP,
            mp4_url=MP4_BBB,
            duration_sec=48,
            views=740000,
            base_likes=64500,
            is_short=True,
            is_live=False,
            processing_status="ready",
            created_at=now - timedelta(days=2),
        ),
    ]

    db.add_all(regular_videos + shorts_videos)
    await db.flush()

    # 4. Seed Initial Subscriptions
    db.add_all(
        [
            Subscription(subscriber_id=ch_edu.id, channel_id=ch_tech.id),
            Subscription(subscriber_id=ch_edu.id, channel_id=ch_music.id),
            Subscription(subscriber_id=ch_game.id, channel_id=ch_tech.id),
        ]
    )

    # 5. Seed Realistic Top-Level Comments + Nested Replies
    first_vid = regular_videos[0]
    second_vid = regular_videos[2]

    c1 = Comment(
        video_id=first_vid.id,
        user_id=ch_tech.id,
        content="Terima kasih sudah menonton! Menurut kalian fitur mana dari laptop 2026 ini yang paling berguna untuk kerja harian?",
        base_likes=1240,
        is_pinned=True,
        created_at=now - timedelta(hours=4),
    )
    c2 = Comment(
        video_id=first_vid.id,
        user_id=ch_edu.id,
        content="Review-nya jujur dan lengkap banget! Kualitas streaming HLS-nya juga langsung tajam tanpa buffering.",
        base_likes=428,
        is_pinned=False,
        created_at=now - timedelta(hours=3),
    )
    c3 = Comment(
        video_id=first_vid.id,
        user_id=ch_music.id,
        content="Akhirnya ada laptop tipis 120Hz yang adem pas dipakai render audio & video barengan!",
        base_likes=185,
        is_pinned=False,
        created_at=now - timedelta(hours=2),
    )
    c4 = Comment(
        video_id=second_vid.id,
        user_id=ch_food.id,
        content="Enak banget didengerin sambil masak malam-malam. Audio studio-nya jernih banget!",
        base_likes=512,
        is_pinned=False,
        created_at=now - timedelta(hours=14),
    )
    db.add_all([c1, c2, c3, c4])
    await db.flush()

    replies = [
        Comment(
            video_id=first_vid.id,
            user_id=ch_game.id,
            parent_id=c1.id,
            content="Baterai 22 jam-nya sih gila banget, bisa ditinggal charger seharian di kampus!",
            base_likes=96,
            created_at=now - timedelta(hours=3, minutes=20),
        ),
        Comment(
            video_id=first_vid.id,
            user_id=ch_tech.id,
            parent_id=c1.id,
            content="Betul banget! Apalagi bobotnya cuma 1,1 kg jadi ringan masuk tas.",
            base_likes=54,
            created_at=now - timedelta(hours=2, minutes=45),
        ),
        Comment(
            video_id=first_vid.id,
            user_id=ch_food.id,
            parent_id=c2.id,
            content="Setuju, 60 FPS mulus banget nggak bikin mata capek.",
            base_likes=23,
            created_at=now - timedelta(hours=2, minutes=10),
        ),
    ]
    db.add_all(replies)

    await db.commit()
    return True
