package com.geoscanner.app.simulation;

import java.io.Serializable;

/** Ground/soil scenario + background interference settings for a simulated survey. */
public class SimGroundConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public GroundType groundType = GroundType.NORMAL;
    /** 0..1, multiplies groundType.baseNoise and mineralizationVariance. */
    public double interferenceLevel = 0.5;
    public double regionalSlopeXPerM = 0.0;
    public double regionalSlopeYPerM = 0.0;

    public double effectiveNoise() {
        return groundType.baseNoise * interferenceLevel;
    }

    public double effectiveMineralizationVariance() {
        return groundType.mineralizationVariance * interferenceLevel;
    }
}
