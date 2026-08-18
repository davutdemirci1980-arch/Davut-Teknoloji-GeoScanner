import type { SensorInfo, SensorResultOut } from "../types";

interface Props {
  sensors: SensorInfo[];
  selectedKeys: Set<string>;
  onToggle: (key: string) => void;
  noiseLevel: number;
  onNoiseLevelChange: (v: number) => void;
  onRun: () => void;
  busy: boolean;
  disabled: boolean;
  results: SensorResultOut[];
  activeSensorKey: string | null;
  onSelectActive: (key: string) => void;
}

export default function SensorPanel({
  sensors,
  selectedKeys,
  onToggle,
  noiseLevel,
  onNoiseLevelChange,
  onRun,
  busy,
  disabled,
  results,
  activeSensorKey,
  onSelectActive,
}: Props) {
  const resultByKey = new Map(results.map((r) => [r.sensor_key, r]));

  return (
    <div className="panel">
      <h2>2. Çoklu Sensör Simülasyonu</h2>
      <p className="panel-hint">Bütün sensör tiplerinin fiziksel tepkisini voksel ızgarası üzerinden hesaplar.</p>

      <div className="sensor-list">
        {sensors.map((s) => {
          const result = resultByKey.get(s.key);
          const isActive = activeSensorKey === s.key;
          return (
            <div key={s.key} className={`sensor-row ${isActive ? "sensor-row-active" : ""}`}>
              <label className="sensor-checkbox">
                <input type="checkbox" checked={selectedKeys.has(s.key)} onChange={() => onToggle(s.key)} />
                <span>{s.name_tr}</span>
              </label>
              <div className="sensor-meta">
                <span className="sensor-depth">≤{s.max_effective_depth_m.toFixed(1)}m</span>
                {result && (
                  <button className="ghost-btn" onClick={() => onSelectActive(s.key)}>
                    {isActive ? "Seçili" : "Göster"}
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      <label>
        Gürültü Seviyesi: {(noiseLevel * 100).toFixed(0)}%
        <input type="range" min={0} max={0.6} step={0.02} value={noiseLevel} onChange={(e) => onNoiseLevelChange(Number(e.target.value))} />
      </label>

      <button className="primary-btn" disabled={busy || disabled || selectedKeys.size === 0} onClick={onRun}>
        {busy ? "Taranıyor..." : "Sensörleri Çalıştır"}
      </button>
    </div>
  );
}
