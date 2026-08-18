import type { VoxelGrid } from "../voxelEngine";
import { localContrast, type SensorDef } from "./base";

export const LidarSensor: SensorDef = {
  key: "lidar",
  name_tr: "LIDAR (Mikro-topografya)",
  name_en: "LIDAR Micro-topography",
  description_tr: "Sığ boşluk/dolgu çökmelerinin yüzeyde bıraktığı milimetrik yükselti farklarını mikro-topografya olarak yakalar.",
  icon: "lidar",
  max_effective_depth_m: 0.6,
  base_reliability: 0.5,

  rawSignal(grid: VoxelGrid): Float64Array {
    const density = grid.propertyGrid("density_kg_m3");
    const isVoid = grid.isAnomalyGrid();
    const contrast = localContrast(grid, density);
    const combined = new Float64Array(contrast.length);
    for (let i = 0; i < contrast.length; i++) combined[i] = contrast[i] + 40.0 * isVoid[i];

    const out = new Float64Array(combined.length);
    const skin = Math.min(grid.nz, Math.max(1, Math.round(0.4 / grid.voxelSizeM)));
    for (let x = 0; x < grid.nx; x++) {
      for (let y = 0; y < grid.ny; y++) {
        for (let i = 0; i < skin; i++) {
          const w = skin === 1 ? 1.0 : 1.0 - i / (skin - 1);
          let maxV = -Infinity;
          for (let z = i; z < grid.nz; z++) maxV = Math.max(maxV, combined[grid.idx(x, y, z)]);
          out[grid.idx(x, y, i)] = maxV * w;
        }
      }
    }
    return out;
  },

  profile2D(grid: VoxelGrid, volume: Float64Array) {
    const data: number[][] = [];
    for (let x = 0; x < grid.nx; x++) {
      const row: number[] = [];
      for (let y = 0; y < grid.ny; y++) {
        let maxV = -Infinity;
        for (let z = 0; z < grid.nz; z++) maxV = Math.max(maxV, volume[grid.idx(x, y, z)]);
        row.push(maxV);
      }
      data.push(row);
    }
    return { data, axes: ["x", "y"] as [string, string] };
  },
};
