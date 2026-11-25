from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class RegisterRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=40, pattern=r"^[a-zA-Z0-9_.-]+$")
    email: EmailStr
    password: str = Field(..., min_length=6, max_length=72)
    display_name: str | None = Field(default=None, max_length=80)


class LoginRequest(BaseModel):
    identifier: str = Field(..., min_length=3, max_length=160, description="Email atau username")
    password: str = Field(..., min_length=1, max_length=72)


class GoogleAuthRequest(BaseModel):
    credential: str = Field(..., min_length=20)


class GoogleOAuthSyncRequest(BaseModel):
    access_token: str | None = Field(default=None, min_length=10)
    code: str | None = Field(default=None, min_length=6)
    redirect_uri: str | None = None
    expires_in: int | None = 3500


class UserPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    email: str
    display_name: str
    handle: str
    avatar_url: str | None = None
    verified: bool = False
    provider: str = "local"
    subscribers_count: int = 0
    youtube_connected: bool = False
    google_user_id: str | None = None
    created_at: datetime


class AuthTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserPublic


class ChannelPublic(BaseModel):
    id: int
    username: str
    display_name: str
    handle: str
    avatar_url: str | None = None
    verified: bool = False
    subscribers_count: int = 0
    subscribed_by_me: bool = False


class VideoCardResponse(BaseModel):
    id: int
    title: str
    description: str
    category: str
    tags: list[str]
    thumbnail_url: str
    hls_url: str | None = None
    mp4_url: str
    duration_sec: int
    views: int
    likes_count: int
    is_short: bool
    is_live: bool
    processing_status: str
    created_at: datetime
    channel: ChannelPublic


class VideoListResponse(BaseModel):
    items: list[VideoCardResponse]
    total: int
    page: int
    limit: int
    has_more: bool


class VideoDetailResponse(VideoCardResponse):
    dislikes_count: int = 0
    liked_by_me: bool = False
    disliked_by_me: bool = False
    comments_count: int = 0
    recommendations: list[VideoCardResponse] = Field(default_factory=list)


class UploadVideoResponse(BaseModel):
    message: str
    ffmpeg_enabled: bool
    video: VideoCardResponse


class LikeRequest(BaseModel):
    action: Literal["like", "dislike"] = "like"


class LikeResponse(BaseModel):
    video_id: int
    likes_count: int
    dislikes_count: int
    liked_by_me: bool
    disliked_by_me: bool


class SubscribeResponse(BaseModel):
    channel_id: int
    subscribed: bool
    subscribers_count: int


class CommentCreateRequest(BaseModel):
    content: str = Field(..., min_length=1, max_length=2000)
    parent_id: int | None = None


class CommentAuthorPublic(BaseModel):
    id: int
    display_name: str
    handle: str
    avatar_url: str | None = None
    verified: bool = False


class CommentReplyResponse(BaseModel):
    id: int
    video_id: int
    parent_id: int
    content: str
    likes_count: int
    liked_by_me: bool = False
    is_channel_owner: bool = False
    created_at: datetime
    author: CommentAuthorPublic


class CommentResponse(BaseModel):
    id: int
    video_id: int
    content: str
    likes_count: int
    liked_by_me: bool = False
    is_pinned: bool = False
    is_channel_owner: bool = False
    created_at: datetime
    author: CommentAuthorPublic
    replies: list[CommentReplyResponse] = Field(default_factory=list)


class CommentListResponse(BaseModel):
    video_id: int
    total: int
    items: list[CommentResponse]


class CommentLikeResponse(BaseModel):
    comment_id: int
    likes_count: int
    liked_by_me: bool


class ShortsListResponse(BaseModel):
    items: list[VideoCardResponse]
    total: int
    page: int
    limit: int
    has_more: bool


class WatchHistoryProgressRequest(BaseModel):
    video_key: str = Field(..., min_length=1, max_length=64)
    title: str = Field(..., min_length=1, max_length=240)
    channel_name: str = Field(default="VeloxTube", max_length=120)
    thumbnail_url: str = Field(default="", max_length=600)
    duration_sec: int = Field(default=0, ge=0)
    position_sec: float = Field(default=0.0, ge=0.0)
    source: str = Field(default="veloxtube", max_length=24)


class WatchHistoryItemResponse(BaseModel):
    video_key: str
    title: str
    channel_name: str
    thumbnail_url: str
    duration_sec: int
    position_sec: float
    source: str
    watched_at: datetime


class YouTubeRateRequest(BaseModel):
    yt_id: str = Field(..., min_length=5, max_length=32)
    rating: Literal["like", "dislike", "none"] = "like"


class YouTubeSubscribeRequest(BaseModel):
    yt_channel_id: str = Field(..., min_length=5, max_length=64)


class YouTubeCommentRequest(BaseModel):
    yt_id: str = Field(..., min_length=5, max_length=32)
    content: str = Field(..., min_length=1, max_length=2000)
