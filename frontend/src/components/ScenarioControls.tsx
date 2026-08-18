import { useState } from "react";
import type { ScenarioCreateRequest, ScenarioPreset } from "../types";

interface Props {
  onCreate: (req: ScenarioCreateRequest) => void;
  busy: boolean;
}

const PRESETS: { value: ScenarioPreset; label: string }[] = [
  { value: "random", label: "Karma (rastgele nesneler)" },
  { value: "utility_lines", label: "Altyapı Hatları (boru/kablo)" },
  { value: "archaeology", label: "Arkeolojik Alan" },
  { value: "geology_only", label: "Sadece Jeoloji (anomalisiz)" },
];

export default function ScenarioControls({ onCreate, busy }: Props) {
  const [nx, setNx] = useState(28);
  const [ny, setNy] = useState(28);
  const [nz, setNz] = useState(18);
  const [voxelSize, setVoxelSize] = useState(0.5);
  const [preset, setPreset] = useState<ScenarioPreset>("random");
  const [numAnomalies, setNumAnomalies] = useState(5);
  const [seed, setSeed] = useState<string>("");

  const dimsM = `${(nx * voxelSize).toFixed(1)} x ${(ny * voxelSize).toFixed(1)} x ${(nz * voxelSize).toFixed(1)} m`;

  return (
    <div className="panel">
      <h2>1. Voksel Simülasyon Laboratuvarı</h2>
      <p className="panel-hint">Tam voksel tabanlı sentetik yeraltı sahnesi üretir: katmanlı jeoloji + gömülü nesneler.</p>

      <label>
        Senaryo Türü
        <select value={preset} onChange={(e) => setPreset(e.target.value as ScenarioPreset)}>
          {PRESETS.map((p) => (
            <option key={p.value} value={p.value}>
              {p.label}
            </option>
          ))}
        </select>
      </label>

      <div className="grid-3">
        <label>
          X (voksel)
          <input type="number" min={6} max={60} value={nx} onChange={(e) => setNx(Number(e.target.value))} />
        </label>
        <label>
          Y (voksel)
          <input type="number" min={6} max={60} value={ny} onChange={(e) => setNy(Number(e.target.value))} />
        </label>
        <label>
          Derinlik (voksel)
          <input type="number" min={4} max={40} value={nz} onChange={(e) => setNz(Number(e.target.value))} />
        </label>
      </div>

      <label>
        Voksel Boyutu (m): {voxelSize.toFixed(2)}
        <input
          type="range"
          min={0.1}
          max={1.0}
          step={0.05}
          value={voxelSize}
          onChange={(e) => setVoxelSize(Number(e.target.value))}
        />
      </label>

      <label>
        Gömülü Nesne Sayısı: {numAnomalies}
        <input
          type="range"
          min={0}
          max={20}
          value={numAnomalies}
          onChange={(e) => setNumAnomalies(Number(e.target.value))}
        />
      </label>

      <label>
        Rastgele Tohum (opsiyonel)
        <input type="text" placeholder="boş = rastgele" value={seed} onChange={(e) => setSeed(e.target.value)} />
      </label>

      <p className="panel-hint">Sahne boyutu: {dimsM}</p>

      <button
        className="primary-btn"
        disabled={busy}
        onClick={() =>
          onCreate({
            nx,
            ny,
            nz,
            voxel_size_m: voxelSize,
            preset,
            num_anomalies: numAnomalies,
            seed: seed.trim() === "" ? null : Number(seed),
          })
        }
      >
        {busy ? "Oluşturuluyor..." : "Yeni Senaryo Oluştur"}
      </button>
    </div>
  );
}
