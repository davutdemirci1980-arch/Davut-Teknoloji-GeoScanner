package com.geoscanner.app.ui.main;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.geoscanner.app.R;
import com.geoscanner.app.ble.BLEManager;
import com.geoscanner.app.data.FileManager;
import com.geoscanner.app.data.ScanDataPoint;
import com.geoscanner.app.ui.fieldfind.FieldFindActivity;
import com.geoscanner.app.ui.live3d.Live3DActivity;
import com.geoscanner.app.ui.records.RecordsActivity;
import com.geoscanner.app.ui.scan.ScanPreviewActivity;
import com.geoscanner.app.ui.scan.ScanSetupActivity;
import com.geoscanner.app.ui.settings.SettingsActivity;
import com.geoscanner.app.ui.voxler4d.Pro4DVoxlerActivity;
import com.geoscanner.app.utils.LocaleHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMPORT_FILE = 200;
    private static final int REQUEST_PERMISSIONS = 100;

    private BLEManager bleManager;
    private TextView tvConnectionStatus;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(0xFF111111);
        getWindow().setNavigationBarColor(0xFF111111);

        bleManager = BLEManager.getInstance();
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);

        requestPermissions();
        setupButtons();
        updateConnectionStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateConnectionStatus();
    }

    private void setupButtons() {
        findViewById(R.id.btnConnect).setOnClickListener(v -> showDeviceScanner());
        findViewById(R.id.btnNewScan).setOnClickListener(v -> startActivity(new Intent(this, ScanSetupActivity.class)));
        findViewById(R.id.btnLive3D).setOnClickListener(v -> startActivity(new Intent(this, Live3DActivity.class)));
        findViewById(R.id.btnRecords).setOnClickListener(v -> startActivity(new Intent(this, RecordsActivity.class)));
        findViewById(R.id.btnFieldFind).setOnClickListener(v -> startActivity(new Intent(this, FieldFindActivity.class)));
        findViewById(R.id.btn4DVoxler).setOnClickListener(v -> startActivity(new Intent(this, Pro4DVoxlerActivity.class)));
        findViewById(R.id.btnImport).setOnClickListener(v -> importFile());
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void updateConnectionStatus() {
        if (bleManager.isConnected()) {
            BluetoothDevice device = bleManager.getConnectedDevice();
            String name = "Unknown";
            if (device != null) {
                try {
                    if (device.getName() != null) name = device.getName();
                } catch (SecurityException ignored) {
                }
            }
            String typeLabel;
            switch (bleManager.getDetectedDeviceType()) {
                case "zirve":
                    typeLabel = " [Zirve 4D]";
                    break;
                case "nordic":
                    typeLabel = " [Nordic]";
                    break;
                case "ble":
                    typeLabel = " [BLE]";
                    break;
                case "classic":
                    typeLabel = " [Classic]";
                    break;
                default:
                    typeLabel = "";
                    break;
            }
            tvConnectionStatus.setText(String.format(getString(R.string.main_connected), name) + typeLabel);
            tvConnectionStatus.setTextColor(0xFF00FF00);
            return;
        }
        tvConnectionStatus.setText(getString(R.string.main_disconnected));
        tvConnectionStatus.setTextColor(0xFFFF4444);
    }

    private void showDeviceScanner() {
        if (!bleManager.isBluetoothEnabled()) {
            Toast.makeText(this, getString(R.string.dialog_enable_bt), Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_GeoScanner_Dialog);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_device_scan, (ViewGroup) null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ListView listView = dialogView.findViewById(R.id.deviceList);
        TextView tvStatus = dialogView.findViewById(R.id.tvScanStatus);
        Button btnScan = dialogView.findViewById(R.id.btnStartScan);
        ArrayList<String> deviceNames = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        listView.setAdapter(adapter);

        bleManager.setDiscoveryListener(new BLEManager.DeviceDiscoveryListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi) {
                String name = "Unknown";
                try {
                    if (device.getName() != null) name = device.getName();
                } catch (SecurityException ignored) {
                }
                String signal = rssi > -60 ? "████" : rssi > -75 ? "███" : rssi > -85 ? "██" : "█";
                String upper = name.toUpperCase();
                boolean isKnown = upper.contains("ZIRVE") || upper.contains("ZRV") || upper.contains("4D") || upper.contains("GEO")
                        || upper.contains("SCANNER") || upper.contains("GPR") || upper.contains("HC-") || upper.contains("HM-")
                        || upper.contains("BT-") || upper.contains("MLT") || upper.contains("JDY") || upper.contains("CC254");
                String prefix = isKnown ? "⭐ " : "";
                String entry = prefix + name + " (" + device.getAddress() + ") " + signal + " " + rssi + "dBm";
                if (!deviceNames.contains(entry)) {
                    if (isKnown) deviceNames.add(0, entry);
                    else deviceNames.add(entry);
                    adapter.notifyDataSetChanged();
                }
                tvStatus.setText(String.format(getString(R.string.dialog_found_devices), deviceNames.size()));
            }

            @Override
            public void onScanFinished() {
                tvStatus.setText(String.format(getString(R.string.dialog_scan_complete), deviceNames.size()));
                btnScan.setText(getString(R.string.dialog_scan_again));
                btnScan.setEnabled(true);
            }
        });

        btnScan.setOnClickListener(v -> {
            deviceNames.clear();
            adapter.notifyDataSetChanged();
            tvStatus.setText(getString(R.string.dialog_scanning));
            btnScan.setEnabled(false);
            bleManager.startScan();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            List<BluetoothDevice> devices = bleManager.getDiscoveredDevices();
            if (position >= devices.size()) return;
            bleManager.stopScan();
            BluetoothDevice device = devices.get(position);
            tvStatus.setText(getString(R.string.dialog_connecting));
            bleManager.setConnectionListener(new BLEManager.ConnectionListener() {
                @Override
                public void onConnected(BluetoothDevice d) {
                    dialog.dismiss();
                    updateConnectionStatus();
                    Toast.makeText(MainActivity.this, getString(R.string.dialog_connected), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onDisconnected() {
                    updateConnectionStatus();
                }

                @Override
                public void onConnectionFailed(String reason) {
                    tvStatus.setText(String.format(getString(R.string.dialog_connection_failed), reason));
                }
            });
            bleManager.connect(device);
        });

        dialog.show();
        bleManager.startScan();
        tvStatus.setText(getString(R.string.dialog_scanning));
        btnScan.setEnabled(false);
    }

    private void importFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.import_select)), REQUEST_IMPORT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_FILE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            String fileName = "imported_" + System.currentTimeMillis();
            String path = uri.getPath();
            if (path != null) {
                int lastDot = path.lastIndexOf('.');
                fileName = lastDot >= 0 ? fileName + path.substring(lastDot) : fileName + ".csv";
            }
            File destFile = new File(FileManager.getScanDirectory(this), fileName);
            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while (is != null && (len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            }

            List<ScanDataPoint> points = FileManager.readAuto(destFile);
            if (points == null || points.isEmpty()) {
                Toast.makeText(this, getString(R.string.import_parse_error), Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, String.format(getString(R.string.import_success), points.size()), Toast.LENGTH_SHORT).show();
            Intent previewIntent = new Intent(this, ScanPreviewActivity.class);
            previewIntent.putExtra("filePath", destFile.getAbsolutePath());
            startActivity(previewIntent);
        } catch (Exception e) {
            Toast.makeText(this, String.format(getString(R.string.import_error), e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            perms.add("android.permission.BLUETOOTH_SCAN");
            perms.add("android.permission.BLUETOOTH_CONNECT");
        }
        perms.add("android.permission.ACCESS_FINE_LOCATION");
        perms.add("android.permission.ACCESS_COARSE_LOCATION");

        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }
}
