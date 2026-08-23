package com.geoscanner.app.simulation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Builds a PDF field report (section 22) for a simulated survey: scene
 * setup (grid/sensor/ground/operator note), the detected anomaly list with
 * its shape/depth/AI-candidate data, and the standard "this is not proof"
 * disclaimer. Uses the framework's built-in PdfDocument — no extra library.
 */
public class SimReportGenerator {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;

    public static File generate(Context context, SimRunConfig config, List<SimAnomalyCluster> clusters) {
        try {
            PdfDocument document = new PdfDocument();
            Writer w = new Writer(document);

            w.title("GeoScanner — Simülasyon Raporu");
            w.small(new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date()));
            w.gap();

            w.header("Tarama Kurulumu");
            w.body(String.format(Locale.US, "Grid: %d x %d hücre, adım %.0f cm, desen: %s",
                    config.grid.cols, config.grid.rows, config.grid.stepCm, config.grid.pattern.displayNameTr));
            w.body("Sensör: " + config.sensor.mode.displayNameTr
                    + String.format(Locale.US, ", yükseklik %.0f cm", config.sensor.heightAboveGroundM * 100));
            w.body("Zemin: " + config.ground.groundType.displayNameTr
                    + String.format(Locale.US, ", parazit seviyesi %%%.0f", config.ground.interferenceLevel * 100));
            if (config.operatorError.enabled) {
                w.body("Operatör hatası simülasyonu: etkin");
            }
            if (!config.interferences.isEmpty()) {
                w.body("Parazit kaynağı sayısı: " + config.interferences.size());
            }
            if (config.operatorNote != null && !config.operatorNote.trim().isEmpty()) {
                w.gap();
                w.header("Operatör Notu");
                w.body(config.operatorNote.trim());
            }
            w.gap();

            w.header("Tespit Edilen Anomaliler (" + clusters.size() + ")");
            if (clusters.isEmpty()) {
                w.body("Gürültü eşiğinin üzerinde anomali kümesi bulunamadı.");
            }
            for (int i = 0; i < clusters.size(); i++) {
                SimAnomalyCluster c = clusters.get(i);
                String typeName = c.candidateType != null ? c.candidateType.displayNameTr : "?";
                w.body(String.format(Locale.US, "%d. %s adayı — güven %%%.0f", i + 1, typeName, c.confidence * 100));
                w.small(String.format(Locale.US, "Konum (%.2f, %.2f) m, şekil %s, genlik %.1f",
                        c.centerXM, c.centerYM, c.shapeLabel(), c.peakAmplitude));
                w.small(String.format(Locale.US, "Derinlik tahmini: yarı-genişlik %.2f m / ters çözüm %.2f m (belirsizlik ±%.2f m)",
                        c.depthEstimateHalfWidthM, c.depthEstimateInversionM, c.depthUncertaintyM));
                if (c.falsePositiveRisk) {
                    w.small("Uyarı: " + c.falsePositiveReason);
                }
                w.gap();
            }

            w.header("Not");
            w.small("Simülasyon veya yazılım analizi, tek başına yeraltındaki bir nesnenin türünü ya da derinliğini "
                    + "kesin olarak kanıtlamaz. Hedef sınıflandırması ve derinlik sonuçları, kullanılan sensörün "
                    + "fiziksel ölçüm kabiliyeti ve saha koşullarıyla sınırlıdır. Bu rapor eğitim/simülasyon amaçlıdır.");

            w.finish();

            File dir = new File(context.getExternalFilesDir(null), "reports");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "SimRapor_" + System.currentTimeMillis() + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                document.writeTo(fos);
            }
            document.close();
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    /** Small stateful helper that paginates plain text blocks onto A4 PdfDocument pages. */
    private static class Writer {
        private final PdfDocument document;
        private final Paint titlePaint = new Paint();
        private final Paint headerPaint = new Paint();
        private final Paint bodyPaint = new Paint();
        private final Paint smallPaint = new Paint();
        private PdfDocument.Page page;
        private Canvas canvas;
        private int pageNumber = 1;
        private float y = MARGIN;

        Writer(PdfDocument document) {
            this.document = document;
            titlePaint.setTextSize(18);
            titlePaint.setFakeBoldText(true);
            headerPaint.setTextSize(13);
            headerPaint.setFakeBoldText(true);
            bodyPaint.setTextSize(11);
            smallPaint.setTextSize(9);
            smallPaint.setColor(0xFF555555);
            startPage();
        }

        private void startPage() {
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
            page = document.startPage(info);
            canvas = page.getCanvas();
            y = MARGIN;
        }

        private void ensureSpace(float needed) {
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page);
                pageNumber++;
                startPage();
            }
        }

        void title(String text) {
            ensureSpace(24);
            canvas.drawText(text, MARGIN, y + 18, titlePaint);
            y += 26;
        }

        void header(String text) {
            ensureSpace(20);
            canvas.drawText(text, MARGIN, y + 14, headerPaint);
            y += 20;
        }

        void body(String text) {
            for (String line : wrap(text, 90)) {
                ensureSpace(16);
                canvas.drawText(line, MARGIN, y + 12, bodyPaint);
                y += 16;
            }
        }

        void small(String text) {
            for (String line : wrap(text, 110)) {
                ensureSpace(12);
                canvas.drawText(line, MARGIN, y + 10, smallPaint);
                y += 12;
            }
        }

        void gap() {
            y += 8;
        }

        void finish() {
            document.finishPage(page);
        }

        private String[] wrap(String text, int maxCharsPerLine) {
            if (text.length() <= maxCharsPerLine) return new String[]{text};
            java.util.List<String> lines = new java.util.ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                if (line.length() + word.length() + 1 > maxCharsPerLine) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            if (line.length() > 0) lines.add(line.toString());
            return lines.toArray(new String[0]);
        }
    }
}
