package com.geoscanner.app.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.ui.main.MainActivity;
import com.geoscanner.app.utils.LocaleHelper;

public class SettingsActivity extends AppCompatActivity {
    private TextView tvCurrentLang;

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
        updateCurrentLanguageLabel();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLanguage).setOnClickListener(v -> showLanguageDialog());
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
