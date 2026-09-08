package com.giga.tech1000.heartbeatz.utils;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

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
                .placeholder(com.giga.tech1000.icons_pack.R.drawable.album_24px)
                .centerCrop()
                .error(com.giga.tech1000.icons_pack.R.drawable.album_24px)
                .fallback(com.giga.tech1000.icons_pack.R.drawable.album_24px)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .into(view);
    }
}
