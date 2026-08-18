from __future__ import annotations

from pydantic import BaseModel, Field

from app.simulation.scenarios import ScenarioPreset


class MaterialOut(BaseModel):
    id: int
    key: str
    name_tr: str
    name_en: str
    color: str
    is_anomaly: bool


class SensorInfoOut(BaseModel):
    key: str
    name_tr: str
    name_en: str
    description_tr: str
    icon: str
    max_effective_depth_m: float
    base_reliability: float


class ScenarioCreateRequest(BaseModel):
    nx: int = Field(28, ge=6, le=60)
    ny: int = Field(28, ge=6, le=60)
    nz: int = Field(18, ge=4, le=40)
    voxel_size_m: float = Field(0.5, gt=0.05, le=2.0)
    preset: ScenarioPreset = "random"
    num_anomalies: int = Field(5, ge=0, le=20)
    seed: int | None = None


class ScenarioCreateResponse(BaseModel):
    session_id: str
    shape: tuple[int, int, int]
    dimensions_m: tuple[float, float, float]
    voxel_size_m: float
    preset: str
    seed: int | None


class VoxelOut(BaseModel):
    x: int
    y: int
    z: int
    m: int


class VoxelGridResponse(BaseModel):
    session_id: str
    shape: tuple[int, int, int]
    voxel_size_m: float
    voxels: list[VoxelOut]
    materials: list[MaterialOut]


class SensorRunRequest(BaseModel):
    sensor_keys: list[str] | None = None
    noise_level: float = Field(0.12, ge=0.0, le=1.0)
    seed: int | None = None


class SensorResultOut(BaseModel):
    sensor_key: str
    stats: dict[str, float]
    profile_2d: list[list[float]]
    profile_axes: tuple[str, str]


class SensorRunResponse(BaseModel):
    session_id: str
    results: list[SensorResultOut]


class SparseVoxelValue(BaseModel):
    x: int
    y: int
    z: int
    v: float


class SparseVolumeResponse(BaseModel):
    session_id: str
    shape: tuple[int, int, int]
    voxel_size_m: float
    voxels: list[SparseVoxelValue]


class AnalyzeRequest(BaseModel):
    min_size_voxels: int = Field(2, ge=1, le=50)
    threshold_percentile: float = Field(92.0, ge=50.0, le=99.9)


class AnomalyOut(BaseModel):
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
    signature: dict[str, float]
    material_scores: dict[str, float]


class AnalyzeResponse(BaseModel):
    session_id: str
    sensor_weights: dict[str, float]
    fused_stats: dict[str, float]
    anomalies: list[AnomalyOut]
