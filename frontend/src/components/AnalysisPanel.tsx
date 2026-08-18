import { useState } from "react";
import type { AnalyzeResponse } from "../types";

interface Props {
  onAnalyze: (minSizeVoxels: number, thresholdPercentile: number) => void;
  busy: boolean;
  disabled: boolean;
  result: AnalyzeResponse | null;
}

export default function AnalysisPanel({ onAnalyze, busy, disabled, result }: Props) {
  const [threshold, setThreshold] = useState(96);
  const [minSize, setMinSize] = useState(3);

  return (
    <div className="panel">
      <h2>3. Yapay Zeka Füzyon &amp; Analiz</h2>
      <p className="panel-hint">Tüm sensörlerin voksel olasılık hacimlerini ağırlıklı birleştirir, kümeler ve sınıflandırır.</p>

      <label>
        Anomali Eşiği (persentil): {threshold}
        <input type="range" min={50} max={99} value={threshold} onChange={(e) => setThreshold(Number(e.target.value))} />
      </label>
      <label>
        Min. Küme Boyutu (voksel): {minSize}
        <input type="range" min={1} max={20} value={minSize} onChange={(e) => setMinSize(Number(e.target.value))} />
      </label>

      <button className="primary-btn" disabled={busy || disabled} onClick={() => onAnalyze(minSize, threshold)}>
        {busy ? "Analiz Ediliyor..." : "AI Analiz Çalıştır"}
      </button>

      {result && (
        <div className="analysis-results">
          <p className="panel-hint">
            {result.anomalies.length} anomali tespit edildi · füzyon ort {result.fused_stats.mean.toFixed(3)}
          </p>
          <div className="anomaly-list">
            {result.anomalies.map((a) => (
              <div key={a.id} className="anomaly-card">
                <div className="anomaly-card-header">
                  <span className="anomaly-id">#{a.id}</span>
                  <span className="anomaly-material">{a.predicted_material_name_tr}</span>
                </div>
                <div className="anomaly-metrics">
                  <span>Güven: {(a.classification_confidence * 100).toFixed(0)}%</span>
                  <span>Sinyal: {(a.fused_confidence * 100).toFixed(0)}%</span>
                  <span>Hacim: {a.volume_m3.toFixed(2)} m³</span>
                  <span>Derinlik: {a.centroid_m[2].toFixed(2)} m</span>
                </div>
                <div className="anomaly-sensors">
                  {a.contributing_sensors.length > 0
                    ? `Doğrulayan sensörler: ${a.contributing_sensors.join(", ")}`
                    : "Tek sensör altında zayıf sinyal"}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
