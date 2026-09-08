package com.giga.tech1000.heartbeatz.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Custom view for displaying audio spectrum (FFT) visualization.
 * Shows real-time frequency response with EQ curve overlay.
 */
public class SpectrumView extends View {

    // Paint objects for drawing
    private Paint gridPaint;
    private Paint spectrumPaint;
    private Paint eqCurvePaint;
    private Paint peakHoldPaint;
    private Paint axisPaint;
    private Paint labelPaint;

    // Data arrays
    private float[] fftMagnitudes; // Normalized FFT magnitudes (0-1)
    private float[] eqCurve; // EQ curve values for each frequency bin
    private float[] peakHold; // Peak hold values for each frequency bin

    // Configuration
    private int fftSize = 1024;
    private int sampleRate = 44100;
    private float decayRate = 0.95f; // How fast peaks decay
    private float peakHoldDecay = 0.995f; // How fast peak hold decays

    // Visual settings
    private static final int GRID_COLOR = Color.parseColor("#40FFFFFF");
    private static final int SPECTRUM_COLOR = Color.parseColor("#40FF6B6B");
    private static final int EQ_CURVE_COLOR = Color.parseColor("#FFFF6B6B");
    private static final int PEAK_HOLD_COLOR = Color.parseColor("#8000BFFF");
    private static final int AXIS_COLOR = Color.parseColor("#80FFFFFF");
    private static final int LABEL_COLOR = Color.parseColor("#FFFFFFFF");

    public SpectrumView(Context context) {
        super(context);
        init();
    }

    public SpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpectrumView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize paints
        gridPaint = new Paint();
        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);

        spectrumPaint = new Paint();
        spectrumPaint.setColor(SPECTRUM_COLOR);
        spectrumPaint.setStrokeWidth(2f);
        spectrumPaint.setStyle(Paint.Style.FILL);
        spectrumPaint.setAntiAlias(true);

        eqCurvePaint = new Paint();
        eqCurvePaint.setColor(EQ_CURVE_COLOR);
        eqCurvePaint.setStrokeWidth(3f);
        eqCurvePaint.setStyle(Paint.Style.STROKE);
        eqCurvePaint.setAntiAlias(true);

        peakHoldPaint = new Paint();
        peakHoldPaint.setColor(PEAK_HOLD_COLOR);
        peakHoldPaint.setStrokeWidth(2f);
        peakHoldPaint.setStyle(Paint.Style.STROKE);
        peakHoldPaint.setAntiAlias(true);

        axisPaint = new Paint();
        axisPaint.setColor(AXIS_COLOR);
        axisPaint.setStrokeWidth(2f);
        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setAntiAlias(true);

        labelPaint = new Paint();
        labelPaint.setColor(LABEL_COLOR);
        labelPaint.setTextSize(14f);
        labelPaint.setAntiAlias(true);

        // Initialize data arrays
        fftMagnitudes = new float[fftSize / 2]; // Only need positive frequencies
        eqCurve = new float[fftSize / 2];
        peakHold = new float[fftSize / 2];

        // Initialize arrays to zero
        clearData();
    }

    /**
     * Clear all data arrays
     */
    public void clearData() {
        for (int i = 0; i < fftMagnitudes.length; i++) {
            fftMagnitudes[i] = 0f;
            eqCurve[i] = 0f;
            peakHold[i] = 0f;
        }
        invalidate(); // Trigger redraw
    }

    /**
     * Set FFT magnitude data (normalized 0-1)
     */
    public void setFftData(float[] magnitudes) {
        if (magnitudes == null) return;

        // Copy data, ensuring we don't exceed array bounds
        int length = Math.min(magnitudes.length, fftMagnitudes.length);
        System.arraycopy(magnitudes, 0, fftMagnitudes, 0, length);

        // Update peak hold
        for (int i = 0; i < length; i++) {
            if (fftMagnitudes[i] > peakHold[i]) {
                peakHold[i] = fftMagnitudes[i];
            } else {
                peakHold[i] *= peakHoldDecay;
            }
        }

        // Apply decay to spectrum for smooth fading
        for (int i = 0; i < fftMagnitudes.length; i++) {
            fftMagnitudes[i] *= decayRate;
        }

        invalidate(); // Trigger redraw
    }

    /**
     * Set EQ curve data (normalized 0-1, representing gain adjustment)
     */
    public void setEqCurveData(float[] curve) {
        if (curve == null) return;

        int length = Math.min(curve.length, eqCurve.length);
        System.arraycopy(curve, 0, eqCurve, 0, length);
        invalidate(); // Trigger redraw
    }

    /**
     * Set the FFT size (must be power of 2)
     */
    public void setFftSize(int size) {
        // Ensure it's a power of 2
        fftSize = Integer.highestOneBit(size);
        if (fftSize < 32) fftSize = 32; // Minimum reasonable size

        // Reinitialize data arrays
        fftMagnitudes = new float[fftSize / 2];
        eqCurve = new float[fftSize / 2];
        peakHold = new float[fftSize / 2];
        clearData();
    }

    /**
     * Set the audio sample rate
     */
    public void setSampleRate(int rate) {
        this.sampleRate = rate;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        // Draw background (optional - could be transparent)
        // canvas.drawColor(Color.TRANSPARENT);

        // Calculate drawing area (leave margin for labels)
        int marginBottom = 40;
        int marginLeft = 60;
        int marginTop = 20;
        int marginRight = 20;

        int graphWidth = width - marginLeft - marginRight;
        int graphHeight = height - marginTop - marginBottom;

        if (graphWidth <= 0 || graphHeight <= 0) return;

        // Draw grid
        drawGrid(canvas, marginLeft, marginTop, graphWidth, graphHeight);

        // Draw axes
        drawAxes(canvas, marginLeft, marginTop, graphWidth, graphHeight);

        // Draw labels
        drawLabels(canvas, marginLeft, marginTop, graphWidth, graphHeight);

        // Draw spectrum (fill area under curve)
        drawSpectrum(canvas, marginLeft, marginTop, graphWidth, graphHeight);

        // Draw EQ curve
        drawEqCurve(canvas, marginLeft, marginTop, graphWidth, graphHeight);

        // Draw peak hold
        drawPeakHold(canvas, marginLeft, marginTop, graphWidth, graphHeight);
    }

    /**
     * Draw frequency and dB grid lines
     */
    private void drawGrid(Canvas canvas, int marginLeft, int marginTop, int width, int height) {
        // Vertical lines (frequency)
        int[] freqLabels = {20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000};
        for (int freq : freqLabels) {
            if (freq >= 20 && freq <= sampleRate / 2) {
                float x = marginLeft + width * getFrequencyX(freq, sampleRate);
                canvas.drawLine(x, marginTop, x, marginTop + height, gridPaint);
            }
        }

        // Horizontal lines (dB)
        int[] dbLabels = {-60, -40, -20, -12, -6, 0, 6, 12};
        for (int db : dbLabels) {
            float y = marginTop + height * getDbY(db);
            canvas.drawLine(marginLeft, y, marginLeft + width, y, gridPaint);
        }
    }

    /**
     * Draw X and Y axes
     */
    private void drawAxes(Canvas canvas, int marginLeft, int marginTop, int width, int height) {
        // Y axis (left)
        canvas.drawLine(marginLeft, marginTop, marginLeft, marginTop + height, axisPaint);

        // X axis (bottom)
        canvas.drawLine(marginLeft, marginTop + height, marginLeft + width, marginTop + height, axisPaint);
    }

    /**
     * Draw frequency and dB labels
     */
    private void drawLabels(Canvas canvas, int marginLeft, int marginTop, int width, int height) {
        // Frequency labels (below X axis)
        int[] freqLabels = {20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000};
        for (int freq : freqLabels) {
            if (freq >= 20 && freq <= sampleRate / 2) {
                float x = marginLeft + width * getFrequencyX(freq, sampleRate);
                String label;
                if (freq >= 1000) {
                    label = (freq / 1000) + "k";
                } else {
                    label = String.valueOf(freq);
                }
                canvas.drawText(label, x - labelPaint.measureText(label) / 2,
                        marginTop + height + 25, labelPaint);
            }
        }

        // dB labels (left of Y axis)
        int[] dbLabels = {-60, -40, -20, -12, 0, 12};
        for (int db : dbLabels) {
            float y = marginTop + height * getDbY(db);
            String label = db + "dB";
            canvas.drawText(label, marginLeft - labelPaint.measureText(label) - 5,
                    y + labelPaint.getTextSize() / 3, labelPaint);
        }
    }

    /**
     * Draw the FFT spectrum as a filled area
     */
    private void drawSpectrum(Canvas canvas, int marginLeft, int marginTop, int width, int height) {
        if (fftMagnitudes == null) return;

        // Create path for the spectrum area
        android.graphics.Path path = new android.graphics.Path();
        boolean firstPoint = true;

        for (int i = 0; i < fftMagnitudes.length; i++) {
            float frequency = getFrequencyForBin(i, fftSize, sampleRate);
            float x = marginLeft + width * getFrequencyX(frequency, sampleRate);
            float y = marginTop + height * (1f - getDbY(fftToDb(fftMagnitudes[i])));

            if (firstPoint) {
                path.moveTo(x, y);
                firstPoint = false;
            } else {
                path.lineTo(x, y);
            }
        }

        // Close the path to fill area
        path.lineTo(marginLeft + width, marginTop + height); // Bottom right
        path.lineTo(marginLeft, marginTop + height); // Bottom left
        path.close();

        canvas.drawPath(path, spectrumPaint);
    }

    /**
     * Draw the EQ curve
     */
    private void drawEqCurve(Canvas canvas, int marginLeft, int marginTop, int width, int height) {
        if (eqCurve == null) return;

        boolean firstPoint = true;
        float prevX = 0, prevY = 0;

        for (int i = 0; i < eqCurve.length; i++) {
            float frequency = getFrequencyForBin(i, fftSize, sampleRate);
            float x = marginLeft + width * getFrequencyX(frequency, sampleRate);
            // Convert EQ curve (0-1 where 0.5 is flat) to dB for display
            float db = (eqCurve[i] - 0.5f) * 60f; // +/- 30dB range
            float y = marginTop + height * (1f - getDbY(db));

            if (firstPoint) {
                prevX = x;
                prevY = y;
                firstPoint = false;
            } else {
                // Draw line segment
                canvas.drawLine(prevX, prevY, x, y, eqCurvePaint);
                prevX = x;
                prevY = y;
            }
        }
    }

    /**
     * Draw peak hold indicators
     */
    private void drawPeakHold(Canvas canvas, int marginLeft, int marginTop, int width, int height) {
        if (peakHold == null) return;

        boolean firstPoint = true;
        float prevX = 0, prevY = 0;

        for (int i = 0; i < peakHold.length; i++) {
            float frequency = getFrequencyForBin(i, fftSize, sampleRate);
            float x = marginLeft + width * getFrequencyX(frequency, sampleRate);
            float y = marginTop + height * (1f - getDbY(fftToDb(peakHold[i])));

            if (firstPoint) {
                prevX = x;
                prevY = y;
                firstPoint = false;
            } else {
                // Draw line segment
                canvas.drawLine(prevX, prevY, x, y, peakHoldPaint);
                prevX = x;
                prevY = y;
            }
        }
    }

    /**
     * Convert linear magnitude to dB
     */
    private float fftToDb(float magnitude) {
        if (magnitude <= 0f) return -80f; // Avoid log(0)
        return 20f * (float) Math.log10(magnitude);
    }

    /**
     * Get normalized X position for a frequency (0-1, logarithmic scale)
     */
    private float getFrequencyX(float frequency, int sampleRate) {
        // Logarithmic scale from 20Hz to sampleRate/2
        float minFreq = 20f;
        float maxFreq = sampleRate / 2f;

        if (frequency <= minFreq) return 0f;
        if (frequency >= maxFreq) return 1f;

        float logMin = (float) Math.log10(minFreq);
        float logMax = (float) Math.log10(maxFreq);
        float logFreq = (float) Math.log10(frequency);

        return (logFreq - logMin) / (logMax - logMin);
    }

    /**
     * Get normalized Y position for dB value (0-1, where 0 is top, 1 is bottom)
     * Maps from -80dB to +20dB
     */
    private float getDbY(float db) {
        // Clamp to reasonable range
        db = Math.max(-80f, Math.min(20f, db));
        // Convert to 0-1 range (0 = top, 1 = bottom)
        return (db + 80f) / 100f;
    }

    /**
     * Get frequency for a FFT bin
     */
    private float getFrequencyForBin(int bin, int fftSize, int sampleRate) {
        return bin * sampleRate / (float) fftSize;
    }
}