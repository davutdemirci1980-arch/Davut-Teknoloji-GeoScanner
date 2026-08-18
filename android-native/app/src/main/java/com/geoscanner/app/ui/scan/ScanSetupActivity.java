package com.geoscanner.app.ui.scan;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.utils.LocaleHelper;

public class ScanSetupActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_setup);
        getWindow().setStatusBarColor(0xFF111111);
        getWindow().setNavigationBarColor(0xFF111111);

        EditText etName = findViewById(R.id.etScanName);
        SeekBar sbWidth = findViewById(R.id.sbGridWidth);
        SeekBar sbHeight = findViewById(R.id.sbGridHeight);
        SeekBar sbStep = findViewById(R.id.sbStepSize);
        TextView tvWidth = findViewById(R.id.tvGridWidth);
        TextView tvHeight = findViewById(R.id.tvGridHeight);
        TextView tvStep = findViewById(R.id.tvStepSize);

        etName.setText("Scan_" + (System.currentTimeMillis() % 10000));
        sbWidth.setMax(29);
        sbWidth.setProgress(5);
        sbHeight.setMax(29);
        sbHeight.setProgress(5);
        sbStep.setMax(90);
        sbStep.setProgress(20);
        updateLabels(tvWidth, tvHeight, tvStep, sbWidth, sbHeight, sbStep);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updateLabels(tvWidth, tvHeight, tvStep, sbWidth, sbHeight, sbStep);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        };
        sbWidth.setOnSeekBarChangeListener(listener);
        sbHeight.setOnSeekBarChangeListener(listener);
        sbStep.setOnSeekBarChangeListener(listener);

        findViewById(R.id.btnStartScan).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) name = "Scan_" + System.currentTimeMillis();
            int width = sbWidth.getProgress() + 1;
            int height = sbHeight.getProgress() + 1;
            int step = sbStep.getProgress() + 10;

            Intent intent = new Intent(this, ScanActiveActivity.class);
            intent.putExtra("scanName", name);
            intent.putExtra("gridWidth", width);
            intent.putExtra("gridHeight", height);
            intent.putExtra("stepSize", step);
            startActivity(intent);
            finish();
        });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void updateLabels(TextView tw, TextView th, TextView ts, SeekBar sw, SeekBar sh, SeekBar ss) {
        tw.setText(String.format(getString(R.string.setup_width), sw.getProgress() + 1));
        th.setText(String.format(getString(R.string.setup_height), sh.getProgress() + 1));
        ts.setText(String.format(getString(R.string.setup_step), ss.getProgress() + 10));
    }
}
