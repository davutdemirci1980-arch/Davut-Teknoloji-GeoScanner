package com.geoscanner.app.ui.ar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.geoscanner.app.R;
import com.geoscanner.app.simulation.SimAnomalyCluster;
import com.geoscanner.app.simulation.SimRunConfig;
import com.geoscanner.app.utils.GeoMath;
import com.geoscanner.app.utils.LocaleHelper;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live camera view with anomaly labels bound to compass heading + GPS
 * position (section-25/26-adjacent "AR" viewer — not a listed spec item,
 * added as a genuinely novel field-use feature). Requires the scan to have
 * captured GPS + compass at setup time (SimLabActivity's GPS & Compass
 * section) since that's the only reference frame available to project the
 * simulation's local X/Y anomaly positions into real-world bearings.
 */
public class ArOverlayActivity extends AppCompatActivity {
    public static final String EXTRA_CLUSTERS = "ar_clusters";
    public static final String EXTRA_CONFIG = "ar_config";

    private static final int REQUEST_PERMISSIONS = 600;

    private PreviewView previewView;
    private ArOverlayView arOverlay;
    private TextView tvArStatus;

    private final List<ArTarget> targets = new ArrayList<>();
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private LocationManager locationManager;
    private boolean ready = false;

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);
            double azimuth = Math.toDegrees(orientation[0]);
            if (azimuth < 0) azimuth += 360;
            arOverlay.setHeading(azimuth);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            updateTargetBearings(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_overlay);

        previewView = findViewById(R.id.previewView);
        arOverlay = findViewById(R.id.arOverlay);
        tvArStatus = findViewById(R.id.tvArStatus);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        List<SimAnomalyCluster> clusters = (List<SimAnomalyCluster>) getIntent().getSerializableExtra(EXTRA_CLUSTERS);
        SimRunConfig config = (SimRunConfig) getIntent().getSerializableExtra(EXTRA_CONFIG);

        if (clusters == null || config == null || config.latitude == null || config.longitude == null || config.headingDeg == null) {
            tvArStatus.setText(getString(R.string.ar_no_reference));
            return;
        }

        buildTargets(clusters, config);
        ready = true;
        tvArStatus.setText(getString(R.string.ar_waiting_fix));

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) : null;
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        requestPermissionsIfNeeded();
    }

    private void buildTargets(List<SimAnomalyCluster> clusters, SimRunConfig config) {
        for (int i = 0; i < clusters.size(); i++) {
            SimAnomalyCluster c = clusters.get(i);
            double bearingFromOrigin = (config.headingDeg + Math.toDegrees(Math.atan2(c.centerXM, c.centerYM)) + 360) % 360;
            double distanceFromOrigin = Math.sqrt(c.centerXM * c.centerXM + c.centerYM * c.centerYM);
            double[] latLon = GeoMath.destinationPoint(config.latitude, config.longitude, bearingFromOrigin, distanceFromOrigin);

            String typeName = c.candidateType != null ? c.candidateType.displayNameTr : "?";
            String label = "#" + (i + 1) + " " + typeName;
            int color = c.falsePositiveRisk ? 0xFFFF8800 : (c.peakAmplitude > 0 ? 0xFF00FF88 : 0xFF00AAFF);
            targets.add(new ArTarget(label, latLon[0], latLon[1], color));
        }
    }

    private void requestPermissionsIfNeeded() {
        boolean hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (hasCamera && hasLocation) {
            startCamera();
            return;
        }
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) return;
        boolean hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (hasCamera && hasLocation) {
            startCamera();
        } else {
            tvArStatus.setText(getString(R.string.ar_permission_denied));
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
            } catch (Exception e) {
                Toast.makeText(this, String.format(getString(R.string.ar_camera_error), e.getMessage()), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();
        if (!ready) return;
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (locationManager != null && (hasFine || hasCoarse)) {
            try {
                String provider = hasFine ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
                locationManager.requestLocationUpdates(provider, 2000L, 1f, locationListener);
                Location last = locationManager.getLastKnownLocation(provider);
                if (last != null) updateTargetBearings(last);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(sensorListener);
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {
            }
        }
    }

    private void updateTargetBearings(Location location) {
        for (ArTarget t : targets) {
            t.bearingDeg = GeoMath.bearingBetween(location.getLatitude(), location.getLongitude(), t.latitude, t.longitude);
            t.distanceM = GeoMath.distanceBetween(location.getLatitude(), location.getLongitude(), t.latitude, t.longitude);
        }
        arOverlay.setTargets(targets);
        tvArStatus.setText(String.format(Locale.US, getString(R.string.ar_status_live), targets.size()));
    }
}
