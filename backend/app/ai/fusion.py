"""Multi-sensor data fusion.

Combines every active sensor's per-voxel anomaly-probability volume into a
single fused probability volume, weighting each sensor by its typical
reliability. Also tracks a per-voxel "agreement count" — how many sensors
independently flagged that voxel — which is a strong, cheap confidence
signal in real multi-sensor geophysical surveys.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from app.sensors.base import SensorSimulationResult
from app.sensors.registry import SENSOR_REGISTRY


@dataclass
class FusionResult:
    fused_probability: np.ndarray
    agreement_count: np.ndarray
    sensor_keys: list[str]
    weights: dict[str, float]


def fuse_sensor_results(results: dict[str, SensorSimulationResult]) -> FusionResult:
    if not results:
        raise ValueError("At least one sensor result is required for fusion")

    keys = list(results.keys())
    volumes = np.stack([results[k].probability_volume for k in keys], axis=0)
    raw_weights = np.array([SENSOR_REGISTRY[k].base_reliability for k in keys])
    weights = raw_weights / raw_weights.sum()

    fused = np.tensordot(weights, volumes, axes=([0], [0]))
    agreement = (volumes > 0.5).sum(axis=0)

    return FusionResult(
        fused_probability=fused,
        agreement_count=agreement,
        sensor_keys=keys,
        weights=dict(zip(keys, weights.tolist())),
    )
