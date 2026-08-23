package com.geoscanner.app.simulation;

import java.io.Serializable;

/** Survey grid geometry and walking route. */
public class SimGridConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public int cols = 11;
    public int rows = 11;
    public double stepCm = 30;
    public ScanPattern pattern = ScanPattern.ZIGZAG;

    public double stepM() {
        return stepCm / 100.0;
    }
}
