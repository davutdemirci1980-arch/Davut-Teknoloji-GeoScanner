// Simple blue -> cyan -> yellow -> red heat colormap for 0..1 values.
const STOPS: [number, number, number, number][] = [
  [0.0, 12, 20, 69],
  [0.25, 15, 118, 172],
  [0.5, 45, 191, 132],
  [0.75, 240, 200, 40],
  [1.0, 219, 51, 36],
];

export function heatColor(t: number): [number, number, number] {
  const v = Math.min(1, Math.max(0, t));
  for (let i = 0; i < STOPS.length - 1; i++) {
    const [t0, r0, g0, b0] = STOPS[i];
    const [t1, r1, g1, b1] = STOPS[i + 1];
    if (v >= t0 && v <= t1) {
      const f = (v - t0) / (t1 - t0 || 1);
      return [r0 + (r1 - r0) * f, g0 + (g1 - g0) * f, b0 + (b1 - b0) * f];
    }
  }
  const [, r, g, b] = STOPS[STOPS.length - 1];
  return [r, g, b];
}

export function heatColorCss(t: number): string {
  const [r, g, b] = heatColor(t);
  return `rgb(${r.toFixed(0)}, ${g.toFixed(0)}, ${b.toFixed(0)})`;
}

export function heatColorHex(t: number): string {
  const [r, g, b] = heatColor(t);
  const toHex = (n: number) => Math.round(n).toString(16).padStart(2, "0");
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}
