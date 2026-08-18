import type { VoxelGrid } from "../voxelEngine";
import { localContrast, powerLawAttenuation, type SensorDef } from "./base";

export const MagnetometerSensor: SensorDef = {
  key: "magnetometer",
  name_tr: "Manyetometre",
  name_en: "Magnetometer",
  description_tr: "Manyetik duyarlılık farklarından (metal, yanmış toprak, taş yapı) kaynaklı alan anomalilerini ölçer.",
  icon: "magnet",
  max_effective_depth_m: 3.0,
  base_reliability: 0.75,

  rawSignal(grid: VoxelGrid): Float64Array {
    const susceptibility = grid.propertyGrid("magnetic_susceptibility");
    const contrast = localContrast(grid, susceptibility);
    const attenuation = powerLawAttenuation(grid, 1.2, 2.5);
    const out = new Float64Array(contrast.length);
    for (let x = 0; x < grid.nx; x++) {
      for (let y = 0; y < grid.ny; y++) {
        for (let z = 0; z < grid.nz; z++) {
          const i = grid.idx(x, y, z);
          out[i] = contrast[i] * attenuation[z];
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
