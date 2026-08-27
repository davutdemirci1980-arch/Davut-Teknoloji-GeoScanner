package com.geoscanner.app.compare;

/** One grid cell of a {@link ScanDiffResult}: the two scans' values at that spot and their diff. */
public class DiffCell {
    public boolean hasA;
    public boolean hasB;
    public double valueA;
    public double valueB;
    public double diff;
    public boolean changed;
    public int regionId = -1;
}
