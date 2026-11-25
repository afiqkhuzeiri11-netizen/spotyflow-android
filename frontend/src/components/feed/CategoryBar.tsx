import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { CATEGORIES, type CategoryId } from '../../types/api';

interface CategoryBarProps {
  activeCategory: CategoryId;
  onSelectCategory: (id: CategoryId) => void;
}

export const CategoryBar: React.FC<CategoryBarProps> = React.memo(
  ({ activeCategory, onSelectCategory }) => {
    const railRef = useRef<HTMLDivElement | null>(null);
    const [canLeft, setCanLeft] = useState(false);
    const [canRight, setCanRight] = useState(false);

    const syncScrollArrows = useCallback(() => {
      const el = railRef.current;
      if (!el) return;
      setCanLeft(el.scrollLeft > 4);
      setCanRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 4);
    }, []);

    useEffect(() => {
      syncScrollArrows();
      window.addEventListener('resize', syncScrollArrows, { passive: true });
      return () => window.removeEventListener('resize', syncScrollArrows);
    }, [syncScrollArrows]);

    const scrollRail = useCallback((delta: number) => {
      railRef.current?.scrollBy({ left: delta, behavior: 'smooth' });
    }, []);

    return (
      <div
        className="sticky top-14 z-30 flex h-14 items-center px-3 sm:px-6"
        style={{ backgroundColor: 'var(--vt-bg)' }}
      >
        <div className="relative flex w-full items-center">
          {canLeft && (
            <div
              className="pointer-events-none absolute bottom-0 left-0 top-0 z-10 hidden items-center pr-6 md:flex"
              style={{
                background: 'linear-gradient(90deg, var(--vt-bg) 55%, transparent 100%)',
              }}
            >
              <button
                type="button"
                onClick={() => scrollRail(-240)}
                aria-label="Geser kiri"
                className="pointer-events-auto flex h-9 w-9 cursor-pointer items-center justify-center rounded-full"
                style={{ backgroundColor: 'var(--vt-bg)', color: 'var(--vt-text-primary)' }}
              >
                <ChevronLeft className="h-5 w-5" />
              </button>
            </div>
          )}

          <div
            ref={railRef}
            onScroll={syncScrollArrows}
            className="vt-no-scrollbar flex w-full items-center gap-2.5 overflow-x-auto py-2"
            role="tablist"
            aria-label="Filter kategori"
          >
            {CATEGORIES.map((cat) => {
              const active = activeCategory === cat.id;
              return (
                <button
                  key={cat.id}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => onSelectCategory(cat.id)}
                  className="shrink-0 cursor-pointer rounded-lg px-3 py-1.5 text-sm font-medium transition-opacity hover:opacity-90"
                  style={{
                    backgroundColor: active ? 'var(--vt-chip-active-bg)' : 'var(--vt-chip)',
                    color: active ? 'var(--vt-chip-active-text)' : 'var(--vt-text-primary)',
                  }}
                >
                  {cat.label}
                </button>
              );
            })}
          </div>

          {canRight && (
            <div
              className="pointer-events-none absolute bottom-0 right-0 top-0 z-10 hidden items-center pl-6 md:flex"
              style={{
                background: 'linear-gradient(270deg, var(--vt-bg) 55%, transparent 100%)',
              }}
            >
              <button
                type="button"
                onClick={() => scrollRail(240)}
                aria-label="Geser kanan"
                className="pointer-events-auto flex h-9 w-9 cursor-pointer items-center justify-center rounded-full"
                style={{ backgroundColor: 'var(--vt-bg)', color: 'var(--vt-text-primary)' }}
              >
                <ChevronRight className="h-5 w-5" />
              </button>
            </div>
          )}
        </div>
      </div>
    );
  }
);

CategoryBar.displayName = 'CategoryBar';
