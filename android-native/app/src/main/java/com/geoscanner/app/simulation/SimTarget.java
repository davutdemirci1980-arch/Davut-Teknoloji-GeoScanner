package com.geoscanner.app.simulation;

/** A single virtual buried target placed in the simulation scene. */
public class SimTarget {
    public TargetType type;
    public double xM;
    public double yM;
    public double depthM;
    public double sizeM;
    public TargetShape shape;
    public double orientationDeg;
    /** Null = use type.defaultContrast; otherwise overrides it (user-tunable "material" strength). */
    public Double contrastOverride;

    public SimTarget(TargetType type, double xM, double yM, double depthM) {
        this.type = type;
        this.xM = xM;
        this.yM = yM;
        this.depthM = depthM;
        this.sizeM = type.defaultSizeM;
        this.shape = type.defaultShape;
        this.orientationDeg = 0;
        this.contrastOverride = null;
    }

    public double contrast() {
        return contrastOverride != null ? contrastOverride : type.defaultContrast;
    }

    public String label() {
        return type.displayNameTr;
    }
}
