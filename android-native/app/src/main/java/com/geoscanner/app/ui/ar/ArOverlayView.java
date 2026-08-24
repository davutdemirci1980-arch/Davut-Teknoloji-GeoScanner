package com.geoscanner.app.ui.ar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.geoscanner.app.utils.GeoMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transparent overlay drawn on top of the live camera preview: shows a
 * marker + label for each {@link ArTarget} whose bearing from the device's
 * current position falls within the assumed camera field of view, sized by
 * distance. This is a compass/GPS-anchored overlay (not full SLAM/ARCore
 * tracking) — labels stay correctly oriented as the phone pans, but do not
 * account for the device's own walking motion between location fixes.
 */
public class ArOverlayView extends View {
    private static final double ASSUMED_FOV_DEG = 60.0;

    private final List<ArTarget> targets = new ArrayList<>();
    private double deviceHeadingDeg = 0;
    private boolean headingKnown = false;

    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distanceTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ArOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        labelBgPaint.setColor(0xCC000000);
        labelTextPaint.setColor(0xFFFFFFFF);
        labelTextPaint.setTextSize(spToPx(14));
        labelTextPaint.setFakeBoldText(true);
        distanceTextPaint.setColor(0xFFCCCCCC);
        distanceTextPaint.setTextSize(spToPx(12));
        markerPaint.setStyle(Paint.Style.FILL);
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setTargets(List<ArTarget> newTargets) {
        targets.clear();
        targets.addAll(newTargets);
        invalidate();
    }

    public void setHeading(double headingDeg) {
        this.deviceHeadingDeg = headingDeg;
        this.headingKnown = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!headingKnown) return;

        int width = getWidth();
        int height = getHeight();
        float centerY = height * 0.45f;

        for (ArTarget t : targets) {
            double diff = GeoMath.angleDiff(t.bearingDeg, deviceHeadingDeg);
            if (Math.abs(diff) > ASSUMED_FOV_DEG / 2.0) continue;

            float screenX = (float) (width / 2.0 + (diff / (ASSUMED_FOV_DEG / 2.0)) * (width / 2.0));
            float scale = (float) Math.max(0.5, Math.min(1.6, 30.0 / Math.max(1.0, t.distanceM)));

            markerPaint.setColor(t.color);
            canvas.drawCircle(screenX, centerY, 10 * scale, markerPaint);

            String label = t.label;
            String distanceText = String.format(Locale.US, "%.1f m", t.distanceM);
            float labelWidth = Math.max(labelTextPaint.measureText(label), distanceTextPaint.measureText(distanceText)) + 24;
            float labelHeight = 56 * scale;
            float left = screenX - labelWidth / 2;
            float top = centerY - 24 * scale - labelHeight;

            canvas.drawRoundRect(left, top, left + labelWidth, top + labelHeight, 10, 10, labelBgPaint);
            canvas.drawText(label, left + 12, top + 22 * scale, labelTextPaint);
            canvas.drawText(distanceText, left + 12, top + 42 * scale, distanceTextPaint);
        }
    }
}
