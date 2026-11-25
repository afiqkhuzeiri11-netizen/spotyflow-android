import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  Bell,
  LogOut,
  Menu,
  Moon,
  Plus,
  Search,
  Sun,
  User,
  UserCircle2,
  X,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';

interface HeaderProps {
  searchInput: string;
  onSearchInputChange: (val: string) => void;
  onToggleSidebar: () => void;
  onLogoClick: () => void;
  onOpenUpload: () => void;
  onNavigateProfile: () => void;
  onLogoutToast: () => void;
}

export const Header: React.FC<HeaderProps> = React.memo(
  ({
    searchInput,
    onSearchInputChange,
    onToggleSidebar,
    onLogoClick,
    onOpenUpload,
    onNavigateProfile,
    onLogoutToast,
  }) => {
    const { theme, toggleTheme } = useTheme();
    const { user, openAuthModal, logout } = useAuth();

    const [mobileSearchOpen, setMobileSearchOpen] = useState(false);
    const [searchFocused, setSearchFocused] = useState(false);
    const [profileOpen, setProfileOpen] = useState(false);
    const [notifOpen, setNotifOpen] = useState(false);

    const mobileInputRef = useRef<HTMLInputElement | null>(null);
    const profileRef = useRef<HTMLDivElement | null>(null);
    const notifRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
      if (mobileSearchOpen) {
        mobileInputRef.current?.focus();
      }
    }, [mobileSearchOpen]);

    useEffect(() => {
      const handleOutside = (e: MouseEvent) => {
        const t = e.target as Node;
        if (profileRef.current && !profileRef.current.contains(t)) setProfileOpen(false);
        if (notifRef.current && !notifRef.current.contains(t)) setNotifOpen(false);
      };
      document.addEventListener('mousedown', handleOutside);
      return () => document.removeEventListener('mousedown', handleOutside);
    }, []);

    return (
      <header
        className="sticky top-0 z-40 flex h-14 w-full items-center justify-between gap-2 px-3 sm:px-4"
        style={{ backgroundColor: 'var(--vt-bg)' }}
      >
        {mobileSearchOpen ? (
          <div className="flex w-full items-center gap-2 md:hidden">
            <button
              type="button"
              onClick={() => setMobileSearchOpen(false)}
              aria-label="Kembali"
              className="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <div
              className="flex h-10 flex-1 items-center rounded-full border px-3.5"
              style={{
                backgroundColor: 'var(--vt-search-bg)',
                borderColor: 'var(--vt-search-border)',
              }}
            >
              <input
                ref={mobileInputRef}
                type="text"
                value={searchInput}
                onChange={(e) => onSearchInputChange(e.target.value)}
                placeholder="Telusuri VeloxTube"
                className="w-full bg-transparent text-sm outline-none"
              />
              {searchInput && (
                <button
                  type="button"
                  onClick={() => {
                    onSearchInputChange('');
                    mobileInputRef.current?.focus();
                  }}
                  aria-label="Hapus pencarian"
                  className="ml-1 flex h-7 w-7 cursor-pointer items-center justify-center rounded-full"
                >
                  <X className="h-4 w-4" />
                </button>
              )}
            </div>
          </div>
        ) : (
          <>
            {/* Left: Menu + Brand Logo */}
            <div className="flex shrink-0 items-center gap-1 sm:gap-3">
              <button
                type="button"
                onClick={onToggleSidebar}
                aria-label="Toggle menu navigasi"
                className="hidden h-10 w-10 cursor-pointer items-center justify-center rounded-full transition-colors md:flex"
                onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
                onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
              >
                <Menu className="h-5 w-5" />
              </button>

              <button
                type="button"
                onClick={onLogoClick}
                className="flex cursor-pointer items-center gap-1.5 py-1 pr-1"
                aria-label="VeloxTube Beranda"
              >
                <span className="inline-flex h-[22px] w-[32px] items-center justify-center rounded-[6px] bg-[#FF0000]">
                  <svg className="h-3 w-3 fill-white" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M8 5v14l11-7z" />
                  </svg>
                </span>
                <span className="text-[19px] font-bold tracking-tighter">VeloxTube</span>
                <span
                  className="-mt-2.5 text-[10px] font-medium"
                  style={{ color: 'var(--vt-text-secondary)' }}
                >
                  ID
                </span>
              </button>
            </div>

            {/* Center: Desktop YouTube Search Bar (300ms debounced -> GET /api/videos?search=...) */}
            <form
              onSubmit={(e) => e.preventDefault()}
              className="mx-2 hidden max-w-[600px] flex-1 items-center md:flex"
            >
              <div
                className="flex h-10 flex-1 items-center rounded-l-full border pl-4 pr-2 transition-colors"
                style={{
                  backgroundColor: 'var(--vt-search-bg)',
                  borderColor: searchFocused ? '#1C62B9' : 'var(--vt-search-border)',
                }}
              >
                {searchFocused && (
                  <Search
                    className="mr-2.5 h-4 w-4 shrink-0"
                    style={{ color: 'var(--vt-text-secondary)' }}
                  />
                )}
                <input
                  type="text"
                  value={searchInput}
                  onFocus={() => setSearchFocused(true)}
                  onBlur={() => setSearchFocused(false)}
                  onChange={(e) => onSearchInputChange(e.target.value)}
                  placeholder="Telusuri"
                  aria-label="Telusuri video"
                  className="w-full bg-transparent text-[15px] outline-none"
                />
                {searchInput && (
                  <button
                    type="button"
                    onClick={() => onSearchInputChange('')}
                    aria-label="Hapus pencarian"
                    className="flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-full"
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <X className="h-4 w-4" />
                  </button>
                )}
              </div>
              <button
                type="submit"
                aria-label="Cari"
                className="flex h-10 w-16 shrink-0 cursor-pointer items-center justify-center rounded-r-full border border-l-0"
                style={{
                  backgroundColor: 'var(--vt-search-btn)',
                  borderColor: 'var(--vt-search-border)',
                }}
              >
                <Search className="h-5 w-5" />
              </button>
            </form>

            {/* Right: Mobile Search, + Buat, Theme Toggle, Notifications, Profile/Login */}
            <div className="flex shrink-0 items-center gap-1 sm:gap-2">
              <button
                type="button"
                onClick={() => setMobileSearchOpen(true)}
                aria-label="Buka pencarian"
                className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full md:hidden"
              >
                <Search className="h-5 w-5" />
              </button>

              <button
                type="button"
                onClick={onOpenUpload}
                className="hidden h-9 cursor-pointer items-center gap-1.5 rounded-full px-3.5 text-sm font-medium transition-opacity hover:opacity-85 sm:flex"
                style={{
                  backgroundColor: 'var(--vt-chip)',
                  color: 'var(--vt-text-primary)',
                }}
              >
                <Plus className="h-5 w-5" />
                <span>Buat</span>
              </button>

              <button
                type="button"
                onClick={toggleTheme}
                aria-label={theme === 'dark' ? 'Ubah ke Mode Terang' : 'Ubah ke Mode Gelap'}
                title={theme === 'dark' ? 'Mode Terang' : 'Mode Gelap'}
                className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full"
                onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
                onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
              >
                {theme === 'dark' ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
              </button>

              <div ref={notifRef} className="relative">
                <button
                  type="button"
                  onClick={() => {
                    setNotifOpen((p) => !p);
                    setProfileOpen(false);
                  }}
                  aria-label="Notifikasi"
                  className="relative flex h-10 w-10 cursor-pointer items-center justify-center rounded-full"
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                >
                  <Bell className="h-5 w-5" />
                  <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-[#FF0000]" />
                </button>
                {notifOpen && (
                  <div
                    className="vt-fade-scale absolute right-0 top-12 z-50 w-72 rounded-xl border p-4 shadow-2xl sm:w-80"
                    style={{
                      backgroundColor: 'var(--vt-surface-elevated)',
                      borderColor: 'var(--vt-border)',
                    }}
                  >
                    <p className="text-sm font-semibold">Notifikasi</p>
                    <p className="mt-2 text-xs leading-relaxed" style={{ color: 'var(--vt-text-secondary)' }}>
                      Video terbaru dari channel yang kamu subscribe dan status transcode HLS akan muncul di sini.
                    </p>
                  </div>
                )}
              </div>

              <div ref={profileRef} className="relative">
                {user ? (
                  <>
                    <button
                      type="button"
                      onClick={() => {
                        setProfileOpen((p) => !p);
                        setNotifOpen(false);
                      }}
                      aria-label="Menu profil"
                      className="flex h-9 w-9 cursor-pointer items-center justify-center overflow-hidden rounded-full"
                    >
                      {user.avatar_url ? (
                        <img
                          src={user.avatar_url}
                          alt={user.display_name}
                          className="h-8 w-8 rounded-full object-cover"
                        />
                      ) : (
                        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#FF0000] text-sm font-bold text-white">
                          {user.display_name.charAt(0).toUpperCase()}
                        </span>
                      )}
                    </button>

                    {profileOpen && (
                      <div
                        className="vt-fade-scale absolute right-0 top-12 z-50 w-68 overflow-hidden rounded-xl border shadow-2xl"
                        style={{
                          backgroundColor: 'var(--vt-surface-elevated)',
                          borderColor: 'var(--vt-border)',
                        }}
                      >
                        <div
                          className="flex items-center gap-3 border-b p-4"
                          style={{ borderColor: 'var(--vt-border)' }}
                        >
                          {user.avatar_url ? (
                            <img
                              src={user.avatar_url}
                              alt={user.display_name}
                              className="h-10 w-10 rounded-full object-cover"
                            />
                          ) : (
                            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#FF0000] font-bold text-white">
                              {user.display_name.charAt(0).toUpperCase()}
                            </span>
                          )}
                          <div className="min-w-0">
                            <p className="truncate text-sm font-semibold">{user.display_name}</p>
                            <p className="truncate text-xs" style={{ color: 'var(--vt-text-secondary)' }}>
                              {user.handle}
                            </p>
                          </div>
                        </div>

                        <div className="py-1.5">
                          <button
                            type="button"
                            onClick={() => {
                              setProfileOpen(false);
                              onNavigateProfile();
                            }}
                            className="flex w-full cursor-pointer items-center gap-3 px-4 py-2.5 text-sm"
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
                            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                          >
                            <User className="h-4 w-4" />
                            <span>Channel Anda</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => {
                              toggleTheme();
                              setProfileOpen(false);
                            }}
                            className="flex w-full cursor-pointer items-center gap-3 px-4 py-2.5 text-sm"
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
                            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                          >
                            {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
                            <span>{theme === 'dark' ? 'Mode Terang' : 'Mode Gelap'}</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => {
                              logout();
                              setProfileOpen(false);
                              onLogoutToast();
                            }}
                            className="flex w-full cursor-pointer items-center gap-3 px-4 py-2.5 text-sm text-red-400"
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')}
                            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                          >
                            <LogOut className="h-4 w-4" />
                            <span>Keluar</span>
                          </button>
                        </div>
                      </div>
                    )}
                  </>
                ) : (
                  <button
                    type="button"
                    onClick={() => openAuthModal('login')}
                    className="flex h-9 cursor-pointer items-center gap-1.5 rounded-full border px-3 text-sm font-medium text-[#3EA6FF] transition-colors hover:bg-[#3EA6FF]/15"
                    style={{ borderColor: 'var(--vt-border)' }}
                  >
                    <UserCircle2 className="h-5 w-5" />
                    <span className="hidden sm:inline">Login</span>
                  </button>
                )}
              </div>
            </div>
          </>
        )}
      </header>
    );
  }
);

Header.displayName = 'Header';
