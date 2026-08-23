package com.geoscanner.app.simulation;

import java.io.Serializable;

/** Calibration/reference options (section 7): baseline subtraction and dual-sensor balancing. */
public class SimCalibrationConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Subtract the average of the first column (clean-ground reference strip) from every point. */
    public boolean referenceFirstColumn = false;
    /** Zero-mean sensor 1 and sensor 2 independently before computing the gradient (DUAL mode only). */
    public boolean balanceDualSensors = false;
}
