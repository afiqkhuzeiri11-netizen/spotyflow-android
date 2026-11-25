# VeloxTube — Full-Stack High-Performance 60 FPS Video Streaming

Aplikasi video streaming multi-platform bergaya YouTube yang ringan, mulus 60 FPS, anti-lag, dan anti-memory leak untuk **Web**, **Desktop Windows (`.exe` via Tauri)**, dan **Android (`.apk` via Capacitor)**.

---

### Akses Cepat

- 🌐 **Buka Versi Web (Live):** [https://spotyflow.vercel.app](https://spotyflow.vercel.app)
- 📱 **Download Aplikasi Android (`.apk`):** [Download VeloxTube.apk (Latest Release)](https://github.com/afiqkhuzeiri11-netizen/spotyflow-android/releases/latest/download/VeloxTube.apk)

---

### Fitur Utama
- **Desain Identik YouTube (Dark `#0F0F0F` & Light `#FFFFFF`):** Sticky Header, Full/Mini Desktop Sidebar, Mobile Bottom Navigation, Category Chips, Skeleton Shimmer, dan Responsive Grid.
- **Custom HLS Video Player (`hls.js` + HTML5 Video):** Mendukung streaming adaptif `.m3u8` dengan fallback `.mp4` Byte-Range seeking instan, scrubber interaktif + tooltip timestamp, kecepatan `0.25x`–`2x`, pilihan resolusi, **Ambient Mode** hemat GPU (`16×9` canvas tiap `500ms`), Keyboard Shortcuts, dan Mobile Double-Tap `-10d`/`+10d`.
- **Persistent Floating Miniplayer:** Berpindah dari Watch Page ke Beranda atau menekan tombol Back HP otomatis mengecilkan video ke Miniplayer tanpa menghentikan atau me-reload video.
- **Vertical Shorts (`/shorts`):** Full-viewport scroll-snap dengan `IntersectionObserver` dan pembersihan memori agresif (`hls.destroy()` + unload `<video>` di luar jendela aktif).
- **Native Android Experience (`com.veloxtube.app`):**
  - Hardware Back Button: Watch Page → Miniplayer, Beranda → tekan 2x untuk keluar.
  - Auto-Rotate Landscape + Immersive Mode saat Fullscreen, kembali Portrait saat keluar.
  - Warna Status Bar otomatis mengikuti Dark/Light Mode + dukungan Safe Area notch.
- **Auto-Fallback Offline/Demo Mode:** Jika backend FastAPI belum aktif atau tidak terjangkau dari HP/Vercel, frontend otomatis beralih mulus ke katalog HLS publik dengan penyimpanan Like, Subscribe, dan Komentar di `localStorage`.

---

### Struktur Proyek
- `/backend` — Python FastAPI + Async SQLAlchemy (SQLite / PostgreSQL) + JWT Auth + Google OAuth + FFmpeg HLS Transcoder + HTTP Byte-Range Streaming (`Dockerfile`, `Procfile`, `render.yaml`, `railway.toml`).
- `/frontend` — React + Vite + TypeScript + Tailwind CSS + `hls.js` + **Tauri v2 (`src-tauri/`)** + **Capacitor Android (`android/`)** + `vercel.json`.

---

### Menjalankan Secara Lokal

#### 1. Backend (FastAPI)
```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

#### 2. Frontend Web (Vite)
```bash
cd frontend
npm install
npm run dev
```

#### 3. Aplikasi Desktop Windows (`.exe` via Tauri)
*(Membutuhkan Rust Toolchain & WebView2)*
```bash
cd frontend
npm run desktop:dev
npm run desktop:build
```

#### 4. Aplikasi Android (`.apk` via Capacitor)
```bash
cd frontend
npm run android:sync
cd android
./gradlew assembleDebug
```
