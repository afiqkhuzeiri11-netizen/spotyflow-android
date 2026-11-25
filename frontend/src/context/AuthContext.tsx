import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { AUTH_TOKEN_KEY } from '../config/api';
import { authApi, extractApiError } from '../services/api';
import type { UserPublic } from '../types/api';

interface AuthContextValue {
  user: UserPublic | null;
  isLoadingAuth: boolean;
  authModalOpen: boolean;
  authModalMode: 'login' | 'register';
  openAuthModal: (mode?: 'login' | 'register') => void;
  closeAuthModal: () => void;
  login: (identifier: string, password: string) => Promise<UserPublic>;
  register: (payload: {
    username: string;
    email: string;
    password: string;
    display_name?: string;
  }) => Promise<UserPublic>;
  loginWithGoogle: (credential: string) => Promise<UserPublic>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserPublic | null>(null);
  const [isLoadingAuth, setIsLoadingAuth] = useState(true);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [authModalMode, setAuthModalMode] = useState<'login' | 'register'>('login');

  // Restore active JWT session from FastAPI GET /api/auth/me on startup
  useEffect(() => {
    let active = true;
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (!token) {
      setIsLoadingAuth(false);
      return;
    }

    authApi
      .me()
      .then((me) => {
        if (active) setUser(me);
      })
      .catch(() => {
        try {
          localStorage.removeItem(AUTH_TOKEN_KEY);
        } catch {
          // Ignore
        }
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setIsLoadingAuth(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const openAuthModal = useCallback((mode: 'login' | 'register' = 'login') => {
    setAuthModalMode(mode);
    setAuthModalOpen(true);
  }, []);

  const closeAuthModal = useCallback(() => {
    setAuthModalOpen(false);
  }, []);

  const login = useCallback(async (identifier: string, password: string): Promise<UserPublic> => {
    try {
      const res = await authApi.login({ identifier, password });
      localStorage.setItem(AUTH_TOKEN_KEY, res.access_token);
      setUser(res.user);
      setAuthModalOpen(false);
      return res.user;
    } catch (err) {
      throw new Error(extractApiError(err, 'Login gagal. Periksa email/username dan password.'));
    }
  }, []);

  const register = useCallback(
    async (payload: {
      username: string;
      email: string;
      password: string;
      display_name?: string;
    }): Promise<UserPublic> => {
      try {
        const res = await authApi.register(payload);
        localStorage.setItem(AUTH_TOKEN_KEY, res.access_token);
        setUser(res.user);
        setAuthModalOpen(false);
        return res.user;
      } catch (err) {
        throw new Error(extractApiError(err, 'Pendaftaran akun gagal.'));
      }
    },
    []
  );

  const loginWithGoogle = useCallback(async (credential: string): Promise<UserPublic> => {
    try {
      const res = await authApi.loginWithGoogle(credential);
      localStorage.setItem(AUTH_TOKEN_KEY, res.access_token);
      setUser(res.user);
      setAuthModalOpen(false);
      return res.user;
    } catch (err) {
      throw new Error(extractApiError(err, 'Verifikasi Login Google gagal di backend.'));
    }
  }, []);

  const logout = useCallback(() => {
    try {
      localStorage.removeItem(AUTH_TOKEN_KEY);
      const g = (
        window as unknown as {
          google?: { accounts?: { id?: { disableAutoSelect?: () => void } } };
        }
      ).google;
      g?.accounts?.id?.disableAutoSelect?.();
    } catch {
      // Ignore
    }
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({
      user,
      isLoadingAuth,
      authModalOpen,
      authModalMode,
      openAuthModal,
      closeAuthModal,
      login,
      register,
      loginWithGoogle,
      logout,
    }),
    [
      user,
      isLoadingAuth,
      authModalOpen,
      authModalMode,
      openAuthModal,
      closeAuthModal,
      login,
      register,
      loginWithGoogle,
      logout,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth harus digunakan di dalam AuthProvider');
  return ctx;
}
