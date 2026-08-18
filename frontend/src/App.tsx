import { useEffect, useState } from "react";
import { api, IS_STANDALONE } from "./api/client";
import VoxelViewer3D, { type ViewerMode } from "./components/VoxelViewer3D";
import ScenarioControls from "./components/ScenarioControls";
import SensorPanel from "./components/SensorPanel";
import AnalysisPanel from "./components/AnalysisPanel";
import Legend from "./components/Legend";
import SensorHeatmap from "./components/SensorHeatmap";
import BackendSettings from "./components/BackendSettings";
import type {
  AnalyzeResponse,
  Material,
  ScenarioCreateRequest,
  ScenarioCreateResponse,
  SensorInfo,
  SensorResultOut,
  SparseVolumeResponse,
  VoxelGridResponse,
} from "./types";
import "./index.css";

type DisplayMode = "geology" | "sensor" | "fusion";

export default function App() {
  const [materials, setMaterials] = useState<Material[]>([]);
  const [sensors, setSensors] = useState<SensorInfo[]>([]);

  const [scenario, setScenario] = useState<ScenarioCreateResponse | null>(null);
  const [voxelGrid, setVoxelGrid] = useState<VoxelGridResponse | null>(null);

  const [selectedSensorKeys, setSelectedSensorKeys] = useState<Set<string>>(new Set());
  const [noiseLevel, setNoiseLevel] = useState(0.12);
  const [sensorRunResults, setSensorRunResults] = useState<SensorResultOut[]>([]);
  const [activeSensorKey, setActiveSensorKey] = useState<string | null>(null);
  const [activeSensorVolume, setActiveSensorVolume] = useState<SparseVolumeResponse | null>(null);

  const [analysisResult, setAnalysisResult] = useState<AnalyzeResponse | null>(null);
  const [fusedVolume, setFusedVolume] = useState<SparseVolumeResponse | null>(null);

  const [displayMode, setDisplayMode] = useState<DisplayMode>("geology");

  const [creatingScenario, setCreatingScenario] = useState(false);
  const [runningSensors, setRunningSensors] = useState(false);
  const [loadingVolume, setLoadingVolume] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .getMaterials()
      .then(setMaterials)
      .catch((e) => setError(String(e)));
    api
      .getSensors()
      .then((s) => {
        setSensors(s);
        setSelectedSensorKeys(new Set(s.map((x) => x.key)));
      })
      .catch((e) => setError(String(e)));
  }, []);

  async function handleCreateScenario(req: ScenarioCreateRequest) {
    setError(null);
    setCreatingScenario(true);
    try {
      const created = await api.createScenario(req);
      const voxels = await api.getVoxels(created.session_id);
      setScenario(created);
      setVoxelGrid(voxels);
      setSensorRunResults([]);
      setActiveSensorKey(null);
      setActiveSensorVolume(null);
      setAnalysisResult(null);
      setFusedVolume(null);
      setDisplayMode("geology");
    } catch (e) {
      setError(String(e));
    } finally {
      setCreatingScenario(false);
    }
  }

  async function loadSensorVolume(sessionId: string, key: string) {
    setLoadingVolume(true);
    try {
      const volume = await api.getSensorVolume(sessionId, key, 0.15);
      setActiveSensorVolume(volume);
      setDisplayMode("sensor");
    } catch (e) {
      setError(String(e));
    } finally {
      setLoadingVolume(false);
    }
  }

  async function handleRunSensors() {
    if (!scenario) return;
    setError(null);
    setRunningSensors(true);
    try {
      const res = await api.runSensors(scenario.session_id, Array.from(selectedSensorKeys), noiseLevel, null);
      setSensorRunResults(res.results);
      setAnalysisResult(null);
      setFusedVolume(null);
      const firstKey = res.results[0]?.sensor_key ?? null;
      setActiveSensorKey(firstKey);
      if (firstKey) await loadSensorVolume(scenario.session_id, firstKey);
    } catch (e) {
      setError(String(e));
    } finally {
      setRunningSensors(false);
    }
  }

  async function handleSelectActiveSensor(key: string) {
    if (!scenario) return;
    setActiveSensorKey(key);
    await loadSensorVolume(scenario.session_id, key);
  }

  async function handleAnalyze(minSizeVoxels: number, thresholdPercentile: number) {
    if (!scenario) return;
    setError(null);
    setAnalyzing(true);
    try {
      const res = await api.analyze(scenario.session_id, minSizeVoxels, thresholdPercentile);
      setAnalysisResult(res);
      const fused = await api.getFusedVolume(scenario.session_id, 0.15);
      setFusedVolume(fused);
      setDisplayMode("fusion");
    } catch (e) {
      setError(String(e));
    } finally {
      setAnalyzing(false);
    }
  }

  function toggleSensorKey(key: string) {
    setSelectedSensorKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  const activeResult = sensorRunResults.find((r) => r.sensor_key === activeSensorKey) ?? null;

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1>Davut Teknoloji · GeoScanner</h1>
          <p>Tam voksel tabanlı, çoklu sensörlü, yapay zeka destekli yer altı görüntüleme simülasyon laboratuvarı</p>
          {IS_STANDALONE && <p className="standalone-tag">Bağımsız sürüm — sunucu yok, tüm hesaplama bu cihazda çalışır</p>}
        </div>
        <div className="header-right">
          {scenario && (
            <div className="scenario-badge">
              {scenario.dimensions_m[0].toFixed(1)}×{scenario.dimensions_m[1].toFixed(1)}×{scenario.dimensions_m[2].toFixed(1)} m ·{" "}
              {scenario.shape[0]}×{scenario.shape[1]}×{scenario.shape[2]} voksel
            </div>
          )}
          {!IS_STANDALONE && <BackendSettings onSaved={() => window.location.reload()} />}
        </div>
      </header>

      {error && (
        <div className="error-banner" onClick={() => setError(null)}>
          {error}
        </div>
      )}

      <div className="app-body">
        <aside className="sidebar sidebar-left">
          <ScenarioControls onCreate={handleCreateScenario} busy={creatingScenario} />
          <SensorPanel
            sensors={sensors}
            selectedKeys={selectedSensorKeys}
            onToggle={toggleSensorKey}
            noiseLevel={noiseLevel}
            onNoiseLevelChange={setNoiseLevel}
            onRun={handleRunSensors}
            busy={runningSensors}
            disabled={!scenario}
            results={sensorRunResults}
            activeSensorKey={activeSensorKey}
            onSelectActive={handleSelectActiveSensor}
          />
        </aside>

        <main className="viewer-column">
          <div className="viewer-toolbar">
            <div className="mode-toggle">
              <button className={displayMode === "geology" ? "active" : ""} onClick={() => setDisplayMode("geology")} disabled={!voxelGrid}>
                Jeoloji (Voksel)
              </button>
              <button
                className={displayMode === "sensor" ? "active" : ""}
                onClick={() => setDisplayMode("sensor")}
                disabled={!activeSensorVolume}
              >
                Sensör Isı Hacmi
              </button>
              <button className={displayMode === "fusion" ? "active" : ""} onClick={() => setDisplayMode("fusion")} disabled={!fusedVolume}>
                AI Füzyon &amp; Anomaliler
              </button>
            </div>
            {loadingVolume && <span className="loading-tag">hacim yükleniyor...</span>}
          </div>

          <div className="viewer-frame">
            {scenario ? (
              <VoxelViewer3D
                shape={scenario.shape}
                voxelSizeM={scenario.voxel_size_m}
                geologyVoxels={voxelGrid?.voxels}
                materials={materials}
                sparseVolume={displayMode === "sensor" ? activeSensorVolume?.voxels : displayMode === "fusion" ? fusedVolume?.voxels : undefined}
                mode={(displayMode === "geology" ? "geology" : "value") as ViewerMode}
                anomalies={displayMode === "fusion" ? analysisResult?.anomalies : undefined}
              />
            ) : (
              <div className="viewer-empty">Başlamak için soldan bir senaryo oluşturun.</div>
            )}
          </div>

          {sensorRunResults.length > 0 && (
            <div className="heatmap-row">
              {sensorRunResults.map((r) => {
                const info = sensors.find((s) => s.key === r.sensor_key);
                return (
                  <div key={r.sensor_key} onClick={() => handleSelectActiveSensor(r.sensor_key)} className="heatmap-click-wrap">
                    <SensorHeatmap result={r} title={info?.name_tr ?? r.sensor_key} height={110} />
                  </div>
                );
              })}
            </div>
          )}

          {activeResult && displayMode === "sensor" && (
            <p className="panel-hint viewer-caption">
              Aktif sensör: {sensors.find((s) => s.key === activeSensorKey)?.name_tr ?? activeSensorKey} —{" "}
              {sensors.find((s) => s.key === activeSensorKey)?.description_tr}
            </p>
          )}
        </main>

        <aside className="sidebar sidebar-right">
          <AnalysisPanel onAnalyze={handleAnalyze} busy={analyzing} disabled={sensorRunResults.length === 0} result={analysisResult} />
          <Legend materials={materials} />
        </aside>
      </div>
    </div>
  );
}
