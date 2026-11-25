import React, { useCallback, useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import {
  FastForward,
  Maximize2,
  Pause,
  Play,
  Rewind,
  SkipForward,
  Volume2,
  VolumeX,
  X,
} from 'lucide-react';
import type { QualityOption, VideoCardItem } from '../../types/api';
import {
  enterNativeFullscreenOrientation,
  exitNativeFullscreenOrientation,
} from '../../hooks/useNativeBridge';
import { historyApi } from '../../services/api';
import { MiniProgressBar, PlayerControls } from './PlayerControls';

const VOLUME_STORAGE_KEY = 'veloxtube_player_volume_v1';

function readSavedVolume(): number {
  try {
    const raw = localStorage.getItem(VOLUME_STORAGE_KEY);
    if (!raw) return 1.0;
    const parsed = Number(raw);
    if (Number.isFinite(parsed) && parsed >= 0 && parsed <= 1) {
      return parsed === 0 ? 1.0 : parsed;
    }
  } catch {
    // Ignore storage error
  }
  return 1.0;
}

const DEFAULT_QUALITIES: QualityOption[] = [
  { label: 'Auto', height: -1, levelIndex: -1 },
  { label: '1080p', height: 1080, levelIndex: -1 },
  { label: '720p', height: 720, levelIndex: -1 },
  { label: '480p', height: 480, levelIndex: -1 },
  { label: '360p', height: 360, levelIndex: -1 },
];

interface GlobalVideoPlayerProps {
  video: VideoCardItem;
  isMiniplayer: boolean;
  isTheater: boolean;
  isHidden?: boolean;
  hasNextVideo: boolean;
  externalPauseSignal: number;
  onExpandToWatch: () => void;
  onEnterMiniplayer: () => void;
  onClosePlayer: () => void;
  onToggleTheater: () => void;
  onNextVideo: () => void;
  onNotify: (msg: string) => void;
}

interface SeekBurst {
  side: 'left' | 'right';
  deltaSec: number;
  key: number;
}

interface CenterFlash {
  kind: 'play' | 'pause' | 'mute' | 'unmute' | 'volume';
  label?: string;
  key: number;
}

/**
 * Single Global Persistent Video Player:
 * Stays mounted in ONE stable position so switching /watch/:id <-> Home (Floating Miniplayer)
 * NEVER unmounts, NEVER pauses, and NEVER reloads the HLS/MP4 video stream!
 */
export const GlobalVideoPlayer: React.FC<GlobalVideoPlayerProps> = React.memo(
  ({
    video,
    isMiniplayer,
    isTheater,
    isHidden = false,
    hasNextVideo,
    externalPauseSignal,
    onExpandToWatch,
    onEnterMiniplayer,
    onClosePlayer,
    onToggleTheater,
    onNextVideo,
    onNotify,
  }) => {
    const containerRef = useRef<HTMLDivElement | null>(null);
    const videoRef = useRef<HTMLVideoElement | null>(null);
    const hlsRef = useRef<Hls | null>(null);
    const ambientCanvasRef = useRef<HTMLCanvasElement | null>(null);

    const [isPlaying, setIsPlaying] = useState(true);
    const [isBuffering, setIsBuffering] = useState(true);
    const [isMuted, setIsMuted] = useState(false);
    const [volume, setVolume] = useState<number>(() => readSavedVolume());
    const [playbackRate, setPlaybackRate] = useState(1);

    // Determine whether to use synchronized YouTube Embed (when yt_id has no direct muxed HLS/MP4 stream)
    const hasDirectStream = Boolean(
      (video.hls_url && video.hls_url.includes('.m3u8')) ||
        (video.mp4_url && !video.mp4_url.includes('youtube.com/watch'))
    );
    const useYoutubeEmbed = Boolean(video.yt_id && !hasDirectStream);
    const resumeAppliedRef = useRef<string | null>(null);
    const [qualities, setQualities] = useState<QualityOption[]>(DEFAULT_QUALITIES);
    const [activeQualityLabel, setActiveQualityLabel] = useState('Auto');
    const [ambientEnabled, setAmbientEnabled] = useState(true);
    const [ambientRgb, setAmbientRgb] = useState('42, 42, 48');
    const [isFullscreen, setIsFullscreen] = useState(false);

    const [controlsVisible, setControlsVisible] = useState(true);
    const settingsOpenRef = useRef(false);
    const hideTimerRef = useRef<number | null>(null);

    const [seekBurst, setSeekBurst] = useState<SeekBurst | null>(null);
    const [centerFlash, setCenterFlash] = useState<CenterFlash | null>(null);
    const lastTapRef = useRef<{ time: number; side: 'left' | 'right' | 'center' }>({
      time: 0,
      side: 'center',
    });
    const singleTapTimerRef = useRef<number | null>(null);
    const burstAccumRef = useRef(0);
    const burstResetTimerRef = useRef<number | null>(null);

    const scheduleAutoHide = useCallback(() => {
      if (hideTimerRef.current !== null) window.clearTimeout(hideTimerRef.current);
      setControlsVisible(true);
      hideTimerRef.current = window.setTimeout(() => {
        const el = videoRef.current;
        if (el && !el.paused && !settingsOpenRef.current) {
          setControlsVisible(false);
        }
      }, 3000);
    }, []);

    // Strict Anti-Memory-Leak Engine Cleanup
    const destroyMediaEngine = useCallback(() => {
      if (hlsRef.current) {
        try {
          hlsRef.current.destroy();
        } catch {
          // Ignore destroy failure
        }
        hlsRef.current = null;
      }
      const el = videoRef.current;
      if (el) {
        try {
          el.pause();
          el.removeAttribute('src');
          el.load();
        } catch {
          // Ignore unload failure
        }
      }
    }, []);

    // External pause signal (e.g. when user navigates into /shorts)
    useEffect(() => {
      if (externalPauseSignal <= 0) return;
      const el = videoRef.current;
      if (el && !el.paused) {
        el.pause();
      }
    }, [externalPauseSignal]);

    // Attach HLS (.m3u8) with automatic MP4 Byte-Range fallback ONLY when video.id changes
    useEffect(() => {
      if (useYoutubeEmbed) {
        destroyMediaEngine();
        setIsBuffering(false);
        // Save entry immediately to cross-device watch history
        const key = video.yt_id ? `yt_${video.yt_id}` : String(video.id);
        historyApi
          .saveProgress({
            video_key: key,
            title: video.title,
            channel_name: video.channel.display_name,
            thumbnail_url: video.thumbnail_url,
            duration_sec: video.duration_sec || 0,
            position_sec: video.resume_position_sec || 0,
            source: 'youtube',
          })
          .catch(() => {});
        return;
      }

      const el = videoRef.current;
      if (!el) return;

      let cancelled = false;
      destroyMediaEngine();
      setIsBuffering(true);
      setActiveQualityLabel('Auto');
      setQualities(DEFAULT_QUALITIES);
      resumeAppliedRef.current = null;

      // Always open videos with audio ON (muted = false) and saved volume (default 100%)
      const savedVol = readSavedVolume();
      el.volume = savedVol;
      el.muted = false;
      setVolume(savedVol);
      setIsMuted(false);
      el.playbackRate = playbackRate;

      const startMp4Fallback = () => {
        if (cancelled || !videoRef.current) return;
        const vid = videoRef.current;
        vid.src = video.mp4_url;
        vid.playbackRate = playbackRate;
        vid.play().catch(() => setIsPlaying(false));
      };

      const hlsSource = video.hls_url;
      if (hlsSource && Hls.isSupported() && hlsSource.includes('.m3u8')) {
        const hls = new Hls({
          enableWorker: true,
          lowLatencyMode: Boolean(video.is_live),
          maxBufferLength: 30,
          maxMaxBufferLength: 60,
          capLevelToPlayerSize: true,
        });
        hlsRef.current = hls;
        hls.loadSource(hlsSource);
        hls.attachMedia(el);

        hls.on(Hls.Events.MANIFEST_PARSED, (_evt, data) => {
          if (cancelled) return;
          const levelByHeight = new Map<number, number>();
          data.levels.forEach((lvl, idx) => {
            if (lvl.height && !levelByHeight.has(lvl.height)) {
              levelByHeight.set(lvl.height, idx);
            }
          });
          const targets = [1080, 720, 480, 360];
          const opts: QualityOption[] = [{ label: 'Auto', height: -1, levelIndex: -1 }];
          targets.forEach((targetH) => {
            let bestIdx = -1;
            let bestDiff = Infinity;
            levelByHeight.forEach((idx, h) => {
              const diff = Math.abs(h - targetH);
              if (diff < bestDiff) {
                bestDiff = diff;
                bestIdx = idx;
              }
            });
            opts.push({ label: `${targetH}p`, height: targetH, levelIndex: bestIdx });
          });
          setQualities(opts);
          el.play().catch(() => setIsPlaying(false));
        });

        hls.on(Hls.Events.ERROR, (_evt, data) => {
          if (data.fatal) {
            if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
              hls.recoverMediaError();
            } else {
              try {
                hls.destroy();
              } catch {
                // Ignore
              }
              hlsRef.current = null;
              startMp4Fallback();
            }
          }
        });
      } else if (hlsSource && el.canPlayType('application/vnd.apple.mpegurl')) {
        el.src = hlsSource;
        el.play().catch(() => startMp4Fallback());
      } else {
        startMp4Fallback();
      }

      scheduleAutoHide();

      return () => {
        cancelled = true;
        destroyMediaEngine();
      };
      // Strictly re-run ONLY when switching video source
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [video.id, video.yt_id, video.hls_url, video.mp4_url, useYoutubeEmbed]);

    // Periodic Cross-Device Watch History & Resume Playback sync (every 5s)
    useEffect(() => {
      if (useYoutubeEmbed) return;
      const videoKey = video.yt_id ? `yt_${video.yt_id}` : String(video.id);
      const saveNow = () => {
        const el = videoRef.current;
        if (!el || el.readyState < 1) return;
        const pos = Number.isFinite(el.currentTime) ? el.currentTime : 0;
        const dur = Number.isFinite(el.duration) && el.duration > 0 ? Math.floor(el.duration) : video.duration_sec;
        historyApi
          .saveProgress({
            video_key: videoKey,
            title: video.title,
            channel_name: video.channel.display_name,
            thumbnail_url: video.thumbnail_url,
            duration_sec: dur || 0,
            position_sec: pos,
            source: video.yt_id ? 'youtube' : 'veloxtube',
          })
          .catch(() => {});
      };

      const interval = window.setInterval(saveNow, 5000);
      return () => {
        window.clearInterval(interval);
        saveNow();
      };
    }, [video.id, video.yt_id, video.title, video.channel.display_name, video.thumbnail_url, video.duration_sec, useYoutubeEmbed]);

    // Lightweight Ambient Mode: downscaled 16x9 canvas sampled every 500ms (zero per-frame blur!)
    useEffect(() => {
      if (!ambientEnabled || isMiniplayer || isFullscreen) return;

      if (!ambientCanvasRef.current) {
        const c = document.createElement('canvas');
        c.width = 16;
        c.height = 9;
        ambientCanvasRef.current = c;
      }

      const sampleFrame = () => {
        const el = videoRef.current;
        const c = ambientCanvasRef.current;
        if (!el || !c || el.paused || el.readyState < 2) return;
        const ctx = c.getContext('2d', { willReadFrequently: true });
        if (!ctx) return;
        try {
          ctx.drawImage(el, 0, 0, 16, 9);
          const px = ctx.getImageData(0, 0, 16, 9).data;
          let r = 0;
          let g = 0;
          let b = 0;
          const n = px.length / 4;
          for (let i = 0; i < px.length; i += 4) {
            r += px[i];
            g += px[i + 1];
            b += px[i + 2];
          }
          setAmbientRgb(`${Math.round(r / n)}, ${Math.round(g / n)}, ${Math.round(b / n)}`);
        } catch {
          // Cross-origin frame security guard — retain smooth neutral ambient glow
        }
      };

      const id = window.setInterval(sampleFrame, 500);
      return () => window.clearInterval(id);
    }, [ambientEnabled, isMiniplayer, isFullscreen]);

    // Fullscreen listener
    useEffect(() => {
      const onFs = () => setIsFullscreen(Boolean(document.fullscreenElement));
      document.addEventListener('fullscreenchange', onFs);
      return () => document.removeEventListener('fullscreenchange', onFs);
    }, []);

    useEffect(() => {
      return () => {
        if (hideTimerRef.current !== null) window.clearTimeout(hideTimerRef.current);
        if (singleTapTimerRef.current !== null) window.clearTimeout(singleTapTimerRef.current);
        if (burstResetTimerRef.current !== null) window.clearTimeout(burstResetTimerRef.current);
      };
    }, []);

    const flashCenter = useCallback((kind: CenterFlash['kind'], label?: string) => {
      setCenterFlash({ kind, label, key: Date.now() });
    }, []);

    const handleTogglePlay = useCallback(() => {
      const el = videoRef.current;
      if (!el) return;
      if (el.paused) {
        el.play().catch(() => {});
        flashCenter('play');
        scheduleAutoHide();
      } else {
        el.pause();
        flashCenter('pause');
        setControlsVisible(true);
      }
    }, [flashCenter, scheduleAutoHide]);

    const handleSeekRelative = useCallback(
      (delta: number, showRipple = true) => {
        const el = videoRef.current;
        if (!el || video.is_live) return;
        const dur = Number.isFinite(el.duration) && el.duration > 0 ? el.duration : 0;
        const target = Math.max(
          0,
          dur > 0 ? Math.min(dur, el.currentTime + delta) : el.currentTime + delta
        );
        el.currentTime = target;

        if (showRipple) {
          const side: 'left' | 'right' = delta < 0 ? 'left' : 'right';
          burstAccumRef.current =
            Math.sign(burstAccumRef.current) === Math.sign(delta)
              ? burstAccumRef.current + delta
              : delta;
          setSeekBurst({ side, deltaSec: burstAccumRef.current, key: Date.now() });
          if (burstResetTimerRef.current !== null) {
            window.clearTimeout(burstResetTimerRef.current);
          }
          burstResetTimerRef.current = window.setTimeout(() => {
            burstAccumRef.current = 0;
            setSeekBurst(null);
          }, 620);
        }
        scheduleAutoHide();
      },
      [video.is_live, scheduleAutoHide]
    );

    const handleToggleMute = useCallback(() => {
      const el = videoRef.current;
      if (!el) return;
      const next = !el.muted;
      el.muted = next;
      setIsMuted(next);
      if (!next && el.volume === 0) {
        el.volume = 0.6;
        setVolume(0.6);
      }
      flashCenter(next ? 'mute' : 'unmute');
      scheduleAutoHide();
    }, [flashCenter, scheduleAutoHide]);

    const handleVolumeChange = useCallback(
      (val: number) => {
        const clamped = Math.max(0, Math.min(1, Number(val.toFixed(2))));
        const el = videoRef.current;
        if (el) {
          el.volume = clamped;
          el.muted = clamped === 0;
        }
        setVolume(clamped);
        setIsMuted(clamped === 0);
        try {
          if (clamped > 0) localStorage.setItem(VOLUME_STORAGE_KEY, String(clamped));
        } catch {
          // Ignore storage error
        }
        scheduleAutoHide();
      },
      [scheduleAutoHide]
    );

    const handlePlaybackRateChange = useCallback(
      (rate: number) => {
        const el = videoRef.current;
        if (el) el.playbackRate = rate;
        setPlaybackRate(rate);
        onNotify(`Kecepatan: ${rate === 1 ? 'Normal' : `${rate}x`}`);
      },
      [onNotify]
    );

    const handleSelectQuality = useCallback(
      (opt: QualityOption) => {
        setActiveQualityLabel(opt.label);
        if (hlsRef.current) {
          hlsRef.current.currentLevel = opt.levelIndex;
        }
        onNotify(`Kualitas video: ${opt.label}`);
      },
      [onNotify]
    );

    const handleToggleFullscreen = useCallback(() => {
      const box = containerRef.current;
      if (!box) return;
      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
        exitNativeFullscreenOrientation();
      } else if (box.requestFullscreen) {
        box
          .requestFullscreen()
          .then(() => enterNativeFullscreenOrientation())
          .catch(() => {
            // Fallback for mobile WebViews where element fullscreen is restricted
            enterNativeFullscreenOrientation();
          });
      }
      scheduleAutoHide();
    }, [scheduleAutoHide]);

    const handleToggleMini = useCallback(() => {
      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
      }
      if (isMiniplayer) {
        onExpandToWatch();
      } else {
        onEnterMiniplayer();
      }
    }, [isMiniplayer, onExpandToWatch, onEnterMiniplayer]);

    // Media Session API integration (Hardware media keys & Android notification controls)
    useEffect(() => {
      if (!('mediaSession' in navigator)) return;
      try {
        navigator.mediaSession.metadata = new MediaMetadata({
          title: video.title,
          artist: video.channel.display_name,
          album: 'VeloxTube',
          artwork: video.thumbnail_url
            ? [{ src: video.thumbnail_url, sizes: '640x360', type: 'image/jpeg' }]
            : [],
        });
        navigator.mediaSession.setActionHandler('play', () => videoRef.current?.play().catch(() => {}));
        navigator.mediaSession.setActionHandler('pause', () => videoRef.current?.pause());
        navigator.mediaSession.setActionHandler('seekbackward', () => handleSeekRelative(-10, false));
        navigator.mediaSession.setActionHandler('seekforward', () => handleSeekRelative(10, false));
        navigator.mediaSession.setActionHandler(
          'nexttrack',
          hasNextVideo ? () => onNextVideo() : null
        );
      } catch {
        // Ignore unsupported browser MediaSession actions
      }
    }, [video.title, video.channel.display_name, video.thumbnail_url, hasNextVideo, handleSeekRelative, onNextVideo]);

    // Desktop Keyboard Shortcuts (Space/K, J/L, Arrows, M, F, T, I)
    useEffect(() => {
      if (isHidden) return;
      const onKeyDown = (e: KeyboardEvent) => {
        const target = e.target as HTMLElement | null;
        const tag = target?.tagName?.toLowerCase();
        if (tag === 'input' || tag === 'textarea' || target?.isContentEditable) return;
        if (e.ctrlKey || e.metaKey || e.altKey) return;

        const key = e.key.toLowerCase();
        if (key === ' ' || key === 'k') {
          e.preventDefault();
          handleTogglePlay();
        } else if (key === 'j') {
          e.preventDefault();
          handleSeekRelative(-10);
        } else if (key === 'l') {
          e.preventDefault();
          handleSeekRelative(10);
        } else if (e.key === 'ArrowLeft') {
          e.preventDefault();
          handleSeekRelative(-5);
        } else if (e.key === 'ArrowRight') {
          e.preventDefault();
          handleSeekRelative(5);
        } else if (e.key === 'ArrowUp') {
          e.preventDefault();
          const nextVol = Math.min(1, (isMuted ? 0 : volume) + 0.05);
          handleVolumeChange(nextVol);
          flashCenter('volume', `${Math.round(nextVol * 100)}%`);
        } else if (e.key === 'ArrowDown') {
          e.preventDefault();
          const nextVol = Math.max(0, (isMuted ? 0 : volume) - 0.05);
          handleVolumeChange(nextVol);
          flashCenter('volume', `${Math.round(nextVol * 100)}%`);
        } else if (key === 'm') {
          e.preventDefault();
          handleToggleMute();
        } else if (key === 'f' && !isMiniplayer) {
          e.preventDefault();
          handleToggleFullscreen();
        } else if (key === 't' && !isMiniplayer) {
          e.preventDefault();
          onToggleTheater();
        } else if (key === 'i') {
          e.preventDefault();
          handleToggleMini();
        }
      };

      window.addEventListener('keydown', onKeyDown);
      return () => window.removeEventListener('keydown', onKeyDown);
    }, [
      isHidden,
      isMiniplayer,
      isMuted,
      volume,
      handleTogglePlay,
      handleSeekRelative,
      handleVolumeChange,
      handleToggleMute,
      handleToggleFullscreen,
      handleToggleMini,
      onToggleTheater,
      flashCenter,
    ]);

    // Click / Touch Double-Tap Handler (-10d / +10d with ripple on mobile!)
    const handleSurfaceClick = useCallback(
      (e: React.MouseEvent<HTMLDivElement>) => {
        if (isMiniplayer) {
          onExpandToWatch();
          return;
        }

        const rect = e.currentTarget.getBoundingClientRect();
        const relX = (e.clientX - rect.left) / Math.max(1, rect.width);
        const side: 'left' | 'right' | 'center' =
          relX < 0.38 ? 'left' : relX > 0.62 ? 'right' : 'center';
        const now = Date.now();
        const isTouchLike =
          Boolean(window.matchMedia) && window.matchMedia('(pointer: coarse)').matches;

        if (
          side !== 'center' &&
          now - lastTapRef.current.time < 300 &&
          lastTapRef.current.side === side
        ) {
          if (singleTapTimerRef.current !== null) {
            window.clearTimeout(singleTapTimerRef.current);
            singleTapTimerRef.current = null;
          }
          lastTapRef.current = { time: 0, side: 'center' };
          handleSeekRelative(side === 'left' ? -10 : 10, true);
          return;
        }

        lastTapRef.current = { time: now, side };

        if (isTouchLike) {
          if (singleTapTimerRef.current !== null) {
            window.clearTimeout(singleTapTimerRef.current);
          }
          singleTapTimerRef.current = window.setTimeout(() => {
            setControlsVisible((prev) => {
              const next = !prev;
              if (next) scheduleAutoHide();
              return next;
            });
            singleTapTimerRef.current = null;
          }, 260);
        } else {
          handleTogglePlay();
        }
      },
      [isMiniplayer, onExpandToWatch, handleSeekRelative, handleTogglePlay, scheduleAutoHide]
    );

    const handleCloseWithCleanup = useCallback(
      (e: React.MouseEvent) => {
        e.stopPropagation();
        destroyMediaEngine();
        onClosePlayer();
      },
      [destroyMediaEngine, onClosePlayer]
    );

    const outerClass = isHidden
      ? 'hidden'
      : isMiniplayer
      ? 'fixed bottom-14 left-0 right-0 z-40 border-t shadow-2xl md:bottom-5 md:right-5 md:left-auto md:w-[360px] md:overflow-hidden md:rounded-xl md:border'
      : isTheater
      ? 'relative z-20 w-full bg-black lg:col-span-2 lg:row-start-1'
      : 'sticky top-14 z-30 w-full bg-black md:static md:z-10 md:rounded-xl lg:col-start-1 lg:row-start-1';

    return (
      <div
        className={outerClass}
        style={
          isMiniplayer
            ? {
                backgroundColor: 'var(--vt-surface)',
                borderColor: 'var(--vt-border)',
              }
            : undefined
        }
      >
        {/* Lightweight GPU-Only Ambient Mode Glow (16x9 canvas sampled every 500ms) */}
        {!isMiniplayer && !isFullscreen && ambientEnabled && (
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -inset-x-6 -bottom-10 -top-4 -z-10 hidden opacity-55 transition-colors duration-700 md:block"
            style={{
              background: `radial-gradient(ellipse at center, rgba(${ambientRgb}, 0.65) 0%, rgba(${ambientRgb}, 0.16) 52%, transparent 76%)`,
            }}
          />
        )}

        {/* Mobile Miniplayer Top Red Progress Line */}
        {isMiniplayer && (
          <div className="md:hidden">
            <MiniProgressBar videoRef={videoRef} isLive={Boolean(video.is_live)} />
          </div>
        )}

        <div
          className={
            isMiniplayer
              ? 'flex h-14 items-center gap-2.5 pr-2 md:block md:h-auto md:pr-0'
              : 'relative w-full'
          }
        >
          <div
            ref={containerRef}
            onMouseMove={!isMiniplayer ? scheduleAutoHide : undefined}
            onMouseLeave={() => {
              if (
                !isMiniplayer &&
                videoRef.current &&
                !videoRef.current.paused &&
                !settingsOpenRef.current
              ) {
                setControlsVisible(false);
              }
            }}
            onClick={handleSurfaceClick}
            className={
              isMiniplayer
                ? 'group/mini relative h-14 w-[100px] shrink-0 cursor-pointer overflow-hidden bg-black md:aspect-video md:h-auto md:w-full'
                : isTheater
                ? 'relative mx-auto aspect-video max-h-[78vh] w-full cursor-pointer select-none overflow-hidden bg-black'
                : 'relative aspect-video w-full cursor-pointer select-none overflow-hidden bg-black md:rounded-xl'
            }
          >
            {useYoutubeEmbed ? (
              <iframe
                key={video.yt_id}
                src={
                  video.embed_url ||
                  `https://www.youtube.com/embed/${video.yt_id}?autoplay=1&mute=0&playsinline=1&rel=0&enablejsapi=1${
                    video.resume_position_sec && video.resume_position_sec > 5
                      ? `&start=${Math.floor(video.resume_position_sec)}`
                      : ''
                  }`
                }
                title={video.title}
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                allowFullScreen
                className="h-full w-full border-0"
              />
            ) : (
              <video
                ref={videoRef}
                playsInline
                crossOrigin="anonymous"
                poster={video.thumbnail_url}
                onLoadedMetadata={(e) => {
                  const el = e.currentTarget;
                  const resumeSec = video.resume_position_sec || 0;
                  const key = video.yt_id ? `yt_${video.yt_id}` : String(video.id);
                  if (
                    resumeSec > 5 &&
                    resumeAppliedRef.current !== key &&
                    Number.isFinite(el.duration) &&
                    resumeSec < el.duration - 5
                  ) {
                    el.currentTime = resumeSec;
                    resumeAppliedRef.current = key;
                  }
                }}
                onPlay={() => {
                  setIsPlaying(true);
                  setIsBuffering(false);
                  if ('mediaSession' in navigator) {
                    try {
                      navigator.mediaSession.playbackState = 'playing';
                    } catch {
                      // Ignore
                    }
                  }
                }}
                onPause={() => {
                  setIsPlaying(false);
                  setControlsVisible(true);
                  if ('mediaSession' in navigator) {
                    try {
                      navigator.mediaSession.playbackState = 'paused';
                    } catch {
                      // Ignore
                    }
                  }
                }}
                onWaiting={() => setIsBuffering(true)}
                onPlaying={() => setIsBuffering(false)}
                onCanPlay={() => setIsBuffering(false)}
                onEnded={() => {
                  if (hasNextVideo) onNextVideo();
                }}
                className="h-full w-full object-contain"
              />
            )}

            {!useYoutubeEmbed && isBuffering && (
              <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/25">
                <div className="h-10 w-10 animate-spin rounded-full border-[3px] border-white/25 border-t-white" />
              </div>
            )}

            {/* Mobile Double-Tap & Keyboard Seek Ripple (-10d / +10d) */}
            {!isMiniplayer && seekBurst && (
              <div
                key={seekBurst.key}
                className={`pointer-events-none absolute inset-y-0 flex w-2/5 items-center justify-center ${
                  seekBurst.side === 'left' ? 'left-0' : 'right-0'
                }`}
              >
                <div className="vt-seek-ripple flex h-28 w-28 flex-col items-center justify-center rounded-full bg-black/60 text-white">
                  {seekBurst.side === 'left' ? (
                    <Rewind className="h-7 w-7 fill-white" />
                  ) : (
                    <FastForward className="h-7 w-7 fill-white" />
                  )}
                  <span className="mt-1 text-xs font-bold">
                    {seekBurst.deltaSec > 0 ? `+${seekBurst.deltaSec}d` : `${seekBurst.deltaSec}d`}
                  </span>
                </div>
              </div>
            )}

            {/* Center Play/Pause/Volume Flash */}
            {!isMiniplayer && centerFlash && (
              <div
                key={centerFlash.key}
                className="pointer-events-none absolute inset-0 flex items-center justify-center"
              >
                <div className="vt-center-flash flex h-16 w-16 flex-col items-center justify-center rounded-full bg-black/65 text-white">
                  {centerFlash.kind === 'play' && <Play className="h-7 w-7 fill-white" />}
                  {centerFlash.kind === 'pause' && <Pause className="h-7 w-7 fill-white" />}
                  {centerFlash.kind === 'mute' && <VolumeX className="h-7 w-7" />}
                  {(centerFlash.kind === 'unmute' || centerFlash.kind === 'volume') && (
                    <Volume2 className="h-6 w-6" />
                  )}
                  {centerFlash.label && (
                    <span className="mt-0.5 text-[11px] font-bold">{centerFlash.label}</span>
                  )}
                </div>
              </div>
            )}

            {/* Desktop Floating Miniplayer Overlay */}
            {isMiniplayer && (
              <div className="hidden md:absolute md:inset-0 md:flex md:flex-col md:justify-between md:bg-black/45 md:p-2 md:opacity-0 md:transition-opacity md:duration-150 md:group-hover/mini:opacity-100">
                <div className="flex items-center justify-between">
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onExpandToWatch();
                    }}
                    aria-label="Kembali ke Halaman Tonton"
                    title="Kembali ke Halaman Tonton"
                    className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full bg-black/65 text-white hover:bg-black/85"
                  >
                    <Maximize2 className="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    onClick={handleCloseWithCleanup}
                    aria-label="Tutup miniplayer"
                    title="Tutup"
                    className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full bg-black/65 text-white hover:bg-black/85"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>

                <div className="flex items-center justify-center gap-4">
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleTogglePlay();
                    }}
                    aria-label={isPlaying ? 'Jeda' : 'Putar'}
                    className="flex h-11 w-11 cursor-pointer items-center justify-center rounded-full bg-black/70 text-white hover:bg-black/90"
                  >
                    {isPlaying ? (
                      <Pause className="h-5 w-5 fill-white" />
                    ) : (
                      <Play className="h-5 w-5 fill-white" />
                    )}
                  </button>
                  {hasNextVideo && (
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onNextVideo();
                      }}
                      aria-label="Video berikutnya"
                      className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-full bg-black/70 text-white hover:bg-black/90"
                    >
                      <SkipForward className="h-4 w-4 fill-white" />
                    </button>
                  )}
                </div>
                <div className="h-2" />
              </div>
            )}

            {/* Full Interactive Controls (Watch Mode & Fullscreen) */}
            {!isMiniplayer && !useYoutubeEmbed && (
              <PlayerControls
                videoRef={videoRef}
                isPlaying={isPlaying}
                isLive={Boolean(video.is_live)}
                isMuted={isMuted}
                volume={volume}
                playbackRate={playbackRate}
                qualities={qualities}
                activeQualityLabel={activeQualityLabel}
                ambientEnabled={ambientEnabled}
                isTheater={isTheater}
                isFullscreen={isFullscreen}
                visible={controlsVisible}
                hasNextVideo={hasNextVideo}
                onTogglePlay={handleTogglePlay}
                onNextVideo={onNextVideo}
                onToggleMute={handleToggleMute}
                onVolumeChange={handleVolumeChange}
                onPlaybackRateChange={handlePlaybackRateChange}
                onSelectQuality={handleSelectQuality}
                onToggleAmbient={() => {
                  setAmbientEnabled((prev) => {
                    onNotify(!prev ? 'Mode Ambient aktif' : 'Mode Ambient nonaktif');
                    return !prev;
                  });
                }}
                onToggleMiniplayer={handleToggleMini}
                onToggleTheater={onToggleTheater}
                onToggleFullscreen={handleToggleFullscreen}
                onSettingsOpenChange={(open) => {
                  settingsOpenRef.current = open;
                  if (open) setControlsVisible(true);
                }}
                onUserActivity={scheduleAutoHide}
              />
            )}
          </div>

          {/* Miniplayer Info Strip */}
          {isMiniplayer && (
            <>
              <div className="hidden md:block">
                <MiniProgressBar videoRef={videoRef} isLive={Boolean(video.is_live)} />
              </div>
              <div
                onClick={onExpandToWatch}
                className="flex min-w-0 flex-1 cursor-pointer items-center justify-between gap-2 md:px-3 md:py-2.5"
              >
                <div className="min-w-0 flex-1">
                  <p
                    className="truncate text-[13px] font-semibold"
                    style={{ color: 'var(--vt-text-primary)' }}
                  >
                    {video.title}
                  </p>
                  <p
                    className="truncate text-[11px]"
                    style={{ color: 'var(--vt-text-secondary)' }}
                  >
                    {video.channel.display_name}
                  </p>
                </div>

                <div className="flex shrink-0 items-center gap-0.5 md:hidden">
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleTogglePlay();
                    }}
                    aria-label={isPlaying ? 'Jeda' : 'Putar'}
                    className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full"
                    style={{ color: 'var(--vt-text-primary)' }}
                  >
                    {isPlaying ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5" />}
                  </button>
                  <button
                    type="button"
                    onClick={handleCloseWithCleanup}
                    aria-label="Tutup miniplayer"
                    className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full"
                    style={{ color: 'var(--vt-text-primary)' }}
                  >
                    <X className="h-5 w-5" />
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    );
  }
);

GlobalVideoPlayer.displayName = 'GlobalVideoPlayer';
