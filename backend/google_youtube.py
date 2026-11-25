from __future__ import annotations

import json
import os
import re
from datetime import datetime, timezone
from typing import Any

import httpx

from youtube_engine import _stable_numeric_id, parse_iso8601_duration

YT_WATCH_URL_RE = re.compile(r"(?:v=|youtu\.be/|shorts/)([A-Za-z0-9_-]{11})")


async def exchange_google_auth_code(code: str, redirect_uri: str | None = None) -> dict[str, Any]:
    client_id = os.getenv("GOOGLE_CLIENT_ID", "").strip()
    client_secret = os.getenv("GOOGLE_CLIENT_SECRET", "").strip()
    resolved_redirect = (
        redirect_uri or os.getenv("GOOGLE_REDIRECT_URI", "http://localhost:5173")
    ).strip()

    if not client_id or not client_secret:
        raise ValueError("GOOGLE_CLIENT_ID atau GOOGLE_CLIENT_SECRET belum diisi di backend/.env")

    async with httpx.AsyncClient(timeout=8.0) as client:
        resp = await client.post(
            "https://oauth2.googleapis.com/token",
            data={
                "code": code,
                "client_id": client_id,
                "client_secret": client_secret,
                "redirect_uri": resolved_redirect,
                "grant_type": "authorization_code",
            },
        )
        resp.raise_for_status()
        return resp.json()


async def fetch_google_userinfo(access_token: str) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=6.0) as client:
        resp = await client.get(
            "https://www.googleapis.com/oauth2/v3/userinfo",
            headers={"Authorization": f"Bearer {access_token}"},
        )
        resp.raise_for_status()
        return resp.json()


async def fetch_youtube_subscriptions(access_token: str) -> dict[str, Any]:
    """Fetch user's real YouTube Subscriptions and latest videos from top subscribed channels."""
    headers = {"Authorization": f"Bearer {access_token}"}
    async with httpx.AsyncClient(timeout=8.0) as client:
        sub_resp = await client.get(
            "https://www.googleapis.com/youtube/v3/subscriptions",
            headers=headers,
            params={
                "part": "snippet,contentDetails",
                "mine": "true",
                "maxResults": 30,
                "order": "relevance",
            },
        )
        sub_resp.raise_for_status()
        sub_items = sub_resp.json().get("items", [])

        channels: list[dict[str, Any]] = []
        channel_ids: list[str] = []
        for item in sub_items:
            sn = item.get("snippet", {})
            ch_id = sn.get("resourceId", {}).get("channelId")
            if not ch_id:
                continue
            thumbs = sn.get("thumbnails", {})
            avatar = thumbs.get("medium", {}).get("url") or thumbs.get("default", {}).get("url")
            channels.append(
                {
                    "id": _stable_numeric_id(ch_id),
                    "yt_channel_id": ch_id,
                    "title": sn.get("title", "Channel"),
                    "description": sn.get("description", ""),
                    "avatar_url": avatar,
                }
            )
            channel_ids.append(ch_id)

        # Fetch latest videos from the top 6 subscribed channels via uploads playlist (UC -> UU)
        videos: list[dict[str, Any]] = []
        for ch_id in channel_ids[:6]:
            if not ch_id.startswith("UC"):
                continue
            uploads_playlist = f"UU{ch_id[2:]}"
            try:
                pl_resp = await client.get(
                    "https://www.googleapis.com/youtube/v3/playlistItems",
                    headers=headers,
                    params={
                        "part": "snippet,contentDetails",
                        "playlistId": uploads_playlist,
                        "maxResults": 4,
                    },
                )
                if pl_resp.status_code != 200:
                    continue
                for pit in pl_resp.json().get("items", []):
                    sn = pit.get("snippet", {})
                    yt_id = sn.get("resourceId", {}).get("videoId")
                    if not yt_id:
                        continue
                    thumbs = sn.get("thumbnails", {})
                    thumb = (
                        thumbs.get("high", {}).get("url")
                        or thumbs.get("medium", {}).get("url")
                        or f"https://i.ytimg.com/vi/{yt_id}/hqdefault.jpg"
                    )
                    ch_title = sn.get("channelTitle", "Subscribed Channel")
                    videos.append(
                        {
                            "id": _stable_numeric_id(yt_id),
                            "yt_id": yt_id,
                            "yt_channel_id": ch_id,
                            "title": sn.get("title", "Video"),
                            "description": sn.get("description", ""),
                            "category": "youtube",
                            "tags": ["youtube", "subscription"],
                            "thumbnail_url": thumb,
                            "hls_url": None,
                            "mp4_url": f"https://www.youtube.com/watch?v={yt_id}",
                            "duration_sec": 0,
                            "views": 0,
                            "likes_count": 0,
                            "is_short": False,
                            "is_live": False,
                            "processing_status": "ready",
                            "created_at": sn.get("publishedAt") or datetime.now(timezone.utc).isoformat(),
                            "channel": {
                                "id": _stable_numeric_id(ch_id),
                                "username": ch_id,
                                "display_name": ch_title,
                                "handle": f"@{re.sub(r'[^a-zA-Z0-9_]', '', ch_title.lower())[:20] or 'channel'}",
                                "avatar_url": next(
                                    (c["avatar_url"] for c in channels if c["yt_channel_id"] == ch_id),
                                    None,
                                ),
                                "verified": True,
                                "subscribers_count": 0,
                                "subscribed_by_me": True,
                                "yt_channel_id": ch_id,
                            },
                        }
                    )
            except Exception:
                continue

        videos.sort(key=lambda v: str(v.get("created_at", "")), reverse=True)
        return {"channels": channels, "videos": videos}


async def fetch_youtube_liked_videos(access_token: str) -> list[dict[str, Any]]:
    headers = {"Authorization": f"Bearer {access_token}"}
    async with httpx.AsyncClient(timeout=8.0) as client:
        resp = await client.get(
            "https://www.googleapis.com/youtube/v3/videos",
            headers=headers,
            params={
                "part": "snippet,contentDetails,statistics",
                "myRating": "like",
                "maxResults": 30,
            },
        )
        resp.raise_for_status()
        items = resp.json().get("items", [])

    videos: list[dict[str, Any]] = []
    for it in items:
        yt_id = it.get("id")
        if not yt_id:
            continue
        sn = it.get("snippet", {})
        stats = it.get("statistics", {})
        dur = parse_iso8601_duration(it.get("contentDetails", {}).get("duration"))
        thumbs = sn.get("thumbnails", {})
        thumb = (
            thumbs.get("high", {}).get("url")
            or thumbs.get("medium", {}).get("url")
            or f"https://i.ytimg.com/vi/{yt_id}/hqdefault.jpg"
        )
        ch_id = sn.get("channelId", yt_id)
        ch_title = sn.get("channelTitle", "YouTube Creator")
        videos.append(
            {
                "id": _stable_numeric_id(yt_id),
                "yt_id": yt_id,
                "yt_channel_id": ch_id,
                "title": sn.get("title", "Liked Video"),
                "description": sn.get("description", ""),
                "category": "youtube",
                "tags": list(sn.get("tags") or [])[:6],
                "thumbnail_url": thumb,
                "hls_url": None,
                "mp4_url": f"https://www.youtube.com/watch?v={yt_id}",
                "duration_sec": dur,
                "views": int(stats.get("viewCount") or 0),
                "likes_count": int(stats.get("likeCount") or 0),
                "is_short": 0 < dur <= 62,
                "is_live": False,
                "processing_status": "ready",
                "created_at": sn.get("publishedAt") or datetime.now(timezone.utc).isoformat(),
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
    return videos


async def fetch_youtube_playlists(access_token: str) -> list[dict[str, Any]]:
    headers = {"Authorization": f"Bearer {access_token}"}
    async with httpx.AsyncClient(timeout=8.0) as client:
        resp = await client.get(
            "https://www.googleapis.com/youtube/v3/playlists",
            headers=headers,
            params={
                "part": "snippet,contentDetails",
                "mine": "true",
                "maxResults": 25,
            },
        )
        resp.raise_for_status()
        items = resp.json().get("items", [])

    playlists: list[dict[str, Any]] = []
    for it in items:
        sn = it.get("snippet", {})
        thumbs = sn.get("thumbnails", {})
        playlists.append(
            {
                "id": it.get("id", ""),
                "title": sn.get("title", "Playlist"),
                "description": sn.get("description", ""),
                "item_count": int(it.get("contentDetails", {}).get("itemCount") or 0),
                "thumbnail_url": thumbs.get("high", {}).get("url")
                or thumbs.get("medium", {}).get("url")
                or "",
            }
        )
    return playlists


async def rate_youtube_video(access_token: str, yt_id: str, rating: str) -> bool:
    """2-Way sync: rating in ('like', 'dislike', 'none')."""
    async with httpx.AsyncClient(timeout=6.0) as client:
        resp = await client.post(
            "https://www.googleapis.com/youtube/v3/videos/rate",
            headers={"Authorization": f"Bearer {access_token}"},
            params={"id": yt_id, "rating": rating},
        )
        return resp.status_code in (200, 204)


async def subscribe_youtube_channel(access_token: str, yt_channel_id: str) -> bool:
    """2-Way sync: subscribe to a YouTube channel on user's Google account."""
    async with httpx.AsyncClient(timeout=6.0) as client:
        resp = await client.post(
            "https://www.googleapis.com/youtube/v3/subscriptions",
            headers={"Authorization": f"Bearer {access_token}"},
            params={"part": "snippet"},
            json={
                "snippet": {
                    "resourceId": {
                        "kind": "youtube#channel",
                        "channelId": yt_channel_id,
                    }
                }
            },
        )
        return resp.status_code in (200, 201, 400)


async def post_youtube_comment(access_token: str, yt_id: str, text_original: str) -> bool:
    """2-Way sync: post a top-level comment to a YouTube video."""
    async with httpx.AsyncClient(timeout=6.0) as client:
        resp = await client.post(
            "https://www.googleapis.com/youtube/v3/commentThreads",
            headers={"Authorization": f"Bearer {access_token}"},
            params={"part": "snippet"},
            json={
                "snippet": {
                    "videoId": yt_id,
                    "topLevelComment": {
                        "snippet": {"textOriginal": text_original},
                    },
                }
            },
        )
        return resp.status_code in (200, 201)


def parse_takeout_watch_history(raw_bytes: bytes, max_items: int = 300) -> list[dict[str, Any]]:
    """
    Parse Google Takeout `watch-history.json` into normalized WatchHistory items.
    """
    data = json.loads(raw_bytes.decode("utf-8", errors="ignore"))
    if not isinstance(data, list):
        raise ValueError("Format watch-history.json tidak valid (harus berupa JSON array).")

    parsed: list[dict[str, Any]] = []
    seen: set[str] = set()
    for entry in data:
        if not isinstance(entry, dict):
            continue
        title_url = str(entry.get("titleUrl") or "")
        match = YT_WATCH_URL_RE.search(title_url)
        if not match:
            continue
        yt_id = match.group(1)
        video_key = f"yt_{yt_id}"
        if video_key in seen:
            continue
        seen.add(video_key)

        raw_title = str(entry.get("title") or "").strip()
        for prefix in ("Watched ", "Menonton "):
            if raw_title.startswith(prefix):
                raw_title = raw_title[len(prefix) :].strip()
        if not raw_title:
            raw_title = f"YouTube Video ({yt_id})"

        subtitles = entry.get("subtitles") or []
        channel_name = "YouTube"
        if isinstance(subtitles, list) and subtitles and isinstance(subtitles[0], dict):
            channel_name = str(subtitles[0].get("name") or "YouTube").strip()

        watched_at = datetime.now(timezone.utc)
        raw_time = entry.get("time")
        if isinstance(raw_time, str) and raw_time:
            try:
                watched_at = datetime.fromisoformat(raw_time.replace("Z", "+00:00"))
            except Exception:
                pass

        parsed.append(
            {
                "video_key": video_key,
                "title": raw_title[:240],
                "channel_name": channel_name[:120],
                "thumbnail_url": f"https://i.ytimg.com/vi/{yt_id}/hqdefault.jpg",
                "duration_sec": 0,
                "position_sec": 0.0,
                "source": "takeout",
                "watched_at": watched_at,
            }
        )
        if len(parsed) >= max_items:
            break

    return parsed
