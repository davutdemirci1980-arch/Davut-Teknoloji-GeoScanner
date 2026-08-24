package com.geoscanner.app.utils;

/** Small great-circle math helpers for the AR overlay: bearing, distance, and destination-point projection. */
public class GeoMath {
    private static final double EARTH_RADIUS_M = 6371000.0;

    /** Bearing in degrees (0-360, clockwise from true north) from point A to point B. */
    public static double bearingBetween(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);
        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        double theta = Math.atan2(y, x);
        return (Math.toDegrees(theta) + 360) % 360;
    }

    /** Great-circle distance in meters between two lat/lon points (haversine). */
    public static double distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /** Destination point {lat, lon} starting at (lat, lon), traveling bearingDeg (from true north) for distanceM meters. */
    public static double[] destinationPoint(double lat, double lon, double bearingDeg, double distanceM) {
        double phi1 = Math.toRadians(lat);
        double lambda1 = Math.toRadians(lon);
        double theta = Math.toRadians(bearingDeg);
        double delta = distanceM / EARTH_RADIUS_M;

        double phi2 = Math.asin(Math.sin(phi1) * Math.cos(delta) + Math.cos(phi1) * Math.sin(delta) * Math.cos(theta));
        double lambda2 = lambda1 + Math.atan2(
                Math.sin(theta) * Math.sin(delta) * Math.cos(phi1),
                Math.cos(delta) - Math.sin(phi1) * Math.sin(phi2));

        return new double[]{Math.toDegrees(phi2), Math.toDegrees(lambda2)};
    }

    /** Normalizes an angle difference to (-180, 180]. */
    public static double angleDiff(double a, double b) {
        double d = (a - b) % 360;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;
    }
}
