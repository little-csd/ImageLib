package com.example.imageloader;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

// TODO: 1/19/19 change disk manager to lru
class DiskCacheManager extends BaseCacheManager{

    private static final String TAG = "DiskCacheManager";
    private String diskCacheDir;

    private SharedPreferences prefs;

    DiskCacheManager(String diskCacheDir, SharedPreferences prefs) {
        this.diskCacheDir = diskCacheDir;
        this.prefs = prefs;

        if (prefs == null)
            Log.i(TAG, "DiskCacheManager: error! prefs is null");
        if (diskCacheDir == null)
            Log.i(TAG, "DiskCacheManager: error! can not obtain diskCacheDir");
        else
            Log.i(TAG, "DiskCacheManager: " + diskCacheDir);
    }

    void putBitmap(String uri, Bitmap bitmap, int sampleSize) {
        if (diskCacheDir == null) {
            Log.i(TAG, "putBitmap: error! diskCacheDir is null");
            return;
        }
        String dst = diskCacheDir + String.valueOf(uri.hashCode() + sampleSize) + ".jpg";
        File file = new File(dst);
        if (file.exists()) {
            Log.i(TAG, "putBitmap: file has existed: " + uri);
            return;
        }
        try {
            FileOutputStream os = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.flush();
            os.close();
        } catch (IOException e) {
            Log.i(TAG, "putBitmap: " + e);
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(uri, dst);
        editor.apply();
    }

    Bitmap getBitmap(String uri, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;

        String dst = prefs.getString(uri, null);
        if (dst == null) return null;
        File file = new File(dst);
        if (!file.exists()) {
            Log.i(TAG, "getBitmap: error! File is not exist");
            return null;
        }
        return BitmapFactory.decodeFile(dst, options);
    }

    int getSampleSize(@NonNull String uri, int reqWidth, int reqHeight) {
        String str = prefs.getString(uri, null);
        if (str == null) return -1;
        if (reqHeight == 0 || reqWidth == 0) return 1;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(uri, options);
        return calculateInSampleSize(options, reqWidth, reqHeight);
    }

    private int calculateInSampleSize(BitmapFactory.Options options,
                                      int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while (halfHeight / inSampleSize >= reqHeight &&
                    halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}