package com.giga.tech1000.utils;

import java.util.Locale;

public class TimeConverter {

    /**
     * Converts milliseconds to a time string formatted as mm:ss or hh:mm:ss if duration exceeds an hour.
     * @param millis Time in milliseconds
     * @return Formatted time string
     */
    public static String formatTime(long millis) {
        if (millis < 0) millis = 0;

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

}

