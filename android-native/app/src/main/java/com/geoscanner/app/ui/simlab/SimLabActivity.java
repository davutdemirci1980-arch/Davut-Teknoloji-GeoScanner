package com.geoscanner.app.ui.simlab;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.data.FileManager;
import com.geoscanner.app.data.ScanDataPoint;
import com.geoscanner.app.simulation.GroundType;
import com.geoscanner.app.simulation.InterferenceType;
import com.geoscanner.app.simulation.OperatorErrorConfig;
import com.geoscanner.app.simulation.ScanPattern;
import com.geoscanner.app.simulation.SensorMode;
import com.geoscanner.app.simulation.SimCalibrationConfig;
import com.geoscanner.app.simulation.SimDataPoint;
import com.geoscanner.app.simulation.SimGridConfig;
import com.geoscanner.app.simulation.SimGroundConfig;
import com.geoscanner.app.simulation.SimInterferenceSource;
import com.geoscanner.app.simulation.SimRunConfig;
import com.geoscanner.app.simulation.SimScenarioPresets;
import com.geoscanner.app.simulation.SimSensorConfig;
import com.geoscanner.app.simulation.SimTarget;
import com.geoscanner.app.simulation.SimulationEngine;
import com.geoscanner.app.simulation.TargetType;
import com.geoscanner.app.ui.scan.ScanPreviewActivity;
import com.geoscanner.app.utils.LocaleHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Simulation lab: builds a synthetic underground scene (targets + ground +
 * sensor + grid), runs it through {@link SimulationEngine}, and hands the
 * result to the existing 2D/3D/4D viewers via a SIM_-prefixed CSV file —
 * exactly the same pipeline real device scans already use, so the output is
 * never confused with (or written into) real measurement data.
 */
public class SimLabActivity extends AppCompatActivity {
    private LinearLayout dynamicContainer;
    private LinearLayout targetListContainer;
    private Spinner spGround;
    private SeekBar sbInterference;
    private Spinner spSensorMode;
    private EditText etHeight;
    private EditText etSpacing;
    private EditText etCols;
    private EditText etRows;
    private EditText etStep;
    private Spinner spPattern;
    private Spinner spPreset;
    private CheckBox cbExamMode;
    private boolean examAnswerRevealed = false;
    private LinearLayout interferenceListContainer;
    private CheckBox cbOperatorError;
    private SeekBar sbOperatorErrorSeverity;
    private CheckBox cbReferenceFirstColumn;
    private CheckBox cbBalanceDualSensors;
    private EditText etOperatorNote;

    private final List<SimTarget> targets = new ArrayList<>();
    private final List<SimInterferenceSource> interferences = new ArrayList<>();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sim_lab);
        getWindow().setStatusBarColor(0xFF111111);
        getWindow().setNavigationBarColor(0xFF111111);

        dynamicContainer = findViewById(R.id.dynamicContainer);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnStartSim).setOnClickListener(v -> startSimulation());
        findViewById(R.id.btnAnalyze).setOnClickListener(v -> runAnalysis());
        findViewById(R.id.btnCompare).setOnClickListener(v -> runComparison());

        buildGroundSection();
        buildInterferenceSection();
        buildOperatorErrorSection();
        buildCalibrationSection();
        buildSensorSection();
        buildGridSection();
        buildPresetSection();
        buildTargetSection();
        buildNoteSection();
    }

    private void buildNoteSection() {
        LinearLayout c = card(getString(R.string.simlab_section_note));
        etOperatorNote = new EditText(this);
        etOperatorNote.setHint(getString(R.string.simlab_note_hint));
        etOperatorNote.setTextColor(0xFFFFFFFF);
        etOperatorNote.setHintTextColor(0xFF666666);
        etOperatorNote.setMinLines(2);
        c.addView(etOperatorNote);
    }

    // ---------------------------------------------------------------- UI build helpers

    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.btn_card);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFF00AAFF);
        t.setTextSize(15);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setPadding(0, 0, 0, dp(8));
        card.addView(t);

        dynamicContainer.addView(card);
        return card;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView label(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFAAAAAA);
        t.setTextSize(13);
        t.setPadding(0, dp(8), 0, dp(2));
        parent.addView(t);
        return t;
    }

    private Spinner spinner(LinearLayout parent, String labelText, String[] options) {
        label(parent, labelText);
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        parent.addView(sp);
        return sp;
    }

    private EditText numberField(LinearLayout parent, String labelText, String defaultValue) {
        label(parent, labelText);
        EditText et = new EditText(this);
        et.setText(defaultValue);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        et.setTextColor(0xFFFFFFFF);
        parent.addView(et);
        return et;
    }

    private void chipRow(LinearLayout parent, String[] labels, String[] values, EditText target) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        for (int i = 0; i < labels.length; i++) {
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextColor(0xFFFFFFFF);
            chip.setTextSize(12);
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundResource(R.drawable.grid_cell);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(i == labels.length - 1 ? 0 : dp(6));
            chip.setLayoutParams(lp);
            String value = values[i];
            chip.setOnClickListener(v -> target.setText(value));
            row.addView(chip);
        }
        parent.addView(row);
    }

    // ---------------------------------------------------------------- sections

    private void buildGroundSection() {
        LinearLayout c = card(getString(R.string.simlab_section_ground));
        spGround = spinner(c, getString(R.string.simlab_ground_type), GroundType.displayNames());

        label(c, getString(R.string.simlab_interference));
        sbInterference = new SeekBar(this);
        sbInterference.setMax(100);
        sbInterference.setProgress(40);
        c.addView(sbInterference);
    }

    private void buildInterferenceSection() {
        LinearLayout c = card(getString(R.string.simlab_section_interference));
        interferenceListContainer = new LinearLayout(this);
        interferenceListContainer.setOrientation(LinearLayout.VERTICAL);
        c.addView(interferenceListContainer);

        TextView btnAdd = new TextView(this);
        btnAdd.setText(getString(R.string.simlab_interference_add));
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setBackgroundResource(R.drawable.btn_card);
        btnAdd.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        btnAdd.setLayoutParams(lp);
        btnAdd.setOnClickListener(v -> showAddInterferenceDialog());
        c.addView(btnAdd);

        renderInterferenceList();
    }

    private void renderInterferenceList() {
        interferenceListContainer.removeAllViews();
        if (interferences.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.simlab_interference_empty));
            empty.setTextColor(0xFF888888);
            empty.setTextSize(13);
            interferenceListContainer.addView(empty);
            return;
        }
        for (int i = 0; i < interferences.size(); i++) {
            SimInterferenceSource s = interferences.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView tv = new TextView(this);
            tv.setText(String.format(Locale.US, "%d. %s  (%.2f, %.2f)m", i + 1, s.label(), s.xM, s.yM));
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(12);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView remove = new TextView(this);
            remove.setText("✕");
            remove.setTextColor(0xFFFF4444);
            remove.setPadding(dp(12), 0, dp(4), 0);
            int idx = i;
            remove.setOnClickListener(v -> {
                interferences.remove(idx);
                renderInterferenceList();
            });

            row.addView(tv);
            row.addView(remove);
            interferenceListContainer.addView(row);
        }
    }

    private void showAddInterferenceDialog() {
        int pad = dp(20);
        int gap = dp(10);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(getString(R.string.simlab_interference_dialog_title));
        title.setTextColor(0xFF00AAFF);
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding(0, 0, 0, gap);
        container.addView(title);

        Spinner spType = spinner(container, getString(R.string.simlab_target_type), InterferenceType.displayNames());
        SimGridConfig grid = currentGridConfig();
        double cx = (grid.cols - 1) * grid.stepM() / 2.0;
        double cy = (grid.rows - 1) * grid.stepM() / 2.0;
        EditText etX = numberField(container, getString(R.string.simlab_target_x), String.format(Locale.US, "%.2f", cx));
        EditText etY = numberField(container, getString(R.string.simlab_target_y), String.format(Locale.US, "%.2f", cy));

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
            InterferenceType type = InterferenceType.values()[spType.getSelectedItemPosition()];
            double x = parseOr(etX, cx);
            double y = parseOr(etY, cy);
            interferences.add(new SimInterferenceSource(type, x, y));
            renderInterferenceList();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void buildOperatorErrorSection() {
        LinearLayout c = card(getString(R.string.simlab_section_operator_error));

        cbOperatorError = new CheckBox(this);
        cbOperatorError.setText(getString(R.string.simlab_operator_error_enable));
        cbOperatorError.setTextColor(0xFFFFFFFF);
        c.addView(cbOperatorError);

        label(c, getString(R.string.simlab_operator_error_severity));
        sbOperatorErrorSeverity = new SeekBar(this);
        sbOperatorErrorSeverity.setMax(100);
        sbOperatorErrorSeverity.setProgress(50);
        c.addView(sbOperatorErrorSeverity);

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.simlab_operator_error_hint));
        hint.setTextColor(0xFF888888);
        hint.setTextSize(11);
        hint.setPadding(0, dp(6), 0, 0);
        c.addView(hint);
    }

    private void buildCalibrationSection() {
        LinearLayout c = card(getString(R.string.simlab_section_calibration));

        cbReferenceFirstColumn = new CheckBox(this);
        cbReferenceFirstColumn.setText(getString(R.string.simlab_calibration_reference_column));
        cbReferenceFirstColumn.setTextColor(0xFFFFFFFF);
        c.addView(cbReferenceFirstColumn);

        cbBalanceDualSensors = new CheckBox(this);
        cbBalanceDualSensors.setText(getString(R.string.simlab_calibration_balance_dual));
        cbBalanceDualSensors.setTextColor(0xFFFFFFFF);
        c.addView(cbBalanceDualSensors);
    }

    private void buildSensorSection() {
        LinearLayout c = card(getString(R.string.simlab_section_sensor));
        spSensorMode = spinner(c, getString(R.string.simlab_sensor_mode), SensorMode.displayNames());
        etHeight = numberField(c, getString(R.string.simlab_sensor_height), "10");
        etSpacing = numberField(c, getString(R.string.simlab_sensor_spacing), "50");
    }

    private void buildGridSection() {
        LinearLayout c = card(getString(R.string.simlab_section_grid));
        etCols = numberField(c, getString(R.string.simlab_grid_cols), "11");
        chipRow(c, new String[]{"5", "9", "11", "13", "20"}, new String[]{"5", "9", "11", "13", "20"}, etCols);
        etRows = numberField(c, getString(R.string.simlab_grid_rows), "11");
        chipRow(c, new String[]{"5", "9", "11", "13", "20"}, new String[]{"5", "9", "11", "13", "20"}, etRows);
        etStep = numberField(c, getString(R.string.simlab_grid_step), "30");
        chipRow(c, new String[]{"10", "20", "30", "50"}, new String[]{"10", "20", "30", "50"}, etStep);
        spPattern = spinner(c, getString(R.string.simlab_grid_pattern), ScanPattern.displayNames());
    }

    private void buildPresetSection() {
        LinearLayout c = card(getString(R.string.simlab_section_preset));
        spPreset = spinner(c, getString(R.string.simlab_preset_pick), SimScenarioPresets.Preset.displayNames());

        cbExamMode = new CheckBox(this);
        cbExamMode.setText(getString(R.string.simlab_exam_mode));
        cbExamMode.setTextColor(0xFFFFD700);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbLp.topMargin = dp(8);
        cbExamMode.setLayoutParams(cbLp);
        c.addView(cbExamMode);

        TextView btnLoad = new TextView(this);
        btnLoad.setText(getString(R.string.simlab_preset_load));
        btnLoad.setTextColor(0xFFFFFFFF);
        btnLoad.setGravity(Gravity.CENTER);
        btnLoad.setBackgroundResource(R.drawable.btn_card);
        btnLoad.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        btnLoad.setLayoutParams(lp);
        btnLoad.setOnClickListener(v -> loadPreset());
        c.addView(btnLoad);
    }

    private void buildTargetSection() {
        LinearLayout c = card(getString(R.string.simlab_section_targets));
        targetListContainer = new LinearLayout(this);
        targetListContainer.setOrientation(LinearLayout.VERTICAL);
        c.addView(targetListContainer);

        TextView btnAdd = new TextView(this);
        btnAdd.setText(getString(R.string.simlab_target_add));
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setBackgroundResource(R.drawable.btn_primary);
        btnAdd.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        btnAdd.setLayoutParams(lp);
        btnAdd.setOnClickListener(v -> {
            if (cbExamMode.isChecked() && !targets.isEmpty() && !examAnswerRevealed) {
                Toast.makeText(this, getString(R.string.simlab_exam_locked_add), Toast.LENGTH_SHORT).show();
                return;
            }
            showAddTargetDialog();
        });
        c.addView(btnAdd);

        renderTargetList();
    }

    private void renderTargetList() {
        targetListContainer.removeAllViews();
        if (targets.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.simlab_target_empty));
            empty.setTextColor(0xFF888888);
            empty.setTextSize(13);
            targetListContainer.addView(empty);
            return;
        }
        if (cbExamMode.isChecked() && !examAnswerRevealed) {
            TextView locked = new TextView(this);
            locked.setText(getString(R.string.simlab_exam_locked, targets.size()));
            locked.setTextColor(0xFFFFD700);
            locked.setTextSize(13);
            locked.setPadding(0, dp(4), 0, dp(8));
            targetListContainer.addView(locked);

            TextView btnReveal = new TextView(this);
            btnReveal.setText(getString(R.string.simlab_exam_reveal));
            btnReveal.setTextColor(0xFFFFFFFF);
            btnReveal.setGravity(Gravity.CENTER);
            btnReveal.setBackgroundResource(R.drawable.btn_card);
            btnReveal.setPadding(0, dp(10), 0, dp(10));
            btnReveal.setOnClickListener(v -> {
                examAnswerRevealed = true;
                renderTargetList();
            });
            targetListContainer.addView(btnReveal);
            return;
        }
        for (int i = 0; i < targets.size(); i++) {
            SimTarget t = targets.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView tv = new TextView(this);
            tv.setText(String.format(Locale.US, "%d. %s  (%.2f, %.2f)m  derinlik %.2fm  boyut %.2fm",
                    i + 1, t.label(), t.xM, t.yM, t.depthM, t.sizeM));
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(12);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tvLp);

            TextView remove = new TextView(this);
            remove.setText("✕");
            remove.setTextColor(0xFFFF4444);
            remove.setPadding(dp(12), 0, dp(4), 0);
            int idx = i;
            remove.setOnClickListener(v -> {
                targets.remove(idx);
                renderTargetList();
            });

            row.addView(tv);
            row.addView(remove);
            targetListContainer.addView(row);
        }
    }

    private void showAddTargetDialog() {
        int pad = dp(20);
        int gap = dp(10);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(getString(R.string.simlab_target_dialog_title));
        title.setTextColor(0xFF00AAFF);
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding(0, 0, 0, gap);
        container.addView(title);

        Spinner spType = spinner(container, getString(R.string.simlab_target_type), TargetType.displayNames());

        SimGridConfig grid = currentGridConfig();
        double cx = (grid.cols - 1) * grid.stepM() / 2.0;
        double cy = (grid.rows - 1) * grid.stepM() / 2.0;

        EditText etX = numberField(container, getString(R.string.simlab_target_x), String.format(Locale.US, "%.2f", cx));
        EditText etY = numberField(container, getString(R.string.simlab_target_y), String.format(Locale.US, "%.2f", cy));
        EditText etDepth = numberField(container, getString(R.string.simlab_target_depth), "1.0");
        EditText etSize = numberField(container, getString(R.string.simlab_target_size), "0.5");
        EditText etOrientation = numberField(container, getString(R.string.simlab_target_orientation), "0");

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
            TargetType type = TargetType.values()[spType.getSelectedItemPosition()];
            double x = parseOr(etX, cx);
            double y = parseOr(etY, cy);
            double depth = Math.max(0.05, parseOr(etDepth, 1.0));
            SimTarget t = new SimTarget(type, x, y, depth);
            t.sizeM = Math.max(0.05, parseOr(etSize, type.defaultSizeM));
            t.orientationDeg = parseOr(etOrientation, 0);
            targets.add(t);
            renderTargetList();
            dialog.dismiss();
        });

        dialog.show();
    }

    private double parseOr(EditText et, double fallback) {
        try {
            return Double.parseDouble(et.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void loadPreset() {
        boolean examMode = cbExamMode.isChecked();
        SimScenarioPresets.Preset[] all = SimScenarioPresets.Preset.values();
        SimScenarioPresets.Preset preset = examMode
                ? all[new java.util.Random().nextInt(all.length)]
                : all[spPreset.getSelectedItemPosition()];

        SimGridConfig grid = currentGridConfig();
        targets.clear();
        targets.addAll(SimScenarioPresets.buildTargets(preset, grid));
        spGround.setSelection(SimScenarioPresets.groundTypeFor(preset).ordinal());
        sbInterference.setProgress((int) (SimScenarioPresets.interferenceFor(preset) * 100));
        examAnswerRevealed = false;
        renderTargetList();

        if (examMode) {
            Toast.makeText(this, getString(R.string.simlab_exam_started), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.simlab_preset_loaded, preset.displayNameTr), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- config readers

    private SimGridConfig currentGridConfig() {
        SimGridConfig g = new SimGridConfig();
        g.cols = Math.max(2, (int) parseOr(etCols, 11));
        g.rows = Math.max(2, (int) parseOr(etRows, 11));
        g.stepCm = Math.max(1, parseOr(etStep, 30));
        g.pattern = ScanPattern.values()[spPattern.getSelectedItemPosition()];
        return g;
    }

    private SimSensorConfig currentSensorConfig() {
        SimSensorConfig s = new SimSensorConfig();
        s.mode = SensorMode.values()[spSensorMode.getSelectedItemPosition()];
        s.heightAboveGroundM = Math.max(0.01, parseOr(etHeight, 10) / 100.0);
        s.sensorSpacingM = Math.max(0.05, parseOr(etSpacing, 50) / 100.0);
        s.sensitivityNoise = 0.15;
        s.driftPerRow = 0.02;
        s.offset = 0;
        return s;
    }

    private SimGroundConfig currentGroundConfig() {
        SimGroundConfig g = new SimGroundConfig();
        g.groundType = GroundType.values()[spGround.getSelectedItemPosition()];
        g.interferenceLevel = sbInterference.getProgress() / 100.0;
        return g;
    }

    private OperatorErrorConfig currentOperatorErrorConfig() {
        OperatorErrorConfig e = new OperatorErrorConfig();
        e.enabled = cbOperatorError.isChecked();
        double severity = sbOperatorErrorSeverity.getProgress() / 50.0;
        e.walkingSpeedVariation *= severity;
        e.heightWobble *= severity;
        e.tiltVibration *= severity;
        e.lineDrift *= severity;
        e.missedPointRate *= severity;
        e.turnErrorRate *= severity;
        return e;
    }

    private SimCalibrationConfig currentCalibrationConfig() {
        SimCalibrationConfig c = new SimCalibrationConfig();
        c.referenceFirstColumn = cbReferenceFirstColumn.isChecked();
        c.balanceDualSensors = cbBalanceDualSensors.isChecked();
        return c;
    }

    private SimRunConfig currentRunConfig() {
        SimRunConfig config = new SimRunConfig();
        config.targets.addAll(targets);
        config.interferences.addAll(interferences);
        config.ground = currentGroundConfig();
        config.sensor = currentSensorConfig();
        config.grid = currentGridConfig();
        config.operatorError = currentOperatorErrorConfig();
        config.calibration = currentCalibrationConfig();
        config.operatorNote = etOperatorNote.getText().toString();
        return config;
    }

    // ---------------------------------------------------------------- run

    private List<ScanDataPoint> convert(List<SimDataPoint> raw) {
        List<ScanDataPoint> converted = new ArrayList<>(raw.size());
        for (SimDataPoint p : raw) {
            converted.add(new ScanDataPoint(p.gridX, p.gridY, p.dominantDepthM * 100.0, p.displayValue));
        }
        Collections.sort(converted, (a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
        return converted;
    }

    private void startSimulation() {
        SimRunConfig config = currentRunConfig();
        long seed = System.currentTimeMillis();

        List<SimDataPoint> raw = SimulationEngine.generate(config, seed, config.operatorError.enabled);
        List<ScanDataPoint> converted = convert(raw);

        String suffix = config.operatorError.enabled ? "_hatali" : "";
        String name = "SIM_" + seed + suffix;
        File file = FileManager.saveCSV(this, name, converted);
        if (file == null) {
            Toast.makeText(this, getString(R.string.simlab_save_error), Toast.LENGTH_SHORT).show();
            return;
        }

        if (config.operatorError.enabled) {
            List<SimDataPoint> cleanRaw = SimulationEngine.generate(config, seed, false);
            List<ScanDataPoint> cleanConverted = convert(cleanRaw);
            File cleanFile = FileManager.saveCSV(this, "SIM_" + seed + "_temiz", cleanConverted);
            if (cleanFile != null) {
                Toast.makeText(this, getString(R.string.simlab_clean_saved), Toast.LENGTH_LONG).show();
            }
        }

        Toast.makeText(this, getString(R.string.simlab_generated, converted.size()), Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, ScanPreviewActivity.class);
        intent.putExtra("filePath", file.getAbsolutePath());
        startActivity(intent);
    }

    private void runAnalysis() {
        if (targets.isEmpty()) {
            Toast.makeText(this, getString(R.string.simlab_target_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        SimRunConfig config = currentRunConfig();
        List<SimDataPoint> raw = SimulationEngine.generate(config, System.currentTimeMillis(), config.operatorError.enabled);

        Intent intent = new Intent(this, SimAnalysisActivity.class);
        intent.putExtra(SimAnalysisActivity.EXTRA_POINTS, new ArrayList<>(raw));
        intent.putExtra(SimAnalysisActivity.EXTRA_CONFIG, config);
        startActivity(intent);
    }

    private void runComparison() {
        if (targets.isEmpty()) {
            Toast.makeText(this, getString(R.string.simlab_target_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        SimRunConfig config = currentRunConfig();
        List<SimDataPoint> rawA = SimulationEngine.generate(config, System.currentTimeMillis(), config.operatorError.enabled);
        List<SimDataPoint> rawB = SimulationEngine.generate(config, System.currentTimeMillis() + 1, config.operatorError.enabled);

        Intent intent = new Intent(this, SimComparisonActivity.class);
        intent.putExtra(SimComparisonActivity.EXTRA_POINTS_A, new ArrayList<>(rawA));
        intent.putExtra(SimComparisonActivity.EXTRA_POINTS_B, new ArrayList<>(rawB));
        intent.putExtra(SimComparisonActivity.EXTRA_CONFIG, config);
        startActivity(intent);
    }
}
