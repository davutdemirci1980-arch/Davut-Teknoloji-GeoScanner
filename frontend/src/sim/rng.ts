// Small seedable PRNG (mulberry32) + helpers mirroring the subset of
// numpy.random.Generator used by the Python simulation, so scenario/sensor
// generation stays reproducible when a seed is given.
export class Rng {
  private state: number;

  constructor(seed?: number | null) {
    this.state = (seed ?? Math.floor(Math.random() * 2 ** 31)) >>> 0;
  }

  random(): number {
    this.state |= 0;
    this.state = (this.state + 0x6d2b79f5) | 0;
    let t = Math.imul(this.state ^ (this.state >>> 15), 1 | this.state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  uniform(min: number, max: number): number {
    return min + this.random() * (max - min);
  }

  integers(min: number, maxExclusive: number): number {
    return Math.floor(this.uniform(min, maxExclusive));
  }

  normal(mean = 0, std = 1): number {
    // Box-Muller transform.
    const u1 = Math.max(this.random(), 1e-12);
    const u2 = this.random();
    const z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    return mean + z0 * std;
  }
}
