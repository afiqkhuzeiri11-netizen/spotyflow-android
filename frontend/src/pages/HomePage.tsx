import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { RefreshCw, SearchX, WifiOff } from 'lucide-react';
import { CategoryBar } from '../components/feed/CategoryBar';
import { VideoCard } from '../components/feed/VideoCard';
import { VideoCardSkeleton } from '../components/feed/VideoCardSkeleton';
import type { SidebarKey } from '../components/layout/Sidebar';
import { useAuth } from '../context/AuthContext';
import { useInfiniteScroll } from '../hooks/useInfiniteScroll';
import { extractApiError, videosApi } from '../services/api';
import type { CategoryId, VideoCardItem } from '../types/api';

interface HomePageProps {
  sidebarExpanded: boolean;
  activeSection: SidebarKey;
  activeCategory: CategoryId;
  searchQuery: string;
  refreshTick: number;
  watchLaterIds: number[];
  onSelectCategory: (id: CategoryId) => void;
  onOpenWatch: (videoId: number) => void;
  onToggleWatchLater: (videoId: number) => void;
  onShareVideo: (video: VideoCardItem) => void;
  onResetFilters: () => void;
}

const BATCH_LIMIT = 12;

export const HomePage: React.FC<HomePageProps> = ({
  sidebarExpanded,
  activeSection,
  activeCategory,
  searchQuery,
  refreshTick,
  watchLaterIds,
  onSelectCategory,
  onOpenWatch,
  onToggleWatchLater,
  onShareVideo,
  onResetFilters,
}) => {
  const { user, openAuthModal } = useAuth();

  const [videos, setVideos] = useState<VideoCardItem[]>([]);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const reqSeqRef = useRef(0);
  const watchLaterSet = useMemo(() => new Set(watchLaterIds), [watchLaterIds]);

  const loadBatch = useCallback(
    async (targetPage: number, reset: boolean) => {
      const seq = reset ? ++reqSeqRef.current : reqSeqRef.current;
      if (reset) {
        setIsInitialLoading(true);
        setErrorMsg(null);
      } else {
        setIsLoadingMore(true);
      }

      try {
        const res = await videosApi.list({
          page: targetPage,
          limit: BATCH_LIMIT,
          category: activeCategory,
          search: searchQuery,
          subscribed_only: activeSection === 'subscriptions',
        });

        if (seq !== reqSeqRef.current) return;

        let incoming = res.items;
        if (activeSection === 'watch_later') {
          incoming = incoming.filter((v) => watchLaterSet.has(v.id));
        }

        if (reset) {
          setVideos(incoming);
        } else {
          setVideos((prev) => {
            const seen = new Set(prev.map((x) => x.id));
            return [...prev, ...incoming.filter((x) => !seen.has(x.id))];
          });
        }
        setHasMore(res.has_more);
        setPage(targetPage + 1);
      } catch (err) {
        if (seq !== reqSeqRef.current) return;
        setErrorMsg(extractApiError(err, 'Gagal memuat daftar video dari server.'));
      } finally {
        if (seq === reqSeqRef.current) {
          setIsInitialLoading(false);
          setIsLoadingMore(false);
        }
      }
    },
    [activeCategory, searchQuery, activeSection, watchLaterSet]
  );

  useEffect(() => {
    loadBatch(1, true);
  }, [loadBatch, refreshTick, user?.id]);

  const handleLoadMore = useCallback(() => {
    if (isInitialLoading || isLoadingMore || !hasMore) return;
    loadBatch(page, false);
  }, [isInitialLoading, isLoadingMore, hasMore, loadBatch, page]);

  const sentinelRef = useInfiniteScroll({
    hasMore,
    isLoading: isInitialLoading || isLoadingMore,
    onLoadMore: handleLoadMore,
  });

  const gridClass = sidebarExpanded
    ? 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4'
    : 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4';

  const sectionLabel: Partial<Record<SidebarKey, string>> = {
    subscriptions: 'Terbaru dari Subscription',
    history: 'Riwayat Tontonan',
    watch_later: 'Tonton Nanti',
    liked: 'Video yang Disukai',
  };

  return (
    <div className="w-full">
      {activeSection === 'home' && (
        <CategoryBar activeCategory={activeCategory} onSelectCategory={onSelectCategory} />
      )}

      <section className="w-full pb-20 pt-2 sm:px-6 md:pb-10">
        {(sectionLabel[activeSection] || searchQuery.trim()) && (
          <div className="mb-4 flex items-center justify-between px-3 sm:px-0">
            <h1 className="text-lg font-bold sm:text-xl">
              {searchQuery.trim()
                ? `Hasil pencarian untuk "${searchQuery.trim()}"`
                : sectionLabel[activeSection]}
            </h1>
            {searchQuery.trim() && (
              <button
                type="button"
                onClick={onResetFilters}
                className="cursor-pointer rounded-full px-3 py-1 text-xs font-medium"
                style={{ backgroundColor: 'var(--vt-chip)' }}
              >
                Reset
              </button>
            )}
          </div>
        )}

        {errorMsg ? (
          <div className="flex flex-col items-center justify-center px-6 py-20 text-center">
            <div
              className="flex h-16 w-16 items-center justify-center rounded-full"
              style={{ backgroundColor: 'var(--vt-chip)' }}
            >
              <WifiOff className="h-8 w-8 text-red-400" />
            </div>
            <h2 className="mt-4 text-base font-semibold">Koneksi ke Backend Gagal</h2>
            <p className="mt-1.5 max-w-md text-sm" style={{ color: 'var(--vt-text-secondary)' }}>
              {errorMsg}
            </p>
            <button
              type="button"
              onClick={() => loadBatch(1, true)}
              className="mt-4 inline-flex cursor-pointer items-center gap-2 rounded-full bg-[#FF0000] px-4 py-2 text-sm font-semibold text-white"
            >
              <RefreshCw className="h-4 w-4" />
              <span>Coba Lagi</span>
            </button>
          </div>
        ) : isInitialLoading ? (
          <div className={`grid gap-x-4 gap-y-7 ${gridClass}`}>
            {Array.from({ length: 12 }).map((_, idx) => (
              <VideoCardSkeleton key={`sk_${idx}`} />
            ))}
          </div>
        ) : videos.length === 0 ? (
          <div className="flex flex-col items-center justify-center px-6 py-20 text-center">
            <div
              className="flex h-16 w-16 items-center justify-center rounded-full"
              style={{ backgroundColor: 'var(--vt-chip)' }}
            >
              <SearchX className="h-8 w-8" style={{ color: 'var(--vt-text-secondary)' }} />
            </div>
            <h2 className="mt-4 text-base font-semibold">
              {activeSection === 'subscriptions' && !user
                ? 'Login untuk melihat Subscription'
                : 'Belum ada video di bagian ini'}
            </h2>
            <p className="mt-1 max-w-sm text-sm" style={{ color: 'var(--vt-text-secondary)' }}>
              {activeSection === 'subscriptions' && !user
                ? 'Masuk dengan akun Google atau email untuk melacak video baru dari channel favoritmu.'
                : 'Coba ubah kategori, hapus kata kunci pencarian, atau upload video pertamamu.'}
            </p>
            {activeSection === 'subscriptions' && !user ? (
              <button
                type="button"
                onClick={() => openAuthModal('login')}
                className="mt-4 cursor-pointer rounded-full bg-[#3EA6FF] px-5 py-2 text-sm font-semibold text-[#0F0F0F]"
              >
                Login Sekarang
              </button>
            ) : (
              <button
                type="button"
                onClick={onResetFilters}
                className="mt-4 cursor-pointer rounded-full px-4 py-2 text-sm font-medium"
                style={{
                  backgroundColor: 'var(--vt-chip-active-bg)',
                  color: 'var(--vt-chip-active-text)',
                }}
              >
                Kembali ke Beranda
              </button>
            )}
          </div>
        ) : (
          <>
            <div className={`grid gap-x-4 gap-y-7 ${gridClass}`}>
              {videos.map((v) => (
                <VideoCard
                  key={v.id}
                  video={v}
                  isInWatchLater={watchLaterSet.has(v.id)}
                  onOpenWatch={onOpenWatch}
                  onToggleWatchLater={onToggleWatchLater}
                  onShareVideo={onShareVideo}
                />
              ))}
              {isLoadingMore &&
                Array.from({ length: 4 }).map((_, idx) => (
                  <VideoCardSkeleton key={`sk_more_${idx}`} />
                ))}
            </div>

            {hasMore && <div ref={sentinelRef} className="h-8 w-full" aria-hidden="true" />}

            {!hasMore && (
              <p
                className="mt-10 text-center text-xs"
                style={{ color: 'var(--vt-text-secondary)' }}
              >
                Semua video telah dimuat ({videos.length} video).
              </p>
            )}
          </>
        )}
      </section>
    </div>
  );
};
