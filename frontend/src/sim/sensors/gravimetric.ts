import type { VoxelGrid } from "../voxelEngine";
import { localContrast, powerLawAttenuation, type SensorDef } from "./base";

export const GravimetricSensor: SensorDef = {
  key: "gravimetric",
  name_tr: "Mikrogravite",
  name_en: "Microgravity",
  description_tr: "Yoğunluk farklarının (büyük boşluk/kütle) yerçekimi alanında yarattığı zayıf ama derin-görebilen anomalileri ölçer.",
  icon: "gravity",
  max_effective_depth_m: 8.0,
  base_reliability: 0.6,

  rawSignal(grid: VoxelGrid): Float64Array {
    const density = grid.propertyGrid("density_kg_m3");
    const contrast = localContrast(grid, density);
    const attenuation = powerLawAttenuation(grid, 8.0, 1.5);
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
        let sum = 0;
        for (let z = 0; z < grid.nz; z++) sum += volume[grid.idx(x, y, z)];
        row.push(sum);
      }
      data.push(row);
    }
    return { data, axes: ["x", "y"] as [string, string] };
  },
};
