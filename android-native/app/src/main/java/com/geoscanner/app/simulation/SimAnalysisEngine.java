package com.geoscanner.app.simulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced anomaly analysis (sections 14-17 of the spec): thresholds and
 * clusters the anomaly grid, estimates each cluster's depth two independent
 * ways, classifies its shape, and scores it against reference signatures for
 * each {@link TargetType} — the same cosine-similarity + softmax approach
 * the backend's Python classifier uses, ported so the native app doesn't
 * need a server round-trip. Never looks at the simulation's ground truth
 * when scoring; the true depth is only attached afterwards, for training
 * comparison, and clearly labeled as such by the caller.
 */
public class SimAnalysisEngine {

    private static final double DETECTION_SIGMA = 1.3;
    /** Calibrated so a default single-metal preset (contrast*volume≈21.6) at ~0.9m recovers ~0.9m at zero offset. */
    private static final double DEPTH_INVERSION_CONSTANT = 21.6;

    private static final Map<TargetType, double[]> REFERENCE_PROFILES = new EnumMap<>(TargetType.class);

    static {
        // [sign(+1/-1), elongation(0..1), amplitudeTier(0..1), areaTier(0..1)]
        REFERENCE_PROFILES.put(TargetType.METAL, new double[]{1, 0.1, 0.9, 0.2});
        REFERENCE_PROFILES.put(TargetType.VOID, new double[]{-1, 0.2, 0.4, 0.3});
        REFERENCE_PROFILES.put(TargetType.ROOM, new double[]{-1, 0.3, 0.5, 0.7});
        REFERENCE_PROFILES.put(TargetType.TUNNEL, new double[]{-1, 0.9, 0.5, 0.5});
        REFERENCE_PROFILES.put(TargetType.GRAVE, new double[]{-1, 0.4, 0.3, 0.3});
        REFERENCE_PROFILES.put(TargetType.SARCOPHAGUS, new double[]{-1, 0.3, 0.35, 0.4});
        REFERENCE_PROFILES.put(TargetType.CUBE, new double[]{1, 0.2, 0.6, 0.3});
        REFERENCE_PROFILES.put(TargetType.PIPE, new double[]{1, 0.9, 0.5, 0.2});
        REFERENCE_PROFILES.put(TargetType.WALL, new double[]{-1, 0.9, 0.25, 0.4});
        REFERENCE_PROFILES.put(TargetType.ROCK, new double[]{1, 0.15, 0.2, 0.3});
        REFERENCE_PROFILES.put(TargetType.MINERALIZED_ZONE, new double[]{1, 0.2, 0.35, 0.7});
        REFERENCE_PROFILES.put(TargetType.WATER_WET_SOIL, new double[]{-1, 0.25, 0.15, 0.7});
    }

    public static List<SimAnomalyCluster> analyze(List<SimDataPoint> points, SimGridConfig grid,
                                                    double sensorHeightM, List<SimInterferenceSource> interferences) {
        int rows = grid.rows;
        int cols = grid.cols;
        double stepM = grid.stepM();

        double[][] value = new double[rows][cols];
        double[][] depthTruth = new double[rows][cols];
        boolean[][] present = new boolean[rows][cols];
        for (SimDataPoint p : points) {
            if (p.gridY < 0 || p.gridY >= rows || p.gridX < 0 || p.gridX >= cols) continue;
            value[p.gridY][p.gridX] = p.displayValue;
            depthTruth[p.gridY][p.gridX] = p.dominantDepthM;
            present[p.gridY][p.gridX] = true;
        }

        double mean = 0;
        int n = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!present[r][c]) continue;
                mean += value[r][c];
                n++;
            }
        }
        if (n == 0) return new ArrayList<>();
        mean /= n;

        double variance = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!present[r][c]) continue;
                double d = value[r][c] - mean;
                variance += d * d;
            }
        }
        variance /= n;
        double std = Math.sqrt(variance);
        double threshold = Math.max(std * DETECTION_SIGMA, 1e-6);

        boolean[][] visited = new boolean[rows][cols];
        List<SimAnomalyCluster> clusters = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (visited[r][c] || !present[r][c]) continue;
                double dev = value[r][c] - mean;
                if (Math.abs(dev) < threshold) {
                    visited[r][c] = true;
                    continue;
                }
                double sign = Math.signum(dev);
                clusters.add(floodFillCluster(value, present, visited, mean, threshold, sign, r, c, rows, cols, stepM, depthTruth));
            }
        }

        for (SimAnomalyCluster cluster : clusters) {
            estimateDepth(cluster, value, mean, rows, cols, stepM, sensorHeightM);
            classify(cluster, points, interferences);
        }

        clusters.sort(Comparator.comparingDouble((SimAnomalyCluster cl) -> Math.abs(cl.peakAmplitude)).reversed());
        return clusters;
    }

    private static SimAnomalyCluster floodFillCluster(double[][] value, boolean[][] present, boolean[][] visited,
                                                        double mean, double threshold, double sign,
                                                        int startR, int startC, int rows, int cols, double stepM,
                                                        double[][] depthTruth) {
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startR, startC});
        visited[startR][startC] = true;

        List<int[]> cells = new ArrayList<>();
        double weightedRow = 0, weightedCol = 0, weightSum = 0;
        double peakAbs = -1, peakSigned = 0;
        int peakR = startR, peakC = startC;
        int minR = startR, maxR = startR, minC = startC, maxC = startC;

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int r = cur[0], c = cur[1];
            cells.add(cur);
            double dev = value[r][c] - mean;
            double w = Math.abs(dev);
            weightedRow += r * w;
            weightedCol += c * w;
            weightSum += w;
            if (w > peakAbs) {
                peakAbs = w;
                peakSigned = dev;
                peakR = r;
                peakC = c;
            }
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
            minC = Math.min(minC, c);
            maxC = Math.max(maxC, c);

            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int nr = r + dr, nc = c + dc;
                    if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                    if (visited[nr][nc] || !present[nr][nc]) continue;
                    double ndev = value[nr][nc] - mean;
                    if (Math.abs(ndev) < threshold || Math.signum(ndev) != sign) continue;
                    visited[nr][nc] = true;
                    queue.add(new int[]{nr, nc});
                }
            }
        }

        SimAnomalyCluster cluster = new SimAnomalyCluster();
        cluster.cellCount = cells.size();
        cluster.centerYM = weightSum > 0 ? (weightedRow / weightSum) * stepM : peakR * stepM;
        cluster.centerXM = weightSum > 0 ? (weightedCol / weightSum) * stepM : peakC * stepM;
        int rowSpan = maxR - minR + 1;
        int colSpan = maxC - minC + 1;
        cluster.widthM = Math.max(rowSpan, colSpan) * stepM;
        cluster.areaM2 = cluster.cellCount * stepM * stepM;
        cluster.aspectRatio = (double) Math.max(rowSpan, colSpan) / Math.max(1, Math.min(rowSpan, colSpan));
        cluster.singlePointSpike = cluster.cellCount == 1;
        cluster.peakAmplitude = peakSigned;
        cluster.trueDepthM = depthTruth[peakR][peakC];
        cluster.explanation = "";
        return cluster;
    }

    private static void estimateDepth(SimAnomalyCluster cluster, double[][] value, double mean,
                                       int rows, int cols, double stepM, double sensorHeightM) {
        int peakR = (int) Math.round(cluster.centerYM / stepM);
        int peakC = (int) Math.round(cluster.centerXM / stepM);
        peakR = Math.max(0, Math.min(rows - 1, peakR));
        peakC = Math.max(0, Math.min(cols - 1, peakC));

        double halfLevel = Math.abs(cluster.peakAmplitude) / 2.0;
        double horizWidthCells = halfWidthAlongRow(value, mean, peakR, peakC, cols, halfLevel);
        double vertWidthCells = halfWidthAlongCol(value, mean, peakR, peakC, rows, halfLevel);
        double avgHalfWidthCells = (horizWidthCells + vertWidthCells) / 2.0;
        cluster.depthEstimateHalfWidthM = Math.max(stepM * 0.4, avgHalfWidthCells * stepM);

        double peakAbs = Math.max(Math.abs(cluster.peakAmplitude), 1e-6);
        double rEstimate = Math.cbrt(DEPTH_INVERSION_CONSTANT / peakAbs);
        cluster.depthEstimateInversionM = Math.max(0.05, rEstimate - sensorHeightM);

        cluster.depthUncertaintyM = Math.abs(cluster.depthEstimateHalfWidthM - cluster.depthEstimateInversionM);
    }

    private static double halfWidthAlongRow(double[][] value, double mean, int peakR, int peakC, int cols, double halfLevel) {
        int left = peakC, right = peakC;
        while (left > 0 && Math.abs(value[peakR][left - 1] - mean) >= halfLevel) left--;
        while (right < cols - 1 && Math.abs(value[peakR][right + 1] - mean) >= halfLevel) right++;
        return (right - left) / 2.0;
    }

    private static double halfWidthAlongCol(double[][] value, double mean, int peakR, int peakC, int rows, double halfLevel) {
        int top = peakR, bottom = peakR;
        while (top > 0 && Math.abs(value[top - 1][peakC] - mean) >= halfLevel) top--;
        while (bottom < rows - 1 && Math.abs(value[bottom + 1][peakC] - mean) >= halfLevel) bottom++;
        return (bottom - top) / 2.0;
    }

    private static void classify(SimAnomalyCluster cluster, List<SimDataPoint> points, List<SimInterferenceSource> interferences) {
        double sign = Math.signum(cluster.peakAmplitude);
        double elongation = Math.min(1.0, (cluster.aspectRatio - 1.0) / 4.0);
        double amplitudeTier = Math.min(1.0, Math.abs(cluster.peakAmplitude) / 60.0);
        double areaTier = Math.min(1.0, cluster.cellCount / 12.0);
        double[] feature = {sign, elongation, amplitudeTier, areaTier};

        Map<TargetType, Double> scores = new EnumMap<>(TargetType.class);
        for (Map.Entry<TargetType, double[]> entry : REFERENCE_PROFILES.entrySet()) {
            scores.put(entry.getKey(), cosineSimilarity(feature, entry.getValue()));
        }

        TargetType best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Map.Entry<TargetType, Double> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }

        double temperature = 8.0;
        double sumExp = 0;
        for (double s : scores.values()) sumExp += Math.exp(temperature * (s - bestScore));
        double confidence = sumExp > 0 ? 1.0 / sumExp : 0.0;

        cluster.candidateType = best;
        cluster.confidence = confidence;

        StringBuilder why = new StringBuilder();
        why.append(sign > 0 ? "Pozitif genlik" : "Negatif genlik");
        why.append(elongation > 0.4 ? ", uzun/çizgisel şekil" : ", kompakt şekil");
        why.append(amplitudeTier > 0.5 ? ", güçlü sinyal" : ", zayıf/orta sinyal");
        why.append(areaTier > 0.5 ? ", geniş alan" : ", dar alan");
        cluster.explanation = why.toString();

        boolean nearInterference = false;
        if (interferences != null) {
            for (SimInterferenceSource s : interferences) {
                double dx = cluster.centerXM - s.xM;
                double dy = cluster.centerYM - s.yM;
                if (Math.sqrt(dx * dx + dy * dy) < 0.5) {
                    nearInterference = true;
                    break;
                }
            }
        }

        if (cluster.singlePointSpike) {
            cluster.falsePositiveRisk = true;
            cluster.falsePositiveReason = "Tek nokta — muhtemelen gürültü/parazit, gerçek hedef olma ihtimali düşük";
            cluster.confidence *= 0.4;
        } else if (nearInterference) {
            cluster.falsePositiveRisk = true;
            cluster.falsePositiveReason = "Bilinen bir parazit kaynağına yakın — hedef değil parazit olabilir";
            cluster.confidence *= 0.35;
        } else {
            cluster.falsePositiveRisk = false;
            cluster.falsePositiveReason = null;
        }
    }

    /**
     * Section 25: a lightweight, honest "which library scenario does this
     * profile resemble" heuristic for real-device data — reuses the same
     * per-cluster classification this engine already produces rather than
     * re-deriving a separate signal comparison. Explicitly a rough profile
     * match, not a diagnosis (per the spec's own caveat).
     */
    public static String suggestSimilarScenario(List<SimAnomalyCluster> clusters) {
        if (clusters.isEmpty()) {
            return "Belirgin bir anomali bulunamadı — temiz zemin / 'Sadece Jeoloji' senaryosuna yakın.";
        }

        boolean hasPositive = false, hasNegative = false;
        double maxHalfWidthDepth = 0;
        TargetType best = null;
        double bestConfidence = -1;
        for (SimAnomalyCluster c : clusters) {
            if (c.peakAmplitude > 0) hasPositive = true;
            else hasNegative = true;
            maxHalfWidthDepth = Math.max(maxHalfWidthDepth, c.depthEstimateHalfWidthM);
            if (c.candidateType != null && c.confidence > bestConfidence) {
                bestConfidence = c.confidence;
                best = c.candidateType;
            }
        }

        String base;
        if (hasPositive && hasNegative) {
            base = "Metal + Boşluk";
        } else if (best == null) {
            base = "belirsiz bir profil";
        } else {
            switch (best) {
                case METAL:
                    base = clusters.size() > 1 ? "İki Metal" : "Tek Metal";
                    break;
                case VOID:
                    base = "Boşluk";
                    break;
                case ROOM:
                    base = "Oda";
                    break;
                case TUNNEL:
                    base = "Tünel";
                    break;
                case MINERALIZED_ZONE:
                    base = "Mineralizasyon";
                    break;
                default:
                    base = best.displayNameTr;
                    break;
            }
        }

        String depthNote = maxHalfWidthDepth > 2.5 ? " (Derin Büyük Hedef profiline yakın)"
                : maxHalfWidthDepth > 0 && maxHalfWidthDepth < 0.5 ? " (Sığ Küçük Hedef profiline yakın)" : "";
        return base + " senaryosuna benziyor" + depthNote + " — bu kesin bir teşhis değil, analiz desteğidir.";
    }

    private static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom > 1e-9 ? dot / denom : 0;
    }
}
