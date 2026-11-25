import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Maximize,
  Minimize,
  Pause,
  PictureInPicture2,
  Play,
  RectangleHorizontal,
  Settings,
  SkipForward,
  Sliders,
  Sparkles,
  Volume1,
  Volume2,
  VolumeX,
} from 'lucide-react';
import type { QualityOption } from '../../types/api';
import { formatDuration } from '../../utils/format';

const PLAYBACK_SPEEDS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2];

type SettingsPanel = 'closed' | 'main' | 'speed' | 'quality';

interface PlayerControlsProps {
  videoRef: React.RefObject<HTMLVideoElement | null>;
  isPlaying: boolean;
  isLive: boolean;
  isMuted: boolean;
  volume: number;
  playbackRate: number;
  qualities: QualityOption[];
  activeQualityLabel: string;
  ambientEnabled: boolean;
  isTheater: boolean;
  isFullscreen: boolean;
  visible: boolean;
  hasNextVideo: boolean;
  onTogglePlay: () => void;
  onNextVideo: () => void;
  onToggleMute: () => void;
  onVolumeChange: (val: number) => void;
  onPlaybackRateChange: (rate: number) => void;
  onSelectQuality: (opt: QualityOption) => void;
  onToggleAmbient: () => void;
  onToggleMiniplayer: () => void;
  onToggleTheater: () => void;
  onToggleFullscreen: () => void;
  onSettingsOpenChange: (open: boolean) => void;
  onUserActivity: () => void;
}

/**
 * Isolated PlayerControls:
 * Listens directly to HTMLVideoElement timeupdate/progress events so 250ms timeline updates
 * NEVER re-render WatchPage, CommentSection, UpNextList, or AppShell.
 */
export const PlayerControls: React.FC<PlayerControlsProps> = React.memo(
  ({
    videoRef,
    isPlaying,
    isLive,
    isMuted,
    volume,
    playbackRate,
    qualities,
    activeQualityLabel,
    ambientEnabled,
    isTheater,
    isFullscreen,
    visible,
    hasNextVideo,
    onTogglePlay,
    onNextVideo,
    onToggleMute,
    onVolumeChange,
    onPlaybackRateChange,
    onSelectQuality,
    onToggleAmbient,
    onToggleMiniplayer,
    onToggleTheater,
    onToggleFullscreen,
    onSettingsOpenChange,
    onUserActivity,
  }) => {
    const [currentTime, setCurrentTime] = useState(0);
    const [duration, setDuration] = useState(0);
    const [bufferedPct, setBufferedPct] = useState(0);
    const [isScrubbing, setIsScrubbing] = useState(false);
    const [hoverFrac, setHoverFrac] = useState<number | null>(null);
    const [settingsView, setSettingsView] = useState<SettingsPanel>('closed');

    const barRef = useRef<HTMLDivElement | null>(null);
    const settingsRef = useRef<HTMLDivElement | null>(null);
    const scrubbingRef = useRef(false);

    useEffect(() => {
      onSettingsOpenChange(settingsView !== 'closed');
    }, [settingsView, onSettingsOpenChange]);

    useEffect(() => {
      if (settingsView === 'closed') return;
      const onOutside = (e: MouseEvent) => {
        if (settingsRef.current && !settingsRef.current.contains(e.target as Node)) {
          setSettingsView('closed');
        }
      };
      document.addEventListener('mousedown', onOutside);
      return () => document.removeEventListener('mousedown', onOutside);
    }, [settingsView]);

    useEffect(() => {
      const el = videoRef.current;
      if (!el) return;

      const syncTime = () => {
        if (scrubbingRef.current) return;
        setCurrentTime(el.currentTime || 0);
        const dur = Number.isFinite(el.duration) && el.duration > 0 ? el.duration : 0;
        if (dur > 0) setDuration(dur);
      };

      const syncBuffer = () => {
        const dur = Number.isFinite(el.duration) && el.duration > 0 ? el.duration : 0;
        if (dur > 0 && el.buffered.length > 0) {
          const end = el.buffered.end(el.buffered.length - 1);
          setBufferedPct(Math.min(100, (end / dur) * 100));
        }
      };

      syncTime();
      syncBuffer();

      el.addEventListener('timeupdate', syncTime);
      el.addEventListener('durationchange', syncTime);
      el.addEventListener('loadedmetadata', syncTime);
      el.addEventListener('progress', syncBuffer);

      return () => {
        el.removeEventListener('timeupdate', syncTime);
        el.removeEventListener('durationchange', syncTime);
        el.removeEventListener('loadedmetadata', syncTime);
        el.removeEventListener('progress', syncBuffer);
      };
    }, [videoRef]);

    const fracFromClientX = useCallback((clientX: number): number => {
      const bar = barRef.current;
      if (!bar) return 0;
      const rect = bar.getBoundingClientRect();
      return Math.max(0, Math.min(1, (clientX - rect.left) / Math.max(1, rect.width)));
    }, []);

    const seekToFrac = useCallback(
      (frac: number) => {
        const el = videoRef.current;
        if (!el || isLive) return;
        const dur = Number.isFinite(el.duration) && el.duration > 0 ? el.duration : duration;
        if (dur <= 0) return;
        const target = frac * dur;
        el.currentTime = target;
        setCurrentTime(target);
      },
      [videoRef, isLive, duration]
    );

    const handleBarPointerDown = useCallback(
      (e: React.PointerEvent<HTMLDivElement>) => {
        if (isLive) return;
        e.stopPropagation();
        e.currentTarget.setPointerCapture(e.pointerId);
        scrubbingRef.current = true;
        setIsScrubbing(true);
        const frac = fracFromClientX(e.clientX);
        setHoverFrac(frac);
        seekToFrac(frac);
        onUserActivity();
      },
      [isLive, fracFromClientX, seekToFrac, onUserActivity]
    );

    const handleBarPointerMove = useCallback(
      (e: React.PointerEvent<HTMLDivElement>) => {
        const frac = fracFromClientX(e.clientX);
        setHoverFrac(frac);
        if (scrubbingRef.current) {
          seekToFrac(frac);
          onUserActivity();
        }
      },
      [fracFromClientX, seekToFrac, onUserActivity]
    );

    const handleBarPointerUp = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
      if (scrubbingRef.current) {
        scrubbingRef.current = false;
        setIsScrubbing(false);
        try {
          e.currentTarget.releasePointerCapture(e.pointerId);
        } catch {
          // Ignore
        }
      }
    }, []);

    const playedPct = isLive
      ? 100
      : duration > 0
      ? Math.min(100, (currentTime / duration) * 100)
      : 0;

    const effVol = isMuted ? 0 : volume;
    const VolumeIcon = effVol === 0 ? VolumeX : effVol < 0.5 ? Volume1 : Volume2;

    return (
      <div
        onClick={(e) => e.stopPropagation()}
        className={`absolute inset-x-0 bottom-0 z-20 flex flex-col justify-end bg-gradient-to-t from-black/85 via-black/45 to-transparent px-3 pb-2 pt-10 transition-opacity duration-200 ${
          visible || settingsView !== 'closed' || isScrubbing
            ? 'opacity-100'
            : 'pointer-events-none opacity-0'
        }`}
      >
        {/* Interactive Timeline Scrubber */}
        <div
          ref={barRef}
          onPointerDown={handleBarPointerDown}
          onPointerMove={handleBarPointerMove}
          onPointerUp={handleBarPointerUp}
          onPointerLeave={() => {
            if (!scrubbingRef.current) setHoverFrac(null);
          }}
          role="slider"
          aria-label="Timeline video"
          aria-valuemin={0}
          aria-valuemax={Math.round(duration)}
          aria-valuenow={Math.round(currentTime)}
          tabIndex={0}
          className="group/bar relative mb-1.5 flex h-5 w-full cursor-pointer select-none items-center touch-none"
        >
          {hoverFrac !== null && !isLive && duration > 0 && (
            <div
              className="pointer-events-none absolute -top-8 -translate-x-1/2 rounded bg-black/90 px-2 py-0.5 text-xs font-semibold tabular-nums text-white shadow-md"
              style={{ left: `${Math.max(6, Math.min(94, hoverFrac * 100))}%` }}
            >
              {formatDuration(hoverFrac * duration)}
            </div>
          )}

          <div className="relative h-[3px] w-full rounded-full bg-white/30 transition-transform duration-150 group-hover/bar:scale-y-150">
            <div
              className="absolute inset-y-0 left-0 rounded-full bg-white/45"
              style={{ width: `${bufferedPct}%` }}
            />
            {hoverFrac !== null && !isLive && (
              <div
                className="absolute inset-y-0 left-0 rounded-full bg-white/30"
                style={{ width: `${hoverFrac * 100}%` }}
              />
            )}
            <div
              className="absolute inset-y-0 left-0 rounded-full bg-[#FF0000]"
              style={{ width: `${playedPct}%` }}
            />
          </div>

          {!isLive && (
            <div
              className={`pointer-events-none absolute top-1/2 h-3.5 w-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#FF0000] shadow transition-transform duration-150 ${
                isScrubbing || hoverFrac !== null ? 'scale-100' : 'scale-0 group-hover/bar:scale-100'
              }`}
              style={{ left: `${playedPct}%` }}
            />
          )}
        </div>

        {/* Bottom Buttons Row */}
        <div className="flex items-center justify-between gap-2 text-white">
          <div className="flex items-center gap-1 sm:gap-2">
            <button
              type="button"
              onClick={onTogglePlay}
              aria-label={isPlaying ? 'Jeda (k)' : 'Putar (k)'}
              title={isPlaying ? 'Jeda (k)' : 'Putar (k)'}
              className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-full hover:bg-white/15"
            >
              {isPlaying ? (
                <Pause className="h-5 w-5 fill-white" />
              ) : (
                <Play className="h-5 w-5 fill-white" />
              )}
            </button>

            {hasNextVideo && (
              <button
                type="button"
                onClick={onNextVideo}
                aria-label="Video berikutnya"
                title="Video berikutnya"
                className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-full hover:bg-white/15"
              >
                <SkipForward className="h-5 w-5 fill-white" />
              </button>
            )}

            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={onToggleMute}
                aria-label={isMuted ? 'Bunyikan (m)' : 'Bisukan (m)'}
                title={isMuted ? 'Bunyikan (m)' : 'Bisukan (m)'}
                className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-full hover:bg-white/15"
              >
                <VolumeIcon className="h-5 w-5" />
              </button>
              <div className="hidden w-20 items-center sm:flex">
                <input
                  type="range"
                  min={0}
                  max={1}
                  step={0.05}
                  value={effVol}
                  onChange={(e) => onVolumeChange(Number(e.target.value))}
                  aria-label="Volume"
                  className="h-1 w-full cursor-pointer accent-white"
                />
              </div>
            </div>

            {isLive ? (
              <div className="ml-1 flex items-center gap-1.5 text-xs font-semibold">
                <span className="h-2 w-2 rounded-full bg-[#FF0000]" />
                <span>LIVE</span>
              </div>
            ) : (
              <div className="ml-1 select-none text-xs font-medium tabular-nums text-white/95 sm:text-[13px]">
                <span>{formatDuration(currentTime)}</span>
                <span className="mx-1 text-white/60">/</span>
                <span className="text-white/80">{formatDuration(duration)}</span>
              </div>
            )}
          </div>

          <div className="relative flex items-center gap-0.5 sm:gap-1">
            <div ref={settingsRef} className="relative">
              <button
                type="button"
                onClick={() =>
                  setSettingsView((prev) => (prev === 'closed' ? 'main' : 'closed'))
                }
                aria-label="Pengaturan"
                title="Pengaturan"
                className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-full hover:bg-white/15"
              >
                <Settings
                  className={`h-5 w-5 transition-transform duration-200 ${
                    settingsView !== 'closed' ? 'rotate-45' : ''
                  }`}
                />
              </button>

              {settingsView !== 'closed' && (
                <div className="vt-fade-scale absolute bottom-12 right-0 z-40 w-64 overflow-hidden rounded-xl border border-white/15 bg-[#1C1C1C]/95 py-2 text-sm text-[#F1F1F1] shadow-2xl">
                  {settingsView === 'main' && (
                    <>
                      <button
                        type="button"
                        onClick={onToggleAmbient}
                        className="flex w-full cursor-pointer items-center justify-between px-4 py-2.5 hover:bg-white/10"
                      >
                        <span className="flex items-center gap-2.5">
                          <Sparkles className="h-4 w-4 text-white/80" />
                          <span>Mode Ambient</span>
                        </span>
                        <span
                          className={`inline-flex h-5 w-9 items-center rounded-full px-0.5 transition-colors ${
                            ambientEnabled ? 'bg-[#FF0000]' : 'bg-white/25'
                          }`}
                        >
                          <span
                            className={`h-4 w-4 rounded-full bg-white transition-transform ${
                              ambientEnabled ? 'translate-x-4' : 'translate-x-0'
                            }`}
                          />
                        </span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setSettingsView('speed')}
                        className="flex w-full cursor-pointer items-center justify-between px-4 py-2.5 hover:bg-white/10"
                      >
                        <span className="flex items-center gap-2.5">
                          <Sliders className="h-4 w-4 text-white/80" />
                          <span>Kecepatan</span>
                        </span>
                        <span className="flex items-center gap-1 text-xs text-white/75">
                          {playbackRate === 1 ? 'Normal' : `${playbackRate}x`}
                          <ChevronRight className="h-4 w-4" />
                        </span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setSettingsView('quality')}
                        className="flex w-full cursor-pointer items-center justify-between px-4 py-2.5 hover:bg-white/10"
                      >
                        <span className="flex items-center gap-2.5">
                          <Settings className="h-4 w-4 text-white/80" />
                          <span>Kualitas</span>
                        </span>
                        <span className="flex items-center gap-1 text-xs text-white/75">
                          {activeQualityLabel}
                          <ChevronRight className="h-4 w-4" />
                        </span>
                      </button>
                    </>
                  )}

                  {settingsView === 'speed' && (
                    <div>
                      <button
                        type="button"
                        onClick={() => setSettingsView('main')}
                        className="flex w-full cursor-pointer items-center gap-2 border-b border-white/10 px-3 py-2 text-xs font-semibold text-white/90 hover:bg-white/10"
                      >
                        <ChevronLeft className="h-4 w-4" />
                        <span>Kecepatan pemutaran</span>
                      </button>
                      <div className="max-h-56 overflow-y-auto py-1">
                        {PLAYBACK_SPEEDS.map((spd) => (
                          <button
                            key={spd}
                            type="button"
                            onClick={() => {
                              onPlaybackRateChange(spd);
                              setSettingsView('closed');
                            }}
                            className="flex w-full cursor-pointer items-center justify-between px-4 py-2 text-left text-sm hover:bg-white/10"
                          >
                            <span>{spd === 1 ? 'Normal' : `${spd}x`}</span>
                            {playbackRate === spd && <Check className="h-4 w-4 text-white" />}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {settingsView === 'quality' && (
                    <div>
                      <button
                        type="button"
                        onClick={() => setSettingsView('main')}
                        className="flex w-full cursor-pointer items-center gap-2 border-b border-white/10 px-3 py-2 text-xs font-semibold text-white/90 hover:bg-white/10"
                      >
                        <ChevronLeft className="h-4 w-4" />
                        <span>Kualitas resolusi</span>
                      </button>
                      <div className="max-h-56 overflow-y-auto py-1">
                        {qualities.map((q) => (
                          <button
                            key={q.label}
                            type="button"
                            onClick={() => {
                              onSelectQuality(q);
                              setSettingsView('closed');
                            }}
                            className="flex w-full cursor-pointer items-center justify-between px-4 py-2 text-left text-sm hover:bg-white/10"
                          >
                            <span>{q.label}</span>
                            {activeQualityLabel === q.label && (
                              <Check className="h-4 w-4 text-white" />
                            )}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={onToggleMiniplayer}
              aria-label="Miniplayer (i)"
              title="Miniplayer (i)"
              className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-full hover:bg-white/15"
            >
              <PictureInPicture2 className="h-5 w-5" />
            </button>

            <button
              type="button"
              onClick={onToggleTheater}
              aria-label="Mode Teater (t)"
              title="Mode Teater (t)"
              className="hidden h-9 w-9 cursor-pointer items-center justify-center rounded-full hover:bg-white/15 lg:flex"
            >
              <RectangleHorizontal
                className={`h-5 w-5 ${isTheater ? 'text-[#FF0000]' : 'text-white'}`}
              />
            </button>

            <button
              type="button"
              onClick={onToggleFullscreen}
              aria-label={isFullscreen ? 'Keluar layar penuh (f)' : 'Layar penuh (f)'}
              title={isFullscreen ? 'Keluar layar penuh (f)' : 'Layar penuh (f)'}
              className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-full hover:bg-white/15"
            >
              {isFullscreen ? <Minimize className="h-5 w-5" /> : <Maximize className="h-5 w-5" />}
            </button>
          </div>
        </div>
      </div>
    );
  }
);

PlayerControls.displayName = 'PlayerControls';

export const MiniProgressBar: React.FC<{
  videoRef: React.RefObject<HTMLVideoElement | null>;
  isLive: boolean;
}> = React.memo(({ videoRef, isLive }) => {
  const [pct, setPct] = useState(0);

  useEffect(() => {
    if (isLive) {
      setPct(100);
      return;
    }
    const el = videoRef.current;
    if (!el) return;
    const sync = () => {
      if (Number.isFinite(el.duration) && el.duration > 0) {
        setPct(Math.min(100, (el.currentTime / el.duration) * 100));
      }
    };
    sync();
    el.addEventListener('timeupdate', sync);
    return () => el.removeEventListener('timeupdate', sync);
  }, [videoRef, isLive]);

  return (
    <div className="h-[2.5px] w-full bg-white/20">
      <div className="h-full bg-[#FF0000]" style={{ width: `${pct}%` }} />
    </div>
  );
});

MiniProgressBar.displayName = 'MiniProgressBar';
