import type { SensorSimulationResult } from "../sensors/base";
import { SENSOR_REGISTRY } from "../sensors/registry";

export interface FusionResult {
  fusedProbability: Float64Array;
  agreementCount: Int32Array;
  sensorKeys: string[];
  weights: Record<string, number>;
}

export function fuseSensorResults(results: Record<string, SensorSimulationResult>): FusionResult {
  const keys = Object.keys(results);
  if (keys.length === 0) throw new Error("At least one sensor result is required for fusion");

  const rawWeights = keys.map((k) => SENSOR_REGISTRY[k].base_reliability);
  const weightSum = rawWeights.reduce((a, b) => a + b, 0);
  const weights = rawWeights.map((w) => w / weightSum);

  const n = results[keys[0]].probabilityVolume.length;
  const fused = new Float64Array(n);
  const agreement = new Int32Array(n);

  for (let k = 0; k < keys.length; k++) {
    const vol = results[keys[k]].probabilityVolume;
    const w = weights[k];
    for (let i = 0; i < n; i++) {
      fused[i] += vol[i] * w;
      if (vol[i] > 0.5) agreement[i] += 1;
    }
  }

  const weightMap: Record<string, number> = {};
  keys.forEach((k, i) => (weightMap[k] = weights[i]));

  return { fusedProbability: fused, agreementCount: agreement, sensorKeys: keys, weights: weightMap };
}
