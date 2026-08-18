import { VoxelGrid } from "./voxelEngine";
import { Rng } from "./rng";

export type ScenarioPreset = "random" | "utility_lines" | "archaeology" | "geology_only";

const PRESET_ANOMALY_POOLS: Record<ScenarioPreset, string[]> = {
  random: ["metal_pipe", "pvc_pipe", "void", "concrete", "wood", "archaeological_stone"],
  utility_lines: ["metal_pipe", "pvc_pipe", "concrete"],
  archaeology: ["void", "wood", "archaeological_stone", "concrete"],
  geology_only: [],
};

function buildLayeredGeology(grid: VoxelGrid, rng: Rng): void {
  grid.fill("dry_soil");

  const sequence = ["dry_soil"];
  if (rng.random() < 0.7) sequence.push("sand");
  sequence.push("wet_clay");
  if (rng.random() < 0.35) sequence.push("groundwater");
  if (rng.random() < 0.55) sequence.push("limestone");
  sequence.push("bedrock");

  const weights = sequence.map(() => rng.uniform(0.6, 1.4));
  const weightSum = weights.reduce((a, b) => a + b, 0);
  const normalized = weights.map((w) => w / weightSum);
  const thicknesses = normalized.map((w) => Math.max(1, Math.round(w * grid.nz)));

  let total = thicknesses.reduce((a, b) => a + b, 0);
  while (total > grid.nz) {
    const maxIdx = thicknesses.indexOf(Math.max(...thicknesses));
    thicknesses[maxIdx] -= 1;
    total -= 1;
  }
  while (total < grid.nz) {
    const minIdx = thicknesses.indexOf(Math.min(...thicknesses));
    thicknesses[minIdx] += 1;
    total += 1;
  }

  const amp = Math.max(1.0, grid.nz * 0.06);
  const fx = rng.uniform(0.6, 1.4);
  const fy = rng.uniform(0.6, 1.4);
  const phaseX = rng.uniform(0, 2 * Math.PI);
  const phaseY = rng.uniform(0, 2 * Math.PI);
  const undulation = new Int32Array(grid.nx * grid.ny);
  for (let x = 0; x < grid.nx; x++) {
    for (let y = 0; y < grid.ny; y++) {
      const value =
        amp * Math.sin((x / Math.max(grid.nx, 1)) * 2 * Math.PI * fx + phaseX) +
        amp * Math.cos((y / Math.max(grid.ny, 1)) * 2 * Math.PI * fy + phaseY);
      undulation[x * grid.ny + y] = Math.trunc(value);
    }
  }

  let z = 0;
  for (let i = 0; i < sequence.length; i++) {
    grid.setLayer(z, z + thicknesses[i], sequence[i], undulation);
    z += thicknesses[i];
  }
}

function randomAnomalyShape(grid: VoxelGrid, materialKey: string, rng: Rng, maxDepthFraction: number): void {
  const margin = Math.max(1, Math.floor(Math.min(grid.nx, grid.ny) / 6));
  const maxZ = Math.max(2, Math.floor(grid.nz * maxDepthFraction));

  if (materialKey === "metal_pipe" || materialKey === "pvc_pipe") {
    const depth = rng.integers(1, Math.max(2, Math.floor(grid.nz * 0.35)));
    const radius = rng.uniform(0.6, 1.4);
    if (rng.random() < 0.5) {
      const y = rng.integers(margin, Math.max(margin + 1, grid.ny - margin));
      grid.embedCylinder(materialKey, [0, y, depth], [grid.nx - 1, y, depth], radius);
    } else {
      const x = rng.integers(margin, Math.max(margin + 1, grid.nx - margin));
      grid.embedCylinder(materialKey, [x, 0, depth], [x, grid.ny - 1, depth], radius);
    }
    return;
  }

  const cx = rng.integers(margin, Math.max(margin + 1, grid.nx - margin));
  const cy = rng.integers(margin, Math.max(margin + 1, grid.ny - margin));
  const cz = rng.integers(Math.max(1, Math.floor(maxZ / 3)), maxZ);

  if (materialKey === "void") {
    const radius = rng.uniform(1.0, 2.2);
    grid.embedSphere(materialKey, [cx, cy, cz], radius);
  } else if (materialKey === "concrete" || materialKey === "archaeological_stone") {
    const size: [number, number, number] = [rng.integers(2, 5), rng.integers(2, 5), rng.integers(2, 4)];
    grid.embedBox(materialKey, [cx, cy, cz], size);
  } else {
    const size: [number, number, number] = [rng.integers(2, 4), rng.integers(1, 3), rng.integers(1, 2) + 1];
    grid.embedBox(materialKey, [cx, cy, cz], size);
  }
}

export function generateScenario(
  nx = 28,
  ny = 28,
  nz = 18,
  voxelSizeM = 0.5,
  preset: ScenarioPreset = "random",
  numAnomalies = 5,
  seed: number | null = null,
  speckleNoise = 0.01
): VoxelGrid {
  const rng = new Rng(seed);
  const grid = new VoxelGrid(nx, ny, nz, voxelSizeM);

  buildLayeredGeology(grid, rng);

  const pool = PRESET_ANOMALY_POOLS[preset] ?? PRESET_ANOMALY_POOLS.random;
  if (pool.length > 0 && numAnomalies > 0) {
    for (let i = 0; i < numAnomalies; i++) {
      const materialKey = pool[rng.integers(0, pool.length)];
      randomAnomalyShape(grid, materialKey, rng, 0.75);
    }
  }

  if (speckleNoise > 0) grid.addRandomNoise(rng, speckleNoise);

  return grid;
}
