import React, { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import type { VideoCardItem, VideoDetailItem } from '../types/api';

interface PlayerContextValue {
  activeVideo: VideoCardItem | null;
  isMiniplayer: boolean;
  isTheater: boolean;
  nextVideo: VideoCardItem | null;
  externalPauseSignal: number;
  loadVideoIntoPlayer: (video: VideoCardItem | VideoDetailItem, mini?: boolean) => void;
  setNextVideo: (video: VideoCardItem | null) => void;
  setMiniplayer: (mini: boolean) => void;
  toggleTheater: () => void;
  closePlayer: () => void;
  requestPause: () => void;
}

const PlayerContext = createContext<PlayerContextValue | null>(null);

export const PlayerProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [activeVideo, setActiveVideo] = useState<VideoCardItem | null>(null);
  const [isMiniplayer, setIsMiniplayer] = useState(false);
  const [isTheater, setIsTheater] = useState(false);
  const [nextVideo, setNextVideo] = useState<VideoCardItem | null>(null);
  const [externalPauseSignal, setExternalPauseSignal] = useState(0);

  const activeIdRef = useRef<number | null>(null);

  const loadVideoIntoPlayer = useCallback(
    (video: VideoCardItem | VideoDetailItem, mini = false) => {
      // Only replace activeVideo metadata cleanly; GlobalVideoPlayer keys media reload strictly on video.id
      activeIdRef.current = video.id;
      setActiveVideo(video);
      setIsMiniplayer(mini);
      if ('recommendations' in video && Array.isArray(video.recommendations)) {
        setNextVideo(video.recommendations[0] || null);
      }
    },
    []
  );

  const setMiniplayer = useCallback((mini: boolean) => {
    setIsMiniplayer(mini);
  }, []);

  const toggleTheater = useCallback(() => {
    setIsTheater((prev) => !prev);
  }, []);

  const closePlayer = useCallback(() => {
    activeIdRef.current = null;
    setActiveVideo(null);
    setIsMiniplayer(false);
    setNextVideo(null);
  }, []);

  const requestPause = useCallback(() => {
    setExternalPauseSignal((n) => n + 1);
  }, []);

  const value = useMemo(
    () => ({
      activeVideo,
      isMiniplayer,
      isTheater,
      nextVideo,
      externalPauseSignal,
      loadVideoIntoPlayer,
      setNextVideo,
      setMiniplayer,
      toggleTheater,
      closePlayer,
      requestPause,
    }),
    [
      activeVideo,
      isMiniplayer,
      isTheater,
      nextVideo,
      externalPauseSignal,
      loadVideoIntoPlayer,
      setMiniplayer,
      toggleTheater,
      closePlayer,
      requestPause,
    ]
  );

  return <PlayerContext.Provider value={value}>{children}</PlayerContext.Provider>;
};

export function usePlayer(): PlayerContextValue {
  const ctx = useContext(PlayerContext);
  if (!ctx) throw new Error('usePlayer harus digunakan di dalam PlayerProvider');
  return ctx;
}
