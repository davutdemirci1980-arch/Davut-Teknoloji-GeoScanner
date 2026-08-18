import { useRef, useState } from "react";
import type { AnalyzeResponse } from "../types";
import { anomaliesToCsv, saveCsvFile } from "../csvExport";

interface Props {
  onAnalyze: (minSizeVoxels: number, thresholdPercentile: number) => void;
  busy: boolean;
  disabled: boolean;
  result: AnalyzeResponse | null;
}

export default function AnalysisPanel({ onAnalyze, busy, disabled, result }: Props) {
  const [threshold, setThreshold] = useState(96);
  const [minSize, setMinSize] = useState(3);
  const [csvBusy, setCsvBusy] = useState(false);
  const [csvStatus, setCsvStatus] = useState<string | null>(null);
  const [showCsvText, setShowCsvText] = useState(false);
  const [copyStatus, setCopyStatus] = useState<string | null>(null);
  const csvTextareaRef = useRef<HTMLTextAreaElement>(null);

  async function handleDownloadCsv() {
    if (!result) return;
    setCsvBusy(true);
    setCsvStatus(null);
    try {
      const csv = anomaliesToCsv(result);
      const { message } = await saveCsvFile(`geoscanner-anomaliler-${result.session_id.slice(0, 8)}.csv`, csv);
      setCsvStatus(message);
    } finally {
      setCsvBusy(false);
    }
  }

  async function handleCopyCsv() {
    if (!result) return;
    const csv = anomaliesToCsv(result);
    try {
      await navigator.clipboard.writeText(csv);
      setCopyStatus("Panoya kopyalandı.");
      return;
    } catch {
      // Clipboard API blocked in this view — fall through to manual select.
    }
    const el = csvTextareaRef.current;
    if (el) {
      el.focus();
      el.select();
      try {
        if (document.execCommand("copy")) {
          setCopyStatus("Panoya kopyalandı.");
          return;
        }
      } catch {
        // ignore, fall through to manual instructions
      }
    }
    setCopyStatus("Otomatik kopyalanamadı — metni elle seçip kopyalayın (yukarıdaki kutu seçili).");
  }

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
          <div className="csv-actions">
            <button className="ghost-btn csv-btn" disabled={csvBusy || result.anomalies.length === 0} onClick={handleDownloadCsv}>
              {csvBusy ? "Hazırlanıyor..." : "CSV İndir"}
            </button>
            <button
              className="ghost-btn csv-btn"
              disabled={result.anomalies.length === 0}
              onClick={() => {
                setShowCsvText((v) => !v);
                setCopyStatus(null);
              }}
            >
              {showCsvText ? "Metni Gizle" : "CSV'yi Göster / Kopyala"}
            </button>
          </div>
          {csvStatus && <p className="panel-hint csv-status">{csvStatus}</p>}
          {showCsvText && (
            <div className="csv-text-box">
              <textarea
                ref={csvTextareaRef}
                readOnly
                value={anomaliesToCsv(result)}
                onFocus={(e) => e.currentTarget.select()}
                rows={6}
              />
              <div className="csv-text-actions">
                <button className="ghost-btn" onClick={handleCopyCsv}>
                  Panoya Kopyala
                </button>
                {copyStatus && <span className="panel-hint csv-status">{copyStatus}</span>}
              </div>
              <p className="panel-hint">
                Kopyalama çalışmazsa kutunun içine dokunun, tümünü seçip (uzun basılı tutup "Tümünü Seç") elle kopyalayın.
              </p>
            </div>
          )}
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
