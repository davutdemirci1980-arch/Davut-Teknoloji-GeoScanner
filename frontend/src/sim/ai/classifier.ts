import { ANOMALY_MATERIAL_KEYS } from "../materials";

export const SIGNATURE_TEMPLATES: Record<string, Record<string, number>> = {
  metal_pipe: { gpr: 0.9, magnetometer: 0.95, resistivity: 0.6, seismic: 0.5, em_induction: 0.95, thermal: 0.3, gravimetric: 0.4, lidar: 0.2 },
  pvc_pipe: { gpr: 0.8, magnetometer: 0.05, resistivity: 0.7, seismic: 0.3, em_induction: 0.1, thermal: 0.2, gravimetric: 0.2, lidar: 0.15 },
  void: { gpr: 0.85, magnetometer: 0.05, resistivity: 0.9, seismic: 0.6, em_induction: 0.2, thermal: 0.4, gravimetric: 0.75, lidar: 0.5 },
  concrete: { gpr: 0.6, magnetometer: 0.15, resistivity: 0.5, seismic: 0.65, em_induction: 0.25, thermal: 0.35, gravimetric: 0.5, lidar: 0.35 },
  wood: { gpr: 0.45, magnetometer: 0.02, resistivity: 0.4, seismic: 0.3, em_induction: 0.1, thermal: 0.25, gravimetric: 0.25, lidar: 0.2 },
  archaeological_stone: { gpr: 0.55, magnetometer: 0.2, resistivity: 0.5, seismic: 0.55, em_induction: 0.25, thermal: 0.3, gravimetric: 0.45, lidar: 0.3 },
};

const templateKeys = Object.keys(SIGNATURE_TEMPLATES);
console.assert(
  templateKeys.length === ANOMALY_MATERIAL_KEYS.length && templateKeys.every((k) => ANOMALY_MATERIAL_KEYS.includes(k)),
  "signature templates must match anomaly material catalog"
);

export function classifyEvidence(evidenceVector: Record<string, number>): {
  predicted: string;
  confidence: number;
  scores: Record<string, number>;
} {
  const scores: Record<string, number> = {};
  for (const [materialKey, template] of Object.entries(SIGNATURE_TEMPLATES)) {
    const commonKeys = Object.keys(template).filter((k) => k in evidenceVector);
    if (commonKeys.length === 0) {
      scores[materialKey] = 0;
      continue;
    }
    let dot = 0;
    let n1 = 0;
    let n2 = 0;
    for (const k of commonKeys) {
      const v1 = evidenceVector[k];
      const v2 = template[k];
      dot += v1 * v2;
      n1 += v1 * v1;
      n2 += v2 * v2;
    }
    const denom = Math.sqrt(n1) * Math.sqrt(n2);
    scores[materialKey] = denom > 1e-9 ? dot / denom : 0;
  }

  let best = templateKeys[0];
  for (const k of templateKeys) if (scores[k] > scores[best]) best = k;

  const temperature = 20.0;
  const values = templateKeys.map((k) => scores[k]);
  const maxVal = Math.max(...values);
  const expScores = values.map((v) => Math.exp(temperature * (v - maxVal)));
  const expSum = expScores.reduce((a, b) => a + b, 0);
  const confidence = expScores[templateKeys.indexOf(best)] / expSum;

  return { predicted: best, confidence, scores };
}
