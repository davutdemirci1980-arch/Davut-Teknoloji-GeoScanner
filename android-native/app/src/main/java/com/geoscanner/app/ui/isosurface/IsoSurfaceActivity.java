package com.geoscanner.app.ui.isosurface;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.data.FileManager;
import com.geoscanner.app.data.ScanDataPoint;
import com.geoscanner.app.utils.LocaleHelper;
import com.geoscanner.app.utils.SignalProcessor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Renders a completed scan as a Plotly.js surface / isosurface / point cloud in
 * assets/plotly3d.html. Surface + volume JSON payloads are built off the UI
 * thread and cached per interpolation mode, since regenerating them on every
 * mode switch is the dominant cost for larger grids.
 */
public class IsoSurfaceActivity extends AppCompatActivity {
    private static final String TAG = "IsoSurfaceActivity";
    private static final int NZ = 10;

    private final String[] interpKeys = {"nearest", "linear", "cubic", "bspline", "sinc", "gaussian"};
    private final int[] interpModes = {0, 1, 2, 3, 4, 5};
    private final int[] interpColors = {
            Color.parseColor("#FF4444"), Color.parseColor("#44FF44"), Color.parseColor("#44AAFF"),
            Color.parseColor("#FF44FF"), Color.parseColor("#FFAA00"), Color.parseColor("#FFD700"),
    };

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private Button btnSurface, btnIsoSurface, btnPointCloud, btnInterp;
    private List<ScanDataPoint> dataPoints;
    private String currentMode = "surface";
    private int currentInterpMode = 5;
    private boolean webReady = false;

    private int cachedInterpMode = -1;
    private String cachedSurfaceJSON;
    private String cachedVolumeJSON;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_isosurface);
        getWindow().setStatusBarColor(0xFF111111);

        webView = findViewById(R.id.webView3D);
        setupWebView();

        String filePath = getIntent().getStringExtra("filePath");
        Log.i(TAG, "filePath: " + filePath);
        if (filePath != null) {
            try {
                dataPoints = FileManager.readAuto(new File(filePath));
                Log.i(TAG, dataPoints != null ? "Veri yüklendi: " + dataPoints.size() + " nokta" : "FileManager.readAuto null döndü!");
            } catch (Exception e) {
                Log.e(TAG, "Veri okuma hatası: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "filePath null!");
        }

        setupButtons();
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "WebView yüklendi: " + url);
                webReady = true;
                handler.postDelayed(() -> {
                    if (dataPoints != null && !dataPoints.isEmpty()) {
                        Log.i(TAG, "loadCurrentView çağrılıyor, mode=" + currentMode + ", points=" + dataPoints.size());
                        loadCurrentView();
                    } else {
                        Log.w(TAG, "dataPoints boş veya null, veri yüklenemedi");
                    }
                }, 300L);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView hata: code=" + errorCode + " desc=" + description + " url=" + failingUrl);
            }
        });
        webView.loadUrl("file:///android_asset/plotly3d.html");
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setBackgroundColor(0xFF111111);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                Log.d(TAG, "JS[" + msg.messageLevel() + "]: " + msg.message() + " (line " + msg.lineNumber() + ")");
                return true;
            }
        });
    }

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnSurface = findViewById(R.id.btnSurface);
        btnIsoSurface = findViewById(R.id.btnIsoSurface);
        btnPointCloud = findViewById(R.id.btnPointCloud);
        btnInterp = findViewById(R.id.btnInterp);

        btnSurface.setText(getString(R.string.iso_surface));
        btnIsoSurface.setText(getString(R.string.iso_isosurface));
        btnPointCloud.setText(getString(R.string.iso_points));
        updateInterpButtonText();

        btnSurface.setOnClickListener(v -> {
            currentMode = "surface";
            if (webReady) loadCurrentView();
        });
        btnIsoSurface.setOnClickListener(v -> {
            currentMode = "isosurface";
            if (webReady) loadCurrentView();
        });
        btnPointCloud.setOnClickListener(v -> {
            currentMode = "pointcloud";
            if (webReady) loadCurrentView();
        });
        btnInterp.setOnClickListener(v -> showInterpDialog());
    }

    private void showInterpDialog() {
        String[] labels = {
                getString(R.string.iso_interp_nearest), getString(R.string.iso_interp_linear),
                getString(R.string.iso_interp_cubic), getString(R.string.iso_interp_bspline),
                getString(R.string.iso_interp_sinc), getString(R.string.iso_interp_gaussian),
        };
        int checkedItem = 5;
        for (int i = 0; i < interpModes.length; i++) {
            if (interpModes[i] == currentInterpMode) {
                checkedItem = i;
                break;
            }
        }
        int selected = checkedItem;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setTextColor(interpColors[position]);
                    tv.setTextSize(18f);
                    tv.setTypeface(null, android.graphics.Typeface.BOLD);
                    tv.setPadding(40, 24, 40, 24);
                    tv.setBackgroundColor(position == selected ? Color.parseColor("#33FFD700") : Color.TRANSPARENT);
                }
                return view;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_GeoScanner_Dialog);
        builder.setTitle(getString(R.string.iso_interp_label));
        builder.setSingleChoiceItems(adapter, checkedItem, (dialog, which) -> {
            currentInterpMode = interpModes[which];
            invalidateCache();
            updateInterpButtonText();
            dialog.dismiss();
            if (webReady) loadCurrentView();
        });
        builder.setNegativeButton(getString(R.string.records_cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();

        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId > 0) {
            TextView titleView = dialog.findViewById(titleId);
            if (titleView != null) {
                titleView.setTextColor(Color.WHITE);
                titleView.setTextSize(20f);
                titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }
        Button negBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negBtn != null) {
            negBtn.setTextColor(Color.parseColor("#FF4444"));
            negBtn.setTextSize(16f);
        }
    }

    private void updateInterpButtonText() {
        String[] labels = {
                getString(R.string.iso_interp_nearest), getString(R.string.iso_interp_linear),
                getString(R.string.iso_interp_cubic), getString(R.string.iso_interp_bspline),
                getString(R.string.iso_interp_sinc), getString(R.string.iso_interp_gaussian),
        };
        for (int i = 0; i < interpModes.length; i++) {
            if (interpModes[i] == currentInterpMode) {
                btnInterp.setText(labels[i]);
                btnInterp.setTextColor(interpColors[i]);
                return;
            }
        }
    }

    private void invalidateCache() {
        cachedInterpMode = -1;
        cachedSurfaceJSON = null;
        cachedVolumeJSON = null;
    }

    private void ensureCache() {
        if (cachedInterpMode == currentInterpMode && cachedSurfaceJSON != null) return;
        try {
            long t0 = System.currentTimeMillis();
            cachedSurfaceJSON = buildSurfaceJSON().toString();
            cachedVolumeJSON = buildVolumeJSON().toString();
            cachedInterpMode = currentInterpMode;
            Log.i(TAG, "Cache oluşturuldu: " + (System.currentTimeMillis() - t0) + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Cache oluşturma hatası: " + e.getMessage(), e);
        }
    }

    private void loadCurrentView() {
        if (dataPoints == null || dataPoints.isEmpty()) {
            Log.w(TAG, "loadCurrentView: dataPoints boş!");
            return;
        }
        if (!webReady) {
            Log.w(TAG, "loadCurrentView: webReady=false, erteleniyor");
            return;
        }
        Log.i(TAG, "loadCurrentView: mode=" + currentMode + " points=" + dataPoints.size());
        new Thread(() -> {
            ensureCache();
            handler.post(() -> {
                switch (currentMode) {
                    case "surface":
                        sendToWebView("render3D", cachedSurfaceJSON);
                        break;
                    case "isosurface":
                        sendToWebView("renderIsoSurface", cachedVolumeJSON);
                        break;
                    case "pointcloud":
                        sendToWebView("renderPointCloud", cachedVolumeJSON);
                        break;
                    default:
                        break;
                }
            });
        }).start();
    }

    private void sendToWebView(String funcName, String jsonStr) {
        if (jsonStr == null) {
            Log.e(TAG, funcName + ": JSON null!");
            return;
        }
        try {
            String escaped = jsonStr.replace("\\", "\\\\").replace("'", "\\'");
            String js = funcName + "('" + escaped + "');";
            Log.d(TAG, funcName + " JS çağrısı: " + js.length() + " byte");
            webView.evaluateJavascript(js, result -> Log.i(TAG, funcName + " sonuç: " + result));
        } catch (Exception e) {
            Log.e(TAG, funcName + " hatası: " + e.getMessage(), e);
            Toast.makeText(this, String.format(getString(R.string.iso_error), e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /** Upsampled 2D surface (X/Y grid + interpolated Z heights) for the "Surface" view. */
    private JSONObject buildSurfaceJSON() throws Exception {
        TreeSet<Integer> xSet = new TreeSet<>();
        TreeSet<Integer> ySet = new TreeSet<>();
        for (ScanDataPoint p : dataPoints) {
            xSet.add(p.x);
            ySet.add(p.y);
        }
        List<Integer> xList = new ArrayList<>(xSet);
        List<Integer> yList = new ArrayList<>(ySet);
        int nx = xList.size();
        int ny = yList.size();
        Log.d(TAG, "buildSurfaceJSON: nx=" + nx + " ny=" + ny);

        if (nx < 2 || ny < 2) {
            Log.w(TAG, "Grid çok küçük (nx=" + nx + ", ny=" + ny + "), PointCloud'a düşüyor");
            return buildFallbackPointCloudJSON();
        }

        double[][] grid = new double[ny][nx];
        for (ScanDataPoint p : dataPoints) {
            int xi = xList.indexOf(p.x);
            int yi = yList.indexOf(p.y);
            if (xi >= 0 && yi >= 0) grid[yi][xi] = p.c;
        }

        int upFactor = Math.max(1, Math.min(4, 20 / Math.max(nx, ny) + 1));
        int outNx = nx * upFactor;
        int outNy = ny * upFactor;
        Log.d(TAG, "Interpolation: upFactor=" + upFactor + " outNx=" + outNx + " outNy=" + outNy);
        double[][] upGrid = SignalProcessor.interpolateGrid2D(grid, outNy, outNx, currentInterpMode);

        JSONArray uxArr = new JSONArray();
        for (int i = 0; i < outNx; i++) {
            double frac = outNx > 1 ? (double) i / (outNx - 1) : 0.0;
            uxArr.put(xList.get(0) + (xList.get(nx - 1) - xList.get(0)) * frac);
        }
        JSONArray uyArr = new JSONArray();
        for (int j = 0; j < outNy; j++) {
            double frac = outNy > 1 ? (double) j / (outNy - 1) : 0.0;
            uyArr.put(yList.get(0) + (yList.get(ny - 1) - yList.get(0)) * frac);
        }
        JSONArray zGrid = new JSONArray();
        for (int j = 0; j < outNy; j++) {
            JSONArray row = new JSONArray();
            for (int i = 0; i < outNx; i++) row.put(upGrid[j][i]);
            zGrid.put(row);
        }

        JSONObject json = new JSONObject();
        json.put("ux", uxArr);
        json.put("uy", uyArr);
        json.put("zGrid", zGrid);
        json.put("interpMode", interpKeys[currentInterpMode]);
        json.put("useSurfaceGrid", true);
        Log.d(TAG, "buildSurfaceJSON tamamlandı: ux=" + uxArr.length() + " uy=" + uyArr.length()
                + " zGrid=" + zGrid.length() + "x" + ((JSONArray) zGrid.get(0)).length());
        return json;
    }

    private JSONObject buildFallbackPointCloudJSON() throws Exception {
        JSONArray xArr = new JSONArray();
        JSONArray yArr = new JSONArray();
        JSONArray zArr = new JSONArray();
        JSONArray cArr = new JSONArray();
        for (ScanDataPoint p : dataPoints) {
            xArr.put(p.x);
            yArr.put(p.y);
            zArr.put(-p.z);
            cArr.put(p.c);
        }
        JSONObject json = new JSONObject();
        json.put("x", xArr);
        json.put("y", yArr);
        json.put("z", zArr);
        json.put("c", cArr);
        json.put("useSurfaceGrid", false);
        return json;
    }

    /** Flattened (k,j,i) -> (x,y,z,c) point list of the extruded 3D volume, for IsoSurface/PointCloud views. */
    private JSONObject buildVolumeJSON() throws Exception {
        TreeSet<Integer> xSet = new TreeSet<>();
        TreeSet<Integer> ySet = new TreeSet<>();
        for (ScanDataPoint p : dataPoints) {
            xSet.add(p.x);
            ySet.add(p.y);
        }
        List<Integer> xList = new ArrayList<>(xSet);
        List<Integer> yList = new ArrayList<>(ySet);
        int nx = xList.size();
        int ny = yList.size();
        Log.d(TAG, "buildVolumeJSON: nx=" + nx + " ny=" + ny + " nz=" + NZ);

        if (nx < 2 || ny < 2) {
            Log.w(TAG, "Grid çok küçük, fallback PointCloud kullanılıyor");
            return buildFallbackPointCloudJSON();
        }

        double[][] anomalyGrid = new double[ny][nx];
        double[][] depthGrid = new double[ny][nx];
        for (ScanDataPoint p : dataPoints) {
            int xi = xList.indexOf(p.x);
            int yi = yList.indexOf(p.y);
            if (xi >= 0 && yi >= 0) {
                anomalyGrid[yi][xi] = p.c;
                depthGrid[yi][xi] = p.z;
            }
        }

        double maxDepth = 100.0;
        for (ScanDataPoint p : dataPoints) maxDepth = Math.max(maxDepth, Math.max(p.z, 50.0));

        int upsample = Math.max(1, Math.min(3, 15 / Math.max(nx, ny) + 1));
        double[][][] volume = SignalProcessor.generateVolume3D(anomalyGrid, depthGrid, nx, ny, NZ, maxDepth, currentInterpMode, upsample);
        int outNx = nx * upsample;
        int outNy = ny * upsample;
        double dz = maxDepth / (NZ - 1);

        JSONArray xArr = new JSONArray();
        JSONArray yArr = new JSONArray();
        JSONArray zArr = new JSONArray();
        JSONArray cArr = new JSONArray();
        double cMin = Double.MAX_VALUE, cMax = -Double.MAX_VALUE;

        for (int k = 0; k < NZ; k++) {
            double depth = k * dz;
            for (int j = 0; j < outNy; j++) {
                double yVal = outNy > 1
                        ? yList.get(0) + ((double) j / (outNy - 1)) * (yList.get(ny - 1) - yList.get(0))
                        : yList.get(0);
                for (int i = 0; i < outNx; i++) {
                    double xVal = outNx > 1
                            ? xList.get(0) + ((double) i / (outNx - 1)) * (xList.get(nx - 1) - xList.get(0))
                            : xList.get(0);
                    double value = volume[k][j][i];
                    xArr.put(xVal);
                    yArr.put(yVal);
                    zArr.put(-depth);
                    cArr.put(value);
                    cMin = Math.min(cMin, value);
                    cMax = Math.max(cMax, value);
                }
            }
        }

        Log.d(TAG, "buildVolumeJSON tamamlandı: " + xArr.length() + " nokta, cMin=" + cMin + " cMax=" + cMax);
        JSONObject json = new JSONObject();
        json.put("x", xArr);
        json.put("y", yArr);
        json.put("z", zArr);
        json.put("c", cArr);
        json.put("isomin", cMin + (cMax - cMin) * 0.2);
        json.put("isomax", cMax);
        json.put("interpMode", interpKeys[currentInterpMode]);
        return json;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
