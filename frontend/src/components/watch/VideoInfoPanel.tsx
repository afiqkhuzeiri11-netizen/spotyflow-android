import React, { useMemo, useState } from 'react';
import {
  Bell,
  Bookmark,
  BookmarkCheck,
  CheckCircle2,
  Share2,
  ThumbsDown,
  ThumbsUp,
} from 'lucide-react';
import type { VideoDetailItem } from '../../types/api';
import { formatCompactNumber, formatRelativeTime } from '../../utils/format';

interface VideoInfoPanelProps {
  video: VideoDetailItem;
  isWatchLater: boolean;
  isReacting: boolean;
  isSubscribing: boolean;
  onReact: (action: 'like' | 'dislike') => void;
  onSubscribe: () => void;
  onShare: () => void;
  onToggleWatchLater: () => void;
  onSelectTag?: (tag: string) => void;
}

export const VideoInfoPanel: React.FC<VideoInfoPanelProps> = React.memo(
  ({
    video,
    isWatchLater,
    isReacting,
    isSubscribing,
    onReact,
    onSubscribe,
    onShare,
    onToggleWatchLater,
    onSelectTag,
  }) => {
    const [descExpanded, setDescExpanded] = useState(false);

    const formattedFullViews = useMemo(() => {
      try {
        return new Intl.NumberFormat('id-ID').format(video.views);
      } catch {
        return String(video.views);
      }
    }, [video.views]);

    const formattedUploadDate = useMemo(() => {
      const ts = Date.parse(video.created_at);
      if (!Number.isFinite(ts)) return formatRelativeTime(video.created_at);
      try {
        return new Intl.DateTimeFormat('id-ID', {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
        }).format(new Date(ts));
      } catch {
        return formatRelativeTime(video.created_at);
      }
    }, [video.created_at]);

    const cleanTags = useMemo(
      () => (video.tags || []).map((t) => t.replace(/^#/, '').trim()).filter(Boolean),
      [video.tags]
    );

    const channelInitial = (video.channel.display_name || 'V').slice(0, 1).toUpperCase();

    return (
      <section className="px-3 pt-3 md:px-0">
        {/* Video Title */}
        <h1
          className="text-lg font-bold leading-snug tracking-tight md:text-[20px] md:leading-7"
          style={{ color: 'var(--vt-text)' }}
        >
          {video.title}
        </h1>

        {/* Channel Row + Action Pills */}
        <div className="mt-3 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          {/* Channel Info + Subscribe */}
          <div className="flex items-center justify-between gap-3 sm:justify-start">
            <div className="flex min-w-0 items-center gap-3">
              {video.channel.avatar_url ? (
                <img
                  src={video.channel.avatar_url}
                  alt={video.channel.display_name}
                  loading="lazy"
                  decoding="async"
                  className="h-10 w-10 shrink-0 rounded-full object-cover"
                  style={{ backgroundColor: 'var(--vt-chip)' }}
                />
              ) : (
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#FF0000] text-sm font-bold text-white">
                  {channelInitial}
                </div>
              )}

              <div className="min-w-0">
                <div className="flex items-center gap-1">
                  <span
                    className="truncate text-[15px] font-semibold"
                    style={{ color: 'var(--vt-text)' }}
                  >
                    {video.channel.display_name}
                  </span>
                  {video.channel.verified && (
                    <CheckCircle2
                      className="h-3.5 w-3.5 shrink-0"
                      style={{ color: 'var(--vt-text-secondary)' }}
                    />
                  )}
                </div>
                <p className="text-xs" style={{ color: 'var(--vt-text-secondary)' }}>
                  {formatCompactNumber(video.channel.subscribers_count)} subscriber
                </p>
              </div>
            </div>

            <button
              type="button"
              disabled={isSubscribing}
              onClick={onSubscribe}
              className="ml-2 inline-flex h-9 shrink-0 cursor-pointer items-center gap-1.5 rounded-full px-4 text-sm font-semibold transition-transform active:scale-95 disabled:opacity-60"
              style={
                video.channel.subscribed_by_me
                  ? {
                      backgroundColor: 'var(--vt-chip)',
                      color: 'var(--vt-text)',
                    }
                  : {
                      backgroundColor: 'var(--vt-chip-active)',
                      color: 'var(--vt-chip-active-text)',
                    }
              }
            >
              {video.channel.subscribed_by_me && <Bell className="h-4 w-4" />}
              <span>{video.channel.subscribed_by_me ? 'Disubscribe' : 'Subscribe'}</span>
            </button>
          </div>

          {/* Action Buttons (Like/Dislike Pill, Share, Watch Later) */}
          <div className="vt-no-scrollbar flex items-center gap-2 overflow-x-auto pb-0.5 lg:pb-0">
            {/* Pill Like / Dislike */}
            <div
              className="inline-flex h-9 shrink-0 items-center overflow-hidden rounded-full"
              style={{ backgroundColor: 'var(--vt-chip)', color: 'var(--vt-text)' }}
            >
              <button
                type="button"
                disabled={isReacting}
                onClick={() => onReact('like')}
                aria-label="Suka video ini"
                aria-pressed={video.liked_by_me}
                className="flex h-full cursor-pointer items-center gap-1.5 px-3.5 text-sm font-medium transition-colors hover:bg-white/10 disabled:opacity-60"
              >
                <ThumbsUp
                  className={`h-4 w-4 ${video.liked_by_me ? 'fill-current text-[#3EA6FF]' : ''}`}
                />
                <span>{formatCompactNumber(video.likes_count)}</span>
              </button>

              <span
                aria-hidden="true"
                className="h-5 w-px"
                style={{ backgroundColor: 'var(--vt-border)' }}
              />

              <button
                type="button"
                disabled={isReacting}
                onClick={() => onReact('dislike')}
                aria-label="Tidak suka video ini"
                aria-pressed={video.disliked_by_me}
                className="flex h-full cursor-pointer items-center px-3.5 text-sm font-medium transition-colors hover:bg-white/10 disabled:opacity-60"
              >
                <ThumbsDown
                  className={`h-4 w-4 ${video.disliked_by_me ? 'fill-current text-[#FF4E45]' : ''}`}
                />
              </button>
            </div>

            {/* Share */}
            <button
              type="button"
              onClick={onShare}
              className="inline-flex h-9 shrink-0 cursor-pointer items-center gap-1.5 rounded-full px-3.5 text-sm font-medium transition-colors"
              style={{ backgroundColor: 'var(--vt-chip)', color: 'var(--vt-text)' }}
            >
              <Share2 className="h-4 w-4" />
              <span>Bagikan</span>
            </button>

            {/* Watch Later */}
            <button
              type="button"
              onClick={onToggleWatchLater}
              aria-pressed={isWatchLater}
              className="inline-flex h-9 shrink-0 cursor-pointer items-center gap-1.5 rounded-full px-3.5 text-sm font-medium transition-colors"
              style={{ backgroundColor: 'var(--vt-chip)', color: 'var(--vt-text)' }}
            >
              {isWatchLater ? (
                <BookmarkCheck className="h-4 w-4 text-[#3EA6FF]" />
              ) : (
                <Bookmark className="h-4 w-4" />
              )}
              <span>{isWatchLater ? 'Tersimpan' : 'Tonton Nanti'}</span>
            </button>
          </div>
        </div>

        {/* Expandable YouTube Description Box */}
        <div
          onClick={() => {
            if (!descExpanded) setDescExpanded(true);
          }}
          className={`mt-3 rounded-xl p-3 text-sm transition-colors ${
            !descExpanded ? 'cursor-pointer' : ''
          }`}
          style={{
            backgroundColor: 'var(--vt-chip)',
            color: 'var(--vt-text)',
          }}
        >
          <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[13px] font-semibold">
            <span>
              {video.is_live
                ? `${formattedFullViews} sedang menonton`
                : `${formattedFullViews} x ditonton`}
            </span>
            <span>{descExpanded ? formattedUploadDate : formatRelativeTime(video.created_at)}</span>
            {cleanTags.length > 0 && (
              <span className="flex flex-wrap items-center gap-1.5 font-medium text-[#3EA6FF]">
                {cleanTags.slice(0, descExpanded ? cleanTags.length : 3).map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onSelectTag?.(tag);
                    }}
                    className="cursor-pointer hover:underline"
                  >
                    #{tag}
                  </button>
                ))}
              </span>
            )}
          </div>

          <p
            className={`mt-1.5 whitespace-pre-line text-[13px] leading-relaxed ${
              descExpanded ? '' : 'line-clamp-2'
            }`}
            style={{ color: 'var(--vt-text)' }}
          >
            {video.description || 'Tidak ada deskripsi untuk video ini.'}
          </p>

          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              setDescExpanded((prev) => !prev);
            }}
            className="mt-2 cursor-pointer text-[13px] font-semibold hover:underline"
            style={{ color: 'var(--vt-text)' }}
          >
            {descExpanded ? 'Lebih sedikit' : '...Selengkapnya'}
          </button>
        </div>
      </section>
    );
  }
);

VideoInfoPanel.displayName = 'VideoInfoPanel';
