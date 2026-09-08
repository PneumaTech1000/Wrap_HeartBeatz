package com.giga.tech1000.heartbeatz.views.knobs;

import android.content.Context;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.giga.tech1000.heartbeatz.R;

import java.util.Random;

public class DiscoveryIndicatorManager {
    private final FrameLayout container;
    private final Random random = new Random();
    private final Context context;

    public DiscoveryIndicatorManager(FrameLayout container) {
        this.container = container;
        this.context = container.getContext();
    }

    public void showDeviceFound(String deviceName) {
        // 1. Inflate the indicator layout
        View node = android.view.LayoutInflater.from(context).inflate(R.layout.party_found_item_float, container, false);
        
        // 2. Set the device name
        android.widget.TextView tvName = node.findViewById(R.id.partyFoundText);
        if (tvName != null) {
            tvName.setText(deviceName);
        }

        // 3. Set Random Position within the container
        // We need to measure the view first or use a reasonable guess for initial size
        node.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = node.getMeasuredWidth();
        int height = node.getMeasuredHeight();

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );

        // Ensure it doesn't clip the edges
        int containerWidth = container.getWidth();
        int containerHeight = container.getHeight();
        
        if (containerWidth > 0 && containerHeight > 0) {
            int marginX = random.nextInt(Math.max(1, containerWidth - width));
            int marginY = random.nextInt(Math.max(1, containerHeight - height));
            params.leftMargin = marginX;
            params.topMargin = marginY;
        } else {
            // Fallback if container not yet measured
            params.gravity = android.view.Gravity.CENTER;
        }
        
        node.setLayoutParams(params);
        node.setScaleX(0f);
        node.setScaleY(0f);
        node.setAlpha(0f);

        container.addView(node);

        // 4. Modern Animate In -> Stay -> Animate Out
        node.animate()
                .scaleX(1.1f).scaleY(1.1f).alpha(1f)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> {
                    // Settle back to normal size
                    node.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();

                    // Disappear after 3 seconds
                    node.postDelayed(() -> {
                        if (node.getParent() != null) {
                            node.animate()
                                    .scaleX(0f).scaleY(0f).alpha(0f)
                                    .setDuration(400)
                                    .withEndAction(() -> container.removeView(node))
                                    .start();
                        }
                    }, 3000);
                }).start();
    }
}

