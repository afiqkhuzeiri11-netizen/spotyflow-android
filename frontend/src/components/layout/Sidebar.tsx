import React from 'react';
import {
  Clock,
  History,
  Home,
  Library,
  Menu,
  ThumbsUp,
  Tv2,
  UserCircle2,
  Zap,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

export type SidebarKey = 'home' | 'shorts' | 'subscriptions' | 'history' | 'watch_later' | 'liked';

interface SidebarProps {
  expanded: boolean;
  mobileOpen: boolean;
  activeKey: SidebarKey;
  onSelect: (key: SidebarKey) => void;
  onCloseMobile: () => void;
}

const NavButton: React.FC<{
  icon: React.ReactNode;
  label: string;
  active: boolean;
  onClick: () => void;
}> = ({ icon, label, active, onClick }) => (
  <button
    type="button"
    onClick={onClick}
    className="flex w-full cursor-pointer items-center gap-5 rounded-xl px-3 py-2.5 text-left text-sm transition-colors"
    style={{
      backgroundColor: active ? 'var(--vt-chip)' : 'transparent',
      fontWeight: active ? 600 : 400,
      color: 'var(--vt-text-primary)',
    }}
    onMouseEnter={(e) => {
      if (!active) e.currentTarget.style.backgroundColor = 'var(--vt-hover)';
    }}
    onMouseLeave={(e) => {
      if (!active) e.currentTarget.style.backgroundColor = 'transparent';
    }}
  >
    <span className="shrink-0">{icon}</span>
    <span className="truncate">{label}</span>
  </button>
);

export const Sidebar: React.FC<SidebarProps> = React.memo(
  ({ expanded, mobileOpen, activeKey, onSelect, onCloseMobile }) => {
    const { user, openAuthModal } = useAuth();

    const renderFull = (isDrawer: boolean) => (
      <div className="flex h-full flex-col overflow-y-auto px-3 pb-6 pt-2">
        {isDrawer && (
          <div className="mb-3 flex items-center gap-3 px-1 pt-1">
            <button
              type="button"
              onClick={onCloseMobile}
              aria-label="Tutup menu"
              className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div className="flex items-center gap-1.5">
              <span className="inline-flex h-[22px] w-[32px] items-center justify-center rounded-[6px] bg-[#FF0000]">
                <svg className="h-3 w-3 fill-white" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M8 5v14l11-7z" />
                </svg>
              </span>
              <span className="text-lg font-bold tracking-tighter">VeloxTube</span>
            </div>
          </div>
        )}

        <nav className="space-y-0.5" aria-label="Menu utama">
          <NavButton
            icon={<Home className="h-5 w-5" />}
            label="Beranda"
            active={activeKey === 'home'}
            onClick={() => {
              onSelect('home');
              if (isDrawer) onCloseMobile();
            }}
          />
          <NavButton
            icon={<Zap className="h-5 w-5" />}
            label="Shorts"
            active={activeKey === 'shorts'}
            onClick={() => {
              onSelect('shorts');
              if (isDrawer) onCloseMobile();
            }}
          />
          <NavButton
            icon={<Tv2 className="h-5 w-5" />}
            label="Subscription"
            active={activeKey === 'subscriptions'}
            onClick={() => {
              onSelect('subscriptions');
              if (isDrawer) onCloseMobile();
            }}
          />
        </nav>

        <hr className="my-3 border-0" style={{ height: '1px', backgroundColor: 'var(--vt-border)' }} />

        <div className="space-y-0.5">
          <p className="px-3 pb-1 text-sm font-semibold">Anda</p>
          <NavButton
            icon={<History className="h-5 w-5" />}
            label="Riwayat"
            active={activeKey === 'history'}
            onClick={() => {
              onSelect('history');
              if (isDrawer) onCloseMobile();
            }}
          />
          <NavButton
            icon={<Clock className="h-5 w-5" />}
            label="Tonton Nanti"
            active={activeKey === 'watch_later'}
            onClick={() => {
              onSelect('watch_later');
              if (isDrawer) onCloseMobile();
            }}
          />
          <NavButton
            icon={<ThumbsUp className="h-5 w-5" />}
            label="Video yang Disukai"
            active={activeKey === 'liked'}
            onClick={() => {
              onSelect('liked');
              if (isDrawer) onCloseMobile();
            }}
          />
        </div>

        {!user && (
          <>
            <hr className="my-3 border-0" style={{ height: '1px', backgroundColor: 'var(--vt-border)' }} />
            <div className="px-3 py-1">
              <p className="text-xs leading-relaxed" style={{ color: 'var(--vt-text-secondary)' }}>
                Login dengan akun Google atau email untuk upload video, like, komentar, dan subscribe.
              </p>
              <button
                type="button"
                onClick={() => {
                  openAuthModal('login');
                  if (isDrawer) onCloseMobile();
                }}
                className="mt-3 inline-flex cursor-pointer items-center gap-2 rounded-full border px-3.5 py-1.5 text-sm font-medium text-[#3EA6FF] hover:bg-[#3EA6FF]/15"
                style={{ borderColor: 'var(--vt-border)' }}
              >
                <UserCircle2 className="h-5 w-5" />
                <span>Login</span>
              </button>
            </div>
          </>
        )}

        <p className="mt-auto px-3 pt-6 text-[11px]" style={{ color: 'var(--vt-text-secondary)' }}>
          © 2026 VeloxTube • FastAPI + HLS
        </p>
      </div>
    );

    return (
      <>
        <aside
          className={`sticky top-14 hidden h-[calc(100vh-3.5rem)] shrink-0 md:block ${
            expanded ? 'w-[240px]' : 'w-[72px]'
          }`}
          style={{ backgroundColor: 'var(--vt-bg)' }}
        >
          {expanded ? (
            renderFull(false)
          ) : (
            <nav aria-label="Menu mini" className="flex flex-col items-center gap-1 px-1 pt-2">
              {(
                [
                  { key: 'home', label: 'Beranda', icon: <Home className="h-5 w-5" /> },
                  { key: 'shorts', label: 'Shorts', icon: <Zap className="h-5 w-5" /> },
                  { key: 'subscriptions', label: 'Subscription', icon: <Tv2 className="h-5 w-5" /> },
                  { key: 'watch_later', label: 'Koleksi', icon: <Library className="h-5 w-5" /> },
                ] as const
              ).map((item) => {
                const active = activeKey === item.key;
                return (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => onSelect(item.key)}
                    className="flex w-16 cursor-pointer flex-col items-center justify-center gap-1.5 rounded-xl py-3.5 text-[10px] transition-colors"
                    style={{
                      backgroundColor: active ? 'var(--vt-chip)' : 'transparent',
                      fontWeight: active ? 600 : 400,
                      color: 'var(--vt-text-primary)',
                    }}
                    onMouseEnter={(e) => {
                      if (!active) e.currentTarget.style.backgroundColor = 'var(--vt-hover)';
                    }}
                    onMouseLeave={(e) => {
                      if (!active) e.currentTarget.style.backgroundColor = 'transparent';
                    }}
                  >
                    {item.icon}
                    <span className="max-w-full truncate px-1">{item.label}</span>
                  </button>
                );
              })}
            </nav>
          )}
        </aside>

        {mobileOpen && (
          <div
            className="fixed inset-0 z-50 bg-black/60 md:hidden"
            onClick={onCloseMobile}
            aria-hidden="true"
          />
        )}
        <aside
          className={`vt-drawer fixed left-0 top-0 z-50 h-full w-[256px] shadow-2xl md:hidden ${
            mobileOpen ? 'vt-drawer-open' : ''
          }`}
          style={{ backgroundColor: 'var(--vt-bg)' }}
          aria-hidden={!mobileOpen}
        >
          {renderFull(true)}
        </aside>
      </>
    );
  }
);

Sidebar.displayName = 'Sidebar';
