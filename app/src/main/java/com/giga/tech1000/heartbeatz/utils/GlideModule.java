package com.giga.tech1000.heartbeatz.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

@com.bumptech.glide.annotation.GlideModule
public final class GlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context,
                             @NonNull GlideBuilder builder) {

        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .format(DecodeFormat.PREFER_RGB_565) // 50% less memory
                        .disallowHardwareConfig()            // safer for transforms
        );
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}

