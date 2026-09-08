package com.giga.tech1000.heartbeatz.theme;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class GradientImageView extends AppCompatImageView {

    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int paletteColor = 0x00000000;
    private boolean hasColor = false;

    public GradientImageView(Context context) {
        super(context);
        init();
    }

    public GradientImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GradientImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // We draw overlay after the image, GPU layer is fine
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Supply the color you computed with Palette (or default).
     * Example: view.setPaletteColor(p.getDominantColor(defaultColor));
     */
    public void setPaletteColor(int color) {
        this.paletteColor = color;
        this.hasColor = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!hasColor) return;

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // percentages measured from bottom:
        // bottom solid area height: 7%  -> solid region
        // fade starts at 20% from bottom (transparent) and ends at 7% from bottom (opaque)
        final float solidPct = 0.07f;   // 7%
        final float topFadePct = 0.2f; // 20%

        float ySolidStart = h * (1f - solidPct);   // e.g., 0.93*h
        float yFadeStart  = h * (1f - topFadePct); // e.g., 0.80*h

        // create gradient shader from transparent (top of blend) -> opaque paletteColor (near bottom)
        @SuppressLint("DrawAllocation") LinearGradient shader = new LinearGradient(
                0, yFadeStart,
                0, ySolidStart,
                new int[] { 0x00FFFFFF, paletteColor }, // transparent -> paletteColor
                new float[] { 0f, 1f },
                Shader.TileMode.CLAMP
        );

        overlayPaint.setShader(shader);
        overlayPaint.setAlpha(255);
        // Draw the gradient from topFade (20% from bottom) down to the bottom.
        // Because shader uses CLAMP, below ySolidStart the color remains paletteColor (solid)
        canvas.drawRect(0, yFadeStart, w, h, overlayPaint);

        // cleanup shader to avoid accidental reuse (optional)
        overlayPaint.setShader(null);
    }
}
