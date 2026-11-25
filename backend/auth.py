from __future__ import annotations

import os
import re
import secrets
from datetime import datetime, timedelta, timezone
from typing import Annotated

import httpx
import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from passlib.context import CryptContext
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import User

JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "veloxtube-dev-secret-change-in-production-2026")
JWT_ALGORITHM = "HS256"
JWT_EXPIRE_MINUTES = int(os.getenv("JWT_EXPIRE_MINUTES", str(60 * 24 * 7)))

GOOGLE_CLIENT_ID = os.getenv(
    "GOOGLE_CLIENT_ID",
    "602927550377-v1dicdufnh5t2l3baq74jlbtg4gold37.apps.googleusercontent.com",
).strip()

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
bearer_scheme = HTTPBearer(auto_error=False)


def hash_password(plain_password: str) -> str:
    safe_pwd = plain_password.encode("utf-8")[:72].decode("utf-8", errors="ignore")
    return pwd_context.hash(safe_pwd)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    try:
        safe_pwd = plain_password.encode("utf-8")[:72].decode("utf-8", errors="ignore")
        return pwd_context.verify(safe_pwd, hashed_password)
    except Exception:
        return False


def create_access_token(user_id: int, username: str) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "username": username,
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(minutes=JWT_EXPIRE_MINUTES)).timestamp()),
    }
    return jwt.encode(payload, JWT_SECRET_KEY, algorithm=JWT_ALGORITHM)


def decode_access_token(token: str) -> dict:
    try:
        return jwt.decode(token, JWT_SECRET_KEY, algorithms=[JWT_ALGORITHM])
    except jwt.ExpiredSignatureError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Sesi login telah kedaluwarsa. Silakan login kembali.",
        ) from exc
    except jwt.InvalidTokenError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token autentikasi tidak valid.",
        ) from exc


def make_handle(raw: str) -> str:
    cleaned = re.sub(r"[^a-z0-9_]", "", raw.lower().strip())
    return f"@{cleaned or 'user'}"


async def verify_google_id_token(credential: str) -> dict:
    """Verify Google OAuth 2.0 ID token against Google's tokeninfo endpoint."""
    try:
        async with httpx.AsyncClient(timeout=7.0) as client:
            resp = await client.get(
                "https://oauth2.googleapis.com/tokeninfo",
                params={"id_token": credential},
                headers={"Accept": "application/json"},
            )
    except httpx.TimeoutException as exc:
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail="Waktu verifikasi ke server Google habis. Silakan coba lagi.",
        ) from exc
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Gagal menghubungi server verifikasi Google.",
        ) from exc

    info = resp.json() if resp.content else {}
    if resp.status_code != 200:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Token Google ditolak ({info.get('error_description', 'invalid_token')}).",
        )

    aud = str(info.get("aud", "")).strip()
    if GOOGLE_CLIENT_ID and aud != GOOGLE_CLIENT_ID:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Client ID Google tidak cocok dengan konfigurasi server VeloxTube.",
        )

    email_verified = str(info.get("email_verified", "")).lower() == "true"
    email = str(info.get("email", "")).strip().lower()
    if not email_verified or "@" not in email:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Akun Google belum memiliki email terverifikasi.",
        )

    return {
        "sub": str(info.get("sub", "")).strip(),
        "email": email,
        "name": str(info.get("name") or email.split("@")[0]).strip(),
        "picture": str(info.get("picture") or "").strip() or None,
    }


async def find_or_create_google_user(db: AsyncSession, google_info: dict) -> User:
    email = google_info["email"]
    stmt = select(User).where(User.email == email)
    existing = (await db.execute(stmt)).scalar_one_or_none()
    if existing is not None:
        if google_info.get("picture") and existing.avatar_url != google_info["picture"]:
            existing.avatar_url = google_info["picture"]
            await db.commit()
            await db.refresh(existing)
        return existing

    base_username = re.sub(r"[^a-z0-9_]", "", email.split("@")[0].lower()) or "kreator"
    username = base_username[:36]
    suffix = 1
    while (
        await db.execute(select(User.id).where(User.username == username))
    ).scalar_one_or_none() is not None:
        username = f"{base_username[:30]}{suffix}"
        suffix += 1

    user = User(
        username=username,
        email=email,
        display_name=google_info["name"][:80],
        handle=make_handle(username),
        password_hash=hash_password(secrets.token_urlsafe(32)),
        avatar_url=google_info.get("picture"),
        verified=False,
        provider="google",
        base_subscribers=0,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    return user


async def get_optional_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> User | None:
    if credentials is None or not credentials.credentials:
        return None
    try:
        payload = decode_access_token(credentials.credentials)
        user_id = int(payload.get("sub", 0))
    except (HTTPException, ValueError, TypeError):
        return None
    if user_id <= 0:
        return None
    return (await db.execute(select(User).where(User.id == user_id))).scalar_one_or_none()


async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> User:
    if credentials is None or not credentials.credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Autentikasi diperlukan. Silakan login terlebih dahulu.",
        )
    payload = decode_access_token(credentials.credentials)
    try:
        user_id = int(payload.get("sub", 0))
    except (ValueError, TypeError) as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token tidak valid.",
        ) from exc

    user = (await db.execute(select(User).where(User.id == user_id))).scalar_one_or_none()
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Pengguna tidak ditemukan.",
        )
    return user
