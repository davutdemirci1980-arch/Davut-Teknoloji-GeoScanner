from __future__ import annotations

import numpy as np

from app.core.voxel_engine import VoxelGrid
from app.sensors.base import BaseSensorSimulator, local_contrast, power_law_attenuation


class MagnetometerSimulator(BaseSensorSimulator):
    key = "magnetometer"
    name_tr = "Manyetometre"
    name_en = "Magnetometer"
    description_tr = "Manyetik duyarlılık farklarından (metal, yanmış toprak, taş yapı) kaynaklı alan anomalilerini ölçer."
    icon = "magnet"
    max_effective_depth_m = 3.0
    base_reliability = 0.75

    def _raw_signal(self, grid: VoxelGrid) -> np.ndarray:
        susceptibility = grid.property_grid("magnetic_susceptibility")
        contrast = local_contrast(susceptibility)
        attenuation = power_law_attenuation(grid, effective_depth_m=1.2, exponent=2.5)
        return contrast * attenuation[None, None, :]

    def profile_2d(self, grid: VoxelGrid, volume: np.ndarray):
        return volume.max(axis=2), ("x", "y")
