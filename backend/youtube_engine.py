from __future__ import annotations

import asyncio
import hashlib
import os
import re
from datetime import datetime, timedelta, timezone
from typing import Any

import httpx
from cachetools import TTLCache

# In-memory TTL Caches for sub-millisecond repeat hits (Anti-Lag & Zero Perceived Delay)
SEARCH_CACHE: TTLCache[str, list[dict[str, Any]]] = TTLCache(maxsize=256, ttl=2400)  # 40 min
SUGGEST_CACHE: TTLCache[str, list[str]] = TTLCache(maxsize=512, ttl=3600)  # 60 min
STREAM_CACHE: TTLCache[str, dict[str, Any]] = TTLCache(maxsize=512, ttl=2700)  # 45 min
TRENDING_CACHE: TTLCache[str, list[str]] = TTLCache(maxsize=8, ttl=1800)  # 30 min

ISO_DURATION_RE = re.compile(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?")

DEFAULT_TRENDING_QUERIES_ID = [
    "review gadget terbaru 2026",
    "musik akustik santai indonesia",
    "live gaming valorant indonesia",
    "tutorial react fastapi fullstack",
    "resep ramen jepang rumahan",
    "podcast teknologi ai indonesia",
    "lofi chill beats untuk belajar",
    "vlog jalan jalan jepang 4k",
    "setup meja kerja minimalis",
    "highlight sepak bola terbaru",
]


def _stable_numeric_id(yt_id: str) -> int:
    digest = hashlib.sha1(yt_id.encode("utf-8")).hexdigest()[:8]
    return 100_000_000 + (int(digest, 16) % 800_000_000)


def parse_iso8601_duration(iso: str | None) -> int:
    if not iso:
        return 0
    m = ISO_DURATION_RE.match(iso)
    if not m:
        return 0
    h = int(m.group(1) or 0)
    mins = int(m.group(2) or 0)
    secs = int(m.group(3) or 0)
    return h * 3600 + mins * 60 + secs


async def fetch_youtube_suggestions(keyword: str) -> list[str]:
    """
    Fetch real-time YouTube autocomplete suggestions (id/ID) with 1-hour TTL cache.
    """
    clean = keyword.strip()
    if not clean:
        return await fetch_trending_queries()

    cache_key = clean.lower()
    cached = SUGGEST_CACHE.get(cache_key)
    if cached is not None:
        return cached

    url = "http://suggestqueries.google.com/complete/search"
    params = {
        "client": "firefox",
        "ds": "yt",
        "hl": "id",
        "gl": "ID",
        "q": clean,
    }
    try:
        async with httpx.AsyncClient(timeout=2.5) as client:
            resp = await client.get(url, params=params)
            resp.raise_for_status()
            payload = resp.json()
            if isinstance(payload, list) and len(payload) >= 2 and isinstance(payload[1], list):
                suggestions = [str(s).strip() for s in payload[1] if str(s).strip()][:10]
                SUGGEST_CACHE[cache_key] = suggestions
                return suggestions
    except Exception:
        pass

    # Fallback partial match from trending queries
    lower = clean.lower()
    fallback = [q for q in DEFAULT_TRENDING_QUERIES_ID if lower in q.lower()][:10]
    return fallback


async def fetch_trending_queries() -> list[str]:
    cached = TRENDING_CACHE.get("id")
    if cached is not None:
        return cached

    api_key = os.getenv("YOUTUBE_API_KEY", "").strip()
    if api_key:
        try:
            async with httpx.AsyncClient(
                timeout=3.5,
                headers={"Referer": "https://spotyflow.vercel.app/"},
            ) as client:
                resp = await client.get(
                    "https://www.googleapis.com/youtube/v3/videos",
                    params={
                        "part": "snippet",
                        "chart": "mostPopular",
                        "regionCode": "ID",
                        "maxResults": 10,
                        "key": api_key,
                    },
                )
                if resp.status_code == 200:
                    data = resp.json()
                    titles = [
                        str(item.get("snippet", {}).get("title", "")).strip()
                        for item in data.get("items", [])
                    ]
                    titles = [t for t in titles if t][:10]
                    if titles:
                        TRENDING_CACHE["id"] = titles
                        return titles
        except Exception:
            pass

    TRENDING_CACHE["id"] = DEFAULT_TRENDING_QUERIES_ID
    return DEFAULT_TRENDING_QUERIES_ID


async def _search_via_youtube_api(
    query: str,
    filter_type: str,
    api_key: str,
    page_token: str | None = None,
) -> tuple[list[dict[str, Any]], str | None]:
    params: dict[str, Any] = {
        "part": "snippet",
        "q": query,
        "maxResults": 50,
        "regionCode": "ID",
        "relevanceLanguage": "id",
        "key": api_key,
    }
    if page_token:
        params["pageToken"] = page_token

    if filter_type == "channel":
        params["type"] = "channel"
    else:
        params["type"] = "video"

    if filter_type == "live":
        params["eventType"] = "live"
    elif filter_type == "today":
        params["publishedAfter"] = (
            datetime.now(timezone.utc) - timedelta(hours=24)
        ).strftime("%Y-%m-%dT%H:%M:%SZ")
    elif filter_type == "most_viewed":
        params["order"] = "viewCount"
    elif filter_type == "long":
        params["videoDuration"] = "long"
    elif filter_type == "shorts":
        params["videoDuration"] = "short"

    async with httpx.AsyncClient(
        timeout=6.0,
        headers={"Referer": "https://spotyflow.vercel.app/"},
    ) as client:
        resp = await client.get("https://www.googleapis.com/youtube/v3/search", params=params)
        resp.raise_for_status()
        data = resp.json()

        next_token = data.get("nextPageToken")
        raw_items = data.get("items", [])
        video_ids = [
            it.get("id", {}).get("videoId")
            for it in raw_items
            if it.get("id", {}).get("videoId")
        ]

        details_map: dict[str, dict[str, Any]] = {}
        if video_ids:
            det_resp = await client.get(
                "https://www.googleapis.com/youtube/v3/videos",
                params={
                    "part": "contentDetails,statistics,liveStreamingDetails",
                    "id": ",".join(video_ids[:50]),
                    "key": api_key,
                },
            )
            if det_resp.status_code == 200:
                for v in det_resp.json().get("items", []):
                    details_map[v["id"]] = v

    results: list[dict[str, Any]] = []
    for item in raw_items:
        id_obj = item.get("id", {})
        snippet = item.get("snippet", {})
        kind = id_obj.get("kind", "")
        thumbs = snippet.get("thumbnails", {})
        thumb_url = (
            thumbs.get("high", {}).get("url")
            or thumbs.get("medium", {}).get("url")
            or thumbs.get("default", {}).get("url")
            or ""
        )

        if kind == "youtube#channel" or id_obj.get("channelId") and not id_obj.get("videoId"):
            ch_id = id_obj.get("channelId", "")
            if not ch_id:
                continue
            results.append(
                {
                    "id": _stable_numeric_id(ch_id),
                    "yt_id": None,
                    "yt_channel_id": ch_id,
                    "result_type": "channel",
                    "title": snippet.get("title", "Channel YouTube"),
                    "description": snippet.get("description", ""),
                    "category": "youtube",
                    "tags": ["channel", "youtube"],
                    "thumbnail_url": thumb_url,
                    "hls_url": None,
                    "mp4_url": "",
                    "duration_sec": 0,
                    "views": 0,
                    "likes_count": 0,
                    "is_short": False,
                    "is_live": False,
                    "processing_status": "ready",
                    "created_at": snippet.get("publishedAt") or datetime.now(timezone.utc).isoformat(),
                    "channel": {
                        "id": _stable_numeric_id(ch_id),
                        "username": ch_id,
                        "display_name": snippet.get("channelTitle") or snippet.get("title", "Channel"),
                        "handle": f"@{re.sub(r'[^a-zA-Z0-9_]', '', (snippet.get('title') or 'channel').lower())[:20]}",
                        "avatar_url": thumb_url,
                        "verified": True,
                        "subscribers_count": 0,
                        "subscribed_by_me": False,
                        "yt_channel_id": ch_id,
                    },
                }
            )
            continue

        yt_id = id_obj.get("videoId")
        if not yt_id:
            continue

        det = details_map.get(yt_id, {})
        dur_sec = parse_iso8601_duration(det.get("contentDetails", {}).get("duration"))
        stats = det.get("statistics", {})
        views = int(stats.get("viewCount") or 0)
        likes = int(stats.get("likeCount") or 0)
        is_live = snippet.get("liveBroadcastContent") == "live" or bool(
            det.get("liveStreamingDetails", {}).get("concurrentViewers")
        )
        is_short = (not is_live) and 0 < dur_sec <= 62
        ch_id = snippet.get("channelId", yt_id)
        ch_title = snippet.get("channelTitle", "YouTube Creator")

        results.append(
            {
                "id": _stable_numeric_id(yt_id),
                "yt_id": yt_id,
                "yt_channel_id": ch_id,
                "result_type": "short" if is_short else "video",
                "title": snippet.get("title", "Video YouTube"),
                "description": snippet.get("description", ""),
                "category": "live" if is_live else "youtube",
                "tags": ["youtube", "shorts"] if is_short else ["youtube"],
                "thumbnail_url": thumb_url or f"https://i.ytimg.com/vi/{yt_id}/hqdefault.jpg",
                "hls_url": None,
                "mp4_url": f"https://www.youtube.com/watch?v={yt_id}",
                "duration_sec": 0 if is_live else dur_sec,
                "views": views,
                "likes_count": likes,
                "is_short": is_short,
                "is_live": is_live,
                "processing_status": "ready",
                "created_at": snippet.get("publishedAt") or datetime.now(timezone.utc).isoformat(),
                "channel": {
                    "id": _stable_numeric_id(ch_id),
                    "username": ch_id,
                    "display_name": ch_title,
                    "handle": f"@{re.sub(r'[^a-zA-Z0-9_]', '', ch_title.lower())[:20] or 'creator'}",
                    "avatar_url": None,
                    "verified": True,
                    "subscribers_count": 0,
                    "subscribed_by_me": False,
                    "yt_channel_id": ch_id,
                },
            }
        )

    return results, next_token


def _ytdlp_flat_search_sync(query: str, count: int = 60) -> list[dict[str, Any]]:
    import yt_dlp

    ydl_opts: dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "extract_flat": "in_playlist",
        "skip_download": True,
        "ignoreerrors": True,
        "socket_timeout": 8,
    }
    search_expr = f"ytsearch{count}:{query}"
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(search_expr, download=False)

    entries = (info or {}).get("entries") or []
    results: list[dict[str, Any]] = []
    now_iso = datetime.now(timezone.utc).isoformat()

    for entry in entries:
        if not entry:
            continue
        yt_id = str(entry.get("id") or "").strip()
        if not yt_id or len(yt_id) != 11:
            continue
        title = str(entry.get("title") or "Video YouTube").strip()
        dur_sec = int(entry.get("duration") or 0)
        views = int(entry.get("view_count") or 0)
        is_live = bool(entry.get("live_status") == "is_live" or entry.get("is_live"))
        is_short = (not is_live) and 0 < dur_sec <= 62
        ch_name = str(entry.get("channel") or entry.get("uploader") or "YouTube Creator").strip()
        ch_id = str(entry.get("channel_id") or entry.get("uploader_id") or ch_name)
        thumbs = entry.get("thumbnails") or []
        thumb_url = (
            thumbs[-1].get("url")
            if thumbs and isinstance(thumbs[-1], dict) and thumbs[-1].get("url")
            else f"https://i.ytimg.com/vi/{yt_id}/hqdefault.jpg"
        )

        upload_date = entry.get("upload_date")
        created_at = now_iso
        if isinstance(upload_date, str) and len(upload_date) == 8 and upload_date.isdigit():
            try:
                created_at = datetime.strptime(upload_date, "%Y%m%d").replace(
                    tzinfo=timezone.utc
                ).isoformat()
            except Exception:
                created_at = now_iso

        results.append(
            {
                "id": _stable_numeric_id(yt_id),
                "yt_id": yt_id,
                "yt_channel_id": ch_id,
                "result_type": "short" if is_short else "video",
                "title": title,
                "description": str(entry.get("description") or ""),
                "category": "live" if is_live else "youtube",
                "tags": ["youtube", "shorts"] if is_short else ["youtube"],
                "thumbnail_url": thumb_url,
                "hls_url": None,
                "mp4_url": f"https://www.youtube.com/watch?v={yt_id}",
                "duration_sec": 0 if is_live else dur_sec,
                "views": views,
                "likes_count": max(0, views // 28),
                "is_short": is_short,
                "is_live": is_live,
                "processing_status": "ready",
                "created_at": created_at,
                "channel": {
                    "id": _stable_numeric_id(ch_id),
                    "username": ch_id,
                    "display_name": ch_name,
                    "handle": f"@{re.sub(r'[^a-zA-Z0-9_]', '', ch_name.lower())[:20] or 'creator'}",
                    "avatar_url": None,
                    "verified": True,
                    "subscribers_count": 0,
                    "subscribed_by_me": False,
                    "yt_channel_id": ch_id,
                },
            }
        )
    return results


def _apply_search_filter(items: list[dict[str, Any]], filter_type: str) -> list[dict[str, Any]]:
    f = (filter_type or "all").lower()
    if f == "video":
        return [i for i in items if not i.get("is_short") and i.get("result_type") != "channel"]
    if f == "shorts":
        return [i for i in items if i.get("is_short")]
    if f == "channel":
        channels = [i for i in items if i.get("result_type") == "channel"]
        if channels:
            return channels
        # Synthesize unique channels from video results when yt-dlp flat search was used
        seen: set[str] = set()
        synth: list[dict[str, Any]] = []
        for it in items:
            ch = it.get("channel") or {}
            cname = str(ch.get("display_name") or "")
            if cname and cname not in seen:
                seen.add(cname)
                synth.append(
                    {
                        **it,
                        "result_type": "channel",
                        "title": cname,
                        "description": f"Channel YouTube • {ch.get('handle', '')}",
                    }
                )
        return synth
    if f == "live":
        return [i for i in items if i.get("is_live")]
    if f == "today":
        cutoff = datetime.now(timezone.utc) - timedelta(hours=36)
        recent = []
        for it in items:
            try:
                dt = datetime.fromisoformat(str(it.get("created_at", "")).replace("Z", "+00:00"))
                if dt >= cutoff:
                    recent.append(it)
            except Exception:
                pass
        return recent or items[:20]
    if f == "most_viewed":
        return sorted(items, key=lambda x: int(x.get("views") or 0), reverse=True)
    if f == "long":
        return [i for i in items if int(i.get("duration_sec") or 0) >= 1200]
    return items


async def search_youtube_massive(
    query: str,
    filter_type: str = "all",
    page: int = 1,
    limit: int = 20,
    page_token: str | None = None,
) -> dict[str, Any]:
    """
    Massive YouTube search:
    - Uses YouTube Data API v3 (maxResults=50 + nextPageToken) if YOUTUBE_API_KEY is set.
    - Automatically falls back to yt-dlp flat playlist search (`ytsearch60:{query}`, ~1-2s).
    - Cached in TTLCache (40 min) with full filtering and pagination.
    """
    clean_q = query.strip()
    if not clean_q:
        return {
            "items": [],
            "total": 0,
            "page": page,
            "limit": limit,
            "has_more": False,
            "next_page_token": None,
            "source": "empty",
        }

    api_key = os.getenv("YOUTUBE_API_KEY", "").strip()
    cache_key = f"{clean_q.lower()}::{filter_type.lower()}::{page_token or 'p0'}::{'api' if api_key else 'ytdlp'}"
    source = "youtube_api" if api_key else "yt_dlp"
    next_token: str | None = None

    all_items = SEARCH_CACHE.get(cache_key)
    if all_items is None:
        if api_key:
            try:
                all_items, next_token = await _search_via_youtube_api(
                    clean_q, filter_type, api_key, page_token
                )
            except Exception:
                all_items = None
                source = "yt_dlp"

        if all_items is None:
            try:
                raw_query = (
                    f"{clean_q} #shorts"
                    if filter_type == "shorts"
                    else f"{clean_q} live"
                    if filter_type == "live"
                    else clean_q
                )
                raw = await asyncio.to_thread(_ytdlp_flat_search_sync, raw_query, 60)
                all_items = _apply_search_filter(raw, filter_type)
                source = "yt_dlp"
            except Exception:
                all_items = []
                source = "fallback"

        if all_items:
            SEARCH_CACHE[cache_key] = all_items

    total = len(all_items)
    start = max(0, (page - 1) * limit)
    sliced = all_items[start : start + limit]
    has_more = (start + len(sliced)) < total or bool(next_token)

    return {
        "items": sliced,
        "total": total,
        "page": page,
        "limit": limit,
        "has_more": has_more,
        "next_page_token": next_token,
        "source": source,
    }


def _resolve_yt_stream_sync(yt_id: str) -> dict[str, Any]:
    import yt_dlp

    ydl_opts: dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "socket_timeout": 8,
        # Prioritize combined video+audio (muxed) or HLS with audio so audio is 100% present!
        "format": "best[acodec!=none][vcodec!=none][ext=mp4]/best[acodec!=none][vcodec!=none]/best",
    }
    url = f"https://www.youtube.com/watch?v={yt_id}"
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)

    if not info:
        raise RuntimeError("Tidak dapat mengambil info video YouTube.")

    stream_url = str(info.get("url") or "")
    hls_url: str | None = None
    mp4_url = ""

    # Look for HLS manifest or progressive MP4 with BOTH video + audio tracks
    formats = info.get("formats") or []
    for fmt in reversed(formats):
        f_url = fmt.get("url")
        if not f_url:
            continue
        proto = str(fmt.get("protocol") or "")
        vcodec = str(fmt.get("vcodec") or "none")
        acodec = str(fmt.get("acodec") or "none")
        if "m3u8" in proto and vcodec != "none" and not hls_url:
            hls_url = f_url
        if vcodec != "none" and acodec != "none" and fmt.get("ext") == "mp4" and not mp4_url:
            mp4_url = f_url

    if not mp4_url and stream_url:
        if ".m3u8" in stream_url:
            hls_url = hls_url or stream_url
        else:
            mp4_url = stream_url

    ch_name = str(info.get("channel") or info.get("uploader") or "YouTube Creator")
    ch_id = str(info.get("channel_id") or info.get("uploader_id") or yt_id)
    dur_sec = int(info.get("duration") or 0)
    is_live = bool(info.get("is_live"))

    return {
        "yt_id": yt_id,
        "title": str(info.get("title") or "Video YouTube"),
        "description": str(info.get("description") or ""),
        "thumbnail_url": str(info.get("thumbnail") or f"https://i.ytimg.com/vi/{yt_id}/hqdefault.jpg"),
        "hls_url": hls_url,
        "mp4_url": mp4_url,
        "embed_url": f"https://www.youtube.com/embed/{yt_id}?autoplay=1&mute=0&playsinline=1&rel=0&enablejsapi=1",
        "duration_sec": 0 if is_live else dur_sec,
        "views": int(info.get("view_count") or 0),
        "likes_count": int(info.get("like_count") or 0),
        "is_live": is_live,
        "is_short": (not is_live) and 0 < dur_sec <= 62,
        "tags": list(info.get("tags") or [])[:12],
        "channel_name": ch_name,
        "channel_id": ch_id,
        "subscribers_count": int(info.get("channel_follower_count") or 0),
        "has_audio": True,
    }


async def resolve_youtube_stream(yt_id: str) -> dict[str, Any]:
    clean_id = yt_id.strip()
    cached = STREAM_CACHE.get(clean_id)
    if cached is not None:
        return cached

    try:
        resolved = await asyncio.to_thread(_resolve_yt_stream_sync, clean_id)
        STREAM_CACHE[clean_id] = resolved
        return resolved
    except Exception:
        fallback = {
            "yt_id": clean_id,
            "title": f"YouTube Video ({clean_id})",
            "description": "",
            "thumbnail_url": f"https://i.ytimg.com/vi/{clean_id}/hqdefault.jpg",
            "hls_url": None,
            "mp4_url": "",
            "embed_url": f"https://www.youtube.com/embed/{clean_id}?autoplay=1&mute=0&playsinline=1&rel=0&enablejsapi=1",
            "duration_sec": 0,
            "views": 0,
            "likes_count": 0,
            "is_live": False,
            "is_short": False,
            "tags": ["youtube"],
            "channel_name": "YouTube",
            "channel_id": clean_id,
            "subscribers_count": 0,
            "has_audio": True,
        }
        STREAM_CACHE[clean_id] = fallback
        return fallback
