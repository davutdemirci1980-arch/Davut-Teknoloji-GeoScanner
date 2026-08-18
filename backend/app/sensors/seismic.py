from __future__ import annotations

import numpy as np

from app.core.voxel_engine import VoxelGrid
from app.sensors.base import BaseSensorSimulator, power_law_attenuation


class SeismicSimulator(BaseSensorSimulator):
    key = "seismic"
    name_tr = "Sismik Yansıma"
    name_en = "Seismic Reflection"
    description_tr = "Akustik empedans (yoğunluk x hız) sınırlarından yansıyan sismik dalgalarla derin katman görüntüleme."
    icon = "seismic"
    max_effective_depth_m = 10.0
    base_reliability = 0.78

    def _raw_signal(self, grid: VoxelGrid) -> np.ndarray:
        density = grid.property_grid("density_kg_m3")
        velocity = grid.property_grid("seismic_velocity_m_s")
        impedance = density * velocity

        reflectivity = np.zeros_like(impedance)
        z1 = impedance[:, :, 1:]
        z0 = impedance[:, :, :-1]
        reflectivity[:, :, 1:] = np.abs((z1 - z0) / (z1 + z0 + 1e-9))

        attenuation = power_law_attenuation(grid, effective_depth_m=self.max_effective_depth_m, exponent=1.2)
        return reflectivity * attenuation[None, None, :]
