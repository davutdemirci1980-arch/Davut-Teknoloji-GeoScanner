package com.geoscanner.app.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ScanRecord implements Serializable {
    private String name;
    private String filePath;
    private String format;
    private long timestamp;
    private int gridWidth;
    private int gridHeight;
    private int stepSize;
    private String deviceType;
    private transient List<ScanDataPoint> dataPoints;

    public ScanRecord() {
        this.dataPoints = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    public ScanRecord(String name, int gridWidth, int gridHeight, int stepSize) {
        this();
        this.name = name;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.stepSize = stepSize;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getGridWidth() {
        return gridWidth;
    }

    public void setGridWidth(int gridWidth) {
        this.gridWidth = gridWidth;
    }

    public int getGridHeight() {
        return gridHeight;
    }

    public void setGridHeight(int gridHeight) {
        this.gridHeight = gridHeight;
    }

    public int getStepSize() {
        return stepSize;
    }

    public void setStepSize(int stepSize) {
        this.stepSize = stepSize;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public List<ScanDataPoint> getDataPoints() {
        return dataPoints;
    }

    public void setDataPoints(List<ScanDataPoint> dataPoints) {
        this.dataPoints = dataPoints;
    }
}
