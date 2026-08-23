package com.geoscanner.app.simulation;

import java.util.ArrayList;
import java.util.List;

/** Ready-made training scenarios, matching the simulation lab spec's preset list. */
public class SimScenarioPresets {

    public enum Preset {
        SINGLE_METAL("Tek Metal"),
        DOUBLE_METAL("İki Metal"),
        VOID_TARGET("Boşluk"),
        ROOM("Oda"),
        TUNNEL("Tünel"),
        MINERALIZATION("Mineralizasyon"),
        METAL_PLUS_VOID("Metal + Boşluk"),
        DEEP_LARGE_TARGET("Derin Büyük Hedef"),
        SHALLOW_SMALL_TARGET("Sığ Küçük Hedef");

        public final String displayNameTr;

        Preset(String displayNameTr) {
            this.displayNameTr = displayNameTr;
        }

        public static String[] displayNames() {
            Preset[] values = values();
            String[] names = new String[values.length];
            for (int i = 0; i < values.length; i++) names[i] = values[i].displayNameTr;
            return names;
        }
    }

    public static List<SimTarget> buildTargets(Preset preset, SimGridConfig grid) {
        double cx = (grid.cols - 1) * grid.stepM() / 2.0;
        double cy = (grid.rows - 1) * grid.stepM() / 2.0;
        List<SimTarget> list = new ArrayList<>();

        switch (preset) {
            case SINGLE_METAL:
                list.add(new SimTarget(TargetType.METAL, cx, cy, 0.8));
                break;
            case DOUBLE_METAL:
                list.add(new SimTarget(TargetType.METAL, cx - 0.8, cy, 0.6));
                list.add(new SimTarget(TargetType.METAL, cx + 0.8, cy, 1.0));
                break;
            case VOID_TARGET:
                list.add(new SimTarget(TargetType.VOID, cx, cy, 1.2));
                break;
            case ROOM:
                list.add(new SimTarget(TargetType.ROOM, cx, cy, 2.5));
                break;
            case TUNNEL: {
                SimTarget t = new SimTarget(TargetType.TUNNEL, cx, cy, 2.0);
                t.orientationDeg = 90;
                t.sizeM = Math.max(grid.cols, grid.rows) * grid.stepM() * 0.6;
                list.add(t);
                break;
            }
            case MINERALIZATION:
                list.add(new SimTarget(TargetType.MINERALIZED_ZONE, cx, cy, 1.0));
                break;
            case METAL_PLUS_VOID:
                list.add(new SimTarget(TargetType.METAL, cx - 0.7, cy - 0.7, 0.5));
                list.add(new SimTarget(TargetType.VOID, cx + 0.7, cy + 0.7, 1.5));
                break;
            case DEEP_LARGE_TARGET: {
                SimTarget t = new SimTarget(TargetType.ROOM, cx, cy, 4.0);
                t.sizeM = 3.5;
                list.add(t);
                break;
            }
            case SHALLOW_SMALL_TARGET: {
                SimTarget t = new SimTarget(TargetType.METAL, cx, cy, 0.25);
                t.sizeM = 0.1;
                list.add(t);
                break;
            }
        }
        return list;
    }

    public static GroundType groundTypeFor(Preset preset) {
        return preset == Preset.MINERALIZATION ? GroundType.MINERALIZED : GroundType.NORMAL;
    }

    public static double interferenceFor(Preset preset) {
        return preset == Preset.MINERALIZATION ? 0.8 : 0.4;
    }
}
