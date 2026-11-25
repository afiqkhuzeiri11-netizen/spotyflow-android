import React, { useCallback, useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import {
  Bell,
  CheckCircle2,
  MessageSquare,
  Pause,
  Play,
  Share2,
  ThumbsDown,
  ThumbsUp,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { extractApiError, videosApi } from '../../services/api';
import type { VideoCardItem } from '../../types/api';
import { formatCompactNumber } from '../../utils/format';
import { MiniProgressBar } from '../player/PlayerControls';

interface ShortVideoItemProps {
  video: VideoCardItem;
  isActive: boolean;
  shouldLoad: boolean;
  isMuted: boolean;
  commentCountOverride?: number;
  onToggleMute: () => void;
  onOpenComments: (video: VideoCardItem) => void;
  onShare: (video: VideoCardItem) => void;
  onNotify: (msg: string) => void;
}

export const ShortVideoItem: React.FC<ShortVideoItemProps> = React.memo(
  ({
    video,
    isActive,
    shouldLoad,
    isMuted,
    commentCountOverride,
    onToggleMute,
    onOpenComments,
    onShare,
    onNotify,
  }) => {
    const { user, openAuthModal } = useAuth();
    const videoRef = useRef<HTMLVideoElement | null>(null);
    const hlsRef = useRef<Hls | null>(null);

    const [isPaused, setIsPaused] = useState(false);
    const [flashState, setFlashState] = useState<{ kind: 'play' | 'pause'; key: number } | null>(
      null
    );

    const [liked, setLiked] = useState(false);
    const [disliked, setDisliked] = useState(false);
    const [likesCount, setLikesCount] = useState(video.likes_count);
    const [subscribed, setSubscribed] = useState(video.channel.subscribed_by_me);
    const [reacting, setReacting] = useState(false);
    const [subscribing, setSubscribing] = useState(false);

    // Strict Anti-Memory-Leak Cleanup for off-screen Shorts (>1 slot away)
    const destroyMedia = useCallback(() => {
      if (hlsRef.current) {
        try {
          hlsRef.current.destroy();
        } catch {
          // Ignore destroy errors
        }
        hlsRef.current = null;
      }
      const el = videoRef.current;
      if (el) {
        try {
          el.pause();
          el.removeAttribute('src');
          el.load();
        } catch {
          // Ignore unload errors
        }
      }
    }, []);

    // Load stream ONLY when within active window (activeIndex +/- 1), destroy otherwise
    useEffect(() => {
      const el = videoRef.current;
      if (!el) return;

      if (!shouldLoad) {
        destroyMedia();
        return;
      }

      let cancelled = false;
      destroyMedia();

      const loadMp4 = () => {
        if (cancelled || !videoRef.current) return;
        videoRef.current.src = video.mp4_url;
        videoRef.current.load();
      };

      const hlsSrc = video.hls_url;
      if (hlsSrc && Hls.isSupported() && hlsSrc.includes('.m3u8')) {
        const hls = new Hls({
          enableWorker: true,
          maxBufferLength: 15,
          maxMaxBufferLength: 30,
          capLevelToPlayerSize: true,
        });
        hlsRef.current = hls;
        hls.loadSource(hlsSrc);
        hls.attachMedia(el);
        hls.on(Hls.Events.ERROR, (_evt, data) => {
          if (data.fatal) {
            try {
              hls.destroy();
            } catch {
              // Ignore
            }
            hlsRef.current = null;
            loadMp4();
          }
        });
      } else if (hlsSrc && el.canPlayType('application/vnd.apple.mpegurl')) {
        el.src = hlsSrc;
      } else {
        loadMp4();
      }

      return () => {
        cancelled = true;
        destroyMedia();
      };
    }, [shouldLoad, video.id, video.hls_url, video.mp4_url, destroyMedia]);

    // Sync active playback state strictly (only 1 Short plays at a time!)
    useEffect(() => {
      const el = videoRef.current;
      if (!el || !shouldLoad) return;

      el.muted = isMuted;
      if (isActive) {
        setIsPaused(false);
        el.play().catch(() => {
          // Fallback to muted autoplay if browser blocks unmuted playback
          el.muted = true;
          el.play().catch(() => setIsPaused(true));
        });
      } else {
        el.pause();
        try {
          el.currentTime = 0;
        } catch {
          // Ignore seek error before metadata loads
        }
      }
    }, [isActive, shouldLoad, isMuted]);

    const handleTogglePlay = useCallback(() => {
      const el = videoRef.current;
      if (!el) return;
      if (el.paused) {
        el.play().catch(() => {});
        setIsPaused(false);
        setFlashState({ kind: 'play', key: Date.now() });
      } else {
        el.pause();
        setIsPaused(true);
        setFlashState({ kind: 'pause', key: Date.now() });
      }
    }, []);

    const handleReact = async (action: 'like' | 'dislike', e: React.MouseEvent) => {
      e.stopPropagation();
      if (!user) {
        openAuthModal('login');
        onNotify('Silakan login untuk menyukai Shorts.');
        return;
      }
      if (reacting) return;

      const prev = { liked, disliked, likesCount };
      if (action === 'like') {
        const nextLiked = !liked;
        setLiked(nextLiked);
        setDisliked(false);
        setLikesCount((c) => Math.max(0, c + (nextLiked ? 1 : -1)));
      } else {
        const nextDisliked = !disliked;
        setDisliked(nextDisliked);
        if (liked) {
          setLiked(false);
          setLikesCount((c) => Math.max(0, c - 1));
        }
      }

      setReacting(true);
      try {
        const res = await videosApi.react(video.id, action);
        setLiked(res.liked_by_me);
        setDisliked(res.disliked_by_me);
        setLikesCount(res.likes_count);
      } catch (err) {
        setLiked(prev.liked);
        setDisliked(prev.disliked);
        setLikesCount(prev.likesCount);
        onNotify(extractApiError(err, 'Gagal menyimpan reaksi.'));
      } finally {
        setReacting(false);
      }
    };

    const handleSubscribe = async (e: React.MouseEvent) => {
      e.stopPropagation();
      if (!user) {
        openAuthModal('login');
        onNotify('Silakan login untuk subscribe channel.');
        return;
      }
      if (subscribing) return;

      const prevSub = subscribed;
      setSubscribed(!prevSub);
      setSubscribing(true);
      try {
        const res = await videosApi.subscribe(video.channel.id);
        setSubscribed(res.subscribed);
        onNotify(
          res.subscribed
            ? `Subscribe ke ${video.channel.display_name}`
            : `Berhenti subscribe ${video.channel.display_name}`
        );
      } catch (err) {
        setSubscribed(prevSub);
        onNotify(extractApiError(err, 'Gagal mengubah subscribe.'));
      } finally {
        setSubscribing(false);
      }
    };

    const initial = (video.channel.display_name || 'V').slice(0, 1).toUpperCase();

    return (
      <div className="relative flex h-full w-full snap-start snap-always items-center justify-center md:py-4">
        {/* Short Card Frame (Full screen on Mobile, 9:16 vertical stage on Desktop) */}
        <div
          onClick={handleTogglePlay}
          className="relative h-full w-full cursor-pointer overflow-hidden bg-black md:aspect-[9/16] md:h-[calc(100dvh-6.2rem)] md:max-h-[860px] md:w-auto md:rounded-2xl md:shadow-2xl"
        >
          {shouldLoad ? (
            <video
              ref={videoRef}
              loop
              playsInline
              muted={isMuted}
              poster={video.thumbnail_url}
              className="h-full w-full object-cover"
            />
          ) : (
            <img
              src={video.thumbnail_url}
              alt={video.title}
              loading="lazy"
              decoding="async"
              className="h-full w-full object-cover"
            />
          )}

          {/* Top & Bottom subtle readability gradients */}
          <div className="pointer-events-none absolute inset-x-0 top-0 h-24 bg-gradient-to-b from-black/55 to-transparent" />
          <div className="pointer-events-none absolute inset-x-0 bottom-0 h-56 bg-gradient-to-t from-black/85 via-black/35 to-transparent" />

          {/* Top-Left Play/Pause status + Top-Right Mute Toggle */}
          <div className="absolute inset-x-3 top-3 z-20 flex items-center justify-between">
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                handleTogglePlay();
              }}
              aria-label={isPaused ? 'Putar' : 'Jeda'}
              className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-black/45 text-white backdrop-blur-sm transition-colors hover:bg-black/65"
            >
              {isPaused ? (
                <Play className="h-5 w-5 fill-white" />
              ) : (
                <Pause className="h-5 w-5 fill-white" />
              )}
            </button>

            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onToggleMute();
              }}
              aria-label={isMuted ? 'Aktifkan suara' : 'Bisukan'}
              className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-black/45 text-white backdrop-blur-sm transition-colors hover:bg-black/65"
            >
              {isMuted ? <VolumeX className="h-5 w-5" /> : <Volume2 className="h-5 w-5" />}
            </button>
          </div>

          {/* Center Tap Play/Pause Flash */}
          {flashState && (
            <div
              key={flashState.key}
              className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center"
            >
              <div className="vt-center-flash flex h-16 w-16 items-center justify-center rounded-full bg-black/60 text-white">
                {flashState.kind === 'play' ? (
                  <Play className="h-7 w-7 fill-white" />
                ) : (
                  <Pause className="h-7 w-7 fill-white" />
                )}
              </div>
            </div>
          )}

          {/* Right Floating Action Bar (Like, Dislike, Comments, Share, Avatar) */}
          <div
            onClick={(e) => e.stopPropagation()}
            className="absolute bottom-20 right-3 z-20 flex flex-col items-center gap-4 md:bottom-8"
          >
            {/* Like */}
            <button
              type="button"
              onClick={(e) => handleReact('like', e)}
              className="flex cursor-pointer flex-col items-center gap-1 text-white"
            >
              <span
                className={`flex h-11 w-11 items-center justify-center rounded-full transition-transform active:scale-90 ${
                  liked ? 'bg-white text-black' : 'bg-black/45 text-white hover:bg-black/65'
                }`}
              >
                <ThumbsUp className={`h-5 w-5 ${liked ? 'fill-current' : ''}`} />
              </span>
              <span className="text-xs font-semibold drop-shadow">
                {formatCompactNumber(likesCount)}
              </span>
            </button>

            {/* Dislike */}
            <button
              type="button"
              onClick={(e) => handleReact('dislike', e)}
              className="flex cursor-pointer flex-col items-center gap-1 text-white"
            >
              <span
                className={`flex h-11 w-11 items-center justify-center rounded-full transition-transform active:scale-90 ${
                  disliked ? 'bg-white text-black' : 'bg-black/45 text-white hover:bg-black/65'
                }`}
              >
                <ThumbsDown className={`h-5 w-5 ${disliked ? 'fill-current' : ''}`} />
              </span>
              <span className="text-xs font-semibold drop-shadow">Tidak suka</span>
            </button>

            {/* Comments Drawer Trigger */}
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onOpenComments(video);
              }}
              className="flex cursor-pointer flex-col items-center gap-1 text-white"
            >
              <span className="flex h-11 w-11 items-center justify-center rounded-full bg-black/45 text-white transition-transform hover:bg-black/65 active:scale-90">
                <MessageSquare className="h-5 w-5" />
              </span>
              <span className="text-xs font-semibold drop-shadow">
                {commentCountOverride !== undefined
                  ? formatCompactNumber(commentCountOverride)
                  : 'Komentar'}
              </span>
            </button>

            {/* Share */}
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onShare(video);
              }}
              className="flex cursor-pointer flex-col items-center gap-1 text-white"
            >
              <span className="flex h-11 w-11 items-center justify-center rounded-full bg-black/45 text-white transition-transform hover:bg-black/65 active:scale-90">
                <Share2 className="h-5 w-5" />
              </span>
              <span className="text-xs font-semibold drop-shadow">Bagikan</span>
            </button>

            {/* Channel Mini Square Avatar */}
            <div className="mt-1 h-10 w-10 overflow-hidden rounded-lg border-2 border-white/80 bg-[#272727]">
              {video.channel.avatar_url ? (
                <img
                  src={video.channel.avatar_url}
                  alt={video.channel.display_name}
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="flex h-full w-full items-center justify-center bg-[#FF0000] text-xs font-bold text-white">
                  {initial}
                </div>
              )}
            </div>
          </div>

          {/* Bottom-Left Channel Info & Caption */}
          <div
            onClick={(e) => e.stopPropagation()}
            className="absolute bottom-6 left-3 right-20 z-20 text-white"
          >
            <div className="flex items-center gap-2">
              {video.channel.avatar_url ? (
                <img
                  src={video.channel.avatar_url}
                  alt={video.channel.display_name}
                  className="h-9 w-9 shrink-0 rounded-full border border-white/30 object-cover"
                />
              ) : (
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#FF0000] text-xs font-bold text-white">
                  {initial}
                </div>
              )}

              <div className="flex min-w-0 items-center gap-1">
                <span className="truncate text-sm font-semibold drop-shadow">
                  {video.channel.handle || `@${video.channel.username}`}
                </span>
                {video.channel.verified && <CheckCircle2 className="h-3.5 w-3.5 shrink-0" />}
              </div>

              <button
                type="button"
                disabled={subscribing}
                onClick={handleSubscribe}
                className={`ml-1 inline-flex h-8 shrink-0 cursor-pointer items-center gap-1 rounded-full px-3 text-xs font-semibold transition-transform active:scale-95 ${
                  subscribed
                    ? 'bg-white/20 text-white backdrop-blur-sm'
                    : 'bg-white text-[#0F0F0F]'
                }`}
              >
                {subscribed && <Bell className="h-3 w-3" />}
                <span>{subscribed ? 'Disubscribe' : 'Subscribe'}</span>
              </button>
            </div>

            <p className="mt-2 line-clamp-2 text-sm font-medium leading-snug text-white drop-shadow">
              {video.title}
            </p>
          </div>

          {/* Slim Isolated Red Progress Bar at the Bottom */}
          <div className="absolute inset-x-0 bottom-0 z-30">
            <MiniProgressBar videoRef={videoRef} isLive={false} />
          </div>
        </div>
      </div>
    );
  }
);

ShortVideoItem.displayName = 'ShortVideoItem';
