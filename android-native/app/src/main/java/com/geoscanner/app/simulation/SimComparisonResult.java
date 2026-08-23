package com.geoscanner.app.simulation;

import java.util.ArrayList;
import java.util.List;

/** Result of comparing two independent scans of the same scene (section 18). */
public class SimComparisonResult {
    /** Pearson correlation (-1..1) between the two scans' raw signal grids. */
    public double repeatabilityScore;
    /** Anomalies that showed up (nearby) in both scans — likely real. */
    public final List<SimAnomalyCluster> common = new ArrayList<>();
    /** Anomalies seen only in scan A — possibly noise, drift, or a one-off error. */
    public final List<SimAnomalyCluster> onlyInA = new ArrayList<>();
    /** Anomalies seen only in scan B. */
    public final List<SimAnomalyCluster> onlyInB = new ArrayList<>();
}
