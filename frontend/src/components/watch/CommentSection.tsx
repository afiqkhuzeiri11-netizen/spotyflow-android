import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ArrowDownWideNarrow,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  MessageSquare,
  Pin,
  Send,
  ThumbsUp,
  X,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { extractApiError, videosApi, youtubeApi } from '../../services/api';
import type { CommentItem, CommentReplyItem, CommentSort } from '../../types/api';
import { formatCompactNumber, formatRelativeTime } from '../../utils/format';

interface CommentSectionProps {
  videoId: number;
  ytId?: string | null;
  initialTotal?: number;
  onNotify: (msg: string) => void;
  /** When true, renders ONLY as a slide-up modal drawer (reused by ShortsPage) */
  drawerOnly?: boolean;
  drawerOpen?: boolean;
  onCloseDrawer?: () => void;
  onCountChange?: (total: number) => void;
}

const AvatarBadge: React.FC<{
  src: string | null;
  name: string;
  size?: 'sm' | 'md';
}> = ({ src, name, size = 'md' }) => {
  const cls = size === 'sm' ? 'h-6 w-6 text-[11px]' : 'h-9 w-9 text-xs';
  if (src) {
    return (
      <img
        src={src}
        alt={name}
        loading="lazy"
        decoding="async"
        className={`${cls} shrink-0 rounded-full object-cover`}
        style={{ backgroundColor: 'var(--vt-chip)' }}
      />
    );
  }
  return (
    <div
      className={`${cls} flex shrink-0 items-center justify-center rounded-full bg-[#3EA6FF] font-bold text-[#0F0F0F]`}
    >
      {(name || 'U').slice(0, 1).toUpperCase()}
    </div>
  );
};

export const CommentSection: React.FC<CommentSectionProps> = React.memo(
  ({
    videoId,
    ytId = null,
    initialTotal = 0,
    onNotify,
    drawerOnly = false,
    drawerOpen = false,
    onCloseDrawer,
    onCountChange,
  }) => {
    const { user, openAuthModal } = useAuth();

    const [comments, setComments] = useState<CommentItem[]>([]);
    const [totalCount, setTotalCount] = useState(initialTotal);
    const [sort, setSort] = useState<CommentSort>('top');
    const [sortMenuOpen, setSortMenuOpen] = useState(false);
    const [loading, setLoading] = useState(true);

    const [inputText, setInputText] = useState('');
    const [inputFocused, setInputFocused] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    const [replyingToId, setReplyingToId] = useState<number | null>(null);
    const [replyText, setReplyText] = useState('');
    const [submittingReply, setSubmittingReply] = useState(false);
    const [expandedReplies, setExpandedReplies] = useState<Record<number, boolean>>({});

    // Mobile Bottom Sheet Drawer state (for WatchPage mobile view)
    const [mobileSheetOpen, setMobileSheetOpen] = useState(false);
    const touchStartYRef = useRef<number | null>(null);
    const [dragOffsetY, setDragOffsetY] = useState(0);

    const isSheetActive = drawerOnly ? drawerOpen : mobileSheetOpen;

    const closeSheet = useCallback(() => {
      setDragOffsetY(0);
      if (drawerOnly) {
        onCloseDrawer?.();
      } else {
        setMobileSheetOpen(false);
      }
    }, [drawerOnly, onCloseDrawer]);

    const fetchComments = useCallback(async () => {
      if (!videoId) return;
      if (drawerOnly && !drawerOpen) return;
      setLoading(true);
      try {
        const res = await videosApi.comments(videoId, sort);
        setComments(res.items);
        setTotalCount(res.total);
        onCountChange?.(res.total);
      } catch (err) {
        onNotify(extractApiError(err, 'Gagal memuat komentar.'));
      } finally {
        setLoading(false);
      }
    }, [videoId, sort, drawerOnly, drawerOpen, onNotify, onCountChange]);

    useEffect(() => {
      fetchComments();
    }, [fetchComments]);

    // Lock body scroll when bottom sheet is open on mobile
    useEffect(() => {
      if (!isSheetActive) return;
      const prev = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      return () => {
        document.body.style.overflow = prev;
      };
    }, [isSheetActive]);

    const handleSubmitComment = async (e: React.FormEvent) => {
      e.preventDefault();
      if (!user) {
        openAuthModal('login');
        onNotify('Silakan login untuk menulis komentar.');
        return;
      }
      const trimmed = inputText.trim();
      if (!trimmed || submitting) return;

      setSubmitting(true);
      try {
        if (ytId) {
          const ytRes = await youtubeApi.comment(ytId, trimmed);
          const localComment: CommentItem = {
            id: Date.now(),
            video_id: videoId,
            content: trimmed,
            likes_count: 0,
            liked_by_me: false,
            is_pinned: false,
            is_channel_owner: false,
            created_at: new Date().toISOString(),
            author: {
              id: user.id,
              display_name: user.display_name,
              handle: user.handle,
              avatar_url: user.avatar_url,
              verified: user.verified,
            },
            replies: [],
          };
          setComments((prev) => [localComment, ...prev]);
          setTotalCount((c) => {
            const next = c + 1;
            onCountChange?.(next);
            return next;
          });
          setInputText('');
          setInputFocused(false);
          onNotify(
            ytRes.synced
              ? 'Komentar berhasil dikirim ke YouTube!'
              : 'Komentar disimpan secara lokal.'
          );
          return;
        }
        const created = await videosApi.addComment(videoId, trimmed, null);
        setComments((prev) => [created, ...prev]);
        setTotalCount((c) => {
          const next = c + 1;
          onCountChange?.(next);
          return next;
        });
        setInputText('');
        setInputFocused(false);
        onNotify('Komentar ditambahkan.');
      } catch (err) {
        onNotify(extractApiError(err, 'Gagal mengirim komentar.'));
      } finally {
        setSubmitting(false);
      }
    };

    const handleSubmitReply = async (parentId: number) => {
      if (!user) {
        openAuthModal('login');
        onNotify('Silakan login untuk membalas komentar.');
        return;
      }
      const trimmed = replyText.trim();
      if (!trimmed || submittingReply) return;

      setSubmittingReply(true);
      try {
        const created = await videosApi.addComment(videoId, trimmed, parentId);
        const replyItem: CommentReplyItem = {
          id: created.id,
          video_id: created.video_id,
          parent_id: parentId,
          content: created.content,
          likes_count: created.likes_count,
          liked_by_me: created.liked_by_me,
          is_channel_owner: created.is_channel_owner,
          created_at: created.created_at,
          author: created.author,
        };
        setComments((prev) =>
          prev.map((item) =>
            item.id === parentId
              ? { ...item, replies: [...item.replies, replyItem] }
              : item
          )
        );
        setExpandedReplies((prev) => ({ ...prev, [parentId]: true }));
        setTotalCount((c) => {
          const next = c + 1;
          onCountChange?.(next);
          return next;
        });
        setReplyText('');
        setReplyingToId(null);
        onNotify('Balasan ditambahkan.');
      } catch (err) {
        onNotify(extractApiError(err, 'Gagal mengirim balasan.'));
      } finally {
        setSubmittingReply(false);
      }
    };

    const handleLikeComment = async (commentId: number, parentId?: number) => {
      if (!user) {
        openAuthModal('login');
        onNotify('Silakan login untuk menyukai komentar.');
        return;
      }

      // Optimistic toggle
      setComments((prev) =>
        prev.map((item) => {
          if (!parentId && item.id === commentId) {
            const nextLiked = !item.liked_by_me;
            return {
              ...item,
              liked_by_me: nextLiked,
              likes_count: Math.max(0, item.likes_count + (nextLiked ? 1 : -1)),
            };
          }
          if (parentId && item.id === parentId) {
            return {
              ...item,
              replies: item.replies.map((r) => {
                if (r.id !== commentId) return r;
                const nextLiked = !r.liked_by_me;
                return {
                  ...r,
                  liked_by_me: nextLiked,
                  likes_count: Math.max(0, r.likes_count + (nextLiked ? 1 : -1)),
                };
              }),
            };
          }
          return item;
        })
      );

      try {
        const res = await videosApi.likeComment(commentId);
        setComments((prev) =>
          prev.map((item) => {
            if (!parentId && item.id === commentId) {
              return { ...item, liked_by_me: res.liked_by_me, likes_count: res.likes_count };
            }
            if (parentId && item.id === parentId) {
              return {
                ...item,
                replies: item.replies.map((r) =>
                  r.id === commentId
                    ? { ...r, liked_by_me: res.liked_by_me, likes_count: res.likes_count }
                    : r
                ),
              };
            }
            return item;
          })
        );
      } catch (err) {
        onNotify(extractApiError(err, 'Gagal menyukai komentar.'));
        fetchComments();
      }
    };

    const handleSheetTouchStart = (e: React.TouchEvent) => {
      touchStartYRef.current = e.touches[0].clientY;
    };

    const handleSheetTouchMove = (e: React.TouchEvent) => {
      if (touchStartYRef.current === null) return;
      const delta = e.touches[0].clientY - touchStartYRef.current;
      if (delta > 0) {
        setDragOffsetY(delta);
      }
    };

    const handleSheetTouchEnd = () => {
      if (dragOffsetY > 90) {
        closeSheet();
      } else {
        setDragOffsetY(0);
      }
      touchStartYRef.current = null;
    };

    const renderCommentBody = () => (
      <div className="flex flex-col">
        {/* Header: Count + Sort Dropdown */}
        <div className="mb-4 flex items-center gap-6">
          <h2 className="text-base font-bold md:text-lg" style={{ color: 'var(--vt-text)' }}>
            {formatCompactNumber(totalCount)} Komentar
          </h2>

          <div className="relative">
            <button
              type="button"
              onClick={() => setSortMenuOpen((o) => !o)}
              className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg px-2 py-1 text-sm font-semibold transition-colors"
              style={{ color: 'var(--vt-text)' }}
            >
              <ArrowDownWideNarrow className="h-4 w-4" />
              <span>Urutkan</span>
            </button>

            {sortMenuOpen && (
              <div
                className="vt-fade-scale absolute left-0 top-full z-30 mt-1 w-44 overflow-hidden rounded-xl border py-1.5 shadow-xl"
                style={{
                  backgroundColor: 'var(--vt-surface)',
                  borderColor: 'var(--vt-border)',
                }}
              >
                {[
                  { id: 'top' as const, label: 'Komentar teratas' },
                  { id: 'newest' as const, label: 'Terbaru dulu' },
                ].map((opt) => (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => {
                      setSort(opt.id);
                      setSortMenuOpen(false);
                    }}
                    className="flex w-full cursor-pointer items-center px-4 py-2 text-left text-sm transition-colors"
                    style={{
                      backgroundColor: sort === opt.id ? 'var(--vt-chip)' : 'transparent',
                      color: 'var(--vt-text)',
                    }}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Add Comment Form */}
        <form onSubmit={handleSubmitComment} className="mb-6 flex items-start gap-3">
          <AvatarBadge
            src={user?.avatar_url ?? null}
            name={user?.display_name ?? 'T'}
          />
          <div className="min-w-0 flex-1">
            <input
              type="text"
              value={inputText}
              onFocus={() => {
                if (!user) {
                  openAuthModal('login');
                  onNotify('Silakan login untuk menulis komentar.');
                  return;
                }
                setInputFocused(true);
              }}
              onChange={(e) => setInputText(e.target.value)}
              placeholder={user ? 'Tambahkan komentar...' : 'Login untuk menambahkan komentar...'}
              className="w-full border-b bg-transparent pb-1.5 text-sm outline-none transition-colors focus:border-b-2"
              style={{
                borderColor: inputFocused ? 'var(--vt-text)' : 'var(--vt-border)',
                color: 'var(--vt-text)',
              }}
            />

            {inputFocused && (
              <div className="mt-2 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setInputFocused(false);
                    setInputText('');
                  }}
                  className="h-9 cursor-pointer rounded-full px-4 text-sm font-semibold transition-colors"
                  style={{ color: 'var(--vt-text)' }}
                >
                  Batal
                </button>
                <button
                  type="submit"
                  disabled={!inputText.trim() || submitting}
                  className="inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-full bg-[#3EA6FF] px-4 text-sm font-semibold text-[#0F0F0F] transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <Send className="h-3.5 w-3.5" />
                  <span>{submitting ? 'Mengirim...' : 'Komentar'}</span>
                </button>
              </div>
            )}
          </div>
        </form>

        {/* Comments List */}
        {loading ? (
          <div className="space-y-4">
            {Array.from({ length: 3 }).map((_, idx) => (
              <div key={idx} className="flex gap-3">
                <div className="vt-shimmer h-9 w-9 shrink-0 rounded-full" />
                <div className="flex-1 space-y-2">
                  <div className="vt-shimmer h-3 w-36 rounded" />
                  <div className="vt-shimmer h-3.5 w-4/5 rounded" />
                </div>
              </div>
            ))}
          </div>
        ) : comments.length === 0 ? (
          <div
            className="rounded-xl py-8 text-center text-sm"
            style={{ color: 'var(--vt-text-secondary)' }}
          >
            Belum ada komentar. Jadilah yang pertama berkomentar!
          </div>
        ) : (
          <div className="space-y-5">
            {comments.map((comment) => {
              const repliesOpen = Boolean(expandedReplies[comment.id]);
              return (
                <div key={comment.id} className="flex items-start gap-3">
                  <AvatarBadge
                    src={comment.author.avatar_url}
                    name={comment.author.display_name}
                  />
                  <div className="min-w-0 flex-1">
                    {comment.is_pinned && (
                      <div
                        className="mb-1 flex items-center gap-1 text-[11px] font-medium"
                        style={{ color: 'var(--vt-text-secondary)' }}
                      >
                        <Pin className="h-3 w-3" />
                        <span>Disematkan oleh kreator</span>
                      </div>
                    )}

                    <div className="flex flex-wrap items-center gap-1.5">
                      <span
                        className={`inline-flex items-center gap-1 text-[13px] font-semibold ${
                          comment.is_channel_owner
                            ? 'rounded-full px-2 py-0.5'
                            : ''
                        }`}
                        style={
                          comment.is_channel_owner
                            ? {
                                backgroundColor: 'var(--vt-chip)',
                                color: 'var(--vt-text)',
                              }
                            : { color: 'var(--vt-text)' }
                        }
                      >
                        <span>{comment.author.handle || comment.author.display_name}</span>
                        {comment.author.verified && <CheckCircle2 className="h-3 w-3" />}
                      </span>
                      <span className="text-xs" style={{ color: 'var(--vt-text-secondary)' }}>
                        {formatRelativeTime(comment.created_at)}
                      </span>
                    </div>

                    <p
                      className="mt-1 whitespace-pre-line text-sm leading-relaxed"
                      style={{ color: 'var(--vt-text)' }}
                    >
                      {comment.content}
                    </p>

                    {/* Comment Action Row */}
                    <div className="mt-1.5 flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => handleLikeComment(comment.id)}
                        className="inline-flex cursor-pointer items-center gap-1.5 rounded-full px-2 py-1 text-xs font-medium transition-colors hover:bg-white/10"
                        style={{ color: 'var(--vt-text)' }}
                      >
                        <ThumbsUp
                          className={`h-3.5 w-3.5 ${
                            comment.liked_by_me ? 'fill-current text-[#3EA6FF]' : ''
                          }`}
                        />
                        {comment.likes_count > 0 && (
                          <span>{formatCompactNumber(comment.likes_count)}</span>
                        )}
                      </button>

                      <button
                        type="button"
                        onClick={() => {
                          if (!user) {
                            openAuthModal('login');
                            onNotify('Silakan login untuk membalas komentar.');
                            return;
                          }
                          setReplyingToId((curr) => (curr === comment.id ? null : comment.id));
                          setReplyText('');
                        }}
                        className="cursor-pointer rounded-full px-2.5 py-1 text-xs font-semibold transition-colors hover:bg-white/10"
                        style={{ color: 'var(--vt-text)' }}
                      >
                        Balas
                      </button>
                    </div>

                    {/* Inline Reply Form */}
                    {replyingToId === comment.id && (
                      <div className="mt-2.5 flex items-start gap-2.5">
                        <AvatarBadge
                          src={user?.avatar_url ?? null}
                          name={user?.display_name ?? 'U'}
                          size="sm"
                        />
                        <div className="min-w-0 flex-1">
                          <input
                            type="text"
                            autoFocus
                            value={replyText}
                            onChange={(e) => setReplyText(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' && !e.shiftKey) {
                                e.preventDefault();
                                handleSubmitReply(comment.id);
                              }
                            }}
                            placeholder={`Balas ${comment.author.handle}...`}
                            className="w-full border-b bg-transparent pb-1 text-xs outline-none focus:border-b-2"
                            style={{
                              borderColor: 'var(--vt-text)',
                              color: 'var(--vt-text)',
                            }}
                          />
                          <div className="mt-2 flex items-center justify-end gap-2">
                            <button
                              type="button"
                              onClick={() => {
                                setReplyingToId(null);
                                setReplyText('');
                              }}
                              className="h-8 cursor-pointer rounded-full px-3 text-xs font-semibold"
                              style={{ color: 'var(--vt-text)' }}
                            >
                              Batal
                            </button>
                            <button
                              type="button"
                              disabled={!replyText.trim() || submittingReply}
                              onClick={() => handleSubmitReply(comment.id)}
                              className="h-8 cursor-pointer rounded-full bg-[#3EA6FF] px-3.5 text-xs font-semibold text-[#0F0F0F] disabled:opacity-40"
                            >
                              {submittingReply ? 'Mengirim...' : 'Balas'}
                            </button>
                          </div>
                        </div>
                      </div>
                    )}

                    {/* Nested Replies Toggle & Thread */}
                    {comment.replies.length > 0 && (
                      <div className="mt-2">
                        <button
                          type="button"
                          onClick={() =>
                            setExpandedReplies((prev) => ({
                              ...prev,
                              [comment.id]: !prev[comment.id],
                            }))
                          }
                          className="inline-flex cursor-pointer items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold text-[#3EA6FF] transition-colors hover:bg-[#3EA6FF]/15"
                        >
                          {repliesOpen ? (
                            <ChevronUp className="h-4 w-4" />
                          ) : (
                            <ChevronDown className="h-4 w-4" />
                          )}
                          <span>
                            {repliesOpen
                              ? `Sembunyikan ${comment.replies.length} balasan`
                              : `Lihat ${comment.replies.length} balasan`}
                          </span>
                        </button>

                        {repliesOpen && (
                          <div className="mt-2.5 space-y-3 pl-1">
                            {comment.replies.map((reply) => (
                              <div key={reply.id} className="flex items-start gap-2.5">
                                <AvatarBadge
                                  src={reply.author.avatar_url}
                                  name={reply.author.display_name}
                                  size="sm"
                                />
                                <div className="min-w-0 flex-1">
                                  <div className="flex flex-wrap items-center gap-1.5">
                                    <span
                                      className={`inline-flex items-center gap-1 text-xs font-semibold ${
                                        reply.is_channel_owner ? 'rounded-full px-2 py-0.5' : ''
                                      }`}
                                      style={
                                        reply.is_channel_owner
                                          ? {
                                              backgroundColor: 'var(--vt-chip)',
                                              color: 'var(--vt-text)',
                                            }
                                          : { color: 'var(--vt-text)' }
                                      }
                                    >
                                      <span>{reply.author.handle || reply.author.display_name}</span>
                                      {reply.author.verified && (
                                        <CheckCircle2 className="h-3 w-3" />
                                      )}
                                    </span>
                                    <span
                                      className="text-[11px]"
                                      style={{ color: 'var(--vt-text-secondary)' }}
                                    >
                                      {formatRelativeTime(reply.created_at)}
                                    </span>
                                  </div>

                                  <p
                                    className="mt-0.5 whitespace-pre-line text-[13px] leading-relaxed"
                                    style={{ color: 'var(--vt-text)' }}
                                  >
                                    {reply.content}
                                  </p>

                                  <button
                                    type="button"
                                    onClick={() => handleLikeComment(reply.id, comment.id)}
                                    className="mt-1 inline-flex cursor-pointer items-center gap-1 rounded-full px-2 py-0.5 text-xs transition-colors hover:bg-white/10"
                                    style={{ color: 'var(--vt-text)' }}
                                  >
                                    <ThumbsUp
                                      className={`h-3 w-3 ${
                                        reply.liked_by_me ? 'fill-current text-[#3EA6FF]' : ''
                                      }`}
                                    />
                                    {reply.likes_count > 0 && (
                                      <span>{formatCompactNumber(reply.likes_count)}</span>
                                    )}
                                  </button>
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    );

    const previewComment = comments[0];

    return (
      <>
        {/* WatchPage Inline Desktop Comments + Mobile Preview Card */}
        {!drawerOnly && (
          <section className="mt-4 px-3 pb-4 md:mt-6 md:px-0">
            {/* Mobile Compact Comment Preview Card -> Opens Swipeable Bottom Sheet */}
            <div className="md:hidden">
              <button
                type="button"
                onClick={() => setMobileSheetOpen(true)}
                className="w-full cursor-pointer rounded-xl p-3 text-left transition-colors"
                style={{ backgroundColor: 'var(--vt-chip)', color: 'var(--vt-text)' }}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-sm font-semibold">
                    <span>Komentar</span>
                    <span
                      className="text-xs font-normal"
                      style={{ color: 'var(--vt-text-secondary)' }}
                    >
                      {formatCompactNumber(totalCount)}
                    </span>
                  </div>
                  <ChevronDown className="h-4 w-4" style={{ color: 'var(--vt-text-secondary)' }} />
                </div>

                {previewComment ? (
                  <div className="mt-2 flex items-center gap-2">
                    <AvatarBadge
                      src={previewComment.author.avatar_url}
                      name={previewComment.author.display_name}
                      size="sm"
                    />
                    <p
                      className="line-clamp-2 text-xs leading-snug"
                      style={{ color: 'var(--vt-text)' }}
                    >
                      {previewComment.content}
                    </p>
                  </div>
                ) : (
                  <div
                    className="mt-2 flex items-center gap-2 text-xs"
                    style={{ color: 'var(--vt-text-secondary)' }}
                  >
                    <MessageSquare className="h-4 w-4" />
                    <span>Ketuk untuk melihat atau menambahkan komentar</span>
                  </div>
                )}
              </button>
            </div>

            {/* Desktop Full Inline Comments */}
            <div className="hidden md:block">{renderCommentBody()}</div>
          </section>
        )}

        {/* GPU-Only Slide-Up Bottom Sheet Drawer (Mobile WatchPage + Shorts Comments) */}
        <div
          className={`fixed inset-0 z-50 transition-opacity duration-200 ${
            isSheetActive
              ? 'pointer-events-auto opacity-100'
              : 'pointer-events-none opacity-0'
          } ${drawerOnly ? '' : 'md:hidden'}`}
        >
          {/* Backdrop */}
          <div
            onClick={closeSheet}
            className="absolute inset-0 bg-black/65"
          />

          {/* Slide-Up Sheet */}
          <div
            onTouchStart={handleSheetTouchStart}
            onTouchMove={handleSheetTouchMove}
            onTouchEnd={handleSheetTouchEnd}
            className={`vt-sheet absolute inset-x-0 bottom-0 flex max-h-[82dvh] flex-col rounded-t-2xl border-t shadow-2xl md:left-auto md:right-6 md:bottom-6 md:max-h-[78dvh] md:w-[420px] md:rounded-2xl md:border ${
              isSheetActive ? 'vt-sheet-open' : ''
            }`}
            style={{
              backgroundColor: 'var(--vt-surface)',
              borderColor: 'var(--vt-border)',
              transform:
                isSheetActive && dragOffsetY > 0
                  ? `translate3d(0, ${dragOffsetY}px, 0)`
                  : undefined,
            }}
          >
            {/* Swipe Handle + Header */}
            <div
              className="flex shrink-0 flex-col border-b px-4 pb-3 pt-2.5"
              style={{ borderColor: 'var(--vt-border)' }}
            >
              <div className="mx-auto mb-2.5 h-1 w-10 rounded-full bg-white/25 md:hidden" />
              <div className="flex items-center justify-between">
                <h3 className="text-base font-bold" style={{ color: 'var(--vt-text)' }}>
                  Komentar ({formatCompactNumber(totalCount)})
                </h3>
                <button
                  type="button"
                  onClick={closeSheet}
                  aria-label="Tutup komentar"
                  className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full hover:bg-white/10"
                  style={{ color: 'var(--vt-text)' }}
                >
                  <X className="h-5 w-5" />
                </button>
              </div>
            </div>

            {/* Scrollable Body */}
            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
              {renderCommentBody()}
            </div>
          </div>
        </div>
      </>
    );
  }
);

CommentSection.displayName = 'CommentSection';
