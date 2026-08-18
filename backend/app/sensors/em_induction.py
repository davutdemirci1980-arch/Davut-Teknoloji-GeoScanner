from __future__ import annotations

import numpy as np

from app.core.voxel_engine import VoxelGrid
from app.sensors.base import BaseSensorSimulator, em_response_weight, log_local_contrast


class EMInductionSimulator(BaseSensorSimulator):
    key = "em_induction"
    name_tr = "Elektromanyetik İndüksiyon (EMI)"
    name_en = "Electromagnetic Induction"
    description_tr = "İndüklenen ikincil manyetik alandan görünür iletkenlik haritası çıkararak metal ve nemli/kil zonları bulur."
    icon = "emi"
    max_effective_depth_m = 3.5
    base_reliability = 0.8

    def _raw_signal(self, grid: VoxelGrid) -> np.ndarray:
        conductivity = 1.0 / grid.property_grid("resistivity_ohm_m")
        contrast = log_local_contrast(conductivity)
        weight = em_response_weight(grid, peak_depth_m=1.0)
        return contrast * weight[None, None, :]

    def profile_2d(self, grid: VoxelGrid, volume: np.ndarray):
        return volume.sum(axis=2), ("x", "y")
