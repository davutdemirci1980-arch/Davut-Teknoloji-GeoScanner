package com.geoscanner.app.simulation;

/** Calibration/reference options (section 7): baseline subtraction and dual-sensor balancing. */
public class SimCalibrationConfig {
    /** Subtract the average of the first column (clean-ground reference strip) from every point. */
    public boolean referenceFirstColumn = false;
    /** Zero-mean sensor 1 and sensor 2 independently before computing the gradient (DUAL mode only). */
    public boolean balanceDualSensors = false;
}
