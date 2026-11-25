import React from 'react';
import { Home, Plus, Tv2, User, Zap } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import type { SidebarKey } from './Sidebar';

interface MobileBottomNavProps {
  activeKey: SidebarKey;
  onSelect: (key: SidebarKey) => void;
  onOpenUpload: () => void;
  onProfileClick: () => void;
}

export const MobileBottomNav: React.FC<MobileBottomNavProps> = React.memo(
  ({ activeKey, onSelect, onOpenUpload, onProfileClick }) => {
    const { user } = useAuth();

    return (
      <nav
        aria-label="Navigasi seluler"
        className="fixed bottom-0 left-0 right-0 z-40 border-t pb-[env(safe-area-inset-bottom)] md:hidden"
        style={{
          backgroundColor: 'var(--vt-bg)',
          borderColor: 'var(--vt-border)',
        }}
      >
        <div className="grid h-14 grid-cols-5 items-center">
          <button
            type="button"
            onClick={() => onSelect('home')}
            className="flex h-full cursor-pointer flex-col items-center justify-center gap-0.5"
            style={{
              color: activeKey === 'home' ? 'var(--vt-text-primary)' : 'var(--vt-text-secondary)',
            }}
          >
            <Home className="h-[22px] w-[22px]" strokeWidth={activeKey === 'home' ? 2.4 : 1.8} />
            <span className="text-[10px] font-medium">Beranda</span>
          </button>

          <button
            type="button"
            onClick={() => onSelect('shorts')}
            className="flex h-full cursor-pointer flex-col items-center justify-center gap-0.5"
            style={{
              color: activeKey === 'shorts' ? 'var(--vt-text-primary)' : 'var(--vt-text-secondary)',
            }}
          >
            <Zap className="h-[22px] w-[22px]" strokeWidth={activeKey === 'shorts' ? 2.4 : 1.8} />
            <span className="text-[10px] font-medium">Shorts</span>
          </button>

          <div className="flex items-center justify-center">
            <button
              type="button"
              onClick={onOpenUpload}
              aria-label="Upload video"
              className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full border transition-transform active:scale-95"
              style={{
                backgroundColor: 'var(--vt-chip)',
                borderColor: 'var(--vt-border)',
                color: 'var(--vt-text-primary)',
              }}
            >
              <Plus className="h-6 w-6" strokeWidth={2} />
            </button>
          </div>

          <button
            type="button"
            onClick={() => onSelect('subscriptions')}
            className="flex h-full cursor-pointer flex-col items-center justify-center gap-0.5"
            style={{
              color:
                activeKey === 'subscriptions'
                  ? 'var(--vt-text-primary)'
                  : 'var(--vt-text-secondary)',
            }}
          >
            <Tv2
              className="h-[22px] w-[22px]"
              strokeWidth={activeKey === 'subscriptions' ? 2.4 : 1.8}
            />
            <span className="text-[10px] font-medium">Subscription</span>
          </button>

          <button
            type="button"
            onClick={onProfileClick}
            className="flex h-full cursor-pointer flex-col items-center justify-center gap-0.5"
            style={{
              color:
                activeKey === 'watch_later'
                  ? 'var(--vt-text-primary)'
                  : 'var(--vt-text-secondary)',
            }}
          >
            {user?.avatar_url ? (
              <img
                src={user.avatar_url}
                alt={user.display_name}
                className="h-[22px] w-[22px] rounded-full object-cover"
              />
            ) : (
              <User className="h-[22px] w-[22px]" strokeWidth={1.8} />
            )}
            <span className="text-[10px] font-medium">{user ? 'Profil' : 'Login'}</span>
          </button>
        </div>
      </nav>
    );
  }
);

MobileBottomNav.displayName = 'MobileBottomNav';
