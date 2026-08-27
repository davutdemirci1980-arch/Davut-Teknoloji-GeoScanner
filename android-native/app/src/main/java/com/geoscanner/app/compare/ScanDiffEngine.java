package com.geoscanner.app.compare;

import com.geoscanner.app.data.ScanDataPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Compares two CSV scans of the same area taken at different times.
 * <p>
 * Coordinates in {@link ScanDataPoint} are real-world centimeters, so the two
 * scans are first snapped onto a shared cm grid, then auto-aligned by
 * searching small integer-cell shifts for the one that best correlates the
 * overlapping readings (handles the operator not starting from the exact
 * same spot). The remaining per-cell differences are thresholded against the
 * overlap's own noise level, and connected "changed" cells are clustered
 * into regions with a bounding box (in meters) and a percentage change.
 */
public class ScanDiffEngine {
    private static final int MAX_SHIFT_CELLS = 3;
    private static final int MIN_OVERLAP_FOR_SHIFT = 5;
    private static final double DEFAULT_STEP_CM = 30.0;

    public static ScanDiffResult compare(List<ScanDataPoint> pointsA, List<ScanDataPoint> pointsB) {
        if (pointsA == null || pointsB == null || pointsA.isEmpty() || pointsB.isEmpty()) return null;

        Grid gridA = buildGrid(pointsA);
        Grid gridB = buildGrid(pointsB);
        if (gridA == null || gridB == null) return null;

        double stepXCm = gridA.stepX > 0 ? gridA.stepX : (gridB.stepX > 0 ? gridB.stepX : DEFAULT_STEP_CM);
        double stepYCm = gridA.stepY > 0 ? gridA.stepY : (gridB.stepY > 0 ? gridB.stepY : DEFAULT_STEP_CM);
        double originXCm = Math.min(gridA.originX, gridB.originX);
        double originYCm = Math.min(gridA.originY, gridB.originY);

        Map<Long, Double> a = reindex(gridA, originXCm, originYCm, stepXCm, stepYCm);
        Map<Long, Double> b = reindex(gridB, originXCm, originYCm, stepXCm, stepYCm);

        int[] shift = findBestShift(a, b);
        int shiftX = shift[0];
        int shiftY = shift[1];

        int minIx = Integer.MAX_VALUE, maxIx = Integer.MIN_VALUE, minIy = Integer.MAX_VALUE, maxIy = Integer.MIN_VALUE;
        for (long key : a.keySet()) {
            int ix = keyX(key), iy = keyY(key);
            minIx = Math.min(minIx, ix); maxIx = Math.max(maxIx, ix);
            minIy = Math.min(minIy, iy); maxIy = Math.max(maxIy, iy);
        }
        for (long key : b.keySet()) {
            int ix = keyX(key) - shiftX, iy = keyY(key) - shiftY;
            minIx = Math.min(minIx, ix); maxIx = Math.max(maxIx, ix);
            minIy = Math.min(minIy, iy); maxIy = Math.max(maxIy, iy);
        }
        if (minIx > maxIx || minIy > maxIy) return null;

        int nx = maxIx - minIx + 1;
        int ny = maxIy - minIy + 1;
        if ((long) nx * ny > 400_000L) return null;

        DiffCell[][] cells = new DiffCell[ny][nx];
        for (int iy = 0; iy < ny; iy++) {
            for (int ix = 0; ix < nx; ix++) {
                cells[iy][ix] = new DiffCell();
            }
        }

        double valMin = Double.MAX_VALUE, valMax = -Double.MAX_VALUE;
        List<Double> diffs = new ArrayList<>();
        for (Map.Entry<Long, Double> e : a.entrySet()) {
            int ix = keyX(e.getKey()) - minIx, iy = keyY(e.getKey()) - minIy;
            DiffCell cell = cells[iy][ix];
            cell.hasA = true;
            cell.valueA = e.getValue();
            valMin = Math.min(valMin, cell.valueA);
            valMax = Math.max(valMax, cell.valueA);
        }
        for (Map.Entry<Long, Double> e : b.entrySet()) {
            int ix = keyX(e.getKey()) - shiftX - minIx, iy = keyY(e.getKey()) - shiftY - minIy;
            if (ix < 0 || ix >= nx || iy < 0 || iy >= ny) continue;
            DiffCell cell = cells[iy][ix];
            cell.hasB = true;
            cell.valueB = e.getValue();
            valMin = Math.min(valMin, cell.valueB);
            valMax = Math.max(valMax, cell.valueB);
        }

        int overlapCells = 0;
        double sumAbs = 0;
        for (int iy = 0; iy < ny; iy++) {
            for (int ix = 0; ix < nx; ix++) {
                DiffCell cell = cells[iy][ix];
                if (cell.hasA && cell.hasB) {
                    cell.diff = cell.valueB - cell.valueA;
                    diffs.add(cell.diff);
                    sumAbs += Math.abs(cell.diff);
                    overlapCells++;
                }
            }
        }

        ScanDiffResult result = new ScanDiffResult();
        result.nx = nx;
        result.ny = ny;
        result.cells = cells;
        result.originXm = (originXCm + minIx * stepXCm) / 100.0;
        result.originYm = (originYCm + minIy * stepYCm) / 100.0;
        result.stepXm = stepXCm / 100.0;
        result.stepYm = stepYCm / 100.0;
        result.shiftX = shiftX;
        result.shiftY = shiftY;
        result.overlapCells = overlapCells;

        if (overlapCells == 0) {
            result.correlationScore = 0;
            result.overallChangedPercent = 0;
            return result;
        }

        double meanAbs = sumAbs / overlapCells;
        double varAbs = 0;
        for (double d : diffs) varAbs += Math.pow(Math.abs(d) - meanAbs, 2);
        double stdAbs = Math.sqrt(varAbs / overlapCells);
        double valueRange = Math.max(valMax - valMin, 1e-9);
        double threshold = Math.max(0.12 * valueRange, meanAbs + 1.5 * stdAbs);
        if (threshold <= 0) threshold = 1e-6;
        result.threshold = threshold;

        double maxAbs = 0;
        int changed = 0;
        for (int iy = 0; iy < ny; iy++) {
            for (int ix = 0; ix < nx; ix++) {
                DiffCell cell = cells[iy][ix];
                if (!cell.hasA || !cell.hasB) continue;
                maxAbs = Math.max(maxAbs, Math.abs(cell.diff));
                if (Math.abs(cell.diff) >= threshold) {
                    cell.changed = true;
                    changed++;
                }
            }
        }
        result.maxAbsDiff = maxAbs;
        result.changedCells = changed;
        result.overallChangedPercent = changed * 100.0 / overlapCells;
        result.correlationScore = pearson(a, b, shiftX, shiftY);
        result.regions = clusterRegions(cells, nx, ny, result.originXm, result.originYm, result.stepXm, result.stepYm);
        return result;
    }

    private static List<DiffRegion> clusterRegions(DiffCell[][] cells, int nx, int ny,
                                                     double originXm, double originYm, double stepXm, double stepYm) {
        List<DiffRegion> regions = new ArrayList<>();
        boolean[][] visited = new boolean[ny][nx];
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        for (int iy = 0; iy < ny; iy++) {
            for (int ix = 0; ix < nx; ix++) {
                if (visited[iy][ix] || !cells[iy][ix].changed) continue;

                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{ix, iy});
                visited[iy][ix] = true;
                int minIx = ix, maxIx = ix, minIy = iy, maxIy = iy;
                int count = 0;
                double sumDiff = 0, sumAbsBaseline = 0;

                while (!queue.isEmpty()) {
                    int[] p = queue.poll();
                    int cx = p[0], cy = p[1];
                    DiffCell cell = cells[cy][cx];
                    count++;
                    sumDiff += cell.diff;
                    sumAbsBaseline += Math.abs(cell.valueA);
                    minIx = Math.min(minIx, cx); maxIx = Math.max(maxIx, cx);
                    minIy = Math.min(minIy, cy); maxIy = Math.max(maxIy, cy);

                    for (int d = 0; d < 4; d++) {
                        int nxi = cx + dx[d], nyi = cy + dy[d];
                        if (nxi < 0 || nxi >= nx || nyi < 0 || nyi >= ny) continue;
                        if (visited[nyi][nxi] || !cells[nyi][nxi].changed) continue;
                        visited[nyi][nxi] = true;
                        queue.add(new int[]{nxi, nyi});
                    }
                }

                DiffRegion region = new DiffRegion();
                region.cellCount = count;
                region.avgDiff = sumDiff / count;
                region.increase = region.avgDiff >= 0;
                double avgBaseline = Math.max(sumAbsBaseline / count, 1e-9);
                region.pctChange = Math.abs(region.avgDiff) / avgBaseline * 100.0;
                region.minXm = originXm + minIx * stepXm;
                region.maxXm = originXm + (maxIx + 1) * stepXm;
                region.minYm = originYm + minIy * stepYm;
                region.maxYm = originYm + (maxIy + 1) * stepYm;
                regions.add(region);
            }
        }

        regions.sort((r1, r2) -> Integer.compare(r2.cellCount, r1.cellCount));
        return regions;
    }

    private static int[] findBestShift(Map<Long, Double> a, Map<Long, Double> b) {
        int bestSx = 0, bestSy = 0;
        double bestScore = -Double.MAX_VALUE;
        boolean found = false;

        for (int sx = -MAX_SHIFT_CELLS; sx <= MAX_SHIFT_CELLS; sx++) {
            for (int sy = -MAX_SHIFT_CELLS; sy <= MAX_SHIFT_CELLS; sy++) {
                List<Double> xs = new ArrayList<>();
                List<Double> ys = new ArrayList<>();
                for (Map.Entry<Long, Double> e : a.entrySet()) {
                    int ix = keyX(e.getKey()), iy = keyY(e.getKey());
                    Double vb = b.get(key(ix + sx, iy + sy));
                    if (vb != null) {
                        xs.add(e.getValue());
                        ys.add(vb);
                    }
                }
                if (xs.size() < MIN_OVERLAP_FOR_SHIFT) continue;
                double score = pearsonScore(xs, ys) * Math.min(1.0, xs.size() / 30.0);
                if (score > bestScore) {
                    bestScore = score;
                    bestSx = sx;
                    bestSy = sy;
                    found = true;
                }
            }
        }
        return found ? new int[]{bestSx, bestSy} : new int[]{0, 0};
    }

    private static double pearson(Map<Long, Double> a, Map<Long, Double> b, int shiftX, int shiftY) {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (Map.Entry<Long, Double> e : a.entrySet()) {
            int ix = keyX(e.getKey()), iy = keyY(e.getKey());
            Double vb = b.get(key(ix + shiftX, iy + shiftY));
            if (vb != null) {
                xs.add(e.getValue());
                ys.add(vb);
            }
        }
        return pearsonScore(xs, ys);
    }

    private static double pearsonScore(List<Double> xs, List<Double> ys) {
        int n = xs.size();
        if (n < 2) return 0;
        double meanX = 0, meanY = 0;
        for (int i = 0; i < n; i++) { meanX += xs.get(i); meanY += ys.get(i); }
        meanX /= n; meanY /= n;
        double cov = 0, varX = 0, varY = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs.get(i) - meanX, dy = ys.get(i) - meanY;
            cov += dx * dy; varX += dx * dx; varY += dy * dy;
        }
        double denom = Math.sqrt(varX) * Math.sqrt(varY);
        return denom > 1e-9 ? cov / denom : 0;
    }

    private static Map<Long, Double> reindex(Grid grid, double originXCm, double originYCm, double stepXCm, double stepYCm) {
        Map<Long, Double> out = new HashMap<>();
        for (Map.Entry<Long, Double> e : grid.values.entrySet()) {
            int oldIx = keyX(e.getKey()), oldIy = keyY(e.getKey());
            double xCm = grid.originX + oldIx * grid.stepX;
            double yCm = grid.originY + oldIy * grid.stepY;
            int ix = (int) Math.round((xCm - originXCm) / stepXCm);
            int iy = (int) Math.round((yCm - originYCm) / stepYCm);
            out.put(key(ix, iy), e.getValue());
        }
        return out;
    }

    private static long key(int ix, int iy) {
        return ((long) (ix + 1_000_000) << 32) | (long) (iy + 1_000_000);
    }

    private static int keyX(long key) {
        return (int) (key >> 32) - 1_000_000;
    }

    private static int keyY(long key) {
        return (int) key - 1_000_000;
    }

    private static Grid buildGrid(List<ScanDataPoint> points) {
        if (points == null || points.isEmpty()) return null;
        TreeSet<Integer> xs = new TreeSet<>();
        TreeSet<Integer> ys = new TreeSet<>();
        for (ScanDataPoint p : points) {
            xs.add(p.x);
            ys.add(p.y);
        }
        double stepX = medianStep(xs);
        double stepY = medianStep(ys);
        double originX = xs.first();
        double originY = ys.first();
        if (stepX <= 0) stepX = DEFAULT_STEP_CM;
        if (stepY <= 0) stepY = DEFAULT_STEP_CM;

        Map<Long, Double> values = new HashMap<>();
        for (ScanDataPoint p : points) {
            int ix = (int) Math.round((p.x - originX) / stepX);
            int iy = (int) Math.round((p.y - originY) / stepY);
            values.put(key(ix, iy), p.c);
        }

        Grid grid = new Grid();
        grid.stepX = stepX;
        grid.stepY = stepY;
        grid.originX = originX;
        grid.originY = originY;
        grid.values = values;
        return grid;
    }

    private static double medianStep(TreeSet<Integer> sorted) {
        if (sorted.size() < 2) return 0;
        List<Integer> list = new ArrayList<>(sorted);
        List<Integer> diffs = new ArrayList<>();
        for (int i = 1; i < list.size(); i++) {
            int d = list.get(i) - list.get(i - 1);
            if (d > 0) diffs.add(d);
        }
        if (diffs.isEmpty()) return 0;
        diffs.sort(null);
        return diffs.get(diffs.size() / 2);
    }

    private static class Grid {
        double stepX, stepY, originX, originY;
        Map<Long, Double> values;
    }
}
