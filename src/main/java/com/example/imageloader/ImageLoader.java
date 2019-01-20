package com.example.imageloader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.ImageView;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageLoader {

    private static final String TAG = "ImageLoader";

    private static final int IMAGE_TAG = R.string.app_name;
    private static final int REQUEST_RESULT = 1;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2;
    private static final long ALIVE_TIME = 10L;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Thread Pool #" + mCount.getAndIncrement());
        }
    };
    private static final BlockingDeque<Runnable> sPoolWorkQueue =
            new LinkedBlockingDeque<>(128);
    private static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE,
            ALIVE_TIME,
            TimeUnit.SECONDS,
            sPoolWorkQueue,
            sThreadFactory
    );
    private static ImageLoader loader = null;
    private MemCacheManager memCacheManager;
    //private DiskCacheManager diskCacheManager;
    private NetworkManager networkManager;
    private BitmapAdjust bitmapAdjust;
    @SuppressLint("HandlerLeak")
    private Handler mainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            RequestResult result = (RequestResult) msg.obj;
            ImageView imageView = result.getImageView();
            imageView.setScaleType(result.getType());
            String uri = (String) imageView.getTag(IMAGE_TAG);
            if (uri.equals(result.getUri())) {
                imageView.setImageBitmap(result.getBitmap());
            } else {
                Log.d(TAG, "Wrong happened that the uri can not match ImageView.");
            }
        }
    };

    private ImageLoader(@NonNull Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences("image", Context.MODE_PRIVATE);
        String diskCacheDir = getDiskCacheDir(context) + "/";
        memCacheManager = new MemCacheManager();
        diskCacheManager = new DiskCacheManager(diskCacheDir, prefs);
        networkManager = new NetworkManager();
        bitmapAdjust = new BitmapAdjust();
    }

    public static ImageLoader with(Context context) {
        if (loader == null)
            loader = new ImageLoader(context);
        return loader;
    }

    private String getDiskCacheDir(Context context) {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            return context.getExternalCacheDir().getPath();
        } else {
            return context.getCacheDir().getPath();
        }
    }

    public void bindBitmap(String uri, ImageView imageView) {
        bindBitmap(uri, imageView, imageView.getWidth(),
                imageView.getHeight(), ImageView.ScaleType.FIT_CENTER);
    }

    /**
     * This method should run On UI thread or it will cause exception.
     * It will search in the memory first, and then the disk, last the network
     *
     * @param uri       the image's uri
     * @param imageView the imageView that will be bind
     * @param reqWidth  the width that is requested
     * @param reqHeight the height that is requested
     */
    public void bindBitmap(String uri, ImageView imageView,
                           int reqWidth, int reqHeight, ImageView.ScaleType type) {
        imageView.setTag(IMAGE_TAG, uri);
        if (Looper.getMainLooper() != Looper.myLooper())
            throw new RuntimeException("You should bind bitmap in UI Thread");

        THREAD_POOL_EXECUTOR.execute(() -> {
            Bitmap mBitmap = loadBitmap(uri, reqWidth, reqHeight);
            if (mBitmap != null) {
                RequestResult result = new RequestResult(mBitmap, imageView, uri, type);
                mainHandler.obtainMessage(REQUEST_RESULT, result).sendToTarget();
            }
        });
    }

    /**
     * This method should not be used in UI thread
     * It may need http request
     *
     * @param uri       the bitmap's uri
     * @param reqWidth  the width that is requested
     * @param reqHeight the height that is requested
     * @return Bitmap
     */
    public Bitmap loadBitmap(String uri, int reqWidth, int reqHeight) {
        if (Looper.getMainLooper() == Looper.myLooper())
            throw new RuntimeException("You should not load bitmap in UI Thread");

        int sampleSize = diskCacheManager.getSampleSize(uri, reqWidth, reqHeight);
        Bitmap bitmap = memCacheManager.getBitmapFromMemory(uri, sampleSize);
        if (bitmap != null) {
            Log.d(TAG, "loadBitmap: load bitmap from memory cache succeed.");
            return bitmap;
        }

        bitmap = diskCacheManager.getBitmapFromDisk(uri, sampleSize);
        if (bitmap != null) {
            Log.d(TAG, "loadBitmap: load bitmap from disk cache succeed.");
            memCacheManager.putBitmapIntoCache(uri, bitmap, sampleSize);
            return bitmap;
        }

        bitmap = networkManager.getBitmapFromNetwork(uri);
        if (bitmap != null) {
            Log.d(TAG, "loadBitmap: load bitmap from network succeed.");
            diskCacheManager.putBitmapIntoDisk(uri, bitmap);
            sampleSize = diskCacheManager.getSampleSize(uri, reqWidth, reqHeight);
            memCacheManager.putBitmapIntoCache(uri, bitmap, sampleSize);
            return bitmap;
        }

        Log.d(TAG, "loadBitmap: load bitmap failure of Uri: " + uri);
        return null;
    }

    /**
     * This method can resize the bitmap to any size it is requested
     *
     * @param bitmap    the bitmap wait to be resize
     * @param reqWidth  the width bitmap would be
     * @param reqHeight the height bitmap would be
     * @return Bitmap after resizing
     */
    public Bitmap resizeBitmap(Bitmap bitmap, int reqWidth, int reqHeight) {
        if (reqHeight == 0 || reqWidth == 0 || bitmap == null) return bitmap;
        return bitmapAdjust.resizeBitmap(bitmap, reqWidth, reqHeight);
    }

    /**
     * This method can rotate the bitmap for any rotate it is requested
     *
     * @param bitmap the bitmap wait to be rotate
     * @param angle  the bitmap's rotate angle, it should be degrees
     * @return Bitmap after rotate
     */
    public Bitmap rotateBitmap(Bitmap bitmap, float angle) {
        if (bitmap == null || Math.abs(angle) < 0.01) return bitmap;
        return bitmapAdjust.rotateBitmap(bitmap, angle);
    }

    /**
     * This method can crop a bitmap.
     * Remember that the size should not be out of range
     * or it will cause an IllegalArgumentException.
     */
    public Bitmap cropBitmap(Bitmap bitmap, int left, int right, int top, int bottom) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (left > right) {
            int t = right;
            right = left;
            left = t;
        }
        if (top > bottom) {
            int t = bottom;
            bottom = top;
            top = t;
        }
        if (left < 0 || top < 0 || right > width || height > bottom)
            throw new IllegalArgumentException("The argument is out of range!");
        return bitmapAdjust.cropBitmap(bitmap, left, right, top, bottom);
    }

    /**
     * This methods can transform the bitmap to a circle one
     *
     * @param bitmap the bitmap wait to be cut
     * @param radius the radius which should not out of range
     * @return Bitmap after cut
     */
    public Bitmap circleBitmap(Bitmap bitmap, int radius) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int mRadius = (width < height ? width : height) / 2;
        if (mRadius < 0 || mRadius < radius)
            throw new IllegalArgumentException("The angument is out of range!");
        return bitmapAdjust.circleBitmap(bitmap, radius);
    }
}