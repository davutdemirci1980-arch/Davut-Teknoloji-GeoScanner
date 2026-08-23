package com.geoscanner.app.simulation;

/** One detected anomaly cluster, with its geometry, two independent depth estimates, and an AI candidate label. */
public class SimAnomalyCluster {
    public double centerXM;
    public double centerYM;
    public double widthM;
    public double areaM2;
    public double peakAmplitude;
    public int cellCount;
    public double aspectRatio;
    public boolean singlePointSpike;

    public double depthEstimateHalfWidthM;
    public double depthEstimateInversionM;
    public double depthUncertaintyM;
    /** Known only because this is simulated data — the true depth of whichever target dominates the peak cell. */
    public double trueDepthM;

    public TargetType candidateType;
    public double confidence;
    public String explanation;
    public boolean falsePositiveRisk;
    public String falsePositiveReason;
    /** Free-text user marking, e.g. "Hedef A", "Şüpheli Bölge", "Kontrol Edilecek" (section 22). */
    public String userMarkLabel = "";

    public String shapeLabel() {
        if (singlePointSpike) return "Tek Nokta";
        if (aspectRatio >= 2.2) return "Uzun / Çizgisel";
        if (cellCount >= 8 && aspectRatio < 1.6) return "Geniş Alan";
        return "Dairesel / Lokal";
    }
}
