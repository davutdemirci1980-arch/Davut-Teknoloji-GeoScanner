package com.geoscanner.app.simulation;

/**
 * Catalog of virtual target types the simulation lab can place in a scene.
 * Each type carries a default (tunable) magnetic-style contrast, a default
 * shape/size, and whether it is "elongated" (wall/tunnel/pipe-like), which
 * makes its response direction-sensitive to {@link SimTarget#orientationDeg}.
 */
public enum TargetType {
    METAL("Metal", 800.0, TargetShape.SPHERE, 0.3, false),
    VOID("Boşluk", -120.0, TargetShape.BOX, 0.8, false),
    ROOM("Oda", -180.0, TargetShape.BOX, 2.5, false),
    TUNNEL("Tünel", -150.0, TargetShape.CYLINDER, 1.2, true),
    GRAVE("Mezar", -90.0, TargetShape.BOX, 1.0, false),
    SARCOPHAGUS("Lahit", -110.0, TargetShape.BOX, 1.5, false),
    CUBE("Küp", 300.0, TargetShape.BOX, 0.6, false),
    PIPE("Boru", 250.0, TargetShape.CYLINDER, 0.3, true),
    WALL("Duvar", -60.0, TargetShape.PLATE, 0.5, true),
    ROCK("Kaya", 40.0, TargetShape.SPHERE, 0.8, false),
    MINERALIZED_ZONE("Mineralize Bölge", 150.0, TargetShape.BOX, 2.0, false),
    WATER_WET_SOIL("Su / Islak Zemin", -40.0, TargetShape.BOX, 2.0, false);

    public final String displayNameTr;
    public final double defaultContrast;
    public final TargetShape defaultShape;
    public final double defaultSizeM;
    public final boolean elongated;

    TargetType(String displayNameTr, double defaultContrast, TargetShape defaultShape, double defaultSizeM, boolean elongated) {
        this.displayNameTr = displayNameTr;
        this.defaultContrast = defaultContrast;
        this.defaultShape = defaultShape;
        this.defaultSizeM = defaultSizeM;
        this.elongated = elongated;
    }

    public static String[] displayNames() {
        TargetType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].displayNameTr;
        return names;
    }
}
