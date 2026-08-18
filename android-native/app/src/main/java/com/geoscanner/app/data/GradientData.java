package com.geoscanner.app.data;

public class GradientData {

    public enum DataType {
        GRADIENT,
        MAGNETOMETER,
        DIFFERENTIAL,
        SINGLE,
        TRIPLE
    }

    private final DataType type;
    private final Float sensor1;
    private final Float sensor2;
    private final float gradient;
    private final long timestamp;

    public GradientData(DataType type, Float sensor1, Float sensor2, float gradient) {
        this.type = type;
        this.sensor1 = sensor1;
        this.sensor2 = sensor2;
        this.gradient = gradient;
        this.timestamp = System.currentTimeMillis();
    }

    public GradientData(float gradient) {
        this(DataType.SINGLE, null, null, gradient);
    }

    public DataType getType() {
        return type;
    }

    public Float getSensor1() {
        return sensor1;
    }

    public Float getSensor2() {
        return sensor2;
    }

    public float getGradient() {
        return gradient;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getEffectiveValue() {
        return sensor2 != null ? sensor2 : gradient;
    }

    @Override
    public String toString() {
        return "GradientData(type=" + type + ", s1=" + sensor1 + ", s2=" + sensor2 + ", gradient=" + gradient + ", ts=" + timestamp + ")";
    }
}
