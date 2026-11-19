import { useId } from 'react';

/** Colour themes for generated landscapes: sky top/bottom, sun, three mountain layers, foreground. */
const PALETTES = [
  { sky: ['#7cc0ee', '#e6f4fb'], sun: '#fff7d6', far: '#8fa9c4', mid: '#4f7393', near: '#2b4a63', ground: '#9a8a3a' }, // alpine day
  { sky: ['#f59e5b', '#fde3c2'], sun: '#fff1cf', far: '#cf8a76', mid: '#9a5b4c', near: '#5a3536', ground: '#6e5124' }, // sunrise
  { sky: ['#5fb7cf', '#d9f1f3'], sun: '#ffffff', far: '#88aea6', mid: '#3f7466', near: '#1e4a3d', ground: '#3f6e35' }, // lake valley
  { sky: ['#a99bd8', '#f0eafa'], sun: '#fde7f0', far: '#a497c0', mid: '#6b5d92', near: '#3b3360', ground: '#5b4a6e' }, // dusk
  { sky: ['#f6c453', '#fff3cf'], sun: '#fffbe8', far: '#c9a77a', mid: '#8b6b45', near: '#4f3b26', ground: '#a07a2c' }, // golden hour
  { sky: ['#9dc7d9', '#eef6f8'], sun: '#ffffff', far: '#b4c4cf', mid: '#7b8f9c', near: '#465866', ground: '#7d8b6a' }, // high pass
] as const;

function hash(text: string): number {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** Small deterministic PRNG so a given area always renders the same scene. */
function rng(seed: number) {
  let s = seed || 1;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

function ridge(rand: () => number, base: number, amplitude: number, peaks: number): string {
  const points: string[] = ['0,260', `0,${base}`];
  const step = 400 / peaks;
  for (let i = 0; i <= peaks; i++) {
    const x = i * step;
    const peakX = x + step * (0.3 + rand() * 0.4);
    const peakY = base - amplitude * (0.45 + rand() * 0.55);
    const valleyY = base - amplitude * rand() * 0.25;
    points.push(`${peakX.toFixed(1)},${peakY.toFixed(1)}`, `${(x + step).toFixed(1)},${valleyY.toFixed(1)}`);
  }
  points.push('400,260');
  return points.join(' ');
}

/**
 * Generated mountain landscape used as destination artwork. Deterministic per `seed`
 * (an area code), so each destination keeps its own look. Pure inline SVG: no external
 * images, compatible with the strict CSP.
 */
export function AreaArt({ seed, className = '' }: { seed: string; className?: string }) {
  const id = useId().replace(/:/g, '');
  const h = hash(seed);
  const p = PALETTES[h % PALETTES.length];
  const rand = rng(h);
  const sunX = 60 + rand() * 280;
  const sunY = 40 + rand() * 40;
  const far = ridge(rand, 170, 120, 4);
  const mid = ridge(rand, 200, 80, 5);
  const near = ridge(rand, 235, 50, 6);
  const hasLake = rand() > 0.55;

  return (
    <svg className={`area-art ${className}`} viewBox="0 0 400 260" preserveAspectRatio="xMidYMid slice"
      role="img" aria-hidden focusable="false">
      <defs>
        <linearGradient id={`sky${id}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={p.sky[0]} />
          <stop offset="1" stopColor={p.sky[1]} />
        </linearGradient>
        <linearGradient id={`snow${id}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="0.35" stopColor="#f4f7fa" />
          <stop offset="0.6" stopColor={p.far} />
        </linearGradient>
      </defs>
      <rect width="400" height="260" fill={`url(#sky${id})`} />
      <circle cx={sunX} cy={sunY} r="26" fill={p.sun} opacity="0.9" />
      <polygon points={far} fill={`url(#snow${id})`} />
      <polygon points={mid} fill={p.mid} opacity="0.95" />
      <polygon points={near} fill={p.near} />
      {hasLake ? (
        <>
          <rect y="238" width="400" height="22" fill={p.sky[0]} opacity="0.85" />
          <rect y="244" width="400" height="2" fill="#ffffff" opacity="0.35" />
        </>
      ) : (
        <rect y="246" width="400" height="14" fill={p.ground} opacity="0.85" />
      )}
    </svg>
  );
}
