import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AlertCircle, ChevronDown, ChevronUp, Film, RefreshCw } from 'lucide-react';
import { ShortVideoItem } from '../components/shorts/ShortVideoItem';
import { CommentSection } from '../components/watch/CommentSection';
import { usePlayer } from '../context/PlayerContext';
import { extractApiError, videosApi } from '../services/api';
import type { VideoCardItem } from '../types/api';

interface ShortsPageProps {
  onShareVideo: (video: VideoCardItem) => void;
  onNotify: (msg: string) => void;
}

export const ShortsPage: React.FC<ShortsPageProps> = ({ onShareVideo, onNotify }) => {
  const { requestPause } = usePlayer();

  const [shorts, setShorts] = useState<VideoCardItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [activeIndex, setActiveIndex] = useState(0);
  const [isMuted, setIsMuted] = useState(false);

  const [commentTarget, setCommentTarget] = useState<VideoCardItem | null>(null);
  const [commentCounts, setCommentCounts] = useState<Record<number, number>>({});

  const scrollerRef = useRef<HTMLDivElement | null>(null);
  const slideRefs = useRef<Array<HTMLDivElement | null>>([]);

  // Pause global main player immediately upon entering /shorts
  useEffect(() => {
    requestPause();
  }, [requestPause]);

  const fetchShorts = useCallback(async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const res = await videosApi.shorts(1, 20);
      setShorts(res.items);
      setActiveIndex(0);
    } catch (err) {
      setErrorMsg(extractApiError(err, 'Gagal memuat daftar Shorts.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchShorts();
  }, [fetchShorts]);

  // Strict IntersectionObserver (threshold: 0.75) so ONLY 1 Short plays at a time
  useEffect(() => {
    const root = scrollerRef.current;
    if (!root || shorts.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting && entry.intersectionRatio >= 0.75) {
            const idx = Number((entry.target as HTMLElement).dataset.shortIndex);
            if (Number.isFinite(idx)) {
              setActiveIndex(idx);
            }
          }
        }
      },
      {
        root,
        threshold: [0.75],
      }
    );

    slideRefs.current.forEach((node) => {
      if (node) observer.observe(node);
    });

    return () => observer.disconnect();
  }, [shorts]);

  const scrollToIndex = useCallback(
    (nextIdx: number) => {
      const clamped = Math.max(0, Math.min(shorts.length - 1, nextIdx));
      const target = slideRefs.current[clamped];
      if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    },
    [shorts.length]
  );

  // Desktop ArrowUp / ArrowDown navigation
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (commentTarget) return;
      const target = e.target as HTMLElement | null;
      const tag = target?.tagName?.toLowerCase();
      if (tag === 'input' || tag === 'textarea' || target?.isContentEditable) return;

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        scrollToIndex(activeIndex + 1);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        scrollToIndex(activeIndex - 1);
      } else if (e.key.toLowerCase() === 'm') {
        e.preventDefault();
        setIsMuted((m) => !m);
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [activeIndex, scrollToIndex, commentTarget]);

  const handleToggleMute = useCallback(() => {
    setIsMuted((m) => !m);
  }, []);

  const handleOpenComments = useCallback((video: VideoCardItem) => {
    setCommentTarget(video);
  }, []);

  const handleCountChange = useCallback(
    (total: number) => {
      if (!commentTarget) return;
      setCommentCounts((prev) => ({ ...prev, [commentTarget.id]: total }));
    },
    [commentTarget]
  );

  if (loading) {
    return (
      <div className="flex h-[calc(100dvh-7rem)] w-full items-center justify-center md:h-[calc(100dvh-3.5rem)]">
        <div className="vt-shimmer h-[82%] w-full max-w-[400px] rounded-2xl" />
      </div>
    );
  }

  if (errorMsg) {
    return (
      <div className="flex h-[calc(100dvh-7rem)] flex-col items-center justify-center px-6 text-center md:h-[calc(100dvh-3.5rem)]">
        <AlertCircle className="h-12 w-12 text-[#FF4E45]" />
        <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--vt-text)' }}>
          Shorts tidak dapat dimuat
        </h2>
        <p className="mt-1 max-w-sm text-sm" style={{ color: 'var(--vt-text-secondary)' }}>
          {errorMsg}
        </p>
        <button
          type="button"
          onClick={fetchShorts}
          className="mt-5 inline-flex h-10 cursor-pointer items-center gap-2 rounded-full bg-[#3EA6FF] px-5 text-sm font-semibold text-[#0F0F0F]"
        >
          <RefreshCw className="h-4 w-4" />
          Coba Lagi
        </button>
      </div>
    );
  }

  if (shorts.length === 0) {
    return (
      <div className="flex h-[calc(100dvh-7rem)] flex-col items-center justify-center px-6 text-center md:h-[calc(100dvh-3.5rem)]">
        <Film className="h-12 w-12" style={{ color: 'var(--vt-text-secondary)' }} />
        <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--vt-text)' }}>
          Belum ada video Shorts
        </h2>
        <p className="mt-1 text-sm" style={{ color: 'var(--vt-text-secondary)' }}>
          Upload video vertikal dan centang opsi Shorts untuk menampilkannya di sini.
        </p>
      </div>
    );
  }

  return (
    <div className="relative h-[calc(100dvh-7rem)] w-full overflow-hidden md:h-[calc(100dvh-3.5rem)]">
      {/* Vertical Full-Viewport Scroll-Snap Feed */}
      <div
        ref={scrollerRef}
        className="vt-no-scrollbar h-full w-full snap-y snap-mandatory overflow-y-scroll overscroll-y-contain"
      >
        {shorts.map((shortVideo, idx) => (
          <div
            key={shortVideo.id}
            data-short-index={idx}
            ref={(el) => {
              slideRefs.current[idx] = el;
            }}
            className="h-full w-full snap-start snap-always"
          >
            <ShortVideoItem
              video={shortVideo}
              isActive={idx === activeIndex}
              shouldLoad={idx >= activeIndex - 1 && idx <= activeIndex + 2}
              isMuted={isMuted}
              commentCountOverride={commentCounts[shortVideo.id]}
              onToggleMute={handleToggleMute}
              onOpenComments={handleOpenComments}
              onShare={onShareVideo}
              onNotify={onNotify}
            />
          </div>
        ))}
      </div>

      {/* Desktop Floating Up / Down Navigation Buttons */}
      <div className="pointer-events-none fixed right-6 top-1/2 z-30 hidden -translate-y-1/2 flex-col gap-3 md:flex">
        <button
          type="button"
          disabled={activeIndex <= 0}
          onClick={() => scrollToIndex(activeIndex - 1)}
          aria-label="Shorts sebelumnya"
          className="pointer-events-auto flex h-12 w-12 cursor-pointer items-center justify-center rounded-full shadow-lg transition-transform hover:scale-105 active:scale-95 disabled:cursor-not-allowed disabled:opacity-30"
          style={{
            backgroundColor: 'var(--vt-chip)',
            color: 'var(--vt-text)',
          }}
        >
          <ChevronUp className="h-6 w-6" />
        </button>
        <button
          type="button"
          disabled={activeIndex >= shorts.length - 1}
          onClick={() => scrollToIndex(activeIndex + 1)}
          aria-label="Shorts berikutnya"
          className="pointer-events-auto flex h-12 w-12 cursor-pointer items-center justify-center rounded-full shadow-lg transition-transform hover:scale-105 active:scale-95 disabled:cursor-not-allowed disabled:opacity-30"
          style={{
            backgroundColor: 'var(--vt-chip)',
            color: 'var(--vt-text)',
          }}
        >
          <ChevronDown className="h-6 w-6" />
        </button>
      </div>

      {/* Shared API-Connected Comments Drawer for Active Short */}
      {commentTarget && (
        <CommentSection
          videoId={commentTarget.id}
          drawerOnly
          drawerOpen={Boolean(commentTarget)}
          onCloseDrawer={() => setCommentTarget(null)}
          onCountChange={handleCountChange}
          onNotify={onNotify}
        />
      )}
    </div>
  );
};
