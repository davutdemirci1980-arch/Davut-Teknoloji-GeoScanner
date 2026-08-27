package com.geoscanner.app.compare;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.geoscanner.app.R;

/**
 * Renders a {@link ScanDiffResult} as a 2D color-coded difference map:
 * unchanged / no-data cells are faded to a neutral gray so they recede, while
 * changed cells are drawn in a vivid diverging color (orange = increase,
 * blue = decrease) scaled by magnitude. An optional "relief" mode lifts
 * changed cells by a small height proportional to their magnitude, with a
 * shaded skirt underneath, for a simple 3D-bump feel without any 3D engine.
 */
public class DiffMapView extends View {
    private static final int COLOR_NO_DATA = 0xFF1E1E1E;
    private static final int COLOR_UNCHANGED = 0xFF3A3A3A;
    private static final float MAX_LIFT_DP = 14f;

    private ScanDiffResult result;
    private boolean reliefMode;
    private final Paint cellPaint = new Paint();
    private final Paint skirtPaint = new Paint();
    private final Paint textPaint = new Paint();

    public DiffMapView(Context context) {
        super(context);
        init();
    }

    public DiffMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        cellPaint.setStyle(Paint.Style.FILL);
        skirtPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFF888888);
        textPaint.setTextSize(getResources().getDisplayMetrics().density * 13f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setResult(ScanDiffResult result) {
        this.result = result;
        invalidate();
    }

    public void setReliefMode(boolean reliefMode) {
        this.reliefMode = reliefMode;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF111111);

        if (result == null || result.nx == 0 || result.ny == 0) {
            canvas.drawText(getContext().getString(R.string.timecompare_no_result),
                    getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }

        float maxLiftPx = MAX_LIFT_DP * getResources().getDisplayMetrics().density;
        float cellW = getWidth() / (float) result.nx;
        float cellH = (getHeight() - (reliefMode ? maxLiftPx : 0)) / (float) result.ny;

        for (int iy = result.ny - 1; iy >= 0; iy--) {
            float top = (result.ny - 1 - iy) * cellH + (reliefMode ? maxLiftPx : 0);
            for (int ix = 0; ix < result.nx; ix++) {
                DiffCell cell = result.cells[iy][ix];
                float left = ix * cellW;
                float lift = 0f;

                if (!cell.hasA || !cell.hasB) {
                    cellPaint.setColor(COLOR_NO_DATA);
                } else if (!cell.changed) {
                    cellPaint.setColor(COLOR_UNCHANGED);
                } else {
                    double t = result.maxAbsDiff > 1e-9 ? Math.min(1.0, Math.abs(cell.diff) / result.maxAbsDiff) : 1.0;
                    cellPaint.setColor(diffColor(cell.diff >= 0, t));
                    if (reliefMode) lift = (float) (maxLiftPx * (0.25 + 0.75 * t));
                }

                if (reliefMode && lift > 0.5f) {
                    skirtPaint.setColor(darken(cellPaint.getColor(), 0.55f));
                    canvas.drawRect(left, top + cellH - lift, left + cellW, top + cellH, skirtPaint);
                    canvas.drawRect(left, top - lift, left + cellW, top + cellH - lift, cellPaint);
                } else {
                    canvas.drawRect(left, top, left + cellW, top + cellH, cellPaint);
                }
            }
        }
    }

    private static int diffColor(boolean increase, double t) {
        float clamped = (float) Math.max(0.0, Math.min(1.0, t));
        if (increase) {
            return lerpColor(0xFFFFC266, 0xFFFF3B1F, clamped);
        }
        return lerpColor(0xFF8FD3FF, 0xFF1E5BFF, clamped);
    }

    private static int lerpColor(int from, int to, float t) {
        int ar = Color.red(from), ag = Color.green(from), ab = Color.blue(from);
        int br = Color.red(to), bg = Color.green(to), bb = Color.blue(to);
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int b = (int) (ab + (bb - ab) * t);
        return Color.rgb(r, g, b);
    }

    private static int darken(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        return Color.rgb(r, g, b);
    }
}
