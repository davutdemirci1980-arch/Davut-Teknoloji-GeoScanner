package com.geoscanner.app.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.ui.main.MainActivity;
import com.geoscanner.app.utils.AiSettings;
import com.geoscanner.app.utils.LocaleHelper;

public class SettingsActivity extends AppCompatActivity {
    private TextView tvCurrentLang;
    private TextView tvAiKeyStatus;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getWindow().setStatusBarColor(0xFF111111);

        tvCurrentLang = findViewById(R.id.tvCurrentLang);
        tvAiKeyStatus = findViewById(R.id.tvAiKeyStatus);
        updateCurrentLanguageLabel();
        updateAiKeyStatusLabel();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLanguage).setOnClickListener(v -> showLanguageDialog());
        findViewById(R.id.btnAiApiKey).setOnClickListener(v -> showAiApiKeyDialog());
    }

    private void updateAiKeyStatusLabel() {
        boolean hasKey = AiSettings.hasApiKey(this);
        tvAiKeyStatus.setText(hasKey ? getString(R.string.settings_ai_key_set) : getString(R.string.settings_ai_key_not_set));
    }

    private void showAiApiKeyDialog() {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        int gap = (int) (12 * getResources().getDisplayMetrics().density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(getString(R.string.settings_ai_key_dialog_title));
        title.setTextColor(0xFF00AAFF);
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding(0, 0, 0, gap);
        container.addView(title);

        TextView message = new TextView(this);
        message.setText(getString(R.string.settings_ai_key_dialog_msg));
        message.setTextColor(0xFFCCCCCC);
        message.setPadding(0, 0, 0, gap);
        container.addView(message);

        EditText input = new EditText(this);
        input.setText(AiSettings.getApiKey(this));
        input.setHint(R.string.settings_ai_key_hint);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);
        container.addView(input);

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
        btnSave.setText(getString(android.R.string.ok));
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
            AiSettings.setApiKey(this, input.getText().toString());
            updateAiKeyStatusLabel();
            Toast.makeText(this, getString(R.string.settings_ai_key_saved), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateCurrentLanguageLabel() {
        String code = LocaleHelper.getSavedLanguage(this);
        String displayName = LocaleHelper.getLanguageDisplayName(code);
        tvCurrentLang.setText(getString(R.string.language_current, displayName));
    }

    private void showLanguageDialog() {
        String[] langNames = {"English", "Türkçe", "Français"};
        String[] langCodes = {"en", "tr", "fr"};
        String current = LocaleHelper.getSavedLanguage(this);
        int checkedItem = 0;
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(current)) {
                checkedItem = i;
                break;
            }
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.language_select))
                .setSingleChoiceItems(langNames, checkedItem, (dialog, which) -> {
                    String selectedCode = langCodes[which];
                    String currentCode = LocaleHelper.getSavedLanguage(this);
                    if (!selectedCode.equals(currentCode)) {
                        LocaleHelper.persistLanguage(this, selectedCode);
                        Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finishAffinity();
                        }, 500L);
                    } else {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
