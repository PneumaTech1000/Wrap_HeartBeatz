package com.giga.tech1000.heartbeatz.views.knobs.extended;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import com.giga.tech1000.heartbeatz.views.knobs.KnobView;

import java.util.Locale;

public class TempoKnob extends KnobView {

    public TempoKnob(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected float getNormalizedValue() {
        if (value <= 1.0f) {
            // 0.5..1.0 → 0.0..0.5
            return (value - 0.5f) / 0.5f * 0.5f;
        } else {
            // 1.0..2.0 → 0.5..1.0
            return 0.5f + (value - 1.0f) * 0.5f;
        }
    }

    @Override
    protected void onDrawText(Canvas canvas, float cx, float cy, float knobRadius) {
        // value already in 0.5x → 2.0x
        Paint.FontMetrics fm = valueTextPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;

        valueTextPaint.setTextSize(knobRadius * 0.32f * 0.85f);

        canvas.drawText(
                String.format(Locale.getDefault(), "%.2fx", value),
                cx,
                textY,
                valueTextPaint
        );
    }
}
