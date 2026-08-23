package com.geoscanner.app.simulation;

import java.io.Serializable;

/**
 * One raw simulated survey sample. This is the "raw" simulation layer:
 * separate from any visualization/interpolation and always tagged
 * {@link #isSimulation} = true so it can never be confused with a real
 * device reading (see design principle in the simulation lab spec).
 */
public class SimDataPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    public int gridX;
    public int gridY;
    public double xM;
    public double yM;
    public double bx;
    public double by;
    public double bz;
    public double sensor1;
    public double sensor2;
    public double gradient;
    /** Single-channel value (mode-dependent: bz, gradient, or 3-axis magnitude) used to feed the existing 2D/3D/4D viewers. */
    public double displayValue;
    /** Depth (m) of whichever target dominates this point's signal; feeds the existing depth-layered 3D/4D extrusion. */
    public double dominantDepthM;
    /** True when the operator-error simulation dropped the real reading at this point (recorded as background only). */
    public boolean missed;
    public final boolean isSimulation = true;
}
