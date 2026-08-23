package com.geoscanner.app.simulation;

/**
 * Ground/soil scenario presets. baseNoise and mineralizationVariance are in
 * the same arbitrary simulation units as {@link TargetType#defaultContrast}.
 */
public enum GroundType {
    NORMAL("Normal Toprak", 8.0, 5.0, 0.0),
    CLAY("Killi", 10.0, 8.0, 0.0),
    SANDY("Kumlu", 6.0, 4.0, 0.0),
    ROCKY("Kayalık", 18.0, 15.0, 0.0),
    MINERALIZED("Mineralize", 25.0, 60.0, 0.0),
    IRON_OXIDE("Demir Oksitli", 30.0, 70.0, 0.0),
    SALTY("Tuzlu", 15.0, 20.0, 6.0),
    WET("Nemli", 12.0, 10.0, 0.0),
    HETEROGENEOUS("Heterojen", 20.0, 40.0, 0.0);

    public final String displayNameTr;
    public final double baseNoise;
    public final double mineralizationVariance;
    public final double conductivityDrift;

    GroundType(String displayNameTr, double baseNoise, double mineralizationVariance, double conductivityDrift) {
        this.displayNameTr = displayNameTr;
        this.baseNoise = baseNoise;
        this.mineralizationVariance = mineralizationVariance;
        this.conductivityDrift = conductivityDrift;
    }

    public static String[] displayNames() {
        GroundType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].displayNameTr;
        return names;
    }
}
