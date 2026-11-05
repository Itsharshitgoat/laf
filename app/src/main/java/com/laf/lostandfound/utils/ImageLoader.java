package com.laf.lostandfound.utils;

import android.content.Context;
import android.util.Base64;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.laf.lostandfound.R;

public final class ImageLoader {

    private ImageLoader() {
    }

    public static void loadImage(Context context, String url, ImageView imageView) {
        loadImage(context, url, imageView, 0);
    }

    public static void loadImage(Context context, String url, ImageView imageView, int cornerRadiusDp) {
        if (context == null || imageView == null) return;

        if (url == null || url.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.placeholder_image);
            return;
        }

        try {
            Object model = resolveModel(url);
            if (cornerRadiusDp > 0) {
                Glide.with(context)
                        .load(model)
                        .transform(new CenterCrop(), new RoundedCorners(cornerRadiusDp))
                        .placeholder(R.drawable.placeholder_image)
                        .error(R.drawable.placeholder_image)
                        .into(imageView);
            } else {
                Glide.with(context)
                        .load(model)
                        .centerCrop()
                        .placeholder(R.drawable.placeholder_image)
                        .error(R.drawable.placeholder_image)
                        .into(imageView);
            }
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.placeholder_image);
        }
    }

    public static void loadAvatar(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;
        Object model = (url == null || url.trim().isEmpty()) ? R.drawable.ic_person : resolveModel(url);
        try {
            Glide.with(context)
                    .load(model)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(imageView);
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.ic_person);
        }
    }

    private static Object resolveModel(String url) {
        if (url.startsWith("data:image")) {
            String base64Data = url.substring(url.indexOf(',') + 1);
            return Base64.decode(base64Data, Base64.DEFAULT);
        }
        return url.trim();
    }
}
