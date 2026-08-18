package com.geoscanner.app.data;

import org.json.JSONException;
import org.json.JSONObject;

public class ScanDataPoint {
    public int x;
    public int y;
    public double z;
    public double c;

    public ScanDataPoint(int x, int y, double z, double c) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.c = c;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("x", x);
        obj.put("y", y);
        obj.put("z", z);
        obj.put("c", c);
        return obj;
    }

    public static ScanDataPoint fromJSON(JSONObject obj) throws JSONException {
        return new ScanDataPoint(obj.getInt("x"), obj.getInt("y"), obj.optDouble("z", 0.0), obj.getDouble("c"));
    }

    @Override
    public String toString() {
        return "ScanDataPoint(x=" + x + ", y=" + y + ", z=" + z + ", c=" + c + ")";
    }
}
