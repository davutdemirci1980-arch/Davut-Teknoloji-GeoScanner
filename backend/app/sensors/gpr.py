from __future__ import annotations

import numpy as np

from app.core.voxel_engine import VoxelGrid
from app.sensors.base import BaseSensorSimulator


class GPRSimulator(BaseSensorSimulator):
    key = "gpr"
    name_tr = "Yer Radarı (GPR)"
    name_en = "Ground Penetrating Radar"
    description_tr = "Dielektrik geçirgenlik sınırlarından yansıyan elektromanyetik dalgalarla sığ-orta derinlik görüntüleme."
    icon = "radar"
    max_effective_depth_m = 5.0
    base_reliability = 0.85

    def _raw_signal(self, grid: VoxelGrid) -> np.ndarray:
        permittivity = grid.property_grid("permittivity")
        conductivity = 1.0 / grid.property_grid("resistivity_ohm_m")

        sqrt_eps = np.sqrt(permittivity)
        reflectivity = np.zeros_like(sqrt_eps)
        reflectivity[:, :, 1:] = np.abs(np.diff(sqrt_eps, axis=2))

        mean_conductivity_above = np.cumsum(conductivity, axis=2) / (
            np.arange(1, grid.nz + 1)[None, None, :]
        )
        attenuation = np.exp(-3.0 * mean_conductivity_above * grid.voxel_size_m)

        return reflectivity * attenuation
