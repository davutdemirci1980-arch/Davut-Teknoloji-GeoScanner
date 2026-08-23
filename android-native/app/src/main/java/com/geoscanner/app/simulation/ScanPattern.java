package com.geoscanner.app.simulation;

public enum ScanPattern {
    ZIGZAG("Zigzag"),
    PARALLEL("Paralel"),
    SINGLE_DIRECTION("Tek Yön"),
    RIGHT_TO_LEFT("Sağdan Sola"),
    LEFT_TO_RIGHT("Soldan Sağa"),
    NORTH_SOUTH("Kuzey-Güney"),
    EAST_WEST("Doğu-Batı");

    public final String displayNameTr;

    ScanPattern(String displayNameTr) {
        this.displayNameTr = displayNameTr;
    }

    public static String[] displayNames() {
        ScanPattern[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].displayNameTr;
        return names;
    }
}
