import type { VoxelGrid } from "../voxelEngine";
import type { SensorSimulationResult } from "../sensors/base";
import { classifyEvidence } from "./classifier";
import { MATERIALS } from "../materials";

export interface DetectedAnomaly {
  id: number;
  centroidVox: [number, number, number];
  centroidM: [number, number, number];
  sizeVoxels: number;
  volumeM3: number;
  bboxVox: [[number, number], [number, number], [number, number]];
  fusedConfidence: number;
  agreementCount: number;
  contributingSensors: string[];
  predictedMaterial: string;
  predictedMaterialNameTr: string;
  classificationConfidence: number;
  signature: Record<string, number>;
  materialScores: Record<string, number>;
}

function percentile(sorted: Float64Array, p: number): number {
  const idx = (p / 100) * (sorted.length - 1);
  const lo = Math.floor(idx);
  const hi = Math.ceil(idx);
  if (lo === hi) return sorted[lo];
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
}

const NEIGHBOR_OFFSETS: [number, number, number][] = [];
for (let dx = -1; dx <= 1; dx++)
  for (let dy = -1; dy <= 1; dy++)
    for (let dz = -1; dz <= 1; dz++) if (dx !== 0 || dy !== 0 || dz !== 0) NEIGHBOR_OFFSETS.push([dx, dy, dz]);

export function detectAnomalies(
  grid: VoxelGrid,
  sensorResults: Record<string, SensorSimulationResult>,
  fusedProbability: Float64Array,
  agreementCount: Int32Array,
  minSizeVoxels = 2,
  thresholdPercentile = 92.0
): DetectedAnomaly[] {
  const sorted = fusedProbability.slice().sort();
  let mean = 0;
  for (const v of fusedProbability) mean += v;
  mean /= fusedProbability.length;
  let variance = 0;
  for (const v of fusedProbability) variance += (v - mean) ** 2;
  const std = Math.sqrt(variance / fusedProbability.length);
  const threshold = Math.max(percentile(sorted, thresholdPercentile), mean + std);

  const { nx, ny, nz } = grid;
  const visited = new Uint8Array(nx * ny * nz);
  const anomalies: DetectedAnomaly[] = [];
  const sensorKeys = Object.keys(sensorResults);

  for (let x0 = 0; x0 < nx; x0++) {
    for (let y0 = 0; y0 < ny; y0++) {
      for (let z0 = 0; z0 < nz; z0++) {
        const start = grid.idx(x0, y0, z0);
        if (visited[start] || fusedProbability[start] < threshold) continue;

        const component: number[] = [];
        const stack: [number, number, number][] = [[x0, y0, z0]];
        visited[start] = 1;
        while (stack.length > 0) {
          const [x, y, z] = stack.pop()!;
          component.push(grid.idx(x, y, z));
          for (const [dx, dy, dz] of NEIGHBOR_OFFSETS) {
            const nx2 = x + dx;
            const ny2 = y + dy;
            const nz2 = z + dz;
            if (nx2 < 0 || nx2 >= nx || ny2 < 0 || ny2 >= ny || nz2 < 0 || nz2 >= nz) continue;
            const ni = grid.idx(nx2, ny2, nz2);
            if (visited[ni] || fusedProbability[ni] < threshold) continue;
            visited[ni] = 1;
            stack.push([nx2, ny2, nz2]);
          }
        }

        if (component.length < minSizeVoxels) continue;

        let sx = 0, sy = 0, sz = 0;
        let xMin = Infinity, xMax = -Infinity, yMin = Infinity, yMax = -Infinity, zMin = Infinity, zMax = -Infinity;
        let fusedSum = 0;
        let agreementSum = 0;
        for (const i of component) {
          const x = Math.floor(i / (ny * nz));
          const y = Math.floor((i % (ny * nz)) / nz);
          const z = i % nz;
          sx += x; sy += y; sz += z;
          xMin = Math.min(xMin, x); xMax = Math.max(xMax, x);
          yMin = Math.min(yMin, y); yMax = Math.max(yMax, y);
          zMin = Math.min(zMin, z); zMax = Math.max(zMax, z);
          fusedSum += fusedProbability[i];
          agreementSum += agreementCount[i];
        }
        const n = component.length;
        const centroidVox: [number, number, number] = [sx / n, sy / n, sz / n];

        const evidenceVector: Record<string, number> = {};
        const contributing: string[] = [];
        for (const key of sensorKeys) {
          const vol = sensorResults[key].probabilityVolume;
          let sum = 0;
          for (const i of component) sum += vol[i];
          const value = sum / n;
          evidenceVector[key] = value;
          if (value > 0.5) contributing.push(key);
        }

        const { predicted, confidence, scores } = classifyEvidence(evidenceVector);
        const material = MATERIALS[predicted];

        anomalies.push({
          id: 0,
          centroidVox,
          centroidM: [centroidVox[0] * grid.voxelSizeM, centroidVox[1] * grid.voxelSizeM, centroidVox[2] * grid.voxelSizeM],
          sizeVoxels: n,
          volumeM3: n * grid.voxelSizeM ** 3,
          bboxVox: [[xMin, xMax], [yMin, yMax], [zMin, zMax]],
          fusedConfidence: fusedSum / n,
          agreementCount: Math.round(agreementSum / n),
          contributingSensors: contributing,
          predictedMaterial: predicted,
          predictedMaterialNameTr: material.name_tr,
          classificationConfidence: confidence,
          signature: evidenceVector,
          materialScores: scores,
        });
      }
    }
  }

  anomalies.sort((a, b) => b.fusedConfidence - a.fusedConfidence);
  anomalies.forEach((a, i) => (a.id = i + 1));
  return anomalies;
}
