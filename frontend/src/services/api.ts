import axios from 'axios';
import {
  AUTH_TOKEN_KEY,
  getConfiguredApiBaseUrl,
  resolveMediaUrl,
  shouldPreferImmediateDemoFallback,
} from '../config/api';
import type {
  AuthTokenResponse,
  CommentItem,
  CommentLikeResponse,
  CommentListResponse,
  CommentSort,
  LikeResponse,
  MassiveSearchResponse,
  SearchFilterId,
  ShortsListResponse,
  SubscribeResponse,
  UploadVideoResponse,
  UserPublic,
  VideoCardItem,
  VideoDetailItem,
  VideoListResponse,
  WatchHistoryItem,
  YouTubeChannelSub,
  YouTubePlaylistItem,
  YouTubeStreamInfo,
} from '../types/api';
import { demoFallback } from './demoFallback';

export type BackendConnectionMode = 'online' | 'demo';

let backendMode: BackendConnectionMode = shouldPreferImmediateDemoFallback() ? 'demo' : 'online';
let backendDownUntil = backendMode === 'demo' ? Number.MAX_SAFE_INTEGER : 0;

const prefetchedKeys = new Set<string>();

export function getBackendConnectionMode(): BackendConnectionMode {
  return backendMode;
}

export function resetBackendConnectionMode(): void {
  backendMode = shouldPreferImmediateDemoFallback() ? 'demo' : 'online';
  backendDownUntil = backendMode === 'demo' ? Number.MAX_SAFE_INTEGER : 0;
  apiClient.defaults.baseURL = getConfiguredApiBaseUrl();
}

export const apiClient = axios.create({
  baseURL: getConfiguredApiBaseUrl(),
  timeout: 6500,
});

// Automatically attach JWT Bearer token from localStorage to every request
apiClient.interceptors.request.use((config) => {
  config.baseURL = getConfiguredApiBaseUrl();
  try {
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
  } catch {
    // Ignore storage access errors
  }
  return config;
});

function isRecoverableNetworkFailure(err: unknown): boolean {
  if (!axios.isAxiosError(err)) return false;
  if (!err.response) return true;
  return err.response.status >= 502;
}

async function withBackendFallback<T>(
  remoteCall: () => Promise<T>,
  fallbackCall: () => T | Promise<T>
): Promise<T> {
  if (Date.now() < backendDownUntil) {
    backendMode = 'demo';
    return await fallbackCall();
  }

  try {
    const result = await remoteCall();
    backendMode = 'online';
    backendDownUntil = 0;
    return result;
  } catch (err) {
    if (isRecoverableNetworkFailure(err)) {
      backendMode = 'demo';
      backendDownUntil = Date.now() + 30_000;
      return await fallbackCall();
    }
    throw err;
  }
}

function normalizeVideoMedia(video: VideoCardItem): VideoCardItem {
  return {
    ...video,
    thumbnail_url: resolveMediaUrl(video.thumbnail_url),
    hls_url: video.hls_url ? resolveMediaUrl(video.hls_url) : null,
    mp4_url: resolveMediaUrl(video.mp4_url),
    channel: {
      ...video.channel,
      avatar_url: video.channel.avatar_url ? resolveMediaUrl(video.channel.avatar_url) : null,
    },
  };
}

function normalizeUserMedia(user: UserPublic): UserPublic {
  return {
    ...user,
    avatar_url: user.avatar_url ? resolveMediaUrl(user.avatar_url) : null,
  };
}

function normalizeCommentMedia(comment: CommentItem): CommentItem {
  return {
    ...comment,
    author: {
      ...comment.author,
      avatar_url: comment.author.avatar_url ? resolveMediaUrl(comment.author.avatar_url) : null,
    },
    replies: (comment.replies || []).map((r) => ({
      ...r,
      author: {
        ...r.author,
        avatar_url: r.author.avatar_url ? resolveMediaUrl(r.author.avatar_url) : null,
      },
    })),
  };
}

export function extractApiError(err: unknown, fallback = 'Terjadi kesalahan jaringan.'): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { detail?: string | Array<{ msg?: string }> } | undefined;
    if (typeof data?.detail === 'string' && data.detail.trim()) {
      return data.detail;
    }
    if (Array.isArray(data?.detail) && data.detail[0]?.msg) {
      return data.detail[0].msg;
    }
    if (err.code === 'ERR_NETWORK') {
      return `Server backend (${getConfiguredApiBaseUrl()}) tidak terjangkau. Beralih ke mode lokal.`;
    }
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

export const authApi = {
  async register(payload: {
    username: string;
    email: string;
    password: string;
    display_name?: string;
  }): Promise<AuthTokenResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<AuthTokenResponse>('/api/auth/register', payload);
        return { ...data, user: normalizeUserMedia(data.user) };
      },
      () => demoFallback.register(payload)
    );
  },

  async login(payload: { identifier: string; password: string }): Promise<AuthTokenResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<AuthTokenResponse>('/api/auth/login', payload);
        return { ...data, user: normalizeUserMedia(data.user) };
      },
      () => demoFallback.login(payload)
    );
  },

  async loginWithGoogle(credential: string): Promise<AuthTokenResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<AuthTokenResponse>('/api/auth/google', { credential });
        return { ...data, user: normalizeUserMedia(data.user) };
      },
      () => demoFallback.loginWithGoogle(credential)
    );
  },

  async loginWithGoogleOAuth(accessToken: string): Promise<AuthTokenResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<AuthTokenResponse>('/api/auth/google/oauth', {
          access_token: accessToken,
        });
        return { ...data, user: normalizeUserMedia(data.user) };
      },
      async () => {
        // Offline / Vercel-only direct Google userinfo resolution
        try {
          const resp = await axios.get<{
            sub?: string;
            email?: string;
            name?: string;
            picture?: string;
          }>('https://www.googleapis.com/oauth2/v3/userinfo', {
            headers: { Authorization: `Bearer ${accessToken}` },
          });
          const fakeJwt = btoa(
            JSON.stringify({
              email: resp.data.email || 'user@gmail.com',
              name: resp.data.name || 'Pengguna Google',
              picture: resp.data.picture || null,
            })
          );
          const res = demoFallback.loginWithGoogle(`eyJ.${fakeJwt}.sig`);
          res.user.youtube_connected = true;
          res.user.google_user_id = resp.data.sub || null;
          return res;
        } catch {
          const res = demoFallback.login({ identifier: 'google_user@gmail.com', password: 'oauth' });
          res.user.youtube_connected = true;
          return res;
        }
      }
    );
  },

  async me(): Promise<UserPublic> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<UserPublic>('/api/auth/me');
        return normalizeUserMedia(data);
      },
      () => demoFallback.me()
    );
  },
};

export interface FetchVideosQuery {
  page?: number;
  limit?: number;
  category?: string;
  search?: string;
  channel_id?: number;
  subscribed_only?: boolean;
}

export const videosApi = {
  async list(params: FetchVideosQuery): Promise<VideoListResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<VideoListResponse>('/api/videos', {
          params: {
            page: params.page ?? 1,
            limit: params.limit ?? 12,
            category: params.category && params.category !== 'all' ? params.category : undefined,
            search: params.search?.trim() ? params.search.trim() : undefined,
            channel_id: params.channel_id,
            subscribed_only: params.subscribed_only || undefined,
          },
        });
        return {
          ...data,
          items: data.items.map(normalizeVideoMedia),
        };
      },
      () => demoFallback.listVideos(params)
    );
  },

  async shorts(page = 1, limit = 10): Promise<ShortsListResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<ShortsListResponse>('/api/shorts', {
          params: { page, limit },
        });
        return {
          ...data,
          items: data.items.map(normalizeVideoMedia),
        };
      },
      () => demoFallback.listShorts(page, limit)
    );
  },

  async detail(videoId: number): Promise<VideoDetailItem> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<VideoDetailItem>(`/api/videos/${videoId}`);
        const base = normalizeVideoMedia(data);
        return {
          ...data,
          ...base,
          recommendations: (data.recommendations || []).map(normalizeVideoMedia),
        };
      },
      () => demoFallback.detail(videoId)
    );
  },

  async youtubeStream(ytId: string): Promise<YouTubeStreamInfo> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<YouTubeStreamInfo>(`/api/yt/stream/${ytId}`, {
          timeout: 12000,
        });
        return data;
      },
      () => demoFallback.youtubeStream(ytId)
    );
  },

  prefetch(videoKey: string): void {
    const key = videoKey.trim();
    if (!key || prefetchedKeys.has(key) || Date.now() < backendDownUntil) return;
    prefetchedKeys.add(key);
    if (prefetchedKeys.size > 150) {
      const first = prefetchedKeys.values().next().value;
      if (first) prefetchedKeys.delete(first);
    }
    apiClient.post(`/api/videos/${encodeURIComponent(key)}/prefetch`).catch(() => {
      // Silent background prefetch
    });
  },

  async upload(
    formData: FormData,
    onProgress?: (percent: number) => void
  ): Promise<UploadVideoResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<UploadVideoResponse>('/api/videos/upload', formData, {
          timeout: 0,
          headers: { 'Content-Type': 'multipart/form-data' },
          onUploadProgress: (evt) => {
            if (!onProgress) return;
            const total = evt.total || 0;
            if (total > 0) {
              onProgress(Math.min(100, Math.round((evt.loaded * 100) / total)));
            }
          },
        });
        return {
          ...data,
          video: normalizeVideoMedia(data.video),
        };
      },
      () => {
        onProgress?.(100);
        return demoFallback.uploadVideo(formData);
      }
    );
  },

  async react(videoId: number, action: 'like' | 'dislike'): Promise<LikeResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<LikeResponse>(`/api/videos/${videoId}/like`, {
          action,
        });
        return data;
      },
      () => demoFallback.react(videoId, action)
    );
  },

  async subscribe(channelId: number): Promise<SubscribeResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<SubscribeResponse>(
          `/api/channels/${channelId}/subscribe`
        );
        return data;
      },
      () => demoFallback.subscribe(channelId)
    );
  },

  async comments(videoId: number, sort: CommentSort = 'top'): Promise<CommentListResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<CommentListResponse>(
          `/api/videos/${videoId}/comments`,
          { params: { sort } }
        );
        return {
          ...data,
          items: data.items.map(normalizeCommentMedia),
        };
      },
      () => demoFallback.comments(videoId, sort)
    );
  },

  async addComment(
    videoId: number,
    content: string,
    parentId?: number | null
  ): Promise<CommentItem> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<CommentItem>(`/api/videos/${videoId}/comments`, {
          content,
          parent_id: parentId ?? null,
        });
        return normalizeCommentMedia(data);
      },
      () => demoFallback.addComment(videoId, content, parentId)
    );
  },

  async likeComment(commentId: number): Promise<CommentLikeResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<CommentLikeResponse>(
          `/api/comments/${commentId}/like`
        );
        return data;
      },
      () => demoFallback.likeComment(commentId)
    );
  },
};

const CLOUD_SEARCH_ORIGIN = 'https://spotyflow.vercel.app';

function getCloudSearchBase(): string {
  if (typeof window !== 'undefined' && window.location.hostname.endsWith('vercel.app')) {
    return '';
  }
  return CLOUD_SEARCH_ORIGIN;
}

export const searchApi = {
  async search(params: {
    q: string;
    filter?: SearchFilterId;
    page?: number;
    limit?: number;
    pageToken?: string | null;
  }): Promise<MassiveSearchResponse> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<MassiveSearchResponse>('/api/search', {
          timeout: 15000,
          params: {
            q: params.q,
            filter: params.filter || 'all',
            page: params.page ?? 1,
            limit: params.limit ?? 20,
            page_token: params.pageToken || undefined,
          },
        });
        return {
          ...data,
          items: (data.items || []).map(normalizeVideoMedia),
        };
      },
      async () => {
        // Fallback Tier 1: Live YouTube Data API v3 via Vercel Serverless Edge Proxy (works on Web & APK 24/7!)
        try {
          const { data } = await axios.get<MassiveSearchResponse>(
            `${getCloudSearchBase()}/api/search`,
            {
              timeout: 10000,
              params: {
                q: params.q,
                filter: params.filter || 'all',
                page: params.page ?? 1,
                limit: params.limit ?? 20,
                page_token: params.pageToken || undefined,
              },
            }
          );
          if (data && Array.isArray(data.items) && data.items.length > 0) {
            return data;
          }
        } catch {
          // Fall through to local demo search
        }
        return demoFallback.massiveSearch(
          params.q,
          params.filter || 'all',
          params.page ?? 1,
          params.limit ?? 20
        );
      }
    );
  },

  async suggest(q: string): Promise<string[]> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<{ suggestions: string[] }>('/api/search/suggest', {
          timeout: 2500,
          params: { q },
        });
        return data.suggestions || [];
      },
      async () => {
        try {
          const { data } = await axios.get<{ suggestions: string[] }>(
            `${getCloudSearchBase()}/api/suggest`,
            { timeout: 2500, params: { q } }
          );
          if (data?.suggestions?.length) return data.suggestions;
        } catch {
          // Fall through
        }
        return demoFallback.suggest(q);
      }
    );
  },

  async trending(): Promise<string[]> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<{ trending: string[] }>('/api/search/trending', {
          timeout: 2500,
        });
        return data.trending || [];
      },
      async () => {
        try {
          const { data } = await axios.get<{ trending: string[] }>(
            `${getCloudSearchBase()}/api/trending`,
            { timeout: 3000 }
          );
          if (data?.trending?.length) return data.trending;
        } catch {
          // Fall through
        }
        return demoFallback.trending();
      }
    );
  },
};

export const youtubeApi = {
  async subscriptions(): Promise<{
    connected: boolean;
    channels: YouTubeChannelSub[];
    videos: VideoCardItem[];
  }> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<{
          connected: boolean;
          channels: YouTubeChannelSub[];
          videos: VideoCardItem[];
        }>('/api/youtube/subscriptions');
        return {
          ...data,
          videos: (data.videos || []).map(normalizeVideoMedia),
        };
      },
      () => ({ connected: false, channels: [], videos: [] })
    );
  },

  async liked(): Promise<{ connected: boolean; items: VideoCardItem[] }> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<{ connected: boolean; items: VideoCardItem[] }>(
          '/api/youtube/liked'
        );
        return {
          ...data,
          items: (data.items || []).map(normalizeVideoMedia),
        };
      },
      () => ({ connected: false, items: demoFallback.listLikedLocal() })
    );
  },

  async playlists(): Promise<{ connected: boolean; items: YouTubePlaylistItem[] }> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<{ connected: boolean; items: YouTubePlaylistItem[] }>(
          '/api/youtube/playlists'
        );
        return data;
      },
      () => ({ connected: false, items: [] })
    );
  },

  async rate(ytId: string, rating: 'like' | 'dislike' | 'none'): Promise<{ synced: boolean }> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<{ synced: boolean }>('/api/youtube/rate', {
          yt_id: ytId,
          rating,
        });
        return data;
      },
      () => ({ synced: false })
    );
  },

  async subscribe(ytChannelId: string): Promise<{ synced: boolean }> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<{ synced: boolean }>('/api/youtube/subscribe', {
          yt_channel_id: ytChannelId,
        });
        return data;
      },
      () => ({ synced: false })
    );
  },

  async comment(ytId: string, content: string): Promise<{ synced: boolean }> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<{ synced: boolean }>('/api/youtube/comment', {
          yt_id: ytId,
          content,
        });
        return data;
      },
      () => ({ synced: false })
    );
  },
};

export const historyApi = {
  async list(): Promise<WatchHistoryItem[]> {
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.get<WatchHistoryItem[]>('/api/history');
        return data;
      },
      () => demoFallback.listHistory()
    );
  },

  async saveProgress(payload: Omit<WatchHistoryItem, 'watched_at'>): Promise<WatchHistoryItem> {
    // Always mirror into localStorage for instant resume even offline
    demoFallback.saveHistoryProgress(payload);
    return withBackendFallback(
      async () => {
        const { data } = await apiClient.post<WatchHistoryItem>('/api/history/progress', payload);
        return data;
      },
      () => demoFallback.saveHistoryProgress(payload)
    );
  },

  async remove(videoKey: string): Promise<void> {
    demoFallback.removeHistory(videoKey);
    await withBackendFallback(
      async () => {
        await apiClient.delete(`/api/history/${encodeURIComponent(videoKey)}`);
      },
      () => undefined
    );
  },

  async clear(): Promise<void> {
    demoFallback.clearHistory();
    await withBackendFallback(
      async () => {
        await apiClient.delete('/api/history');
      },
      () => undefined
    );
  },

  async importTakeout(
    file: File
  ): Promise<{ imported: number; parsed_total: number; message: string }> {
    return withBackendFallback(
      async () => {
        const fd = new FormData();
        fd.append('file', file);
        const { data } = await apiClient.post<{
          imported: number;
          parsed_total: number;
          message: string;
        }>('/api/history/import-takeout', fd, {
          headers: { 'Content-Type': 'multipart/form-data' },
          timeout: 20000,
        });
        return data;
      },
      () => demoFallback.importTakeout(file)
    );
  },
};
