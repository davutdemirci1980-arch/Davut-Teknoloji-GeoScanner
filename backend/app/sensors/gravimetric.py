from __future__ import annotations

import numpy as np

from app.core.voxel_engine import VoxelGrid
from app.sensors.base import BaseSensorSimulator, local_contrast, power_law_attenuation


class GravimetricSimulator(BaseSensorSimulator):
    key = "gravimetric"
    name_tr = "Mikrogravite"
    name_en = "Microgravity"
    description_tr = "Yoğunluk farklarının (büyük boşluk/kütle) yerçekimi alanında yarattığı zayıf ama derin-görebilen anomalileri ölçer."
    icon = "gravity"
    max_effective_depth_m = 8.0
    base_reliability = 0.6

    def _raw_signal(self, grid: VoxelGrid) -> np.ndarray:
        density = grid.property_grid("density_kg_m3")
        contrast = local_contrast(density)
        attenuation = power_law_attenuation(grid, effective_depth_m=self.max_effective_depth_m, exponent=1.5)
        return contrast * attenuation[None, None, :]

    def profile_2d(self, grid: VoxelGrid, volume: np.ndarray):
        return volume.sum(axis=2), ("x", "y")
