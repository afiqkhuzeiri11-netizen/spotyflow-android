import { useEffect, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import { ScreenOrientation } from '@capacitor/screen-orientation';
import { StatusBar, Style } from '@capacitor/status-bar';
import type { ThemeMode } from '../types/api';

export function isNativeAndroid(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
}

export async function enterNativeFullscreenOrientation(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    await StatusBar.hide();
  } catch {
    // Ignore on unsupported devices
  }
  try {
    await ScreenOrientation.lock({ orientation: 'landscape' });
  } catch {
    // Ignore on unsupported devices
  }
}

export async function exitNativeFullscreenOrientation(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    await ScreenOrientation.lock({ orientation: 'portrait' });
  } catch {
    // Ignore on unsupported devices
  }
  try {
    await StatusBar.show();
  } catch {
    // Ignore on unsupported devices
  }
}

interface UseNativeBridgeOptions {
  theme: ThemeMode;
  pathname: string;
  hasActiveVideo: boolean;
  isMiniplayer: boolean;
  onEnterMiniplayerFromWatch: () => void;
  onNavigateHome: () => void;
  onNotify: (msg: string) => void;
}

/**
 * Native Android (Capacitor) Bridge:
 * 1. Syncs StatusBar color & icon contrast with Dark (#0F0F0F) / Light (#FFFFFF) mode.
 * 2. Handles Android Hardware Back Button:
 *    - In Fullscreen -> exits fullscreen first.
 *    - On /watch/:id -> shrinks active video to Floating Miniplayer & navigates Home.
 *    - On /shorts -> navigates Home.
 *    - On Home -> double-press Back within 2s exits the app cleanly.
 */
export function useNativeBridge({
  theme,
  pathname,
  hasActiveVideo,
  isMiniplayer,
  onEnterMiniplayerFromWatch,
  onNavigateHome,
  onNotify,
}: UseNativeBridgeOptions): void {
  const lastBackPressRef = useRef<number>(0);

  // Sync StatusBar theme color & icon style
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    const applyStatusBar = async () => {
      try {
        await StatusBar.setOverlaysWebView({ overlay: false });
        if (theme === 'dark') {
          await StatusBar.setBackgroundColor({ color: '#0F0F0F' });
          await StatusBar.setStyle({ style: Style.Dark });
        } else {
          await StatusBar.setBackgroundColor({ color: '#FFFFFF' });
          await StatusBar.setStyle({ style: Style.Light });
        }
      } catch {
        // Ignore on platforms where StatusBar plugin is unavailable
      }
    };
    applyStatusBar();
  }, [theme]);

  // Sync orientation & immersive mode whenever browser Fullscreen API changes
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    const onFullscreenChange = () => {
      if (document.fullscreenElement) {
        enterNativeFullscreenOrientation();
      } else {
        exitNativeFullscreenOrientation();
      }
    };
    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange);
  }, []);

  // Hardware Back Button listener (Android)
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;

    const listenerPromise = CapApp.addListener('backButton', () => {
      // 1. If in fullscreen, exit fullscreen first
      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
        exitNativeFullscreenOrientation();
        return;
      }

      // 2. If on Watch Page -> shrink into Floating Miniplayer & return Home
      if (pathname.startsWith('/watch/')) {
        if (hasActiveVideo && !isMiniplayer) {
          onEnterMiniplayerFromWatch();
        } else {
          onNavigateHome();
        }
        return;
      }

      // 3. If on Shorts or any secondary route -> return Home
      if (pathname !== '/') {
        onNavigateHome();
        return;
      }

      // 4. On Home -> press Back twice within 2 seconds to exit
      const now = Date.now();
      if (now - lastBackPressRef.current < 2000) {
        CapApp.exitApp();
      } else {
        lastBackPressRef.current = now;
        onNotify('Tekan kembali sekali lagi untuk keluar dari VeloxTube');
      }
    });

    return () => {
      listenerPromise.then((handle) => handle.remove()).catch(() => {});
    };
  }, [
    pathname,
    hasActiveVideo,
    isMiniplayer,
    onEnterMiniplayerFromWatch,
    onNavigateHome,
    onNotify,
  ]);
}
