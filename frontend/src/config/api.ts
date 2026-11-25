/**
 * Centralized API, OAuth & Offline/Demo Auto-Fallback Configuration for VeloxTube
 * (Web / Vercel, Tauri Desktop .exe, Capacitor Android .apk).
 */

export const CUSTOM_API_URL_KEY = 'veloxtube_custom_api_url_v1';
export const AUTH_TOKEN_KEY = 'veloxtube_access_token_v1';
export const WATCH_LATER_KEY = 'veloxtube_watch_later_v1';

export const GOOGLE_CLIENT_ID: string =
  (import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined)?.trim() ||
  '602927550377-v1dicdufnh5t2l3baq74jlbtg4gold37.apps.googleusercontent.com';

const ENV_API_URL: string = ((import.meta.env.VITE_API_URL as string | undefined)?.trim() || '').replace(
  /\/$/,
  ''
);

function readCustomApiUrl(): string {
  try {
    return (localStorage.getItem(CUSTOM_API_URL_KEY) || '').trim().replace(/\/$/, '');
  } catch {
    return '';
  }
}

export function getConfiguredApiBaseUrl(): string {
  const custom = readCustomApiUrl();
  if (custom) return custom;
  if (ENV_API_URL) return ENV_API_URL;
  return 'http://localhost:8000';
}

export function setCustomApiBaseUrl(url: string): void {
  const cleaned = url.trim().replace(/\/$/, '');
  try {
    if (cleaned) {
      localStorage.setItem(CUSTOM_API_URL_KEY, cleaned);
    } else {
      localStorage.removeItem(CUSTOM_API_URL_KEY);
    }
  } catch {
    // Ignore storage errors
  }
}

export const API_BASE_URL: string = getConfiguredApiBaseUrl();

/**
 * Returns true when running on a remote HTTPS web host (e.g. spotyflow.vercel.app)
 * or inside Capacitor Android / Tauri WITHOUT an explicit VITE_API_URL or custom LAN URL.
 * In that scenario localhost:8000 is unreachable from phones/cloud visitors, so we switch
 * instantaneously to local/demo fallback mode instead of stalling on network timeouts.
 */
export function shouldPreferImmediateDemoFallback(): boolean {
  if (readCustomApiUrl() || ENV_API_URL) return false;
  if (typeof window === 'undefined') return false;

  const host = window.location.hostname.toLowerCase();
  const protocol = window.location.protocol.toLowerCase();

  // Inside Capacitor Android WebView (https://localhost or capacitor://localhost)
  const isCapacitor = Boolean((window as unknown as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor?.isNativePlatform?.());
  if (isCapacitor || protocol === 'capacitor:' || protocol === 'file:') {
    return true;
  }

  // Hosted on Vercel or any non-local domain without VITE_API_URL
  const isLocalHost = host === 'localhost' || host === '127.0.0.1' || host === '[::1]';
  return !isLocalHost;
}

/**
 * Resolves relative media/thumbnail/stream paths returned by FastAPI (e.g. /media/..., /api/media/stream/...)
 * into full URLs pointing at API_BASE_URL while leaving external https:// or blob: URLs untouched.
 */
export function resolveMediaUrl(url: string | null | undefined): string {
  if (!url) return '';
  const trimmed = url.trim();
  if (/^(https?:|data:|blob:)/i.test(trimmed)) {
    return trimmed;
  }
  const normalized = trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
  return `${getConfiguredApiBaseUrl()}${normalized}`;
}
