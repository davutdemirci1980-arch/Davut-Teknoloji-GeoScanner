import { VoxelGrid } from "./voxelEngine";
import { generateScenario, type ScenarioPreset } from "./scenarios";
import { SENSOR_REGISTRY, ALL_SENSOR_KEYS } from "./sensors/registry";
import { simulateSensor, type SensorSimulationResult } from "./sensors/base";
import { Rng } from "./rng";
import { fuseSensorResults, type FusionResult } from "./ai/fusion";
import { detectAnomalies, type DetectedAnomaly } from "./ai/anomalyDetector";

let sessionCounter = 0;

export interface LabSession {
  id: string;
  grid: VoxelGrid;
  preset: string;
  seed: number | null;
  sensorResults: Record<string, SensorSimulationResult>;
  fusion: FusionResult | null;
  anomalies: DetectedAnomaly[];
}

class SimulationLab {
  private sessions = new Map<string, LabSession>();

  createScenario(
    nx = 28,
    ny = 28,
    nz = 18,
    voxelSizeM = 0.5,
    preset: ScenarioPreset = "random",
    numAnomalies = 5,
    seed: number | null = null
  ): LabSession {
    const grid = generateScenario(nx, ny, nz, voxelSizeM, preset, numAnomalies, seed);
    const id = `local-${++sessionCounter}-${Date.now()}`;
    const session: LabSession = { id, grid, preset, seed, sensorResults: {}, fusion: null, anomalies: [] };
    this.sessions.set(id, session);
    return session;
  }

  get(sessionId: string): LabSession {
    const session = this.sessions.get(sessionId);
    if (!session) throw new Error(`Unknown lab session: ${sessionId}`);
    return session;
  }

  runSensors(
    sessionId: string,
    sensorKeys: string[] | null,
    noiseLevel = 0.12,
    seed: number | null = null
  ): Record<string, SensorSimulationResult> {
    const session = this.get(sessionId);
    const keys = sensorKeys && sensorKeys.length > 0 ? sensorKeys : ALL_SENSOR_KEYS;
    const unknown = keys.filter((k) => !(k in SENSOR_REGISTRY));
    if (unknown.length > 0) throw new Error(`Unknown sensor types: ${unknown.join(", ")}`);

    const rng = new Rng(seed);
    const results: Record<string, SensorSimulationResult> = {};
    for (const key of keys) {
      results[key] = simulateSensor(SENSOR_REGISTRY[key], session.grid, rng, noiseLevel);
    }
    session.sensorResults = results;
    session.fusion = null;
    session.anomalies = [];
    return results;
  }

  runAnalysis(sessionId: string, minSizeVoxels = 2, thresholdPercentile = 92.0): { fusion: FusionResult; anomalies: DetectedAnomaly[] } {
    const session = this.get(sessionId);
    if (Object.keys(session.sensorResults).length === 0) throw new Error("Run sensors before requesting analysis");

    const fusion = fuseSensorResults(session.sensorResults);
    const anomalies = detectAnomalies(
      session.grid,
      session.sensorResults,
      fusion.fusedProbability,
      fusion.agreementCount,
      minSizeVoxels,
      thresholdPercentile
    );
    session.fusion = fusion;
    session.anomalies = anomalies;
    return { fusion, anomalies };
  }
}

export const lab = new SimulationLab();
