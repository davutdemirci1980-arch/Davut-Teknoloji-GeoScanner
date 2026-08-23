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
import com.geoscanner.app.simulation.SimDataPoint;
import com.geoscanner.app.simulation.SimGridConfig;
import com.geoscanner.app.simulation.SimInterferenceSource;
import com.geoscanner.app.utils.LocaleHelper;

import java.io.Serializable;
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
    public static final String EXTRA_INTERFERENCES = "sim_interferences";
    public static final String EXTRA_COLS = "sim_cols";
    public static final String EXTRA_ROWS = "sim_rows";
    public static final String EXTRA_STEP_CM = "sim_step_cm";
    public static final String EXTRA_SENSOR_HEIGHT_M = "sim_sensor_height_m";

    private LinearLayout resultsContainer;

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
        resultsContainer = findViewById(R.id.resultsContainer);
        TextView tvSummary = findViewById(R.id.tvSummary);

        List<SimDataPoint> points = (List<SimDataPoint>) getIntent().getSerializableExtra(EXTRA_POINTS);
        List<SimInterferenceSource> interferences = (List<SimInterferenceSource>) getIntent().getSerializableExtra(EXTRA_INTERFERENCES);
        SimGridConfig grid = new SimGridConfig();
        grid.cols = getIntent().getIntExtra(EXTRA_COLS, 11);
        grid.rows = getIntent().getIntExtra(EXTRA_ROWS, 11);
        grid.stepCm = getIntent().getDoubleExtra(EXTRA_STEP_CM, 30);
        double sensorHeightM = getIntent().getDoubleExtra(EXTRA_SENSOR_HEIGHT_M, 0.1);

        if (points == null) points = new ArrayList<>();

        List<SimAnomalyCluster> clusters = SimAnalysisEngine.analyze(points, grid, sensorHeightM, interferences);
        tvSummary.setText(String.format(Locale.US, getString(R.string.simanalysis_summary), clusters.size()));

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

        TextView trueDepth = new TextView(this);
        trueDepth.setText(String.format(Locale.US, "Gerçek (simülasyon) derinlik: %.2f m — yalnızca eğitim/karşılaştırma amaçlı", cluster.trueDepthM));
        trueDepth.setTextColor(0xFFFFD700);
        trueDepth.setTextSize(12);
        trueDepth.setPadding(0, dp(4), 0, 0);
        card.addView(trueDepth);

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

        return card;
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
