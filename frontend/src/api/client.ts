import type {
  AnalyzeResponse,
  Material,
  ScenarioCreateRequest,
  ScenarioCreateResponse,
  SensorInfo,
  SensorRunResponse,
  SparseVolumeResponse,
  VoxelGridResponse,
} from "../types";

const BASE = "/api";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!res.ok) {
    const detail = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${detail}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  getMaterials: () => request<Material[]>("/materials"),
  getSensors: () => request<SensorInfo[]>("/sensors"),

  createScenario: (body: ScenarioCreateRequest) =>
    request<ScenarioCreateResponse>("/scenarios", { method: "POST", body: JSON.stringify(body) }),

  getVoxels: (sessionId: string, stride = 1) =>
    request<VoxelGridResponse>(`/scenarios/${sessionId}/voxels?stride=${stride}`),

  runSensors: (sessionId: string, sensorKeys: string[] | null, noiseLevel: number, seed: number | null) =>
    request<SensorRunResponse>(`/scenarios/${sessionId}/sensors/run`, {
      method: "POST",
      body: JSON.stringify({ sensor_keys: sensorKeys, noise_level: noiseLevel, seed }),
    }),

  getSensorVolume: (sessionId: string, sensorKey: string, minProbability = 0.2) =>
    request<SparseVolumeResponse>(
      `/scenarios/${sessionId}/sensors/${sensorKey}/volume?min_probability=${minProbability}`
    ),

  analyze: (sessionId: string, minSizeVoxels = 2, thresholdPercentile = 92) =>
    request<AnalyzeResponse>(`/scenarios/${sessionId}/analyze`, {
      method: "POST",
      body: JSON.stringify({ min_size_voxels: minSizeVoxels, threshold_percentile: thresholdPercentile }),
    }),

  getFusedVolume: (sessionId: string, minProbability = 0.2) =>
    request<SparseVolumeResponse>(
      `/scenarios/${sessionId}/analysis/fused_volume?min_probability=${minProbability}`
    ),
};
