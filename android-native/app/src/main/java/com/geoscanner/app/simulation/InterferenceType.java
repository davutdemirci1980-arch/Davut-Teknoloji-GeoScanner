package com.geoscanner.app.simulation;

/** External interference sources modeled by the Parazit Laboratuvarı (section 6 of the spec). */
public enum InterferenceType {
    PHONE("Telefon", 60.0, 0.3),
    POWER_LINE("Elektrik Hattı", 200.0, 2.5),
    VEHICLE("Araç", 350.0, 1.5),
    IRON_FENCE("Demir Çit", 180.0, 4.0),
    HIGH_VOLTAGE("Yüksek Gerilim", 400.0, 3.0),
    NEARBY_METAL("Çevredeki Metal", 150.0, 1.0),
    MAGNETIC_ROCK("Manyetik Taş", 90.0, 0.8),
    ELECTRONIC_NOISE("Elektronik Gürültü", 40.0, 0.2);

    public final String displayNameTr;
    public final double defaultIntensity;
    /** How "spike-like" (1.0) vs "sustained/broad" (0.0) the source's footprint is. */
    public final double spikiness;

    InterferenceType(String displayNameTr, double defaultIntensity, double spikiness) {
        this.displayNameTr = displayNameTr;
        this.defaultIntensity = defaultIntensity;
        this.spikiness = spikiness;
    }

    public static String[] displayNames() {
        InterferenceType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].displayNameTr;
        return names;
    }
}
