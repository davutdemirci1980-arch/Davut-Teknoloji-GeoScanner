import type { VoxelGrid } from "../voxelEngine";
import { logLocalContrast, powerLawAttenuation, type SensorDef } from "./base";
import { gaussianBlur2D } from "./gaussianBlur";

export const ResistivitySensor: SensorDef = {
  key: "resistivity",
  name_tr: "Elektriksel Özdirenç Tomografisi (ERT)",
  name_en: "Electrical Resistivity Tomography",
  description_tr: "Zemine akım enjekte ederek özdirenç dağılımından yeraltı katmanlarını ve boşlukları haritalar.",
  icon: "ert",
  max_effective_depth_m: 6.0,
  base_reliability: 0.82,

  rawSignal(grid: VoxelGrid): Float64Array {
    const resistivity = grid.propertyGrid("resistivity_ohm_m");
    const contrast = logLocalContrast(grid, resistivity);
    const attenuation = powerLawAttenuation(grid, 6.0, 2.0);
    const { nx, ny, nz } = grid;
    const out = new Float64Array(nx * ny * nz);

    for (let z = 0; z < nz; z++) {
      const sigma = 0.3 + 0.18 * z;
      const slice = new Float64Array(nx * ny);
      for (let x = 0; x < nx; x++) {
        for (let y = 0; y < ny; y++) slice[x * ny + y] = contrast[grid.idx(x, y, z)];
      }
      const smoothed = gaussianBlur2D(slice, nx, ny, sigma);
      for (let x = 0; x < nx; x++) {
        for (let y = 0; y < ny; y++) out[grid.idx(x, y, z)] = smoothed[x * ny + y] * attenuation[z];
      }
    }
    return out;
  },
};
