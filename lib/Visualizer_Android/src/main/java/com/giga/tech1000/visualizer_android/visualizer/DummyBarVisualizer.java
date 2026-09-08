package com.giga.tech1000.visualizer_android.visualizer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Random;

public class DummyBarVisualizer extends View {

    private static final int BAR_COUNT = 5;
    private static final long ANIM_DURATION = 350;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] barHeights = new float[BAR_COUNT];
    private final Random random = new Random();

    private ValueAnimator animator;
    private boolean isPlaying;
    private static final float BAR_STEP = 0.25f; // 25% per update
    private final float[] targetHeights = new float[BAR_COUNT];



    public DummyBarVisualizer(Context context) {
        super(context);
        init();
    }

    public DummyBarVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setColor(0xff7f13ec); // white bars
        paint.setStyle(Paint.Style.FILL);
        startAnimator();
    }

    private void startAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIM_DURATION);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            if (!isPlaying) return;

            for (int i = 0; i < BAR_COUNT; i++) {

                // Occasionally pick a new target
                if (random.nextFloat() < 0.15f) {
                    targetHeights[i] = 0.3f + random.nextFloat() * 0.7f;
                }

                // Smoothly move toward target (25% step)
                barHeights[i] += (targetHeights[i] - barHeights[i]) * BAR_STEP;
            }

            invalidate();
        });
    }


    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;

        this.isPlaying = playing;

        if (playing) {
            if (!animator.isStarted()) animator.start();
        } else {
            animator.cancel();
            Arrays.fill(barHeights, 0.2f);
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        float barWidth = w / (BAR_COUNT * 2f);
        float gap = barWidth;

        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * (barWidth + gap);
            float top = h * (1f - barHeights[i]);
            canvas.drawRoundRect(
                    left,
                    top,
                    left + barWidth,
                    h,
                    barWidth / 2,
                    barWidth / 2,
                    paint
            );
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.cancel();
    }
}

