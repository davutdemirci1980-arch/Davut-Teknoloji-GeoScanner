package com.geoscanner.app.simulation;

import java.util.ArrayList;
import java.util.List;

/** Bundles everything {@link SimulationEngine} needs for one survey run. */
public class SimRunConfig {
    public final List<SimTarget> targets = new ArrayList<>();
    public final List<SimInterferenceSource> interferences = new ArrayList<>();
    public SimGroundConfig ground = new SimGroundConfig();
    public SimSensorConfig sensor = new SimSensorConfig();
    public SimGridConfig grid = new SimGridConfig();
    public OperatorErrorConfig operatorError = new OperatorErrorConfig();
    public SimCalibrationConfig calibration = new SimCalibrationConfig();
}
