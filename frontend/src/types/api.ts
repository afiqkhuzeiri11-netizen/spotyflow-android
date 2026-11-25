export type ThemeMode = 'dark' | 'light';

export type CategoryId =
  | 'all'
  | 'gaming'
  | 'music'
  | 'tech'
  | 'podcast'
  | 'live'
  | 'education'
  | 'culinary'
  | 'recent';

export interface CategoryOption {
  id: CategoryId;
  label: string;
}

export interface UserPublic {
  id: number;
  username: string;
  email: string;
  display_name: string;
  handle: string;
  avatar_url: string | null;
  verified: boolean;
  provider: string;
  subscribers_count: number;
  created_at: string;
}

export interface AuthTokenResponse {
  access_token: string;
  token_type: string;
  user: UserPublic;
}

export interface ChannelPublic {
  id: number;
  username: string;
  display_name: string;
  handle: string;
  avatar_url: string | null;
  verified: boolean;
  subscribers_count: number;
  subscribed_by_me: boolean;
}

export interface VideoCardItem {
  id: number;
  title: string;
  description: string;
  category: string;
  tags: string[];
  thumbnail_url: string;
  hls_url: string | null;
  mp4_url: string;
  duration_sec: number;
  views: number;
  likes_count: number;
  is_short: boolean;
  is_live: boolean;
  processing_status: string;
  created_at: string;
  channel: ChannelPublic;
}

export interface VideoDetailItem extends VideoCardItem {
  dislikes_count: number;
  liked_by_me: boolean;
  disliked_by_me: boolean;
  comments_count: number;
  recommendations: VideoCardItem[];
}

export interface VideoListResponse {
  items: VideoCardItem[];
  total: number;
  page: number;
  limit: number;
  has_more: boolean;
}

export interface ShortsListResponse {
  items: VideoCardItem[];
  total: number;
  page: number;
  limit: number;
  has_more: boolean;
}

export interface UploadVideoResponse {
  message: string;
  ffmpeg_enabled: boolean;
  video: VideoCardItem;
}

export interface LikeResponse {
  video_id: number;
  likes_count: number;
  dislikes_count: number;
  liked_by_me: boolean;
  disliked_by_me: boolean;
}

export interface SubscribeResponse {
  channel_id: number;
  subscribed: boolean;
  subscribers_count: number;
}

export interface CommentAuthor {
  id: number;
  display_name: string;
  handle: string;
  avatar_url: string | null;
  verified: boolean;
}

export interface CommentReplyItem {
  id: number;
  video_id: number;
  parent_id: number;
  content: string;
  likes_count: number;
  liked_by_me: boolean;
  is_channel_owner: boolean;
  created_at: string;
  author: CommentAuthor;
}

export interface CommentItem {
  id: number;
  video_id: number;
  content: string;
  likes_count: number;
  liked_by_me: boolean;
  is_pinned: boolean;
  is_channel_owner: boolean;
  created_at: string;
  author: CommentAuthor;
  replies: CommentReplyItem[];
}

export interface CommentListResponse {
  video_id: number;
  total: number;
  items: CommentItem[];
}

export interface CommentLikeResponse {
  comment_id: number;
  likes_count: number;
  liked_by_me: boolean;
}

export interface QualityOption {
  label: string;
  height: number;
  levelIndex: number;
}

export type UpNextFilter = 'all' | 'channel' | 'related';
export type CommentSort = 'top' | 'newest';

export const CATEGORIES: CategoryOption[] = [
  { id: 'all', label: 'Semua' },
  { id: 'gaming', label: 'Gaming' },
  { id: 'music', label: 'Musik' },
  { id: 'tech', label: 'Teknologi' },
  { id: 'podcast', label: 'Podcast' },
  { id: 'live', label: 'Live' },
  { id: 'education', label: 'Pendidikan' },
  { id: 'culinary', label: 'Kuliner' },
  { id: 'recent', label: 'Baru diupload' },
];
