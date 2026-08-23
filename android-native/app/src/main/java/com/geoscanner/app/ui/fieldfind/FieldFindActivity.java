package com.geoscanner.app.ui.fieldfind;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.geoscanner.app.R;
import com.geoscanner.app.utils.AiSettings;
import com.geoscanner.app.utils.AiVisionClient;
import com.geoscanner.app.utils.LocaleHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FieldFindActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_IMAGE = 300;
    private static final int REQUEST_CAMERA_CAPTURE = 301;
    private static final int REQUEST_CAMERA_PERMISSION = 302;

    private ImageView ivPhotoPreview;
    private TextView tvPhotoPlaceholder;
    private TextView btnAnalyze;
    private TextView tvStatus;
    private View resultCard;
    private TextView tvResult;

    private Bitmap currentPhoto;
    private File pendingCameraFile;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_field_find);
        getWindow().setStatusBarColor(0xFF111111);
        getWindow().setNavigationBarColor(0xFF111111);

        ivPhotoPreview = findViewById(R.id.ivPhotoPreview);
        tvPhotoPlaceholder = findViewById(R.id.tvPhotoPlaceholder);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        tvStatus = findViewById(R.id.tvStatus);
        resultCard = findViewById(R.id.resultCard);
        tvResult = findViewById(R.id.tvResult);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPickGallery).setOnClickListener(v -> pickFromGallery());
        findViewById(R.id.btnTakePhoto).setOnClickListener(v -> requestCameraAndCapture());
        btnAnalyze.setOnClickListener(v -> onAnalyzeClicked());
    }

    private void pickFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.field_find_pick_gallery)), REQUEST_PICK_IMAGE);
    }

    private void requestCameraAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        launchCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, getString(R.string.field_find_camera_permission), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchCamera() {
        try {
            File dir = getFieldPhotoDir();
            File photoFile = new File(dir, "find_" + System.currentTimeMillis() + ".jpg");
            pendingCameraFile = photoFile;
            Uri photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);

            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQUEST_CAMERA_CAPTURE);
            } else {
                Toast.makeText(this, getString(R.string.field_find_no_photo), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, String.format(getString(R.string.field_find_error), e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private File getFieldPhotoDir() {
        File dir = new File(getExternalFilesDir(null), "field_photos");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_PICK_IMAGE && data != null && data.getData() != null) {
            loadBitmapFromUri(data.getData());
        } else if (requestCode == REQUEST_CAMERA_CAPTURE && pendingCameraFile != null) {
            loadBitmapFromFile(pendingCameraFile);
        }
    }

    private void loadBitmapFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) {
                Toast.makeText(this, getString(R.string.field_find_no_photo), Toast.LENGTH_SHORT).show();
                return;
            }
            int rotation = 0;
            try (InputStream exifStream = getContentResolver().openInputStream(uri)) {
                if (exifStream != null) rotation = readExifRotation(exifStream);
            } catch (Exception ignored) {
            }
            setCurrentPhoto(rotateIfNeeded(bitmap, rotation));
        } catch (Exception e) {
            Toast.makeText(this, String.format(getString(R.string.field_find_error), e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadBitmapFromFile(File file) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) {
                Toast.makeText(this, getString(R.string.field_find_no_photo), Toast.LENGTH_SHORT).show();
                return;
            }
            int rotation = 0;
            try (FileInputStream fis = new FileInputStream(file)) {
                rotation = readExifRotation(fis);
            } catch (Exception ignored) {
            }
            setCurrentPhoto(rotateIfNeeded(bitmap, rotation));
        } catch (Exception e) {
            Toast.makeText(this, String.format(getString(R.string.field_find_error), e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private int readExifRotation(InputStream stream) throws IOException {
        ExifInterface exif = new ExifInterface(stream);
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    private Bitmap rotateIfNeeded(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void setCurrentPhoto(Bitmap bitmap) {
        currentPhoto = bitmap;
        ivPhotoPreview.setImageBitmap(bitmap);
        ivPhotoPreview.setVisibility(View.VISIBLE);
        tvPhotoPlaceholder.setVisibility(View.GONE);
        resultCard.setVisibility(View.GONE);
        btnAnalyze.setEnabled(true);
        btnAnalyze.setAlpha(1.0f);
    }

    private void onAnalyzeClicked() {
        if (currentPhoto == null) {
            Toast.makeText(this, getString(R.string.field_find_no_photo), Toast.LENGTH_SHORT).show();
            return;
        }
        String apiKey = AiSettings.getApiKey(this);
        if (apiKey.isEmpty()) {
            promptForApiKey();
            return;
        }
        startAnalysis(apiKey);
    }

    private void promptForApiKey() {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);

        TextView message = new TextView(this);
        message.setText(getString(R.string.field_find_no_api_key));
        message.setTextColor(0xFFCCCCCC);
        message.setPadding(0, 0, 0, pad / 2);
        container.addView(message);

        EditText input = new EditText(this);
        input.setHint(R.string.settings_ai_key_hint);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);
        container.addView(input);

        new android.app.AlertDialog.Builder(this, R.style.Theme_GeoScanner_Dialog)
                .setTitle(getString(R.string.settings_ai_key_dialog_title))
                .setView(container)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String key = input.getText().toString().trim();
                    if (!key.isEmpty()) {
                        AiSettings.setApiKey(this, key);
                        startAnalysis(key);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startAnalysis(String apiKey) {
        btnAnalyze.setEnabled(false);
        btnAnalyze.setAlpha(0.4f);
        tvStatus.setText(getString(R.string.field_find_analyzing));
        tvStatus.setVisibility(View.VISIBLE);
        resultCard.setVisibility(View.GONE);

        String model = AiSettings.getModel(this);
        String prompt = getString(R.string.field_find_prompt);

        AiVisionClient.analyze(apiKey, model, currentPhoto, prompt, new AiVisionClient.Callback() {
            @Override
            public void onSuccess(String analysisText) {
                tvStatus.setVisibility(View.GONE);
                btnAnalyze.setEnabled(true);
                btnAnalyze.setAlpha(1.0f);
                tvResult.setText(analysisText);
                resultCard.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                tvStatus.setVisibility(View.GONE);
                btnAnalyze.setEnabled(true);
                btnAnalyze.setAlpha(1.0f);
                Toast.makeText(FieldFindActivity.this, String.format(getString(R.string.field_find_error), message), Toast.LENGTH_LONG).show();
            }
        });
    }
}
