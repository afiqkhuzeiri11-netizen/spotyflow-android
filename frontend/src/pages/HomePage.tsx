import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  FileUp,
  ListVideo,
  RefreshCw,
  SearchX,
  Trash2,
  WifiOff,
} from 'lucide-react';
import { CategoryBar } from '../components/feed/CategoryBar';
import { VideoCard } from '../components/feed/VideoCard';
import { VideoCardSkeleton } from '../components/feed/VideoCardSkeleton';
import type { SidebarKey } from '../components/layout/Sidebar';
import { useAuth } from '../context/AuthContext';
import { useInfiniteScroll } from '../hooks/useInfiniteScroll';
import {
  extractApiError,
  historyApi,
  searchApi,
  videosApi,
  youtubeApi,
} from '../services/api';
import {
  SEARCH_FILTERS,
  type CategoryId,
  type SearchFilterId,
  type VideoCardItem,
  type WatchHistoryItem,
  type YouTubeChannelSub,
  type YouTubePlaylistItem,
} from '../types/api';

interface HomePageProps {
  sidebarExpanded: boolean;
  activeSection: SidebarKey;
  activeCategory: CategoryId;
  searchQuery: string;
  refreshTick: number;
  watchLaterIds: number[];
  onSelectCategory: (id: CategoryId) => void;
  onOpenWatch: (video: VideoCardItem) => void;
  onToggleWatchLater: (videoId: number) => void;
  onShareVideo: (video: VideoCardItem) => void;
  onResetFilters: () => void;
  onNotify: (msg: string) => void;
}

const FEED_BATCH_LIMIT = 12;
const SEARCH_BATCH_LIMIT = 20;

function historyToVideoCard(item: WatchHistoryItem, idx: number): VideoCardItem {
  const isYt = item.video_key.startsWith('yt_');
  const ytId = isYt ? item.video_key.slice(3) : null;
  const numericId = !isYt && /^\d+$/.test(item.video_key) ? Number(item.video_key) : 900000 + idx;
  return {
    id: numericId,
    yt_id: ytId,
    resume_position_sec: item.position_sec,
    title: item.title,
    description: '',
    category: isYt ? 'youtube' : 'tech',
    tags: [],
    thumbnail_url:
      item.thumbnail_url ||
      (ytId
        ? `https://i.ytimg.com/vi/${ytId}/hqdefault.jpg`
        : 'https://images.unsplash.com/photo-1611162617474-5b21e879e113?w=640&h=360&fit=crop&q=80'),
    hls_url: null,
    mp4_url: ytId ? `https://www.youtube.com/watch?v=${ytId}` : '',
    duration_sec: item.duration_sec,
    views: 0,
    likes_count: 0,
    is_short: false,
    is_live: false,
    processing_status: 'ready',
    created_at: item.watched_at,
    channel: {
      id: numericId,
      username: item.channel_name.toLowerCase().replace(/\s+/g, '_'),
      display_name: item.channel_name,
      handle: `@${item.channel_name.toLowerCase().replace(/[^a-z0-9_]/g, '') || 'channel'}`,
      avatar_url: null,
      verified: isYt,
      subscribers_count: 0,
      subscribed_by_me: false,
    },
  };
}

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
  onNotify,
}) => {
  const { user, openAuthModal, connectGoogleYouTube } = useAuth();

  const [videos, setVideos] = useState<VideoCardItem[]>([]);
  const [searchFilter, setSearchFilter] = useState<SearchFilterId>('all');
  const [searchSource, setSearchSource] = useState<string | null>(null);
  const [nextPageToken, setNextPageToken] = useState<string | null>(null);

  const [syncedChannels, setSyncedChannels] = useState<YouTubeChannelSub[]>([]);
  const [syncedPlaylists, setSyncedPlaylists] = useState<YouTubePlaylistItem[]>([]);
  const [historyMap, setHistoryMap] = useState<Record<string, WatchHistoryItem>>({});
  const [importingTakeout, setImportingTakeout] = useState(false);

  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const reqSeqRef = useRef(0);
  const takeoutInputRef = useRef<HTMLInputElement | null>(null);
  const watchLaterSet = useMemo(() => new Set(watchLaterIds), [watchLaterIds]);

  const isSearching = Boolean(searchQuery.trim());

  // Reset search filter whenever query changes
  useEffect(() => {
    setSearchFilter('all');
    setNextPageToken(null);
  }, [searchQuery]);

  // Load lightweight resume progress map for red progress bars on thumbnails
  useEffect(() => {
    historyApi
      .list()
      .then((list) => {
        const map: Record<string, WatchHistoryItem> = {};
        list.forEach((h) => {
          map[h.video_key] = h;
        });
        setHistoryMap(map);
      })
      .catch(() => {});
  }, [user?.id, refreshTick, activeSection]);

  const loadBatch = useCallback(
    async (targetPage: number, reset: boolean, tokenOverride?: string | null) => {
      const seq = reset ? ++reqSeqRef.current : reqSeqRef.current;
      if (reset) {
        setIsInitialLoading(true);
        setErrorMsg(null);
        setSyncedChannels([]);
        setSyncedPlaylists([]);
      } else {
        setIsLoadingMore(true);
      }

      try {
        // 1. Massive YouTube Search Mode (/api/search -> 60-80+ results paginated 20/page)
        if (searchQuery.trim()) {
          const res = await searchApi.search({
            q: searchQuery.trim(),
            filter: searchFilter,
            page: targetPage,
            limit: SEARCH_BATCH_LIMIT,
            pageToken: tokenOverride ?? null,
          });
          if (seq !== reqSeqRef.current) return;

          setSearchSource(res.source || null);
          setNextPageToken(res.next_page_token ?? null);

          if (reset) {
            setVideos(res.items);
          } else {
            setVideos((prev) => {
              const seen = new Set(prev.map((x) => x.yt_id || String(x.id)));
              return [...prev, ...res.items.filter((x) => !seen.has(x.yt_id || String(x.id)))];
            });
          }
          setHasMore(res.has_more);
          setPage(targetPage + 1);
          return;
        }

        // 2. Cross-Device Watch History Section (with Takeout Import)
        if (activeSection === 'history') {
          const list = await historyApi.list();
          if (seq !== reqSeqRef.current) return;
          const mapped = list.map((item, i) => historyToVideoCard(item, i));
          setVideos(mapped);
          setHasMore(false);
          return;
        }

        // 3. Liked Videos + YouTube Playlists Sync Section
        if (activeSection === 'liked') {
          const [likedRes, plRes] = await Promise.all([
            youtubeApi.liked(),
            youtubeApi.playlists(),
          ]);
          if (seq !== reqSeqRef.current) return;
          setVideos(likedRes.items);
          setSyncedPlaylists(plRes.items);
          setHasMore(false);
          return;
        }

        // 4. Subscriptions Section (Merges Synced YouTube Subscriptions + Local SQL Subscriptions)
        if (activeSection === 'subscriptions') {
          const [localRes, ytSubRes] = await Promise.all([
            videosApi.list({
              page: targetPage,
              limit: FEED_BATCH_LIMIT,
              subscribed_only: true,
            }),
            reset && user ? youtubeApi.subscriptions() : Promise.resolve({ connected: false, channels: [], videos: [] }),
          ]);
          if (seq !== reqSeqRef.current) return;

          if (reset && ytSubRes.channels.length > 0) {
            setSyncedChannels(ytSubRes.channels);
          }

          const combined = reset ? [...ytSubRes.videos, ...localRes.items] : localRes.items;
          if (reset) {
            setVideos(combined);
          } else {
            setVideos((prev) => {
              const seen = new Set(prev.map((x) => x.yt_id || String(x.id)));
              return [...prev, ...combined.filter((x) => !seen.has(x.yt_id || String(x.id)))];
            });
          }
          setHasMore(localRes.has_more);
          setPage(targetPage + 1);
          return;
        }

        // 5. Default Home Feed / Watch Later
        const res = await videosApi.list({
          page: targetPage,
          limit: FEED_BATCH_LIMIT,
          category: activeCategory,
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
            const seen = new Set(prev.map((x) => x.yt_id || String(x.id)));
            return [...prev, ...incoming.filter((x) => !seen.has(x.yt_id || String(x.id)))];
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
    [activeCategory, searchQuery, searchFilter, activeSection, watchLaterSet, user]
  );

  useEffect(() => {
    loadBatch(1, true, null);
  }, [loadBatch, refreshTick, user?.id]);

  const handleLoadMore = useCallback(() => {
    if (isInitialLoading || isLoadingMore || !hasMore) return;
    loadBatch(page, false, nextPageToken);
  }, [isInitialLoading, isLoadingMore, hasMore, loadBatch, page, nextPageToken]);

  const sentinelRef = useInfiniteScroll({
    hasMore,
    isLoading: isInitialLoading || isLoadingMore,
    onLoadMore: handleLoadMore,
  });

  const handleImportTakeout = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setImportingTakeout(true);
    try {
      const res = await historyApi.importTakeout(file);
      onNotify(res.message);
      loadBatch(1, true, null);
    } catch (err) {
      onNotify(extractApiError(err, 'Gagal mengimpor file watch-history.json.'));
    } finally {
      setImportingTakeout(false);
    }
  };

  const handleClearHistory = async () => {
    await historyApi.clear();
    setVideos([]);
    setHistoryMap({});
    onNotify('Riwayat tontonan dibersihkan.');
  };

  const gridClass = sidebarExpanded
    ? 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4'
    : 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4';

  const sectionLabel: Partial<Record<SidebarKey, string>> = {
    subscriptions: 'Terbaru dari Subscription',
    history: 'Riwayat Tontonan & Lanjutkan Menonton',
    watch_later: 'Tonton Nanti',
    liked: 'Video yang Disukai & Playlist',
  };

  return (
    <div className="w-full">
      {/* Category Chips on Home, OR 8 Massive YouTube Search Filters when Searching */}
      {isSearching ? (
        <div
          className="sticky top-14 z-30 w-full px-3 py-2.5 sm:px-6"
          style={{ backgroundColor: 'var(--vt-bg)' }}
        >
          <div className="vt-no-scrollbar flex items-center gap-2.5 overflow-x-auto">
            {SEARCH_FILTERS.map((f) => {
              const active = searchFilter === f.id;
              return (
                <button
                  key={f.id}
                  type="button"
                  onClick={() => setSearchFilter(f.id)}
                  className="h-8 shrink-0 cursor-pointer rounded-lg px-3 text-[13px] font-medium transition-colors"
                  style={{
                    backgroundColor: active ? 'var(--vt-chip-active-bg)' : 'var(--vt-chip)',
                    color: active ? 'var(--vt-chip-active-text)' : 'var(--vt-text-primary)',
                  }}
                >
                  {f.label}
                </button>
              );
            })}
          </div>
        </div>
      ) : (
        activeSection === 'home' && (
          <CategoryBar activeCategory={activeCategory} onSelectCategory={onSelectCategory} />
        )
      )}

      <section className="w-full pb-20 pt-2 sm:px-6 md:pb-10">
        {/* Section / Search Header */}
        {(sectionLabel[activeSection] || isSearching) && (
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2 px-3 sm:px-0">
            <div>
              <h1 className="text-lg font-bold sm:text-xl">
                {isSearching
                  ? `Hasil pencarian untuk "${searchQuery.trim()}"`
                  : sectionLabel[activeSection]}
              </h1>
              {isSearching && searchSource && !isInitialLoading && (
                <p className="mt-0.5 text-xs" style={{ color: 'var(--vt-text-secondary)' }}>
                  Menampilkan {videos.length} hasil • Sumber:{' '}
                  {searchSource === 'youtube_api'
                    ? 'YouTube Data API v3'
                    : searchSource === 'yt_dlp'
                    ? 'YouTube Flat Search'
                    : 'Katalog Lokal'}
                </p>
              )}
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {isSearching && (
                <button
                  type="button"
                  onClick={onResetFilters}
                  className="cursor-pointer rounded-full px-3.5 py-1.5 text-xs font-medium"
                  style={{ backgroundColor: 'var(--vt-chip)' }}
                >
                  Reset Pencarian
                </button>
              )}

              {activeSection === 'history' && (
                <>
                  <input
                    ref={takeoutInputRef}
                    type="file"
                    accept="application/json,.json"
                    onChange={handleImportTakeout}
                    className="hidden"
                  />
                  <button
                    type="button"
                    disabled={importingTakeout}
                    onClick={() => takeoutInputRef.current?.click()}
                    className="inline-flex cursor-pointer items-center gap-1.5 rounded-full bg-[#3EA6FF] px-3.5 py-1.5 text-xs font-semibold text-[#0F0F0F] disabled:opacity-50"
                  >
                    <FileUp className="h-3.5 w-3.5" />
                    <span>
                      {importingTakeout
                        ? 'Mengimpor...'
                        : 'Import YouTube Takeout (watch-history.json)'}
                    </span>
                  </button>
                  {videos.length > 0 && (
                    <button
                      type="button"
                      onClick={handleClearHistory}
                      className="inline-flex cursor-pointer items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium text-red-400"
                      style={{ backgroundColor: 'var(--vt-chip)' }}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                      <span>Hapus Riwayat</span>
                    </button>
                  )}
                </>
              )}

              {(activeSection === 'subscriptions' || activeSection === 'liked') &&
                user &&
                !user.youtube_connected && (
                  <button
                    type="button"
                    onClick={async () => {
                      try {
                        await connectGoogleYouTube();
                        onNotify('Akun YouTube tersinkron! Memuat daftar terbaru...');
                        loadBatch(1, true, null);
                      } catch (err) {
                        onNotify(
                          err instanceof Error ? err.message : 'Gagal sinkronisasi YouTube.'
                        );
                      }
                    }}
                    className="inline-flex cursor-pointer items-center gap-1.5 rounded-full bg-[#FF0000] px-3.5 py-1.5 text-xs font-semibold text-white"
                  >
                    <RefreshCw className="h-3.5 w-3.5" />
                    <span>Sinkronkan Akun YouTube</span>
                  </button>
                )}
            </div>
          </div>
        )}

        {/* Synced YouTube Subscribed Channels Strip */}
        {activeSection === 'subscriptions' && syncedChannels.length > 0 && (
          <div className="vt-no-scrollbar mb-5 flex items-center gap-4 overflow-x-auto px-3 pb-1 sm:px-0">
            {syncedChannels.map((ch) => (
              <div
                key={ch.yt_channel_id}
                className="flex w-16 shrink-0 flex-col items-center text-center"
              >
                {ch.avatar_url ? (
                  <img
                    src={ch.avatar_url}
                    alt={ch.title}
                    loading="lazy"
                    className="h-12 w-12 rounded-full object-cover ring-2 ring-[#FF0000]/40"
                  />
                ) : (
                  <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#FF0000] text-sm font-bold text-white">
                    {ch.title.charAt(0).toUpperCase()}
                  </div>
                )}
                <span className="mt-1.5 w-full truncate text-[11px] font-medium">{ch.title}</span>
              </div>
            ))}
          </div>
        )}

        {/* Synced YouTube Playlists Strip (on Liked/Library view) */}
        {activeSection === 'liked' && syncedPlaylists.length > 0 && (
          <div className="mb-6 px-3 sm:px-0">
            <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold">
              <ListVideo className="h-4 w-4 text-[#FF0000]" />
              <span>Playlist YouTube Anda ({syncedPlaylists.length})</span>
            </h2>
            <div className="vt-no-scrollbar flex gap-3 overflow-x-auto pb-1">
              {syncedPlaylists.map((pl) => (
                <div
                  key={pl.id}
                  className="w-48 shrink-0 overflow-hidden rounded-xl border"
                  style={{
                    backgroundColor: 'var(--vt-surface)',
                    borderColor: 'var(--vt-border)',
                  }}
                >
                  <div className="relative aspect-video bg-black">
                    {pl.thumbnail_url && (
                      <img
                        src={pl.thumbnail_url}
                        alt={pl.title}
                        loading="lazy"
                        className="h-full w-full object-cover"
                      />
                    )}
                    <span className="absolute bottom-1.5 right-1.5 rounded bg-black/80 px-1.5 py-0.5 text-[11px] font-semibold text-white">
                      {pl.item_count} video
                    </span>
                  </div>
                  <p className="truncate p-2.5 text-xs font-semibold">{pl.title}</p>
                </div>
              ))}
            </div>
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
              onClick={() => loadBatch(1, true, null)}
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
                : activeSection === 'history'
                ? 'Riwayat tontonan masih kosong'
                : 'Belum ada video di bagian ini'}
            </h2>
            <p className="mt-1 max-w-sm text-sm" style={{ color: 'var(--vt-text-secondary)' }}>
              {activeSection === 'subscriptions' && !user
                ? 'Masuk dengan akun Google & YouTube untuk menyinkronkan subscription, video disukai, dan playlist.'
                : activeSection === 'history'
                ? 'Tonton video atau impor file watch-history.json dari Google Takeout di tombol atas.'
                : 'Coba ubah kategori, filter pencarian, atau kata kunci lain.'}
            </p>
            {activeSection === 'subscriptions' && !user ? (
              <button
                type="button"
                onClick={() => openAuthModal('login')}
                className="mt-4 cursor-pointer rounded-full bg-[#3EA6FF] px-5 py-2 text-sm font-semibold text-[#0F0F0F]"
              >
                Login &amp; Sinkronkan YouTube
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
              {videos.map((v) => {
                const key = v.yt_id ? `yt_${v.yt_id}` : String(v.id);
                const hist = historyMap[key];
                const resumePct =
                  hist && hist.duration_sec > 0 && hist.position_sec > 3
                    ? Math.min(100, (hist.position_sec / hist.duration_sec) * 100)
                    : 0;
                const videoWithResume =
                  hist && hist.position_sec > 3
                    ? { ...v, resume_position_sec: hist.position_sec }
                    : v;
                return (
                  <VideoCard
                    key={key}
                    video={videoWithResume}
                    isInWatchLater={watchLaterSet.has(v.id)}
                    resumeProgressPct={resumePct}
                    onOpenWatch={onOpenWatch}
                    onToggleWatchLater={onToggleWatchLater}
                    onShareVideo={onShareVideo}
                  />
                );
              })}
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
