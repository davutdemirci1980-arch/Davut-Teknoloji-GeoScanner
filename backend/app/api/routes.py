from __future__ import annotations

import numpy as np
from fastapi import APIRouter, HTTPException

from app.core.materials import MATERIALS
from app.models.schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    AnomalyOut,
    MaterialOut,
    ScenarioCreateRequest,
    ScenarioCreateResponse,
    SensorInfoOut,
    SensorResultOut,
    SensorRunRequest,
    SensorRunResponse,
    SparseVolumeResponse,
    SparseVoxelValue,
    VoxelGridResponse,
    VoxelOut,
)
from app.sensors.registry import SENSOR_REGISTRY
from app.simulation.lab import lab

router = APIRouter(prefix="/api")


@router.get("/materials", response_model=list[MaterialOut])
def get_materials() -> list[MaterialOut]:
    return [
        MaterialOut(id=m.id, key=m.key, name_tr=m.name_tr, name_en=m.name_en, color=m.color, is_anomaly=m.is_anomaly)
        for m in MATERIALS.values()
    ]


@router.get("/sensors", response_model=list[SensorInfoOut])
def get_sensors() -> list[SensorInfoOut]:
    return [
        SensorInfoOut(
            key=s.key,
            name_tr=s.name_tr,
            name_en=s.name_en,
            description_tr=s.description_tr,
            icon=s.icon,
            max_effective_depth_m=s.max_effective_depth_m,
            base_reliability=s.base_reliability,
        )
        for s in SENSOR_REGISTRY.values()
    ]


@router.post("/scenarios", response_model=ScenarioCreateResponse)
def create_scenario(req: ScenarioCreateRequest) -> ScenarioCreateResponse:
    session = lab.create_scenario(
        nx=req.nx, ny=req.ny, nz=req.nz, voxel_size_m=req.voxel_size_m,
        preset=req.preset, num_anomalies=req.num_anomalies, seed=req.seed,
    )
    return ScenarioCreateResponse(
        session_id=session.id,
        shape=session.grid.shape,
        dimensions_m=session.grid.dimensions_m,
        voxel_size_m=session.grid.voxel_size_m,
        preset=session.preset,
        seed=session.seed,
    )


@router.get("/scenarios/{session_id}/voxels", response_model=VoxelGridResponse)
def get_voxels(session_id: str, stride: int = 1) -> VoxelGridResponse:
    session = _get_session_or_404(session_id)
    voxels = session.grid.to_sparse_voxel_list(skip_material_key=None, stride=max(1, stride))
    return VoxelGridResponse(
        session_id=session_id,
        shape=session.grid.shape,
        voxel_size_m=session.grid.voxel_size_m,
        voxels=[VoxelOut(**v) for v in voxels],
        materials=[
            MaterialOut(id=m.id, key=m.key, name_tr=m.name_tr, name_en=m.name_en, color=m.color, is_anomaly=m.is_anomaly)
            for m in MATERIALS.values()
        ],
    )


@router.post("/scenarios/{session_id}/sensors/run", response_model=SensorRunResponse)
def run_sensors(session_id: str, req: SensorRunRequest) -> SensorRunResponse:
    _get_session_or_404(session_id)
    try:
        results = lab.run_sensors(session_id, sensor_keys=req.sensor_keys, noise_level=req.noise_level, seed=req.seed)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return SensorRunResponse(
        session_id=session_id,
        results=[
            SensorResultOut(
                sensor_key=r.sensor_key,
                stats=r.stats,
                profile_2d=np.round(r.profile_2d, 4).tolist(),
                profile_axes=r.profile_axes,
            )
            for r in results.values()
        ],
    )


@router.get("/scenarios/{session_id}/sensors/{sensor_key}/volume", response_model=SparseVolumeResponse)
def get_sensor_volume(session_id: str, sensor_key: str, min_probability: float = 0.2, stride: int = 1) -> SparseVolumeResponse:
    session = _get_session_or_404(session_id)
    if sensor_key not in session.sensor_results:
        raise HTTPException(status_code=404, detail=f"Sensor '{sensor_key}' has not been run for this session yet")

    volume = session.sensor_results[sensor_key].probability_volume[::stride, ::stride, ::stride]
    voxels = _sparse_from_volume(volume, min_probability)
    return SparseVolumeResponse(
        session_id=session_id,
        shape=volume.shape,
        voxel_size_m=session.grid.voxel_size_m * max(1, stride),
        voxels=voxels,
    )


@router.post("/scenarios/{session_id}/analyze", response_model=AnalyzeResponse)
def analyze(session_id: str, req: AnalyzeRequest) -> AnalyzeResponse:
    _get_session_or_404(session_id)
    try:
        fusion, anomalies = lab.run_analysis(
            session_id, min_size_voxels=req.min_size_voxels, threshold_percentile=req.threshold_percentile
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    fused_stats = {
        "min": float(fusion.fused_probability.min()),
        "max": float(fusion.fused_probability.max()),
        "mean": float(fusion.fused_probability.mean()),
        "std": float(fusion.fused_probability.std()),
    }
    return AnalyzeResponse(
        session_id=session_id,
        sensor_weights=fusion.weights,
        fused_stats=fused_stats,
        anomalies=[
            AnomalyOut(
                id=a.id, centroid_vox=a.centroid_vox, centroid_m=a.centroid_m, size_voxels=a.size_voxels,
                volume_m3=round(a.volume_m3, 4), bbox_vox=a.bbox_vox, fused_confidence=round(a.fused_confidence, 4),
                agreement_count=a.agreement_count, contributing_sensors=a.contributing_sensors,
                predicted_material=a.predicted_material, predicted_material_name_tr=a.predicted_material_name_tr,
                classification_confidence=round(a.classification_confidence, 4),
                signature={k: round(v, 4) for k, v in a.signature.items()},
                material_scores={k: round(v, 4) for k, v in a.material_scores.items()},
            )
            for a in anomalies
        ],
    )


@router.get("/scenarios/{session_id}/analysis/fused_volume", response_model=SparseVolumeResponse)
def get_fused_volume(session_id: str, min_probability: float = 0.2, stride: int = 1) -> SparseVolumeResponse:
    session = _get_session_or_404(session_id)
    if session.fusion is None:
        raise HTTPException(status_code=404, detail="Run /analyze for this session first")

    volume = session.fusion.fused_probability[::stride, ::stride, ::stride]
    voxels = _sparse_from_volume(volume, min_probability)
    return SparseVolumeResponse(
        session_id=session_id,
        shape=volume.shape,
        voxel_size_m=session.grid.voxel_size_m * max(1, stride),
        voxels=voxels,
    )


def _sparse_from_volume(volume: np.ndarray, min_value: float) -> list[SparseVoxelValue]:
    coords = np.argwhere(volume >= min_value)
    return [
        SparseVoxelValue(x=int(x), y=int(y), z=int(z), v=round(float(volume[x, y, z]), 4))
        for x, y, z in coords
    ]


def _get_session_or_404(session_id: str):
    try:
        return lab.get(session_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
