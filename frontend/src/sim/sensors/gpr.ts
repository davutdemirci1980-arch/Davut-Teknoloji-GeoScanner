import type { VoxelGrid } from "../voxelEngine";
import type { SensorDef } from "./base";

export const GPRSensor: SensorDef = {
  key: "gpr",
  name_tr: "Yer Radarı (GPR)",
  name_en: "Ground Penetrating Radar",
  description_tr: "Dielektrik geçirgenlik sınırlarından yansıyan elektromanyetik dalgalarla sığ-orta derinlik görüntüleme.",
  icon: "radar",
  max_effective_depth_m: 5.0,
  base_reliability: 0.85,

  rawSignal(grid: VoxelGrid): Float64Array {
    const permittivity = grid.propertyGrid("permittivity");
    const resistivity = grid.propertyGrid("resistivity_ohm_m");
    const { nx, ny, nz } = grid;
    const out = new Float64Array(nx * ny * nz);

    for (let x = 0; x < nx; x++) {
      for (let y = 0; y < ny; y++) {
        let condSum = 0;
        let prevSqrtEps = 0;
        for (let z = 0; z < nz; z++) {
          const i = grid.idx(x, y, z);
          const sqrtEps = Math.sqrt(permittivity[i]);
          const conductivity = 1.0 / resistivity[i];
          condSum += conductivity;
          const meanCondAbove = condSum / (z + 1);
          const reflectivity = z === 0 ? 0 : Math.abs(sqrtEps - prevSqrtEps);
          const attenuation = Math.exp(-3.0 * meanCondAbove * grid.voxelSizeM);
          out[i] = reflectivity * attenuation;
          prevSqrtEps = sqrtEps;
        }
      }
    }
    return out;
  },
};
