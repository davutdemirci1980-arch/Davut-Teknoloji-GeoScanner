// TypeScript port of backend/app/core/materials.py — runs entirely in-browser.
export interface MaterialDef {
  id: number;
  key: string;
  name_tr: string;
  name_en: string;
  permittivity: number;
  resistivity_ohm_m: number;
  density_kg_m3: number;
  magnetic_susceptibility: number;
  seismic_velocity_m_s: number;
  thermal_conductivity: number;
  color: string;
  is_anomaly: boolean;
}

export const MATERIALS_LIST: MaterialDef[] = [
  { id: 0, key: "dry_soil", name_tr: "Kuru Toprak", name_en: "Dry Soil", permittivity: 6.0, resistivity_ohm_m: 100.0, density_kg_m3: 1500, magnetic_susceptibility: 0.02, seismic_velocity_m_s: 400, thermal_conductivity: 1.0, color: "#8a6642", is_anomaly: false },
  { id: 1, key: "wet_clay", name_tr: "Islak Kil", name_en: "Wet Clay", permittivity: 25.0, resistivity_ohm_m: 15.0, density_kg_m3: 1800, magnetic_susceptibility: 0.05, seismic_velocity_m_s: 1500, thermal_conductivity: 1.3, color: "#5c4a3a", is_anomaly: false },
  { id: 2, key: "sand", name_tr: "Kum", name_en: "Sand", permittivity: 4.5, resistivity_ohm_m: 500.0, density_kg_m3: 1600, magnetic_susceptibility: 0.01, seismic_velocity_m_s: 500, thermal_conductivity: 0.6, color: "#d9c07a", is_anomaly: false },
  { id: 3, key: "bedrock", name_tr: "Ana Kaya (Granit)", name_en: "Bedrock (Granite)", permittivity: 5.5, resistivity_ohm_m: 5000.0, density_kg_m3: 2700, magnetic_susceptibility: 0.4, seismic_velocity_m_s: 5000, thermal_conductivity: 2.8, color: "#6f6f6f", is_anomaly: false },
  { id: 4, key: "limestone", name_tr: "Kireçtaşı", name_en: "Limestone", permittivity: 8.0, resistivity_ohm_m: 1000.0, density_kg_m3: 2400, magnetic_susceptibility: 0.05, seismic_velocity_m_s: 3500, thermal_conductivity: 2.2, color: "#c7c1a8", is_anomaly: false },
  { id: 5, key: "groundwater", name_tr: "Yeraltı Suyu", name_en: "Groundwater", permittivity: 81.0, resistivity_ohm_m: 30.0, density_kg_m3: 1000, magnetic_susceptibility: 0.0, seismic_velocity_m_s: 1480, thermal_conductivity: 0.6, color: "#3a7bd5", is_anomaly: false },
  { id: 6, key: "void", name_tr: "Boşluk / Oyuk", name_en: "Void / Cavity", permittivity: 1.0, resistivity_ohm_m: 100000.0, density_kg_m3: 1.2, magnetic_susceptibility: 0.0, seismic_velocity_m_s: 340, thermal_conductivity: 0.025, color: "#111318", is_anomaly: true },
  { id: 7, key: "metal_pipe", name_tr: "Metal Boru", name_en: "Metal Pipe", permittivity: 1.0, resistivity_ohm_m: 1e-6, density_kg_m3: 7800, magnetic_susceptibility: 100.0, seismic_velocity_m_s: 5900, thermal_conductivity: 45.0, color: "#c0392b", is_anomaly: true },
  { id: 8, key: "pvc_pipe", name_tr: "PVC Boru", name_en: "PVC Pipe", permittivity: 3.0, resistivity_ohm_m: 1e12, density_kg_m3: 1400, magnetic_susceptibility: 0.0, seismic_velocity_m_s: 2400, thermal_conductivity: 0.19, color: "#e67e22", is_anomaly: true },
  { id: 9, key: "concrete", name_tr: "Beton Yapı", name_en: "Concrete Structure", permittivity: 9.0, resistivity_ohm_m: 200.0, density_kg_m3: 2400, magnetic_susceptibility: 0.03, seismic_velocity_m_s: 3200, thermal_conductivity: 1.7, color: "#a5a5a5", is_anomaly: true },
  { id: 10, key: "wood", name_tr: "Ahşap (Arkeolojik)", name_en: "Wood (Archaeological)", permittivity: 2.5, resistivity_ohm_m: 1e4, density_kg_m3: 700, magnetic_susceptibility: 0.0, seismic_velocity_m_s: 1200, thermal_conductivity: 0.15, color: "#8b5a2b", is_anomaly: true },
  { id: 11, key: "archaeological_stone", name_tr: "Arkeolojik Duvar", name_en: "Archaeological Wall", permittivity: 7.5, resistivity_ohm_m: 800.0, density_kg_m3: 2200, magnetic_susceptibility: 0.06, seismic_velocity_m_s: 3000, thermal_conductivity: 1.9, color: "#b08968", is_anomaly: true },
];

export const MATERIALS: Record<string, MaterialDef> = Object.fromEntries(MATERIALS_LIST.map((m) => [m.key, m]));
export const MATERIALS_BY_ID: MaterialDef[] = [...MATERIALS_LIST].sort((a, b) => a.id - b.id);

export const BACKGROUND_MATERIAL_KEYS = ["dry_soil", "wet_clay", "sand", "bedrock", "limestone", "groundwater"];
export const ANOMALY_MATERIAL_KEYS = MATERIALS_LIST.filter((m) => m.is_anomaly).map((m) => m.key);

export function materialPropertyArray(field: keyof MaterialDef): Float64Array {
  const arr = new Float64Array(MATERIALS_BY_ID.length);
  MATERIALS_BY_ID.forEach((m, i) => {
    arr[i] = m[field] as number;
  });
  return arr;
}
