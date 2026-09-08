package com.giga.tech1000.heartbeatz.views.knobs.extended;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import com.giga.tech1000.heartbeatz.views.knobs.KnobView;

public class PitchKnob extends KnobView {

    public PitchKnob(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDrawText(Canvas canvas, float cx, float cy, float knobRadius) {
        // value already in -12 → +12 (semitones)
        int semitones = Math.round(value);

        Paint.FontMetrics fm = valueTextPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;

        valueTextPaint.setTextSize(knobRadius * 0.32f * 0.8f);

        String text = semitones > 0
                ? "+" + semitones
                : String.valueOf(semitones);

        canvas.drawText(text, cx, textY, valueTextPaint);
    }
}
