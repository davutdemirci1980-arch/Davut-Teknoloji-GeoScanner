"""Voxel-cluster anomaly detection on top of the fused probability volume."""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
from scipy import ndimage

from app.ai.classifier import classify_evidence
from app.core.materials import MATERIALS
from app.core.voxel_engine import VoxelGrid
from app.sensors.base import SensorSimulationResult

_CONNECTIVITY_26 = np.ones((3, 3, 3), dtype=int)


@dataclass
class DetectedAnomaly:
    id: int
    centroid_vox: tuple[float, float, float]
    centroid_m: tuple[float, float, float]
    size_voxels: int
    volume_m3: float
    bbox_vox: tuple[tuple[int, int], tuple[int, int], tuple[int, int]]
    fused_confidence: float
    agreement_count: int
    contributing_sensors: list[str]
    predicted_material: str
    predicted_material_name_tr: str
    classification_confidence: float
    signature: dict[str, float] = field(default_factory=dict)
    material_scores: dict[str, float] = field(default_factory=dict)


def detect_anomalies(
    grid: VoxelGrid,
    sensor_results: dict[str, SensorSimulationResult],
    fused_probability: np.ndarray,
    agreement_count: np.ndarray,
    min_size_voxels: int = 2,
    threshold_percentile: float = 92.0,
) -> list[DetectedAnomaly]:
    threshold = max(
        float(np.percentile(fused_probability, threshold_percentile)),
        float(fused_probability.mean() + fused_probability.std()),
    )
    mask = fused_probability >= threshold
    labeled, num_components = ndimage.label(mask, structure=_CONNECTIVITY_26)

    anomalies: list[DetectedAnomaly] = []
    for label_id in range(1, num_components + 1):
        component_mask = labeled == label_id
        size = int(component_mask.sum())
        if size < min_size_voxels:
            continue

        coords = np.argwhere(component_mask)
        centroid_vox = coords.mean(axis=0)
        bbox = tuple((int(coords[:, i].min()), int(coords[:, i].max())) for i in range(3))
        fused_conf = float(fused_probability[component_mask].mean())
        agreement = int(round(float(agreement_count[component_mask].mean())))

        evidence_vector: dict[str, float] = {}
        contributing: list[str] = []
        for key, result in sensor_results.items():
            value = float(result.probability_volume[component_mask].mean())
            evidence_vector[key] = value
            if value > 0.5:
                contributing.append(key)

        predicted_material, class_conf, scores = classify_evidence(evidence_vector)
        material = MATERIALS[predicted_material]

        anomalies.append(
            DetectedAnomaly(
                id=0,
                centroid_vox=tuple(float(c) for c in centroid_vox),
                centroid_m=tuple(float(c) * grid.voxel_size_m for c in centroid_vox),
                size_voxels=size,
                volume_m3=size * grid.voxel_size_m**3,
                bbox_vox=bbox,
                fused_confidence=fused_conf,
                agreement_count=agreement,
                contributing_sensors=contributing,
                predicted_material=predicted_material,
                predicted_material_name_tr=material.name_tr,
                classification_confidence=class_conf,
                signature=evidence_vector,
                material_scores=scores,
            )
        )

    anomalies.sort(key=lambda a: a.fused_confidence, reverse=True)
    for index, anomaly in enumerate(anomalies):
        anomaly.id = index + 1
    return anomalies
