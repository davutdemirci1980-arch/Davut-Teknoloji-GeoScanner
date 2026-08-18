package com.geoscanner.app.data;

import android.content.Context;
import android.util.Log;

import com.geoscanner.app.utils.CryptoUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class FileManager {
    private static final String TAG = "FileManager";

    public static File getScanDirectory(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "geoscanner_scans");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File save7ESX(Context context, String name, List<ScanDataPoint> points, int gridW, int gridH, int stepSize) {
        try {
            JSONArray dataArray = new JSONArray();
            for (ScanDataPoint p : points) dataArray.put(p.toJSON());

            JSONObject meta = new JSONObject();
            meta.put("gridWidth", gridW);
            meta.put("gridHeight", gridH);
            meta.put("stepSize", stepSize);
            meta.put("timestamp", System.currentTimeMillis());
            meta.put("appVersion", "DavutTeknoloji 1.0");

            JSONObject root = new JSONObject();
            root.put("name", name);
            root.put("data", dataArray);
            root.put("metadata", meta);

            String encrypted = CryptoUtils.encrypt(root.toString());
            File dir = getScanDirectory(context);
            File file = new File(dir, name + ".7esx");
            int counter = 1;
            while (file.exists()) {
                file = new File(dir, name + "_" + counter + ".7esx");
                counter++;
            }
            writeFile(file, encrypted);
            Log.i(TAG, "Saved 7ESX: " + file.getName());
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Save 7ESX error: " + e.getMessage());
            return null;
        }
    }

    public static File saveCSV(Context context, String name, List<ScanDataPoint> points) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# name=").append(name).append("\n");
            sb.append("# direction=zigzag\n");
            sb.append("X,Y,Z,C\n");
            for (ScanDataPoint p : points) {
                sb.append(String.format(Locale.US, "%d,%d,%.4f,%.4f\n", p.x, p.y, p.z, p.c));
            }
            File dir = getScanDirectory(context);
            File file = new File(dir, name + ".csv");
            writeFile(file, sb.toString());
            Log.i(TAG, "Saved CSV: " + file.getName());
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Save CSV error: " + e.getMessage());
            return null;
        }
    }

    public static File exportVTK(Context context, String name, List<ScanDataPoint> points) {
        if (points == null || points.isEmpty()) return null;
        try {
            TreeSet<Integer> xSet = new TreeSet<>();
            TreeSet<Integer> ySet = new TreeSet<>();
            for (ScanDataPoint p : points) {
                xSet.add(p.x);
                ySet.add(p.y);
            }
            List<Integer> xList = new ArrayList<>(xSet);
            List<Integer> yList = new ArrayList<>(ySet);
            int nx = xList.size();
            int ny = yList.size();
            if (nx == 0 || ny == 0) return null;

            double dx = nx > 1 ? xList.get(1) - xList.get(0) : 30.0;
            double dy = ny > 1 ? yList.get(1) - yList.get(0) : 30.0;
            double[][] anomalyGrid = new double[ny][nx];
            double[][] depthGrid = new double[ny][nx];
            for (ScanDataPoint p : points) {
                int xi = xList.indexOf(p.x);
                int yi = yList.indexOf(p.y);
                if (xi >= 0 && yi >= 0) {
                    anomalyGrid[yi][xi] = p.c;
                    depthGrid[yi][xi] = p.z;
                }
            }

            double maxDepth = 100.0;
            for (ScanDataPoint p : points) maxDepth = Math.max(maxDepth, Math.max(p.z, 100.0));
            int nz = 20;
            double dz = maxDepth / (nz - 1);

            StringBuilder sb = new StringBuilder();
            sb.append("# vtk DataFile Version 3.0\r\n");
            sb.append("Davut Teknoloji - ").append(name).append("\r\n");
            sb.append("ASCII\r\n");
            sb.append("DATASET STRUCTURED_POINTS\r\n");
            sb.append(String.format(Locale.US, "DIMENSIONS %d %d %d\r\n", nx, ny, nz));
            sb.append(String.format(Locale.US, "ORIGIN %.6f %.6f %.6f\r\n", (double) xList.get(0), (double) yList.get(0), 0.0));
            sb.append(String.format(Locale.US, "SPACING %.6f %.6f %.6f\r\n", dx, dy, dz));
            sb.append("\r\n");
            sb.append(String.format(Locale.US, "POINT_DATA %d\r\n", nx * ny * nz));
            sb.append("SCALARS anomaly float 1\r\n");
            sb.append("LOOKUP_TABLE default\r\n");

            for (int k = 0; k < nz; k++) {
                double currentDepth = k * dz;
                for (int j = 0; j < ny; j++) {
                    StringBuilder line = new StringBuilder();
                    for (int i = 0; i < nx; i++) {
                        double anomaly = anomalyGrid[j][i];
                        double peakDepth = depthGrid[j][i];
                        double sigma = Math.max(peakDepth / 2.0, 10.0);
                        double diff = currentDepth - peakDepth;
                        double value = Math.abs(anomaly) * Math.exp(-(diff * diff) / (2.0 * sigma * sigma));
                        if (i > 0) line.append(" ");
                        line.append(String.format(Locale.US, "%.4f", value));
                    }
                    sb.append(line).append("\r\n");
                }
            }

            File dir = getScanDirectory(context);
            File file = new File(dir, name + ".vtk");
            writeFile(file, sb.toString());
            Log.i(TAG, "Exported VTK: " + file.getName());
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Export VTK error: " + e.getMessage());
            return null;
        }
    }

    public static File exportGRD(Context context, String name, List<ScanDataPoint> points) {
        if (points == null || points.isEmpty()) return null;
        try {
            TreeSet<Integer> xSet = new TreeSet<>();
            TreeSet<Integer> ySet = new TreeSet<>();
            for (ScanDataPoint p : points) {
                xSet.add(p.x);
                ySet.add(p.y);
            }
            List<Integer> xList = new ArrayList<>(xSet);
            List<Integer> yList = new ArrayList<>(ySet);
            int nx = xList.size();
            int ny = yList.size();
            if (nx == 0 || ny == 0) return null;

            double noData = 1.70141e38;
            double[][] grid = new double[ny][nx];
            for (double[] row : grid) Arrays.fill(row, noData);
            for (ScanDataPoint p : points) {
                int xi = xList.indexOf(p.x);
                int yi = yList.indexOf(p.y);
                if (xi >= 0 && yi >= 0) grid[yi][xi] = p.c;
            }

            double zMin = Double.MAX_VALUE, zMax = -Double.MAX_VALUE;
            for (ScanDataPoint p : points) {
                zMin = Math.min(zMin, p.c);
                zMax = Math.max(zMax, p.c);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("DSAA\r\n");
            sb.append(nx).append(" ").append(ny).append("\r\n");
            sb.append(String.format(Locale.US, "%.6f %.6f\r\n", (double) xList.get(0), (double) xList.get(xList.size() - 1)));
            sb.append(String.format(Locale.US, "%.6f %.6f\r\n", (double) yList.get(0), (double) yList.get(yList.size() - 1)));
            sb.append(String.format(Locale.US, "%.6f %.6f\r\n", zMin, zMax));
            for (int j = 0; j < ny; j++) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < nx; i++) {
                    if (i > 0) line.append(" ");
                    double val = grid[j][i];
                    line.append(val >= noData ? "1.70141e+038" : String.format(Locale.US, "%.6f", val));
                }
                sb.append(line).append("\r\n");
            }

            File dir = getScanDirectory(context);
            File file = new File(dir, name + ".grd");
            writeFile(file, sb.toString());
            Log.i(TAG, "Exported GRD: " + file.getName());
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Export GRD error: " + e.getMessage());
            return null;
        }
    }

    public static List<ScanDataPoint> read7ESX(File file) {
        try {
            String encrypted = readFile(file);
            String json = CryptoUtils.decrypt(encrypted);
            if (json == null || json.isEmpty()) return null;

            List<ScanDataPoint> points = new ArrayList<>();
            JSONArray dataArray = json.contains("\"data\"")
                    ? new JSONObject(json).getJSONArray("data")
                    : new JSONArray(json);
            for (int i = 0; i < dataArray.length(); i++) {
                points.add(ScanDataPoint.fromJSON(dataArray.getJSONObject(i)));
            }
            return points;
        } catch (Exception e) {
            Log.e(TAG, "Read 7ESX error: " + e.getMessage());
            return null;
        }
    }

    public static List<ScanDataPoint> readCSV(File file) {
        try {
            String content = readFile(file);
            if (content.startsWith("﻿")) content = content.substring(1);
            String[] lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n");

            boolean isZigzag = false;
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.toLowerCase(Locale.US).contains("direction=zigzag")) isZigzag = true;
            }

            List<ScanDataPoint> points = new ArrayList<>();
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("x") || lower.startsWith("\"x") || lower.contains("longitude")) continue;

                String[] parts = line.split("[,;\\t]+");
                try {
                    if (parts.length >= 4) {
                        double xd = Double.parseDouble(parts[0].trim().replace("\"", ""));
                        double yd = Double.parseDouble(parts[1].trim().replace("\"", ""));
                        double z = Double.parseDouble(parts[2].trim().replace("\"", ""));
                        double c = Double.parseDouble(parts[3].trim().replace("\"", ""));
                        points.add(new ScanDataPoint((int) xd, (int) yd, z, c));
                    } else if (parts.length == 3) {
                        double xd = Double.parseDouble(parts[0].trim().replace("\"", ""));
                        double yd = Double.parseDouble(parts[1].trim().replace("\"", ""));
                        double c = Double.parseDouble(parts[2].trim().replace("\"", ""));
                        points.add(new ScanDataPoint((int) xd, (int) yd, 0.0, c));
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            if (points.isEmpty()) return null;

            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (ScanDataPoint p : points) {
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }
            if (points.get(0).y == maxY && maxY != minY) {
                for (ScanDataPoint p : points) p.y = maxY - p.y + minY;
            }

            if (!isZigzag) isZigzag = detectZigzag(points);
            if (isZigzag) correctZigzagDirection(points);

            return points;
        } catch (Exception e) {
            Log.e(TAG, "Read CSV error: " + e.getMessage());
            return null;
        }
    }

    private static boolean detectZigzag(List<ScanDataPoint> points) {
        TreeMap<Integer, List<ScanDataPoint>> rowMap = new TreeMap<>();
        for (ScanDataPoint p : points) {
            rowMap.computeIfAbsent(p.y, k -> new ArrayList<>()).add(p);
        }
        if (rowMap.size() < 2) return false;

        List<Integer> yKeys = new ArrayList<>(rowMap.keySet());
        int forwardCount = 0, reverseCount = 0;
        for (int i = 0; i < yKeys.size(); i++) {
            List<ScanDataPoint> row = rowMap.get(yKeys.get(i));
            if (row.size() < 2) continue;
            boolean ascending = row.get(0).x <= row.get(row.size() - 1).x;
            boolean isEvenRow = i % 2 == 0;
            if (isEvenRow == ascending) forwardCount++;
            else reverseCount++;
        }
        return reverseCount > 0 && forwardCount > 0;
    }

    private static void correctZigzagDirection(List<ScanDataPoint> points) {
        TreeMap<Integer, List<ScanDataPoint>> rowMap = new TreeMap<>();
        for (ScanDataPoint p : points) {
            rowMap.computeIfAbsent(p.y, k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<Integer, List<ScanDataPoint>> entry : rowMap.entrySet()) {
            List<ScanDataPoint> row = entry.getValue();
            if (row.size() < 2) continue;
            boolean isReversed = row.get(0).x > row.get(row.size() - 1).x;
            if (isReversed) {
                int n = row.size();
                int[] sortedRowX = new int[n];
                for (int i = 0; i < n; i++) sortedRowX[i] = row.get(i).x;
                Arrays.sort(sortedRowX);
                for (int i = 0; i < n; i++) row.get(i).x = sortedRowX[i];
            }
        }
    }

    public static List<ScanDataPoint> readVTK(File file) {
        try {
            String content = readFile(file);
            String[] lines = content.replace("\r\n", "\n").split("\n");
            List<Float> values = new ArrayList<>();
            boolean readingData = false;
            double sx = 1, sy = 1, sz = 1, ox = 0, oy = 0, oz = 0;
            int nx = 0, ny = 0, nz = 0;

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.startsWith("DIMENSIONS")) {
                    String[] p = line.split("\\s+");
                    nx = Integer.parseInt(p[1]);
                    ny = Integer.parseInt(p[2]);
                    nz = Integer.parseInt(p[3]);
                } else if (line.startsWith("ORIGIN")) {
                    String[] p = line.split("\\s+");
                    ox = Double.parseDouble(p[1]);
                    oy = Double.parseDouble(p[2]);
                    oz = Double.parseDouble(p[3]);
                } else if (line.startsWith("SPACING")) {
                    String[] p = line.split("\\s+");
                    sx = Double.parseDouble(p[1]);
                    sy = Double.parseDouble(p[2]);
                    sz = Double.parseDouble(p[3]);
                } else if (line.startsWith("LOOKUP_TABLE")) {
                    readingData = true;
                } else if (readingData && !line.isEmpty()) {
                    for (String v : line.split("\\s+")) {
                        try {
                            values.add(Float.parseFloat(v));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            if (nx == 0 || ny == 0) return null;

            List<ScanDataPoint> points = new ArrayList<>();
            for (int j = 0; j < ny; j++) {
                for (int i = 0; i < nx; i++) {
                    double maxVal = 0, bestDepth = 0;
                    for (int k = 0; k < nz; k++) {
                        int idx = k * ny * nx + j * nx + i;
                        float val = idx < values.size() ? values.get(idx) : 0f;
                        if (Math.abs(val) > Math.abs(maxVal)) {
                            bestDepth = k * sz + oz;
                            maxVal = val;
                        }
                    }
                    points.add(new ScanDataPoint((int) (i * sx + ox), (int) (oy + j * sy), bestDepth, maxVal));
                }
            }
            return points;
        } catch (Exception e) {
            Log.e(TAG, "Read VTK error: " + e.getMessage());
            return null;
        }
    }

    public static List<ScanDataPoint> readGRD(File file) {
        try {
            String content = readFile(file);
            String[] lines = content.replace("\r\n", "\n").split("\n");
            if (lines.length < 5 || !lines[0].trim().equals("DSAA")) return null;

            String[] dims = lines[1].trim().split("\\s+");
            int nx = Integer.parseInt(dims[0]);
            int ny = Integer.parseInt(dims[1]);
            String[] xRange = lines[2].trim().split("\\s+");
            double xMin = Double.parseDouble(xRange[0]);
            double xMax = Double.parseDouble(xRange[1]);
            String[] yRange = lines[3].trim().split("\\s+");
            double yMin = Double.parseDouble(yRange[0]);
            double yMax = Double.parseDouble(yRange[1]);
            double dx = nx > 1 ? (xMax - xMin) / (nx - 1) : 1.0;
            double dy = ny > 1 ? (yMax - yMin) / (ny - 1) : 1.0;

            List<ScanDataPoint> points = new ArrayList<>();
            int lineIdx = 5;
            for (int j = 0; j < ny && lineIdx < lines.length; j++, lineIdx++) {
                String[] vals = lines[lineIdx].trim().split("\\s+");
                for (int i = 0; i < nx && i < vals.length; i++) {
                    double val = Double.parseDouble(vals[i]);
                    if (val < 1.7e38) {
                        points.add(new ScanDataPoint((int) (i * dx + xMin), (int) (j * dy + yMin), 0.0, val));
                    }
                }
            }
            return points.isEmpty() ? null : points;
        } catch (Exception e) {
            Log.e(TAG, "Read GRD error: " + e.getMessage());
            return null;
        }
    }

    public static List<ScanDataPoint> readDAT(File file) {
        try {
            String content = readFile(file);
            String[] lines = content.replace("\r\n", "\n").split("\n");
            List<ScanDataPoint> points = new ArrayList<>();
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[DATA]") || line.startsWith("BEGIN_DATA") || line.startsWith("END_DATA") || line.startsWith("[")) {
                    continue;
                }
                String[] parts = line.split("[\\s,;\\t]+");
                if (parts.length >= 3) {
                    try {
                        int x = (int) Double.parseDouble(parts[0]);
                        int y = (int) Double.parseDouble(parts[1]);
                        double c = Double.parseDouble(parts[2]);
                        double z = parts.length >= 4 ? Double.parseDouble(parts[3]) : 0.0;
                        points.add(new ScanDataPoint(x, y, z, c));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return points.isEmpty() ? null : points;
        } catch (Exception e) {
            Log.e(TAG, "Read DAT error: " + e.getMessage());
            return null;
        }
    }

    public static List<ScanDataPoint> readAuto(File file) {
        String ext = getExtension(file).toLowerCase(Locale.US);
        switch (ext) {
            case "7esx": return read7ESX(file);
            case "csv": return readCSV(file);
            case "vtk": return readVTK(file);
            case "grd": return readGRD(file);
            case "dat": return readDAT(file);
            default:
                List<ScanDataPoint> pts = readCSV(file);
                return pts != null ? pts : readDAT(file);
        }
    }

    public static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    public static String getNameWithoutExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    public static List<File> listScanFiles(Context context) {
        File dir = getScanDirectory(context);
        File[] files = dir.listFiles((d, n) -> {
            String lower = n.toLowerCase(Locale.US);
            return lower.endsWith(".7esx") || lower.endsWith(".csv") || lower.endsWith(".vtk") || lower.endsWith(".grd") || lower.endsWith(".dat");
        });
        List<File> list = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            list.addAll(Arrays.asList(files));
        }
        return list;
    }
}
