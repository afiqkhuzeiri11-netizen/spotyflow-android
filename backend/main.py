from __future__ import annotations

import logging
import mimetypes
import re
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Annotated, Literal

import aiofiles
from fastapi import (
    BackgroundTasks,
    Depends,
    FastAPI,
    File,
    Form,
    HTTPException,
    Query,
    Request,
    UploadFile,
    status,
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import delete, desc, func, or_, select, update
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from auth import (
    create_access_token,
    find_or_create_google_user,
    get_current_user,
    get_optional_user,
    hash_password,
    make_handle,
    verify_google_id_token,
    verify_password,
)
from database import AsyncSessionLocal, get_db, init_db
from google_youtube import (
    exchange_google_auth_code,
    fetch_google_userinfo,
    fetch_youtube_liked_videos,
    fetch_youtube_playlists,
    fetch_youtube_subscriptions,
    parse_takeout_watch_history,
    post_youtube_comment,
    rate_youtube_video,
    subscribe_youtube_channel,
)
from models import Comment, CommentLike, Subscription, User, Video, VideoReaction, WatchHistory
from schemas import (
    AuthTokenResponse,
    ChannelPublic,
    CommentAuthorPublic,
    CommentCreateRequest,
    CommentLikeResponse,
    CommentListResponse,
    CommentReplyResponse,
    CommentResponse,
    GoogleAuthRequest,
    GoogleOAuthSyncRequest,
    LikeRequest,
    LikeResponse,
    LoginRequest,
    RegisterRequest,
    ShortsListResponse,
    SubscribeResponse,
    UploadVideoResponse,
    UserPublic,
    VideoCardResponse,
    VideoDetailResponse,
    VideoListResponse,
    WatchHistoryItemResponse,
    WatchHistoryProgressRequest,
    YouTubeCommentRequest,
    YouTubeRateRequest,
    YouTubeSubscribeRequest,
)
from seed import seed_database
from video_processor import (
    MEDIA_ROOT,
    THUMBS_DIR,
    UPLOADS_DIR,
    ensure_media_dirs,
    generate_fallback_svg_thumbnail,
    is_ffmpeg_available,
    transcode_video_to_hls_background,
)
from youtube_engine import (
    fetch_trending_queries,
    fetch_youtube_suggestions,
    resolve_youtube_stream,
    search_youtube_massive,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("veloxtube.api")

# Ensure proper HLS MIME types on Windows & Linux
mimetypes.add_type("application/vnd.apple.mpegurl", ".m3u8")
mimetypes.add_type("video/mp2t", ".ts")
mimetypes.add_type("video/mp4", ".mp4")
mimetypes.add_type("video/webm", ".webm")

ALLOWED_VIDEO_EXTS = {".mp4", ".mov", ".webm", ".mkv", ".m4v"}
ALLOWED_IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}
MAX_VIDEO_BYTES = 500 * 1024 * 1024  # 500 MB
MAX_THUMB_BYTES = 10 * 1024 * 1024   # 10 MB
STREAM_CHUNK_SIZE = 256 * 1024       # 256 KB per chunk for instant Byte-Range seeking


@asynccontextmanager
async def lifespan(_app: FastAPI):
    ensure_media_dirs()
    await init_db()
    async with AsyncSessionLocal() as session:
        seeded = await seed_database(session)
        if seeded:
            logger.info("VeloxTube initial SQL seed completed (5 channels, 12 videos, 4 Shorts).")
    logger.info("FFmpeg HLS Transcoder status: %s", "AVAILABLE" if is_ffmpeg_available() else "FALLBACK_MP4_RANGE")
    yield


app = FastAPI(
    title="VeloxTube Streaming API",
    version="1.0.0",
    description="High-performance async video streaming backend (FastAPI + Async SQL + FFmpeg HLS + HTTP Byte-Range).",
    lifespan=lifespan,
)

# Multi-platform CORS: Web (Vite), Desktop (Tauri), Android/iOS (Capacitor), and LAN IP testing
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:4173",
        "http://localhost:3000",
        "http://localhost",
        "https://localhost",
        "capacitor://localhost",
        "ionic://localhost",
        "tauri://localhost",
        "http://tauri.localhost",
        "https://tauri.localhost",
    ],
    allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1|192\.168\.\d+\.\d+|10\.\d+\.\d+\.\d+)(:\d+)?$",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["Content-Range", "Accept-Ranges", "Content-Length", "Content-Type"],
)

ensure_media_dirs()
app.mount("/media", StaticFiles(directory=str(MEDIA_ROOT)), name="media")


# =========================================================================
# INTERNAL SERIALIZATION HELPERS (Zero N+1 batched aggregation)
# =========================================================================

async def _get_subscriber_counts(db: AsyncSession, channel_ids: list[int]) -> dict[int, int]:
    if not channel_ids:
        return {}
    stmt = (
        select(Subscription.channel_id, func.count(Subscription.id))
        .where(Subscription.channel_id.in_(channel_ids))
        .group_by(Subscription.channel_id)
    )
    rows = (await db.execute(stmt)).all()
    return {int(cid): int(cnt) for cid, cnt in rows}


async def _get_user_subscribed_set(db: AsyncSession, user_id: int | None, channel_ids: list[int]) -> set[int]:
    if not user_id or not channel_ids:
        return set()
    stmt = select(Subscription.channel_id).where(
        Subscription.subscriber_id == user_id,
        Subscription.channel_id.in_(channel_ids),
    )
    return {int(cid) for cid in (await db.execute(stmt)).scalars().all()}


async def _get_video_like_counts(db: AsyncSession, video_ids: list[int]) -> dict[int, tuple[int, int]]:
    if not video_ids:
        return {}
    stmt = (
        select(VideoReaction.video_id, VideoReaction.is_like, func.count(VideoReaction.id))
        .where(VideoReaction.video_id.in_(video_ids))
        .group_by(VideoReaction.video_id, VideoReaction.is_like)
    )
    result: dict[int, list[int]] = {vid: [0, 0] for vid in video_ids}
    for vid, is_like, cnt in (await db.execute(stmt)).all():
        if is_like:
            result[int(vid)][0] = int(cnt)
        else:
            result[int(vid)][1] = int(cnt)
    return {k: (v[0], v[1]) for k, v in result.items()}


async def _serialize_video_cards(
    db: AsyncSession,
    videos: list[Video],
    current_user: User | None,
) -> list[VideoCardResponse]:
    if not videos:
        return []
    channel_ids = list({v.channel_id for v in videos})
    video_ids = [v.id for v in videos]

    sub_counts = await _get_subscriber_counts(db, channel_ids)
    sub_set = await _get_user_subscribed_set(db, current_user.id if current_user else None, channel_ids)
    like_map = await _get_video_like_counts(db, video_ids)

    items: list[VideoCardResponse] = []
    for v in videos:
        ch = v.channel
        db_likes, _ = like_map.get(v.id, (0, 0))
        tags = [t.strip() for t in (v.tags or "").split(",") if t.strip()]
        items.append(
            VideoCardResponse(
                id=v.id,
                title=v.title,
                description=v.description,
                category=v.category,
                tags=tags,
                thumbnail_url=v.thumbnail_url,
                hls_url=v.hls_url,
                mp4_url=v.mp4_url,
                duration_sec=v.duration_sec,
                views=v.views,
                likes_count=v.base_likes + db_likes,
                is_short=v.is_short,
                is_live=v.is_live,
                processing_status=v.processing_status,
                created_at=v.created_at,
                channel=ChannelPublic(
                    id=ch.id,
                    username=ch.username,
                    display_name=ch.display_name,
                    handle=ch.handle,
                    avatar_url=ch.avatar_url,
                    verified=ch.verified,
                    subscribers_count=ch.base_subscribers + sub_counts.get(ch.id, 0),
                    subscribed_by_me=ch.id in sub_set,
                ),
            )
        )
    return items


async def _build_user_public(db: AsyncSession, user: User) -> UserPublic:
    db_subs = (
        await db.execute(select(func.count(Subscription.id)).where(Subscription.channel_id == user.id))
    ).scalar_one()
    return UserPublic(
        id=user.id,
        username=user.username,
        email=user.email,
        display_name=user.display_name,
        handle=user.handle,
        avatar_url=user.avatar_url,
        verified=user.verified,
        provider=user.provider,
        subscribers_count=user.base_subscribers + int(db_subs),
        youtube_connected=bool(user.google_access_token),
        google_user_id=user.google_user_id,
        created_at=user.created_at,
    )


# =========================================================================
# 1. HEALTH & HTTP 206 BYTE-RANGE STREAMING ENDPOINT
# =========================================================================

@app.get("/api/health")
async def health_check() -> dict:
    return {
        "ok": True,
        "app": "VeloxTube API",
        "ffmpeg_available": is_ffmpeg_available(),
        "streaming_modes": ["hls_m3u8", "http_byte_range_mp4"],
    }


@app.get("/api/media/stream/{filename}")
async def stream_uploaded_video_range(filename: str, request: Request):
    """
    High-performance HTTP 206 Partial Content Byte-Range streaming endpoint for uploaded MP4/WebM files.
    Enables instant seeking/skipping without lag even when FFmpeg HLS transcoding is not installed.
    """
    safe_name = Path(filename).name
    if safe_name != filename or ".." in filename:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Nama file tidak valid.")

    file_path = UPLOADS_DIR / safe_name
    if not file_path.exists() or not file_path.is_file():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="File video tidak ditemukan.")

    file_size = file_path.stat().st_size
    content_type = mimetypes.guess_type(str(file_path))[0] or "video/mp4"
    range_header = request.headers.get("range")

    start = 0
    end = file_size - 1
    status_code = status.HTTP_200_OK

    if range_header:
        match = re.match(r"bytes=(\d*)-(\d*)", range_header.strip())
        if match:
            s_str, e_str = match.groups()
            if s_str:
                start = int(s_str)
            if e_str:
                end = int(e_str)
            elif not s_str and e_str:
                start = max(0, file_size - int(e_str))
            end = min(end, file_size - 1)
            if start >= file_size or start > end:
                raise HTTPException(
                    status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE,
                    headers={"Content-Range": f"bytes */{file_size}"},
                )
            status_code = status.HTTP_206_PARTIAL_CONTENT

    content_length = end - start + 1

    async def file_iterator(path: Path, offset: int, length: int):
        remaining = length
        async with aiofiles.open(path, "rb") as f:
            await f.seek(offset)
            while remaining > 0:
                chunk = await f.read(min(STREAM_CHUNK_SIZE, remaining))
                if not chunk:
                    break
                remaining -= len(chunk)
                yield chunk

    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(content_length),
        "Cache-Control": "public, max-age=3600",
    }
    if status_code == status.HTTP_206_PARTIAL_CONTENT:
        headers["Content-Range"] = f"bytes {start}-{end}/{file_size}"

    return StreamingResponse(
        file_iterator(file_path, start, content_length),
        status_code=status_code,
        media_type=content_type,
        headers=headers,
    )


# =========================================================================
# 2. AUTHENTICATION ENDPOINTS (JWT + bcrypt + Google OAuth 2.0)
# =========================================================================

@app.post("/api/auth/register", response_model=AuthTokenResponse, status_code=status.HTTP_201_CREATED)
async def register_user(
    body: RegisterRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
):
    username = body.username.strip().lower()
    email = str(body.email).strip().lower()

    conflict = (
        await db.execute(select(User).where(or_(User.username == username, User.email == email)))
    ).scalar_one_or_none()
    if conflict is not None:
        detail = "Email sudah terdaftar." if conflict.email == email else "Username sudah digunakan."
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=detail)

    display_name = (body.display_name or body.username).strip()[:80]
    user = User(
        username=username,
        email=email,
        display_name=display_name,
        handle=make_handle(username),
        password_hash=hash_password(body.password),
        avatar_url=None,
        verified=False,
        provider="local",
        base_subscribers=0,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)

    token = create_access_token(user.id, user.username)
    return AuthTokenResponse(access_token=token, user=await _build_user_public(db, user))


@app.post("/api/auth/login", response_model=AuthTokenResponse)
async def login_user(
    body: LoginRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
):
    ident = body.identifier.strip().lower()
    user = (
        await db.execute(select(User).where(or_(User.email == ident, User.username == ident)))
    ).scalar_one_or_none()

    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Email/username atau password salah.",
        )

    token = create_access_token(user.id, user.username)
    return AuthTokenResponse(access_token=token, user=await _build_user_public(db, user))


@app.post("/api/auth/google", response_model=AuthTokenResponse)
async def login_with_google(
    body: GoogleAuthRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
):
    google_info = await verify_google_id_token(body.credential)
    user = await find_or_create_google_user(db, google_info)
    token = create_access_token(user.id, user.username)
    return AuthTokenResponse(access_token=token, user=await _build_user_public(db, user))


@app.get("/api/auth/me", response_model=UserPublic)
async def get_me(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
):
    return await _build_user_public(db, current_user)


# =========================================================================
# 3. VIDEOS, SHORTS, UPLOAD, LIKE & SUBSCRIBE ENDPOINTS
# =========================================================================

@app.get("/api/videos", response_model=VideoListResponse)
async def list_videos(
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
    page: int = Query(default=1, ge=1),
    limit: int = Query(default=12, ge=1, le=48),
    category: str = Query(default="all"),
    q: str = Query(default="", max_length=120),
    search: str = Query(default="", max_length=120),
    channel_id: int | None = Query(default=None, ge=1),
    subscribed_only: bool = Query(default=False),
):
    filters = [Video.is_short.is_(False)]

    cat = category.strip().lower()
    if cat == "live":
        filters.append(or_(Video.is_live.is_(True), Video.category == "live"))
    elif cat not in ("", "all", "recent"):
        filters.append(Video.category == cat)

    if channel_id is not None:
        filters.append(Video.channel_id == channel_id)

    if subscribed_only:
        if not current_user:
            return VideoListResponse(items=[], total=0, page=page, limit=limit, has_more=False)
        sub_ids = list(
            (
                await db.execute(
                    select(Subscription.channel_id).where(Subscription.subscriber_id == current_user.id)
                )
            )
            .scalars()
            .all()
        )
        if not sub_ids:
            return VideoListResponse(items=[], total=0, page=page, limit=limit, has_more=False)
        filters.append(Video.channel_id.in_(sub_ids))

    search_term = (search or q).strip()
    if search_term:
        pattern = f"%{search_term}%"
        filters.append(
            or_(
                Video.title.ilike(pattern),
                Video.description.ilike(pattern),
                Video.tags.ilike(pattern),
                Video.category.ilike(pattern),
            )
        )

    total = int(
        (await db.execute(select(func.count(Video.id)).where(*filters))).scalar_one()
    )

    order_col = desc(Video.created_at) if cat == "recent" else desc(Video.created_at)
    offset = (page - 1) * limit
    stmt = (
        select(Video)
        .options(selectinload(Video.channel))
        .where(*filters)
        .order_by(order_col, desc(Video.id))
        .offset(offset)
        .limit(limit)
    )
    videos = list((await db.execute(stmt)).scalars().all())
    items = await _serialize_video_cards(db, videos, current_user)

    return VideoListResponse(
        items=items,
        total=total,
        page=page,
        limit=limit,
        has_more=(offset + len(items)) < total,
    )


@app.get("/api/shorts", response_model=ShortsListResponse)
async def list_shorts(
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
    page: int = Query(default=1, ge=1),
    limit: int = Query(default=10, ge=1, le=30),
):
    total = int(
        (await db.execute(select(func.count(Video.id)).where(Video.is_short.is_(True)))).scalar_one()
    )
    offset = (page - 1) * limit
    stmt = (
        select(Video)
        .options(selectinload(Video.channel))
        .where(Video.is_short.is_(True))
        .order_by(desc(Video.created_at), desc(Video.id))
        .offset(offset)
        .limit(limit)
    )
    videos = list((await db.execute(stmt)).scalars().all())
    items = await _serialize_video_cards(db, videos, current_user)

    return ShortsListResponse(
        items=items,
        total=total,
        page=page,
        limit=limit,
        has_more=(offset + len(items)) < total,
    )


@app.get("/api/videos/{video_id}", response_model=VideoDetailResponse)
async def get_video_detail(
    video_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
):
    stmt = select(Video).options(selectinload(Video.channel)).where(Video.id == video_id)
    video = (await db.execute(stmt)).scalar_one_or_none()
    if video is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Video tidak ditemukan.")

    # Atomic view counter increment (syncs cleanly from DB without double-counting)
    await db.execute(
        update(Video)
        .where(Video.id == video_id)
        .values(views=Video.views + 1)
        .execution_options(synchronize_session=False)
    )
    await db.commit()
    await db.refresh(video)

    serialized = (await _serialize_video_cards(db, [video], current_user))[0]
    like_map = await _get_video_like_counts(db, [video.id])
    _, dislikes_count = like_map.get(video.id, (0, 0))

    liked_by_me = False
    disliked_by_me = False
    if current_user:
        react = (
            await db.execute(
                select(VideoReaction).where(
                    VideoReaction.user_id == current_user.id,
                    VideoReaction.video_id == video.id,
                )
            )
        ).scalar_one_or_none()
        if react is not None:
            liked_by_me = bool(react.is_like)
            disliked_by_me = not bool(react.is_like)

    comments_count = int(
        (await db.execute(select(func.count(Comment.id)).where(Comment.video_id == video.id))).scalar_one()
    )

    # Fetch Up Next recommendations (same category first, excluding current video)
    rec_stmt = (
        select(Video)
        .options(selectinload(Video.channel))
        .where(Video.id != video.id, Video.is_short.is_(False))
        .order_by(desc(Video.category == video.category), desc(Video.views), desc(Video.created_at))
        .limit(12)
    )
    rec_videos = list((await db.execute(rec_stmt)).scalars().all())
    recommendations = await _serialize_video_cards(db, rec_videos, current_user)

    return VideoDetailResponse(
        **serialized.model_dump(),
        dislikes_count=dislikes_count,
        liked_by_me=liked_by_me,
        disliked_by_me=disliked_by_me,
        comments_count=comments_count,
        recommendations=recommendations,
    )


@app.post("/api/videos/upload", response_model=UploadVideoResponse, status_code=status.HTTP_201_CREATED)
async def upload_video(
    background_tasks: BackgroundTasks,
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
    file: UploadFile = File(...),
    title: str = Form(..., min_length=2, max_length=180),
    description: str = Form(default="", max_length=5000),
    category: str = Form(default="tech", max_length=40),
    tags: str = Form(default="", max_length=320),
    is_short: bool = Form(default=False),
    thumbnail: UploadFile | None = File(default=None),
):
    ensure_media_dirs()

    orig_ext = Path(file.filename or "video.mp4").suffix.lower() or ".mp4"
    if orig_ext not in ALLOWED_VIDEO_EXTS:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Format video tidak didukung ({orig_ext}). Gunakan MP4, WebM, MOV, atau MKV.",
        )

    uid = uuid.uuid4().hex[:12]
    saved_video_name = f"vid_{uid}{orig_ext}"
    saved_video_path = UPLOADS_DIR / saved_video_name

    # Stream uploaded file to disk in chunks (zero RAM spike on large video uploads)
    total_written = 0
    try:
        async with aiofiles.open(saved_video_path, "wb") as out_f:
            while True:
                chunk = await file.read(1024 * 1024)
                if not chunk:
                    break
                total_written += len(chunk)
                if total_written > MAX_VIDEO_BYTES:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail="Ukuran video melebihi batas maksimal 500 MB.",
                    )
                await out_f.write(chunk)
    except HTTPException:
        if saved_video_path.exists():
            saved_video_path.unlink(missing_ok=True)
        raise

    if total_written == 0:
        saved_video_path.unlink(missing_ok=True)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="File video kosong.")

    # Optional user-uploaded thumbnail
    thumb_url: str | None = None
    if thumbnail is not None and thumbnail.filename:
        t_ext = Path(thumbnail.filename).suffix.lower()
        if t_ext not in ALLOWED_IMAGE_EXTS:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Format thumbnail harus JPG, PNG, atau WebP.",
            )
        t_bytes = await thumbnail.read(MAX_THUMB_BYTES + 1)
        if len(t_bytes) > MAX_THUMB_BYTES:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail="Ukuran thumbnail maksimal 10 MB.",
            )
        if t_bytes:
            saved_thumb_name = f"thumb_{uid}{t_ext}"
            saved_thumb_path = THUMBS_DIR / saved_thumb_name
            async with aiofiles.open(saved_thumb_path, "wb") as tf:
                await tf.write(t_bytes)
            thumb_url = f"/media/thumbnails/{saved_thumb_name}"

    ffmpeg_ready = is_ffmpeg_available()
    mp4_stream_url = f"/api/media/stream/{saved_video_name}"

    video = Video(
        channel_id=current_user.id,
        title=title.strip(),
        description=description.strip(),
        category=category.strip().lower() or "tech",
        tags=tags.strip(),
        thumbnail_url=thumb_url or "",
        hls_url=None,
        mp4_url=mp4_stream_url,
        duration_sec=0,
        views=0,
        base_likes=0,
        is_short=bool(is_short),
        is_live=False,
        processing_status="processing" if ffmpeg_ready else "ready",
    )
    db.add(video)
    await db.flush()

    if not thumb_url:
        video.thumbnail_url = generate_fallback_svg_thumbnail(video.id, video.title, bool(is_short))

    await db.commit()

    # Reload with channel relationship
    loaded = (
        await db.execute(
            select(Video).options(selectinload(Video.channel)).where(Video.id == video.id)
        )
    ).scalar_one()

    # Kick off FFmpeg HLS background transcoding (or instant Byte-Range readiness if FFmpeg absent)
    background_tasks.add_task(
        transcode_video_to_hls_background,
        loaded.id,
        saved_video_path,
        thumb_url is None,
        bool(is_short),
    )

    serialized = (await _serialize_video_cards(db, [loaded], current_user))[0]
    msg = (
        "Video berhasil diupload dan sedang dikonversi ke HLS adaptif (720p & 480p) di latar belakang."
        if ffmpeg_ready
        else "Video berhasil diupload dan siap diputar instan dengan HTTP Byte-Range Streaming."
    )
    return UploadVideoResponse(message=msg, ffmpeg_enabled=ffmpeg_ready, video=serialized)


@app.post("/api/videos/{video_id}/like", response_model=LikeResponse)
async def toggle_video_reaction(
    video_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
    body: LikeRequest | None = None,
):
    video = (await db.execute(select(Video).where(Video.id == video_id))).scalar_one_or_none()
    if video is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Video tidak ditemukan.")

    want_like = (body.action if body else "like") == "like"
    existing = (
        await db.execute(
            select(VideoReaction).where(
                VideoReaction.user_id == current_user.id,
                VideoReaction.video_id == video_id,
            )
        )
    ).scalar_one_or_none()

    liked_by_me = False
    disliked_by_me = False

    if existing is None:
        db.add(VideoReaction(user_id=current_user.id, video_id=video_id, is_like=want_like))
        liked_by_me = want_like
        disliked_by_me = not want_like
    elif existing.is_like == want_like:
        await db.delete(existing)
    else:
        existing.is_like = want_like
        liked_by_me = want_like
        disliked_by_me = not want_like

    await db.commit()
    db_likes, db_dislikes = (await _get_video_like_counts(db, [video_id])).get(video_id, (0, 0))

    return LikeResponse(
        video_id=video_id,
        likes_count=video.base_likes + db_likes,
        dislikes_count=db_dislikes,
        liked_by_me=liked_by_me,
        disliked_by_me=disliked_by_me,
    )


@app.post("/api/channels/{channel_id}/subscribe", response_model=SubscribeResponse)
async def toggle_subscribe_channel(
    channel_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    channel = (await db.execute(select(User).where(User.id == channel_id))).scalar_one_or_none()
    if channel is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Channel tidak ditemukan.")
    if channel.id == current_user.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Kamu tidak dapat subscribe ke channel sendiri.",
        )

    existing = (
        await db.execute(
            select(Subscription).where(
                Subscription.subscriber_id == current_user.id,
                Subscription.channel_id == channel_id,
            )
        )
    ).scalar_one_or_none()

    if existing is not None:
        await db.delete(existing)
        subscribed = False
    else:
        db.add(Subscription(subscriber_id=current_user.id, channel_id=channel_id))
        subscribed = True

    await db.commit()
    db_subs = int(
        (
            await db.execute(
                select(func.count(Subscription.id)).where(Subscription.channel_id == channel_id)
            )
        ).scalar_one()
    )

    return SubscribeResponse(
        channel_id=channel_id,
        subscribed=subscribed,
        subscribers_count=channel.base_subscribers + db_subs,
    )


# =========================================================================
# 4. COMMENTS & NESTED REPLIES ENDPOINTS
# =========================================================================

@app.get("/api/videos/{video_id}/comments", response_model=CommentListResponse)
async def list_video_comments(
    video_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
    sort: Literal["top", "newest"] = Query(default="top"),
):
    video = (await db.execute(select(Video.id, Video.channel_id).where(Video.id == video_id))).first()
    if video is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Video tidak ditemukan.")
    video_channel_id = int(video[1])

    all_comments = list(
        (
            await db.execute(
                select(Comment)
                .options(selectinload(Comment.author))
                .where(Comment.video_id == video_id)
                .order_by(Comment.created_at.asc())
            )
        )
        .scalars()
        .all()
    )

    comment_ids = [c.id for c in all_comments]
    like_counts: dict[int, int] = {}
    liked_by_me_set: set[int] = set()

    if comment_ids:
        like_rows = (
            await db.execute(
                select(CommentLike.comment_id, func.count(CommentLike.id))
                .where(CommentLike.comment_id.in_(comment_ids))
                .group_by(CommentLike.comment_id)
            )
        ).all()
        like_counts = {int(cid): int(cnt) for cid, cnt in like_rows}

        if current_user:
            liked_by_me_set = {
                int(cid)
                for cid in (
                    await db.execute(
                        select(CommentLike.comment_id).where(
                            CommentLike.user_id == current_user.id,
                            CommentLike.comment_id.in_(comment_ids),
                        )
                    )
                )
                .scalars()
                .all()
            }

    replies_by_parent: dict[int, list[CommentReplyResponse]] = {}
    top_level: list[Comment] = []

    for c in all_comments:
        if c.parent_id is None:
            top_level.append(c)
        else:
            replies_by_parent.setdefault(c.parent_id, []).append(
                CommentReplyResponse(
                    id=c.id,
                    video_id=c.video_id,
                    parent_id=c.parent_id,
                    content=c.content,
                    likes_count=c.base_likes + like_counts.get(c.id, 0),
                    liked_by_me=c.id in liked_by_me_set,
                    is_channel_owner=c.user_id == video_channel_id,
                    created_at=c.created_at,
                    author=CommentAuthorPublic(
                        id=c.author.id,
                        display_name=c.author.display_name,
                        handle=c.author.handle,
                        avatar_url=c.author.avatar_url,
                        verified=c.author.verified,
                    ),
                )
            )

    if sort == "newest":
        top_level.sort(key=lambda c: c.created_at, reverse=True)
    else:
        top_level.sort(
            key=lambda c: (
                1 if c.is_pinned else 0,
                c.base_likes + like_counts.get(c.id, 0),
                c.created_at.timestamp(),
            ),
            reverse=True,
        )

    items = [
        CommentResponse(
            id=c.id,
            video_id=c.video_id,
            content=c.content,
            likes_count=c.base_likes + like_counts.get(c.id, 0),
            liked_by_me=c.id in liked_by_me_set,
            is_pinned=c.is_pinned,
            is_channel_owner=c.user_id == video_channel_id,
            created_at=c.created_at,
            author=CommentAuthorPublic(
                id=c.author.id,
                display_name=c.author.display_name,
                handle=c.author.handle,
                avatar_url=c.author.avatar_url,
                verified=c.author.verified,
            ),
            replies=replies_by_parent.get(c.id, []),
        )
        for c in top_level
    ]

    return CommentListResponse(video_id=video_id, total=len(all_comments), items=items)


@app.post(
    "/api/videos/{video_id}/comments",
    response_model=CommentResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_video_comment(
    video_id: int,
    body: CommentCreateRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    video = (await db.execute(select(Video.id, Video.channel_id).where(Video.id == video_id))).first()
    if video is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Video tidak ditemukan.")
    video_channel_id = int(video[1])

    parent_id = body.parent_id
    if parent_id is not None:
        parent = (
            await db.execute(
                select(Comment).where(Comment.id == parent_id, Comment.video_id == video_id)
            )
        ).scalar_one_or_none()
        if parent is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Komentar induk tidak ditemukan.",
            )
        # Flatten reply-to-reply onto the root thread (identik YouTube)
        if parent.parent_id is not None:
            parent_id = parent.parent_id

    comment = Comment(
        video_id=video_id,
        user_id=current_user.id,
        parent_id=parent_id,
        content=body.content.strip(),
        base_likes=0,
        is_pinned=False,
    )
    db.add(comment)
    await db.commit()
    await db.refresh(comment)

    return CommentResponse(
        id=comment.id,
        video_id=comment.video_id,
        content=comment.content,
        likes_count=0,
        liked_by_me=False,
        is_pinned=False,
        is_channel_owner=current_user.id == video_channel_id,
        created_at=comment.created_at,
        author=CommentAuthorPublic(
            id=current_user.id,
            display_name=current_user.display_name,
            handle=current_user.handle,
            avatar_url=current_user.avatar_url,
            verified=current_user.verified,
        ),
        replies=[],
    )


@app.post("/api/comments/{comment_id}/like", response_model=CommentLikeResponse)
async def toggle_comment_like(
    comment_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    comment = (await db.execute(select(Comment).where(Comment.id == comment_id))).scalar_one_or_none()
    if comment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Komentar tidak ditemukan.")

    existing = (
        await db.execute(
            select(CommentLike).where(
                CommentLike.user_id == current_user.id,
                CommentLike.comment_id == comment_id,
            )
        )
    ).scalar_one_or_none()

    if existing is not None:
        await db.delete(existing)
        liked = False
    else:
        db.add(CommentLike(user_id=current_user.id, comment_id=comment_id))
        liked = True

    await db.commit()
    db_likes = int(
        (
            await db.execute(
                select(func.count(CommentLike.id)).where(CommentLike.comment_id == comment_id)
            )
        ).scalar_one()
    )

    return CommentLikeResponse(
        comment_id=comment_id,
        likes_count=comment.base_likes + db_likes,
        liked_by_me=liked,
    )


# =========================================================================
# 5. MASSIVE YOUTUBE SEARCH, REAL-TIME SUGGESTIONS & INSTANT STREAM PREFETCH
# =========================================================================

@app.get("/api/search")
async def massive_youtube_search(
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
    q: str = Query(default="", max_length=160),
    filter: Literal["all", "video", "shorts", "channel", "live", "today", "most_viewed", "long"] = Query(
        default="all"
    ),
    page: int = Query(default=1, ge=1),
    limit: int = Query(default=20, ge=1, le=50),
    page_token: str | None = Query(default=None),
):
    query_str = q.strip()
    if not query_str:
        return {
            "items": [],
            "total": 0,
            "page": page,
            "limit": limit,
            "has_more": False,
            "next_page_token": None,
            "source": "empty",
        }

    result = await search_youtube_massive(
        query=query_str,
        filter_type=filter,
        page=page,
        limit=limit,
        page_token=page_token,
    )

    # Also merge matching local SQL videos at the top of page 1 if external search is empty
    if page == 1 and not result["items"]:
        pattern = f"%{query_str}%"
        local_videos = list(
            (
                await db.execute(
                    select(Video)
                    .options(selectinload(Video.channel))
                    .where(
                        or_(
                            Video.title.ilike(pattern),
                            Video.description.ilike(pattern),
                            Video.tags.ilike(pattern),
                        )
                    )
                    .order_by(desc(Video.views))
                    .limit(limit)
                )
            )
            .scalars()
            .all()
        )
        serialized = [v.model_dump(mode="json") for v in await _serialize_video_cards(db, local_videos, current_user)]
        return {
            "items": serialized,
            "total": len(serialized),
            "page": 1,
            "limit": limit,
            "has_more": False,
            "next_page_token": None,
            "source": "local_db",
        }

    return result


@app.get("/api/search/suggest")
async def search_suggestions(q: str = Query(default="", max_length=120)):
    suggestions = await fetch_youtube_suggestions(q)
    return {"query": q, "suggestions": suggestions}


@app.get("/api/search/trending")
async def search_trending():
    queries = await fetch_trending_queries()
    return {"trending": queries}


@app.get("/api/yt/stream/{yt_id}")
async def get_youtube_stream(yt_id: str):
    if not re.fullmatch(r"[A-Za-z0-9_-]{6,20}", yt_id):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="ID video YouTube tidak valid.")
    return await resolve_youtube_stream(yt_id)


@app.post("/api/videos/{video_key}/prefetch")
async def prefetch_video_stream(video_key: str, background_tasks: BackgroundTasks):
    """Silent hover/viewport prefetch warming TTLCache for zero perceived playback delay."""
    clean = video_key.strip()
    yt_id = clean[3:] if clean.startswith("yt_") else (clean if not clean.isdigit() else None)
    if yt_id and re.fullmatch(r"[A-Za-z0-9_-]{6,20}", yt_id):
        background_tasks.add_task(resolve_youtube_stream, yt_id)
        return {"prefetched": True, "kind": "youtube", "yt_id": yt_id}
    return {"prefetched": True, "kind": "local", "video_key": clean}


# =========================================================================
# 6. GOOGLE OAUTH 2.0 FULL YOUTUBE ACCOUNT SYNC & 2-WAY ACTIONS
# =========================================================================

@app.post("/api/auth/google/oauth", response_model=AuthTokenResponse)
async def google_oauth_youtube_sync(
    body: GoogleOAuthSyncRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
):
    access_token = body.access_token
    refresh_token: str | None = None
    expires_in = body.expires_in or 3500

    if not access_token and body.code:
        try:
            token_data = await exchange_google_auth_code(body.code, body.redirect_uri)
            access_token = token_data.get("access_token")
            refresh_token = token_data.get("refresh_token")
            expires_in = int(token_data.get("expires_in") or 3500)
        except Exception as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Gagal menukar kode OAuth Google: {exc}",
            ) from exc

    if not access_token:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="access_token atau code Google OAuth wajib disertakan.",
        )

    try:
        profile = await fetch_google_userinfo(access_token)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token Google OAuth tidak valid atau sudah kedaluwarsa.",
        ) from exc

    email = str(profile.get("email") or "").strip().lower()
    if not email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Akun Google tidak memiliki alamat email yang terverifikasi.",
        )

    user = await find_or_create_google_user(
        db=db,
        email=email,
        name=str(profile.get("name") or email.split("@")[0]),
        picture=profile.get("picture"),
    )

    user.google_user_id = str(profile.get("sub") or user.google_user_id or "")
    user.google_access_token = access_token
    if refresh_token:
        user.google_refresh_token = refresh_token
    user.google_token_expires_at = datetime.now(timezone.utc) + timedelta(seconds=expires_in)
    await db.commit()
    await db.refresh(user)

    jwt_token = create_access_token(user.id, user.username)
    return AuthTokenResponse(access_token=jwt_token, user=await _build_user_public(db, user))


@app.get("/api/youtube/subscriptions")
async def get_synced_youtube_subscriptions(
    current_user: Annotated[User, Depends(get_current_user)],
):
    if not current_user.google_access_token:
        return {"connected": False, "channels": [], "videos": []}
    try:
        data = await fetch_youtube_subscriptions(current_user.google_access_token)
        return {"connected": True, **data}
    except Exception as exc:
        logger.warning("YouTube subscriptions sync failed: %s", exc)
        return {"connected": False, "channels": [], "videos": [], "error": "Token YouTube perlu disegarkan."}


@app.get("/api/youtube/liked")
async def get_synced_youtube_liked(
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    yt_videos: list[dict] = []
    connected = bool(current_user.google_access_token)
    if current_user.google_access_token:
        try:
            yt_videos = await fetch_youtube_liked_videos(current_user.google_access_token)
        except Exception as exc:
            logger.warning("YouTube liked sync failed: %s", exc)
            connected = False

    # Also include local VeloxTube liked videos
    liked_ids = list(
        (
            await db.execute(
                select(VideoReaction.video_id).where(
                    VideoReaction.user_id == current_user.id,
                    VideoReaction.is_like.is_(True),
                )
            )
        )
        .scalars()
        .all()
    )
    local_serialized: list[dict] = []
    if liked_ids:
        local_vids = list(
            (
                await db.execute(
                    select(Video).options(selectinload(Video.channel)).where(Video.id.in_(liked_ids))
                )
            )
            .scalars()
            .all()
        )
        local_serialized = [
            v.model_dump(mode="json")
            for v in await _serialize_video_cards(db, local_vids, current_user)
        ]

    return {"connected": connected, "items": local_serialized + yt_videos}


@app.get("/api/youtube/playlists")
async def get_synced_youtube_playlists(
    current_user: Annotated[User, Depends(get_current_user)],
):
    if not current_user.google_access_token:
        return {"connected": False, "items": []}
    try:
        playlists = await fetch_youtube_playlists(current_user.google_access_token)
        return {"connected": True, "items": playlists}
    except Exception as exc:
        logger.warning("YouTube playlists sync failed: %s", exc)
        return {"connected": False, "items": []}


@app.post("/api/youtube/rate")
async def youtube_two_way_rate(
    body: YouTubeRateRequest,
    current_user: Annotated[User, Depends(get_current_user)],
):
    if not current_user.google_access_token:
        return {"synced": False, "reason": "Akun Google/YouTube belum dihubungkan."}
    ok = await rate_youtube_video(current_user.google_access_token, body.yt_id, body.rating)
    return {"synced": ok, "yt_id": body.yt_id, "rating": body.rating}


@app.post("/api/youtube/subscribe")
async def youtube_two_way_subscribe(
    body: YouTubeSubscribeRequest,
    current_user: Annotated[User, Depends(get_current_user)],
):
    if not current_user.google_access_token:
        return {"synced": False, "reason": "Akun Google/YouTube belum dihubungkan."}
    ok = await subscribe_youtube_channel(current_user.google_access_token, body.yt_channel_id)
    return {"synced": ok, "yt_channel_id": body.yt_channel_id}


@app.post("/api/youtube/comment")
async def youtube_two_way_comment(
    body: YouTubeCommentRequest,
    current_user: Annotated[User, Depends(get_current_user)],
):
    if not current_user.google_access_token:
        return {"synced": False, "reason": "Akun Google/YouTube belum dihubungkan."}
    ok = await post_youtube_comment(current_user.google_access_token, body.yt_id, body.content)
    return {"synced": ok, "yt_id": body.yt_id}


# =========================================================================
# 7. CROSS-DEVICE WATCH HISTORY, RESUME PLAYBACK & GOOGLE TAKEOUT IMPORT
# =========================================================================

@app.get("/api/history", response_model=list[WatchHistoryItemResponse])
async def list_watch_history(
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
    limit: int = Query(default=60, ge=1, le=200),
):
    rows = list(
        (
            await db.execute(
                select(WatchHistory)
                .where(WatchHistory.user_id == current_user.id)
                .order_by(desc(WatchHistory.watched_at))
                .limit(limit)
            )
        )
        .scalars()
        .all()
    )
    return [
        WatchHistoryItemResponse(
            video_key=r.video_key,
            title=r.title,
            channel_name=r.channel_name,
            thumbnail_url=r.thumbnail_url,
            duration_sec=r.duration_sec,
            position_sec=r.position_sec,
            source=r.source,
            watched_at=r.watched_at,
        )
        for r in rows
    ]


@app.post("/api/history/progress", response_model=WatchHistoryItemResponse)
async def upsert_watch_history_progress(
    body: WatchHistoryProgressRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    existing = (
        await db.execute(
            select(WatchHistory).where(
                WatchHistory.user_id == current_user.id,
                WatchHistory.video_key == body.video_key,
            )
        )
    ).scalar_one_or_none()

    now = datetime.now(timezone.utc)
    if existing:
        existing.title = body.title
        existing.channel_name = body.channel_name
        if body.thumbnail_url:
            existing.thumbnail_url = body.thumbnail_url
        if body.duration_sec > 0:
            existing.duration_sec = body.duration_sec
        existing.position_sec = body.position_sec
        existing.watched_at = now
        entry = existing
    else:
        entry = WatchHistory(
            user_id=current_user.id,
            google_user_id=current_user.google_user_id,
            video_key=body.video_key,
            title=body.title,
            channel_name=body.channel_name,
            thumbnail_url=body.thumbnail_url,
            duration_sec=body.duration_sec,
            position_sec=body.position_sec,
            source=body.source,
            watched_at=now,
        )
        db.add(entry)

    await db.commit()
    await db.refresh(entry)
    return WatchHistoryItemResponse(
        video_key=entry.video_key,
        title=entry.title,
        channel_name=entry.channel_name,
        thumbnail_url=entry.thumbnail_url,
        duration_sec=entry.duration_sec,
        position_sec=entry.position_sec,
        source=entry.source,
        watched_at=entry.watched_at,
    )


@app.delete("/api/history/{video_key}")
async def delete_watch_history_item(
    video_key: str,
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    await db.execute(
        delete(WatchHistory).where(
            WatchHistory.user_id == current_user.id,
            WatchHistory.video_key == video_key,
        )
    )
    await db.commit()
    return {"deleted": True, "video_key": video_key}


@app.delete("/api/history")
async def clear_watch_history(
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
):
    await db.execute(delete(WatchHistory).where(WatchHistory.user_id == current_user.id))
    await db.commit()
    return {"cleared": True}


@app.post("/api/history/import-takeout")
async def import_takeout_watch_history(
    db: Annotated[AsyncSession, Depends(get_db)],
    current_user: Annotated[User, Depends(get_current_user)],
    file: UploadFile = File(...),
):
    raw = await file.read()
    if len(raw) > 25 * 1024 * 1024:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Ukuran file watch-history.json maksimal 25 MB.",
        )
    try:
        entries = parse_takeout_watch_history(raw, max_items=300)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Gagal membaca file Google Takeout: {exc}",
        ) from exc

    imported = 0
    for item in entries:
        existing = (
            await db.execute(
                select(WatchHistory).where(
                    WatchHistory.user_id == current_user.id,
                    WatchHistory.video_key == item["video_key"],
                )
            )
        ).scalar_one_or_none()
        if existing is None:
            db.add(
                WatchHistory(
                    user_id=current_user.id,
                    google_user_id=current_user.google_user_id,
                    **item,
                )
            )
            imported += 1

    await db.commit()
    return {
        "imported": imported,
        "parsed_total": len(entries),
        "message": f"Berhasil mengimpor {imported} riwayat tontonan dari Google Takeout.",
    }
