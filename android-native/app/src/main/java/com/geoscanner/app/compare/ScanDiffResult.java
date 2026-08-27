package com.geoscanner.app.compare;

import java.util.ArrayList;
import java.util.List;

/**
 * Output of {@link ScanDiffEngine#compare}: a dense grid covering the union
 * footprint of the two aligned scans, plus the clustered "changed" regions
 * and summary stats.
 */
public class ScanDiffResult {
    public int nx;
    public int ny;
    /** cells[iy][ix], iy=0 is the southernmost (lowest Y) row. */
    public DiffCell[][] cells;

    public double originXm;
    public double originYm;
    public double stepXm;
    public double stepYm;

    public int shiftX;
    public int shiftY;
    public double correlationScore;

    public int overlapCells;
    public int changedCells;
    public double overallChangedPercent;
    public double maxAbsDiff;
    public double threshold;

    public List<DiffRegion> regions = new ArrayList<>();
}
