import React, { useEffect, useRef, useState } from 'react';
import { Loader2, Lock, Mail, RefreshCw, User, X } from 'lucide-react';
import { GOOGLE_CLIENT_ID } from '../../config/api';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';

interface GoogleAccountsIdApi {
  initialize: (config: {
    client_id: string;
    callback: (response: { credential?: string }) => void;
    auto_select?: boolean;
  }) => void;
  renderButton: (
    parent: HTMLElement,
    options: {
      theme?: 'outline' | 'filled_blue' | 'filled_black';
      size?: 'large' | 'medium' | 'small';
      shape?: 'pill' | 'rectangular';
      text?: 'signin_with' | 'continue_with';
      width?: number;
      locale?: string;
    }
  ) => void;
}

interface AuthModalProps {
  onSuccessToast: (msg: string) => void;
}

export const AuthModal: React.FC<AuthModalProps> = ({ onSuccessToast }) => {
  const { theme } = useTheme();
  const {
    authModalOpen,
    authModalMode,
    closeAuthModal,
    login,
    register,
    loginWithGoogle,
    connectGoogleYouTube,
  } = useAuth();

  const [mode, setMode] = useState<'login' | 'register'>(authModalMode);
  const [identifier, setIdentifier] = useState('');
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const gsiRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setMode(authModalMode);
    setError(null);
  }, [authModalMode, authModalOpen]);

  // Render official SpotyFlow/VeloxTube Google Sign-In button via GIS
  useEffect(() => {
    if (!authModalOpen) return;

    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeAuthModal();
    };
    window.addEventListener('keydown', handleKey);

    let tries = 0;
    const renderGoogleButton = () => {
      const g = (
        window as unknown as { google?: { accounts?: { id?: GoogleAccountsIdApi } } }
      ).google;
      const api = g?.accounts?.id;
      const el = gsiRef.current;
      if (api && el) {
        try {
          el.innerHTML = '';
          api.initialize({
            client_id: GOOGLE_CLIENT_ID,
            callback: async (resp) => {
              if (!resp?.credential) return;
              setSubmitting(true);
              setError(null);
              try {
                const u = await loginWithGoogle(resp.credential);
                onSuccessToast(`Berhasil login dengan Google sebagai ${u.display_name}`);
              } catch (err) {
                setError(err instanceof Error ? err.message : 'Login Google gagal.');
              } finally {
                setSubmitting(false);
              }
            },
            auto_select: false,
          });
          api.renderButton(el, {
            theme: theme === 'dark' ? 'filled_black' : 'outline',
            size: 'large',
            shape: 'pill',
            text: 'continue_with',
            width: 340,
            locale: 'id',
          });
          return true;
        } catch {
          return false;
        }
      }
      return false;
    };

    if (!renderGoogleButton()) {
      const timer = window.setInterval(() => {
        tries += 1;
        if (renderGoogleButton() || tries >= 14) {
          window.clearInterval(timer);
        }
      }, 250);
      return () => {
        window.clearInterval(timer);
        window.removeEventListener('keydown', handleKey);
      };
    }

    return () => window.removeEventListener('keydown', handleKey);
  }, [authModalOpen, theme, closeAuthModal, loginWithGoogle, onSuccessToast]);

  if (!authModalOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      if (mode === 'login') {
        const u = await login(identifier.trim(), password);
        onSuccessToast(`Selamat datang kembali, ${u.display_name}!`);
      } else {
        const u = await register({
          username: username.trim(),
          email: email.trim(),
          password,
          display_name: displayName.trim() || username.trim(),
        });
        onSuccessToast(`Akun ${u.handle} berhasil dibuat!`);
      }
      setPassword('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Autentikasi gagal.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSeededCreatorLogin = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const u = await login('gadgetlab@veloxtube.id', 'veloxtube123');
      onSuccessToast(`Login akun kreator ${u.display_name} berhasil!`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Gagal login akun uji coba.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
      onClick={closeAuthModal}
      role="dialog"
      aria-modal="true"
      aria-labelledby="vt-auth-heading"
    >
      <div
        className="vt-fade-scale w-full max-w-md rounded-2xl border p-6 shadow-2xl"
        style={{
          backgroundColor: 'var(--vt-surface)',
          borderColor: 'var(--vt-border)',
          color: 'var(--vt-text-primary)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="inline-flex h-6 w-9 items-center justify-center rounded-md bg-[#FF0000]">
              <svg className="h-3.5 w-3.5 fill-white" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M8 5v14l11-7z" />
              </svg>
            </span>
            <span className="text-lg font-bold tracking-tight">VeloxTube</span>
          </div>
          <button
            type="button"
            onClick={closeAuthModal}
            aria-label="Tutup modal login"
            className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-full transition-opacity hover:opacity-80"
            style={{ backgroundColor: 'var(--vt-chip)' }}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Mode Tabs */}
        <div
          className="mt-5 grid grid-cols-2 rounded-xl p-1"
          style={{ backgroundColor: 'var(--vt-bg)' }}
        >
          <button
            type="button"
            onClick={() => {
              setMode('login');
              setError(null);
            }}
            className="cursor-pointer rounded-lg py-2 text-sm font-semibold transition-opacity"
            style={{
              backgroundColor: mode === 'login' ? 'var(--vt-chip)' : 'transparent',
              color: mode === 'login' ? 'var(--vt-text-primary)' : 'var(--vt-text-secondary)',
            }}
          >
            Login
          </button>
          <button
            type="button"
            onClick={() => {
              setMode('register');
              setError(null);
            }}
            className="cursor-pointer rounded-lg py-2 text-sm font-semibold transition-opacity"
            style={{
              backgroundColor: mode === 'register' ? 'var(--vt-chip)' : 'transparent',
              color: mode === 'register' ? 'var(--vt-text-primary)' : 'var(--vt-text-secondary)',
            }}
          >
            Daftar Akun
          </button>
        </div>

        <h2 id="vt-auth-heading" className="mt-4 text-lg font-bold">
          {mode === 'login' ? 'Masuk ke VeloxTube' : 'Buat Channel VeloxTube'}
        </h2>

        {/* Official Google OAuth 2.0 Sign-In & Full YouTube Sync */}
        <div className="mt-4 flex flex-col items-center gap-2.5">
          <div ref={gsiRef} className="flex min-h-[44px] w-full items-center justify-center" />
          <button
            type="button"
            disabled={submitting}
            onClick={async () => {
              setSubmitting(true);
              setError(null);
              try {
                const u = await connectGoogleYouTube();
                onSuccessToast(
                  `Akun YouTube (${u.display_name}) tersinkron! Subscriptions, Liked & Playlist aktif.`
                );
              } catch (err) {
                setError(err instanceof Error ? err.message : 'Sinkronisasi YouTube gagal.');
              } finally {
                setSubmitting(false);
              }
            }}
            className="flex w-full cursor-pointer items-center justify-center gap-2 rounded-full border px-4 py-2 text-xs font-semibold transition-colors hover:bg-white/10 disabled:opacity-50"
            style={{ borderColor: 'var(--vt-border)', color: 'var(--vt-text-primary)' }}
          >
            <RefreshCw className="h-3.5 w-3.5 text-[#FF0000]" />
            <span>Sinkronkan Akun Google &amp; YouTube (Subscriptions, Like &amp; Playlist)</span>
          </button>
        </div>

        <div className="my-4 flex items-center gap-3">
          <span className="h-px flex-1" style={{ backgroundColor: 'var(--vt-border)' }} />
          <span className="text-xs" style={{ color: 'var(--vt-text-secondary)' }}>
            atau dengan email &amp; password
          </span>
          <span className="h-px flex-1" style={{ backgroundColor: 'var(--vt-border)' }} />
        </div>

        {error && (
          <div className="mb-3 rounded-xl border border-red-500/30 bg-red-500/10 px-3.5 py-2.5 text-xs font-medium text-red-400">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3">
          {mode === 'register' && (
            <>
              <div>
                <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
                  Username (huruf kecil/angka)
                </label>
                <div
                  className="flex items-center gap-2 rounded-xl border px-3 py-2.5"
                  style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
                >
                  <User className="h-4 w-4 shrink-0 opacity-60" />
                  <input
                    type="text"
                    required
                    minLength={3}
                    maxLength={40}
                    pattern="^[a-zA-Z0-9_.-]+$"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="contoh: afiq_creator"
                    className="w-full bg-transparent text-sm outline-none"
                  />
                </div>
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
                  Nama Tampilan Channel
                </label>
                <div
                  className="flex items-center gap-2 rounded-xl border px-3 py-2.5"
                  style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
                >
                  <User className="h-4 w-4 shrink-0 opacity-60" />
                  <input
                    type="text"
                    maxLength={80}
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="contoh: Afiq Studio"
                    className="w-full bg-transparent text-sm outline-none"
                  />
                </div>
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
                  Email
                </label>
                <div
                  className="flex items-center gap-2 rounded-xl border px-3 py-2.5"
                  style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
                >
                  <Mail className="h-4 w-4 shrink-0 opacity-60" />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="nama@email.com"
                    className="w-full bg-transparent text-sm outline-none"
                  />
                </div>
              </div>
            </>
          )}

          {mode === 'login' && (
            <div>
              <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
                Email atau Username
              </label>
              <div
                className="flex items-center gap-2 rounded-xl border px-3 py-2.5"
                style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
              >
                <Mail className="h-4 w-4 shrink-0 opacity-60" />
                <input
                  type="text"
                  required
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  placeholder="gadgetlab@veloxtube.id"
                  className="w-full bg-transparent text-sm outline-none"
                />
              </div>
            </div>
          )}

          <div>
            <label className="mb-1 block text-xs font-medium" style={{ color: 'var(--vt-text-secondary)' }}>
              Password
            </label>
            <div
              className="flex items-center gap-2 rounded-xl border px-3 py-2.5"
              style={{ backgroundColor: 'var(--vt-bg)', borderColor: 'var(--vt-border)' }}
            >
              <Lock className="h-4 w-4 shrink-0 opacity-60" />
              <input
                type="password"
                required
                minLength={6}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Minimal 6 karakter"
                className="w-full bg-transparent text-sm outline-none"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="mt-2 flex w-full cursor-pointer items-center justify-center gap-2 rounded-full bg-[#FF0000] py-2.5 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
            <span>{mode === 'login' ? 'Masuk' : 'Daftar Sekarang'}</span>
          </button>
        </form>

        {mode === 'login' && (
          <button
            type="button"
            disabled={submitting}
            onClick={handleSeededCreatorLogin}
            className="mt-3 w-full cursor-pointer rounded-full py-2 text-xs font-medium transition-opacity hover:opacity-90"
            style={{
              backgroundColor: 'var(--vt-chip)',
              color: 'var(--vt-text-primary)',
            }}
          >
            Uji cepat: Login sebagai GadgetLab ID (Seeded Backend)
          </button>
        )}
      </div>
    </div>
  );
};
