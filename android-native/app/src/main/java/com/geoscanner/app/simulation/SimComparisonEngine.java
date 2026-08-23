package com.geoscanner.app.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares two independently-generated scans of the same scene (section 18):
 * a Pearson-correlation repeatability score over the raw grids, plus
 * anomaly-cluster matching (nearby clusters in both scans = "common",
 * others = seen in only one scan and worth a second look).
 */
public class SimComparisonEngine {

    public static SimComparisonResult compare(List<SimDataPoint> rawA, List<SimDataPoint> rawB,
                                                List<SimAnomalyCluster> clustersA, List<SimAnomalyCluster> clustersB,
                                                double stepM) {
        SimComparisonResult result = new SimComparisonResult();
        result.repeatabilityScore = pearsonCorrelation(rawA, rawB);

        boolean[] usedB = new boolean[clustersB.size()];
        double matchRadius = stepM * 1.5;

        for (SimAnomalyCluster a : clustersA) {
            int bestIdx = -1;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < clustersB.size(); i++) {
                if (usedB[i]) continue;
                SimAnomalyCluster b = clustersB.get(i);
                double dx = a.centerXM - b.centerXM;
                double dy = a.centerYM - b.centerYM;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0 && bestDist <= matchRadius) {
                usedB[bestIdx] = true;
                result.common.add(a);
            } else {
                result.onlyInA.add(a);
            }
        }
        for (int i = 0; i < clustersB.size(); i++) {
            if (!usedB[i]) result.onlyInB.add(clustersB.get(i));
        }
        return result;
    }

    private static double pearsonCorrelation(List<SimDataPoint> a, List<SimDataPoint> b) {
        int maxRow = 0, maxCol = 0;
        for (SimDataPoint p : a) {
            maxRow = Math.max(maxRow, p.gridY);
            maxCol = Math.max(maxCol, p.gridX);
        }
        double[][] gridA = new double[maxRow + 1][maxCol + 1];
        boolean[][] hasA = new boolean[maxRow + 1][maxCol + 1];
        for (SimDataPoint p : a) {
            gridA[p.gridY][p.gridX] = p.displayValue;
            hasA[p.gridY][p.gridX] = true;
        }

        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (SimDataPoint p : b) {
            if (p.gridY <= maxRow && p.gridX <= maxCol && hasA[p.gridY][p.gridX]) {
                xs.add(gridA[p.gridY][p.gridX]);
                ys.add(p.displayValue);
            }
        }
        int n = xs.size();
        if (n < 2) return 0;

        double meanX = 0, meanY = 0;
        for (int i = 0; i < n; i++) {
            meanX += xs.get(i);
            meanY += ys.get(i);
        }
        meanX /= n;
        meanY /= n;

        double cov = 0, varX = 0, varY = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs.get(i) - meanX;
            double dy = ys.get(i) - meanY;
            cov += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }
        double denom = Math.sqrt(varX) * Math.sqrt(varY);
        return denom > 1e-9 ? cov / denom : 0;
    }
}
