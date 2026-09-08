package com.giga.tech1000.media_player.statics;

public class ClassManager {
    private static ClassManager instance;

    private Class<?> activity;

    public ClassManager(Class<?> activity) {
        this.activity = activity;
    }

    public static Class<?> getActivity() {
        if (instance == null)
            throw new IllegalStateException("ClassManager not initialized");

        return instance.activity;
    }

    public static void init(Class<?> activity) {
        if (instance == null || instance.activity == null) {
         instance = new ClassManager(activity);
        }
    }
}
