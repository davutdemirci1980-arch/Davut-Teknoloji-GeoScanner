// TypeScript port of backend/app/sensors/base.py
import type { VoxelGrid } from "../voxelEngine";
import type { Rng } from "../rng";

export interface SensorSimulationResult {
  sensorKey: string;
  probabilityVolume: Float64Array; // flat (nx*ny*nz), values in [0,1]
  profile2D: number[][];
  profileAxes: [string, string];
  stats: { min: number; max: number; mean: number; std: number };
}

export interface SensorDef {
  key: string;
  name_tr: string;
  name_en: string;
  description_tr: string;
  icon: string;
  max_effective_depth_m: number;
  base_reliability: number;
  rawSignal: (grid: VoxelGrid) => Float64Array;
  profile2D?: (grid: VoxelGrid, volume: Float64Array) => { data: number[][]; axes: [string, string] };
}

export function normalize01(arr: Float64Array): Float64Array {
  let lo = Infinity;
  let hi = -Infinity;
  for (const v of arr) {
    if (v < lo) lo = v;
    if (v > hi) hi = v;
  }
  const out = new Float64Array(arr.length);
  if (hi - lo < 1e-9) return out;
  for (let i = 0; i < arr.length; i++) out[i] = (arr[i] - lo) / (hi - lo);
  return out;
}

// Deviation of each voxel from the median of its own depth (z) slice.
export function localContrast(grid: VoxelGrid, field: Float64Array): Float64Array {
  const { nx, ny, nz } = grid;
  const out = new Float64Array(field.length);
  const sliceVals = new Float64Array(nx * ny);
  for (let z = 0; z < nz; z++) {
    let n = 0;
    for (let x = 0; x < nx; x++) {
      for (let y = 0; y < ny; y++) sliceVals[n++] = field[grid.idx(x, y, z)];
    }
    const sorted = sliceVals.slice(0, n).sort();
    const median = n % 2 === 1 ? sorted[(n - 1) / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    for (let x = 0; x < nx; x++) {
      for (let y = 0; y < ny; y++) {
        const i = grid.idx(x, y, z);
        out[i] = Math.abs(field[i] - median);
      }
    }
  }
  return out;
}

export function logLocalContrast(grid: VoxelGrid, field: Float64Array): Float64Array {
  const safe = new Float64Array(field.length);
  for (let i = 0; i < field.length; i++) safe[i] = Math.log10(Math.max(field[i], 1e-9));
  return localContrast(grid, safe);
}

export function depthAxisM(grid: VoxelGrid): Float64Array {
  const out = new Float64Array(grid.nz);
  for (let z = 0; z < grid.nz; z++) out[z] = (z + 0.5) * grid.voxelSizeM;
  return out;
}

export function powerLawAttenuation(grid: VoxelGrid, effectiveDepthM: number, exponent: number): Float64Array {
  const depths = depthAxisM(grid);
  const out = new Float64Array(grid.nz);
  for (let z = 0; z < grid.nz; z++) out[z] = 1.0 / Math.pow(1.0 + depths[z] / effectiveDepthM, exponent);
  return out;
}

export function surfaceSkinWeight(grid: VoxelGrid, skinDepthM: number): Float64Array {
  const depths = depthAxisM(grid);
  const out = new Float64Array(grid.nz);
  for (let z = 0; z < grid.nz; z++) out[z] = Math.exp(-depths[z] / skinDepthM);
  return out;
}

export function emResponseWeight(grid: VoxelGrid, peakDepthM: number): Float64Array {
  const depths = depthAxisM(grid);
  const out = new Float64Array(grid.nz);
  for (let z = 0; z < grid.nz; z++) {
    const x = depths[z] / peakDepthM;
    out[z] = Math.max(0, x * Math.exp(1.0 - x));
  }
  return out;
}

function defaultProfile2D(grid: VoxelGrid, volume: Float64Array): { data: number[][]; axes: [string, string] } {
  const midY = Math.floor(grid.ny / 2);
  const data: number[][] = [];
  for (let x = 0; x < grid.nx; x++) {
    const row: number[] = [];
    for (let z = 0; z < grid.nz; z++) row.push(volume[grid.idx(x, midY, z)]);
    data.push(row);
  }
  return { data, axes: ["x", "z"] };
}

export function simulateSensor(sensor: SensorDef, grid: VoxelGrid, rng: Rng, noiseLevel = 0.12): SensorSimulationResult {
  const raw = sensor.rawSignal(grid);

  let mean = 0;
  for (const v of raw) mean += v;
  mean /= raw.length;
  let variance = 0;
  for (const v of raw) variance += (v - mean) ** 2;
  const std = Math.sqrt(variance / raw.length);

  const noisy = new Float64Array(raw.length);
  const noiseStd = Math.max(1e-6, noiseLevel) * (std + 1e-6);
  for (let i = 0; i < raw.length; i++) noisy[i] = raw[i] + rng.normal(0, noiseStd);

  const volume = normalize01(noisy);
  const { data, axes } = (sensor.profile2D ?? defaultProfile2D)(grid, volume);

  let vLo = Infinity;
  let vHi = -Infinity;
  let vSum = 0;
  for (const v of volume) {
    if (v < vLo) vLo = v;
    if (v > vHi) vHi = v;
    vSum += v;
  }
  const vMean = vSum / volume.length;
  let vVar = 0;
  for (const v of volume) vVar += (v - vMean) ** 2;

  return {
    sensorKey: sensor.key,
    probabilityVolume: volume,
    profile2D: data,
    profileAxes: axes,
    stats: { min: vLo, max: vHi, mean: vMean, std: Math.sqrt(vVar / volume.length) },
  };
}
