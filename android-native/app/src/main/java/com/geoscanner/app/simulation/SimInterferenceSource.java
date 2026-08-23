package com.geoscanner.app.simulation;

/** A placed external interference source — never a buried target, always tagged separately in the scene. */
public class SimInterferenceSource {
    public InterferenceType type;
    public double xM;
    public double yM;
    public Double intensityOverride;

    public SimInterferenceSource(InterferenceType type, double xM, double yM) {
        this.type = type;
        this.xM = xM;
        this.yM = yM;
    }

    public double intensity() {
        return intensityOverride != null ? intensityOverride : type.defaultIntensity;
    }

    public String label() {
        return type.displayNameTr;
    }
}
