import type { SensorDef } from "./base";
import { GPRSensor } from "./gpr";
import { MagnetometerSensor } from "./magnetometer";
import { ResistivitySensor } from "./resistivity";
import { SeismicSensor } from "./seismic";
import { EMInductionSensor } from "./emInduction";
import { ThermalSensor } from "./thermal";
import { GravimetricSensor } from "./gravimetric";
import { LidarSensor } from "./lidar";

export const SENSOR_REGISTRY: Record<string, SensorDef> = {
  gpr: GPRSensor,
  magnetometer: MagnetometerSensor,
  resistivity: ResistivitySensor,
  seismic: SeismicSensor,
  em_induction: EMInductionSensor,
  thermal: ThermalSensor,
  gravimetric: GravimetricSensor,
  lidar: LidarSensor,
};

export const ALL_SENSOR_KEYS = Object.keys(SENSOR_REGISTRY);
