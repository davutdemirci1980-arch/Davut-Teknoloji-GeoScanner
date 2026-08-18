package com.geoscanner.app.ui.live3d;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.ble.BLEManager;
import com.geoscanner.app.utils.LocaleHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Full-screen live 3D point-cloud view: streams sensor readings straight
 * from {@link BLEManager} into the canvas-based renderer in
 * assets/live3d.html via WebView.evaluateJavascript, walking a simulated
 * scan line (auto-advancing X, manual "new line" for Y) as points come in.
 */
public class Live3DActivity extends AppCompatActivity {
    private static final String TAG = "Live3DActivity";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Float> xData = new ArrayList<>();
    private final List<Float> yData = new ArrayList<>();
    private final List<Float> zData = new ArrayList<>();
    private final List<Float> cData = new ArrayList<>();

    private BLEManager bleManager;
    private ToneGenerator toneGenerator;
    private WebView webView;
    private TextView tvLiveValue;
    private TextView tvStatus;

    private float currentX = 0f;
    private float currentY = 0f;
    private final float stepX = 30f;
    private int lineIndex = 0;
    private int pointsInLine = 0;
    private boolean isLive = false;
    private boolean webReady = false;
    private final boolean soundEnabled = true;
    private int totalPoints = 0;
    private long lastSoundTime = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live3d);
        getWindow().setStatusBarColor(0xFF111111);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = findViewById(R.id.webViewLive);
        tvLiveValue = findViewById(R.id.tvLiveValue);
        tvStatus = findViewById(R.id.tvLiveStatus);

        initSound();
        setupWebView();
        setupButtons();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "WebView yüklendi: " + url);
                webReady = true;
                handler.postDelayed(Live3DActivity.this::setupBLE, 500L);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView hata: " + description);
            }
        });
        webView.loadUrl("file:///android_asset/live3d.html");
    }

    private void initSound() {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (curVol < maxVol / 2) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol * 3 / 4, 0);
                }
            }
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            Log.i(TAG, "ToneGenerator başarıyla oluşturuldu");
        } catch (Exception e) {
            Log.e(TAG, "ToneGenerator oluşturulamadı", e);
            try {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
                Log.i(TAG, "ToneGenerator NOTIFICATION stream ile oluşturuldu");
            } catch (Exception e2) {
                Log.e(TAG, "ToneGenerator hiçbir stream ile oluşturulamadı", e2);
                toneGenerator = null;
            }
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.setBackgroundColor(0xFF111111);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                Log.d(TAG, "JS: " + msg.message());
                return true;
            }
        });
    }

    private void setupBLE() {
        bleManager = BLEManager.getInstance();
        bleManager.setGradientListener(data -> {
            float value = data.getGradient();
            Log.d(TAG, ">>> Sinyal geldi: " + value + " isLive=" + isLive + " webReady=" + webReady);
            if (isLive) {
                runOnUiThread(() -> {
                    tvLiveValue.setText(String.format("%.1f", value));
                    tvLiveValue.setTextColor(0xFF00FF00);
                    addLivePoint(value);
                });
            }
        });
        bleManager.setRawDataListener(raw -> Log.d(TAG, "Raw: " + raw));

        if (bleManager.isConnected()) {
            tvStatus.setText(getString(R.string.live_scanning));
            tvStatus.setTextColor(0xFF00CCFF);
            isLive = true;
            bleManager.startLiveMode();
            Log.i(TAG, "Cihaz bağlı - Live mode başlatıldı. Tip: " + bleManager.getDetectedDeviceType() + " Mode: " + bleManager.getConnectionMode());
            playSound(28, 150);
            startDataWatchdog();
            return;
        }
        tvStatus.setText(getString(R.string.live_demo_mode));
        tvStatus.setTextColor(0xFFFFA500);
        Log.i(TAG, "Cihaz bağlı değil - Demo modu");
        startDemoMode();
    }

    /** Detects a stalled feed (no bytes for 2s) and force-emits the device's last known value once. */
    private void startDataWatchdog() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || !isLive || bleManager == null || !bleManager.isConnected()) return;

                long lastTime = bleManager.getLastDataTime();
                long now = System.currentTimeMillis();
                if (lastTime > 0 && now - lastTime > 2000) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Sinyal bekleniyor... (" + bleManager.getTotalDataReceived() + " byte alındı)");
                        tvStatus.setTextColor(0xFFFFA500);
                    });
                }

                float lastVal = bleManager.getLastGradientValue();
                if (lastVal != 0f && totalPoints == 0 && lastTime > 0) {
                    Log.w(TAG, "Watchdog: İlk değer zorla ekleniyor: " + lastVal);
                    runOnUiThread(() -> {
                        tvLiveValue.setText(String.format("%.1f", lastVal));
                        addLivePoint(lastVal);
                    });
                }

                handler.postDelayed(this, 2000L);
            }
        }, 3000L);
    }

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnStartLive).setOnClickListener(v -> {
            isLive = true;
            tvStatus.setText(getString(R.string.live_scanning));
            tvStatus.setTextColor(0xFF00CCFF);
            if (bleManager != null && bleManager.isConnected()) {
                bleManager.startLiveMode();
                playSound(28, 150);
                startDataWatchdog();
            } else {
                startDemoMode();
            }
        });
        findViewById(R.id.btnStopLive).setOnClickListener(v -> {
            isLive = false;
            tvStatus.setText(getString(R.string.live_stopped));
            tvStatus.setTextColor(0xFFFF4444);
            if (bleManager != null && bleManager.isConnected()) bleManager.stopLiveMode();
            playSound(26, 100);
        });
        findViewById(R.id.btnNewLine).setOnClickListener(v -> {
            lineIndex++;
            pointsInLine = 0;
            currentX = 0f;
            currentY = lineIndex * stepX;
            tvStatus.setText(String.format(getString(R.string.live_new_line_num), lineIndex));
            playSound(25, 100);
        });
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            xData.clear();
            yData.clear();
            zData.clear();
            cData.clear();
            lineIndex = 0;
            pointsInLine = 0;
            currentX = 0f;
            currentY = 0f;
            totalPoints = 0;
            if (webReady) webView.evaluateJavascript("clearAll();", null);
            tvStatus.setText("Temizlendi");
            tvLiveValue.setText("0.0");
            playSound(41, 100);
        });
    }

    private void addLivePoint(float value) {
        xData.add(currentX);
        yData.add(currentY);
        zData.add(0f);
        cData.add(value);
        totalPoints++;
        currentX += stepX;
        pointsInLine++;

        long now = System.currentTimeMillis();
        if (soundEnabled && toneGenerator != null && now - lastSoundTime > 80) {
            lastSoundTime = now;
            float absVal = Math.abs(value);
            try {
                if (absVal > 3000f) toneGenerator.startTone(12, 150);
                else if (absVal > 2000f) toneGenerator.startTone(15, 120);
                else if (absVal > 1000f) toneGenerator.startTone(9, 80);
                else if (absVal > 500f) toneGenerator.startTone(5, 60);
                else if (absVal > 100f) toneGenerator.startTone(1, 40);
                else if (absVal > 10f) toneGenerator.startTone(44, 25);
                else toneGenerator.startTone(44, 15);
                Log.d(TAG, "Ses çalındı: absVal=" + absVal);
            } catch (Exception e) {
                Log.e(TAG, "Ses hatası", e);
            }
        }

        if (webReady) {
            String js = String.format(Locale.US, "addPoint(%.1f, %.1f, 0, %.2f);", currentX - stepX, currentY, value);
            Log.d(TAG, "JS çağrısı: " + js);
            webView.evaluateJavascript(js, result -> Log.d(TAG, "JS sonuç: " + result));
        } else {
            Log.w(TAG, "WebView hazır değil! webReady=" + webReady);
        }

        tvStatus.setText(String.format("Hat: %d | Nokta: %d | Toplam: %d", lineIndex, pointsInLine, totalPoints));
    }

    private void playSound(int toneType, int durationMs) {
        if (toneGenerator != null && soundEnabled) {
            try {
                toneGenerator.startTone(toneType, durationMs);
                Log.d(TAG, "playSound: type=" + toneType + " dur=" + durationMs);
            } catch (Exception e) {
                Log.e(TAG, "playSound hatası", e);
            }
        } else {
            Log.w(TAG, "playSound: toneGenerator=" + (toneGenerator != null) + " soundEnabled=" + soundEnabled);
        }
    }

    /** Synthetic double-bump surface + noise, used when no device is connected. */
    private void startDemoMode() {
        isLive = true;
        Random random = new Random(42L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || !isLive) return;

                double cx = currentX / 100.0;
                double cy = currentY / 100.0;
                float value = (float) (Math.sin(0.5 * cx) * 50.0 * Math.cos(0.3 * cy)
                        + Math.exp(-((cx - 3.0) * (cx - 3.0) + (cy - 2.0) * (cy - 2.0)) / 2.0) * 30.0
                        + random.nextFloat() * 10f);
                tvLiveValue.setText(String.format("%.1f", value));
                addLivePoint(value);

                if (pointsInLine >= 10) {
                    lineIndex++;
                    pointsInLine = 0;
                    currentX = 0f;
                    currentY = lineIndex * stepX;
                }

                if (lineIndex < 10) {
                    handler.postDelayed(this, 300L);
                } else {
                    isLive = false;
                    tvStatus.setText(String.format(getString(R.string.live_demo_complete), totalPoints));
                }
            }
        }, 1000L);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isLive = false;
        handler.removeCallbacksAndMessages(null);
        if (bleManager != null && bleManager.isConnected()) bleManager.stopLiveMode();
        if (toneGenerator != null) {
            try {
                toneGenerator.release();
            } catch (Exception ignored) {
            }
            toneGenerator = null;
        }
    }
}
