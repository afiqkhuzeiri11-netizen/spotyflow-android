import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { CommentSection } from '../components/watch/CommentSection';
import { UpNextList } from '../components/watch/UpNextList';
import { VideoInfoPanel } from '../components/watch/VideoInfoPanel';
import { useAuth } from '../context/AuthContext';
import { usePlayer } from '../context/PlayerContext';
import { extractApiError, videosApi } from '../services/api';
import type { VideoCardItem, VideoDetailItem } from '../types/api';

interface WatchPageProps {
  watchLaterIds: number[];
  onToggleWatchLater: (videoId: number) => void;
  onShareVideo: (video: VideoCardItem) => void;
  onSelectTag: (tag: string) => void;
  onNotify: (msg: string) => void;
}

export const WatchPage: React.FC<WatchPageProps> = ({
  watchLaterIds,
  onToggleWatchLater,
  onShareVideo,
  onSelectTag,
  onNotify,
}) => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user, openAuthModal } = useAuth();
  const { activeVideo, isMiniplayer, isTheater, loadVideoIntoPlayer } = usePlayer();

  const videoId = Number(id);
  const [detail, setDetail] = useState<VideoDetailItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isReacting, setIsReacting] = useState(false);
  const [isSubscribing, setIsSubscribing] = useState(false);

  const fetchDetail = useCallback(async () => {
    if (!Number.isFinite(videoId) || videoId <= 0) {
      setErrorMsg('ID video tidak valid.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMsg(null);
    try {
      const data = await videosApi.detail(videoId);
      setDetail(data);
      // Sync into persistent GlobalVideoPlayer (same id never reloads stream)
      loadVideoIntoPlayer(data, false);
    } catch (err) {
      setErrorMsg(extractApiError(err, 'Gagal memuat detail video.'));
    } finally {
      setLoading(false);
    }
  }, [videoId, loadVideoIntoPlayer]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  const handleReact = useCallback(
    async (action: 'like' | 'dislike') => {
      if (!detail) return;
      if (!user) {
        openAuthModal('login');
        onNotify('Silakan login untuk menyukai atau tidak menyukai video.');
        return;
      }
      if (isReacting) return;

      const snapshot = detail;
      const nextLiked = action === 'like' ? !detail.liked_by_me : false;
      const nextDisliked = action === 'dislike' ? !detail.disliked_by_me : false;
      let nextLikesCount = detail.likes_count;
      let nextDislikesCount = detail.dislikes_count;

      if (action === 'like') {
        nextLikesCount = Math.max(0, detail.likes_count + (nextLiked ? 1 : -1));
        if (detail.disliked_by_me) {
          nextDislikesCount = Math.max(0, detail.dislikes_count - 1);
        }
      } else {
        nextDislikesCount = Math.max(0, detail.dislikes_count + (nextDisliked ? 1 : -1));
        if (detail.liked_by_me) {
          nextLikesCount = Math.max(0, detail.likes_count - 1);
        }
      }

      setDetail({
        ...detail,
        liked_by_me: nextLiked,
        disliked_by_me: nextDisliked,
        likes_count: nextLikesCount,
        dislikes_count: nextDislikesCount,
      });

      setIsReacting(true);
      try {
        const res = await videosApi.react(detail.id, action);
        setDetail((curr) =>
          curr && curr.id === res.video_id
            ? {
                ...curr,
                likes_count: res.likes_count,
                dislikes_count: res.dislikes_count,
                liked_by_me: res.liked_by_me,
                disliked_by_me: res.disliked_by_me,
              }
            : curr
        );
      } catch (err) {
        setDetail(snapshot);
        onNotify(extractApiError(err, 'Gagal menyimpan reaksi video.'));
      } finally {
        setIsReacting(false);
      }
    },
    [detail, user, openAuthModal, onNotify, isReacting]
  );

  const handleSubscribe = useCallback(async () => {
    if (!detail) return;
    if (!user) {
      openAuthModal('login');
      onNotify('Silakan login untuk subscribe channel ini.');
      return;
    }
    if (isSubscribing) return;

    const snapshot = detail;
    const nextSub = !detail.channel.subscribed_by_me;
    setDetail({
      ...detail,
      channel: {
        ...detail.channel,
        subscribed_by_me: nextSub,
        subscribers_count: Math.max(0, detail.channel.subscribers_count + (nextSub ? 1 : -1)),
      },
    });

    setIsSubscribing(true);
    try {
      const res = await videosApi.subscribe(detail.channel.id);
      setDetail((curr) =>
        curr && curr.channel.id === res.channel_id
          ? {
              ...curr,
              channel: {
                ...curr.channel,
                subscribed_by_me: res.subscribed,
                subscribers_count: res.subscribers_count,
              },
            }
          : curr
      );
      onNotify(
        res.subscribed
          ? `Berhasil subscribe ${detail.channel.display_name}`
          : `Berhenti subscribe ${detail.channel.display_name}`
      );
    } catch (err) {
      setDetail(snapshot);
      onNotify(extractApiError(err, 'Gagal mengubah status subscribe.'));
    } finally {
      setIsSubscribing(false);
    }
  }, [detail, user, openAuthModal, onNotify, isSubscribing]);

  const handleSelectRecommended = useCallback(
    (vid: VideoCardItem) => {
      loadVideoIntoPlayer(vid, false);
      navigate(`/watch/${vid.id}`);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
    [loadVideoIntoPlayer, navigate]
  );

  const handleShare = useCallback(() => {
    if (detail) onShareVideo(detail);
  }, [detail, onShareVideo]);

  const handleToggleWL = useCallback(() => {
    if (detail) onToggleWatchLater(detail.id);
  }, [detail, onToggleWatchLater]);

  // Error State
  if (!loading && errorMsg) {
    return (
      <div className="col-span-full mx-auto my-12 flex max-w-md flex-col items-center rounded-2xl p-8 text-center">
        <AlertCircle className="h-12 w-12 text-[#FF4E45]" />
        <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--vt-text)' }}>
          Video tidak dapat dimuat
        </h2>
        <p className="mt-1.5 text-sm" style={{ color: 'var(--vt-text-secondary)' }}>
          {errorMsg}
        </p>
        <div className="mt-5 flex items-center gap-3">
          <button
            type="button"
            onClick={fetchDetail}
            className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-full bg-[#3EA6FF] px-5 text-sm font-semibold text-[#0F0F0F]"
          >
            <RefreshCw className="h-4 w-4" />
            Coba Lagi
          </button>
          <button
            type="button"
            onClick={() => navigate('/')}
            className="h-10 cursor-pointer rounded-full px-5 text-sm font-semibold"
            style={{ backgroundColor: 'var(--vt-chip)', color: 'var(--vt-text)' }}
          >
            Ke Beranda
          </button>
        </div>
      </div>
    );
  }

  // Initial Skeleton while loading direct URL before activeVideo is populated
  if (loading && !detail) {
    return (
      <>
        {(!activeVideo || isMiniplayer) && (
          <div className="vt-shimmer aspect-video w-full md:rounded-xl lg:col-start-1 lg:row-start-1" />
        )}
        <div className="min-w-0 px-3 pt-4 md:px-0 lg:col-start-1 lg:row-start-2">
          <div className="vt-shimmer h-6 w-3/4 rounded-md" />
          <div className="mt-4 flex items-center gap-3">
            <div className="vt-shimmer h-10 w-10 rounded-full" />
            <div className="space-y-2">
              <div className="vt-shimmer h-4 w-36 rounded" />
              <div className="vt-shimmer h-3 w-24 rounded" />
            </div>
          </div>
          <div className="vt-shimmer mt-4 h-24 w-full rounded-xl" />
        </div>
        <div className="mt-4 space-y-3 px-3 md:px-0 lg:col-start-2 lg:row-start-1 lg:row-span-2 lg:mt-0">
          {Array.from({ length: 6 }).map((_, idx) => (
            <div key={idx} className="flex gap-2.5">
              <div className="vt-shimmer h-[94px] w-[168px] shrink-0 rounded-lg" />
              <div className="flex-1 space-y-2">
                <div className="vt-shimmer h-4 w-full rounded" />
                <div className="vt-shimmer h-3 w-2/3 rounded" />
                <div className="vt-shimmer h-3 w-1/2 rounded" />
              </div>
            </div>
          ))}
        </div>
      </>
    );
  }

  if (!detail) return null;

  return (
    <>
      {/* Left Column under Player: Video Info + Comments */}
      <div className="min-w-0 pb-16 md:pb-10 lg:col-start-1 lg:row-start-2">
        <VideoInfoPanel
          video={detail}
          isWatchLater={watchLaterIds.includes(detail.id)}
          isReacting={isReacting}
          isSubscribing={isSubscribing}
          onReact={handleReact}
          onSubscribe={handleSubscribe}
          onShare={handleShare}
          onToggleWatchLater={handleToggleWL}
          onSelectTag={onSelectTag}
        />

        <CommentSection
          videoId={detail.id}
          initialTotal={detail.comments_count}
          onNotify={onNotify}
        />
      </div>

      {/* Right Column: Up Next Recommendations */}
      <aside
        className={
          isTheater
            ? 'mt-4 min-w-0 pb-16 md:pb-8 lg:col-start-2 lg:row-start-2 lg:mt-3'
            : 'mt-4 min-w-0 pb-16 md:pb-8 lg:col-start-2 lg:row-start-1 lg:row-span-2 lg:mt-0'
        }
      >
        <UpNextList
          currentVideo={detail}
          recommendations={detail.recommendations}
          onSelectVideo={handleSelectRecommended}
        />
      </aside>
    </>
  );
};
