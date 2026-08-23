package com.geoscanner.app.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a synthetic (SIMULATION-tagged) survey: sums a simplified
 * magnetic-dipole-style falloff from each placed {@link SimTarget}, adds
 * ground noise/mineralization/slope and sensor imperfections, and lays the
 * samples out over the configured grid following the chosen walking pattern.
 *
 * This is a training/what-if approximation, not a physically rigorous EM
 * solver — matches the simulation lab's stated purpose (testing algorithms
 * and building operator intuition, never a substitute for real field data).
 */
public class SimulationEngine {

    public static List<SimDataPoint> generate(List<SimTarget> targets, SimGroundConfig ground,
                                                SimSensorConfig sensor, SimGridConfig grid, long seed) {
        Random rng = new Random(seed);
        double stepM = grid.stepM();
        double mineralFreq1 = 0.15 + rng.nextDouble() * 0.15;
        double mineralFreq2 = 0.35 + rng.nextDouble() * 0.2;
        double mineralPhase1 = rng.nextDouble() * Math.PI * 2;
        double mineralPhase2 = rng.nextDouble() * Math.PI * 2;
        double mineralPhase3 = rng.nextDouble() * Math.PI * 2;

        List<SimDataPoint> points = new ArrayList<>(grid.cols * grid.rows);

        for (int row = 0; row < grid.rows; row++) {
            double yM = row * stepM;
            boolean reverseRow = grid.pattern == ScanPattern.ZIGZAG && row % 2 == 1;
            boolean reverseAll = grid.pattern == ScanPattern.RIGHT_TO_LEFT;

            for (int i = 0; i < grid.cols; i++) {
                int col = (reverseRow || reverseAll) ? (grid.cols - 1 - i) : i;
                double xM = col * stepM;

                double groundNoise = ground.effectiveNoise() * rng.nextGaussian();
                double mineralNoise = mineralNoiseAt(xM, yM, ground.effectiveMineralizationVariance(),
                        mineralFreq1, mineralFreq2, mineralPhase1, mineralPhase2, mineralPhase3);
                double slope = ground.regionalSlopeXPerM * xM + ground.regionalSlopeYPerM * yM;
                double drift = sensor.driftPerRow * row;
                double commonBackground = groundNoise + mineralNoise + slope + drift + sensor.offset;
                double sensorJitter = sensor.sensitivityNoise * 5.0;

                SimDataPoint p = new SimDataPoint();
                p.gridX = col;
                p.gridY = row;
                p.xM = xM;
                p.yM = yM;
                p.dominantDepthM = dominantDepthAt(targets, xM, yM, sensor.heightAboveGroundM);

                switch (sensor.mode) {
                    case DUAL_GRADIOMETER: {
                        double s1 = fieldAt(targets, xM, yM, sensor.heightAboveGroundM)
                                + commonBackground + sensorJitter * rng.nextGaussian();
                        double s2 = fieldAt(targets, xM, yM, sensor.heightAboveGroundM + sensor.sensorSpacingM)
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
                        double bz = fieldAt(targets, xM, yM, sensor.heightAboveGroundM) + commonBackground;
                        double bx = (fieldAt(targets, xM + probe, yM, sensor.heightAboveGroundM)
                                - fieldAt(targets, xM - probe, yM, sensor.heightAboveGroundM)) / (2 * probe) * probe;
                        double by = (fieldAt(targets, xM, yM + probe, sensor.heightAboveGroundM)
                                - fieldAt(targets, xM, yM - probe, sensor.heightAboveGroundM)) / (2 * probe) * probe;
                        p.bz = bz + sensorJitter * rng.nextGaussian();
                        p.bx = bx + sensorJitter * 0.3 * rng.nextGaussian();
                        p.by = by + sensorJitter * 0.3 * rng.nextGaussian();
                        p.displayValue = Math.sqrt(p.bx * p.bx + p.by * p.by + p.bz * p.bz);
                        break;
                    }
                    case SINGLE:
                    default: {
                        double s1 = fieldAt(targets, xM, yM, sensor.heightAboveGroundM)
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
        return points;
    }

    private static double mineralNoiseAt(double xM, double yM, double variance,
                                          double f1, double f2, double ph1, double ph2, double ph3) {
        if (variance <= 0) return 0;
        double a = Math.sin(f1 * xM + ph1) * Math.cos(f1 * yM + ph2);
        double b = Math.sin(f2 * (xM + yM) + ph3);
        return variance * 0.5 * (a + b);
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
