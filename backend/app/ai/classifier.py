"""Signature-matching AI classifier.

Each anomaly cluster produces an "evidence vector": for every active sensor,
the sensor's own mean anomaly-probability inside the cluster region. This is
compared (cosine similarity) against reference multi-sensor signature
templates for each known buried-object type — a lightweight, explainable
nearest-template classification approach that never looks at the ground
truth material directly, only at what the simulated sensors themselves
reported.
"""
from __future__ import annotations

import numpy as np

from app.core.materials import ANOMALY_MATERIAL_KEYS

# Expected relative sensor response strength (0-1) for each buried-object
# type, derived from the physical reasoning behind each sensor simulator
# (e.g. metal is highly conductive & magnetic; voids are dielectric/resistive
# extremes with negative density contrast; PVC is invisible to magnetics/EMI).
SIGNATURE_TEMPLATES: dict[str, dict[str, float]] = {
    "metal_pipe": {
        "gpr": 0.9, "magnetometer": 0.95, "resistivity": 0.6, "seismic": 0.5,
        "em_induction": 0.95, "thermal": 0.3, "gravimetric": 0.4, "lidar": 0.2,
    },
    "pvc_pipe": {
        "gpr": 0.8, "magnetometer": 0.05, "resistivity": 0.7, "seismic": 0.3,
        "em_induction": 0.1, "thermal": 0.2, "gravimetric": 0.2, "lidar": 0.15,
    },
    "void": {
        "gpr": 0.85, "magnetometer": 0.05, "resistivity": 0.9, "seismic": 0.6,
        "em_induction": 0.2, "thermal": 0.4, "gravimetric": 0.75, "lidar": 0.5,
    },
    "concrete": {
        "gpr": 0.6, "magnetometer": 0.15, "resistivity": 0.5, "seismic": 0.65,
        "em_induction": 0.25, "thermal": 0.35, "gravimetric": 0.5, "lidar": 0.35,
    },
    "wood": {
        "gpr": 0.45, "magnetometer": 0.02, "resistivity": 0.4, "seismic": 0.3,
        "em_induction": 0.1, "thermal": 0.25, "gravimetric": 0.25, "lidar": 0.2,
    },
    "archaeological_stone": {
        "gpr": 0.55, "magnetometer": 0.2, "resistivity": 0.5, "seismic": 0.55,
        "em_induction": 0.25, "thermal": 0.3, "gravimetric": 0.45, "lidar": 0.3,
    },
}

assert set(SIGNATURE_TEMPLATES.keys()) == set(ANOMALY_MATERIAL_KEYS)


def classify_evidence(evidence_vector: dict[str, float]) -> tuple[str, float, dict[str, float]]:
    """Return (predicted_material_key, confidence_0_1, per_material_scores)."""
    scores: dict[str, float] = {}
    for material_key, template in SIGNATURE_TEMPLATES.items():
        common_keys = [k for k in template if k in evidence_vector]
        if not common_keys:
            scores[material_key] = 0.0
            continue
        v1 = np.array([evidence_vector[k] for k in common_keys])
        v2 = np.array([template[k] for k in common_keys])
        denom = float(np.linalg.norm(v1) * np.linalg.norm(v2))
        scores[material_key] = float(v1 @ v2 / denom) if denom > 1e-9 else 0.0

    best = max(scores, key=lambda k: scores[k])

    # Softmax over similarity scores (temperature-sharpened) turns "which
    # template matches best" into a proper probability distribution, so a
    # clearly-dominant match reads as high confidence and a close call reads
    # as low confidence, instead of every prediction clustering near 1/N.
    temperature = 20.0
    values = np.array(list(scores.values()))
    exp_scores = np.exp(temperature * (values - values.max()))
    probabilities = exp_scores / exp_scores.sum()
    confidence = float(probabilities[list(scores.keys()).index(best)])

    return best, confidence, scores
