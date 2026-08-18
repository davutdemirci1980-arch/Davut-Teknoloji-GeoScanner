import type { VoxelGrid } from "../voxelEngine";
import { localContrast, surfaceSkinWeight, type SensorDef } from "./base";

export const ThermalSensor: SensorDef = {
  key: "thermal",
  name_tr: "Termal / Kızılötesi",
  name_en: "Thermal Infrared",
  description_tr: "Sığ gömülü yapıların yüzey ısı akışını değiştirmesiyle oluşan yüzey sıcaklık anomalilerini görüntüler.",
  icon: "thermal",
  max_effective_depth_m: 1.0,
  base_reliability: 0.55,

  rawSignal(grid: VoxelGrid): Float64Array {
    const conductivity = grid.propertyGrid("thermal_conductivity");
    const contrast = localContrast(grid, conductivity);
    const weight = surfaceSkinWeight(grid, 0.5);
    const out = new Float64Array(contrast.length);
    for (let x = 0; x < grid.nx; x++) {
      for (let y = 0; y < grid.ny; y++) {
        for (let z = 0; z < grid.nz; z++) {
          const i = grid.idx(x, y, z);
          out[i] = contrast[i] * weight[z];
        }
      }
    }
    return out;
  },

  profile2D(grid: VoxelGrid, volume: Float64Array) {
    const weight: number[] = [];
    for (let z = 0; z < grid.nz; z++) weight.push(grid.nz - z);
    const weightSum = weight.reduce((a, b) => a + b, 0);
    const data: number[][] = [];
    for (let x = 0; x < grid.nx; x++) {
      const row: number[] = [];
      for (let y = 0; y < grid.ny; y++) {
        let sum = 0;
        for (let z = 0; z < grid.nz; z++) sum += volume[grid.idx(x, y, z)] * weight[z];
        row.push(sum / weightSum);
      }
      data.push(row);
    }
    return { data, axes: ["x", "y"] as [string, string] };
  },
};
