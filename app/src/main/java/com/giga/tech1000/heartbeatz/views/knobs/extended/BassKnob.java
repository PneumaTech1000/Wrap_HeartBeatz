package com.giga.tech1000.heartbeatz.views.knobs.extended;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import com.giga.tech1000.heartbeatz.views.knobs.KnobView;

public class BassKnob extends KnobView {

    public BassKnob(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDrawText(Canvas canvas, float cx, float cy, float knobRadius) {
        float normalized = getNormalizedValue();
        int percent = Math.round(normalized * 100);

        Paint.FontMetrics fm = valueTextPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;

        valueTextPaint.setTextSize(knobRadius * 0.32f);
        canvas.drawText(percent + "%", cx, textY, valueTextPaint);
    }
}
