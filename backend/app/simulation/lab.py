"""In-memory simulation laboratory orchestrator.

A `LabSession` owns one voxel-grid scenario plus the most recent sensor
sweep and AI analysis run against it, so the frontend can regenerate the
scenario, re-run sensors with different selections/noise, and re-run
analysis independently without losing the underlying ground-truth model.
"""
from __future__ import annotations

import time
import uuid
from dataclasses import dataclass, field

import numpy as np

from app.ai.anomaly_detector import DetectedAnomaly, detect_anomalies
from app.ai.fusion import FusionResult, fuse_sensor_results
from app.core.voxel_engine import VoxelGrid
from app.sensors.base import SensorSimulationResult
from app.sensors.registry import ALL_SENSOR_KEYS, SENSOR_REGISTRY
from app.simulation.scenarios import ScenarioPreset, generate_scenario


@dataclass
class LabSession:
    id: str
    grid: VoxelGrid
    preset: str
    seed: int | None
    created_at: float = field(default_factory=time.time)
    sensor_results: dict[str, SensorSimulationResult] = field(default_factory=dict)
    fusion: FusionResult | None = None
    anomalies: list[DetectedAnomaly] = field(default_factory=list)


class SimulationLab:
    def __init__(self) -> None:
        self._sessions: dict[str, LabSession] = {}

    def create_scenario(
        self,
        nx: int = 28,
        ny: int = 28,
        nz: int = 18,
        voxel_size_m: float = 0.5,
        preset: ScenarioPreset = "random",
        num_anomalies: int = 5,
        seed: int | None = None,
    ) -> LabSession:
        grid = generate_scenario(nx, ny, nz, voxel_size_m, preset, num_anomalies, seed)
        session = LabSession(id=str(uuid.uuid4()), grid=grid, preset=preset, seed=seed)
        self._sessions[session.id] = session
        return session

    def get(self, session_id: str) -> LabSession:
        if session_id not in self._sessions:
            raise KeyError(f"Unknown lab session: {session_id}")
        return self._sessions[session_id]

    def run_sensors(
        self,
        session_id: str,
        sensor_keys: list[str] | None = None,
        noise_level: float = 0.12,
        seed: int | None = None,
    ) -> dict[str, SensorSimulationResult]:
        session = self.get(session_id)
        keys = sensor_keys or ALL_SENSOR_KEYS
        unknown = [k for k in keys if k not in SENSOR_REGISTRY]
        if unknown:
            raise ValueError(f"Unknown sensor types: {unknown}")

        rng = np.random.default_rng(seed)
        results: dict[str, SensorSimulationResult] = {}
        for key in keys:
            simulator = SENSOR_REGISTRY[key]
            results[key] = simulator.simulate(session.grid, rng, noise_level=noise_level)

        session.sensor_results = results
        session.fusion = None
        session.anomalies = []
        return results

    def run_analysis(
        self,
        session_id: str,
        min_size_voxels: int = 2,
        threshold_percentile: float = 92.0,
    ) -> tuple[FusionResult, list[DetectedAnomaly]]:
        session = self.get(session_id)
        if not session.sensor_results:
            raise ValueError("Run sensors before requesting analysis")

        fusion = fuse_sensor_results(session.sensor_results)
        anomalies = detect_anomalies(
            session.grid,
            session.sensor_results,
            fusion.fused_probability,
            fusion.agreement_count,
            min_size_voxels=min_size_voxels,
            threshold_percentile=threshold_percentile,
        )
        session.fusion = fusion
        session.anomalies = anomalies
        return fusion, anomalies

    def delete(self, session_id: str) -> None:
        self._sessions.pop(session_id, None)


lab = SimulationLab()
