from __future__ import annotations

import numpy as np

from app.core.voxel_engine import VoxelGrid
from app.sensors.base import BaseSensorSimulator, local_contrast


class LidarSimulator(BaseSensorSimulator):
    key = "lidar"
    name_tr = "LIDAR (Mikro-topografya)"
    name_en = "LIDAR Micro-topography"
    description_tr = "Sığ boşluk/dolgu çökmelerinin yüzeyde bıraktığı milimetrik yükselti farklarını mikro-topografya olarak yakalar."
    icon = "lidar"
    max_effective_depth_m = 0.6
    base_reliability = 0.5

    def _raw_signal(self, grid: VoxelGrid) -> np.ndarray:
        density = grid.property_grid("density_kg_m3")
        is_void = grid.is_anomaly_grid().astype(float)
        contrast = local_contrast(density) + 40.0 * is_void

        raw = np.zeros_like(contrast)
        skin = min(grid.nz, max(1, int(round(0.4 / grid.voxel_size_m))))
        weight = np.linspace(1.0, 0.0, skin)
        for i, w in enumerate(weight):
            raw[:, :, i] = contrast[:, :, i:].max(axis=2) * w
        return raw

    def profile_2d(self, grid: VoxelGrid, volume: np.ndarray):
        return volume.max(axis=2), ("x", "y")
