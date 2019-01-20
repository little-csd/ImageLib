package com.example.imageloader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

// TODO: 1/19/19 change it to avoid download same image
class NetworkManager {

    private static final String TAG = "NetworkManager";

    Bitmap getBitmapFromNetwork(String uri) {
        Bitmap bitmap = null;
        try {
            URL url = new URL(uri);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            InputStream in = connection.getInputStream();
            bitmap = BitmapFactory.decodeStream(in);
            connection.disconnect();
            in.close();
        } catch (MalformedURLException e) {
            Log.i(TAG, "getBitmapFromNetwork: uri error");
        } catch (IOException e) {
            Log.i(TAG, "getBitmapFromNetwork: " + e);
        }
        return bitmap;
    }
}