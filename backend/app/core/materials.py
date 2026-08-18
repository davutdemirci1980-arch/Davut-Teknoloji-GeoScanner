"""Physical property catalog for subsurface materials.

Values are simplified, literature-typical order-of-magnitude figures used to
drive the sensor physics simulations. They are not meant to be laboratory
exact, only internally consistent enough to produce plausible, distinguishable
sensor responses per material.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Material:
    id: int
    key: str
    name_tr: str
    name_en: str
    # Relative dielectric permittivity (affects GPR velocity/reflection).
    permittivity: float
    # Bulk electrical resistivity in ohm-meters (affects ERT / EMI).
    resistivity_ohm_m: float
    # Bulk density in kg/m^3 (affects gravimetric response).
    density_kg_m3: float
    # Volume magnetic susceptibility, SI units x1e-3 (affects magnetometer).
    magnetic_susceptibility: float
    # Seismic P-wave velocity in m/s (affects seismic reflection).
    seismic_velocity_m_s: float
    # Thermal conductivity W/(m*K) (affects thermal/infrared surface signature).
    thermal_conductivity: float
    # Rendering color, hex.
    color: str
    is_anomaly: bool = False


MATERIALS: dict[str, Material] = {
    m.key: m
    for m in [
        Material(0, "dry_soil", "Kuru Toprak", "Dry Soil", 6.0, 100.0, 1500, 0.02, 400, 1.0, "#8a6642"),
        Material(1, "wet_clay", "Islak Kil", "Wet Clay", 25.0, 15.0, 1800, 0.05, 1500, 1.3, "#5c4a3a"),
        Material(2, "sand", "Kum", "Sand", 4.5, 500.0, 1600, 0.01, 500, 0.6, "#d9c07a"),
        Material(3, "bedrock", "Ana Kaya (Granit)", "Bedrock (Granite)", 5.5, 5000.0, 2700, 0.4, 5000, 2.8, "#6f6f6f"),
        Material(4, "limestone", "Kireçtaşı", "Limestone", 8.0, 1000.0, 2400, 0.05, 3500, 2.2, "#c7c1a8"),
        Material(5, "groundwater", "Yeraltı Suyu", "Groundwater", 81.0, 30.0, 1000, 0.0, 1480, 0.6, "#3a7bd5"),
        Material(6, "void", "Boşluk / Oyuk", "Void / Cavity", 1.0, 100000.0, 1.2, 0.0, 340, 0.025, "#111318", True),
        Material(7, "metal_pipe", "Metal Boru", "Metal Pipe", 1.0, 1e-6, 7800, 100.0, 5900, 45.0, "#c0392b", True),
        Material(8, "pvc_pipe", "PVC Boru", "PVC Pipe", 3.0, 1e12, 1400, 0.0, 2400, 0.19, "#e67e22", True),
        Material(9, "concrete", "Beton Yapı", "Concrete Structure", 9.0, 200.0, 2400, 0.03, 3200, 1.7, "#a5a5a5", True),
        Material(10, "wood", "Ahşap (Arkeolojik)", "Wood (Archaeological)", 2.5, 1e4, 700, 0.0, 1200, 0.15, "#8b5a2b", True),
        Material(11, "archaeological_stone", "Arkeolojik Duvar", "Archaeological Wall", 7.5, 800.0, 2200, 0.06, 3000, 1.9, "#b08968", True),
    ]
}

MATERIALS_BY_ID: dict[int, Material] = {m.id: m for m in MATERIALS.values()}

BACKGROUND_MATERIAL_KEYS = ["dry_soil", "wet_clay", "sand", "bedrock", "limestone", "groundwater"]
ANOMALY_MATERIAL_KEYS = [m.key for m in MATERIALS.values() if m.is_anomaly]


def material_property_array(field: str) -> "list[float]":
    """Return a list indexed by material id for a given property name."""
    ordered = [MATERIALS_BY_ID[i] for i in range(len(MATERIALS_BY_ID))]
    return [getattr(m, field) for m in ordered]
