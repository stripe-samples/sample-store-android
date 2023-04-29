package com.stripe.android.samplestore;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class Common {
    public static Handler handler = new Handler(Looper.getMainLooper());

    public static String STRIPE_KEY = "pk_test_here";

    public static void log(String tag, String message) {
        Log.w(tag, message);
    }

    public static void logError(String tag, String message) {
        Log.e(tag, message);
    }

    public static String webApiUrl() {
        // 10.0.2.2 is the Android emulator's alias to localhost
        return "http://10.0.2.2/stripe-store/";
    }
}
