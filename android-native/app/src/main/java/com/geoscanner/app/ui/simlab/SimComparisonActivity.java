package com.geoscanner.app.ui.simlab;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.simulation.SimAnalysisEngine;
import com.geoscanner.app.simulation.SimAnomalyCluster;
import com.geoscanner.app.simulation.SimComparisonEngine;
import com.geoscanner.app.simulation.SimComparisonResult;
import com.geoscanner.app.simulation.SimDataPoint;
import com.geoscanner.app.simulation.SimRunConfig;
import com.geoscanner.app.utils.LocaleHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows section-18 comparative/repeatability analysis: correlates two
 * independently-generated scans of the same scene and matches their
 * detected anomaly clusters into "seen in both" (likely real) vs
 * "seen in only one scan" (worth a second look — noise, drift, or a
 * one-off operator error).
 */
public class SimComparisonActivity extends AppCompatActivity {
    public static final String EXTRA_POINTS_A = "sim_points_a";
    public static final String EXTRA_POINTS_B = "sim_points_b";
    public static final String EXTRA_CONFIG = "sim_config";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sim_comparison);
        getWindow().setStatusBarColor(0xFF111111);
        getWindow().setNavigationBarColor(0xFF111111);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        List<SimDataPoint> rawA = (List<SimDataPoint>) getIntent().getSerializableExtra(EXTRA_POINTS_A);
        List<SimDataPoint> rawB = (List<SimDataPoint>) getIntent().getSerializableExtra(EXTRA_POINTS_B);
        SimRunConfig config = (SimRunConfig) getIntent().getSerializableExtra(EXTRA_CONFIG);
        if (rawA == null) rawA = new ArrayList<>();
        if (rawB == null) rawB = new ArrayList<>();
        if (config == null) config = new SimRunConfig();

        List<SimAnomalyCluster> clustersA = SimAnalysisEngine.analyze(rawA, config.grid, config.sensor.heightAboveGroundM, config.interferences);
        List<SimAnomalyCluster> clustersB = SimAnalysisEngine.analyze(rawB, config.grid, config.sensor.heightAboveGroundM, config.interferences);
        SimComparisonResult result = SimComparisonEngine.compare(rawA, rawB, clustersA, clustersB, config.grid.stepM());

        TextView tvScoreValue = findViewById(R.id.tvScoreValue);
        String qualityLabel = result.repeatabilityScore >= 0.8 ? getString(R.string.simcompare_quality_high)
                : result.repeatabilityScore >= 0.5 ? getString(R.string.simcompare_quality_medium)
                : getString(R.string.simcompare_quality_low);
        tvScoreValue.setText(String.format(Locale.US, "%.2f — %s", result.repeatabilityScore, qualityLabel));

        LinearLayout container = findViewById(R.id.resultsContainer);
        addSection(container, getString(R.string.simcompare_common, result.common.size()), result.common, 0xFF00FF88);
        addSection(container, getString(R.string.simcompare_only_a, result.onlyInA.size()), result.onlyInA, 0xFFFFD700);
        addSection(container, getString(R.string.simcompare_only_b, result.onlyInB.size()), result.onlyInB, 0xFFFFD700);
    }

    private void addSection(LinearLayout parent, String title, List<SimAnomalyCluster> clusters, int accentColor) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(0xFF00AAFF);
        header.setTextSize(15);
        header.setTypeface(header.getTypeface(), Typeface.BOLD);
        header.setPadding(0, dp(14), 0, dp(6));
        parent.addView(header);

        if (clusters.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.simcompare_none));
            empty.setTextColor(0xFF888888);
            empty.setTextSize(12);
            parent.addView(empty);
            return;
        }

        for (SimAnomalyCluster c : clusters) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.btn_card);
            int pad = dp(12);
            card.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            card.setLayoutParams(lp);

            String typeName = c.candidateType != null ? c.candidateType.displayNameTr : "?";
            TextView tv = new TextView(this);
            tv.setText(String.format(Locale.US, "%s adayı  (%.2f, %.2f)m  güven %%%.0f",
                    typeName, c.centerXM, c.centerYM, c.confidence * 100));
            tv.setTextColor(accentColor);
            tv.setTextSize(13);
            card.addView(tv);

            parent.addView(card);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
