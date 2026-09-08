package com.giga.tech1000.utils;

import android.graphics.Color;

public class ColorCalculator {

    public static boolean isReadable(int textColor) {
        // Assume dark background (your player is dark-themed)
        int background = Color.BLACK;

        double contrast = calculateContrast(textColor, background);
        return contrast >= 4.5; // WCAG AA for normal text
    }


    private static double calculateContrast(int color1, int color2) {
        double l1 = calculateLuminance(color1) + 0.05;
        double l2 = calculateLuminance(color2) + 0.05;
        return Math.max(l1, l2) / Math.min(l1, l2);
    }

    private static double calculateLuminance(int color) {
        double r = linearize(Color.red(color) / 255.0);
        double g = linearize(Color.green(color) / 255.0);
        double b = linearize(Color.blue(color) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearize(double channel) {
        return channel <= 0.03928
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

}
