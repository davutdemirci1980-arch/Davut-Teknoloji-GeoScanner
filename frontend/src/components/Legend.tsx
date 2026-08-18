import type { Material } from "../types";

export default function Legend({ materials }: { materials: Material[] }) {
  return (
    <div className="panel legend-panel">
      <h2>Malzeme Lejantı</h2>
      <div className="legend-grid">
        {materials.map((m) => (
          <div key={m.id} className="legend-item">
            <span className="legend-swatch" style={{ background: m.color }} />
            <span>{m.name_tr}</span>
            {m.is_anomaly && <span className="legend-anomaly-tag">nesne</span>}
          </div>
        ))}
      </div>
    </div>
  );
}
