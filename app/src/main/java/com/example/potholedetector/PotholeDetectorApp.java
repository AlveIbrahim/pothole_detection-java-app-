package com.example.potholedetector;

import android.app.Application;
import android.content.Context;
import androidx.multidex.MultiDex;

public class PotholeDetectorApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize OpenCV here if needed
        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            android.util.Log.e("OpenCV", "OpenCV initialization failed");
        } else {
            android.util.Log.d("OpenCV", "OpenCV initialization successful");
        }
    }
}