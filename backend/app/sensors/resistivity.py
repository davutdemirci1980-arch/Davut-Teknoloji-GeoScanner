from __future__ import annotations

import numpy as np
from scipy.ndimage import gaussian_filter

from app.core.voxel_engine import VoxelGrid
from app.sensors.base import BaseSensorSimulator, log_local_contrast, power_law_attenuation


class ResistivitySimulator(BaseSensorSimulator):
    key = "resistivity"
    name_tr = "Elektriksel Özdirenç Tomografisi (ERT)"
    name_en = "Electrical Resistivity Tomography"
    description_tr = "Zemine akım enjekte ederek özdirenç dağılımından yeraltı katmanlarını ve boşlukları haritalar."
    icon = "ert"
    max_effective_depth_m = 6.0
    base_reliability = 0.82

    def _raw_signal(self, grid: VoxelGrid) -> np.ndarray:
        resistivity = grid.property_grid("resistivity_ohm_m")
        contrast = log_local_contrast(resistivity)

        # ERT loses horizontal resolution with depth; blur each depth slice
        # progressively more to mimic the electrode-array geometric factor.
        smoothed = np.empty_like(contrast)
        for z in range(grid.nz):
            sigma = 0.3 + 0.18 * z
            smoothed[:, :, z] = gaussian_filter(contrast[:, :, z], sigma=sigma)

        attenuation = power_law_attenuation(grid, effective_depth_m=self.max_effective_depth_m, exponent=2.0)
        return smoothed * attenuation[None, None, :]
