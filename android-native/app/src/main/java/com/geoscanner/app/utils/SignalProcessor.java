package com.geoscanner.app.utils;

import java.util.ArrayList;
import java.util.Collections;

public class SignalProcessor {
    public static final int INTERP_NEAREST = 0;
    public static final int INTERP_LINEAR = 1;
    public static final int INTERP_CUBIC = 2;
    public static final int INTERP_BSPLINE = 3;
    public static final int INTERP_SINC = 4;
    public static final int INTERP_GAUSSIAN = 5;

    public static float normalizeDepth(float value, float gridW, float gridH) {
        float abs = Math.abs(value);
        if (abs < 0.01f) {
            return 0.0f;
        }
        float scale = abs > 1.0f ? 100.0f : 10000.0f;
        float raw = Math.abs(((gridW * gridH) / scale) / abs);
        return clamp(raw, 5.0f, 1500.0f);
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float gaussianInterpolate(double surfaceValue, double depth, double peakDepth, double sigma) {
        if (sigma < 10.0) sigma = 10.0;
        double diff = depth - peakDepth;
        return (float) (Math.abs(surfaceValue) * Math.exp(-(diff * diff) / (2.0 * sigma * sigma)));
    }

    public static double[][] interpolateGrid2D(double[][] data, int targetRows, int targetCols, int mode) {
        if (data == null || data.length == 0 || data[0].length == 0) return data;
        int srcRows = data.length;
        int srcCols = data[0].length;
        double[][] result = new double[targetRows][targetCols];
        for (int i = 0; i < targetRows; i++) {
            for (int j = 0; j < targetCols; j++) {
                double srcI = ((double) i / (targetRows - 1)) * (srcRows - 1);
                double srcJ = ((double) j / (targetCols - 1)) * (srcCols - 1);
                switch (mode) {
                    case INTERP_NEAREST:
                        result[i][j] = nearestNeighbor(data, srcI, srcJ);
                        break;
                    case INTERP_LINEAR:
                        result[i][j] = bilinearInterpolate(data, srcI, srcJ);
                        break;
                    case INTERP_CUBIC:
                        result[i][j] = bicubicInterpolate(data, srcI, srcJ);
                        break;
                    case INTERP_BSPLINE:
                        result[i][j] = bsplineInterpolate(data, srcI, srcJ);
                        break;
                    case INTERP_SINC:
                        result[i][j] = sincInterpolate(data, srcI, srcJ);
                        break;
                    case INTERP_GAUSSIAN:
                        result[i][j] = gaussianInterpolate2D(data, srcI, srcJ);
                        break;
                    default:
                        result[i][j] = bilinearInterpolate(data, srcI, srcJ);
                        break;
                }
            }
        }
        return result;
    }

    private static double nearestNeighbor(double[][] data, double row, double col) {
        int r = (int) Math.round(row);
        int c = (int) Math.round(col);
        return data[clampInt(r, 0, data.length - 1)][clampInt(c, 0, data[0].length - 1)];
    }

    private static double bilinearInterpolate(double[][] data, double row, double col) {
        int rows = data.length;
        int cols = data[0].length;
        int r0 = Math.max(0, (int) Math.floor(row));
        int c0 = Math.max(0, (int) Math.floor(col));
        int r1 = Math.min(r0 + 1, rows - 1);
        int c1 = Math.min(c0 + 1, cols - 1);
        double fr = row - r0;
        double fc = col - c0;
        double v00 = data[r0][c0];
        double v01 = data[r0][c1];
        double v10 = data[r1][c0];
        double v11 = data[r1][c1];
        return (1 - fr) * (1 - fc) * v00 + (1 - fr) * fc * v01 + fr * (1 - fc) * v10 + fr * fc * v11;
    }

    private static double bicubicInterpolate(double[][] data, double row, double col) {
        int rows = data.length;
        int cols = data[0].length;
        int r = (int) Math.floor(row);
        int c = (int) Math.floor(col);
        double fr = row - r;
        double fc = col - c;
        double result = 0.0;
        for (int m = -1; m <= 2; m++) {
            for (int n = -1; n <= 2; n++) {
                int ri = clampInt(r + m, 0, rows - 1);
                int ci = clampInt(c + n, 0, cols - 1);
                double weight = cubicKernel(m - fr) * cubicKernel(n - fc);
                result += data[ri][ci] * weight;
            }
        }
        return result;
    }

    private static double cubicKernel(double x) {
        double a = -0.5;
        double ax = Math.abs(x);
        if (ax <= 1.0) {
            return (a + 2.0) * ax * ax * ax - (a + 3.0) * ax * ax + 1.0;
        }
        if (ax < 2.0) {
            return a * ax * ax * ax - 5.0 * a * ax * ax + 8.0 * a * ax - 4.0 * a;
        }
        return 0.0;
    }

    private static double bsplineInterpolate(double[][] data, double row, double col) {
        int rows = data.length;
        int cols = data[0].length;
        int r = (int) Math.floor(row);
        int c = (int) Math.floor(col);
        double fr = row - r;
        double fc = col - c;
        double result = 0.0;
        double weightSum = 0.0;
        for (int m = -1; m <= 2; m++) {
            for (int n = -1; n <= 2; n++) {
                int ri = clampInt(r + m, 0, rows - 1);
                int ci = clampInt(c + n, 0, cols - 1);
                double weight = bsplineBasis(m - fr) * bsplineBasis(n - fc);
                result += data[ri][ci] * weight;
                weightSum += weight;
            }
        }
        return weightSum > 0.0 ? result / weightSum : 0.0;
    }

    private static double bsplineBasis(double x) {
        double ax = Math.abs(x);
        if (ax < 1.0) {
            return (2.0 / 3.0) - ax * ax + 0.5 * ax * ax * ax;
        }
        if (ax < 2.0) {
            double t = 2.0 - ax;
            return (1.0 / 6.0) * t * t * t;
        }
        return 0.0;
    }

    private static double sincInterpolate(double[][] data, double row, double col) {
        int rows = data.length;
        int cols = data[0].length;
        int r = (int) Math.floor(row);
        int c = (int) Math.floor(col);
        double result = 0.0;
        double weightSum = 0.0;
        int a = 3;
        for (int m = -a + 1; m <= a; m++) {
            for (int n = -a + 1; n <= a; n++) {
                int ri = clampInt(r + m, 0, rows - 1);
                int ci = clampInt(c + n, 0, cols - 1);
                double dr = row - (r + m);
                double dc = col - (c + n);
                double weight = lanczosKernel(dr, a) * lanczosKernel(dc, a);
                result += data[ri][ci] * weight;
                weightSum += weight;
            }
        }
        return weightSum > 0.0 ? result / weightSum : 0.0;
    }

    private static double lanczosKernel(double x, int a) {
        if (x == 0.0) return 1.0;
        if (Math.abs(x) >= a) return 0.0;
        double px = Math.PI * x;
        return (Math.sin(px) / px) * (Math.sin(px / a) / (px / a));
    }

    private static double gaussianInterpolate2D(double[][] data, double row, double col) {
        int rows = data.length;
        int cols = data[0].length;
        int radius = 2;
        int r = (int) Math.floor(row);
        int c = (int) Math.floor(col);
        double result = 0.0;
        double weightSum = 0.0;
        for (int m = -radius; m <= radius + 1; m++) {
            for (int n = -radius; n <= radius + 1; n++) {
                int ri = clampInt(r + m, 0, rows - 1);
                int ci = clampInt(c + n, 0, cols - 1);
                double dr = row - (r + m);
                double dc = col - (c + n);
                double weight = Math.exp(-(dr * dr + dc * dc) / (2.0 * 0.8 * 0.8));
                result += data[ri][ci] * weight;
                weightSum += weight;
            }
        }
        return weightSum > 0.0 ? result / weightSum : 0.0;
    }

    /** Extrudes a 2D anomaly/depth grid pair into a 3D (depth, y, x) volume for VTK rendering. */
    public static double[][][] generateVolume3D(double[][] anomalyGrid, double[][] depthGrid, int nx, int ny, int nz, double maxDepth, int interpMode, int upsample) {
        double[][] upAnomaly = anomalyGrid;
        double[][] upDepth = depthGrid;
        int outNx = nx * upsample;
        int outNy = ny * upsample;
        if (upsample > 1) {
            upAnomaly = interpolateGrid2D(anomalyGrid, outNy, outNx, interpMode);
            upDepth = interpolateGrid2D(depthGrid, outNy, outNx, interpMode);
        }
        double dz = maxDepth / (nz - 1);
        double[][][] volume = new double[nz][outNy][outNx];
        for (int k = 0; k < nz; k++) {
            double currentDepth = k * dz;
            for (int j = 0; j < outNy; j++) {
                for (int i = 0; i < outNx; i++) {
                    double anomaly = upAnomaly[j][i];
                    double peakDepth = upDepth[j][i];
                    double sigma = Math.max(peakDepth / 2.0, 10.0);
                    double diff = currentDepth - peakDepth;
                    double depthFactor;
                    switch (interpMode) {
                        case INTERP_NEAREST:
                            depthFactor = Math.abs(diff) <= dz ? 1.0 : 0.0;
                            break;
                        case INTERP_LINEAR:
                            depthFactor = Math.max(0.0, 1.0 - Math.abs(diff) / (2.0 * sigma));
                            break;
                        case INTERP_CUBIC: {
                            double t = Math.abs(diff) / (sigma * 2.0);
                            depthFactor = t < 1.0 ? 2.0 * t * t * t - 3.0 * t * t + 1.0 : 0.0;
                            break;
                        }
                        case INTERP_BSPLINE:
                            depthFactor = bsplineBasis(diff / sigma);
                            break;
                        case INTERP_SINC: {
                            double x = diff / sigma;
                            if (Math.abs(x) < 0.001) {
                                depthFactor = 1.0;
                            } else {
                                double px = Math.PI * x;
                                depthFactor = Math.abs(Math.sin(px) / px);
                            }
                            break;
                        }
                        default:
                            depthFactor = Math.exp(-(diff * diff) / (2.0 * sigma * sigma));
                            break;
                    }
                    volume[k][j][i] = Math.abs(anomaly) * depthFactor;
                }
            }
        }
        return volume;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float[][] smoothGrid(float[][] data, int kernelSize) {
        if (data == null || data.length == 0) return data;
        int rows = data.length;
        int cols = data[0].length;
        float[][] result = new float[rows][cols];
        int half = kernelSize / 2;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float sum = 0;
                int count = 0;
                for (int di = -half; di <= half; di++) {
                    for (int dj = -half; dj <= half; dj++) {
                        int ni = i + di, nj = j + dj;
                        if (ni >= 0 && ni < rows && nj >= 0 && nj < cols) {
                            sum += data[ni][nj];
                            count++;
                        }
                    }
                }
                result[i][j] = count > 0 ? sum / count : 0f;
            }
        }
        return result;
    }

    public static float[][] gaussianBlur(float[][] data, float sigma) {
        if (data == null || data.length == 0) return data;
        int rows = data.length;
        int cols = data[0].length;
        int kernelSize = ((int) (3.0f * sigma)) * 2 + 1;
        float[][] kernel = createGaussianKernel(kernelSize, sigma);
        float[][] result = new float[rows][cols];
        int half = kernelSize / 2;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float sum = 0, weightSum = 0;
                for (int di = -half; di <= half; di++) {
                    for (int dj = -half; dj <= half; dj++) {
                        int ni = i + di, nj = j + dj;
                        if (ni >= 0 && ni < rows && nj >= 0 && nj < cols) {
                            float w = kernel[di + half][dj + half];
                            sum += data[ni][nj] * w;
                            weightSum += w;
                        }
                    }
                }
                result[i][j] = weightSum > 0 ? sum / weightSum : 0f;
            }
        }
        return result;
    }

    private static float[][] createGaussianKernel(int size, float sigma) {
        float[][] kernel = new float[size][size];
        int half = size / 2;
        float sum = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float x = i - half, y = j - half;
                kernel[i][j] = (float) Math.exp(-(x * x + y * y) / (2.0f * sigma * sigma));
                sum += kernel[i][j];
            }
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                kernel[i][j] /= sum;
            }
        }
        return kernel;
    }

    public static float[][] medianFilter(float[][] data, int kernelSize) {
        if (data == null || data.length == 0) return data;
        int rows = data.length;
        int cols = data[0].length;
        float[][] result = new float[rows][cols];
        int half = kernelSize / 2;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                ArrayList<Float> values = new ArrayList<>();
                for (int di = -half; di <= half; di++) {
                    for (int dj = -half; dj <= half; dj++) {
                        int ni = clampInt(i + di, 0, rows - 1);
                        int nj = clampInt(j + dj, 0, cols - 1);
                        values.add(data[ni][nj]);
                    }
                }
                Collections.sort(values);
                result[i][j] = values.get(values.size() / 2);
            }
        }
        return result;
    }

    public static float[][] sharpenFilter(float[][] data, float strength) {
        if (data == null || data.length == 0) return data;
        int rows = data.length;
        int cols = data[0].length;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float center = data[i][j];
                float neighbors = 0;
                int count = 0;
                if (i > 0) { neighbors += data[i - 1][j]; count++; }
                if (i < rows - 1) { neighbors += data[i + 1][j]; count++; }
                if (j > 0) { neighbors += data[i][j - 1]; count++; }
                if (j < cols - 1) { neighbors += data[i][j + 1]; count++; }
                if (count > 0) {
                    float laplacian = center - neighbors / count;
                    result[i][j] = center + strength * laplacian;
                } else {
                    result[i][j] = center;
                }
            }
        }
        return result;
    }

    public static float[][] edgeDetectionFilter(float[][] data) {
        if (data == null || data.length < 3 || data[0].length < 3) return data;
        int rows = data.length;
        int cols = data[0].length;
        float[][] result = new float[rows][cols];
        for (int i = 1; i < rows - 1; i++) {
            for (int j = 1; j < cols - 1; j++) {
                float gx = -data[i - 1][j - 1] + data[i - 1][j + 1] - 2 * data[i][j - 1] + 2 * data[i][j + 1] - data[i + 1][j - 1] + data[i + 1][j + 1];
                float gy = -data[i - 1][j - 1] - 2 * data[i - 1][j] - data[i - 1][j + 1] + data[i + 1][j - 1] + 2 * data[i + 1][j] + data[i + 1][j + 1];
                result[i][j] = (float) Math.sqrt(gx * gx + gy * gy);
            }
        }
        for (int i = 0; i < rows; i++) {
            result[i][0] = result[i][Math.min(1, cols - 1)];
            result[i][cols - 1] = result[i][Math.max(0, cols - 2)];
        }
        for (int j = 0; j < cols; j++) {
            result[0][j] = result[Math.min(1, rows - 1)][j];
            result[rows - 1][j] = result[Math.max(0, rows - 2)][j];
        }
        return result;
    }

    public static float[][] rankFilter(float[][] data, int kernelSize) {
        if (data == null || data.length == 0) return data;
        int rows = data.length;
        int cols = data[0].length;
        float[][] result = new float[rows][cols];
        int half = kernelSize / 2;
        float[][] gradMag = new float[rows][cols];
        float maxGrad = 0;
        for (int i = 1; i < rows - 1; i++) {
            for (int j = 1; j < cols - 1; j++) {
                float gx = data[i][j + 1] - data[i][j - 1];
                float gy = data[i + 1][j] - data[i - 1][j];
                gradMag[i][j] = (float) Math.sqrt(gx * gx + gy * gy);
                if (gradMag[i][j] > maxGrad) maxGrad = gradMag[i][j];
            }
        }
        float maxVal = -Float.MAX_VALUE, minVal = Float.MAX_VALUE;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float centerVal = data[i][j];
                int totalCount = 0, lessCount = 0;
                for (int di = -half; di <= half; di++) {
                    for (int dj = -half; dj <= half; dj++) {
                        int ni = clampInt(i + di, 0, rows - 1);
                        int nj = clampInt(j + dj, 0, cols - 1);
                        totalCount++;
                        if (data[ni][nj] < centerVal) lessCount++;
                    }
                }
                float rank = (float) lessCount / totalCount;
                float gradWeight = maxGrad > 0 ? gradMag[i][j] / maxGrad : 0;
                result[i][j] = (1.0f + 3.0f * gradWeight) * rank;
                if (result[i][j] > maxVal) maxVal = result[i][j];
                if (result[i][j] < minVal) minVal = result[i][j];
            }
        }
        float range = maxVal - minVal;
        if (range > 0) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    float norm = (result[i][j] - minVal) / range;
                    result[i][j] = (float) Math.pow(norm, 0.5);
                }
            }
        }
        return result;
    }

    public static float[][] histogramEqualization(float[][] data, int bins) {
        if (data == null || data.length == 0) return data;
        int rows = data.length;
        int cols = data[0].length;
        float minVal = Float.MAX_VALUE, maxVal = -Float.MAX_VALUE;
        for (float[] row : data) {
            for (float v : row) {
                if (v < minVal) minVal = v;
                if (v > maxVal) maxVal = v;
            }
        }
        float range = maxVal - minVal;
        float[][] result = new float[rows][cols];
        if (range == 0f) {
            for (int i = 0; i < rows; i++) System.arraycopy(data[i], 0, result[i], 0, cols);
            return result;
        }
        int[] histogram = new int[bins];
        for (float[] row : data) {
            for (float v : row) {
                int bin = clampInt((int) ((v - minVal) / range * (bins - 1)), 0, bins - 1);
                histogram[bin]++;
            }
        }
        int total = rows * cols;
        float[] cdf = new float[bins];
        cdf[0] = (float) histogram[0] / total;
        for (int b = 1; b < bins; b++) {
            cdf[b] = cdf[b - 1] + (float) histogram[b] / total;
        }
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int bin = clampInt((int) ((data[i][j] - minVal) / range * (bins - 1)), 0, bins - 1);
                result[i][j] = minVal + cdf[bin] * range;
            }
        }
        return result;
    }

    public static float[][] bandpassFilter(float[][] data, float sigmaLow, float sigmaHigh) {
        float[][] low = gaussianBlur(data, sigmaLow);
        float[][] high = gaussianBlur(data, sigmaHigh);
        int rows = data.length, cols = data[0].length;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                result[i][j] = low[i][j] - high[i][j];
        return result;
    }

    public static float[][] highpassFilter(float[][] data) {
        float[][] low = gaussianBlur(data, 2.0f);
        int rows = data.length, cols = data[0].length;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                result[i][j] = data[i][j] - low[i][j];
        return result;
    }

    public static float[][] lowpassFilter(float[][] data) {
        return gaussianBlur(data, 2.0f);
    }

    public static float[][] gradientMagnitudeFilter(float[][] data) {
        if (data == null || data.length < 3 || data[0].length < 3) return data;
        int rows = data.length, cols = data[0].length;
        float[][] result = new float[rows][cols];
        for (int i = 1; i < rows - 1; i++) {
            for (int j = 1; j < cols - 1; j++) {
                float dx = (data[i][j + 1] - data[i][j - 1]) / 2.0f;
                float dy = (data[i + 1][j] - data[i - 1][j]) / 2.0f;
                result[i][j] = (float) Math.sqrt(dx * dx + dy * dy);
            }
        }
        for (int i = 0; i < rows; i++) {
            result[i][0] = result[i][Math.min(1, cols - 1)];
            result[i][cols - 1] = result[i][Math.max(0, cols - 2)];
        }
        for (int j = 0; j < cols; j++) {
            result[0][j] = result[Math.min(1, rows - 1)][j];
            result[rows - 1][j] = result[Math.max(0, rows - 2)][j];
        }
        return result;
    }

    public static float[][] laplacianFilter(float[][] data) {
        if (data == null || data.length < 3 || data[0].length < 3) return data;
        int rows = data.length, cols = data[0].length;
        float[][] result = new float[rows][cols];
        for (int i = 1; i < rows - 1; i++) {
            for (int j = 1; j < cols - 1; j++) {
                result[i][j] = data[i - 1][j] + data[i + 1][j] + data[i][j - 1] + data[i][j + 1] - 4 * data[i][j];
            }
        }
        return result;
    }

    public static float[][] thresholdFilter(float[][] data) {
        if (data == null || data.length == 0) return data;
        int rows = data.length, cols = data[0].length;
        float mean = 0;
        for (float[] row : data) for (float v : row) mean += v;
        mean /= (rows * cols);
        float std = 0;
        for (float[] row : data) for (float v : row) std += (v - mean) * (v - mean);
        float threshold = mean + (float) Math.sqrt(std / (rows * cols));
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                result[i][j] = data[i][j] > threshold ? data[i][j] : 0f;
        return result;
    }

    public static float[][] normalizeFilter(float[][] data) {
        if (data == null || data.length == 0) return data;
        int rows = data.length, cols = data[0].length;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float[] row : data) {
            for (float v : row) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        float range = max - min;
        if (range == 0f) return data;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                result[i][j] = (data[i][j] - min) / range;
        return result;
    }

    public static float[][] detrendFilter(float[][] data) {
        if (data == null || data.length == 0) return data;
        int rows = data.length, cols = data[0].length;
        float[] rowMeans = new float[rows];
        float[] colMeans = new float[cols];
        float grandMean = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                rowMeans[i] += data[i][j];
                colMeans[j] += data[i][j];
                grandMean += data[i][j];
            }
        }
        for (int i = 0; i < rows; i++) rowMeans[i] /= cols;
        for (int j = 0; j < cols; j++) colMeans[j] /= rows;
        grandMean /= (rows * cols);
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                result[i][j] = data[i][j] - rowMeans[i] - colMeans[j] + grandMean;
        return result;
    }

    public static float[][] applyFilter(float[][] data, String filterCode) {
        if (data == null || filterCode == null || filterCode.equals("none")) return data;
        switch (filterCode) {
            case "gauss": return gaussianBlur(data, 1.5f);
            case "median": return medianFilter(data, 3);
            case "sharpen": return sharpenFilter(data, 1.5f);
            case "edge": return edgeDetectionFilter(data);
            case "rank": return rankFilter(data, 3);
            case "histeq": return histogramEqualization(data, 256);
            case "bandpass": return bandpassFilter(data, 1.0f, 4.0f);
            case "highpass": return highpassFilter(data);
            case "lowpass": return lowpassFilter(data);
            case "gradient": return gradientMagnitudeFilter(data);
            case "laplacian": return laplacianFilter(data);
            case "threshold": return thresholdFilter(data);
            case "normalize": return normalizeFilter(data);
            case "detrend": return detrendFilter(data);
            default: return data;
        }
    }

    public static float[] applyFilter1D(float[] data, int cols, int rows, String filterCode) {
        if (data == null || filterCode == null || filterCode.equals("none")) return data;
        float[][] grid = new float[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                grid[i][j] = data[i * cols + j];
        float[][] filtered = applyFilter(grid, filterCode);
        float[] result = new float[rows * cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                result[i * cols + j] = filtered[i][j];
        return result;
    }

    public static float[][] interpolateGrid(float[][] data, int targetRows, int targetCols) {
        if (data == null || data.length == 0) return data;
        int srcRows = data.length, srcCols = data[0].length;
        float[][] result = new float[targetRows][targetCols];
        for (int i = 0; i < targetRows; i++) {
            for (int j = 0; j < targetCols; j++) {
                float srcI = (float) i / targetRows * srcRows;
                float srcJ = (float) j / targetCols * srcCols;
                int i0 = Math.min((int) srcI, srcRows - 1);
                int j0 = Math.min((int) srcJ, srcCols - 1);
                int i1 = Math.min(i0 + 1, srcRows - 1);
                int j1 = Math.min(j0 + 1, srcCols - 1);
                float fi = srcI - i0, fj = srcJ - j0;
                result[i][j] = (1 - fi) * (1 - fj) * data[i0][j0] + fi * (1 - fj) * data[i1][j0]
                        + (1 - fi) * fj * data[i0][j1] + fi * fj * data[i1][j1];
            }
        }
        return result;
    }

    public static float anomalyToColor(float value, float min, float max) {
        if (max == min) return 0.5f;
        return clamp((value - min) / (max - min), 0.0f, 1.0f);
    }
}
