package com.example.imageloader;

import android.graphics.Bitmap;
import android.widget.ImageView;

class RequestResult {
    private Bitmap bitmap;
    private ImageView imageView;
    private String uri;
    private ImageView.ScaleType type;

    RequestResult(Bitmap bitmap, ImageView imageView, String uri, ImageView.ScaleType type) {
        this.bitmap = bitmap;
        this.imageView = imageView;
        this.uri = uri;
        this.type = type;
    }


    Bitmap getBitmap() {
        return bitmap;
    }

    ImageView getImageView() {
        return imageView;
    }

    String getUri() {
        return uri;
    }

    ImageView.ScaleType getType() {
        return type;
    }
}
