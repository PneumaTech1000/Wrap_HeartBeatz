package com.giga.tech1000.heartbeatz.views.knobs;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.giga.tech1000.heartbeatz.R;

import java.util.Locale;

public abstract class KnobView extends View {

    // ===== Constants =====
    protected static final int TOTAL_DOTS = 36;
    protected static final float START_ANGLE = 135f;
    protected static final float SWEEP_ANGLE = 270f;

    // ===== Value =====
    protected float min = 0f;
    protected float max = 100f;
    protected float value = 0f;

    // ===== Geometry =====
    protected float cx, cy;
    protected float knobRadius;
    protected float dotRadius;
    protected float dotRingRadius;

    protected float pointerStartRadius;
    protected float pointerEndRadius;

    protected float[] dotAngles;

    // ===== Paints =====
    protected Paint knobPaint;
    protected Paint knobBorderPaint;
    protected Paint shadowPaint;
    protected Paint dotActivePaint;
    protected Paint dotInactivePaint;
    protected Paint pointerPaint;
    protected Paint valueTextPaint;

    public interface OnValueChangedListener {
        void onValueChanged(float value);
    }

    private OnValueChangedListener listener;

    public void setOnValueChangedListener(OnValueChangedListener l) {
        listener = l;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
        clamp();
        invalidate();
    }

    public KnobView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        int progressColor = 0xFF1ED760;
        int trackColor = 0xFF1F2227;

        if (attrs != null) {
            try (TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.KnobView)) {
                min = a.getFloat(R.styleable.KnobView_kv_min, min);
                max = a.getFloat(R.styleable.KnobView_kv_max, max);
                value = a.getFloat(R.styleable.KnobView_kv_value, value);
                progressColor = a.getColor(R.styleable.KnobView_kv_progressColor, progressColor);
                trackColor = a.getColor(R.styleable.KnobView_kv_trackColor, trackColor);
            }
        }

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

        knobPaint = paintFill(knobColor);
        knobBorderPaint = paintStroke(borderColor, dp(1));
        shadowPaint = paintShadow();

        dotActivePaint = paintFill(progressColor);
        dotInactivePaint = paintFill(trackColor);

        pointerPaint = paintStroke(progressColor, dp(3));
        pointerPaint.setStrokeCap(Paint.Cap.ROUND);

        valueTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valueTextPaint.setColor(primaryColor);
        valueTextPaint.setTextAlign(Paint.Align.CENTER);
        valueTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        clamp();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Preferred size including dots
        int minSize = (int) (dp(80) + getPaddingLeft() + getPaddingRight());
        int width = resolveSizeAndState(minSize, widthMeasureSpec, 0);
        int height = resolveSizeAndState(minSize, heightMeasureSpec, 0);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        cx = w / 2f;
        cy = h / 2f;

        // Use a padding-aware radius calculation to prevent clipping
        float availableRadius = (Math.min(w, h) / 2f) - dp(20);
        knobRadius = availableRadius * 0.75f;

        dotRadius = dp(3);
        dotRingRadius = knobRadius + dp(12);

        pointerStartRadius = knobRadius * 0.45f;
        pointerEndRadius = knobRadius * 0.75f;

        valueTextPaint.setTextSize(knobRadius * 0.4f);

        dotAngles = new float[TOTAL_DOTS];
        for (int i = 0; i < TOTAL_DOTS; i++) {
            dotAngles[i] = (float) Math.toRadians(
                    START_ANGLE + (SWEEP_ANGLE / (TOTAL_DOTS - 1)) * i
            );
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float normalized = getNormalizedValue();
        normalized = clamp01(normalized);

        int activeDots = Math.round(normalized * (TOTAL_DOTS - 1));

        for (int i = 0; i < TOTAL_DOTS; i++) {
            float rad = dotAngles[i];
            float x = cx + (float) Math.cos(rad) * dotRingRadius;
            float y = cy + (float) Math.sin(rad) * dotRingRadius;

            canvas.drawCircle(
                    x, y, dotRadius,
                    i <= activeDots ? dotActivePaint : dotInactivePaint
            );
        }

        canvas.drawCircle(cx, cy, knobRadius, shadowPaint);
        canvas.drawCircle(cx, cy, knobRadius, knobPaint);
        canvas.drawCircle(cx, cy, knobRadius, knobBorderPaint);

        // Pointer
        float angle = START_ANGLE + SWEEP_ANGLE * normalized;
        float rad = (float) Math.toRadians(angle);

        float sx = cx + (float) Math.cos(rad) * pointerStartRadius;
        float sy = cy + (float) Math.sin(rad) * pointerStartRadius;
        float ex = cx + (float) Math.cos(rad) * pointerEndRadius;
        float ey = cy + (float) Math.sin(rad) * pointerEndRadius;

        canvas.drawLine(sx, sy, ex, ey, pointerPaint);

        onDrawText(canvas, cx, cy, knobRadius);
    }

    /**
     * Override this in subclasses to provide custom text rendering in the center of the knob.
     */
    protected abstract void onDrawText(Canvas canvas, float cx, float cy, float knobRadius);

    /**
     * Override this if the mapping from value to 0..1 is non-linear (like Tempo).
     */
    protected float getNormalizedValue() {
        return (value - min) / (max - min);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {

            getParent().requestDisallowInterceptTouchEvent(true);
            updateValueFromTouch(event.getX(), event.getY());
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP ||
                event.getAction() == MotionEvent.ACTION_CANCEL) {

            getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }

        return super.onTouchEvent(event);
    }

    private void updateValueFromTouch(float x, float y) {
        float rawAngle = (float) Math.toDegrees(Math.atan2(y - cy, x - cx));
        rawAngle = (rawAngle + 360f) % 360f;

        float start = (START_ANGLE + 360f) % 360f;
        float end = (start + SWEEP_ANGLE) % 360f;

        boolean withinSweep = rawAngle >= start || rawAngle <= end;

        if (!withinSweep) return;

        float relative = (rawAngle - start + 360f) % 360f;
        relative = Math.min(relative, SWEEP_ANGLE);

        float newValue = min + (relative / SWEEP_ANGLE) * (max - min);

        if (newValue != value) {
            value = newValue;
            clamp();
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            invalidate();
            if (listener != null) {
                listener.onValueChanged(value);
            }
        }
    }

    // ===== Utils =====

    protected void clamp() {
        value = Math.max(min, Math.min(max, value));
    }

    protected float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    protected float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    protected Paint paintFill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    protected Paint paintStroke(int color, float w) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(color);
        return p;
    }

    protected Paint paintShadow() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShadowLayer(dp(8), 0, dp(3), 0x55000000);
        return p;
    }

    protected int resolveColorAttr(@AttrRes int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
            return tv.resourceId != 0
                    ? ContextCompat.getColor(getContext(), tv.resourceId)
                    : tv.data;
        }
        return fallback;
    }
}
