import { useEffect, useRef, useState, type ReactNode } from 'react';

/** Horizontally scrolling, snap-aligned row with previous/next arrow buttons. */
export function Carousel({ children, label }: { children: ReactNode; label: string }) {
  const track = useRef<HTMLDivElement>(null);
  const [canPrev, setCanPrev] = useState(false);
  const [canNext, setCanNext] = useState(false);

  const update = () => {
    const el = track.current;
    if (!el) return;
    setCanPrev(el.scrollLeft > 4);
    setCanNext(el.scrollLeft + el.clientWidth < el.scrollWidth - 4);
  };

  useEffect(() => {
    update();
    const el = track.current;
    if (!el) return;
    const observer = new ResizeObserver(update);
    observer.observe(el);
    return () => observer.disconnect();
  }, [children]);

  const scroll = (dir: 1 | -1) => {
    const el = track.current;
    if (el) el.scrollBy({ left: dir * el.clientWidth * 0.85, behavior: 'smooth' });
  };

  return (
    <div className="carousel" aria-label={label} role="region">
      <div className="carousel-track" ref={track} onScroll={update}>
        {children}
      </div>
      {canPrev && (
        <button className="carousel-btn prev" aria-label="Previous" onClick={() => scroll(-1)}>←</button>
      )}
      {canNext && (
        <button className="carousel-btn next" aria-label="Next" onClick={() => scroll(1)}>→</button>
      )}
    </div>
  );
}
