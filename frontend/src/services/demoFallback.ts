import { AUTH_TOKEN_KEY } from '../config/api';
import type {
  AuthTokenResponse,
  ChannelPublic,
  CommentItem,
  CommentLikeResponse,
  CommentListResponse,
  CommentSort,
  LikeResponse,
  ShortsListResponse,
  SubscribeResponse,
  UploadVideoResponse,
  UserPublic,
  VideoCardItem,
  VideoDetailItem,
  VideoListResponse,
} from '../types/api';
import type { FetchVideosQuery } from './api';

const DEMO_STATE_KEY = 'veloxtube_demo_state_v1';

// Valid public CORS-enabled HLS (.m3u8) adaptive streams & MP4 fallbacks
const HLS_MUX = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8';
const HLS_SINTEL = 'https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8';
const HLS_TOS =
  'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8';
const HLS_BIPBOP =
  'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8';

const MP4_BBB =
  'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4';
const MP4_ELEPHANTS =
  'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4';
const MP4_BLAZES =
  'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4';
const MP4_ESCAPES =
  'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4';
const MP4_JOYRIDES =
  'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4';

interface DemoStoredState {
  users: Array<UserPublic & { password?: string }>;
  currentUserId: number | null;
  videos: VideoCardItem[];
  likedVideoIds: number[];
  dislikedVideoIds: number[];
  subscribedChannelIds: number[];
  commentsByVideo: Record<number, CommentItem[]>;
  likedCommentIds: number[];
  nextId: number;
}

const CHANNELS: ChannelPublic[] = [
  {
    id: 1,
    username: 'gadgetlab_id',
    display_name: 'GadgetLab ID',
    handle: '@gadgetlabid',
    avatar_url:
      'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=120&h=120&fit=crop&q=80',
    verified: true,
    subscribers_count: 2400000,
    subscribed_by_me: false,
  },
  {
    id: 2,
    username: 'pixelarena',
    display_name: 'PixelArena Gaming',
    handle: '@pixelarena',
    avatar_url:
      'https://images.unsplash.com/photo-1527980965255-d3b416303d12?w=120&h=120&fit=crop&q=80',
    verified: true,
    subscribers_count: 1850000,
    subscribed_by_me: false,
  },
  {
    id: 3,
    username: 'nadiasessions',
    display_name: 'Nadia Sessions',
    handle: '@nadiasessions',
    avatar_url:
      'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=120&h=120&fit=crop&q=80',
    verified: true,
    subscribers_count: 940000,
    subscribed_by_me: false,
  },
  {
    id: 4,
    username: 'kodenusantara',
    display_name: 'Kode Nusantara',
    handle: '@kodenusantara',
    avatar_url:
      'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=120&h=120&fit=crop&q=80',
    verified: true,
    subscribers_count: 620000,
    subscribed_by_me: false,
  },
  {
    id: 5,
    username: 'dapurrasa',
    display_name: 'Dapur Rasa Studio',
    handle: '@dapurrasa',
    avatar_url:
      'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=120&h=120&fit=crop&q=80',
    verified: true,
    subscribers_count: 1120000,
    subscribed_by_me: false,
  },
];

function hoursAgo(h: number): string {
  return new Date(Date.now() - h * 3600 * 1000).toISOString();
}

function createInitialState(): DemoStoredState {
  const [chTech, chGame, chMusic, chEdu, chFood] = CHANNELS;

  const videos: VideoCardItem[] = [
    {
      id: 1,
      title: 'Review Laptop Tipis 2026: Baterai 22 Jam & Layar OLED 120Hz Tanpa Panas!',
      description:
        'Pengujian menyeluruh performa harian, rendering 4K, dan daya tahan baterai nyata selama 7 hari pemakaian penuh.\n\nBab Video:\n00:00 Intro & Desain\n02:45 Layar OLED 120Hz\n06:10 Uji Suhu & Rendering\n12:20 Kesimpulan',
      category: 'tech',
      tags: ['laptop', 'review', 'oled', 'teknologi', '2026'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_MUX,
      mp4_url: MP4_BBB,
      duration_sec: 942,
      views: 842100,
      likes_count: 38400,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(5),
      channel: chTech,
    },
    {
      id: 2,
      title: 'LIVE: Turnamen Grand Final Valorant Asia — Match Point Map 3!',
      description:
        'Siaran langsung Grand Final dengan bitrate adaptif 1080p60 tanpa delay. Dukung tim favoritmu di kolom komentar!',
      category: 'live',
      tags: ['gaming', 'live', 'esports', 'valorant', 'turnamen'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1542751371-adc38448a05e?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_SINTEL,
      mp4_url: MP4_BLAZES,
      duration_sec: 0,
      views: 64320,
      likes_count: 12800,
      is_short: false,
      is_live: true,
      processing_status: 'ready',
      created_at: hoursAgo(1),
      channel: chGame,
    },
    {
      id: 3,
      title: 'Sesi Akustik Tengah Malam — Lo-Fi & Indie Indonesia (Live Studio 4K)',
      description:
        'Konser mini ruang tertutup direkam menggunakan mikrofon kondensor stereo untuk menemani fokus belajar, kerja, dan coding.',
      category: 'music',
      tags: ['musik', 'akustik', 'lofi', 'indie', 'studio'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_TOS,
      mp4_url: MP4_ESCAPES,
      duration_sec: 2318,
      views: 1254000,
      likes_count: 74200,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(26),
      channel: chMusic,
    },
    {
      id: 4,
      title: 'Arsitektur Video Streaming 60 FPS dengan FastAPI, FFmpeg HLS & React Modern',
      description:
        'Bedah arsitektur transcoding HLS multi-resolusi 720p/480p, HTTP Byte-Range streaming, dan manajemen memori anti-lag di browser & Android.',
      category: 'education',
      tags: ['fastapi', 'python', 'ffmpeg', 'hls', 'react', 'edukasi'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_BIPBOP,
      mp4_url: MP4_JOYRIDES,
      duration_sec: 1685,
      views: 318900,
      likes_count: 19400,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(48),
      channel: chEdu,
    },
    {
      id: 5,
      title: 'Rahasia Ramen Kaldu 14 Jam Ala Tokyo — Empuk & Gurih Tanpa MSG!',
      description:
        'Langkah demi langkah membuat ramen otentik di rumah lengkap dengan chashu bakar dan telur ajitama setengah matang.',
      category: 'culinary',
      tags: ['kuliner', 'ramen', 'masak', 'resep', 'jepang'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_MUX,
      mp4_url: MP4_ELEPHANTS,
      duration_sec: 1120,
      views: 590400,
      likes_count: 29100,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(72),
      channel: chFood,
    },
    {
      id: 6,
      title: 'PODCAST TECH #42: Masa Depan AI Lokal di HP & Laptop Tanpa Kuota Internet',
      description:
        'Obrolan santai tapi mendalam tentang model AI on-device, efisiensi NPU, dan bagaimana developer Indonesia bisa memanfaatkannya.',
      category: 'podcast',
      tags: ['podcast', 'ai', 'teknologi', 'diskusi', 'developer'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_SINTEL,
      mp4_url: MP4_BBB,
      duration_sec: 3420,
      views: 215600,
      likes_count: 11300,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(96),
      channel: chTech,
    },
    {
      id: 7,
      title: 'Setup Meja Kerja Minimalis 2026 — Kabel Tersembunyi & Pencahayaan Sinematik',
      description:
        'Tour meja kerja 100% bebas kabel kusut dengan monitor ultrawide OLED, arm hidrolik, dan lighting ambient otomatis.',
      category: 'tech',
      tags: ['setup', 'desksetup', 'minimalis', 'gadget', 'produktivitas'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1593640408182-31c70c8268f5?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_TOS,
      mp4_url: MP4_ESCAPES,
      duration_sec: 814,
      views: 467200,
      likes_count: 24800,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(120),
      channel: chTech,
    },
    {
      id: 8,
      title: 'Gameplay Ultra 4K 60FPS: Eksplorasi Dunia Cyberpunk dengan Ray Tracing Maksimal',
      description:
        'Uji kartu grafis kelas atas di resolusi 4K native lengkap dengan benchmark frame-time real-time.',
      category: 'gaming',
      tags: ['gaming', '4k', '60fps', 'raytracing', 'benchmark'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_MUX,
      mp4_url: MP4_BLAZES,
      duration_sec: 1455,
      views: 980300,
      likes_count: 51900,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(144),
      channel: chGame,
    },
    {
      id: 9,
      title: 'Belajar TypeScript & React Performance dalam 20 Menit — Anti Re-Render!',
      description:
        'Panduan praktis React.memo, isolasi state video player, debounce search, dan cleanup memory leak.',
      category: 'education',
      tags: ['typescript', 'react', 'frontend', 'coding', 'tutorial'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1516116216624-53e697fedbea?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_BIPBOP,
      mp4_url: MP4_JOYRIDES,
      duration_sec: 1210,
      views: 184500,
      likes_count: 14200,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(168),
      channel: chEdu,
    },
    {
      id: 10,
      title: 'Kopi Susu Gula Aren Cafe-Grade di Rumah: Modal 5 Ribu Rasa 45 Ribu!',
      description:
        'Trik ekstraksi espresso menggunakan moka pot & perbandingan susu evaporasi agar teksturnya creamy maksimal.',
      category: 'culinary',
      tags: ['kopi', 'kuliner', 'barista', 'resep', 'minuman'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_SINTEL,
      mp4_url: MP4_BBB,
      duration_sec: 645,
      views: 412000,
      likes_count: 22600,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(192),
      channel: chFood,
    },
    {
      id: 11,
      title: 'Playlist Fokus Deep Work — Synthwave & Ambient Instrumental (No Ads)',
      description:
        'Musik instrumental hangat tanpa vokal untuk sesi kerja panjang, menulis kode, atau membaca malam hari.',
      category: 'music',
      tags: ['synthwave', 'ambient', 'musik', 'fokus', 'belajar'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_TOS,
      mp4_url: MP4_ESCAPES,
      duration_sec: 3600,
      views: 742000,
      likes_count: 41000,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(216),
      channel: chMusic,
    },
    {
      id: 12,
      title: 'Cerita di Balik Layar: Membangun Aplikasi Video Streaming Multi-Platform',
      description:
        'Podcast santai membahas perjalanan membuat satu codebase untuk Web Vercel, Desktop Tauri, dan Android APK.',
      category: 'podcast',
      tags: ['podcast', 'startup', 'tauri', 'capacitor', 'veloxtube'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1478737270239-2f02b77fc618?w=640&h=360&fit=crop&q=80',
      hls_url: HLS_MUX,
      mp4_url: MP4_JOYRIDES,
      duration_sec: 1920,
      views: 158400,
      likes_count: 9850,
      is_short: false,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(240),
      channel: chEdu,
    },
    // 4 Vertical Shorts
    {
      id: 101,
      title: 'Trik Rahasia Kamera HP Biar Video Terlihat Seperti Film Bioskop! 🎬 #shorts',
      description: 'Atur shutter speed 1/50 dan kunci eksposur untuk hasil video sinematik instan.',
      category: 'tech',
      tags: ['shorts', 'kamera', 'sinematik', 'tips'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=540&h=960&fit=crop&q=80',
      hls_url: HLS_MUX,
      mp4_url: MP4_BLAZES,
      duration_sec: 42,
      views: 1420000,
      likes_count: 128000,
      is_short: true,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(6),
      channel: chTech,
    },
    {
      id: 102,
      title: 'Clutch 1v5 Paling Gila di Detik Terakhir! 😱🔥 #shorts #gaming',
      description: 'Refleks kilat di map terakhir bikin seluruh arena heboh!',
      category: 'gaming',
      tags: ['shorts', 'gaming', 'clutch', 'highlight'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1542751110-97427bbecf20?w=540&h=960&fit=crop&q=80',
      hls_url: HLS_SINTEL,
      mp4_url: MP4_ESCAPES,
      duration_sec: 35,
      views: 2150000,
      likes_count: 194500,
      is_short: true,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(14),
      channel: chGame,
    },
    {
      id: 103,
      title: '30 Detik Masak Nasi Goreng Kampung Wangi Smokey Restoran 🍳 #shorts',
      description: 'Rahasianya ada di suhu wajan sebelum telur masuk!',
      category: 'culinary',
      tags: ['shorts', 'kuliner', 'masak', 'nasigoreng'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1603133872878-684f208fb84b?w=540&h=960&fit=crop&q=80',
      hls_url: HLS_TOS,
      mp4_url: MP4_JOYRIDES,
      duration_sec: 30,
      views: 890000,
      likes_count: 67300,
      is_short: true,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(30),
      channel: chFood,
    },
    {
      id: 104,
      title: 'Chord Gitar Akustik yang Bikin Suasana Langsung Adem 🎸✨ #shorts',
      description: 'Coba progresi Maj7 ini saat jamming malam hari.',
      category: 'music',
      tags: ['shorts', 'musik', 'gitar', 'akustik'],
      thumbnail_url:
        'https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=540&h=960&fit=crop&q=80',
      hls_url: HLS_BIPBOP,
      mp4_url: MP4_BBB,
      duration_sec: 48,
      views: 640000,
      likes_count: 54900,
      is_short: true,
      is_live: false,
      processing_status: 'ready',
      created_at: hoursAgo(42),
      channel: chMusic,
    },
  ];

  const commentsByVideo: Record<number, CommentItem[]> = {
    1: [
      {
        id: 1001,
        video_id: 1,
        content:
          'Streaming HLS-nya mulus banget 60 FPS! Mode miniplayer pas balik ke beranda juga gak bikin video putus sama sekali.',
        likes_count: 1420,
        liked_by_me: false,
        is_pinned: true,
        is_channel_owner: true,
        created_at: hoursAgo(4),
        author: {
          id: chTech.id,
          display_name: chTech.display_name,
          handle: chTech.handle,
          avatar_url: chTech.avatar_url,
          verified: true,
        },
        replies: [
          {
            id: 1002,
            video_id: 1,
            parent_id: 1001,
            content: 'Setuju banget bang, di HP Android juga enteng parah nggak ngelag!',
            likes_count: 184,
            liked_by_me: false,
            is_channel_owner: false,
            created_at: hoursAgo(2),
            author: {
              id: chEdu.id,
              display_name: chEdu.display_name,
              handle: chEdu.handle,
              avatar_url: chEdu.avatar_url,
              verified: true,
            },
          },
        ],
      },
      {
        id: 1003,
        video_id: 1,
        content: 'Akhirnya review yang beneran ngetes suhu & baterai harian selama seminggu penuh!',
        likes_count: 412,
        liked_by_me: false,
        is_pinned: false,
        is_channel_owner: false,
        created_at: hoursAgo(3),
        author: {
          id: chMusic.id,
          display_name: chMusic.display_name,
          handle: chMusic.handle,
          avatar_url: chMusic.avatar_url,
          verified: true,
        },
        replies: [],
      },
    ],
  };

  return {
    users: [],
    currentUserId: null,
    videos,
    likedVideoIds: [],
    dislikedVideoIds: [],
    subscribedChannelIds: [],
    commentsByVideo,
    likedCommentIds: [],
    nextId: 5000,
  };
}

function loadState(): DemoStoredState {
  try {
    const raw = localStorage.getItem(DEMO_STATE_KEY);
    if (!raw) return createInitialState();
    const parsed = JSON.parse(raw) as Partial<DemoStoredState>;
    const base = createInitialState();
    return {
      ...base,
      ...parsed,
      videos: Array.isArray(parsed.videos) && parsed.videos.length > 0 ? parsed.videos : base.videos,
      commentsByVideo: parsed.commentsByVideo || base.commentsByVideo,
    };
  } catch {
    return createInitialState();
  }
}

function saveState(state: DemoStoredState): void {
  try {
    localStorage.setItem(DEMO_STATE_KEY, JSON.stringify(state));
  } catch {
    // Ignore quota errors
  }
}

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => `%${`00${c.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join('')
    );
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function hydrateVideo(state: DemoStoredState, video: VideoCardItem): VideoCardItem {
  const isSub = state.subscribedChannelIds.includes(video.channel.id);
  const subDelta = isSub && !video.channel.subscribed_by_me ? 1 : 0;
  return {
    ...video,
    channel: {
      ...video.channel,
      subscribed_by_me: isSub,
      subscribers_count: video.channel.subscribers_count + subDelta,
    },
  };
}

function getCurrentDemoUser(state: DemoStoredState): UserPublic | null {
  const token = (() => {
    try {
      return localStorage.getItem(AUTH_TOKEN_KEY);
    } catch {
      return null;
    }
  })();
  if (!token) return null;
  if (state.currentUserId) {
    const found = state.users.find((u) => u.id === state.currentUserId);
    if (found) return found;
  }
  return state.users[0] || null;
}

export const demoFallback = {
  register(payload: {
    username: string;
    email: string;
    password: string;
    display_name?: string;
  }): AuthTokenResponse {
    const state = loadState();
    const id = state.nextId++;
    const displayName = (payload.display_name || payload.username).trim();
    const cleanHandle = `@${payload.username.replace(/[^a-zA-Z0-9_]/g, '').toLowerCase() || `user${id}`}`;
    const user: UserPublic & { password?: string } = {
      id,
      username: payload.username.trim(),
      email: payload.email.trim().toLowerCase(),
      display_name: displayName,
      handle: cleanHandle,
      avatar_url: null,
      verified: false,
      provider: 'local',
      subscribers_count: 1,
      created_at: new Date().toISOString(),
      password: payload.password,
    };
    state.users = [user, ...state.users.filter((u) => u.email !== user.email)];
    state.currentUserId = user.id;
    saveState(state);
    return {
      access_token: `demo_token_${user.id}`,
      token_type: 'bearer',
      user,
    };
  },

  login(payload: { identifier: string; password: string }): AuthTokenResponse {
    const state = loadState();
    const idf = payload.identifier.trim().toLowerCase();
    let user = state.users.find(
      (u) => u.email.toLowerCase() === idf || u.username.toLowerCase() === idf
    );
    if (!user) {
      // Allow seamless demo sign-in if user hasn't registered locally yet
      const id = state.nextId++;
      const uname = idf.includes('@') ? idf.split('@')[0] : idf || `user${id}`;
      user = {
        id,
        username: uname,
        email: idf.includes('@') ? idf : `${uname}@veloxtube.app`,
        display_name: uname.charAt(0).toUpperCase() + uname.slice(1),
        handle: `@${uname.replace(/[^a-z0-9_]/g, '') || `user${id}`}`,
        avatar_url: null,
        verified: false,
        provider: 'local',
        subscribers_count: 1,
        created_at: new Date().toISOString(),
      };
      state.users.push(user);
    }
    state.currentUserId = user.id;
    saveState(state);
    return {
      access_token: `demo_token_${user.id}`,
      token_type: 'bearer',
      user,
    };
  },

  loginWithGoogle(credential: string): AuthTokenResponse {
    const state = loadState();
    const payload = decodeJwtPayload(credential);
    const email = String(payload?.email || 'pengguna@gmail.com').toLowerCase();
    const name = String(payload?.name || email.split('@')[0] || 'Pengguna Google');
    const picture = typeof payload?.picture === 'string' ? payload.picture : null;

    let user = state.users.find((u) => u.email.toLowerCase() === email);
    if (!user) {
      const id = state.nextId++;
      const baseUsername = email.split('@')[0].replace(/[^a-zA-Z0-9_]/g, '') || `user${id}`;
      user = {
        id,
        username: baseUsername,
        email,
        display_name: name,
        handle: `@${baseUsername.toLowerCase()}`,
        avatar_url: picture,
        verified: true,
        provider: 'google',
        subscribers_count: 12,
        created_at: new Date().toISOString(),
      };
      state.users.push(user);
    } else if (picture) {
      user.avatar_url = picture;
      user.display_name = name;
    }

    state.currentUserId = user.id;
    saveState(state);
    return {
      access_token: `demo_google_${user.id}`,
      token_type: 'bearer',
      user,
    };
  },

  me(): UserPublic {
    const state = loadState();
    const user = getCurrentDemoUser(state);
    if (!user) {
      throw new Error('Sesi lokal tidak ditemukan.');
    }
    return user;
  },

  listVideos(params: FetchVideosQuery): VideoListResponse {
    const state = loadState();
    let items = state.videos.filter((v) => !v.is_short).map((v) => hydrateVideo(state, v));

    if (params.subscribed_only) {
      items = items.filter((v) => state.subscribedChannelIds.includes(v.channel.id));
    }

    if (params.channel_id) {
      items = items.filter((v) => v.channel.id === params.channel_id);
    }

    const cat = (params.category || 'all').toLowerCase();
    if (cat === 'live') {
      items = items.filter((v) => v.is_live || v.category === 'live');
    } else if (cat !== 'all' && cat !== 'recent') {
      items = items.filter((v) => v.category === cat);
    }

    const search = (params.search || '').trim().toLowerCase();
    if (search) {
      items = items.filter(
        (v) =>
          v.title.toLowerCase().includes(search) ||
          v.description.toLowerCase().includes(search) ||
          v.channel.display_name.toLowerCase().includes(search) ||
          v.tags.some((t) => t.toLowerCase().includes(search))
      );
    }

    const page = Math.max(1, params.page ?? 1);
    const limit = Math.max(1, params.limit ?? 12);
    const start = (page - 1) * limit;
    const paged = items.slice(start, start + limit);

    return {
      items: paged,
      total: items.length,
      page,
      limit,
      has_more: start + paged.length < items.length,
    };
  },

  listShorts(page = 1, limit = 20): ShortsListResponse {
    const state = loadState();
    const shorts = state.videos.filter((v) => v.is_short).map((v) => hydrateVideo(state, v));
    const start = (page - 1) * limit;
    const paged = shorts.slice(start, start + limit);
    return {
      items: paged,
      total: shorts.length,
      page,
      limit,
      has_more: start + paged.length < shorts.length,
    };
  },

  detail(videoId: number): VideoDetailItem {
    const state = loadState();
    const foundIdx = state.videos.findIndex((v) => v.id === videoId);
    const rawVideo = foundIdx >= 0 ? state.videos[foundIdx] : state.videos[0];

    // Atomic-style view increment in local demo storage
    if (foundIdx >= 0) {
      state.videos[foundIdx] = { ...rawVideo, views: rawVideo.views + 1 };
      saveState(state);
    }

    const hydrated = hydrateVideo(state, foundIdx >= 0 ? state.videos[foundIdx] : rawVideo);
    const liked = state.likedVideoIds.includes(hydrated.id);
    const disliked = state.dislikedVideoIds.includes(hydrated.id);
    const comments = state.commentsByVideo[hydrated.id] || [];
    const totalComments = comments.reduce((acc, c) => acc + 1 + c.replies.length, 0);

    const recommendations = state.videos
      .filter((v) => !v.is_short && v.id !== hydrated.id)
      .map((v) => hydrateVideo(state, v));

    return {
      ...hydrated,
      likes_count: hydrated.likes_count + (liked ? 1 : 0),
      dislikes_count: disliked ? 1 : 0,
      liked_by_me: liked,
      disliked_by_me: disliked,
      comments_count: totalComments,
      recommendations,
    };
  },

  react(videoId: number, action: 'like' | 'dislike'): LikeResponse {
    const state = loadState();
    const vid = state.videos.find((v) => v.id === videoId);
    const baseLikes = vid ? vid.likes_count : 0;

    let liked = state.likedVideoIds.includes(videoId);
    let disliked = state.dislikedVideoIds.includes(videoId);

    if (action === 'like') {
      liked = !liked;
      disliked = false;
    } else {
      disliked = !disliked;
      liked = false;
    }

    state.likedVideoIds = liked
      ? Array.from(new Set([...state.likedVideoIds, videoId]))
      : state.likedVideoIds.filter((id) => id !== videoId);
    state.dislikedVideoIds = disliked
      ? Array.from(new Set([...state.dislikedVideoIds, videoId]))
      : state.dislikedVideoIds.filter((id) => id !== videoId);
    saveState(state);

    return {
      video_id: videoId,
      likes_count: baseLikes + (liked ? 1 : 0),
      dislikes_count: disliked ? 1 : 0,
      liked_by_me: liked,
      disliked_by_me: disliked,
    };
  },

  subscribe(channelId: number): SubscribeResponse {
    const state = loadState();
    const currently = state.subscribedChannelIds.includes(channelId);
    const next = !currently;
    state.subscribedChannelIds = next
      ? Array.from(new Set([...state.subscribedChannelIds, channelId]))
      : state.subscribedChannelIds.filter((id) => id !== channelId);
    saveState(state);

    const ch =
      state.videos.find((v) => v.channel.id === channelId)?.channel ||
      CHANNELS.find((c) => c.id === channelId);
    const baseSubs = ch ? ch.subscribers_count : 1000;

    return {
      channel_id: channelId,
      subscribed: next,
      subscribers_count: baseSubs + (next ? 1 : 0),
    };
  },

  comments(videoId: number, sort: CommentSort = 'top'): CommentListResponse {
    const state = loadState();
    const list = [...(state.commentsByVideo[videoId] || [])].map((c) => ({
      ...c,
      liked_by_me: state.likedCommentIds.includes(c.id),
      replies: c.replies.map((r) => ({
        ...r,
        liked_by_me: state.likedCommentIds.includes(r.id),
      })),
    }));

    if (sort === 'newest') {
      list.sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at));
    } else {
      list.sort((a, b) => {
        if (a.is_pinned !== b.is_pinned) return a.is_pinned ? -1 : 1;
        return b.likes_count - a.likes_count;
      });
    }

    const total = list.reduce((acc, c) => acc + 1 + c.replies.length, 0);
    return { video_id: videoId, total, items: list };
  },

  addComment(videoId: number, content: string, parentId?: number | null): CommentItem {
    const state = loadState();
    const user = getCurrentDemoUser(state) || {
      id: 999,
      username: 'tamu',
      email: 'tamu@veloxtube.app',
      display_name: 'Pengguna VeloxTube',
      handle: '@veloxuser',
      avatar_url: null,
      verified: false,
      provider: 'local',
      subscribers_count: 1,
      created_at: new Date().toISOString(),
    };

    const id = state.nextId++;
    const nowIso = new Date().toISOString();
    const author = {
      id: user.id,
      display_name: user.display_name,
      handle: user.handle,
      avatar_url: user.avatar_url,
      verified: user.verified,
    };

    const newItem: CommentItem = {
      id,
      video_id: videoId,
      content: content.trim(),
      likes_count: 0,
      liked_by_me: false,
      is_pinned: false,
      is_channel_owner: false,
      created_at: nowIso,
      author,
      replies: [],
    };

    const existing = state.commentsByVideo[videoId] || [];
    if (parentId) {
      state.commentsByVideo[videoId] = existing.map((c) =>
        c.id === parentId
          ? {
              ...c,
              replies: [
                ...c.replies,
                {
                  id,
                  video_id: videoId,
                  parent_id: parentId,
                  content: content.trim(),
                  likes_count: 0,
                  liked_by_me: false,
                  is_channel_owner: false,
                  created_at: nowIso,
                  author,
                },
              ],
            }
          : c
      );
    } else {
      state.commentsByVideo[videoId] = [newItem, ...existing];
    }

    saveState(state);
    return newItem;
  },

  likeComment(commentId: number): CommentLikeResponse {
    const state = loadState();
    const wasLiked = state.likedCommentIds.includes(commentId);
    const nextLiked = !wasLiked;
    state.likedCommentIds = nextLiked
      ? Array.from(new Set([...state.likedCommentIds, commentId]))
      : state.likedCommentIds.filter((id) => id !== commentId);

    let likesCount = nextLiked ? 1 : 0;
    for (const vidKey of Object.keys(state.commentsByVideo)) {
      const list = state.commentsByVideo[Number(vidKey)];
      for (const c of list) {
        if (c.id === commentId) {
          c.likes_count = Math.max(0, c.likes_count + (nextLiked ? 1 : -1));
          likesCount = c.likes_count;
        }
        for (const r of c.replies) {
          if (r.id === commentId) {
            r.likes_count = Math.max(0, r.likes_count + (nextLiked ? 1 : -1));
            likesCount = r.likes_count;
          }
        }
      }
    }

    saveState(state);
    return {
      comment_id: commentId,
      likes_count: likesCount,
      liked_by_me: nextLiked,
    };
  },

  uploadVideo(formData: FormData): UploadVideoResponse {
    const state = loadState();
    const user = getCurrentDemoUser(state);
    const id = state.nextId++;

    const title = String(formData.get('title') || 'Video Baru VeloxTube').trim();
    const description = String(formData.get('description') || '').trim();
    const category = String(formData.get('category') || 'tech').trim();
    const isShort = String(formData.get('is_short') || 'false') === 'true';
    const rawTags = String(formData.get('tags') || '');
    const tags = rawTags
      .split(',')
      .map((t) => t.trim())
      .filter(Boolean);

    const videoFile = formData.get('video_file');
    const thumbFile = formData.get('thumbnail_file');
    const mp4Url =
      videoFile instanceof File ? URL.createObjectURL(videoFile) : MP4_BBB;
    const thumbnailUrl =
      thumbFile instanceof File
        ? URL.createObjectURL(thumbFile)
        : 'https://images.unsplash.com/photo-1611162617474-5b21e879e113?w=640&h=360&fit=crop&q=80';

    const video: VideoCardItem = {
      id,
      title,
      description,
      category,
      tags,
      thumbnail_url: thumbnailUrl,
      hls_url: null,
      mp4_url: mp4Url,
      duration_sec: isShort ? 30 : 120,
      views: 1,
      likes_count: 0,
      is_short: isShort,
      is_live: false,
      processing_status: 'ready',
      created_at: new Date().toISOString(),
      channel: user
        ? {
            id: user.id,
            username: user.username,
            display_name: user.display_name,
            handle: user.handle,
            avatar_url: user.avatar_url,
            verified: user.verified,
            subscribers_count: user.subscribers_count,
            subscribed_by_me: false,
          }
        : CHANNELS[0],
    };

    state.videos = [video, ...state.videos];
    saveState(state);

    return {
      message: 'Video dipublikasikan di mode lokal (server backend sedang offline).',
      ffmpeg_enabled: false,
      video,
    };
  },
};
