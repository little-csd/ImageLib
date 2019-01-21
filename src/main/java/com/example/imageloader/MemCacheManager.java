package com.example.imageloader;

import android.graphics.Bitmap;
import android.util.LruCache;

class MemCacheManager extends BaseCacheManager{
    private LruCache<String, Bitmap> memCache;

    MemCacheManager() {
        int maxMemory = (int) Runtime.getRuntime().maxMemory() / 1024;
        int cacheSize = maxMemory / 8;
        memCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };
    }

    void putBitmap(String uri, Bitmap bitmap, int sampleSize) {
        String key = String.valueOf(uri.hashCode() + sampleSize);
        if (memCache.get(key) == null)
            memCache.put(key, bitmap);
    }

    Bitmap getBitmap(String uri, int sampleSize) {
        String key = String.valueOf(uri.hashCode() + sampleSize);
        return memCache.get(key);
    }

    void reSize(int size) {
        memCache.resize(size);
    }
}