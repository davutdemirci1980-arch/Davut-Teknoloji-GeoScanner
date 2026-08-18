// Separable Gaussian blur on a 2D (nx, ny) slice, with clamped (nearest) edges.
export function gaussianBlur2D(data: Float64Array, nx: number, ny: number, sigma: number): Float64Array {
  if (sigma <= 0) return data.slice();
  const radius = Math.max(1, Math.ceil(sigma * 3));
  const kernel = new Float64Array(2 * radius + 1);
  let sum = 0;
  for (let i = -radius; i <= radius; i++) {
    const w = Math.exp(-(i * i) / (2 * sigma * sigma));
    kernel[i + radius] = w;
    sum += w;
  }
  for (let i = 0; i < kernel.length; i++) kernel[i] /= sum;

  const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

  const tmp = new Float64Array(nx * ny);
  for (let x = 0; x < nx; x++) {
    for (let y = 0; y < ny; y++) {
      let acc = 0;
      for (let k = -radius; k <= radius; k++) {
        const xx = clamp(x + k, 0, nx - 1);
        acc += data[xx * ny + y] * kernel[k + radius];
      }
      tmp[x * ny + y] = acc;
    }
  }

  const out = new Float64Array(nx * ny);
  for (let x = 0; x < nx; x++) {
    for (let y = 0; y < ny; y++) {
      let acc = 0;
      for (let k = -radius; k <= radius; k++) {
        const yy = clamp(y + k, 0, ny - 1);
        acc += tmp[x * ny + yy] * kernel[k + radius];
      }
      out[x * ny + y] = acc;
    }
  }
  return out;
}
