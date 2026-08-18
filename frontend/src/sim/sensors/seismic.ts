import type { VoxelGrid } from "../voxelEngine";
import { powerLawAttenuation, type SensorDef } from "./base";

export const SeismicSensor: SensorDef = {
  key: "seismic",
  name_tr: "Sismik Yansıma",
  name_en: "Seismic Reflection",
  description_tr: "Akustik empedans (yoğunluk x hız) sınırlarından yansıyan sismik dalgalarla derin katman görüntüleme.",
  icon: "seismic",
  max_effective_depth_m: 10.0,
  base_reliability: 0.78,

  rawSignal(grid: VoxelGrid): Float64Array {
    const density = grid.propertyGrid("density_kg_m3");
    const velocity = grid.propertyGrid("seismic_velocity_m_s");
    const attenuation = powerLawAttenuation(grid, 10.0, 1.2);
    const { nx, ny, nz } = grid;
    const out = new Float64Array(nx * ny * nz);

    for (let x = 0; x < nx; x++) {
      for (let y = 0; y < ny; y++) {
        let prevImpedance = 0;
        for (let z = 0; z < nz; z++) {
          const i = grid.idx(x, y, z);
          const impedance = density[i] * velocity[i];
          const reflectivity = z === 0 ? 0 : Math.abs((impedance - prevImpedance) / (impedance + prevImpedance + 1e-9));
          out[i] = reflectivity * attenuation[z];
          prevImpedance = impedance;
        }
      }
    }
    return out;
  },
};
