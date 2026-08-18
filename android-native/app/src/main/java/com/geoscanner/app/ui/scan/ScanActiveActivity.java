package com.geoscanner.app.ui.scan;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.ble.BLEManager;
import com.geoscanner.app.data.FileManager;
import com.geoscanner.app.data.GradientData;
import com.geoscanner.app.data.ScanDataPoint;
import com.geoscanner.app.utils.LocaleHelper;
import com.geoscanner.app.utils.SignalProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The manual grid-walk scanning screen: the operator physically moves the
 * device over each grid cell and taps "Record" to capture the live sensor
 * reading there. Falls back to a randomized demo feed when no device is
 * connected, so the workflow can still be exercised/tested.
 */
public class ScanActiveActivity extends AppCompatActivity {
    private BLEManager bleManager;
    private GridLayout gridLayout;
    private TextView tvStatus;
    private TextView tvCurrentValue;
    private TextView tvProgress;
    private TextView tvPosition;
    private Button btnCalibrate;
    private Button btnRecord;
    private Button btnFinish;

    private FrameLayout[][] cellViews;
    private float[][] gridData;
    private float[][] gridDataRaw;
    private boolean[][] gridCollected;

    private String scanName;
    private int gridWidth;
    private int gridHeight;
    private int stepSize;
    private int currentX = 0;
    private int currentY = 0;
    private float calibrationValue = 0f;
    private float lastSensorValue = 0f;
    private int collectedCount = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_active);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF111111);

        scanName = getIntent().getStringExtra("scanName");
        gridWidth = getIntent().getIntExtra("gridWidth", 6);
        gridHeight = getIntent().getIntExtra("gridHeight", 6);
        stepSize = getIntent().getIntExtra("stepSize", 30);

        gridData = new float[gridHeight][gridWidth];
        gridDataRaw = new float[gridHeight][gridWidth];
        gridCollected = new boolean[gridHeight][gridWidth];
        cellViews = new FrameLayout[gridHeight][gridWidth];

        initViews();
        buildGrid();
        setupBLE();

        currentX = 0;
        currentY = gridHeight - 1;
        highlightCurrentCell();
        updateUI();
    }

    private void initViews() {
        gridLayout = findViewById(R.id.gridLayout);
        tvStatus = findViewById(R.id.tvStatus);
        tvCurrentValue = findViewById(R.id.tvCurrentValue);
        tvProgress = findViewById(R.id.tvProgress);
        tvPosition = findViewById(R.id.tvPosition);
        btnCalibrate = findViewById(R.id.btnCalibrate);
        btnRecord = findViewById(R.id.btnRecord);
        btnFinish = findViewById(R.id.btnFinish);

        btnCalibrate.setOnClickListener(v -> {
            calibrationValue = lastSensorValue;
            Toast.makeText(this, String.format(getString(R.string.scan_calibrated), calibrationValue), Toast.LENGTH_SHORT).show();
        });
        btnRecord.setOnClickListener(v -> recordCurrentPoint());
        btnFinish.setOnClickListener(v -> finishScan());
        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());
    }

    private void confirmExit() {
        if (collectedCount > 0) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.records_delete_title))
                    .setMessage(collectedCount + " points. Exit?")
                    .setPositiveButton(getString(R.string.scan_finish), (d, w) -> finishScan())
                    .setNegativeButton(getString(R.string.records_delete), (d, w) -> finish())
                    .setNeutralButton(getString(R.string.records_cancel), null)
                    .show();
        } else {
            finish();
        }
    }

    private void buildGrid() {
        gridLayout.removeAllViews();
        gridLayout.setColumnCount(gridWidth);
        gridLayout.setRowCount(gridHeight);

        int cellSize = Math.max(Math.min(
                (getResources().getDisplayMetrics().widthPixels - 40) / gridWidth,
                (getResources().getDisplayMetrics().heightPixels / 2) / gridHeight), 30);

        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                FrameLayout cell = new FrameLayout(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(1, 1, 1, 1);
                params.rowSpec = GridLayout.spec(y);
                params.columnSpec = GridLayout.spec(x);
                cell.setLayoutParams(params);

                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setColor(Color.parseColor("#222222"));
                bg.setStroke(1, Color.parseColor("#444444"));
                cell.setBackground(bg);

                int fx = x, fy = y;
                cell.setOnClickListener(v -> {
                    currentX = fx;
                    currentY = fy;
                    updateUI();
                });

                cellViews[y][x] = cell;
                gridLayout.addView(cell);
            }
        }
        highlightCurrentCell();
    }

    private void setupBLE() {
        bleManager = BLEManager.getInstance();
        bleManager.setGradientListener(data -> {
            float gradient = data.getGradient();
            lastSensorValue = gradient;
            float adjusted = gradient - calibrationValue;
            runOnUiThread(() -> {
                tvCurrentValue.setText(String.format("%.1f", adjusted));
                tvStatus.setText(getString(R.string.scan_receiving));
            });
        });

        if (bleManager.isConnected()) {
            tvStatus.setText(getString(R.string.scan_receiving));
            bleManager.startLiveMode();
        } else {
            tvStatus.setText(getString(R.string.scan_demo_mode));
            startDemoMode();
        }
    }

    private void startDemoMode() {
        Random random = new Random();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                lastSensorValue = (random.nextFloat() - 0.5f) * 200f;
                float adjusted = lastSensorValue - calibrationValue;
                tvCurrentValue.setText(String.format("%.1f", adjusted));
                handler.postDelayed(this, 200L);
            }
        }, 200L);
    }

    private void recordCurrentPoint() {
        if (currentX < 0 || currentX >= gridWidth || currentY < 0 || currentY >= gridHeight) return;

        float rawValue = lastSensorValue;
        float adjusted = rawValue - calibrationValue;
        gridData[currentY][currentX] = adjusted;
        gridDataRaw[currentY][currentX] = rawValue;
        gridCollected[currentY][currentX] = true;
        collectedCount++;

        updateCellColor(currentX, currentY, adjusted);
        advanceToNextCell();
        updateUI();

        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(50L);
        } catch (Exception ignored) {
        }
    }

    private void updateCellColor(int x, int y, float value) {
        if (cellViews[y][x] == null) return;

        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < gridHeight; i++) {
            for (int j = 0; j < gridWidth; j++) {
                if (gridCollected[i][j]) {
                    min = Math.min(min, gridData[i][j]);
                    max = Math.max(max, gridData[i][j]);
                }
            }
        }

        for (int i = 0; i < gridHeight; i++) {
            for (int j = 0; j < gridWidth; j++) {
                if (!gridCollected[i][j]) continue;
                float norm = SignalProcessor.anomalyToColor(gridData[i][j], min, max);
                int color = getHeatmapColor(norm);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setColor(color);
                bg.setStroke(1, Color.parseColor("#444444"));
                cellViews[i][j].setBackground(bg);
            }
        }
    }

    private int getHeatmapColor(float t) {
        if (t < 0.25f) {
            float f = t / 0.25f;
            return Color.rgb(0, (int) (f * 255f), (int) (255f - 127f * f));
        }
        if (t < 0.5f) {
            float f = (t - 0.25f) / 0.25f;
            return Color.rgb(0, (int) (255f - f * 128f), (int) (128f - f * 128f));
        }
        if (t < 0.75f) {
            float f = (t - 0.5f) / 0.25f;
            return Color.rgb((int) (255f * f), (int) (128f * f + 127f), 0);
        }
        return Color.rgb(255, (int) (255f - (t - 0.75f) / 0.25f * 255f), 0);
    }

    private void advanceToNextCell() {
        // Boustrophedon (zigzag) walk: up a column, across, down the next.
        if (currentX % 2 == 0) {
            if (currentY > 0) currentY--;
            else if (currentX < gridWidth - 1) currentX++;
        } else {
            if (currentY < gridHeight - 1) currentY++;
            else if (currentX < gridWidth - 1) currentX++;
        }
        highlightCurrentCell();
    }

    private void highlightCurrentCell() {
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if (cellViews[y][x] == null || gridCollected[y][x]) continue;
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setColor(Color.parseColor("#222222"));
                if (x == currentX && y == currentY) {
                    bg.setStroke(3, Color.parseColor("#00AAFF"));
                } else {
                    bg.setStroke(1, Color.parseColor("#444444"));
                }
                cellViews[y][x].setBackground(bg);
            }
        }
    }

    private void updateUI() {
        int total = gridWidth * gridHeight;
        tvProgress.setText(String.format(getString(R.string.scan_progress), collectedCount, total));
        tvPosition.setText(String.format(getString(R.string.scan_position), currentX, currentY));
        if (collectedCount >= total) {
            btnFinish.setVisibility(android.view.View.VISIBLE);
            tvStatus.setText(getString(R.string.scan_complete));
        }
    }

    private void finishScan() {
        List<ScanDataPoint> points = new ArrayList<>();
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if (!gridCollected[y][x]) continue;
                float depth = SignalProcessor.normalizeDepth(gridData[y][x], gridWidth * stepSize, gridHeight * stepSize);
                points.add(new ScanDataPoint(x * stepSize, y * stepSize, depth, gridData[y][x]));
            }
        }

        File file = FileManager.save7ESX(this, scanName, points, gridWidth, gridHeight, stepSize);
        if (file != null) {
            Toast.makeText(this, String.format(getString(R.string.scan_saved), file.getName()), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ScanPreviewActivity.class);
            intent.putExtra("filePath", file.getAbsolutePath());
            intent.putExtra("gridWidth", gridWidth);
            intent.putExtra("gridHeight", gridHeight);
            startActivity(intent);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
