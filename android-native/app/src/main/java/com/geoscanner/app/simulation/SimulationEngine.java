package com.geoscanner.app.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a synthetic (SIMULATION-tagged) survey: sums a simplified
 * magnetic-dipole-style falloff from each placed {@link SimTarget} and each
 * {@link SimInterferenceSource}, adds ground noise/mineralization/slope and
 * sensor imperfections, optionally distorts it with {@link OperatorErrorConfig},
 * optionally applies {@link SimCalibrationConfig} baseline correction, and
 * lays the samples out over the configured grid following the chosen
 * walking pattern.
 *
 * This is a training/what-if approximation, not a physically rigorous EM
 * solver — matches the simulation lab's stated purpose (testing algorithms
 * and building operator intuition, never a substitute for real field data).
 */
public class SimulationEngine {

    /** Runs the clean survey (operator error, if configured, is applied only when applyOperatorError is true). */
    public static List<SimDataPoint> generate(SimRunConfig config, long seed, boolean applyOperatorError) {
        Random rng = new Random(seed);
        SimGridConfig grid = config.grid;
        SimGroundConfig ground = config.ground;
        SimSensorConfig sensor = config.sensor;
        OperatorErrorConfig opError = applyOperatorError ? config.operatorError : null;
        double stepM = grid.stepM();

        double mineralFreq1 = 0.15 + rng.nextDouble() * 0.15;
        double mineralFreq2 = 0.35 + rng.nextDouble() * 0.2;
        double mineralPhase1 = rng.nextDouble() * Math.PI * 2;
        double mineralPhase2 = rng.nextDouble() * Math.PI * 2;
        double mineralPhase3 = rng.nextDouble() * Math.PI * 2;

        List<SimDataPoint> points = new ArrayList<>(grid.cols * grid.rows);

        for (int row = 0; row < grid.rows; row++) {
            double rowBaseY = row * stepM;
            boolean reverseRow = grid.pattern == ScanPattern.ZIGZAG && row % 2 == 1;
            boolean reverseAll = grid.pattern == ScanPattern.RIGHT_TO_LEFT;

            for (int i = 0; i < grid.cols; i++) {
                int col = (reverseRow || reverseAll) ? (grid.cols - 1 - i) : i;
                double baseXM = col * stepM;

                double xM = baseXM;
                double yM = rowBaseY;
                double effectiveHeight = sensor.heightAboveGroundM;
                boolean missedPoint = false;

                if (opError != null && opError.enabled) {
                    xM += opError.walkingSpeedVariation * stepM * 0.4 * rng.nextGaussian();
                    yM += opError.lineDrift * stepM * 0.15 * col + opError.lineDrift * stepM * 0.2 * rng.nextGaussian();
                    effectiveHeight = Math.max(0.02, effectiveHeight + opError.heightWobble * 0.08 * rng.nextGaussian());
                    if (rng.nextDouble() < opError.turnErrorRate * 0.5) {
                        xM += stepM * 2.0 * (rng.nextBoolean() ? 1 : -1);
                        yM += stepM * 2.0 * (rng.nextBoolean() ? 1 : -1);
                    }
                    missedPoint = rng.nextDouble() < opError.missedPointRate;
                }

                double groundNoise = ground.effectiveNoise() * rng.nextGaussian();
                double mineralNoise = mineralNoiseAt(xM, yM, ground.effectiveMineralizationVariance(),
                        mineralFreq1, mineralFreq2, mineralPhase1, mineralPhase2, mineralPhase3);
                double slope = ground.regionalSlopeXPerM * xM + ground.regionalSlopeYPerM * yM;
                double drift = sensor.driftPerRow * row;
                double interference = interferenceAt(config.interferences, xM, yM);
                double commonBackground = groundNoise + mineralNoise + slope + drift + sensor.offset + interference;
                double sensorJitter = sensor.sensitivityNoise * 5.0;
                if (opError != null && opError.enabled) {
                    sensorJitter += opError.tiltVibration * 4.0;
                }

                SimDataPoint p = new SimDataPoint();
                p.gridX = col;
                p.gridY = row;
                p.xM = baseXM;
                p.yM = rowBaseY;
                p.missed = missedPoint;
                p.dominantDepthM = dominantDepthAt(config.targets, xM, yM, effectiveHeight);

                if (missedPoint) {
                    p.displayValue = commonBackground;
                    p.bz = commonBackground;
                    p.sensor1 = commonBackground;
                    points.add(p);
                    continue;
                }

                switch (sensor.mode) {
                    case DUAL_GRADIOMETER: {
                        double s1 = fieldAt(config.targets, xM, yM, effectiveHeight)
                                + commonBackground + sensorJitter * rng.nextGaussian();
                        double s2 = fieldAt(config.targets, xM, yM, effectiveHeight + sensor.sensorSpacingM)
                                + commonBackground + sensorJitter * rng.nextGaussian();
                        p.sensor1 = s1;
                        p.sensor2 = s2;
                        p.gradient = s1 - s2;
                        p.bz = p.gradient;
                        p.displayValue = p.gradient;
                        break;
                    }
                    case THREE_AXIS: {
                        double probe = Math.max(stepM / 2.0, 0.05);
                        double bz = fieldAt(config.targets, xM, yM, effectiveHeight) + commonBackground;
                        double bx = (fieldAt(config.targets, xM + probe, yM, effectiveHeight)
                                - fieldAt(config.targets, xM - probe, yM, effectiveHeight)) / (2 * probe) * probe;
                        double by = (fieldAt(config.targets, xM, yM + probe, effectiveHeight)
                                - fieldAt(config.targets, xM, yM - probe, effectiveHeight)) / (2 * probe) * probe;
                        p.bz = bz + sensorJitter * rng.nextGaussian();
                        p.bx = bx + sensorJitter * 0.3 * rng.nextGaussian();
                        p.by = by + sensorJitter * 0.3 * rng.nextGaussian();
                        p.displayValue = Math.sqrt(p.bx * p.bx + p.by * p.by + p.bz * p.bz);
                        break;
                    }
                    case SINGLE:
                    default: {
                        double s1 = fieldAt(config.targets, xM, yM, effectiveHeight)
                                + commonBackground + sensorJitter * rng.nextGaussian();
                        p.sensor1 = s1;
                        p.bz = s1;
                        p.displayValue = s1;
                        break;
                    }
                }

                points.add(p);
            }
        }

        applyCalibration(points, config.calibration, sensor.mode);
        return points;
    }

    private static void applyCalibration(List<SimDataPoint> points, SimCalibrationConfig cal, SensorMode mode) {
        if (cal == null || points.isEmpty()) return;

        if (cal.balanceDualSensors && mode == SensorMode.DUAL_GRADIOMETER) {
            double meanS1 = 0, meanS2 = 0;
            for (SimDataPoint p : points) {
                meanS1 += p.sensor1;
                meanS2 += p.sensor2;
            }
            meanS1 /= points.size();
            meanS2 /= points.size();
            for (SimDataPoint p : points) {
                p.sensor1 -= meanS1;
                p.sensor2 -= meanS2;
                p.gradient = p.sensor1 - p.sensor2;
                p.bz = p.gradient;
                p.displayValue = p.gradient;
            }
        }

        if (cal.referenceFirstColumn) {
            double sum = 0;
            int count = 0;
            for (SimDataPoint p : points) {
                if (p.gridX == 0) {
                    sum += p.displayValue;
                    count++;
                }
            }
            if (count > 0) {
                double baseline = sum / count;
                for (SimDataPoint p : points) {
                    p.displayValue -= baseline;
                }
            }
        }
    }

    private static double mineralNoiseAt(double xM, double yM, double variance,
                                          double f1, double f2, double ph1, double ph2, double ph3) {
        if (variance <= 0) return 0;
        double a = Math.sin(f1 * xM + ph1) * Math.cos(f1 * yM + ph2);
        double b = Math.sin(f2 * (xM + yM) + ph3);
        return variance * 0.5 * (a + b);
    }

    private static double interferenceAt(List<SimInterferenceSource> sources, double xM, double yM) {
        if (sources == null || sources.isEmpty()) return 0;
        double total = 0;
        for (SimInterferenceSource s : sources) {
            double dx = xM - s.xM;
            double dy = yM - s.yM;
            double distSq = dx * dx + dy * dy;
            double sigma = 0.15 + (1 - s.type.spikiness) * 1.5;
            total += s.intensity() * Math.exp(-distSq / (2 * sigma * sigma));
        }
        return total;
    }

    /** Depth of whichever target contributes the strongest |signal| at this position (0 if no targets). */
    private static double dominantDepthAt(List<SimTarget> targets, double xM, double yM, double heightAboveGroundM) {
        double bestDepth = 0;
        double bestAbs = -1;
        for (SimTarget t : targets) {
            double dx = xM - t.xM;
            double dy = yM - t.yM;
            double verticalDistance = heightAboveGroundM + t.depthM;
            double r = Math.sqrt(dx * dx + dy * dy + verticalDistance * verticalDistance);
            double volumeFactor = t.sizeM * t.sizeM * t.sizeM;
            double c = Math.abs(t.contrast() * volumeFactor / (r * r * r + 0.001));
            if (c > bestAbs) {
                bestAbs = c;
                bestDepth = t.depthM;
            }
        }
        return bestDepth;
    }

    /** Sum of every target's contribution at a lateral position, sampled at the given height above ground. */
    private static double fieldAt(List<SimTarget> targets, double xM, double yM, double heightAboveGroundM) {
        double total = 0;
        for (SimTarget t : targets) {
            double dx = xM - t.xM;
            double dy = yM - t.yM;
            double verticalDistance = heightAboveGroundM + t.depthM;
            double r = Math.sqrt(dx * dx + dy * dy + verticalDistance * verticalDistance);
            double volumeFactor = t.sizeM * t.sizeM * t.sizeM;
            double contribution = t.contrast() * volumeFactor / (r * r * r + 0.001);

            if (t.type.elongated) {
                double angleToPoint = Math.atan2(dy, dx);
                double orientRad = Math.toRadians(t.orientationDeg);
                contribution *= 1 + 0.6 * Math.cos(2 * (angleToPoint - orientRad));
            }
            total += contribution;
        }
        return total;
    }
}
