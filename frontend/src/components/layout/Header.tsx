import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowUpLeft,
  Bell,
  Clock,
  Flame,
  LogOut,
  Menu,
  Mic,
  MicOff,
  Moon,
  Plus,
  RefreshCw,
  Search,
  Sun,
  User,
  UserCircle2,
  X,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import { useDebounce } from '../../hooks/useDebounce';
import { searchApi } from '../../services/api';

const SEARCH_HISTORY_KEY = 'veloxtube_search_history_v1';

interface HeaderProps {
  searchInput: string;
  onSearchInputChange: (val: string) => void;
  onSearchSubmit: (val: string) => void;
  onToggleSidebar: () => void;
  onLogoClick: () => void;
  onOpenUpload: () => void;
  onNavigateProfile: () => void;
  onNotify: (msg: string) => void;
}

interface DropdownRow {
  text: string;
  kind: 'history' | 'trending' | 'suggest';
}

interface SpeechRecognitionInstance {
  lang: string;
  interimResults: boolean;
  maxAlternatives: number;
  start: () => void;
  stop: () => void;
  onresult: ((ev: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
}

function readSearchHistory(): string[] {
  try {
    const raw = localStorage.getItem(SEARCH_HISTORY_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((s): s is string => typeof s === 'string') : [];
  } catch {
    return [];
  }
}

function writeSearchHistory(list: string[]): void {
  try {
    localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(list.slice(0, 12)));
  } catch {
    // Ignore storage error
  }
}

function renderBoldSuggestion(suggestion: string, query: string): React.ReactNode {
  const cleanQ = query.trim();
  if (!cleanQ) return suggestion;
  const idx = suggestion.toLowerCase().indexOf(cleanQ.toLowerCase());
  if (idx < 0) return suggestion;
  const before = suggestion.slice(0, idx);
  const match = suggestion.slice(idx, idx + cleanQ.length);
  const after = suggestion.slice(idx + cleanQ.length);
  return (
    <>
      <span>{before}</span>
      <strong className="font-bold">{match}</strong>
      <span>{after}</span>
    </>
  );
}

export const Header: React.FC<HeaderProps> = React.memo(
  ({
    searchInput,
    onSearchInputChange,
    onSearchSubmit,
    onToggleSidebar,
    onLogoClick,
    onOpenUpload,
    onNavigateProfile,
    onNotify,
  }) => {
    const { theme, toggleTheme } = useTheme();
    const { user, openAuthModal, connectGoogleYouTube, logout } = useAuth();

    const [mobileSearchOpen, setMobileSearchOpen] = useState(false);
    const [searchFocused, setSearchFocused] = useState(false);
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const [highlightIdx, setHighlightIdx] = useState(-1);
    const [suggestions, setSuggestions] = useState<string[]>([]);
    const [trending, setTrending] = useState<string[]>([]);
    const [history, setHistory] = useState<string[]>(() => readSearchHistory());
    const [isListening, setIsListening] = useState(false);

    const [profileOpen, setProfileOpen] = useState(false);
    const [notifOpen, setNotifOpen] = useState(false);

    const desktopSearchRef = useRef<HTMLDivElement | null>(null);
    const desktopInputRef = useRef<HTMLInputElement | null>(null);
    const mobileInputRef = useRef<HTMLInputElement | null>(null);
    const profileRef = useRef<HTMLDivElement | null>(null);
    const notifRef = useRef<HTMLDivElement | null>(null);
    const recognitionRef = useRef<SpeechRecognitionInstance | null>(null);

    // Ultra-fast 120ms debounce for real-time YouTube Autocomplete Suggestions
    const fastQuery = useDebounce(searchInput, 120);

    useEffect(() => {
      if (mobileSearchOpen) {
        mobileInputRef.current?.focus();
        setDropdownOpen(true);
      }
    }, [mobileSearchOpen]);

    // Fetch trending queries once
    useEffect(() => {
      let active = true;
      searchApi
        .trending()
        .then((list) => {
          if (active) setTrending(list);
        })
        .catch(() => {});
      return () => {
        active = false;
      };
    }, []);

    // Fetch live YouTube suggestions (120ms debounced)
    useEffect(() => {
      if (!dropdownOpen && !mobileSearchOpen) return;
      const trimmed = fastQuery.trim();
      if (!trimmed) {
        setSuggestions([]);
        return;
      }
      let active = true;
      searchApi
        .suggest(trimmed)
        .then((list) => {
          if (active) {
            setSuggestions(list.slice(0, 10));
            setHighlightIdx(-1);
          }
        })
        .catch(() => {});
      return () => {
        active = false;
      };
    }, [fastQuery, dropdownOpen, mobileSearchOpen]);

    useEffect(() => {
      const handleOutside = (e: MouseEvent) => {
        const t = e.target as Node;
        if (profileRef.current && !profileRef.current.contains(t)) setProfileOpen(false);
        if (notifRef.current && !notifRef.current.contains(t)) setNotifOpen(false);
        if (desktopSearchRef.current && !desktopSearchRef.current.contains(t)) {
          setDropdownOpen(false);
          setSearchFocused(false);
        }
      };
      document.addEventListener('mousedown', handleOutside);
      return () => document.removeEventListener('mousedown', handleOutside);
    }, []);

    const dropdownRows: DropdownRow[] = useMemo(() => {
      const trimmed = searchInput.trim();
      if (!trimmed) {
        const rows: DropdownRow[] = history.slice(0, 5).map((text) => ({
          text,
          kind: 'history',
        }));
        const seen = new Set(rows.map((r) => r.text.toLowerCase()));
        for (const t of trending) {
          if (!seen.has(t.toLowerCase()) && rows.length < 10) {
            rows.push({ text: t, kind: 'trending' });
            seen.add(t.toLowerCase());
          }
        }
        return rows;
      }
      return suggestions.slice(0, 10).map((text) => ({ text, kind: 'suggest' }));
    }, [searchInput, history, trending, suggestions]);

    const commitSearch = useCallback(
      (rawQuery: string) => {
        const q = rawQuery.trim();
        onSearchInputChange(q);
        onSearchSubmit(q);
        setDropdownOpen(false);
        setSearchFocused(false);
        setMobileSearchOpen(false);
        setHighlightIdx(-1);
        if (q) {
          setHistory((prev) => {
            const next = [q, ...prev.filter((item) => item.toLowerCase() !== q.toLowerCase())].slice(
              0,
              12
            );
            writeSearchHistory(next);
            return next;
          });
        }
      },
      [onSearchInputChange, onSearchSubmit]
    );

    const removeHistoryItem = useCallback((text: string, e: React.MouseEvent) => {
      e.stopPropagation();
      setHistory((prev) => {
        const next = prev.filter((item) => item !== text);
        writeSearchHistory(next);
        return next;
      });
    }, []);

    const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Escape') {
        setDropdownOpen(false);
        setHighlightIdx(-1);
        return;
      }
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setDropdownOpen(true);
        setHighlightIdx((prev) =>
          dropdownRows.length === 0 ? -1 : (prev + 1) % dropdownRows.length
        );
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setDropdownOpen(true);
        setHighlightIdx((prev) =>
          dropdownRows.length === 0
            ? -1
            : prev <= 0
            ? dropdownRows.length - 1
            : prev - 1
        );
      } else if (e.key === 'Enter') {
        e.preventDefault();
        if (highlightIdx >= 0 && dropdownRows[highlightIdx]) {
          commitSearch(dropdownRows[highlightIdx].text);
        } else {
          commitSearch(searchInput);
        }
      }
    };

    // Voice Search (Web Speech API id-ID)
    const handleVoiceSearch = useCallback(() => {
      if (isListening && recognitionRef.current) {
        recognitionRef.current.stop();
        setIsListening(false);
        return;
      }
      const win = window as unknown as {
        SpeechRecognition?: new () => SpeechRecognitionInstance;
        webkitSpeechRecognition?: new () => SpeechRecognitionInstance;
      };
      const SpeechCtor = win.SpeechRecognition || win.webkitSpeechRecognition;
      if (!SpeechCtor) {
        onNotify('Browser ini belum mendukung Pencarian Suara.');
        return;
      }
      try {
        const rec = new SpeechCtor();
        rec.lang = 'id-ID';
        rec.interimResults = false;
        rec.maxAlternatives = 1;
        rec.onresult = (ev) => {
          const transcript = ev.results?.[0]?.[0]?.transcript?.trim();
          if (transcript) {
            commitSearch(transcript);
            onNotify(`Mencari: "${transcript}"`);
          }
        };
        rec.onerror = () => {
          setIsListening(false);
          onNotify('Tidak dapat menangkap suara. Pastikan izin mikrofon aktif.');
        };
        rec.onend = () => {
          setIsListening(false);
        };
        recognitionRef.current = rec;
        setIsListening(true);
        rec.start();
        onNotify('Mendengarkan suara (Bahasa Indonesia)...');
      } catch {
        setIsListening(false);
        onNotify('Gagal memulai Pencarian Suara.');
      }
    }, [isListening, commitSearch, onNotify]);

    const renderDropdownList = (isMobileOverlay: boolean) => {
      if (dropdownRows.length === 0) return null;
      return (
        <div
          className={
            isMobileOverlay
              ? 'fixed inset-x-0 top-14 bottom-0 z-50 overflow-y-auto border-t py-2 md:hidden'
              : 'absolute left-0 right-16 top-11 z-50 overflow-hidden rounded-2xl border py-2 shadow-2xl'
          }
          style={{
            backgroundColor: 'var(--vt-surface-elevated)',
            borderColor: 'var(--vt-border)',
          }}
        >
          {!searchInput.trim() && (
            <p
              className="px-4 pb-1.5 pt-0.5 text-[11px] font-semibold uppercase tracking-wide"
              style={{ color: 'var(--vt-text-secondary)' }}
            >
              {history.length > 0 ? 'Riwayat & Pencarian Populer' : 'Sedang Tren di Indonesia'}
            </p>
          )}
          {dropdownRows.map((row, idx) => {
            const active = idx === highlightIdx;
            return (
              <div
                key={`${row.kind}_${row.text}`}
                onMouseDown={(e) => {
                  e.preventDefault();
                  commitSearch(row.text);
                }}
                onMouseEnter={() => setHighlightIdx(idx)}
                className="flex cursor-pointer items-center justify-between gap-2 px-4 py-2 text-sm transition-colors"
                style={{
                  backgroundColor: active ? 'var(--vt-hover)' : 'transparent',
                  color: 'var(--vt-text-primary)',
                }}
              >
                <div className="flex min-w-0 flex-1 items-center gap-3">
                  {row.kind === 'history' ? (
                    <Clock
                      className="h-4 w-4 shrink-0"
                      style={{ color: 'var(--vt-text-secondary)' }}
                    />
                  ) : row.kind === 'trending' ? (
                    <Flame className="h-4 w-4 shrink-0 text-[#FF0000]" />
                  ) : (
                    <Search
                      className="h-4 w-4 shrink-0"
                      style={{ color: 'var(--vt-text-secondary)' }}
                    />
                  )}
                  <span className="truncate">
                    {renderBoldSuggestion(row.text, searchInput)}
                  </span>
                </div>

                <div className="flex shrink-0 items-center gap-1">
                  {row.kind === 'history' && (
                    <button
                      type="button"
                      onMouseDown={(e) => removeHistoryItem(row.text, e)}
                      aria-label={`Hapus ${row.text}`}
                      className="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-xs hover:bg-white/10"
                      style={{ color: 'var(--vt-text-secondary)' }}
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  )}
                  <button
                    type="button"
                    onMouseDown={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      onSearchInputChange(row.text);
                      if (isMobileOverlay) mobileInputRef.current?.focus();
                      else desktopInputRef.current?.focus();
                    }}
                    aria-label={`Isi cepat ${row.text}`}
                    title="Isi ke kotak pencarian"
                    className="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full hover:bg-white/10"
                    style={{ color: 'var(--vt-text-secondary)' }}
                  >
                    <ArrowUpLeft className="h-4 w-4" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      );
    };

    return (
      <header
        className="sticky top-0 z-40 flex h-14 w-full items-center justify-between gap-2 px-3 sm:px-4"
        style={{ backgroundColor: 'var(--vt-bg)' }}
      >
        {mobileSearchOpen ? (
          <div className="flex w-full items-center gap-2 md:hidden">
            <button
              type="button"
              onClick={() => {
                setMobileSearchOpen(false);
                setDropdownOpen(false);
              }}
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
                onFocus={() => setDropdownOpen(true)}
                onChange={(e) => {
                  onSearchInputChange(e.target.value);
                  setDropdownOpen(true);
                }}
                onKeyDown={handleSearchKeyDown}
                placeholder="Telusuri YouTube & VeloxTube"
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
            <button
              type="button"
              onClick={handleVoiceSearch}
              aria-label="Pencarian Suara"
              className={`flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full ${
                isListening ? 'bg-[#FF0000] text-white' : ''
              }`}
              style={!isListening ? { backgroundColor: 'var(--vt-chip)' } : undefined}
            >
              {isListening ? <MicOff className="h-5 w-5" /> : <Mic className="h-5 w-5" />}
            </button>
            {dropdownOpen && renderDropdownList(true)}
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

            {/* Center: Desktop YouTube Search Bar + 120ms Autocomplete Dropdown + Voice Search */}
            <div
              ref={desktopSearchRef}
              className="relative mx-2 hidden max-w-[640px] flex-1 items-center gap-2 md:flex"
            >
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  commitSearch(searchInput);
                }}
                className="flex flex-1 items-center"
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
                    ref={desktopInputRef}
                    type="text"
                    value={searchInput}
                    onFocus={() => {
                      setSearchFocused(true);
                      setDropdownOpen(true);
                    }}
                    onChange={(e) => {
                      onSearchInputChange(e.target.value);
                      setDropdownOpen(true);
                    }}
                    onKeyDown={handleSearchKeyDown}
                    placeholder="Telusuri"
                    aria-label="Telusuri video"
                    className="w-full bg-transparent text-[15px] outline-none"
                  />
                  {searchInput && (
                    <button
                      type="button"
                      onClick={() => {
                        onSearchInputChange('');
                        desktopInputRef.current?.focus();
                      }}
                      aria-label="Hapus pencarian"
                      className="flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-full"
                      onMouseEnter={(e) =>
                        (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')
                      }
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

              <button
                type="button"
                onClick={handleVoiceSearch}
                aria-label="Telusuri dengan suara"
                title="Telusuri dengan suara (id-ID)"
                className={`flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full transition-colors ${
                  isListening ? 'bg-[#FF0000] text-white' : ''
                }`}
                style={!isListening ? { backgroundColor: 'var(--vt-chip)' } : undefined}
              >
                {isListening ? <MicOff className="h-5 w-5" /> : <Mic className="h-5 w-5" />}
              </button>

              {dropdownOpen && renderDropdownList(false)}
            </div>

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
                    <p
                      className="mt-2 text-xs leading-relaxed"
                      style={{ color: 'var(--vt-text-secondary)' }}
                    >
                      Video terbaru dari channel yang kamu subscribe, sinkronisasi akun YouTube, dan
                      status transcode HLS akan muncul di sini.
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
                        className="vt-fade-scale absolute right-0 top-12 z-50 w-72 overflow-hidden rounded-xl border shadow-2xl"
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
                            <p
                              className="truncate text-xs"
                              style={{ color: 'var(--vt-text-secondary)' }}
                            >
                              {user.handle}
                            </p>
                            {user.youtube_connected && (
                              <span className="mt-1 inline-block rounded bg-[#FF0000]/15 px-1.5 py-0.5 text-[10px] font-semibold text-[#FF4E45]">
                                YouTube Tersinkron
                              </span>
                            )}
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
                            onMouseEnter={(e) =>
                              (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')
                            }
                            onMouseLeave={(e) =>
                              (e.currentTarget.style.backgroundColor = 'transparent')
                            }
                          >
                            <User className="h-4 w-4" />
                            <span>Channel &amp; Koleksi Anda</span>
                          </button>

                          <button
                            type="button"
                            onClick={async () => {
                              setProfileOpen(false);
                              try {
                                const u = await connectGoogleYouTube();
                                onNotify(`Akun YouTube (${u.display_name}) berhasil disinkronkan!`);
                              } catch (err) {
                                onNotify(
                                  err instanceof Error
                                    ? err.message
                                    : 'Gagal menghubungkan akun YouTube.'
                                );
                              }
                            }}
                            className="flex w-full cursor-pointer items-center gap-3 px-4 py-2.5 text-sm"
                            onMouseEnter={(e) =>
                              (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')
                            }
                            onMouseLeave={(e) =>
                              (e.currentTarget.style.backgroundColor = 'transparent')
                            }
                          >
                            <RefreshCw className="h-4 w-4 text-[#FF0000]" />
                            <span>
                              {user.youtube_connected
                                ? 'Segarkan Sinkronisasi YouTube'
                                : 'Hubungkan Akun YouTube'}
                            </span>
                          </button>

                          <button
                            type="button"
                            onClick={() => {
                              toggleTheme();
                              setProfileOpen(false);
                            }}
                            className="flex w-full cursor-pointer items-center gap-3 px-4 py-2.5 text-sm"
                            onMouseEnter={(e) =>
                              (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')
                            }
                            onMouseLeave={(e) =>
                              (e.currentTarget.style.backgroundColor = 'transparent')
                            }
                          >
                            {theme === 'dark' ? (
                              <Sun className="h-4 w-4" />
                            ) : (
                              <Moon className="h-4 w-4" />
                            )}
                            <span>{theme === 'dark' ? 'Mode Terang' : 'Mode Gelap'}</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => {
                              logout();
                              setProfileOpen(false);
                              onNotify('Berhasil keluar dari akun.');
                            }}
                            className="flex w-full cursor-pointer items-center gap-3 px-4 py-2.5 text-sm text-red-400"
                            onMouseEnter={(e) =>
                              (e.currentTarget.style.backgroundColor = 'var(--vt-hover)')
                            }
                            onMouseLeave={(e) =>
                              (e.currentTarget.style.backgroundColor = 'transparent')
                            }
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
