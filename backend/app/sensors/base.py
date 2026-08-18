"""Shared sensor simulation infrastructure.

Every sensor simulator turns the physical property fields stored in a
VoxelGrid into two things:

1. `probability_volume` — an (nx, ny, nz) array in [0, 1], the sensor's own
   estimate of "how anomalous is this voxel", computed from real per-voxel
   physical property contrast plus a sensor-specific depth sensitivity model
   and measurement noise. This is what the AI fusion engine consumes.
2. `profile_2d` — a representative 2D readout (a vertical section for
   penetrating sensors like GPR/seismic/ERT, or a surface map for potential-
   field / near-surface sensors) used purely for visualization.
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass

import numpy as np

from app.core.voxel_engine import VoxelGrid


@dataclass
class SensorSimulationResult:
    sensor_key: str
    probability_volume: np.ndarray
    profile_2d: np.ndarray
    profile_axes: tuple[str, str]
    stats: dict[str, float]


class BaseSensorSimulator(ABC):
    key: str
    name_tr: str
    name_en: str
    description_tr: str
    icon: str
    max_effective_depth_m: float
    base_reliability: float = 0.8

    @abstractmethod
    def _raw_signal(self, grid: VoxelGrid) -> np.ndarray:
        """Return an unnormalized (nx, ny, nz) anomaly-strength field."""
        raise NotImplementedError

    def profile_2d(self, grid: VoxelGrid, volume: np.ndarray) -> tuple[np.ndarray, tuple[str, str]]:
        """Default: vertical cross-section through the middle of Y."""
        mid_y = grid.ny // 2
        return volume[:, mid_y, :], ("x", "z")

    def simulate(self, grid: VoxelGrid, rng: np.random.Generator, noise_level: float = 0.12) -> SensorSimulationResult:
        raw = self._raw_signal(grid)
        raw = raw + rng.normal(0.0, max(1e-6, noise_level) * (np.std(raw) + 1e-6), size=raw.shape)
        volume = normalize01(raw)
        profile, axes = self.profile_2d(grid, volume)
        stats = {
            "min": float(volume.min()),
            "max": float(volume.max()),
            "mean": float(volume.mean()),
            "std": float(volume.std()),
        }
        return SensorSimulationResult(self.key, volume, profile, axes, stats)


def normalize01(arr: np.ndarray) -> np.ndarray:
    lo, hi = float(np.min(arr)), float(np.max(arr))
    if hi - lo < 1e-9:
        return np.zeros_like(arr)
    return (arr - lo) / (hi - lo)


def local_contrast(field: np.ndarray) -> np.ndarray:
    """Deviation of each voxel from the median of its own depth (z) slice.

    This approximates "background subtraction": geology varies gradually
    layer by layer, so a per-depth median is a good stand-in for the
    expected background value a real survey would baseline against.
    """
    medians = np.median(field, axis=(0, 1), keepdims=True)
    return np.abs(field - medians)


def log_local_contrast(field: np.ndarray) -> np.ndarray:
    safe = np.clip(field, 1e-9, None)
    return local_contrast(np.log10(safe))


def depth_axis_m(grid: VoxelGrid) -> np.ndarray:
    return (np.arange(grid.nz) + 0.5) * grid.voxel_size_m


def power_law_attenuation(grid: VoxelGrid, effective_depth_m: float, exponent: float) -> np.ndarray:
    depths = depth_axis_m(grid)
    return 1.0 / (1.0 + depths / effective_depth_m) ** exponent


def surface_skin_weight(grid: VoxelGrid, skin_depth_m: float) -> np.ndarray:
    depths = depth_axis_m(grid)
    return np.exp(-depths / skin_depth_m)


def em_response_weight(grid: VoxelGrid, peak_depth_m: float) -> np.ndarray:
    """McNeill-style EM induction cumulative response shape: rises then decays."""
    depths = depth_axis_m(grid)
    x = depths / peak_depth_m
    w = x * np.exp(1.0 - x)
    return np.clip(w, 0.0, None)
