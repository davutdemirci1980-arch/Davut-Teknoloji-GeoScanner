// Backendless implementation of the same `api` surface as client.ts, backed
// entirely by the in-browser TypeScript port of the simulation/AI engine
// (src/sim). Used by the standalone (no-server) build.
import { MATERIALS_LIST } from "../sim/materials";
import { SENSOR_REGISTRY } from "../sim/sensors/registry";
import { lab } from "../sim/lab";
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

function toMaterialOut(): Material[] {
  return MATERIALS_LIST.map((m) => ({
    id: m.id,
    key: m.key,
    name_tr: m.name_tr,
    name_en: m.name_en,
    color: m.color,
    is_anomaly: m.is_anomaly,
  }));
}

function toSensorInfo(): SensorInfo[] {
  return Object.values(SENSOR_REGISTRY).map((s) => ({
    key: s.key,
    name_tr: s.name_tr,
    name_en: s.name_en,
    description_tr: s.description_tr,
    icon: s.icon,
    max_effective_depth_m: s.max_effective_depth_m,
    base_reliability: s.base_reliability,
  }));
}

function sparseFromVolume(volume: Float64Array, shape: [number, number, number], minProbability: number) {
  const [nx, ny, nz] = shape;
  const voxels: { x: number; y: number; z: number; v: number }[] = [];
  for (let x = 0; x < nx; x++) {
    for (let y = 0; y < ny; y++) {
      for (let z = 0; z < nz; z++) {
        const v = volume[(x * ny + y) * nz + z];
        if (v >= minProbability) voxels.push({ x, y, z, v: Math.round(v * 10000) / 10000 });
      }
    }
  }
  return voxels;
}

export const localApi = {
  getMaterials: async (): Promise<Material[]> => toMaterialOut(),
  getSensors: async (): Promise<SensorInfo[]> => toSensorInfo(),

  createScenario: async (body: ScenarioCreateRequest): Promise<ScenarioCreateResponse> => {
    const session = lab.createScenario(body.nx, body.ny, body.nz, body.voxel_size_m, body.preset, body.num_anomalies, body.seed);
    return {
      session_id: session.id,
      shape: session.grid.shape,
      dimensions_m: session.grid.dimensionsM,
      voxel_size_m: session.grid.voxelSizeM,
      preset: session.preset,
      seed: session.seed,
    };
  },

  getVoxels: async (sessionId: string): Promise<VoxelGridResponse> => {
    const session = lab.get(sessionId);
    return {
      session_id: sessionId,
      shape: session.grid.shape,
      voxel_size_m: session.grid.voxelSizeM,
      voxels: session.grid.toSparseVoxelList(null),
      materials: toMaterialOut(),
    };
  },

  runSensors: async (
    sessionId: string,
    sensorKeys: string[] | null,
    noiseLevel: number,
    seed: number | null
  ): Promise<SensorRunResponse> => {
    const results = lab.runSensors(sessionId, sensorKeys, noiseLevel, seed);
    return {
      session_id: sessionId,
      results: Object.values(results).map((r) => ({
        sensor_key: r.sensorKey,
        stats: r.stats,
        profile_2d: r.profile2D.map((row) => row.map((v) => Math.round(v * 10000) / 10000)),
        profile_axes: r.profileAxes,
      })),
    };
  },

  getSensorVolume: async (sessionId: string, sensorKey: string, minProbability = 0.2): Promise<SparseVolumeResponse> => {
    const session = lab.get(sessionId);
    const result = session.sensorResults[sensorKey];
    if (!result) throw new Error(`Sensor '${sensorKey}' has not been run for this session yet`);
    return {
      session_id: sessionId,
      shape: session.grid.shape,
      voxel_size_m: session.grid.voxelSizeM,
      voxels: sparseFromVolume(result.probabilityVolume, session.grid.shape, minProbability),
    };
  },

  analyze: async (sessionId: string, minSizeVoxels = 2, thresholdPercentile = 92): Promise<AnalyzeResponse> => {
    const { fusion, anomalies } = lab.runAnalysis(sessionId, minSizeVoxels, thresholdPercentile);
    let sum = 0, lo = Infinity, hi = -Infinity;
    for (const v of fusion.fusedProbability) {
      sum += v;
      if (v < lo) lo = v;
      if (v > hi) hi = v;
    }
    const mean = sum / fusion.fusedProbability.length;
    let variance = 0;
    for (const v of fusion.fusedProbability) variance += (v - mean) ** 2;

    return {
      session_id: sessionId,
      sensor_weights: fusion.weights,
      fused_stats: { min: lo, max: hi, mean, std: Math.sqrt(variance / fusion.fusedProbability.length) },
      anomalies: anomalies.map((a) => ({
        id: a.id,
        centroid_vox: a.centroidVox,
        centroid_m: a.centroidM,
        size_voxels: a.sizeVoxels,
        volume_m3: Math.round(a.volumeM3 * 10000) / 10000,
        bbox_vox: a.bboxVox,
        fused_confidence: Math.round(a.fusedConfidence * 10000) / 10000,
        agreement_count: a.agreementCount,
        contributing_sensors: a.contributingSensors,
        predicted_material: a.predictedMaterial,
        predicted_material_name_tr: a.predictedMaterialNameTr,
        classification_confidence: Math.round(a.classificationConfidence * 10000) / 10000,
        signature: Object.fromEntries(Object.entries(a.signature).map(([k, v]) => [k, Math.round(v * 10000) / 10000])),
        material_scores: Object.fromEntries(Object.entries(a.materialScores).map(([k, v]) => [k, Math.round(v * 10000) / 10000])),
      })),
    };
  },

  getFusedVolume: async (sessionId: string, minProbability = 0.2): Promise<SparseVolumeResponse> => {
    const session = lab.get(sessionId);
    if (!session.fusion) throw new Error("Run analyze for this session first");
    return {
      session_id: sessionId,
      shape: session.grid.shape,
      voxel_size_m: session.grid.voxelSizeM,
      voxels: sparseFromVolume(session.fusion.fusedProbability, session.grid.shape, minProbability),
    };
  },
};
