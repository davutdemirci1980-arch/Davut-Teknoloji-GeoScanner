package com.geoscanner.app.compare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Builds a PDF field report for a temporal scan comparison: the diff-map
 * screenshot, the overall changed-area summary, the per-region coordinate
 * and percentage list, and a reliability disclaimer. Uses the framework's
 * built-in PdfDocument, no extra library.
 */
public class DiffReportGenerator {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;

    public static File generate(Context context, ScanDiffResult result, String nameA, String nameB, Bitmap screenshot) {
        try {
            PdfDocument document = new PdfDocument();
            Writer w = new Writer(document);

            w.title("GeoScanner — Zaman Karşılaştırma Raporu");
            w.small(new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date()));
            w.gap();

            w.header("Karşılaştırılan Dosyalar");
            w.body("A: " + nameA);
            w.body("B: " + nameB);
            w.gap();

            if (screenshot != null) {
                w.header("Fark Haritası");
                w.image(screenshot);
                w.gap();
            }

            w.header("Özet");
            w.body(String.format(Locale.US, "Genel değişim: %%%.1f  (%d / %d hücre)",
                    result.overallChangedPercent, result.changedCells, result.overlapCells));
            w.body(String.format(Locale.US, "Hizalama kayması: %d, %d hücre — benzerlik skoru %.2f",
                    result.shiftX, result.shiftY, result.correlationScore));
            w.gap();

            w.header("Değişen Alanlar (" + result.regions.size() + ")");
            if (result.regions.isEmpty()) {
                w.body("Eşik üzerinde anlamlı bir değişim bulunamadı.");
            }
            int i = 1;
            for (DiffRegion r : result.regions) {
                String dir = r.increase ? "arttı" : "azaldı";
                w.body(String.format(Locale.US, "%d. (%.2f, %.2f) — (%.2f, %.2f) m   %%%.0f %s   %d hücre",
                        i++, r.minXm, r.minYm, r.maxXm, r.maxYm, r.pctChange, dir, r.cellCount));
            }
            w.gap();

            w.header("Not");
            w.small("Bu rapor, iki farklı zamanda alınan tarama verileri arasındaki sayısal farkı gösterir. "
                    + "Tespit edilen farklar zeminin doğal değişkenliğinden, ölçüm tekrarlanabilirliğinden veya "
                    + "sensör konumlandırma hatasından da kaynaklanabilir; tek başına yeraltındaki bir değişimin "
                    + "kanıtı değildir.");

            w.finish();

            File dir = new File(context.getExternalFilesDir(null), "reports");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "FarkRaporu_" + System.currentTimeMillis() + ".pdf");
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

        void image(Bitmap bitmap) {
            float maxWidth = PAGE_WIDTH - 2f * MARGIN;
            float scale = Math.min(1f, maxWidth / bitmap.getWidth());
            float drawWidth = bitmap.getWidth() * scale;
            float drawHeight = bitmap.getHeight() * scale;
            float maxHeight = PAGE_HEIGHT - 2f * MARGIN;
            if (drawHeight > maxHeight) {
                float shrink = maxHeight / drawHeight;
                drawWidth *= shrink;
                drawHeight *= shrink;
            }
            ensureSpace(drawHeight);
            Rect dest = new Rect(MARGIN, (int) y, (int) (MARGIN + drawWidth), (int) (y + drawHeight));
            canvas.drawBitmap(bitmap, null, dest, null);
            y += drawHeight + 8;
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
