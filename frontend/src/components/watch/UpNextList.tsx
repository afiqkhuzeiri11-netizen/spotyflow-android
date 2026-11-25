import React, { useMemo, useState } from 'react';
import { CheckCircle2, Radio } from 'lucide-react';
import type { UpNextFilter, VideoCardItem } from '../../types/api';
import { formatDuration, formatRelativeTime, formatViews } from '../../utils/format';

interface UpNextListProps {
  currentVideo: VideoCardItem;
  recommendations: VideoCardItem[];
  onSelectVideo: (video: VideoCardItem) => void;
}

export const UpNextList: React.FC<UpNextListProps> = React.memo(
  ({ currentVideo, recommendations, onSelectVideo }) => {
    const [filter, setFilter] = useState<UpNextFilter>('all');

    const filteredVideos = useMemo(() => {
      if (filter === 'channel') {
        const sameChannel = recommendations.filter(
          (v) => v.channel.id === currentVideo.channel.id
        );
        return sameChannel.length > 0 ? sameChannel : recommendations;
      }
      if (filter === 'related') {
        const sameCategory = recommendations.filter(
          (v) => v.category === currentVideo.category
        );
        return sameCategory.length > 0 ? sameCategory : recommendations;
      }
      return recommendations;
    }, [filter, recommendations, currentVideo.channel.id, currentVideo.category]);

    const chips: Array<{ id: UpNextFilter; label: string }> = [
      { id: 'all', label: 'Semua' },
      { id: 'channel', label: `Dari ${currentVideo.channel.display_name}` },
      { id: 'related', label: 'Terkait' },
    ];

    return (
      <section className="px-3 pb-8 md:px-0">
        {/* Filter Chips */}
        <div className="vt-no-scrollbar mb-3 flex items-center gap-2 overflow-x-auto pb-1">
          {chips.map((chip) => {
            const active = filter === chip.id;
            return (
              <button
                key={chip.id}
                type="button"
                onClick={() => setFilter(chip.id)}
                className="h-8 shrink-0 cursor-pointer rounded-lg px-3 text-[13px] font-medium transition-colors"
                style={{
                  backgroundColor: active ? 'var(--vt-chip-active)' : 'var(--vt-chip)',
                  color: active ? 'var(--vt-chip-active-text)' : 'var(--vt-text)',
                }}
              >
                {chip.label}
              </button>
            );
          })}
        </div>

        {/* Recommendations List */}
        {filteredVideos.length === 0 ? (
          <div
            className="rounded-xl p-6 text-center text-sm"
            style={{ backgroundColor: 'var(--vt-surface)', color: 'var(--vt-text-secondary)' }}
          >
            Belum ada video rekomendasi lainnya.
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {filteredVideos.map((vid) => (
              <article
                key={vid.id}
                onClick={() => onSelectVideo(vid)}
                className="group flex cursor-pointer gap-2.5 rounded-lg transition-colors"
              >
                {/* Compact 168x94 Thumbnail */}
                <div
                  className="relative h-[94px] w-[168px] shrink-0 overflow-hidden rounded-lg bg-black"
                >
                  <img
                    src={vid.thumbnail_url}
                    alt={vid.title}
                    loading="lazy"
                    decoding="async"
                    className="h-full w-full object-cover transition-transform duration-200 group-hover:scale-[1.03]"
                  />

                  {vid.is_live ? (
                    <span className="absolute bottom-1 right-1 inline-flex items-center gap-1 rounded bg-[#CC0000] px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-white">
                      <Radio className="h-2.5 w-2.5" />
                      LIVE
                    </span>
                  ) : (
                    <span className="absolute bottom-1 right-1 rounded bg-black/85 px-1.5 py-0.5 text-[11px] font-semibold text-white">
                      {formatDuration(vid.duration_sec)}
                    </span>
                  )}
                </div>

                {/* Metadata */}
                <div className="flex min-w-0 flex-1 flex-col">
                  <h3
                    className="line-clamp-2 text-[14px] font-semibold leading-snug"
                    style={{ color: 'var(--vt-text)' }}
                  >
                    {vid.title}
                  </h3>
                  <div
                    className="mt-1 flex items-center gap-1 text-xs"
                    style={{ color: 'var(--vt-text-secondary)' }}
                  >
                    <span className="truncate">{vid.channel.display_name}</span>
                    {vid.channel.verified && <CheckCircle2 className="h-3 w-3 shrink-0" />}
                  </div>
                  <p className="mt-0.5 text-xs" style={{ color: 'var(--vt-text-secondary)' }}>
                    {formatViews(vid.views, vid.is_live)}
                    {!vid.is_live && ` • ${formatRelativeTime(vid.created_at)}`}
                  </p>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    );
  }
);

UpNextList.displayName = 'UpNextList';
