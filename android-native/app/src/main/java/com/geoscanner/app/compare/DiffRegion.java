package com.geoscanner.app.compare;

/**
 * A connected cluster of "changed" cells in a {@link ScanDiffResult}, with its
 * footprint in real-world meters and the average change it represents.
 */
public class DiffRegion {
    public int cellCount;
    public double avgDiff;
    public boolean increase;
    public double pctChange;
    public double minXm;
    public double maxXm;
    public double minYm;
    public double maxYm;
}
