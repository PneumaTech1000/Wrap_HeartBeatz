package com.giga.tech1000.extensions;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VerticalSeekBar extends View {

    private Paint trackPaint;
    private Paint progressPaint;
    private Paint thumbPaint;
    private Drawable thumbDrawable;
    
    private final RectF trackRect = new RectF();
    private final RectF progressRect = new RectF();

    private int min = 0;
    private int max = 100;
    private int progress = 0;

    private float trackWidth;
    private float thumbRadius;

    private OnSeekBarChangeListener listener;

    public interface OnSeekBarChangeListener {
        void onProgressChanged(VerticalSeekBar seekBar, int progress, boolean fromUser);
        void onStartTrackingTouch(VerticalSeekBar seekBar);
        void onStopTrackingTouch(VerticalSeekBar seekBar);
    }

    public VerticalSeekBar(Context context) {
        super(context);
        init(context, null);
    }

    public VerticalSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.FILL);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.FILL);

        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setShadowLayer(dpToPx(4), 0, dpToPx(2), 0x40000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        float defaultTrackWidth = dpToPx(4);
        float defaultThumbRadius = dpToPx(10);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.VerticalSeekBar);
            
            // Try legacy names first for backward compatibility
            int trackColor = a.getColor(R.styleable.VerticalSeekBar_vs_track_color, 
                    a.getColor(R.styleable.VerticalSeekBar_vs_trackColor, 0xFFE0E0E0));
            int progressColor = a.getColor(R.styleable.VerticalSeekBar_vs_progress_color, 
                    a.getColor(R.styleable.VerticalSeekBar_vs_progressColor, 0xFF6200EE));
            int thumbColor = a.getColor(R.styleable.VerticalSeekBar_vs_thumb_tint, 
                    a.getColor(R.styleable.VerticalSeekBar_vs_thumbColor, progressColor));
            
            trackWidth = a.getDimension(R.styleable.VerticalSeekBar_vs_track_width, 
                    a.getDimension(R.styleable.VerticalSeekBar_vs_trackWidth, defaultTrackWidth));
            
            thumbDrawable = a.getDrawable(R.styleable.VerticalSeekBar_vs_thumb);
            thumbRadius = a.getDimension(R.styleable.VerticalSeekBar_vs_thumbRadius, defaultThumbRadius);
            progress = a.getInt(R.styleable.VerticalSeekBar_vs_progress, 0);
            min = a.getInt(R.styleable.VerticalSeekBar_vs_min, 0);
            max = a.getInt(R.styleable.VerticalSeekBar_vs_max, 100);

            trackPaint.setColor(trackColor);
            progressPaint.setColor(progressColor);
            thumbPaint.setColor(thumbColor);
            a.recycle();
        } else {
            trackPaint.setColor(0xFFE0E0E0);
            progressPaint.setColor(0xFF6200EE);
            thumbPaint.setColor(0xFF6200EE);
            trackWidth = defaultTrackWidth;
            thumbRadius = defaultThumbRadius;
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minWidth = (int) (thumbRadius * 2 + getPaddingLeft() + getPaddingRight());
        int minHeight = (int) (dpToPx(100) + getPaddingTop() + getPaddingBottom());

        int width = resolveSizeAndState(minWidth, widthMeasureSpec, 0);
        int height = resolveSizeAndState(minHeight, heightMeasureSpec, 0);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        float centerX = width / 2;
        float top = thumbRadius;
        float bottom = height - thumbRadius;
        float usableHeight = bottom - top;

        // Draw track
        trackRect.set(centerX - trackWidth / 2, top, centerX + trackWidth / 2, bottom);
        canvas.drawRoundRect(trackRect, trackWidth / 2, trackWidth / 2, trackPaint);

        // Calculate thumb Y based on progress (Bottom is 0, Top is Max)
        float progressRatio = (float) (progress - min) / (max - min);
        float thumbY = bottom - (progressRatio * usableHeight);

        // Draw progress
        progressRect.set(centerX - trackWidth / 2, thumbY, centerX + trackWidth / 2, bottom);
        canvas.drawRoundRect(progressRect, trackWidth / 2, trackWidth / 2, progressPaint);

        // Draw thumb
        if (thumbDrawable != null) {
            int left = (int) (centerX - thumbRadius);
            int right = (int) (centerX + thumbRadius);
            int topThumb = (int) (thumbY - thumbRadius);
            int bottomThumb = (int) (thumbY + thumbRadius);
            thumbDrawable.setBounds(left, topThumb, right, bottomThumb);
            thumbDrawable.draw(canvas);
        } else {
            canvas.drawCircle(centerX, thumbY, thumbRadius, thumbPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        float y = event.getY();
        float top = thumbRadius;
        float bottom = getHeight() - thumbRadius;
        float usableHeight = bottom - top;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                performClick();
                if (listener != null) listener.onStartTrackingTouch(this);
                updateProgress(y, top, bottom, usableHeight);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                updateProgress(y, top, bottom, usableHeight);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) listener.onStopTrackingTouch(this);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateProgress(float y, float top, float bottom, float usableHeight) {
        float clampedY = Math.max(top, Math.min(y, bottom));
        float progressRatio = (bottom - clampedY) / usableHeight;
        int newProgress = min + Math.round(progressRatio * (max - min));

        if (newProgress != progress) {
            progress = newProgress;
            if (listener != null) {
                listener.onProgressChanged(this, progress, true);
            }
            invalidate();
        }
    }

    public void setProgress(int progress) {
        this.progress = Math.max(min, Math.min(progress, max));
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public void setMin(int min) {
        this.min = min;
        invalidate();
    }

    public int getMin() {
        return min;
    }

    public void setMax(int max) {
        this.max = max;
        invalidate();
    }

    public int getMax() {
        return max;
    }

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener) {
        this.listener = listener;
    }

    public void setColors(int trackColor, int progressColor) {
        trackPaint.setColor(trackColor);
        progressPaint.setColor(progressColor);
        thumbPaint.setColor(progressColor);
        invalidate();
    }
}
