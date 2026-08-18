from __future__ import annotations

from app.sensors.base import BaseSensorSimulator
from app.sensors.em_induction import EMInductionSimulator
from app.sensors.gpr import GPRSimulator
from app.sensors.gravimetric import GravimetricSimulator
from app.sensors.lidar import LidarSimulator
from app.sensors.magnetometer import MagnetometerSimulator
from app.sensors.resistivity import ResistivitySimulator
from app.sensors.seismic import SeismicSimulator
from app.sensors.thermal import ThermalSimulator

SENSOR_REGISTRY: dict[str, BaseSensorSimulator] = {
    s.key: s
    for s in [
        GPRSimulator(),
        MagnetometerSimulator(),
        ResistivitySimulator(),
        SeismicSimulator(),
        EMInductionSimulator(),
        ThermalSimulator(),
        GravimetricSimulator(),
        LidarSimulator(),
    ]
}

ALL_SENSOR_KEYS = list(SENSOR_REGISTRY.keys())
