package com.example.imageloader;

import android.graphics.Bitmap;

public abstract class BaseCacheManager {
    abstract void putBitmap(String uri, Bitmap bitmap, int sampleSize);
    abstract Bitmap getBitmap(String uri, int sampleSize);
}
