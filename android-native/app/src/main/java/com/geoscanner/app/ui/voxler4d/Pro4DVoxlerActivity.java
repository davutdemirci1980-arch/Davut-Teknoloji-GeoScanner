package com.geoscanner.app.ui.voxler4d;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.data.FileManager;
import com.geoscanner.app.data.ScanDataPoint;
import com.geoscanner.app.utils.LocaleHelper;
import com.geoscanner.app.utils.SignalProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.TreeSet;

public class Pro4DVoxlerActivity extends AppCompatActivity {

    private interface SliderCallback {
        void onChanged(int value);
    }

    private FrameLayout mainLayout;
    private WebView webView;
    private FrameLayout bottomBarContainer;
    private LinearLayout mainBottomBar;
    private LinearLayout layersBottomBar;
    private LinearLayout volumePanel;
    private LinearLayout heightFieldPanel;
    private LinearLayout contoursPanel;
    private LinearLayout filterSidePanel;
    private LinearLayout currentPanel;

    private int cols;
    private int rows;
    private int stepSize;
    private float[] gridData;
    private float[] gridDataZ;
    private float[] originalGridData = null;

    private String[] filterNames;
    private final String[] colorScaleNames = {"rainbow", "jet", "hot", "cool", "viridis", "plasma", "inferno", "magma", "spring", "winter"};
    private final String[] filterCodes = {"none", "gauss", "median", "sharpen", "edge", "rank", "histeq", "bandpass", "highpass", "lowpass", "gradient", "laplacian", "threshold", "normalize", "detrend"};

    private boolean vtkReady = false;
    private boolean ttsReady = false;
    private boolean showIso = true;
    private boolean showVolume = false;
    private boolean showHeightField = false;
    private boolean showContours = false;
    private boolean showScatter = false;
    private boolean showBox = true;
    private boolean showAxes = true;
    private boolean autoRotating = false;

    private final boolean[] layerEnabled = {true, true, true, true};
    private float[] layerIsoValues = {0.25f, 0.5f, 0.75f, 0.95f};
    private String[] layerFilters = {"none", "none", "none", "none"};
    private int currentPower = 5;

    private TextToSpeech tts;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale trLocale = new Locale("tr", "TR");
                int result = tts.setLanguage(trLocale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.ENGLISH);
                }
                tts.setSpeechRate(0.9f);
                tts.setPitch(1.0f);
                ttsReady = true;
            }
        });

        filterNames = new String[]{"None", "Mean Remove", "Median", "Sharpen", "Edge Detect", "Rank", "Histogram EQ",
                "Band Pass", "High Pass", "Low Pass", "Gradient", "Laplacian", "Threshold", "Normalize", "Detrend"};

        cols = getIntent().getIntExtra("cols", 0);
        rows = getIntent().getIntExtra("rows", 0);
        stepSize = getIntent().getIntExtra("stepSize", 30);
        gridData = getIntent().getFloatArrayExtra("gridData");
        gridDataZ = getIntent().getFloatArrayExtra("gridDataZ");
        String filePath = getIntent().getStringExtra("filePath");

        if (gridData == null && filePath != null) {
            loadFromFile(filePath);
        }
        if (gridData == null) {
            File scanDir = FileManager.getScanDirectory(this);
            if (scanDir.exists()) {
                File[] files = scanDir.listFiles((dir, name) ->
                        name.endsWith(".csv") || name.endsWith(".7esx") || name.endsWith(".vtk") || name.endsWith(".grd"));
                if (files != null && files.length > 0) {
                    File latest = files[0];
                    for (File f : files) {
                        if (f.lastModified() > latest.lastModified()) {
                            latest = f;
                        }
                    }
                    loadFromFile(latest.getAbsolutePath());
                }
            }
        }
        if (gridData == null) {
            generateDemoData();
        }

        buildUI();
        setupWebView();
    }

    private void buildUI() {
        mainLayout = new FrameLayout(this);
        mainLayout.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        mainLayout.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(16, 40, 16, 8);
        topBar.setBackgroundColor(Color.parseColor("#80000000"));

        TextView backBtn = new TextView(this);
        backBtn.setText("◀");
        backBtn.setTextSize(22.0f);
        backBtn.setTextColor(Color.WHITE);
        backBtn.setPadding(16, 8, 24, 8);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn);

        TextView title = new TextView(this);
        title.setText(getString(R.string.voxler_4d_title));
        title.setTextSize(18.0f);
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        topBar.addView(title);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, -2);
        topParams.gravity = Gravity.TOP;
        mainLayout.addView(topBar, topParams);

        bottomBarContainer = new FrameLayout(this);
        buildMainBottomBar();
        buildLayersBottomBar();
        bottomBarContainer.addView(mainBottomBar);
        bottomBarContainer.addView(layersBottomBar);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, -2);
        bottomParams.gravity = Gravity.BOTTOM;
        mainLayout.addView(bottomBarContainer, bottomParams);

        buildVolumePanel();
        buildHeightFieldPanel();
        buildContoursPanel();
        buildFilterSidePanel();

        setContentView(mainLayout);
    }

    private void setExclusiveRenderMode(String mode) {
        showScatter = mode.equals("scatter");
        showIso = mode.equals("iso");
        showVolume = mode.equals("volume");
        showHeightField = mode.equals("heightfield");
        showContours = mode.equals("contours");
        evalJS("setRenderMode('" + mode + "')");
    }

    private void buildMainBottomBar() {
        mainBottomBar = new LinearLayout(this);
        mainBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        mainBottomBar.setGravity(Gravity.CENTER);
        mainBottomBar.setBackgroundColor(Color.parseColor("#CC111111"));
        mainBottomBar.setPadding(4, 8, 4, 16);

        addBottomButton(mainBottomBar, "📍", getString(R.string.voxler_scatter), Color.parseColor("#E91E63"),
                v -> setExclusiveRenderMode("scatter"));
        addBottomButton(mainBottomBar, "💎", getString(R.string.voxler_isosurface), Color.parseColor("#2196F3"),
                v -> setExclusiveRenderMode("iso"));
        addBottomButton(mainBottomBar, "🧊", getString(R.string.voxler_volume), Color.parseColor("#4CAF50"),
                v -> setExclusiveRenderMode("volume"));
        addBottomButton(mainBottomBar, "🌈", getString(R.string.voxler_heightfield), Color.parseColor("#FF9800"),
                v -> setExclusiveRenderMode("heightfield"));
        addBottomButton(mainBottomBar, "🌀", getString(R.string.voxler_contours), Color.parseColor("#9C27B0"),
                v -> setExclusiveRenderMode("contours"));
        addBottomButton(mainBottomBar, "📊", getString(R.string.voxler_layers), Color.parseColor("#607D8B"),
                v -> showLayersBar());
        addBottomButton(mainBottomBar, "🔧", getString(R.string.voxler_tools), Color.parseColor("#795548"),
                v -> showToolsDialog());
        addBottomButton(mainBottomBar, "🧠", getString(R.string.voxler_ai_analysis), Color.parseColor("#00E5FF"),
                v -> showAIDialog());
    }

    private void addBottomButton(LinearLayout parent, String icon, final String label, int color, View.OnClickListener listener) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(10, 8, 10, 8);
        btn.setMinimumWidth(80);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10.0f);
        bg.setColor(Color.parseColor("#333333"));
        btn.setBackground(bg);

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(18.0f);
        iconTv.setGravity(Gravity.CENTER);
        btn.addView(iconTv);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(8.0f);
        labelTv.setTextColor(color);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setMaxLines(1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = 2;
        btn.addView(labelTv, lp);

        btn.setOnClickListener(listener);
        btn.setOnLongClickListener(v -> {
            if (label.equals(getString(R.string.voxler_isosurface)) || label.equals(getString(R.string.voxler_volume))) {
                showPanel(volumePanel);
                return true;
            }
            if (label.equals(getString(R.string.voxler_heightfield))) {
                showPanel(heightFieldPanel);
                return true;
            }
            if (label.equals(getString(R.string.voxler_contours))) {
                showPanel(contoursPanel);
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1.0f);
        params.setMargins(3, 0, 3, 0);
        parent.addView(btn, params);
    }

    private void buildLayersBottomBar() {
        layersBottomBar = new LinearLayout(this);
        layersBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        layersBottomBar.setGravity(Gravity.CENTER);
        layersBottomBar.setBackgroundColor(Color.parseColor("#CC111111"));
        layersBottomBar.setPadding(8, 8, 8, 16);
        layersBottomBar.setVisibility(View.GONE);

        String[] layerNames = {"K1", "K2", "K3", "K4"};
        int[] layerColors = {Color.parseColor("#FF0000"), Color.parseColor("#FFFF00"), Color.parseColor("#00FF00"), Color.parseColor("#4488FF")};

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout layerBtn = new LinearLayout(this);
            layerBtn.setOrientation(LinearLayout.VERTICAL);
            layerBtn.setGravity(Gravity.CENTER);
            layerBtn.setPadding(14, 10, 14, 10);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(10.0f);
            bg.setColor(Color.parseColor("#333333"));
            layerBtn.setBackground(bg);

            TextView nameTv = new TextView(this);
            nameTv.setText(layerNames[i]);
            nameTv.setTextSize(16.0f);
            nameTv.setTextColor(layerColors[i]);
            nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
            nameTv.setGravity(Gravity.CENTER);
            layerBtn.addView(nameTv);

            final TextView statusTv = new TextView(this);
            statusTv.setText(layerEnabled[i] ? "ON" : "OFF");
            statusTv.setTextSize(9.0f);
            statusTv.setTextColor(layerEnabled[i] ? Color.parseColor("#4CAF50") : Color.parseColor("#808080"));
            statusTv.setGravity(Gravity.CENTER);
            layerBtn.addView(statusTv);

            layerBtn.setOnClickListener(v -> {
                layerEnabled[idx] = !layerEnabled[idx];
                statusTv.setText(layerEnabled[idx] ? "ON" : "OFF");
                statusTv.setTextColor(layerEnabled[idx] ? Color.parseColor("#4CAF50") : Color.parseColor("#808080"));
                evalJS("setLayerEnabled(" + idx + ", " + layerEnabled[idx] + ")");
            });
            layerBtn.setOnLongClickListener(v -> {
                showLayerSettingsDialog(idx);
                return true;
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            lp.setMargins(4, 0, 4, 0);
            layersBottomBar.addView(layerBtn, lp);
        }

        TextView backBtn = new TextView(this);
        backBtn.setText("✕");
        backBtn.setTextSize(20.0f);
        backBtn.setTextColor(Color.WHITE);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setPadding(16, 8, 16, 8);
        backBtn.setOnClickListener(v -> {
            layersBottomBar.setVisibility(View.GONE);
            mainBottomBar.setVisibility(View.VISIBLE);
        });
        layersBottomBar.addView(backBtn);
    }

    private void showLayersBar() {
        mainBottomBar.setVisibility(View.GONE);
        layersBottomBar.setVisibility(View.VISIBLE);
    }

    private void buildVolumePanel() {
        volumePanel = createPanel(getString(R.string.voxler_volume_settings));
        addSliderToPanel(volumePanel, getString(R.string.voxler_power), 1, 10, currentPower, value -> {
            setExclusiveRenderMode("iso");
            currentPower = value;
            evalJS("setPower(" + value + ")");
        });
        addSliderToPanel(volumePanel, "ISO K1", 0, 100, 25, value -> {
            setExclusiveRenderMode("iso");
            evalJS("setLayerIso(0, " + (value / 100.0f) + ")");
        });
        addSliderToPanel(volumePanel, "ISO K2", 0, 100, 50, value -> {
            setExclusiveRenderMode("iso");
            evalJS("setLayerIso(1, " + (value / 100.0f) + ")");
        });
        addSliderToPanel(volumePanel, "ISO K3", 0, 100, 75, value -> {
            setExclusiveRenderMode("iso");
            evalJS("setLayerIso(2, " + (value / 100.0f) + ")");
        });
        addSliderToPanel(volumePanel, "ISO K4", 0, 100, 95, value -> {
            setExclusiveRenderMode("iso");
            evalJS("setLayerIso(3, " + (value / 100.0f) + ")");
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.BOTTOM;
        lp.leftMargin = 8;
        lp.rightMargin = 8;
        lp.bottomMargin = 100;
        volumePanel.setVisibility(View.GONE);
        mainLayout.addView(volumePanel, lp);
    }

    private void buildHeightFieldPanel() {
        heightFieldPanel = createPanel(getString(R.string.voxler_heightfield_settings));
        addSliderToPanel(heightFieldPanel, getString(R.string.voxler_warp), 0, 100, 50, value -> {
            setExclusiveRenderMode("heightfield");
            evalJS("setHeightFieldWarp(" + (value / 50.0f) + ")");
        });
        addSliderToPanel(heightFieldPanel, getString(R.string.voxler_opacity), 0, 100, 80, value -> {
            setExclusiveRenderMode("heightfield");
            evalJS("setHeightFieldOpacity(" + (value / 100.0f) + ")");
        });

        LinearLayout orientRow = new LinearLayout(this);
        orientRow.setOrientation(LinearLayout.HORIZONTAL);
        orientRow.setPadding(8, 4, 8, 4);
        String[] orientLabels = {"XY", "XZ", "YZ"};
        for (int i = 0; i < 3; i++) {
            final int orient = i;
            TextView btn = new TextView(this);
            btn.setText(orientLabels[i]);
            btn.setTextSize(12.0f);
            btn.setTextColor(Color.WHITE);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(20, 8, 20, 8);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(8.0f);
            bg.setColor(Color.parseColor("#444444"));
            btn.setBackground(bg);
            btn.setOnClickListener(v -> {
                setExclusiveRenderMode("heightfield");
                evalJS("setHeightFieldOrientation(" + orient + ")");
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            lp.setMargins(4, 0, 4, 0);
            orientRow.addView(btn, lp);
        }
        heightFieldPanel.addView(orientRow);

        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(-1, -2);
        lp2.gravity = Gravity.BOTTOM;
        lp2.leftMargin = 8;
        lp2.rightMargin = 8;
        lp2.bottomMargin = 100;
        heightFieldPanel.setVisibility(View.GONE);
        mainLayout.addView(heightFieldPanel, lp2);
    }

    private void buildContoursPanel() {
        contoursPanel = createPanel(getString(R.string.voxler_contours_settings));
        addSliderToPanel(contoursPanel, getString(R.string.voxler_levels), 2, 20, 8, value -> {
            setExclusiveRenderMode("contours");
            evalJS("setContoursLevels(" + value + ")");
        });
        addSliderToPanel(contoursPanel, getString(R.string.voxler_offset), 0, 100, 50, value -> {
            setExclusiveRenderMode("contours");
            evalJS("setContoursOffset(" + (value / 100.0f) + ")");
        });

        LinearLayout orientRow = new LinearLayout(this);
        orientRow.setOrientation(LinearLayout.HORIZONTAL);
        orientRow.setPadding(8, 4, 8, 4);
        String[] orientLabels = {"XY", "XZ", "YZ"};
        for (int i = 0; i < 3; i++) {
            final int orient = i;
            TextView btn = new TextView(this);
            btn.setText(orientLabels[i]);
            btn.setTextSize(12.0f);
            btn.setTextColor(Color.WHITE);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(20, 8, 20, 8);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(8.0f);
            bg.setColor(Color.parseColor("#444444"));
            btn.setBackground(bg);
            btn.setOnClickListener(v -> {
                setExclusiveRenderMode("contours");
                evalJS("setContoursOrientation(" + orient + ")");
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            lp.setMargins(4, 0, 4, 0);
            orientRow.addView(btn, lp);
        }
        contoursPanel.addView(orientRow);

        LinearLayout wallRow = new LinearLayout(this);
        wallRow.setOrientation(LinearLayout.HORIZONTAL);
        wallRow.setPadding(8, 4, 8, 4);
        String[] wallLabels = {"Sol", "Sağ", "Arka", "Ön", "Tümü"};
        String[] wallCodes = {"left", "right", "back", "front", "all"};
        for (int i = 0; i < wallLabels.length; i++) {
            final String wallCode = wallCodes[i];
            final TextView wallBtn = new TextView(this);
            wallBtn.setText(wallLabels[i]);
            wallBtn.setTextSize(10.0f);
            wallBtn.setTextColor(Color.WHITE);
            wallBtn.setGravity(Gravity.CENTER);
            wallBtn.setPadding(12, 6, 12, 6);
            GradientDrawable wallBg = new GradientDrawable();
            wallBg.setCornerRadius(6.0f);
            wallBg.setColor(Color.parseColor("#333333"));
            wallBg.setStroke(1, Color.parseColor("#555555"));
            wallBtn.setBackground(wallBg);
            wallBtn.setTag(false);
            wallBtn.setOnClickListener(v -> {
                evalJS("VTK.toggleContourWall('" + wallCode + "')");
                GradientDrawable bg2 = new GradientDrawable();
                bg2.setCornerRadius(6.0f);
                boolean active = wallBtn.getTag() != null && (Boolean) wallBtn.getTag();
                if (active) {
                    bg2.setColor(Color.parseColor("#333333"));
                    bg2.setStroke(1, Color.parseColor("#555555"));
                    wallBtn.setTextColor(Color.WHITE);
                    wallBtn.setTag(false);
                } else {
                    bg2.setColor(Color.parseColor("#00E5FF"));
                    bg2.setStroke(1, Color.parseColor("#00CCFF"));
                    wallBtn.setTextColor(Color.BLACK);
                    wallBtn.setTag(true);
                }
                wallBtn.setBackground(bg2);
            });
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            wlp.setMargins(2, 0, 2, 0);
            wallRow.addView(wallBtn, wlp);
        }
        contoursPanel.addView(wallRow);

        final TextView filterBtn = new TextView(this);
        filterBtn.setText("🔍 Filtre: Yok");
        filterBtn.setTextSize(12.0f);
        filterBtn.setTextColor(Color.parseColor("#00E5FF"));
        filterBtn.setGravity(Gravity.CENTER);
        filterBtn.setPadding(16, 10, 16, 10);
        GradientDrawable filterBg = new GradientDrawable();
        filterBg.setCornerRadius(8.0f);
        filterBg.setColor(Color.parseColor("#333333"));
        filterBg.setStroke(1, Color.parseColor("#00CCFF"));
        filterBtn.setBackground(filterBg);
        filterBtn.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog);
            builder.setTitle("Contours Filtre");
            builder.setItems(filterNames, (dialog, which) -> {
                String code = filterCodes[which];
                filterBtn.setText("🔍 Filtre: " + filterNames[which]);
                evalJS("setContoursFilter('" + code + "')");
                if (!code.equals("none") && gridData != null) {
                    applyFilterAndResend(code);
                }
            });
            builder.show();
        });
        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(-1, -2);
        filterLp.setMargins(8, 6, 8, 4);
        contoursPanel.addView(filterBtn, filterLp);

        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(-1, -2);
        lp2.gravity = Gravity.BOTTOM;
        lp2.leftMargin = 8;
        lp2.rightMargin = 8;
        lp2.bottomMargin = 100;
        contoursPanel.setVisibility(View.GONE);
        mainLayout.addView(contoursPanel, lp2);
    }

    private void buildFilterSidePanel() {
        filterSidePanel = new LinearLayout(this);
        filterSidePanel.setOrientation(LinearLayout.VERTICAL);
        filterSidePanel.setPadding(8, 8, 8, 8);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10.0f);
        bg.setColor(Color.parseColor("#CC1A1A2E"));
        bg.setStroke(1, Color.parseColor("#333333"));
        filterSidePanel.setBackground(bg);

        TextView titleTv = new TextView(this);
        titleTv.setText("Filtreler");
        titleTv.setTextSize(10.0f);
        titleTv.setTextColor(Color.parseColor("#00E5FF"));
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setPadding(0, 0, 0, 4);
        filterSidePanel.addView(titleTv);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        final LinearLayout buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < filterNames.length; i++) {
            final int idx = i;
            final String code = filterCodes[i];
            final Button button = new Button(this);
            button.setText(filterNames[i]);
            button.setTextSize(8.0f);
            button.setAllCaps(false);
            button.setPadding(6, 2, 6, 2);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(6.0f);
            btnBg.setColor(i == 0 ? Color.parseColor("#00E5FF") : Color.parseColor("#333333"));
            button.setBackground(btnBg);
            button.setTextColor(i == 0 ? Color.BLACK : Color.parseColor("#CCCCCC"));
            button.setOnClickListener(v -> {
                for (int j = 0; j < buttonContainer.getChildCount(); j++) {
                    View child = buttonContainer.getChildAt(j);
                    if (child instanceof Button) {
                        GradientDrawable cBg = new GradientDrawable();
                        cBg.setCornerRadius(6.0f);
                        cBg.setColor(Color.parseColor("#333333"));
                        child.setBackground(cBg);
                        ((Button) child).setTextColor(Color.parseColor("#CCCCCC"));
                    }
                }
                GradientDrawable aBg = new GradientDrawable();
                aBg.setCornerRadius(6.0f);
                aBg.setColor(Color.parseColor("#00E5FF"));
                button.setBackground(aBg);
                button.setTextColor(Color.BLACK);
                applyFilterAndResend(code);
                Toast.makeText(this, filterNames[idx] + " uygulandi", Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
            btnLp.setMargins(0, 2, 0, 2);
            buttonContainer.addView(button, btnLp);
        }
        scrollView.addView(buttonContainer);
        filterSidePanel.addView(scrollView, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (getResources().getDisplayMetrics().density * 80.0f), -2);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.rightMargin = 4;
        lp.topMargin = 80;
        lp.bottomMargin = 100;
        filterSidePanel.setVisibility(View.VISIBLE);
        mainLayout.addView(filterSidePanel, lp);
    }

    private LinearLayout createPanel(String title) {
        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(16, 12, 16, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12.0f);
        bg.setColor(Color.parseColor("#DD1A1A2E"));
        bg.setStroke(1, Color.parseColor("#333333"));
        panel.setBackground(bg);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(14.0f);
        titleTv.setTextColor(Color.parseColor("#00E5FF"));
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        titleRow.addView(titleTv, titleLp);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextSize(22.0f);
        closeBtn.setTextColor(Color.parseColor("#FF4444"));
        closeBtn.setPadding(24, 8, 8, 8);
        closeBtn.setMinWidth(48);
        closeBtn.setMinHeight(48);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setOnClickListener(v -> panel.animate().alpha(0.0f).translationX(-50.0f).setDuration(150L).withEndAction(() -> {
            panel.setVisibility(View.GONE);
            if (currentPanel == panel) {
                currentPanel = null;
            }
            if (filterSidePanel != null) {
                filterSidePanel.setVisibility(View.VISIBLE);
            }
        }).start());
        titleRow.addView(closeBtn);

        panel.addView(titleRow);
        return panel;
    }

    private void addSliderToPanel(final LinearLayout panel, String label, final int min, int max, int defaultVal, final SliderCallback callback) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 6, 0, 6);
        row.setTag("slider_row");

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(11.0f);
        labelTv.setTextColor(Color.parseColor("#AAAAAA"));
        labelTv.setMinWidth(80);
        row.addView(labelTv);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(defaultVal - min);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        seekLp.setMargins(8, 0, 8, 0);
        row.addView(seekBar, seekLp);

        final TextView valueTv = new TextView(this);
        valueTv.setText(String.valueOf(defaultVal));
        valueTv.setTextSize(11.0f);
        valueTv.setTextColor(Color.parseColor("#4CAF50"));
        valueTv.setMinWidth(40);
        valueTv.setGravity(Gravity.END);
        row.addView(valueTv);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueTv.setText(String.valueOf(value));
                if (fromUser) {
                    callback.onChanged(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                hideOtherSliders(panel, row);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                showAllSliders(panel);
            }
        });

        panel.addView(row);
    }

    private void hideOtherSliders(LinearLayout panel, LinearLayout activeRow) {
        for (int i = 0; i < panel.getChildCount(); i++) {
            final View child = panel.getChildAt(i);
            if (child instanceof LinearLayout && "slider_row".equals(child.getTag())) {
                if (child != activeRow) {
                    child.animate().alpha(0.0f).setDuration(150L).withEndAction(() -> child.setVisibility(View.GONE)).start();
                } else {
                    child.setBackgroundColor(Color.parseColor("#1A00E5FF"));
                }
            }
        }
    }

    private void showAllSliders(LinearLayout panel) {
        for (int i = 0; i < panel.getChildCount(); i++) {
            View child = panel.getChildAt(i);
            if (child instanceof LinearLayout && "slider_row".equals(child.getTag())) {
                child.setBackgroundColor(0);
                child.setVisibility(View.VISIBLE);
                child.setAlpha(0.0f);
                child.animate().alpha(1.0f).setDuration(200L).start();
            }
        }
    }

    private void showPanel(final LinearLayout panel) {
        if (currentPanel != null) {
            final LinearLayout closingPanel = currentPanel;
            closingPanel.animate().alpha(0.0f).translationX(-50.0f).setDuration(150L)
                    .withEndAction(() -> closingPanel.setVisibility(View.GONE)).start();
        }
        if (panel.getVisibility() == View.VISIBLE) {
            panel.animate().alpha(0.0f).translationX(-50.0f).setDuration(150L).withEndAction(() -> {
                panel.setVisibility(View.GONE);
                if (filterSidePanel != null) {
                    filterSidePanel.setVisibility(View.VISIBLE);
                }
            }).start();
            currentPanel = null;
            return;
        }
        currentPanel = panel;
        if (filterSidePanel != null) {
            filterSidePanel.setVisibility(View.GONE);
        }
        panel.setAlpha(0.0f);
        panel.setTranslationX(-50.0f);
        panel.setVisibility(View.VISIBLE);
        panel.animate().alpha(1.0f).translationX(0.0f).setDuration(200L).start();
    }

    private void showLayerSettingsDialog(final int layerIndex) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        builder.setTitle("K" + (layerIndex + 1) + " " + getString(R.string.voxler_layer_settings));
        builder.setItems(filterNames, (dialog, which) -> {
            layerFilters[layerIndex] = filterCodes[which];
            applyFilterAndResend(filterCodes[which]);
        });
        builder.setNeutralButton(getString(R.string.voxler_color_scale), (dialog, which) -> showColorScaleDialog(layerIndex));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void showColorScaleDialog(final int layerIndex) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        builder.setTitle("K" + (layerIndex + 1) + " " + getString(R.string.voxler_color_scale));
        builder.setItems(colorScaleNames, (dialog, which) ->
                evalJS("setLayerColorScale(" + layerIndex + ", '" + colorScaleNames[which] + "')"));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void showToolsDialog() {
        String[] toolNames = {
                getString(R.string.voxler_tool_auto_rotate), getString(R.string.voxler_tool_gradient),
                getString(R.string.voxler_tool_streamlines), getString(R.string.voxler_tool_slice_anim),
                getString(R.string.voxler_tool_anomaly), getString(R.string.voxler_tool_depth),
                getString(R.string.voxler_tool_target), getString(R.string.voxler_tool_signal),
                getString(R.string.voxler_tool_grid_overlay), getString(R.string.voxler_tool_reset_camera),
                getString(R.string.voxler_tool_toggle_box), getString(R.string.voxler_tool_toggle_axes),
                getString(R.string.voxler_tool_preset_auto), getString(R.string.voxler_tool_preset_metal),
                getString(R.string.voxler_tool_preset_cavity), "🌈 Rainbow1", "🌈 Rainbow2",
                "⚠️ " + getString(R.string.voxler_tool_reset_all)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        builder.setTitle(getString(R.string.voxler_tools));
        builder.setItems(toolNames, (dialog, which) -> {
            switch (which) {
                case 0:
                    autoRotating = !autoRotating;
                    evalJS("setInteractiveTool('auto_rotate')");
                    Toast.makeText(this, getString(autoRotating ? R.string.voxler_auto_rotate_on : R.string.voxler_auto_rotate_off), Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    evalJS("toggleGradientArrows()");
                    break;
                case 2:
                    evalJS("toggleStreamlines()");
                    break;
                case 3:
                    evalJS("toggleSliceAnimation()");
                    break;
                case 4:
                    evalJS("runAnomalyDetection(50)");
                    break;
                case 5:
                    evalJS("runDepthEstimation()");
                    break;
                case 6:
                    evalJS("runTargetClassification()");
                    break;
                case 7:
                    evalJS("toggleSignalStrengthMap()");
                    break;
                case 8:
                    evalJS("toggleGridOverlay()");
                    break;
                case 9:
                    evalJS("resetCamera()");
                    break;
                case 10:
                    showBox = !showBox;
                    evalJS("setShowBox(" + showBox + ")");
                    break;
                case 11:
                    showAxes = !showAxes;
                    evalJS("setShowAxes(" + showAxes + ")");
                    break;
                case 12:
                    evalJS("applyPreset('auto')");
                    break;
                case 13:
                    evalJS("applyPreset('metal')");
                    break;
                case 14:
                    evalJS("applyPreset('cavity')");
                    break;
                case 15:
                    evalJS("setRainbow1()");
                    Toast.makeText(this, "Rainbow1 aktif", Toast.LENGTH_SHORT).show();
                    break;
                case 16:
                    evalJS("setRainbow2()");
                    Toast.makeText(this, "Rainbow2 aktif", Toast.LENGTH_SHORT).show();
                    break;
                case 17:
                    resetAllSettings();
                    break;
                default:
                    break;
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void resetAllSettings() {
        showScatter = false;
        showIso = true;
        showVolume = false;
        showHeightField = false;
        showContours = false;
        showBox = true;
        showAxes = true;
        autoRotating = false;
        currentPower = 1;
        layerEnabled[0] = true;
        layerEnabled[1] = true;
        layerEnabled[2] = true;
        layerEnabled[3] = true;
        layerIsoValues = new float[]{0.25f, 0.5f, 0.75f, 0.95f};
        layerFilters = new String[]{"none", "none", "none", "none"};
        if (originalGridData != null) {
            gridData = Arrays.copyOf(originalGridData, originalGridData.length);
            originalGridData = null;
        }
        if (volumePanel != null) {
            volumePanel.setVisibility(View.GONE);
        }
        if (heightFieldPanel != null) {
            heightFieldPanel.setVisibility(View.GONE);
        }
        if (contoursPanel != null) {
            contoursPanel.setVisibility(View.GONE);
        }
        currentPanel = null;
        if (layersBottomBar != null) {
            layersBottomBar.setVisibility(View.GONE);
        }
        if (mainBottomBar != null) {
            mainBottomBar.setVisibility(View.VISIBLE);
        }
        evalJS("resetCamera()");
        evalJS("setRenderMode('iso')");
        evalJS("setShowBox(true)");
        evalJS("setShowAxes(true)");
        evalJS("setInteractiveTool('stop')");
        evalJS("applyPreset('auto')");
        evalJS("setHeightFieldWarp(1)");
        evalJS("setHeightFieldOpacity(0.8)");
        evalJS("setContoursLevels(8)");
        evalJS("setContoursOffset(0.5)");
        evalJS("setPower(1)");
        sendGridData();
        stopSpeaking();
        Toast.makeText(this, getString(R.string.voxler_reset_done), Toast.LENGTH_SHORT).show();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.addJavascriptInterface(new VTKBridge(), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/vtk/volume_renderer.html");
    }

    private void loadFromFile(String path) {
        try {
            File file = new File(path);
            List<ScanDataPoint> points = FileManager.readAuto(file);
            if (points == null || points.isEmpty()) {
                return;
            }
            TreeSet<Integer> xSet = new TreeSet<>();
            TreeSet<Integer> ySet = new TreeSet<>();
            for (ScanDataPoint p : points) {
                xSet.add(p.x);
                ySet.add(p.y);
            }
            List<Integer> xList = new ArrayList<>(xSet);
            List<Integer> yList = new ArrayList<>(ySet);
            cols = xList.size();
            rows = yList.size();
            if (cols == 0 || rows == 0) {
                return;
            }
            if (xList.size() > 1) {
                stepSize = Math.abs(xList.get(1) - xList.get(0));
            }
            Map<Integer, Integer> xMap = new HashMap<>();
            Map<Integer, Integer> yMap = new HashMap<>();
            for (int i = 0; i < xList.size(); i++) {
                xMap.put(xList.get(i), i);
            }
            for (int i = 0; i < yList.size(); i++) {
                yMap.put(yList.get(i), i);
            }
            gridData = new float[cols * rows];
            gridDataZ = new float[cols * rows];
            for (ScanDataPoint p : points) {
                Integer xi = xMap.get(p.x);
                Integer yi = yMap.get(p.y);
                if (xi != null && yi != null) {
                    int idx = (yi * cols) + xi;
                    if (idx >= 0 && idx < gridData.length) {
                        gridData[idx] = (float) p.c;
                        gridDataZ[idx] = (float) p.z;
                    }
                }
            }
            Toast.makeText(this, getString(R.string.voxler_data_loaded) + ": " + cols + "x" + rows, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateDemoData() {
        cols = 10;
        rows = 10;
        stepSize = 30;
        gridData = new float[cols * rows];
        gridDataZ = new float[cols * rows];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                double cx = (x - (cols / 2.0)) / (cols / 2.0);
                double cy = (y - (rows / 2.0)) / (rows / 2.0);
                double r = Math.sqrt((cx * cx) + (cy * cy));
                gridData[(cols * y) + x] = (float) (Math.sin(3.14 * r) * Math.exp(-r * 0.5) * 5.0);
                gridDataZ[(cols * y) + x] = (float) ((200.0 * r) + 300.0);
            }
        }
        Toast.makeText(this, getString(R.string.voxler_demo_mode), Toast.LENGTH_SHORT).show();
    }

    private void applyFilterAndResend(String filterCode) {
        if (gridData == null) {
            return;
        }
        if (originalGridData == null) {
            originalGridData = new float[gridData.length];
            System.arraycopy(gridData, 0, originalGridData, 0, gridData.length);
        }
        if (filterCode.equals("none")) {
            System.arraycopy(originalGridData, 0, gridData, 0, gridData.length);
        } else {
            float[] filtered = SignalProcessor.applyFilter1D(originalGridData, cols, rows, filterCode);
            if (filtered != null) {
                System.arraycopy(filtered, 0, gridData, 0, gridData.length);
            }
        }
        sendGridData();
        Toast.makeText(this, "Filtre: " + filterCode, Toast.LENGTH_SHORT).show();
    }

    private void sendGridData() {
        if (gridData == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < gridData.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(gridData[i]);
            }
            evalJS("setGridData(" + cols + ", " + rows + ", " + stepSize + ", '" + sb + "')");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void evalJS(final String js) {
        if (webView != null) {
            handler.post(() -> webView.evaluateJavascript("window.VTK." + js, null));
        }
    }

    private class VTKBridge {
        @JavascriptInterface
        public void onVtkReady() {
            handler.post(() -> {
                vtkReady = true;
                sendGridData();
                handler.postDelayed(() -> {
                    if (gridData != null) {
                        runAutoAIAnalysis();
                    }
                }, 2000L);
            });
        }

        @JavascriptInterface
        public void onMeshExported(String json) {
            handler.post(() -> Toast.makeText(Pro4DVoxlerActivity.this, getString(R.string.voxler_mesh_exported), Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void showInfo(final String text) {
            handler.post(() -> Toast.makeText(Pro4DVoxlerActivity.this, text, Toast.LENGTH_SHORT).show());
        }
    }

    private void showAIDialog() {
        if (gridData == null) {
            Toast.makeText(this, getString(R.string.voxler_ai_no_data), Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] aiOptions = {
                getString(R.string.voxler_ai_anomaly), getString(R.string.voxler_ai_classify),
                getString(R.string.voxler_ai_depth), getString(R.string.voxler_ai_signal),
                getString(R.string.voxler_ai_full)
        };
        final int[] aiColors = {
                Color.parseColor("#FF5252"), Color.parseColor("#69F0AE"), Color.parseColor("#448AFF"),
                Color.parseColor("#FFD740"), Color.parseColor("#00E5FF")
        };
        final String[] aiIcons = {"🚨", "🎯", "📏", "📡", "🧠"};

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, aiOptions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(aiColors[position]);
                tv.setTextSize(16.0f);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                tv.setText(aiIcons[position] + "  " + aiOptions[position]);
                tv.setPadding(40, 30, 40, 30);
                return tv;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        builder.setTitle("🧠 " + getString(R.string.voxler_ai_analysis));
        builder.setAdapter(adapter, (dialog, which) -> runAIAnalysis(which));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void speakText(String text) {
        if (tts != null && ttsReady) {
            String cleanText = text.replaceAll("[\\p{So}\\p{Cn}]", "").replaceAll("─+", "").replaceAll("\\n+", ". ").replaceAll("\\s+", " ").trim();
            tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "ai_analysis");
        } else {
            Toast.makeText(this, "Sesli anlatım hazır değil", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopSpeaking() {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }
    }

    private static final class Stats {
        float min, max, mean, stdDev, range;
    }

    private Stats computeStats(float[] data) {
        Stats s = new Stats();
        s.min = Float.MAX_VALUE;
        s.max = -Float.MAX_VALUE;
        float sum = 0.0f;
        for (float v : data) {
            if (v < s.min) s.min = v;
            if (v > s.max) s.max = v;
            sum += v;
        }
        s.mean = sum / data.length;
        s.range = s.max - s.min;
        if (s.range == 0.0f) s.range = 1.0f;
        float variance = 0.0f;
        for (float v : data) {
            variance += (v - s.mean) * (v - s.mean);
        }
        s.stdDev = (float) Math.sqrt(variance / data.length);
        return s;
    }

    private void runAIAnalysis(final int mode) {
        if (gridData == null) {
            return;
        }
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(getString(R.string.voxler_ai_analyzing));
        pd.setCancelable(false);
        pd.show();
        handler.postDelayed(() -> {
            pd.dismiss();
            Stats s = computeStats(gridData);
            float threshold = s.stdDev * 2.0f + s.mean;
            float thresholdLow = s.mean - s.stdDev * 2.0f;
            int anomalyCount = 0;
            float[] histogram = new float[10];
            for (float v : gridData) {
                if (v > threshold || v < thresholdLow) {
                    anomalyCount++;
                }
                int bin = Math.max(0, Math.min(9, (int) (((v - s.min) / s.range) * 9.0f)));
                histogram[bin] += 1.0f;
            }
            float avgDepth = 0.0f;
            if (gridDataZ != null) {
                float depthSum = 0.0f;
                for (float z : gridDataZ) {
                    depthSum += z;
                }
                avgDepth = depthSum / gridDataZ.length;
            }

            StringBuilder result = new StringBuilder();
            switch (mode) {
                case 0:
                    result.append("🚨 ANOMALİ TESPİTİ\n\n");
                    result.append(String.format("Toplam nokta: %d\n", gridData.length));
                    result.append(String.format("Anomali sayısı: %d (%%%.1f)\n", anomalyCount, (anomalyCount * 100.0f) / gridData.length));
                    result.append(String.format("Eşik değer: %.2f / %.2f\n", thresholdLow, threshold));
                    result.append(String.format("Ortalama: %.2f\nStd Sapma: %.2f\n", s.mean, s.stdDev));
                    if (anomalyCount > 0) {
                        result.append("\n⚠️ Yüksek anomali bölgeleri tespit edildi!");
                    } else {
                        result.append("\n✅ Normal dağılım - anomali yok");
                    }
                    break;
                case 1: {
                    result.append("🎯 HEDEF SINIFLANDIRMA\n\n");
                    float signalStrength = (s.max - s.mean) / s.stdDev;
                    if (signalStrength > 3.0f) {
                        result.append("🟢 Güçlü hedef sinyali\n");
                        result.append("Olasılık: Metal/Mineral\n");
                    } else if (signalStrength > 1.5f) {
                        result.append("🟡 Orta seviye sinyal\n");
                        result.append("Olasılık: Boşluk/Yapı\n");
                    } else {
                        result.append("🔴 Zayıf sinyal\n");
                        result.append("Olasılık: Doğal oluşum\n");
                    }
                    result.append(String.format("\nSinyal gücü: %.2f sigma\n", signalStrength));
                    result.append(String.format("Min: %.2f  Max: %.2f\n", s.min, s.max));
                    result.append(String.format("Grid: %dx%d\n", cols, rows));
                    break;
                }
                case 2:
                    result.append("📏 DERİNLİK TAHMİNİ\n\n");
                    result.append(String.format("Ortalama derinlik: %.1f cm\n", avgDepth));
                    if (gridDataZ != null) {
                        float maxZ = -Float.MAX_VALUE;
                        float minZ = Float.MAX_VALUE;
                        for (float z : gridDataZ) {
                            if (z < minZ) minZ = z;
                            if (z > maxZ) maxZ = z;
                        }
                        result.append(String.format("Min derinlik: %.1f cm\n", minZ));
                        result.append(String.format("Max derinlik: %.1f cm\n", maxZ));
                        result.append(String.format("Derinlik aralığı: %.1f cm\n", maxZ - minZ));
                    }
                    result.append(String.format("\nTahmini hedef derinliği: %.0f-%.0f cm\n", avgDepth * 0.8, avgDepth * 1.2));
                    break;
                case 3:
                    result.append("📡 SİNYAL ANALİZİ\n\n");
                    result.append(String.format("Sinyal aralığı: %.2f - %.2f\n", s.min, s.max));
                    result.append(String.format("Dinamik aralık: %.2f\n", s.range));
                    result.append(String.format("Ortalama: %.2f\n", s.mean));
                    result.append(String.format("Std Sapma: %.2f\n", s.stdDev));
                    result.append(String.format("SNR: %.1f dB\n", Math.log10(s.mean / (s.stdDev + 0.001)) * 20.0));
                    result.append("\nHistogram:\n");
                    for (int i = 0; i < 10; i++) {
                        float binStart = ((i * s.range) / 10.0f) + s.min;
                        float binEnd = (((i + 1) * s.range) / 10.0f) + s.min;
                        StringBuilder bar = new StringBuilder();
                        int bars = (int) ((histogram[i] / gridData.length) * 50.0f);
                        for (int b = 0; b < bars; b++) {
                            bar.append("█");
                        }
                        result.append(String.format("%.1f-%.1f: %s (%d)\n", binStart, binEnd, bar, (int) histogram[i]));
                    }
                    break;
                case 4: {
                    result.append("🧠 TAM YAPAY ZEKA ANALİZİ\n\n");
                    result.append(String.format("📊 Veri: %dx%d grid, %d nokta\n", cols, rows, gridData.length));
                    result.append(String.format("📊 Aralık: %.2f - %.2f\n", s.min, s.max));
                    result.append(String.format("📊 Ortalama: %.2f, Std: %.2f\n\n", s.mean, s.stdDev));
                    result.append(String.format("🚨 Anomali: %d nokta (%%%.1f)\n", anomalyCount, (anomalyCount * 100.0f) / gridData.length));
                    float sig = (s.max - s.mean) / s.stdDev;
                    if (sig > 3.0f) {
                        result.append("🎯 Hedef: Güçlü sinyal (Metal/Mineral)\n");
                    } else if (sig > 1.5f) {
                        result.append("🎯 Hedef: Orta sinyal (Boşluk/Yapı)\n");
                    } else {
                        result.append("🎯 Hedef: Zayıf sinyal (Doğal)\n");
                    }
                    result.append(String.format("📏 Derinlik: ~%.0f cm\n", avgDepth));
                    result.append(String.format("📡 SNR: %.1f dB\n", Math.log10(s.mean / (s.stdDev + 0.001)) * 20.0));
                    result.append("\n📝 ÖNERİLER:\n");
                    if (anomalyCount > gridData.length * 0.1) {
                        result.append("• Yüksek anomali - detaylı tarama önerilir\n");
                    }
                    if (sig > 2.0f) {
                        result.append("• Belirgin hedef - kazı önerilir\n");
                    }
                    result.append("• Daha yüksek çözünürlüklü tarama yapılabilir\n");
                    break;
                }
                default:
                    break;
            }
            showAIResultDialog(result.toString());
        }, 1500L);
    }

    private void showAIResultDialog(final String resultText) {
        AlertDialog.Builder resultDialog = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        resultDialog.setTitle("🧠 " + getString(R.string.voxler_ai_result));
        resultDialog.setMessage(resultText);
        resultDialog.setPositiveButton("OK", (dialog, which) -> stopSpeaking());
        resultDialog.setNeutralButton("🔊 Sesli Anlat", null);
        resultDialog.setCancelable(true);
        resultDialog.setOnCancelListener(dialog -> stopSpeaking());
        final AlertDialog dlg = resultDialog.create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            if (tts != null && tts.isSpeaking()) {
                stopSpeaking();
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setText("🔊 Sesli Anlat");
            } else {
                speakText(resultText);
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setText("⏹ Durdur");
            }
        });
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#00E5FF"));
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
    }

    private void runAutoAIAnalysis() {
        if (gridData == null) {
            return;
        }
        Stats s = computeStats(gridData);
        float threshold = s.stdDev * 2.0f + s.mean;
        float thresholdLow = s.mean - 2.0f * s.stdDev;

        int anomalyCount = 0;
        int highAnomalyCount = 0;
        int lowAnomalyCount = 0;
        float maxAnomaly = 0.0f;
        int maxAnomalyX = 0;
        int maxAnomalyY = 0;
        for (int i = 0; i < gridData.length; i++) {
            float v = gridData[i];
            if (v > threshold) {
                highAnomalyCount++;
                anomalyCount++;
                if (v > maxAnomaly) {
                    maxAnomalyX = i % cols;
                    maxAnomalyY = i / cols;
                    maxAnomaly = v;
                }
            } else if (v < thresholdLow) {
                lowAnomalyCount++;
                anomalyCount++;
            }
        }

        int clusterCount = 0;
        boolean[] visited = new boolean[gridData.length];
        for (int i = 0; i < gridData.length; i++) {
            if (visited[i]) {
                continue;
            }
            float f = gridData[i];
            if (f <= threshold && f >= thresholdLow) {
                continue;
            }
            clusterCount++;
            Queue<Integer> queue = new LinkedList<>();
            queue.add(i);
            while (!queue.isEmpty()) {
                int idx = queue.poll();
                if (idx < 0 || idx >= gridData.length || visited[idx]) {
                    continue;
                }
                float v = gridData[idx];
                if (v <= threshold && v >= thresholdLow) {
                    continue;
                }
                visited[idx] = true;
                int x = idx % cols;
                int y = idx / cols;
                if (x > 0) queue.add(idx - 1);
                if (x < cols - 1) queue.add(idx + 1);
                if (y > 0) queue.add(idx - cols);
                if (y < rows - 1) queue.add(idx + cols);
            }
        }

        float signalStrength = (s.max - s.mean) / (0.001f + s.stdDev);
        float avgDepth = 0.0f;
        if (gridDataZ != null && gridDataZ.length > 0) {
            float depthSum = 0.0f;
            for (float z : gridDataZ) {
                depthSum += z;
            }
            avgDepth = depthSum / gridDataZ.length;
        }

        StringBuilder result = new StringBuilder();
        result.append("🧠 OTOMATIİK YAPAY ZEKA ANALİZİ\n");
        result.append("──────────────────────────────\n\n");
        if (anomalyCount > 0) {
            result.append("🚨 ANOMALİ TESPİT EDİLDİ!\n\n");
            result.append(String.format("   Toplam anomali: %d nokta (%%%.1f)\n", anomalyCount, (anomalyCount * 100.0f) / gridData.length));
            result.append(String.format("   Yüksek anomali: %d nokta\n", highAnomalyCount));
            result.append(String.format("   Düşük anomali: %d nokta\n", lowAnomalyCount));
            result.append(String.format("   Anomali kümesi: %d ayrı bölge\n", clusterCount));
            result.append(String.format("   En güçlü: [X:%d Y:%d] = %.2f\n\n", maxAnomalyX, maxAnomalyY, maxAnomaly));
        } else {
            result.append("✅ Anomali tespit edilmedi\n\n");
        }
        result.append("🎯 HEDEF SINIFLANDIRMA:\n");
        String autoMode;
        String autoPreset;
        String modeLabel;
        if (signalStrength > 4.0f) {
            result.append("   🟢 Çok güçlü sinyal - METAL HEDEF\n");
            result.append("   Olasılık: Metal nesne, define, maden\n");
        } else if (signalStrength > 2.5f) {
            result.append("   🟡 Güçlü sinyal - YAPI/BOŞLUK\n");
            result.append("   Olasılık: Mezar, tünel, oda, boşluk\n");
        } else if (signalStrength > 1.5f) {
            result.append("   🟠 Orta sinyal - OLASI HEDEF\n");
            result.append("   Olasılık: Küçük boşluk, taş yapı\n");
        } else {
            result.append("   🔴 Zayıf sinyal - DOĞAL OLUŞUM\n");
            result.append("   Olasılık: Doğal zemin farkı\n");
        }
        result.append(String.format("   Sinyal gücü: %.1f sigma\n\n", signalStrength));
        if (clusterCount > 0) {
            result.append("📍 BÖLGE ANALİZİ:\n");
            if (clusterCount == 1 && highAnomalyCount > lowAnomalyCount) {
                result.append("   Tek yoğun bölge - Muhtemel gömülü nesne\n");
            } else if (clusterCount == 1 && lowAnomalyCount > highAnomalyCount) {
                result.append("   Tek boşluk bölgesi - Muhtemel mezar/oda\n");
            } else if (clusterCount > 3) {
                result.append("   Çoklu dağınık bölge - Karmaşık yapı\n");
            } else {
                result.append(String.format("   %d ayrı hedef bölgesi tespit edildi\n", clusterCount));
            }
            result.append("\n");
        }
        if (avgDepth > 0.0f) {
            result.append(String.format("📏 DERİNLİK: ~%.0f cm\n\n", avgDepth));
        }
        result.append("📝 ÖNERİLER:\n");
        if (anomalyCount > gridData.length * 0.15) {
            result.append("   • Yüksek anomali yoğunluğu - detaylı tarama yapın\n");
        }
        if (signalStrength > 3.0f) {
            result.append("   • Güçlü hedef - kazı önerilir\n");
        }
        if (clusterCount > 1) {
            result.append("   • Birden fazla hedef - her bölgeyi ayrı inceleyin\n");
        }
        result.append("   • Farklı enterpolasyon modlarını deneyin\n");
        result.append("   • IsoSurface ve Volume modlarıyla 3D inceleyin\n");

        if (signalStrength > 4.0f) {
            autoMode = "iso";
            autoPreset = "metal";
            modeLabel = "IsoSurface (Metal)";
        } else if (signalStrength > 2.5f) {
            if (lowAnomalyCount > highAnomalyCount) {
                autoMode = "volume";
                autoPreset = "void";
                modeLabel = "Volume (Boşluk)";
            } else {
                autoMode = "heightfield";
                autoPreset = "auto";
                modeLabel = "HeightField";
            }
        } else if (clusterCount > 3) {
            autoMode = "contours";
            autoPreset = "auto";
            modeLabel = "Contours";
        } else if (signalStrength > 1.5f) {
            autoMode = "iso";
            autoPreset = "auto";
            modeLabel = "IsoSurface";
        } else {
            autoMode = "scatter";
            autoPreset = "auto";
            modeLabel = "Scatter";
        }
        result.append("\n🔄 OTOMATİK MOD: ").append(modeLabel).append("\n");

        showScatter = autoMode.equals("scatter");
        showIso = autoMode.equals("iso");
        showVolume = autoMode.equals("volume");
        showHeightField = autoMode.equals("heightfield");
        showContours = autoMode.equals("contours");
        evalJS("setRenderMode('" + autoMode + "')");
        evalJS("applyPreset('" + autoPreset + "')");

        showAIResultDialog(result.toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        autoRotating = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        autoRotating = false;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
