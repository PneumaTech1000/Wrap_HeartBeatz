package com.giga.tech1000.heartbeatz.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.giga.tech1000.heartbeatz.R;

/**
 * Professional DSPark spectrum analyzer: dense FFT bars, peak-hold caps,
 * mirrored reflection, and a glowing EQ transfer curve.
 * <p>
 * Input magnitudes are expected normalized to {@code [0, 1]} (panel converts dB).
 * EQ curve samples in {@code [0, 1]} map to −12…+12 dB on the vertical axis.
 */
public class SpectrumView extends View implements Choreographer.FrameCallback {

    private static final int BAR_COUNT = 64;
    private static final float DB_RANGE = 12f;
    private static final float ATTACK = 0.55f;   // rise responsiveness
    private static final float RELEASE = 0.18f;  // fall smoothness
    private static final float PEAK_FALL = 0.012f; // peak-hold decay per frame

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint midLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakCapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();
    private final Path curveFillPath = new Path();
    private final RectF barRect = new RectF();

    private final float[] bars = new float[BAR_COUNT];
    private final float[] peaks = new float[BAR_COUNT];
    private final int[] barColors = new int[BAR_COUNT];

    @Nullable private float[] fftSource;
    @Nullable private float[] eqCurve;

    private boolean hasLiveData;
    private boolean standby;
    private boolean frameScheduled;
    private long lastFrameMs;

    @ColorInt private int primaryColor;
    @ColorInt private int cyanColor;
    @ColorInt private int purpleColor;
    @ColorInt private int greenColor;
    @ColorInt private int surfaceDark;

    private float density = 1f;

    public SpectrumView(Context context) {
        super(context);
        init(context);
    }

    public SpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SpectrumView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        primaryColor = ContextCompat.getColor(context, R.color.hb_primary);
        cyanColor = ContextCompat.getColor(context, R.color.hb_accent_cyan);
        purpleColor = ContextCompat.getColor(context, R.color.hb_accent_purple);
        greenColor = ContextCompat.getColor(context, R.color.hb_accent_green);
        surfaceDark = 0xFF0A0A0D;

        // Frequency-mapped bar colors: bass → purple, mids → primary, highs → cyan
        for (int i = 0; i < BAR_COUNT; i++) {
            float t = i / (float) (BAR_COUNT - 1);
            if (t < 0.45f) {
                barColors[i] = ColorUtils.blendARGB(purpleColor, primaryColor, t / 0.45f);
            } else {
                barColors[i] = ColorUtils.blendARGB(primaryColor, cyanColor, (t - 0.45f) / 0.55f);
            }
        }

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(0.8f));
        gridPaint.setColor(0x14FFFFFF);

        midLinePaint.setStyle(Paint.Style.STROKE);
        midLinePaint.setStrokeWidth(dp(1.2f));
        midLinePaint.setColor(0x33FFFFFF);
        midLinePaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{dp(5f), dp(4f)}, 0f));

        barPaint.setStyle(Paint.Style.FILL);
        peakCapPaint.setStyle(Paint.Style.FILL);
        peakCapPaint.setColor(0xE6FFFFFF);

        reflectionPaint.setStyle(Paint.Style.FILL);

        curveFillPaint.setStyle(Paint.Style.FILL);
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(dp(2.6f));
        curvePaint.setStrokeJoin(Paint.Join.ROUND);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setColor(cyanColor);

        curveGlowPaint.setStyle(Paint.Style.STROKE);
        curveGlowPaint.setStrokeWidth(dp(7f));
        curveGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        curveGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        curveGlowPaint.setColor(ColorUtils.setAlphaComponent(cyanColor, 70));

        nodePaint.setStyle(Paint.Style.FILL);
        nodeRingPaint.setStyle(Paint.Style.STROKE);
        nodeRingPaint.setStrokeWidth(dp(1.5f));
        nodeRingPaint.setColor(0xFF0E0E11);

        setLayerType(LAYER_TYPE_HARDWARE, null);
        setWillNotDraw(false);
    }

    /** Normalized FFT magnitudes 0..1. */
    public void setFftData(@Nullable float[] magnitudes) {
        if (magnitudes == null || magnitudes.length == 0) {
            hasLiveData = false;
            ensureFrames();
            return;
        }
        if (fftSource == null || fftSource.length != magnitudes.length) {
            fftSource = new float[magnitudes.length];
        }
        System.arraycopy(magnitudes, 0, fftSource, 0, magnitudes.length);
        hasLiveData = true;
        standby = false;
        ensureFrames();
    }

    /** EQ curve samples 0..1 (−12 dB … +12 dB). */
    public void setEqCurveData(@Nullable float[] curve) {
        this.eqCurve = curve;
        ensureFrames();
    }

    public void setStandby(boolean standby) {
        this.standby = standby;
        if (standby) hasLiveData = false;
        ensureFrames();
    }

    public void setFftSize(int size) { /* API compat */ }

    public void setSampleRate(int rate) { /* API compat */ }

    private void ensureFrames() {
        if (!frameScheduled && isAttachedToWindow()) {
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        frameScheduled = false;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrameMs == 0 ? 0.016f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;

        advanceBars(dt);
        invalidate();

        if (isAttachedToWindow()) {
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void advanceBars(float dt) {
        float attack = 1f - (float) Math.pow(1f - ATTACK, dt * 60f);
        float release = 1f - (float) Math.pow(1f - RELEASE, dt * 60f);
        float peakFall = PEAK_FALL * (dt * 60f);

        if (hasLiveData && fftSource != null) {
            int n = fftSource.length;
            for (int i = 0; i < BAR_COUNT; i++) {
                float t = i / (float) (BAR_COUNT - 1);
                // Log-ish bin pick emphasizes bass detail
                float logT = (float) Math.pow(t, 0.62);
                int idx = Math.min(n - 1, Math.round(logT * (n - 1)));
                float target = clamp01(fftSource[idx]);
                // Mild high-shelf energy curve for visual balance
                target *= 0.72f + 0.28f * (1f - t * 0.35f);

                float cur = bars[i];
                if (target > cur) {
                    bars[i] = cur + (target - cur) * attack;
                } else {
                    bars[i] = cur + (target - cur) * release;
                }

                if (bars[i] > peaks[i]) {
                    peaks[i] = bars[i];
                } else {
                    peaks[i] = Math.max(0f, peaks[i] - peakFall);
                }
            }
        } else {
            // Idle ambient motion
            double phase = SystemClock.uptimeMillis() / 1000.0;
            for (int i = 0; i < BAR_COUNT; i++) {
                float t = i / (float) BAR_COUNT;
                float wave = (float) (
                        0.22 + 0.18 * Math.sin(phase * 1.4 + i * 0.19)
                                + 0.10 * Math.sin(phase * 2.3 + i * 0.07));
                float eqBoost = 0.55f;
                if (eqCurve != null && eqCurve.length > 0) {
                    int bi = Math.min(eqCurve.length - 1, (int) (t * eqCurve.length));
                    eqBoost = 0.35f + clamp01(eqCurve[bi]) * 0.65f;
                }
                float target = wave * Math.max(0.15f, 1f - t * 0.4f) * eqBoost;
                if (standby) target *= 0.12f;
                bars[i] += (target - bars[i]) * 0.12f;
                peaks[i] = Math.max(bars[i], peaks[i] - peakFall * 0.5f);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrameMs = 0;
        ensureFrames();
    }

    @Override
    protected void onDetachedFromWindow() {
        frameScheduled = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padL = dp(10f);
        float padR = dp(10f);
        float padT = dp(6f);
        float padB = dp(6f);
        float usableW = w - padL - padR;
        // Spectrum occupies upper 78%; subtle reflection below
        float spectrumH = (h - padT - padB) * 0.78f;
        float baseY = padT + spectrumH;
        float midY = padT + spectrumH * 0.5f;

        drawBackgroundGrid(canvas, padL, padR, padT, baseY, usableW, spectrumH, w, h);
        drawBars(canvas, padL, baseY, usableW, spectrumH);
        drawEqCurve(canvas, padL, usableW, midY, spectrumH);
    }

    private void drawBackgroundGrid(
            Canvas canvas, float padL, float padR, float padT, float baseY,
            float usableW, float spectrumH, int w, int h) {
        // Soft vertical vignette at edges
        RadialGradient edge = new RadialGradient(
                w * 0.5f, h * 0.4f, Math.max(w, h) * 0.7f,
                new int[]{0x00000000, 0x33000000},
                new float[]{0.55f, 1f},
                Shader.TileMode.CLAMP);
        vignettePaint.setShader(edge);
        canvas.drawRect(0, 0, w, h, vignettePaint);
        vignettePaint.setShader(null);

        // Horizontal dB lines
        float[] fracs = {0f, 0.25f, 0.5f, 0.75f, 1f};
        for (float f : fracs) {
            float y = padT + spectrumH * f;
            canvas.drawLine(padL, y, w - padR, y, gridPaint);
        }
        // 0 dB dashed
        canvas.drawLine(padL, padT + spectrumH * 0.5f, w - padR, padT + spectrumH * 0.5f, midLinePaint);

        // Vertical decade-ish lines
        for (int i = 1; i < 7; i++) {
            float x = padL + usableW * (i / 7f);
            canvas.drawLine(x, padT, x, baseY, gridPaint);
        }
    }

    private void drawBars(Canvas canvas, float padL, float baseY, float usableW, float spectrumH) {
        float gap = dp(1.2f);
        float barW = Math.max(dp(2f), (usableW - gap * (BAR_COUNT - 1)) / BAR_COUNT);
        float radius = Math.min(barW * 0.45f, dp(3f));

        for (int i = 0; i < BAR_COUNT; i++) {
            float level = clamp01(bars[i]);
            float barH = Math.max(dp(2.5f), level * spectrumH * 0.92f);
            float x = padL + i * (barW + gap);
            float top = baseY - barH;

            int col = barColors[i];
            // Vertical gradient: bright top → deep base
            LinearGradient grad = new LinearGradient(
                    x, top, x, baseY,
                    new int[]{
                            ColorUtils.setAlphaComponent(0xFFFFFFFF, 200),
                            ColorUtils.setAlphaComponent(col, 255),
                            ColorUtils.setAlphaComponent(col, 180),
                            ColorUtils.setAlphaComponent(col, 40)
                    },
                    new float[]{0f, 0.15f, 0.55f, 1f},
                    Shader.TileMode.CLAMP);
            barPaint.setShader(grad);

            barRect.set(x, top, x + barW, baseY);
            canvas.drawRoundRect(barRect, radius, radius, barPaint);

            // Peak hold cap
            float peakH = clamp01(peaks[i]) * spectrumH * 0.92f;
            if (peakH > dp(4f)) {
                float py = baseY - peakH;
                peakCapPaint.setColor(ColorUtils.setAlphaComponent(
                        ColorUtils.blendARGB(col, 0xFFFFFFFF, 0.55f), 220));
                canvas.drawRoundRect(
                        x, py - dp(2f), x + barW, py, dp(1f), dp(1f), peakCapPaint);
            }

            // Soft reflection under baseline
            float refH = barH * 0.28f;
            if (refH > dp(2f)) {
                LinearGradient refGrad = new LinearGradient(
                        x, baseY, x, baseY + refH,
                        new int[]{
                                ColorUtils.setAlphaComponent(col, 55),
                                ColorUtils.setAlphaComponent(col, 0)
                        },
                        null,
                        Shader.TileMode.CLAMP);
                reflectionPaint.setShader(refGrad);
                barRect.set(x, baseY, x + barW, baseY + refH);
                canvas.drawRect(barRect, reflectionPaint);
                reflectionPaint.setShader(null);
            }
        }
        barPaint.setShader(null);
    }

    private void drawEqCurve(Canvas canvas, float padL, float usableW, float midY, float spectrumH) {
        float amp = spectrumH * 0.42f;

        int n = (eqCurve != null && eqCurve.length >= 2) ? eqCurve.length : 2;
        float[] xs = new float[n];
        float[] ys = new float[n];

        if (eqCurve != null && eqCurve.length >= 2) {
            for (int i = 0; i < n; i++) {
                xs[i] = padL + (i / (float) (n - 1)) * usableW;
                float gain01 = clamp01(eqCurve[i]);
                float gainDb = (gain01 * 2f - 1f) * DB_RANGE;
                ys[i] = midY - (gainDb / DB_RANGE) * amp;
            }
        } else {
            xs[0] = padL;
            xs[1] = padL + usableW;
            ys[0] = midY;
            ys[1] = midY;
        }

        curvePath.reset();
        curveFillPath.reset();
        curvePath.moveTo(xs[0], ys[0]);
        curveFillPath.moveTo(xs[0], midY);
        curveFillPath.lineTo(xs[0], ys[0]);

        for (int i = 0; i < n - 1; i++) {
            float cpX = (xs[i] + xs[i + 1]) * 0.5f;
            curvePath.cubicTo(cpX, ys[i], cpX, ys[i + 1], xs[i + 1], ys[i + 1]);
            curveFillPath.cubicTo(cpX, ys[i], cpX, ys[i + 1], xs[i + 1], ys[i + 1]);
        }
        curveFillPath.lineTo(xs[n - 1], midY);
        curveFillPath.close();

        // Soft fill under curve
        LinearGradient fillGrad = new LinearGradient(
                0, midY - amp, 0, midY + amp,
                new int[]{
                        ColorUtils.setAlphaComponent(cyanColor, 55),
                        ColorUtils.setAlphaComponent(cyanColor, 12),
                        ColorUtils.setAlphaComponent(primaryColor, 8)
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        curveFillPaint.setShader(fillGrad);
        canvas.drawPath(curveFillPath, curveFillPaint);
        curveFillPaint.setShader(null);

        // Glow + crisp stroke
        canvas.drawPath(curvePath, curveGlowPaint);
        canvas.drawPath(curvePath, curvePaint);

        // Nodes
        if (eqCurve != null && eqCurve.length >= 2) {
            for (int i = 0; i < n; i++) {
                boolean selected = i == n / 2;
                float r = selected ? dp(5.5f) : dp(3.8f);
                nodePaint.setColor(selected ? 0xFFFFFFFF : cyanColor);
                canvas.drawCircle(xs[i], ys[i], r + dp(2.5f),
                        glowNodePaint(ColorUtils.setAlphaComponent(cyanColor, selected ? 90 : 40)));
                canvas.drawCircle(xs[i], ys[i], r, nodePaint);
                canvas.drawCircle(xs[i], ys[i], r, nodeRingPaint);
            }
        }
    }

    private final Paint tmpGlow = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Paint glowNodePaint(int color) {
        tmpGlow.setStyle(Paint.Style.FILL);
        tmpGlow.setColor(color);
        return tmpGlow;
    }

    private float dp(float v) {
        return v * density;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
