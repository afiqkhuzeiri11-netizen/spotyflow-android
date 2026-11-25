import React, { useCallback, useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import {
  CheckCircle2,
  Clock,
  MoreVertical,
  Radio,
  Share2,
  VolumeX,
} from 'lucide-react';
import { videosApi } from '../../services/api';
import type { VideoCardItem } from '../../types/api';
import { formatDuration, formatRelativeTime, formatViews } from '../../utils/format';

interface VideoCardProps {
  video: VideoCardItem;
  isInWatchLater: boolean;
  resumeProgressPct?: number;
  onOpenWatch: (video: VideoCardItem) => void;
  onToggleWatchLater: (videoId: number) => void;
  onShareVideo: (video: VideoCardItem) => void;
}

export const VideoCard: React.FC<VideoCardProps> = React.memo(
  ({
    video,
    isInWatchLater,
    resumeProgressPct = 0,
    onOpenWatch,
    onToggleWatchLater,
    onShareVideo,
  }) => {
    const [showPreview, setShowPreview] = useState(false);
    const [previewReady, setPreviewReady] = useState(false);
    const [menuOpen, setMenuOpen] = useState(false);

    const cardRef = useRef<HTMLElement | null>(null);
    const prefetchTimerRef = useRef<number | null>(null);
    const hoverTimerRef = useRef<number | null>(null);
    const videoElRef = useRef<HTMLVideoElement | null>(null);
    const hlsRef = useRef<Hls | null>(null);
    const menuRef = useRef<HTMLDivElement | null>(null);

    const videoKey = video.yt_id ? `yt_${video.yt_id}` : String(video.id);

    // Mobile Viewport Prefetch via IntersectionObserver (warms backend stream TTLCache silently)
    useEffect(() => {
      const node = cardRef.current;
      if (!node || !window.matchMedia?.('(pointer: coarse)').matches) return;
      const obs = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) {
              videosApi.prefetch(videoKey);
              obs.disconnect();
              break;
            }
          }
        },
        { threshold: 0.35 }
      );
      obs.observe(node);
      return () => obs.disconnect();
    }, [videoKey]);

    /**
     * Mandatory Anti-Memory Leak Cleanup:
     * Immediately destroys hls.js and clears HTMLVideoElement src + buffers when leaving hover or unmounting.
     */
    const cleanupPreview = useCallback(() => {
      if (prefetchTimerRef.current !== null) {
        window.clearTimeout(prefetchTimerRef.current);
        prefetchTimerRef.current = null;
      }
      if (hoverTimerRef.current !== null) {
        window.clearTimeout(hoverTimerRef.current);
        hoverTimerRef.current = null;
      }
      if (hlsRef.current) {
        try {
          hlsRef.current.destroy();
        } catch {
          // Ignore destroy failure
        }
        hlsRef.current = null;
      }
      const el = videoElRef.current;
      if (el) {
        try {
          el.pause();
          el.removeAttribute('src');
          el.load();
        } catch {
          // Ignore unload failure
        }
      }
      setPreviewReady(false);
    }, []);

    useEffect(() => {
      if (!showPreview) {
        cleanupPreview();
        return;
      }

      const el = videoElRef.current;
      if (!el) return;

      let cancelled = false;
      el.muted = true;
      el.playsInline = true;

      const playMp4 = () => {
        if (cancelled || !videoElRef.current || !video.mp4_url) return;
        const vid = videoElRef.current;
        vid.src = video.mp4_url;
        vid.play().catch(() => {});
      };

      const hlsSource = video.hls_url;
      if (hlsSource && Hls.isSupported() && hlsSource.includes('.m3u8')) {
        const hls = new Hls({
          enableWorker: true,
          lowLatencyMode: false,
          maxBufferLength: 6,
          maxMaxBufferLength: 10,
          startLevel: 0,
        });
        hlsRef.current = hls;
        hls.loadSource(hlsSource);
        hls.attachMedia(el);
        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          if (!cancelled) el.play().catch(() => {});
        });
        hls.on(Hls.Events.ERROR, (_evt, data) => {
          if (data.fatal) {
            try {
              hls.destroy();
            } catch {
              // Ignore
            }
            hlsRef.current = null;
            playMp4();
          }
        });
      } else if (hlsSource && el.canPlayType('application/vnd.apple.mpegurl')) {
        el.src = hlsSource;
        el.play().catch(() => playMp4());
      } else {
        playMp4();
      }

      return () => {
        cancelled = true;
        cleanupPreview();
      };
    }, [showPreview, video.hls_url, video.mp4_url, cleanupPreview]);

    useEffect(() => {
      if (!menuOpen) return;
      const onOutside = (e: MouseEvent) => {
        if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
          setMenuOpen(false);
        }
      };
      document.addEventListener('mousedown', onOutside);
      return () => document.removeEventListener('mousedown', onOutside);
    }, [menuOpen]);

    const handleMouseEnter = useCallback(() => {
      if (!window.matchMedia || !window.matchMedia('(hover: hover) and (pointer: fine)').matches) {
        return;
      }
      // 150ms silent prefetch for instant playback when clicked
      if (prefetchTimerRef.current !== null) window.clearTimeout(prefetchTimerRef.current);
      prefetchTimerRef.current = window.setTimeout(() => {
        videosApi.prefetch(videoKey);
      }, 150);

      // 400ms muted hover preview for direct HLS/MP4 videos
      if (!video.yt_id || video.hls_url) {
        if (hoverTimerRef.current !== null) window.clearTimeout(hoverTimerRef.current);
        hoverTimerRef.current = window.setTimeout(() => {
          setShowPreview(true);
        }, 400);
      }
    }, [videoKey, video.yt_id, video.hls_url]);

    const handleMouseLeave = useCallback(() => {
      setShowPreview(false);
      cleanupPreview();
    }, [cleanupPreview]);

    const handleOpen = useCallback(() => {
      setShowPreview(false);
      cleanupPreview();
      onOpenWatch(video);
    }, [cleanupPreview, onOpenWatch, video]);

    return (
      <article ref={cardRef} className="group flex flex-col">
        {/* 16:9 Thumbnail + 150ms Prefetch + 400ms Desktop Hover Preview */}
        <div
          onClick={handleOpen}
          onMouseEnter={handleMouseEnter}
          onMouseLeave={handleMouseLeave}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              handleOpen();
            }
          }}
          aria-label={`Tonton ${video.title}`}
          className="relative aspect-video w-full cursor-pointer overflow-hidden bg-black sm:rounded-xl"
        >
          <img
            src={video.thumbnail_url}
            alt={video.title}
            loading="lazy"
            decoding="async"
            className={`h-full w-full object-cover transition-opacity duration-200 ${
              showPreview && previewReady ? 'opacity-0' : 'opacity-100'
            }`}
          />

          {showPreview && (
            <video
              ref={videoElRef}
              muted
              playsInline
              loop
              preload="none"
              onCanPlay={() => setPreviewReady(true)}
              className={`absolute inset-0 h-full w-full object-cover transition-opacity duration-200 ${
                previewReady ? 'opacity-100' : 'opacity-0'
              }`}
            />
          )}

          {showPreview && previewReady && (
            <div className="pointer-events-none absolute right-2 top-2 flex items-center gap-1 rounded bg-black/75 px-1.5 py-0.5 text-[11px] font-medium text-white">
              <VolumeX className="h-3.5 w-3.5" />
              <span>Preview</span>
            </div>
          )}

          {video.is_live ? (
            <span className="pointer-events-none absolute bottom-2 right-2 inline-flex items-center gap-1 rounded bg-[#CC0000] px-1.5 py-0.5 text-[11px] font-semibold tracking-wide text-white">
              <Radio className="h-3 w-3" />
              LIVE
            </span>
          ) : video.duration_sec > 0 ? (
            <span className="pointer-events-none absolute bottom-2 right-2 rounded bg-black/80 px-1.5 py-0.5 text-xs font-medium text-white">
              {formatDuration(video.duration_sec)}
            </span>
          ) : (
            <span className="pointer-events-none absolute bottom-2 right-2 rounded bg-black/80 px-1.5 py-0.5 text-[11px] font-medium text-white">
              {video.processing_status === 'processing' ? 'Memproses HLS' : 'HD'}
            </span>
          )}

          {resumeProgressPct > 0 && (
            <div className="pointer-events-none absolute inset-x-0 bottom-0 h-1 bg-white/25">
              <div
                className="h-full bg-[#FF0000]"
                style={{ width: `${Math.min(100, Math.max(2, resumeProgressPct))}%` }}
              />
            </div>
          )}
        </div>

        {/* Video Info Row */}
        <div className="relative flex items-start gap-3 px-3 pt-3 sm:px-0">
          <button
            type="button"
            onClick={handleOpen}
            className="mt-0.5 h-9 w-9 shrink-0 cursor-pointer overflow-hidden rounded-full"
            aria-label={video.channel.display_name}
          >
            {video.channel.avatar_url ? (
              <img
                src={video.channel.avatar_url}
                alt={video.channel.display_name}
                loading="lazy"
                decoding="async"
                className="h-full w-full object-cover"
              />
            ) : (
              <span className="flex h-full w-full items-center justify-center bg-[#FF0000] text-sm font-bold text-white">
                {video.channel.display_name.charAt(0).toUpperCase()}
              </span>
            )}
          </button>

          <div className="min-w-0 flex-1 pr-1">
            <h3
              onClick={handleOpen}
              className="line-clamp-2 cursor-pointer text-[15px] font-medium leading-snug"
              style={{ color: 'var(--vt-text-primary)' }}
              title={video.title}
            >
              {video.title}
            </h3>

            <div
              className="mt-1 flex items-center gap-1 text-[13px]"
              style={{ color: 'var(--vt-text-secondary)' }}
            >
              <span className="truncate">{video.channel.display_name}</span>
              {video.channel.verified && (
                <CheckCircle2
                  className="h-3.5 w-3.5 shrink-0 fill-current"
                  aria-label="Terverifikasi"
                />
              )}
            </div>

            <div className="text-[13px] leading-tight" style={{ color: 'var(--vt-text-secondary)' }}>
              <span>{formatViews(video.views, video.is_live)}</span>
              {!video.is_live && (
                <>
                  <span className="mx-1">•</span>
                  <span>{formatRelativeTime(video.created_at)}</span>
                </>
              )}
            </div>
          </div>

          {/* 3-Dot Action Menu */}
          <div ref={menuRef} className="relative shrink-0">
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setMenuOpen((p) => !p);
              }}
              aria-label="Menu video"
              className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full"
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              <MoreVertical className="h-[18px] w-[18px]" />
            </button>

            {menuOpen && (
              <div
                className="vt-fade-scale absolute right-0 top-9 z-30 w-56 overflow-hidden rounded-xl border py-1.5 shadow-2xl"
                style={{
                  backgroundColor: 'var(--vt-surface-elevated)',
                  borderColor: 'var(--vt-border)',
                }}
              >
                <button
                  type="button"
                  onClick={() => {
                    onToggleWatchLater(video.id);
                    setMenuOpen(false);
                  }}
                  className="flex w-full cursor-pointer items-center gap-3 px-3.5 py-2.5 text-left text-sm"
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                >
                  <Clock className="h-4 w-4 shrink-0" />
                  <span>
                    {isInWatchLater ? 'Hapus dari Tonton Nanti' : 'Simpan ke Tonton Nanti'}
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    onShareVideo(video);
                    setMenuOpen(false);
                  }}
                  className="flex w-full cursor-pointer items-center gap-3 px-3.5 py-2.5 text-left text-sm"
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                >
                  <Share2 className="h-4 w-4 shrink-0" />
                  <span>Bagikan tautan</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </article>
    );
  }
);

VideoCard.displayName = 'VideoCard';
