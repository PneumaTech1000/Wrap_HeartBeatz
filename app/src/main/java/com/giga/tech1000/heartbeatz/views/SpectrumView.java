package com.giga.tech1000.heartbeatz.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.giga.tech1000.heartbeatz.R;

/**
 * Responsive FFT bar spectrum + EQ spline curve (DSPark-style).
 * <p>
 * Expects normalized magnitudes in {@code [0, 1]} from {@link #setFftData}.
 * EQ curve points in {@code [0, 1]} map to −12…+12 dB vertical scale via {@link #setEqCurveData}.
 */
public class SpectrumView extends View {

    private static final int BAR_COUNT = 48;
    private static final float DB_RANGE = 12f; // ±12 dB for EQ curve

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint midLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();

    /** Display bars (smoothed). */
    private final float[] bars = new float[BAR_COUNT];
    /** Incoming FFT (may differ in length). */
    @Nullable private float[] fftSource;
    /** EQ gains normalized 0..1 (0 = −12 dB, 0.5 = 0 dB, 1 = +12 dB). */
    @Nullable private float[] eqCurve;

    private float animPhase;
    private boolean hasLiveData;
    private boolean standby;

    @ColorInt private int primaryColor;
    @ColorInt private int cyanColor;
    @ColorInt private int gridColor;
    @ColorInt private int midLineColor;
    @ColorInt private int nodeFillColor;

    @Nullable private LinearGradient barGradient;
    private int lastW;
    private int lastH;

    private ValueAnimator idleAnimator;

    public SpectrumView(Context context) {
        super(context);
        init(context, null);
    }

    public SpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, null);
    }

    public SpectrumView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, null);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        primaryColor = ContextCompat.getColor(context, R.color.hb_primary);
        cyanColor = ContextCompat.getColor(context, R.color.hb_accent_cyan);
        gridColor = 0x0FFFFFFF;
        midLineColor = 0x22FFFFFF;
        nodeFillColor = 0xFFFFFFFF;

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(gridColor);

        midLinePaint.setStyle(Paint.Style.STROKE);
        midLinePaint.setStrokeWidth(dp(1f));
        midLinePaint.setColor(midLineColor);
        midLinePaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{dp(4f), dp(4f)}, 0f));

        barPaint.setStyle(Paint.Style.FILL);

        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(dp(2.5f));
        curvePaint.setColor(cyanColor);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setShadowLayer(dp(8f), 0f, 0f, cyanColor & 0xCCFFFFFF);

        nodePaint.setStyle(Paint.Style.FILL);
        nodePaint.setColor(cyanColor);

        nodeStrokePaint.setStyle(Paint.Style.STROKE);
        nodeStrokePaint.setStrokeWidth(dp(1.5f));
        nodeStrokePaint.setColor(0xFF0E0E11);

        setLayerType(LAYER_TYPE_SOFTWARE, null); // shadow on curve
        startIdleMotion();
    }

    private void startIdleMotion() {
        if (idleAnimator != null) idleAnimator.cancel();
        idleAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        idleAnimator.setDuration(4000);
        idleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        idleAnimator.setInterpolator(new LinearInterpolator());
        idleAnimator.addUpdateListener(a -> {
            animPhase = (float) a.getAnimatedValue();
            if (!hasLiveData) {
                synthesizeIdleBars();
            } else {
                smoothTowardSource();
            }
            invalidate();
        });
        idleAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (idleAnimator != null && !idleAnimator.isRunning()) {
            idleAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (idleAnimator != null) idleAnimator.cancel();
        super.onDetachedFromWindow();
    }

    /** Normalized FFT magnitudes 0..1 (any length). */
    public void setFftData(@Nullable float[] magnitudes) {
        if (magnitudes == null || magnitudes.length == 0) {
            hasLiveData = false;
            return;
        }
        if (fftSource == null || fftSource.length != magnitudes.length) {
            fftSource = new float[magnitudes.length];
        }
        System.arraycopy(magnitudes, 0, fftSource, 0, magnitudes.length);
        hasLiveData = true;
        standby = false;
        smoothTowardSource();
        invalidate();
    }

    /**
     * EQ curve samples in 0..1 (maps to −12…+12 dB). Length typically = band count.
     */
    public void setEqCurveData(@Nullable float[] curve) {
        this.eqCurve = curve;
        invalidate();
    }

    public void setStandby(boolean standby) {
        this.standby = standby;
        if (standby) hasLiveData = false;
        invalidate();
    }

    public void setFftSize(int size) {
        // kept for API compatibility
    }

    public void setSampleRate(int rate) {
        // kept for API compatibility
    }

    private void smoothTowardSource() {
        if (fftSource == null) return;
        int n = fftSource.length;
        for (int i = 0; i < BAR_COUNT; i++) {
            float t = i / (float) (BAR_COUNT - 1);
            // Logarithmic-ish pick from source bins
            float logT = (float) Math.pow(t, 0.65);
            int idx = Math.min(n - 1, Math.round(logT * (n - 1)));
            float target = clamp01(fftSource[idx]);
            // Energy falloff toward highs looks more natural
            target *= (0.55f + 0.45f * (1f - t * 0.5f));
            bars[i] += (target - bars[i]) * 0.35f;
            // Slow decay
            bars[i] *= 0.985f;
        }
    }

    private void synthesizeIdleBars() {
        for (int i = 0; i < BAR_COUNT; i++) {
            float t = i / (float) BAR_COUNT;
            float wave = (float) (0.35 + 0.25 * Math.sin(animPhase + i * 0.22)
                    + 0.15 * Math.sin(animPhase * 1.7 + i * 0.11));
            float freqCurve = Math.max(0.12f, 1f - t * 0.5f);
            float eqBoost = 0.55f;
            if (eqCurve != null && eqCurve.length > 0) {
                int bi = Math.min(eqCurve.length - 1, (int) (t * eqCurve.length));
                eqBoost = 0.4f + eqCurve[bi] * 0.6f;
            }
            float target = wave * freqCurve * eqBoost;
            if (standby) target *= 0.15f;
            bars[i] += (target - bars[i]) * 0.2f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (w != lastW || h != lastH) {
            lastW = w;
            lastH = h;
            barGradient = new LinearGradient(
                    0, 0, 0, h,
                    new int[]{primaryColor, primaryColor & 0xCCFFFFFF, 0x00000000},
                    new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP);
        }

        float midY = h * 0.5f;
        float padL = dp(12f);
        float padR = dp(8f);
        float usableW = w - padL - padR;

        // Horizontal grid (dB)
        float[] gridYs = {0.08f, 0.25f, 0.5f, 0.75f, 0.92f};
        for (float gy : gridYs) {
            float y = h * gy;
            canvas.drawLine(padL, y, w - padR, y, gridPaint);
        }
        // 0 dB dashed midline
        canvas.drawLine(padL, midY, w - padR, midY, midLinePaint);

        // Vertical light grid
        for (int i = 1; i < 8; i++) {
            float x = padL + usableW * (i / 8f);
            canvas.drawLine(x, dp(4f), x, h - dp(4f), gridPaint);
        }

        // Spectrum bars (from bottom)
        float gap = dp(1.5f);
        float barW = Math.max(dp(2f), (usableW - gap * (BAR_COUNT - 1)) / BAR_COUNT);
        if (barGradient != null) {
            barPaint.setShader(barGradient);
        } else {
            barPaint.setColor(primaryColor);
        }

        for (int i = 0; i < BAR_COUNT; i++) {
            float level = clamp01(bars[i]);
            float barH = Math.max(dp(3f), level * (h * 0.78f));
            float x = padL + i * (barW + gap);
            float top = h - barH;
            canvas.drawRoundRect(x, top, x + barW, h, dp(2f), dp(2f), barPaint);
        }
        barPaint.setShader(null);

        // EQ spline curve
        drawEqCurve(canvas, padL, usableW, midY, h);
    }

    private void drawEqCurve(Canvas canvas, float padL, float usableW, float midY, int h) {
        if (eqCurve == null || eqCurve.length < 2) {
            // Flat 0 dB guide if no curve
            curvePath.reset();
            curvePath.moveTo(padL, midY);
            curvePath.lineTo(padL + usableW, midY);
            canvas.drawPath(curvePath, curvePaint);
            return;
        }

        int n = eqCurve.length;
        float[] xs = new float[n];
        float[] ys = new float[n];
        float amp = midY - dp(14f); // max excursion

        for (int i = 0; i < n; i++) {
            xs[i] = padL + (i / (float) (n - 1)) * usableW;
            // eqCurve 0..1 → gain −12..+12 → y inverted
            float gain01 = clamp01(eqCurve[i]);
            float gainDb = (gain01 * 2f - 1f) * DB_RANGE;
            ys[i] = midY - (gainDb / DB_RANGE) * amp;
        }

        curvePath.reset();
        curvePath.moveTo(padL, ys[0]);
        curvePath.lineTo(xs[0], ys[0]);
        for (int i = 0; i < n - 1; i++) {
            float cpX = (xs[i] + xs[i + 1]) * 0.5f;
            curvePath.cubicTo(cpX, ys[i], cpX, ys[i + 1], xs[i + 1], ys[i + 1]);
        }
        curvePath.lineTo(padL + usableW, ys[n - 1]);

        curvePaint.setColor(cyanColor);
        canvas.drawPath(curvePath, curvePaint);

        // Control nodes
        for (int i = 0; i < n; i++) {
            float r = dp(i == n / 2 ? 5f : 3.5f);
            nodePaint.setColor(i == n / 2 ? nodeFillColor : cyanColor);
            canvas.drawCircle(xs[i], ys[i], r, nodePaint);
            canvas.drawCircle(xs[i], ys[i], r, nodeStrokePaint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
