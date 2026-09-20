package com.giga.tech1000.heartbeatz.views.knobs;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.giga.tech1000.heartbeatz.R;

public final class PartyPulseView extends View {

    private static final int PULSE_COUNT = 4;
    private static final long DURATION_MS = 3000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] radii = new float[PULSE_COUNT];
    private final float[] alphas = new float[PULSE_COUNT];

    private ValueAnimator animator;
    private float maxRadius;
    private int primaryColor;

    public PartyPulseView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        TypedValue typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
        primaryColor = typedValue.data;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));

        fillPaint.setStyle(Paint.Style.FILL);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        // Using a more natural Material easing
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(this::onAnimate);
    }

    private void onAnimate(ValueAnimator a) {
        float progress = (float) a.getAnimatedValue();

        for (int i = 0; i < PULSE_COUNT; i++) {
            float offset = (float) i / PULSE_COUNT;
            float p = (progress + offset) % 1f;

            radii[i] = maxRadius * p;
            // Quadratic fade out for smoother transition
            alphas[i] = (1f - p) * (1f - p);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        maxRadius = Math.min(w, h) / 2.5f; // Keep it slightly contained
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        for (int i = 0; i < PULSE_COUNT; i++) {
            int alpha = (int) (alphas[i] * 255);

            // Draw a subtle fill
            fillPaint.setColor(ColorUtils.setAlphaComponent(primaryColor, (int) (alpha * 0.1f)));
            canvas.drawCircle(cx, cy, radii[i], fillPaint);

            // Draw the ring
            paint.setColor(ColorUtils.setAlphaComponent(primaryColor, (int) (alpha * 0.4f)));
            canvas.drawCircle(cx, cy, radii[i], paint);
        }
    }

    public void start() {
        if (animator != null && !animator.isRunning()) {
            animator.start();
        }
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}


