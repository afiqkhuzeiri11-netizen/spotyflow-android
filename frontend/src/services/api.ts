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
  ShortsListResponse,
  SubscribeResponse,
  UploadVideoResponse,
  UserPublic,
  VideoCardItem,
  VideoDetailItem,
  VideoListResponse,
} from '../types/api';
import { demoFallback } from './demoFallback';

export type BackendConnectionMode = 'online' | 'demo';

let backendMode: BackendConnectionMode = shouldPreferImmediateDemoFallback() ? 'demo' : 'online';
let backendDownUntil = backendMode === 'demo' ? Number.MAX_SAFE_INTEGER : 0;

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
  timeout: 4500,
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
  if (!err.response) return true; // ERR_NETWORK, ECONNREFUSED, ECONNABORTED, CORS/Offline
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
      // Cache offline state for 30s so navigation stays instant (60 FPS) without repeated timeouts
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
