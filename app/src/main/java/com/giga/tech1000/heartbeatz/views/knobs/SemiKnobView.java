package com.giga.tech1000.heartbeatz.views.knobs;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.giga.tech1000.heartbeatz.R;

public class SemiKnobView extends View {

    private static final float START_ANGLE = 180f;
    private static final float SWEEP_ANGLE = 180f;

    private float min = 0f;
    private float max = 100f;
    private float value = 50f;

    private float cx, cy, radius;

    private Paint trackPaint;
    private Paint progressPaint;
    private Paint dotPaint;
    private Paint textPaint;

    private final RectF arcRect = new RectF();

    public SemiKnobView(Context c, AttributeSet a) {
        super(c, a);
        init(a);
    }

    private void init(@NonNull AttributeSet attrs) {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        int progressColor = 0xFF1ED760;
        int trackColor = 0xFF1F2227;

        try (TypedArray a =
                     getContext().obtainStyledAttributes(attrs, R.styleable.KnobView)) {

            min = a.getFloat(R.styleable.KnobView_kv_min, min);
            max = a.getFloat(R.styleable.KnobView_kv_max, max);
            value = a.getFloat(R.styleable.KnobView_kv_value, value);
            progressColor = a.getColor(R.styleable.KnobView_kv_progressColor, progressColor);
            trackColor = a.getColor(R.styleable.KnobView_kv_trackColor, trackColor);
        } // ✅ auto recycle happens here

        int knobColor = resolveColorAttr(
                com.google.android.material.R.attr.colorSurface,
                0xFFFFFFFF
        );

        int borderColor = resolveColorAttr(
                com.google.android.material.R.attr.colorOutline,
                0x33000000
        );

        int primaryColor = resolveColorAttr(
                androidx.appcompat.R.attr.colorPrimary,
                0xFF8B5CF6
        );

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(8));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(adjustAlpha(trackColor, 0.15f));

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(trackColor);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(dp(8));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(primaryColor);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(primaryColor);
        textPaint.setTextSize(dp(14));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h * 0.85f;

        radius = Math.min(w, h) * 0.4f;

        arcRect.set(
                cx - radius,
                cy - radius,
                cx + radius,
                cy + radius
        );

        LinearGradient fade = new LinearGradient(
                arcRect.left, cy,
                arcRect.right, cy,
                new int[]{
                        adjustAlpha(dotPaint.getColor(), 0.1f),
                        dotPaint.getColor(),
                        adjustAlpha(dotPaint.getColor(), 0.1f)
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        progressPaint.setShader(fade);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float normalized = (value - min) / (max - min);
        normalized = Math.max(0f, Math.min(1f, normalized));

        float sweep = SWEEP_ANGLE * normalized;

        // Track
        canvas.drawArc(
                arcRect,
                START_ANGLE,
                SWEEP_ANGLE,
                false,
                trackPaint
        );

        // Progress
        canvas.drawArc(
                arcRect,
                START_ANGLE,
                sweep,
                false,
                progressPaint
        );

        // Indicator dot
        float angle = START_ANGLE + sweep;
        float rad = (float) Math.toRadians(angle);

        float dx = cx + (float) Math.cos(rad) * radius;
        float dy = cy + (float) Math.sin(rad) * radius;

        canvas.drawCircle(dx, dy, dp(6), dotPaint);

        // ===== Center percentage text =====
        int percent = Math.round(normalized * 100f);
        String text = percent + "%";

        // Vertical centering correction using font metrics
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;

        canvas.drawText(text, cx, textY, textPaint);

    }

    private void clamp() {
        if (value < min) value = min;
        if (value > max) value = max;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private int resolveColorAttr(@AttrRes int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
            return tv.resourceId != 0
                    ? ContextCompat.getColor(getContext(), tv.resourceId)
                    : tv.data;
        }
        return fallback;
    }

    public static int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

}

