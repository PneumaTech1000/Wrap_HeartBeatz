package com.giga.tech1000.heartbeatz.utils;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.giga.tech1000.heartbeatz.R;

public final class ImageLoader {

    public static void load(ImageView view, Object source) {
        RequestManager rm = Glide.with(view);

        RequestBuilder<Drawable> thumb =
                rm.load(source)
                        .override(150)
                        .centerCrop();

        rm.load(source)
                .thumbnail(thumb.clone())
                .override(300)
                .placeholder(R.mipmap.ic_launcher_foreground)
                .centerCrop()
                .error(R.mipmap.ic_launcher_foreground)
                .fallback(R.mipmap.ic_launcher_foreground)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .into(view);
    }
}
