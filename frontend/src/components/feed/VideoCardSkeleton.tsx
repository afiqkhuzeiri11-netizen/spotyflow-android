import React from 'react';

export const VideoCardSkeleton: React.FC = React.memo(() => {
  return (
    <div className="flex flex-col" aria-hidden="true">
      <div className="vt-skeleton aspect-video w-full sm:rounded-xl" />
      <div className="flex gap-3 px-3 pt-3 sm:px-0">
        <div className="vt-skeleton h-9 w-9 shrink-0 rounded-full" />
        <div className="flex flex-1 flex-col gap-2 pt-0.5">
          <div className="vt-skeleton h-4 w-[90%] rounded" />
          <div className="vt-skeleton h-4 w-[66%] rounded" />
          <div className="vt-skeleton mt-0.5 h-3 w-[46%] rounded" />
        </div>
      </div>
    </div>
  );
});

VideoCardSkeleton.displayName = 'VideoCardSkeleton';
