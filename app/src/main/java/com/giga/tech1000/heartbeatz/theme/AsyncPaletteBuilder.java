package com.giga.tech1000.heartbeatz.theme;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.ColorRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.palette.graphics.Palette;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.theme.interfaces.PaletteStateListener;

import java.util.HashMap;

public class AsyncPaletteBuilder {
    public interface Action {
        void onValueAnimated(int value);
    }

    private enum PALETTE_TYPE {VIBRANT, VIBRANT_DARK, VIBRANT_LIGHT, MUTED, MUTED_DARK, MUTED_LIGHT, DOMINANT}

    private final int ANIM_DURATION = 650;

    private HashMap<PALETTE_TYPE, ValueAnimator> animators = new HashMap<>();
    private HashMap<PALETTE_TYPE, Integer> defColors = new HashMap<>();
    private HashMap<PALETTE_TYPE, Integer> prevColors = new HashMap<>();

    private final PaletteStateListener stateListener;

    private final Context context;

    public AsyncPaletteBuilder(Context cn, PaletteStateListener listener) {
        stateListener = listener;
        context = cn;

        // for vibrant colors
        defColors.put(PALETTE_TYPE.VIBRANT, getColor(R.color.default_vibrant_color));
        defColors.put(PALETTE_TYPE.VIBRANT_DARK, getColor(R.color.default_vibrant_dark_color));
        defColors.put(PALETTE_TYPE.VIBRANT_LIGHT, getColor(R.color.default_vibrant_light_color));

        prevColors.put(PALETTE_TYPE.VIBRANT, getColor(R.color.default_vibrant_color));
        prevColors.put(PALETTE_TYPE.VIBRANT_DARK, getColor(R.color.default_vibrant_dark_color));
        prevColors.put(PALETTE_TYPE.VIBRANT_LIGHT, getColor(R.color.default_vibrant_light_color));

        // for muted colors
        // Muted defaults
        defColors.put(PALETTE_TYPE.MUTED, getColor(R.color.default_muted_color));
        defColors.put(PALETTE_TYPE.MUTED_DARK, getColor(R.color.default_muted_dark_color));
        defColors.put(PALETTE_TYPE.MUTED_LIGHT, getColor(R.color.default_muted_light_color));

        prevColors.put(PALETTE_TYPE.MUTED, getColor(R.color.default_muted_color));
        prevColors.put(PALETTE_TYPE.MUTED_DARK, getColor(R.color.default_muted_dark_color));
        prevColors.put(PALETTE_TYPE.MUTED_LIGHT, getColor(R.color.default_muted_light_color));

        // for dominant color
        defColors.put(PALETTE_TYPE.DOMINANT, getColor(R.color.default_dominant_color));

        prevColors.put(PALETTE_TYPE.DOMINANT, getColor(R.color.default_dominant_color));

    }

    private void onStartAnimation(PALETTE_TYPE type, Action action, int fromColor, int toColor) {
        ValueAnimator animator;
        if (animators.containsKey(type) && animators.get(type) != null) {
            animator = this.animators.get(type);
            if (animator == null) return;
            animator.end();
            animator.removeAllUpdateListeners();
            animator.removeAllListeners();
        }

        animator = getColorAnimator(fromColor, toColor);

        this.animators.put(type, animator);

        animator.addUpdateListener(valueAnimator -> {
            int value = (int) valueAnimator.getAnimatedValue();
            action.onValueAnimated(value);
        });

        animator.start();
    }

    public void onStartAnimation(Bitmap art) {
        if (art != null) {
            Palette.from(art).generate(palette -> {

                // for vibrant colors
                onStartAnimation(PALETTE_TYPE.VIBRANT,
                        value -> {
                            this.stateListener.onUpdateVibrantColor(value);
                            this.prevColors.put(PALETTE_TYPE.VIBRANT, value);
                        },
                        prevColors.get(PALETTE_TYPE.VIBRANT),
                        palette.getVibrantColor(defColors.get(PALETTE_TYPE.VIBRANT)));

                onStartAnimation(PALETTE_TYPE.VIBRANT_DARK,
                        value -> {
                            this.stateListener.onUpdateVibrantDarkColor(value);
                            this.prevColors.put(PALETTE_TYPE.VIBRANT_DARK, value);
                        },
                        prevColors.get(PALETTE_TYPE.VIBRANT_DARK),
                        palette.getDarkVibrantColor(defColors.get(PALETTE_TYPE.VIBRANT_DARK)));

                onStartAnimation(PALETTE_TYPE.VIBRANT_LIGHT,
                        value -> {
                            this.stateListener.onUpdateVibrantLightColor(value);
                            this.prevColors.put(PALETTE_TYPE.VIBRANT_LIGHT, value);
                        },
                        prevColors.get(PALETTE_TYPE.VIBRANT_LIGHT),
                        palette.getLightVibrantColor(defColors.get(PALETTE_TYPE.VIBRANT_LIGHT)));


                // Muted family
                onStartAnimation(PALETTE_TYPE.MUTED,
                        value -> {
                            stateListener.onUpdateMutedColor(value);
                            prevColors.put(PALETTE_TYPE.MUTED, value);
                        },
                        prevColors.get(PALETTE_TYPE.MUTED),
                        palette.getMutedColor(defColors.get(PALETTE_TYPE.MUTED))
                );

                onStartAnimation(PALETTE_TYPE.MUTED_DARK,
                        value -> {
                            stateListener.onUpdateMutedDarkColor(value);
                            prevColors.put(PALETTE_TYPE.MUTED_DARK, value);
                        },
                        prevColors.get(PALETTE_TYPE.MUTED_DARK),
                        palette.getDarkMutedColor(defColors.get(PALETTE_TYPE.MUTED_DARK))
                );

                onStartAnimation(PALETTE_TYPE.MUTED_LIGHT,
                        value -> {
                            stateListener.onUpdateMutedLightColor(value);
                            prevColors.put(PALETTE_TYPE.MUTED_LIGHT, value);
                        },
                        prevColors.get(PALETTE_TYPE.MUTED_LIGHT),
                        palette.getLightMutedColor(defColors.get(PALETTE_TYPE.MUTED_LIGHT))
                );

                // DOMINANT COLOR
                onStartAnimation(PALETTE_TYPE.DOMINANT,
                        value -> {
                            stateListener.onUpdateDominantColor(value);
                            prevColors.put(PALETTE_TYPE.DOMINANT, value);
                        },
                        prevColors.get(PALETTE_TYPE.DOMINANT),
                        palette.getDominantColor(defColors.get(PALETTE_TYPE.DOMINANT))
                );

            });
        } else {
            Palette.from(art).generate(palette -> {

                // for vibrant colors
                onStartAnimation(PALETTE_TYPE.VIBRANT,
                        value -> {
                            this.stateListener.onUpdateVibrantColor(value);
                            this.prevColors.put(PALETTE_TYPE.VIBRANT, value);
                        },
                        prevColors.get(PALETTE_TYPE.VIBRANT),
                        defColors.get(PALETTE_TYPE.VIBRANT)
                );

                onStartAnimation(PALETTE_TYPE.VIBRANT_DARK,
                        value -> {
                            this.stateListener.onUpdateVibrantDarkColor(value);
                            this.prevColors.put(PALETTE_TYPE.VIBRANT_DARK, value);
                        },
                        prevColors.get(PALETTE_TYPE.VIBRANT_DARK),
                        defColors.get(PALETTE_TYPE.VIBRANT_DARK)
                );

                onStartAnimation(PALETTE_TYPE.VIBRANT_LIGHT,
                        value -> {
                            this.stateListener.onUpdateVibrantLightColor(value);
                            this.prevColors.put(PALETTE_TYPE.VIBRANT_LIGHT, value);
                        },
                        prevColors.get(PALETTE_TYPE.VIBRANT_LIGHT),
                        defColors.get(PALETTE_TYPE.VIBRANT_LIGHT)
                );


                // Muted family
                onStartAnimation(PALETTE_TYPE.MUTED,
                        value -> {
                            stateListener.onUpdateMutedColor(value);
                            prevColors.put(PALETTE_TYPE.MUTED, value);
                        },
                        prevColors.get(PALETTE_TYPE.MUTED),
                        defColors.get(PALETTE_TYPE.MUTED)
                );


                onStartAnimation(PALETTE_TYPE.MUTED_DARK,
                        value -> {
                            stateListener.onUpdateMutedDarkColor(value);
                            prevColors.put(PALETTE_TYPE.MUTED_DARK, value);
                        },
                        prevColors.get(PALETTE_TYPE.MUTED_DARK),
                        defColors.get(PALETTE_TYPE.MUTED_DARK)
                );


                onStartAnimation(PALETTE_TYPE.MUTED_LIGHT,
                        value -> {
                            stateListener.onUpdateMutedLightColor(value);
                            prevColors.put(PALETTE_TYPE.MUTED_LIGHT, value);
                        },
                        prevColors.get(PALETTE_TYPE.MUTED_LIGHT),
                        defColors.get(PALETTE_TYPE.MUTED_LIGHT)
                );

                onStartAnimation(PALETTE_TYPE.DOMINANT,
                        value -> {
                            stateListener.onUpdateDominantColor(value);
                            prevColors.put(PALETTE_TYPE.DOMINANT, value);
                        },
                        prevColors.get(PALETTE_TYPE.DOMINANT),
                        defColors.get(PALETTE_TYPE.DOMINANT)
                );


            });

        }
    }


    private ValueAnimator getColorAnimator(int fromColor, int toColor) {
        return ValueAnimator.ofObject(
                new ArgbEvaluator(), new Object[]{Integer.valueOf(fromColor), Integer.valueOf(toColor)});
    }

    private int getColor(@ColorRes int color) {
        return ResourcesCompat.getColor(context.getResources(), color, context.getTheme());
    }


}
