package com.geoscanner.app.ui.records;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.data.FileManager;
import com.geoscanner.app.data.ScanDataPoint;
import com.geoscanner.app.ui.isosurface.IsoSurfaceActivity;
import com.geoscanner.app.ui.scan.ScanPreviewActivity;
import com.geoscanner.app.utils.LocaleHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordsActivity extends AppCompatActivity {
    private ArrayAdapter<String> adapter;
    private List<String> fileNames;
    private List<File> scanFiles;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);
        getWindow().setStatusBarColor(0xFF111111);

        ListView listView = findViewById(R.id.listRecords);
        fileNames = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fileNames);
        listView.setAdapter(adapter);
        loadFiles();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < scanFiles.size()) showFileOptions(scanFiles.get(position));
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < scanFiles.size()) showDeleteDialog(scanFiles.get(position));
            return true;
        });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFiles();
    }

    private void loadFiles() {
        scanFiles = FileManager.listScanFiles(this);
        fileNames.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (File f : scanFiles) {
            String ext = FileManager.getExtension(f).toUpperCase(Locale.US);
            String date = sdf.format(new Date(f.lastModified()));
            long sizeKB = f.length() / 1024;
            fileNames.add(f.getName() + "\n" + ext + " | " + sizeKB + " KB | " + date);
        }
        adapter.notifyDataSetChanged();
        TextView tvEmpty = findViewById(R.id.tvEmpty);
        tvEmpty.setVisibility(scanFiles.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void showFileOptions(File file) {
        String[] options = {
                getString(R.string.records_view_heatmap),
                getString(R.string.records_3d_view),
                getString(R.string.records_export_vtk),
                getString(R.string.records_export_grd),
                getString(R.string.records_export_csv),
                getString(R.string.records_file_info),
                getString(R.string.records_delete),
        };
        new AlertDialog.Builder(this).setTitle(file.getName()).setItems(options, (dialog, which) -> {
            String name = FileManager.getNameWithoutExtension(file);
            switch (which) {
                case 0: {
                    Intent previewIntent = new Intent(this, ScanPreviewActivity.class);
                    previewIntent.putExtra("filePath", file.getAbsolutePath());
                    startActivity(previewIntent);
                    break;
                }
                case 1: {
                    Intent isoIntent = new Intent(this, IsoSurfaceActivity.class);
                    isoIntent.putExtra("filePath", file.getAbsolutePath());
                    startActivity(isoIntent);
                    break;
                }
                case 2:
                    exportAndNotify(file, name, "vtk");
                    break;
                case 3:
                    exportAndNotify(file, name, "grd");
                    break;
                case 4:
                    exportAndNotify(file, name, "csv");
                    break;
                case 5:
                    showFileInfo(file);
                    break;
                case 6:
                    showDeleteDialog(file);
                    break;
                default:
                    break;
            }
        }).show();
    }

    private void exportAndNotify(File file, String name, String format) {
        List<ScanDataPoint> points = FileManager.readAuto(file);
        if (points == null) {
            Toast.makeText(this, getString(R.string.records_cannot_read), Toast.LENGTH_SHORT).show();
            return;
        }
        File out;
        int messageRes;
        switch (format) {
            case "vtk":
                out = FileManager.exportVTK(this, name, points);
                messageRes = R.string.preview_vtk_exported;
                break;
            case "grd":
                out = FileManager.exportGRD(this, name, points);
                messageRes = R.string.preview_grd_exported;
                break;
            default:
                out = FileManager.saveCSV(this, name, points);
                messageRes = R.string.preview_csv_exported;
                break;
        }
        if (out != null) {
            Toast.makeText(this, String.format(getString(messageRes), out.getName()), Toast.LENGTH_LONG).show();
            loadFiles();
        }
    }

    private void showFileInfo(File file) {
        StringBuilder info = new StringBuilder();
        info.append(String.format(getString(R.string.records_info_name), file.getName())).append("\n");
        info.append(String.format(getString(R.string.records_info_size), file.length() / 1024)).append("\n");
        info.append(String.format(getString(R.string.records_info_format), FileManager.getExtension(file).toUpperCase(Locale.US))).append("\n");
        info.append(String.format(getString(R.string.records_info_modified), new Date(file.lastModified()).toString())).append("\n");

        List<ScanDataPoint> points = FileManager.readAuto(file);
        if (points != null) {
            info.append(String.format(getString(R.string.records_info_points), points.size())).append("\n");
            double minC = Double.MAX_VALUE, maxC = -Double.MAX_VALUE;
            for (ScanDataPoint p : points) {
                minC = Math.min(minC, p.c);
                maxC = Math.max(maxC, p.c);
            }
            info.append(String.format(getString(R.string.records_info_range), minC, maxC)).append("\n");
        }

        new AlertDialog.Builder(this).setTitle(getString(R.string.records_file_info)).setMessage(info.toString())
                .setPositiveButton("OK", null).show();
    }

    private void showDeleteDialog(File file) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.records_delete_title))
                .setMessage(String.format(getString(R.string.records_delete_msg), file.getName()))
                .setPositiveButton(getString(R.string.records_delete), (d, w) -> {
                    if (file.delete()) {
                        Toast.makeText(this, getString(R.string.records_deleted), Toast.LENGTH_SHORT).show();
                        loadFiles();
                    }
                })
                .setNegativeButton(getString(R.string.records_cancel), null)
                .show();
    }
}
