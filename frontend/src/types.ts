export interface Material {
  id: number;
  key: string;
  name_tr: string;
  name_en: string;
  color: string;
  is_anomaly: boolean;
}

export interface SensorInfo {
  key: string;
  name_tr: string;
  name_en: string;
  description_tr: string;
  icon: string;
  max_effective_depth_m: number;
  base_reliability: number;
}

export type ScenarioPreset = "random" | "utility_lines" | "archaeology" | "geology_only";

export interface ScenarioCreateRequest {
  nx: number;
  ny: number;
  nz: number;
  voxel_size_m: number;
  preset: ScenarioPreset;
  num_anomalies: number;
  seed: number | null;
}

export interface ScenarioCreateResponse {
  session_id: string;
  shape: [number, number, number];
  dimensions_m: [number, number, number];
  voxel_size_m: number;
  preset: string;
  seed: number | null;
}

export interface VoxelOut {
  x: number;
  y: number;
  z: number;
  m: number;
}

export interface VoxelGridResponse {
  session_id: string;
  shape: [number, number, number];
  voxel_size_m: number;
  voxels: VoxelOut[];
  materials: Material[];
}

export interface SensorResultOut {
  sensor_key: string;
  stats: { min: number; max: number; mean: number; std: number };
  profile_2d: number[][];
  profile_axes: [string, string];
}

export interface SensorRunResponse {
  session_id: string;
  results: SensorResultOut[];
}

export interface SparseVoxelValue {
  x: number;
  y: number;
  z: number;
  v: number;
}

export interface SparseVolumeResponse {
  session_id: string;
  shape: [number, number, number];
  voxel_size_m: number;
  voxels: SparseVoxelValue[];
}

export interface AnomalyOut {
  id: number;
  centroid_vox: [number, number, number];
  centroid_m: [number, number, number];
  size_voxels: number;
  volume_m3: number;
  bbox_vox: [[number, number], [number, number], [number, number]];
  fused_confidence: number;
  agreement_count: number;
  contributing_sensors: string[];
  predicted_material: string;
  predicted_material_name_tr: string;
  classification_confidence: number;
  signature: Record<string, number>;
  material_scores: Record<string, number>;
}

export interface AnalyzeResponse {
  session_id: string;
  sensor_weights: Record<string, number>;
  fused_stats: { min: number; max: number; mean: number; std: number };
  anomalies: AnomalyOut[];
}
