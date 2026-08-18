from __future__ import annotations

import numpy as np

from app.core.voxel_engine import VoxelGrid
from app.sensors.base import BaseSensorSimulator, local_contrast, surface_skin_weight


class ThermalSimulator(BaseSensorSimulator):
    key = "thermal"
    name_tr = "Termal / Kızılötesi"
    name_en = "Thermal Infrared"
    description_tr = "Sığ gömülü yapıların yüzey ısı akışını değiştirmesiyle oluşan yüzey sıcaklık anomalilerini görüntüler."
    icon = "thermal"
    max_effective_depth_m = 1.0
    base_reliability = 0.55

    def _raw_signal(self, grid: VoxelGrid) -> np.ndarray:
        conductivity = grid.property_grid("thermal_conductivity")
        contrast = local_contrast(conductivity)
        weight = surface_skin_weight(grid, skin_depth_m=0.5)
        return contrast * weight[None, None, :]

    def profile_2d(self, grid: VoxelGrid, volume: np.ndarray):
        weight = np.arange(grid.nz, 0, -1, dtype=float)
        surface_map = np.tensordot(volume, weight, axes=([2], [0])) / weight.sum()
        return surface_map, ("x", "y")
