package com.geoscanner.app.simulation;

import java.io.Serializable;

/** Sensor rig settings: mode, geometry, and instrument imperfections. */
public class SimSensorConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public SensorMode mode = SensorMode.SINGLE;
    public double heightAboveGroundM = 0.1;
    /** Vertical spacing between sensor 1 and sensor 2 in DUAL_GRADIOMETER mode. */
    public double sensorSpacingM = 0.5;
    /** 0..1, scales random per-sample sensor noise on top of ground noise. */
    public double sensitivityNoise = 0.15;
    public double offset = 0.0;
    /** Signal added linearly per scanned row, simulating instrument drift over time. */
    public double driftPerRow = 0.0;
}
