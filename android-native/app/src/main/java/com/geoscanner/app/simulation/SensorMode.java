package com.geoscanner.app.simulation;

public enum SensorMode {
    SINGLE("Tek Sensör"),
    DUAL_GRADIOMETER("Çift Sensör Gradyometre"),
    THREE_AXIS("3 Eksenli X/Y/Z");

    public final String displayNameTr;

    SensorMode(String displayNameTr) {
        this.displayNameTr = displayNameTr;
    }

    public static String[] displayNames() {
        SensorMode[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].displayNameTr;
        return names;
    }
}
