package com.geoscanner.app.ui.simlab;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.geoscanner.app.R;
import com.geoscanner.app.simulation.SimAnalysisEngine;
import com.geoscanner.app.simulation.SimAnomalyCluster;
import com.geoscanner.app.simulation.SimDataPoint;
import com.geoscanner.app.simulation.SimRunConfig;
import com.geoscanner.app.simulation.SimReportGenerator;
import com.geoscanner.app.utils.LocaleHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows the results of {@link SimAnalysisEngine} for the most recently
 * generated simulated scan: detected anomaly clusters with shape, two
 * independent depth estimates, an AI candidate label with confidence and
 * explanation, and a false-positive flag. The true (simulation-only) depth
 * is shown separately, clearly labeled, as a training aid — the classifier
 * itself never sees it.
 */
public class SimAnalysisActivity extends AppCompatActivity {
    public static final String EXTRA_POINTS = "sim_points";
    public static final String EXTRA_CONFIG = "sim_config";
    public static final String EXTRA_REAL_DATA = "sim_real_data";

    private LinearLayout resultsContainer;
    private SimRunConfig config;
    private List<SimAnomalyCluster> clusters;
    private boolean realData;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sim_analysis);
        getWindow().setStatusBarColor(0xFF111111);
        getWindow().setNavigationBarColor(0xFF111111);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPdfReport).setOnClickListener(v -> generateReport());
        findViewById(R.id.btnArView).setOnClickListener(v -> openArView());
        resultsContainer = findViewById(R.id.resultsContainer);
        TextView tvSummary = findViewById(R.id.tvSummary);

        List<SimDataPoint> points = (List<SimDataPoint>) getIntent().getSerializableExtra(EXTRA_POINTS);
        config = (SimRunConfig) getIntent().getSerializableExtra(EXTRA_CONFIG);
        realData = getIntent().getBooleanExtra(EXTRA_REAL_DATA, false);
        if (points == null) points = new ArrayList<>();
        if (config == null) config = new SimRunConfig();

        clusters = SimAnalysisEngine.analyze(points, config.grid, config.sensor.heightAboveGroundM, config.interferences);
        tvSummary.setText(String.format(Locale.US, getString(R.string.simanalysis_summary), clusters.size()));

        if (realData) {
            TextView realBanner = new TextView(this);
            realBanner.setText(getString(R.string.simanalysis_real_banner));
            realBanner.setTextColor(0xFFFF8800);
            realBanner.setTextSize(12);
            realBanner.setPadding(0, 0, 0, dp(8));
            resultsContainer.addView(realBanner);

            LinearLayout similarCard = new LinearLayout(this);
            similarCard.setOrientation(LinearLayout.VERTICAL);
            similarCard.setBackgroundResource(R.drawable.btn_card);
            int pad = dp(16);
            similarCard.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            similarCard.setLayoutParams(lp);
            TextView similarTitle = new TextView(this);
            similarTitle.setText(getString(R.string.simanalysis_similar_title));
            similarTitle.setTextColor(0xFF00AAFF);
            similarTitle.setTextSize(14);
            similarTitle.setTypeface(similarTitle.getTypeface(), Typeface.BOLD);
            similarCard.addView(similarTitle);
            TextView similarBody = new TextView(this);
            similarBody.setText(SimAnalysisEngine.suggestSimilarScenario(clusters));
            similarBody.setTextColor(0xFFFFFFFF);
            similarBody.setTextSize(13);
            similarBody.setPadding(0, dp(6), 0, 0);
            similarCard.addView(similarBody);
            resultsContainer.addView(similarCard);
        }

        if (clusters.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.simanalysis_none));
            empty.setTextColor(0xFF888888);
            resultsContainer.addView(empty);
            return;
        }

        for (int i = 0; i < clusters.size(); i++) {
            resultsContainer.addView(buildClusterCard(i + 1, clusters.get(i)));
        }
    }

    private Bitmap captureScreenshot() {
        try {
            resultsContainer.setDrawingCacheEnabled(true);
            int width = resultsContainer.getWidth();
            int height = resultsContainer.getHeight();
            if (width <= 0 || height <= 0) return null;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(0xFF111111);
            resultsContainer.draw(canvas);
            resultsContainer.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private void openArView() {
        if (clusters.isEmpty()) {
            Toast.makeText(this, getString(R.string.simanalysis_none), Toast.LENGTH_SHORT).show();
            return;
        }
        if (config.latitude == null || config.longitude == null || config.headingDeg == null) {
            Toast.makeText(this, getString(R.string.ar_no_reference), Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, com.geoscanner.app.ui.ar.ArOverlayActivity.class);
        intent.putExtra(com.geoscanner.app.ui.ar.ArOverlayActivity.EXTRA_CLUSTERS, new ArrayList<>(clusters));
        intent.putExtra(com.geoscanner.app.ui.ar.ArOverlayActivity.EXTRA_CONFIG, config);
        startActivity(intent);
    }

    private void generateReport() {
        Bitmap screenshot = captureScreenshot();
        File pdf = SimReportGenerator.generate(this, config, clusters, screenshot);
        if (pdf == null) {
            Toast.makeText(this, getString(R.string.simreport_error), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, getString(R.string.simreport_saved), Toast.LENGTH_SHORT).show();
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

    private LinearLayout buildClusterCard(int index, SimAnomalyCluster cluster) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.btn_card);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        String typeName = cluster.candidateType != null ? cluster.candidateType.displayNameTr : "?";
        TextView title = new TextView(this);
        title.setText(String.format(Locale.US, "#%d — %s adayı (%%%.0f güven)", index, typeName, cluster.confidence * 100));
        title.setTextColor(cluster.falsePositiveRisk ? 0xFFFF8800 : 0xFF00AAFF);
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        card.addView(title);

        addLine(card, String.format(Locale.US, "Konum: (%.2f, %.2f) m   Şekil: %s   Genlik: %.1f",
                cluster.centerXM, cluster.centerYM, cluster.shapeLabel(), cluster.peakAmplitude));

        addLine(card, String.format(Locale.US, "Derinlik — yarı-genişlik yöntemi: %.2f m", cluster.depthEstimateHalfWidthM));
        addLine(card, String.format(Locale.US, "Derinlik — genlik ters çözüm yöntemi: %.2f m", cluster.depthEstimateInversionM));
        addLine(card, String.format(Locale.US, "Yöntemler arası belirsizlik: ±%.2f m", cluster.depthUncertaintyM));

        if (!realData) {
            TextView trueDepth = new TextView(this);
            trueDepth.setText(String.format(Locale.US, "Gerçek (simülasyon) derinlik: %.2f m — yalnızca eğitim/karşılaştırma amaçlı", cluster.trueDepthM));
            trueDepth.setTextColor(0xFFFFD700);
            trueDepth.setTextSize(12);
            trueDepth.setPadding(0, dp(4), 0, 0);
            card.addView(trueDepth);
        }

        TextView why = new TextView(this);
        why.setText(cluster.explanation);
        why.setTextColor(0xFFAAAAAA);
        why.setTextSize(12);
        why.setPadding(0, dp(6), 0, 0);
        card.addView(why);

        if (cluster.falsePositiveRisk) {
            TextView warn = new TextView(this);
            warn.setText("⚠ " + cluster.falsePositiveReason);
            warn.setTextColor(0xFFFF4444);
            warn.setTextSize(12);
            warn.setPadding(0, dp(6), 0, 0);
            card.addView(warn);
        }

        TextView markLine = new TextView(this);
        markLine.setTextColor(0xFF00FF88);
        markLine.setTextSize(12);
        markLine.setTypeface(markLine.getTypeface(), Typeface.BOLD);
        markLine.setPadding(0, dp(6), 0, 0);
        updateMarkLine(markLine, cluster);
        card.addView(markLine);

        TextView btnMark = new TextView(this);
        btnMark.setText(getString(R.string.simanalysis_mark));
        btnMark.setTextColor(0xFFFFFFFF);
        btnMark.setGravity(Gravity.CENTER);
        btnMark.setBackgroundResource(R.drawable.grid_cell);
        btnMark.setTextSize(12);
        btnMark.setPadding(0, dp(8), 0, dp(8));
        LinearLayout.LayoutParams markBtnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        markBtnLp.topMargin = dp(8);
        btnMark.setLayoutParams(markBtnLp);
        btnMark.setOnClickListener(v -> showMarkDialog(cluster, markLine));
        card.addView(btnMark);

        return card;
    }

    private void updateMarkLine(TextView markLine, SimAnomalyCluster cluster) {
        boolean hasMark = cluster.userMarkLabel != null && !cluster.userMarkLabel.trim().isEmpty();
        markLine.setText(hasMark ? "🏷 " + cluster.userMarkLabel.trim() : "");
        markLine.setVisibility(hasMark ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void showMarkDialog(SimAnomalyCluster cluster, TextView markLine) {
        int pad = dp(20);
        int gap = dp(10);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(getString(R.string.simanalysis_mark_dialog_title));
        title.setTextColor(0xFF00AAFF);
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding(0, 0, 0, gap);
        container.addView(title);

        String[] presets = {getString(R.string.simanalysis_mark_target_a), getString(R.string.simanalysis_mark_target_b),
                getString(R.string.simanalysis_mark_suspicious), getString(R.string.simanalysis_mark_recheck)};

        EditText input = new EditText(this);
        input.setText(cluster.userMarkLabel);
        input.setHint(getString(R.string.simanalysis_mark_hint));
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);
        container.addView(input);

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, gap, 0, 0);
        for (String preset : presets) {
            TextView chip = new TextView(this);
            chip.setText(preset);
            chip.setTextColor(0xFFFFFFFF);
            chip.setTextSize(11);
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundResource(R.drawable.grid_cell);
            chip.setPadding(dp(8), dp(6), dp(8), dp(6));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            chipLp.setMarginEnd(dp(4));
            chip.setLayoutParams(chipLp);
            chip.setOnClickListener(v -> input.setText(preset));
            chipRow.addView(chip);
        }
        container.addView(chipRow);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, gap * 2, 0, 0);

        TextView btnCancel = new TextView(this);
        btnCancel.setText(getString(R.string.cancel));
        btnCancel.setTextColor(0xFFFFFFFF);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setBackgroundResource(R.drawable.btn_card);
        btnCancel.setPadding(0, gap, 0, gap);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cancelParams.setMarginEnd(gap / 2);
        btnCancel.setLayoutParams(cancelParams);

        TextView btnSave = new TextView(this);
        btnSave.setText(getString(R.string.simlab_target_save));
        btnSave.setTextColor(0xFFFFFFFF);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackgroundResource(R.drawable.btn_primary);
        btnSave.setPadding(0, gap, 0, gap);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        saveParams.setMarginStart(gap / 2);
        btnSave.setLayoutParams(saveParams);

        buttonRow.addView(btnCancel);
        buttonRow.addView(btnSave);
        container.addView(buttonRow);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this, R.style.Theme_GeoScanner_Dialog)
                .setView(container)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            cluster.userMarkLabel = input.getText().toString().trim();
            updateMarkLine(markLine, cluster);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addLine(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(13);
        tv.setPadding(0, dp(6), 0, 0);
        parent.addView(tv);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
