package com.geoscanner.app.simulation;

/**
 * Simulates imperfect field operation on top of an otherwise clean scan:
 * walking-speed variation, sensor height wobble/tilt/vibration, scan-line
 * drift, mis-recorded points, turn error and missed points. All fields are
 * 0..1 severity unless noted; 0 means "no error of this kind".
 */
public class OperatorErrorConfig {
    public boolean enabled = false;
    public double walkingSpeedVariation = 0.3;
    public double heightWobble = 0.3;
    public double tiltVibration = 0.3;
    public double lineDrift = 0.2;
    public double missedPointRate = 0.05;
    public double turnErrorRate = 0.05;
}
