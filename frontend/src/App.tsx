import React, { useCallback, useEffect, useRef, useState } from 'react';
import { BrowserRouter, HashRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { AuthModal } from './components/auth/AuthModal';
import { Header } from './components/layout/Header';
import { MobileBottomNav } from './components/layout/MobileBottomNav';
import { Sidebar, type SidebarKey } from './components/layout/Sidebar';
import { GlobalVideoPlayer } from './components/player/GlobalVideoPlayer';
import { UploadModal } from './components/upload/UploadModal';
import { WATCH_LATER_KEY } from './config/api';
import { AuthProvider, useAuth } from './context/AuthContext';
import { PlayerProvider, usePlayer } from './context/PlayerContext';
import { ThemeProvider, useTheme } from './context/ThemeContext';
import { useDebounce } from './hooks/useDebounce';
import { useNativeBridge } from './hooks/useNativeBridge';
import { HomePage } from './pages/HomePage';
import { ShortsPage } from './pages/ShortsPage';
import { WatchPage } from './pages/WatchPage';
import type { CategoryId, VideoCardItem } from './types/api';

function readWatchLaterIds(): number[] {
  try {
    const raw = localStorage.getItem(WATCH_LATER_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((n): n is number => typeof n === 'number') : [];
  } catch {
    return [];
  }
}

const AppShell: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { theme } = useTheme();
  const { user, openAuthModal } = useAuth();
  const {
    activeVideo,
    isMiniplayer,
    isTheater,
    nextVideo,
    externalPauseSignal,
    loadVideoIntoPlayer,
    setMiniplayer,
    toggleTheater,
    closePlayer,
  } = usePlayer();

  const [sidebarExpanded, setSidebarExpanded] = useState<boolean>(() =>
    typeof window !== 'undefined' ? window.innerWidth >= 1280 : true
  );
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  const [activeSection, setActiveSection] = useState<SidebarKey>('home');
  const [activeCategory, setActiveCategory] = useState<CategoryId>('all');
  const [searchInput, setSearchInput] = useState('');
  const debouncedSearch = useDebounce(searchInput, 300);

  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);
  const [watchLaterIds, setWatchLaterIds] = useState<number[]>(() => readWatchLaterIds());

  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const toastTimerRef = useRef<number | null>(null);

  const isOnWatchRoute = location.pathname.startsWith('/watch/');
  const isOnShortsRoute = location.pathname.startsWith('/shorts');

  // Automatic Miniplayer Mode whenever user leaves /watch/:id while a video is active
  useEffect(() => {
    if (!activeVideo) return;
    if (!isOnWatchRoute && !isOnShortsRoute && !isMiniplayer) {
      setMiniplayer(true);
    }
  }, [activeVideo, isOnWatchRoute, isOnShortsRoute, isMiniplayer, setMiniplayer]);

  const showToast = useCallback((msg: string) => {
    setToastMsg(msg);
    if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
    toastTimerRef.current = window.setTimeout(() => {
      setToastMsg(null);
      toastTimerRef.current = null;
    }, 3000);
  }, []);

  useEffect(() => {
    return () => {
      if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
    };
  }, []);

  const currentSidebarKey: SidebarKey = isOnShortsRoute ? 'shorts' : activeSection;

  const handleToggleSidebar = useCallback(() => {
    if (window.innerWidth < 768) {
      setMobileDrawerOpen((prev) => !prev);
    } else {
      setSidebarExpanded((prev) => !prev);
    }
  }, []);

  const handleLogoClick = useCallback(() => {
    setActiveSection('home');
    setActiveCategory('all');
    setSearchInput('');
    if (activeVideo && isOnWatchRoute) {
      setMiniplayer(true);
    }
    navigate('/');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [activeVideo, isOnWatchRoute, setMiniplayer, navigate]);

  const handleSelectSidebar = useCallback(
    (key: SidebarKey) => {
      if (key === 'shorts') {
        navigate('/shorts');
        return;
      }
      setActiveSection(key);
      if (key === 'home') setActiveCategory('all');
      if (activeVideo && isOnWatchRoute) {
        setMiniplayer(true);
      }
      if (location.pathname !== '/') navigate('/');
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
    [activeVideo, isOnWatchRoute, setMiniplayer, location.pathname, navigate]
  );

  const handleSearchChange = useCallback(
    (val: string) => {
      setSearchInput(val);
      if (activeVideo && isOnWatchRoute) {
        setMiniplayer(true);
      }
      if (location.pathname !== '/') {
        navigate('/');
      }
    },
    [activeVideo, isOnWatchRoute, setMiniplayer, location.pathname, navigate]
  );

  const handleSelectTag = useCallback(
    (tag: string) => {
      setSearchInput(tag);
      if (activeVideo) {
        setMiniplayer(true);
      }
      navigate('/');
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
    [activeVideo, setMiniplayer, navigate]
  );

  const handleOpenUpload = useCallback(() => {
    if (!user) {
      openAuthModal('login');
      showToast('Silakan login terlebih dahulu untuk upload video.');
      return;
    }
    setUploadModalOpen(true);
  }, [user, openAuthModal, showToast]);

  const handleProfileClick = useCallback(() => {
    if (!user) {
      openAuthModal('login');
      return;
    }
    setActiveSection('watch_later');
    if (activeVideo && isOnWatchRoute) {
      setMiniplayer(true);
    }
    if (location.pathname !== '/') navigate('/');
  }, [user, openAuthModal, activeVideo, isOnWatchRoute, setMiniplayer, location.pathname, navigate]);

  const handleOpenWatch = useCallback(
    (videoId: number) => {
      setMiniplayer(false);
      navigate(`/watch/${videoId}`);
      window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
    },
    [setMiniplayer, navigate]
  );

  const handleExpandMiniplayer = useCallback(() => {
    if (!activeVideo) return;
    setMiniplayer(false);
    navigate(`/watch/${activeVideo.id}`);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [activeVideo, setMiniplayer, navigate]);

  const handleEnterMiniplayer = useCallback(() => {
    setMiniplayer(true);
    if (isOnWatchRoute) {
      navigate('/');
    }
  }, [setMiniplayer, isOnWatchRoute, navigate]);

  const handleNavigateHomeNative = useCallback(() => {
    setActiveSection('home');
    navigate('/');
  }, [navigate]);

  useNativeBridge({
    theme,
    pathname: location.pathname,
    hasActiveVideo: Boolean(activeVideo),
    isMiniplayer,
    onEnterMiniplayerFromWatch: handleEnterMiniplayer,
    onNavigateHome: handleNavigateHomeNative,
    onNotify: showToast,
  });

  const handlePlayNextVideo = useCallback(() => {
    if (!nextVideo) return;
    loadVideoIntoPlayer(nextVideo, isMiniplayer && !isOnWatchRoute);
    if (isOnWatchRoute) {
      navigate(`/watch/${nextVideo.id}`);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [nextVideo, loadVideoIntoPlayer, isMiniplayer, isOnWatchRoute, navigate]);

  const handleToggleWatchLater = useCallback(
    (videoId: number) => {
      setWatchLaterIds((prev) => {
        const exists = prev.includes(videoId);
        const next = exists ? prev.filter((id) => id !== videoId) : [videoId, ...prev];
        try {
          localStorage.setItem(WATCH_LATER_KEY, JSON.stringify(next));
        } catch {
          // Ignore storage error
        }
        showToast(exists ? 'Dihapus dari Tonton Nanti' : 'Disimpan ke Tonton Nanti');
        return next;
      });
    },
    [showToast]
  );

  const handleShareVideo = useCallback(
    (video: VideoCardItem) => {
      const link = video.is_short
        ? `${window.location.origin}/shorts`
        : `${window.location.origin}/watch/${video.id}`;
      navigator.clipboard?.writeText(link).catch(() => {});
      showToast(`Tautan "${video.title}" disalin`);
    },
    [showToast]
  );

  const handleResetFilters = useCallback(() => {
    setActiveSection('home');
    setActiveCategory('all');
    setSearchInput('');
    if (location.pathname !== '/') navigate('/');
  }, [location.pathname, navigate]);

  const handleUploaded = useCallback(
    (_video: VideoCardItem, message: string) => {
      showToast(message || 'Video berhasil dipublikasikan!');
      setActiveSection('home');
      setActiveCategory('all');
      setSearchInput('');
      if (location.pathname !== '/') navigate('/');
      setRefreshTick((t) => t + 1);
    },
    [showToast, location.pathname, navigate]
  );

  const effectiveMiniplayer = isMiniplayer || !isOnWatchRoute;
  const watchGridActive = isOnWatchRoute && !effectiveMiniplayer;
  const desktopSidebarExpanded = isOnWatchRoute ? false : sidebarExpanded;

  return (
    <div className="min-h-screen w-full" style={{ backgroundColor: 'var(--vt-bg)' }}>
      <Header
        searchInput={searchInput}
        onSearchInputChange={handleSearchChange}
        onToggleSidebar={handleToggleSidebar}
        onLogoClick={handleLogoClick}
        onOpenUpload={handleOpenUpload}
        onNavigateProfile={handleProfileClick}
        onLogoutToast={() => showToast('Berhasil keluar dari akun.')}
      />

      <div className="flex w-full items-start">
        <Sidebar
          expanded={desktopSidebarExpanded}
          mobileOpen={mobileDrawerOpen}
          activeKey={currentSidebarKey}
          onSelect={handleSelectSidebar}
          onCloseMobile={() => setMobileDrawerOpen(false)}
        />

        <main className="min-w-0 flex-1">
          {/*
            Single Stable Wrapper:
            Keeping <GlobalVideoPlayer> as child #0 inside this div ensures React NEVER unmounts
            the <video> element across /watch/:id <-> Theater <-> Floating Miniplayer <-> Home!
          */}
          <div
            className={
              watchGridActive
                ? 'mx-auto grid w-full max-w-[1720px] grid-cols-1 md:px-6 md:pt-4 lg:grid-cols-[minmax(0,1fr)_402px] lg:gap-x-6'
                : 'w-full'
            }
          >
            {activeVideo && (
              <GlobalVideoPlayer
                video={activeVideo}
                isMiniplayer={effectiveMiniplayer}
                isTheater={isTheater}
                isHidden={isOnShortsRoute}
                hasNextVideo={Boolean(nextVideo)}
                externalPauseSignal={externalPauseSignal}
                onExpandToWatch={handleExpandMiniplayer}
                onEnterMiniplayer={handleEnterMiniplayer}
                onClosePlayer={closePlayer}
                onToggleTheater={toggleTheater}
                onNextVideo={handlePlayNextVideo}
                onNotify={showToast}
              />
            )}

            <Routes>
              <Route
                path="/"
                element={
                  <HomePage
                    sidebarExpanded={sidebarExpanded}
                    activeSection={activeSection}
                    activeCategory={activeCategory}
                    searchQuery={debouncedSearch}
                    refreshTick={refreshTick}
                    watchLaterIds={watchLaterIds}
                    onSelectCategory={setActiveCategory}
                    onOpenWatch={handleOpenWatch}
                    onToggleWatchLater={handleToggleWatchLater}
                    onShareVideo={handleShareVideo}
                    onResetFilters={handleResetFilters}
                  />
                }
              />
              <Route
                path="/watch/:id"
                element={
                  <WatchPage
                    watchLaterIds={watchLaterIds}
                    onToggleWatchLater={handleToggleWatchLater}
                    onShareVideo={handleShareVideo}
                    onSelectTag={handleSelectTag}
                    onNotify={showToast}
                  />
                }
              />
              <Route
                path="/shorts"
                element={<ShortsPage onShareVideo={handleShareVideo} onNotify={showToast} />}
              />
              <Route
                path="*"
                element={
                  <HomePage
                    sidebarExpanded={sidebarExpanded}
                    activeSection={activeSection}
                    activeCategory={activeCategory}
                    searchQuery={debouncedSearch}
                    refreshTick={refreshTick}
                    watchLaterIds={watchLaterIds}
                    onSelectCategory={setActiveCategory}
                    onOpenWatch={handleOpenWatch}
                    onToggleWatchLater={handleToggleWatchLater}
                    onShareVideo={handleShareVideo}
                    onResetFilters={handleResetFilters}
                  />
                }
              />
            </Routes>
          </div>
        </main>
      </div>

      <MobileBottomNav
        activeKey={currentSidebarKey}
        onSelect={handleSelectSidebar}
        onOpenUpload={handleOpenUpload}
        onProfileClick={handleProfileClick}
      />

      <AuthModal onSuccessToast={showToast} />
      <UploadModal
        open={uploadModalOpen}
        onClose={() => setUploadModalOpen(false)}
        onUploaded={handleUploaded}
      />

      {toastMsg && (
        <div
          role="status"
          aria-live="polite"
          className="vt-fade-scale fixed bottom-16 left-1/2 z-50 -translate-x-1/2 rounded-lg bg-[#212121] px-4 py-2.5 text-sm font-medium text-[#F1F1F1] shadow-xl ring-1 ring-white/10 md:bottom-6"
        >
          {toastMsg}
        </div>
      )}
    </div>
  );
};

const RouterWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const useHash = typeof window !== 'undefined' && window.location.protocol === 'file:';
  return useHash ? <HashRouter>{children}</HashRouter> : <BrowserRouter>{children}</BrowserRouter>;
};

export const App: React.FC = () => (
  <RouterWrapper>
    <ThemeProvider>
      <AuthProvider>
        <PlayerProvider>
          <AppShell />
        </PlayerProvider>
      </AuthProvider>
    </ThemeProvider>
  </RouterWrapper>
);

export default App;
