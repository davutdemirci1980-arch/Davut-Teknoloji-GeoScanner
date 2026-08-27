package com.geoscanner.app.ui.compare;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.geoscanner.app.R;
import com.geoscanner.app.compare.DiffMapView;
import com.geoscanner.app.compare.DiffReportGenerator;
import com.geoscanner.app.compare.DiffRegion;
import com.geoscanner.app.compare.ScanDiffEngine;
import com.geoscanner.app.compare.ScanDiffResult;
import com.geoscanner.app.data.FileManager;
import com.geoscanner.app.data.ScanDataPoint;
import com.geoscanner.app.utils.LocaleHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Section: temporal comparison. The user picks two CSVs of the same area
 * taken at different times; {@link ScanDiffEngine} auto-aligns and diffs
 * them, and this screen shows the result as a 2D (optionally simple-3D
 * relief) map with unchanged areas faded out, a changed-region list with
 * coordinates and percentage, and PNG/PDF export.
 */
public class TimeCompareActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_A = 401;
    private static final int REQUEST_PICK_B = 402;

    private File fileA;
    private File fileB;
    private List<ScanDataPoint> pointsA;
    private List<ScanDataPoint> pointsB;
    private ScanDiffResult result;
    private boolean reliefMode = false;

    private TextView tvFileA;
    private TextView tvFileB;
    private TextView btnCompare;
    private TextView tvSummary;
    private TextView tvAlignment;
    private LinearLayout resultsContainer;
    private LinearLayout captureContainer;
    private LinearLayout regionsContainer;
    private DiffMapView diffMapView;
    private TextView btnMode2D;
    private TextView btnMode3D;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_compare);
        getWindow().setStatusBarColor(0xFF111111);
        getWindow().setNavigationBarColor(0xFF111111);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvFileA = findViewById(R.id.tvFileA);
        tvFileB = findViewById(R.id.tvFileB);
        btnCompare = findViewById(R.id.btnCompare);
        tvSummary = findViewById(R.id.tvSummary);
        tvAlignment = findViewById(R.id.tvAlignment);
        resultsContainer = findViewById(R.id.resultsContainer);
        captureContainer = findViewById(R.id.captureContainer);
        regionsContainer = findViewById(R.id.regionsContainer);
        diffMapView = findViewById(R.id.diffMapView);
        btnMode2D = findViewById(R.id.btnMode2D);
        btnMode3D = findViewById(R.id.btnMode3D);

        findViewById(R.id.cardPickA).setOnClickListener(v -> pickFile(REQUEST_PICK_A));
        findViewById(R.id.cardPickB).setOnClickListener(v -> pickFile(REQUEST_PICK_B));
        btnCompare.setOnClickListener(v -> runCompare());
        btnMode2D.setOnClickListener(v -> setMode(false));
        btnMode3D.setOnClickListener(v -> setMode(true));
        findViewById(R.id.btnExportPng).setOnClickListener(v -> exportPng());
        findViewById(R.id.btnExportPdf).setOnClickListener(v -> exportPdf());
    }

    private void pickFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.import_select)), requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode != REQUEST_PICK_A && requestCode != REQUEST_PICK_B) return;

        try {
            File dir = new File(getExternalFilesDir(null), "compare_tmp");
            if (!dir.exists()) dir.mkdirs();
            String fileName = (requestCode == REQUEST_PICK_A ? "scanA_" : "scanB_") + System.currentTimeMillis() + ".csv";
            File destFile = new File(dir, fileName);
            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while (is != null && (len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            }

            List<ScanDataPoint> points = FileManager.readAuto(destFile);
            if (points == null || points.isEmpty()) {
                Toast.makeText(this, getString(R.string.import_parse_error), Toast.LENGTH_SHORT).show();
                return;
            }

            String displayName = queryDisplayName(uri, destFile.getName());
            if (requestCode == REQUEST_PICK_A) {
                fileA = destFile;
                pointsA = points;
                tvFileA.setText(displayName);
            } else {
                fileB = destFile;
                pointsB = points;
                tvFileB.setText(displayName);
            }
            updateCompareEnabled();
        } catch (Exception e) {
            Toast.makeText(this, String.format(getString(R.string.import_error), e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private String queryDisplayName(Uri uri, String fallback) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void updateCompareEnabled() {
        boolean ready = pointsA != null && pointsB != null;
        btnCompare.setEnabled(ready);
        btnCompare.setAlpha(ready ? 1f : 0.5f);
    }

    private void runCompare() {
        if (pointsA == null || pointsB == null) return;
        result = ScanDiffEngine.compare(pointsA, pointsB);
        if (result == null || result.overlapCells == 0) {
            Toast.makeText(this, getString(R.string.timecompare_error_no_overlap), Toast.LENGTH_LONG).show();
            resultsContainer.setVisibility(android.view.View.GONE);
            return;
        }

        resultsContainer.setVisibility(android.view.View.VISIBLE);
        diffMapView.setResult(result);
        diffMapView.setReliefMode(reliefMode);

        tvSummary.setText(String.format(Locale.US, getString(R.string.timecompare_overall_changed),
                result.overallChangedPercent, result.changedCells, result.overlapCells));
        tvAlignment.setText(String.format(Locale.US, getString(R.string.timecompare_alignment_info),
                result.shiftX, result.shiftY, result.correlationScore));

        regionsContainer.removeAllViews();
        if (result.regions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.timecompare_no_regions));
            empty.setTextColor(0xFF888888);
            empty.setTextSize(13);
            regionsContainer.addView(empty);
        } else {
            int index = 1;
            for (DiffRegion region : result.regions) {
                regionsContainer.addView(buildRegionCard(index++, region));
            }
        }
    }

    private LinearLayout buildRegionCard(int index, DiffRegion region) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.btn_card);
        int pad = dp(12);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(String.format(Locale.US, "#%d — %s %%%.0f", index,
                getString(region.increase ? R.string.timecompare_increase : R.string.timecompare_decrease), region.pctChange));
        title.setTextColor(region.increase ? 0xFFFF6644 : 0xFF4499FF);
        title.setTextSize(14);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView coords = new TextView(this);
        coords.setText(String.format(Locale.US, "(%.2f, %.2f) m — (%.2f, %.2f) m   [%d hücre]",
                region.minXm, region.minYm, region.maxXm, region.maxYm, region.cellCount));
        coords.setTextColor(0xFFCCCCCC);
        coords.setTextSize(12);
        coords.setPadding(0, dp(4), 0, 0);
        card.addView(coords);

        return card;
    }

    private void setMode(boolean relief) {
        reliefMode = relief;
        diffMapView.setReliefMode(relief);
        btnMode2D.setBackgroundResource(relief ? R.drawable.grid_cell : R.drawable.btn_primary);
        btnMode3D.setBackgroundResource(relief ? R.drawable.btn_primary : R.drawable.grid_cell);
    }

    private Bitmap captureContainerBitmap() {
        try {
            int width = captureContainer.getWidth();
            int height = captureContainer.getHeight();
            if (width <= 0 || height <= 0) return null;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            captureContainer.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private void exportPng() {
        if (result == null) return;
        Bitmap bitmap = captureContainerBitmap();
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.timecompare_export_error), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(getExternalFilesDir(null), "reports");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "FarkHaritasi_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Toast.makeText(this, getString(R.string.timecompare_export_saved), Toast.LENGTH_SHORT).show();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "image/png");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.timecompare_export_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportPdf() {
        if (result == null) return;
        Bitmap bitmap = captureContainerBitmap();
        File pdf = DiffReportGenerator.generate(this, result,
                fileA != null ? fileA.getName() : "A", fileB != null ? fileB.getName() : "B", bitmap);
        if (pdf == null) {
            Toast.makeText(this, getString(R.string.timecompare_export_error), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, getString(R.string.timecompare_export_saved), Toast.LENGTH_SHORT).show();
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdf);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
