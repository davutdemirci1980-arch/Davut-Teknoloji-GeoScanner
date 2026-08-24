package com.geoscanner.app.ui.ar;

/** One anomaly projected into real-world space: absolute bearing/distance from the device's current position. */
public class ArTarget {
    public String label;
    public double latitude;
    public double longitude;
    public double bearingDeg;
    public double distanceM;
    public int color;

    public ArTarget(String label, double latitude, double longitude, int color) {
        this.label = label;
        this.latitude = latitude;
        this.longitude = longitude;
        this.color = color;
    }
}
