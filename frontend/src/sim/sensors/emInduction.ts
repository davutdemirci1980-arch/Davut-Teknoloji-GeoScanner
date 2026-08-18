import type { VoxelGrid } from "../voxelEngine";
import { emResponseWeight, logLocalContrast, type SensorDef } from "./base";

export const EMInductionSensor: SensorDef = {
  key: "em_induction",
  name_tr: "Elektromanyetik İndüksiyon (EMI)",
  name_en: "Electromagnetic Induction",
  description_tr: "İndüklenen ikincil manyetik alandan görünür iletkenlik haritası çıkararak metal ve nemli/kil zonları bulur.",
  icon: "emi",
  max_effective_depth_m: 3.5,
  base_reliability: 0.8,

  rawSignal(grid: VoxelGrid): Float64Array {
    const resistivity = grid.propertyGrid("resistivity_ohm_m");
    const conductivity = new Float64Array(resistivity.length);
    for (let i = 0; i < resistivity.length; i++) conductivity[i] = 1.0 / resistivity[i];
    const contrast = logLocalContrast(grid, conductivity);
    const weight = emResponseWeight(grid, 1.0);
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
