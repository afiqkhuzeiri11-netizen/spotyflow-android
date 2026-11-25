from __future__ import annotations

import asyncio
import html
import logging
import os
import shutil
from pathlib import Path

from sqlalchemy import select

from database import AsyncSessionLocal
from models import Video

logger = logging.getLogger("veloxtube.processor")

BASE_DIR = Path(__file__).resolve().parent
MEDIA_ROOT = BASE_DIR / "media"
UPLOADS_DIR = MEDIA_ROOT / "uploads"
HLS_DIR = MEDIA_ROOT / "hls"
THUMBS_DIR = MEDIA_ROOT / "thumbnails"


def ensure_media_dirs() -> None:
    for d in (MEDIA_ROOT, UPLOADS_DIR, HLS_DIR, THUMBS_DIR):
        d.mkdir(parents=True, exist_ok=True)


def get_ffmpeg_path() -> str | None:
    env_path = os.getenv("FFMPEG_PATH", "").strip()
    if env_path and Path(env_path).exists():
        return env_path
    return shutil.which("ffmpeg")


def get_ffprobe_path() -> str | None:
    env_path = os.getenv("FFPROBE_PATH", "").strip()
    if env_path and Path(env_path).exists():
        return env_path
    return shutil.which("ffprobe")


def is_ffmpeg_available() -> bool:
    return get_ffmpeg_path() is not None


async def _run_cmd(args: list[str], timeout_sec: float = 600.0) -> tuple[int, str, str]:
    proc = await asyncio.create_subprocess_exec(
        *args,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout_b, stderr_b = await asyncio.wait_for(proc.communicate(), timeout=timeout_sec)
        return (
            proc.returncode or 0,
            stdout_b.decode("utf-8", errors="ignore"),
            stderr_b.decode("utf-8", errors="ignore"),
        )
    except asyncio.TimeoutError:
        proc.kill()
        await proc.wait()
        return (-1, "", "FFmpeg process timed out")


async def probe_video_duration(input_path: Path) -> int:
    ffprobe = get_ffprobe_path()
    if not ffprobe:
        return 0
    rc, out, _ = await _run_cmd(
        [
            ffprobe,
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
            str(input_path),
        ],
        timeout_sec=20.0,
    )
    if rc == 0 and out.strip():
        try:
            return max(0, int(round(float(out.strip()))))
        except ValueError:
            return 0
    return 0


def generate_fallback_svg_thumbnail(video_id: int, title: str, is_short: bool) -> str:
    """Create a crisp local SVG thumbnail when user didn't upload one and FFmpeg is absent."""
    ensure_media_dirs()
    width, height = (540, 960) if is_short else (1280, 720)
    safe_title = html.escape(title.strip()[:46] or "VeloxTube Video")
    badge = "SHORTS" if is_short else "HD VIDEO"

    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#1A1A24"/>
      <stop offset="55%" stop-color="#121216"/>
      <stop offset="100%" stop-color="#290D12"/>
    </linearGradient>
  </defs>
  <rect width="100%" height="100%" fill="url(#bg)"/>
  <circle cx="{width // 2}" cy="{int(height * 0.44)}" r="{68 if is_short else 76}" fill="#FF0000"/>
  <polygon points="{width // 2 - 18},{int(height * 0.44) - 26} {width // 2 + 30},{int(height * 0.44)} {width // 2 - 18},{int(height * 0.44) + 26}" fill="#FFFFFF"/>
  <rect x="40" y="40" width="136" height="36" rx="8" fill="#FF0000"/>
  <text x="108" y="64" fill="#FFFFFF" font-family="Arial, sans-serif" font-size="16" font-weight="700" text-anchor="middle">{badge}</text>
  <text x="{width // 2}" y="{int(height * 0.68)}" fill="#F1F1F1" font-family="Arial, sans-serif" font-size="{26 if is_short else 38}" font-weight="700" text-anchor="middle">{safe_title}</text>
</svg>
"""
    thumb_name = f"thumb_{video_id}.svg"
    thumb_path = THUMBS_DIR / thumb_name
    thumb_path.write_text(svg, encoding="utf-8")
    return f"/media/thumbnails/{thumb_name}"


async def generate_ffmpeg_thumbnail(input_path: Path, video_id: int) -> str | None:
    ffmpeg = get_ffmpeg_path()
    if not ffmpeg:
        return None
    ensure_media_dirs()
    thumb_name = f"thumb_{video_id}.jpg"
    thumb_path = THUMBS_DIR / thumb_name
    rc, _, err = await _run_cmd(
        [
            ffmpeg,
            "-y",
            "-ss",
            "00:00:01.5",
            "-i",
            str(input_path),
            "-vframes",
            "1",
            "-vf",
            "scale='min(1280,iw)':-2",
            "-q:v",
            "3",
            str(thumb_path),
        ],
        timeout_sec=45.0,
    )
    if rc == 0 and thumb_path.exists() and thumb_path.stat().st_size > 500:
        return f"/media/thumbnails/{thumb_name}"
    logger.warning("Thumbnail generation failed for video %s: %s", video_id, err[:240])
    return None


async def transcode_video_to_hls_background(
    video_id: int,
    input_path: Path,
    need_auto_thumb: bool,
    is_short: bool,
) -> None:
    """
    BackgroundTasks worker:
    - If FFmpeg is installed: transcodes video to adaptive multi-resolution HLS (720p + 480p)
      with master.m3u8 + .ts segments, probes duration, and auto-generates JPG thumbnail if needed.
    - If FFmpeg is NOT installed: marks status 'ready' immediately so the video streams via
      our HTTP 206 Byte-Range Streaming endpoint (/api/media/stream/{filename}) with instant seek.
    """
    ensure_media_dirs()
    ffmpeg = get_ffmpeg_path()

    if not ffmpeg:
        logger.info(
            "FFmpeg not installed; video %s ready immediately via HTTP Byte-Range MP4 streaming.",
            video_id,
        )
        async with AsyncSessionLocal() as session:
            video = (
                await session.execute(select(Video).where(Video.id == video_id))
            ).scalar_one_or_none()
            if video:
                video.processing_status = "ready"
                await session.commit()
        return

    try:
        duration_sec = await probe_video_duration(input_path)
        generated_thumb: str | None = None
        if need_auto_thumb:
            generated_thumb = await generate_ffmpeg_thumbnail(input_path, video_id)

        out_dir = HLS_DIR / f"video_{video_id}"
        dir_720 = out_dir / "720p"
        dir_480 = out_dir / "480p"
        dir_720.mkdir(parents=True, exist_ok=True)
        dir_480.mkdir(parents=True, exist_ok=True)

        scale_720 = "scale=-2:720" if not is_short else "scale=720:-2"
        scale_480 = "scale=-2:480" if not is_short else "scale=480:-2"

        # 720p adaptive variant
        rc720, _, err720 = await _run_cmd(
            [
                ffmpeg,
                "-y",
                "-i",
                str(input_path),
                "-vf",
                scale_720,
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-crf",
                "23",
                "-g",
                "48",
                "-sc_threshold",
                "0",
                "-c:a",
                "aac",
                "-b:a",
                "128k",
                "-ac",
                "2",
                "-f",
                "hls",
                "-hls_time",
                "4",
                "-hls_playlist_type",
                "vod",
                "-hls_segment_filename",
                str(dir_720 / "seg_%03d.ts"),
                str(dir_720 / "index.m3u8"),
            ]
        )

        # 480p adaptive variant
        rc480, _, err480 = await _run_cmd(
            [
                ffmpeg,
                "-y",
                "-i",
                str(input_path),
                "-vf",
                scale_480,
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-crf",
                "24",
                "-g",
                "48",
                "-sc_threshold",
                "0",
                "-c:a",
                "aac",
                "-b:a",
                "96k",
                "-ac",
                "2",
                "-f",
                "hls",
                "-hls_time",
                "4",
                "-hls_playlist_type",
                "vod",
                "-hls_segment_filename",
                str(dir_480 / "seg_%03d.ts"),
                str(dir_480 / "index.m3u8"),
            ]
        )

        hls_master_url: str | None = None
        if rc720 == 0 and rc480 == 0:
            res720 = "720x1280" if is_short else "1280x720"
            res480 = "480x854" if is_short else "854x480"
            master_playlist = (
                "#EXTM3U\n"
                "#EXT-X-VERSION:3\n"
                f"#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION={res720}\n"
                "720p/index.m3u8\n"
                f"#EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION={res480}\n"
                "480p/index.m3u8\n"
            )
            (out_dir / "master.m3u8").write_text(master_playlist, encoding="utf-8")
            hls_master_url = f"/media/hls/video_{video_id}/master.m3u8"
        else:
            logger.warning(
                "HLS transcode fallback to MP4 Byte-Range for video %s (rc720=%s, rc480=%s): %s | %s",
                video_id,
                rc720,
                rc480,
                err720[:180],
                err480[:180],
            )

        async with AsyncSessionLocal() as session:
            video = (
                await session.execute(select(Video).where(Video.id == video_id))
            ).scalar_one_or_none()
            if video:
                if hls_master_url:
                    video.hls_url = hls_master_url
                if generated_thumb:
                    video.thumbnail_url = generated_thumb
                if duration_sec > 0:
                    video.duration_sec = duration_sec
                video.processing_status = "ready"
                await session.commit()
    except Exception as exc:
        logger.exception("Unexpected error processing video %s: %s", video_id, exc)
        async with AsyncSessionLocal() as session:
            video = (
                await session.execute(select(Video).where(Video.id == video_id))
            ).scalar_one_or_none()
            if video:
                # Keep playable via MP4 Byte-Range fallback rather than locking the video
                video.processing_status = "ready"
                await session.commit()
