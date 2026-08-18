package com.geoscanner.app.ui.scan;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.data.FileManager;
import com.geoscanner.app.data.ScanDataPoint;
import com.geoscanner.app.ui.isosurface.IsoSurfaceActivity;
import com.geoscanner.app.ui.voxler4d.Pro4DVoxlerActivity;
import com.geoscanner.app.utils.LocaleHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** Renders an imported/completed scan as a tap-to-inspect heatmap, with export shortcuts. */
public class ScanPreviewActivity extends AppCompatActivity {
    private String filePath;
    private List<ScanDataPoint> dataPoints;
    private List<Integer> xList;
    private List<Integer> yList;
    private double[][] gridC;
    private double[][] gridZ;
    private int nx, ny;
    private int cellSize;
    private double globalMinC, globalMaxC, globalMinZ, globalMaxZ;

    private ImageView ivHeatmap;
    private TextView tvInfo, tvIntensity, tvDepth, tvCoords;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_preview);
        getWindow().setStatusBarColor(0xFF111111);

        filePath = getIntent().getStringExtra("filePath");
        ivHeatmap = findViewById(R.id.ivHeatmap);
        tvInfo = findViewById(R.id.tvInfo);
        tvIntensity = findViewById(R.id.tvIntensity);
        tvDepth = findViewById(R.id.tvDepth);
        tvCoords = findViewById(R.id.tvCoords);

        loadData();
        setupButtons();
        setupTouchReader();
    }

    private void loadData() {
        if (filePath == null) return;
        File file = new File(filePath);
        dataPoints = FileManager.readAuto(file);
        if (dataPoints == null || dataPoints.isEmpty()) {
            Toast.makeText(this, getString(R.string.preview_no_data), Toast.LENGTH_SHORT).show();
            return;
        }

        globalMinC = Double.MAX_VALUE;
        globalMaxC = -Double.MAX_VALUE;
        globalMinZ = Double.MAX_VALUE;
        globalMaxZ = -Double.MAX_VALUE;
        for (ScanDataPoint p : dataPoints) {
            globalMinC = Math.min(globalMinC, p.c);
            globalMaxC = Math.max(globalMaxC, p.c);
            globalMinZ = Math.min(globalMinZ, p.z);
            globalMaxZ = Math.max(globalMaxZ, p.z);
        }

        tvInfo.setText(String.format(Locale.US, "%s | %d pts | C:[%.1f ~ %.1f] | Z:[%.1f ~ %.1f]",
                file.getName(), dataPoints.size(), globalMinC, globalMaxC, globalMinZ, globalMaxZ));
        drawHeatmap();
    }

    private void drawHeatmap() {
        if (dataPoints == null || dataPoints.isEmpty()) return;

        TreeSet<Integer> xSet = new TreeSet<>();
        TreeSet<Integer> ySet = new TreeSet<>();
        for (ScanDataPoint p : dataPoints) {
            xSet.add(p.x);
            ySet.add(p.y);
        }
        xList = new ArrayList<>(xSet);
        yList = new ArrayList<>(ySet);
        nx = xList.size();
        ny = yList.size();
        if (nx == 0 || ny == 0) return;

        gridC = new double[ny][nx];
        gridZ = new double[ny][nx];
        for (ScanDataPoint p : dataPoints) {
            int xi = xList.indexOf(p.x);
            int yi = yList.indexOf(p.y);
            if (xi >= 0 && yi >= 0) {
                gridC[yi][xi] = p.c;
                gridZ[yi][xi] = p.z;
            }
        }

        cellSize = Math.max(8, 600 / Math.max(nx, ny));
        int bw = nx * cellSize;
        int bh = ny * cellSize;
        Bitmap bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Data row 0 is the "far" edge of the survey; draw it at the top of the bitmap.
        for (int row = 0; row < ny; row++) {
            int drawRow = (ny - 1) - row;
            for (int col = 0; col < nx; col++) {
                float t = globalMaxC != globalMinC ? (float) ((gridC[row][col] - globalMinC) / (globalMaxC - globalMinC)) : 0.5f;
                Paint paint = new Paint();
                paint.setColor(getHeatmapColor(t));
                canvas.drawRect(col * cellSize, drawRow * cellSize, (col + 1) * cellSize, (drawRow + 1) * cellSize, paint);
            }
        }

        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.argb(60, 255, 255, 255));
        gridPaint.setStrokeWidth(1.0f);
        for (int i = 0; i <= nx; i++) canvas.drawLine(i * cellSize, 0, i * cellSize, bh, gridPaint);
        for (int j = 0; j <= ny; j++) canvas.drawLine(0, j * cellSize, bw, j * cellSize, gridPaint);

        if (cellSize >= 40) {
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(Math.max(8, cellSize / 4f));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setAntiAlias(true);
            textPaint.setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK);

            for (int row = 0; row < ny; row++) {
                int drawRow = (ny - 1) - row;
                for (int col = 0; col < nx; col++) {
                    float cx = col * cellSize + cellSize / 2f;
                    float cy = drawRow * cellSize + cellSize / 2f;
                    canvas.drawText(String.format(Locale.US, "%.0f", gridC[row][col]), cx, cy - 2f, textPaint);

                    Paint smallPaint = new Paint(textPaint);
                    smallPaint.setTextSize(Math.max(6, cellSize / 5f));
                    smallPaint.setColor(Color.argb(180, 180, 180, 255));
                    canvas.drawText(String.format(Locale.US, "d:%.0f", gridZ[row][col]), cx, cy + cellSize / 4f, smallPaint);
                }
            }
        }

        ivHeatmap.setImageBitmap(bitmap);
        updateValueDisplay(-1, -1);
    }

    private void setupTouchReader() {
        ivHeatmap.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) return true;
            if (nx == 0 || ny == 0 || gridC == null) return true;

            float imgW = ivHeatmap.getWidth();
            float imgH = ivHeatmap.getHeight();
            float bmpW = nx * cellSize;
            float bmpH = ny * cellSize;
            float scale = Math.min(imgW / bmpW, imgH / bmpH);
            float offsetX = (imgW - bmpW * scale) / 2f;
            float offsetY = (imgH - bmpH * scale) / 2f;
            float touchX = (event.getX() - offsetX) / scale;
            float touchY = (event.getY() - offsetY) / scale;

            int gi = (int) (touchX / cellSize);
            int screenRow = (int) (touchY / cellSize);
            int gj = (ny - 1) - screenRow;
            if (gi >= 0 && gi < nx && gj >= 0 && gj < ny) updateValueDisplay(gi, gj);
            return true;
        });
    }

    private void updateValueDisplay(int gi, int gj) {
        if (gi >= 0 && gi < nx && gj >= 0 && gj < ny) {
            double intensity = gridC[gj][gi];
            double depth = gridZ[gj][gi];
            int xCoord = xList.get(gi);
            int yCoord = yList.get(gj);
            if (tvIntensity != null) {
                tvIntensity.setText(String.format(Locale.US, "%.2f", intensity));
                tvIntensity.setTextColor(getIntensityColor(intensity));
            }
            if (tvDepth != null) tvDepth.setText(String.format(Locale.US, "%.1f cm", depth));
            if (tvCoords != null) tvCoords.setText(String.format(Locale.US, "X:%d Y:%d [%d,%d]", xCoord, yCoord, gi, gj));
            return;
        }
        if (tvIntensity != null) {
            tvIntensity.setText(String.format(Locale.US, "%.1f ~ %.1f", globalMinC, globalMaxC));
            tvIntensity.setTextColor(Color.parseColor("#00AAFF"));
        }
        if (tvDepth != null) tvDepth.setText(String.format(Locale.US, "%.1f ~ %.1f cm", globalMinZ, globalMaxZ));
        if (tvCoords != null) tvCoords.setText(String.format(Locale.US, "%dx%d grid", nx, ny));
    }

    private int getIntensityColor(double value) {
        float t = globalMaxC != globalMinC ? (float) ((value - globalMinC) / (globalMaxC - globalMinC)) : 0.5f;
        return getHeatmapColor(t);
    }

    private int getHeatmapColor(float t) {
        float v = Math.max(0f, Math.min(1f, t));
        if (v < 0.25f) {
            float f = v / 0.25f;
            return Color.rgb(0, (int) (f * 255f), (int) (255f - 127f * f));
        }
        if (v < 0.5f) {
            float f = (v - 0.25f) / 0.25f;
            return Color.rgb(0, (int) (255f - f * 128f), (int) (128f - f * 128f));
        }
        if (v < 0.75f) {
            float f = (v - 0.5f) / 0.25f;
            return Color.rgb((int) (255f * f), (int) (128f * f + 127f), 0);
        }
        return Color.rgb(255, (int) (255f - (v - 0.75f) / 0.25f * 255f), 0);
    }

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnExportVTK).setOnClickListener(v -> exportAs("vtk"));
        findViewById(R.id.btnExportGRD).setOnClickListener(v -> exportAs("grd"));
        findViewById(R.id.btnExportCSV).setOnClickListener(v -> exportAs("csv"));
        findViewById(R.id.btn3DView).setOnClickListener(v -> {
            if (dataPoints == null) return;
            Intent intent = new Intent(this, IsoSurfaceActivity.class);
            intent.putExtra("filePath", filePath);
            startActivity(intent);
        });
        findViewById(R.id.btn4DVoxler).setOnClickListener(v -> openInVoxler());
    }

    private void exportAs(String format) {
        if (dataPoints == null) return;
        String name = FileManager.getNameWithoutExtension(new File(filePath));
        File file;
        int messageRes;
        switch (format) {
            case "vtk":
                file = FileManager.exportVTK(this, name, dataPoints);
                messageRes = R.string.preview_vtk_exported;
                break;
            case "grd":
                file = FileManager.exportGRD(this, name, dataPoints);
                messageRes = R.string.preview_grd_exported;
                break;
            default:
                file = FileManager.saveCSV(this, name, dataPoints);
                messageRes = R.string.preview_csv_exported;
                break;
        }
        if (file != null) {
            Toast.makeText(this, String.format(getString(messageRes), file.getName()), Toast.LENGTH_LONG).show();
        }
    }

    private void openInVoxler() {
        if (dataPoints == null) return;

        TreeSet<Integer> xSet = new TreeSet<>();
        TreeSet<Integer> ySet = new TreeSet<>();
        for (ScanDataPoint p : dataPoints) {
            xSet.add(p.x);
            ySet.add(p.y);
        }
        List<Integer> cols = new ArrayList<>(xSet);
        List<Integer> rows = new ArrayList<>(ySet);
        int c = cols.size();
        int r = rows.size();
        if (c == 0 || r == 0) return;

        Map<Integer, Integer> xIndex = new HashMap<>();
        Map<Integer, Integer> yIndex = new HashMap<>();
        for (int i = 0; i < cols.size(); i++) xIndex.put(cols.get(i), i);
        for (int i = 0; i < rows.size(); i++) yIndex.put(rows.get(i), i);

        int step = cols.size() > 1 ? Math.abs(cols.get(1) - cols.get(0)) : 30;
        float[] gData = new float[c * r];
        float[] gDataZ = new float[c * r];
        for (ScanDataPoint p : dataPoints) {
            Integer xi = xIndex.get(p.x);
            Integer yi = yIndex.get(p.y);
            if (xi == null || yi == null) continue;
            int idx = yi * c + xi;
            if (idx >= 0 && idx < gData.length) {
                gData[idx] = (float) p.c;
                gDataZ[idx] = (float) p.z;
            }
        }

        Intent intent = new Intent(this, Pro4DVoxlerActivity.class);
        intent.putExtra("filePath", filePath);
        intent.putExtra("cols", c);
        intent.putExtra("rows", r);
        intent.putExtra("stepSize", step);
        intent.putExtra("gridData", gData);
        intent.putExtra("gridDataZ", gDataZ);
        startActivity(intent);
    }
}
