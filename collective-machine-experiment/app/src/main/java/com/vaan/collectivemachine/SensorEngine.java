package com.vaan.collectivemachine;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public final class SensorEngine {
    public interface Callback {
        void onComplete(double[] features, int points);
        void onError(String message);
    }

    private SensorEngine() { }

    public static void captureMotion(Context context, int milliseconds, Callback cb) {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) { cb.onError("Sensor manager unavailable."); return; }
        Sensor sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) { cb.onError("This phone has no accelerometer."); return; }
        List<double[]> points = new ArrayList<>();
        Handler handler = new Handler(Looper.getMainLooper());
        SensorEventListener listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {
                if (event.values.length >= 3) {
                    points.add(new double[]{event.values[0], event.values[1], event.values[2]});
                }
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };
        boolean ok = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME);
        if (!ok) { cb.onError("Could not start accelerometer capture."); return; }
        handler.postDelayed(() -> {
            manager.unregisterListener(listener);
            if (points.size() < 8) cb.onError("Not enough motion samples were captured.");
            else cb.onComplete(SignalEngine.motionFeatures(points), points.size());
        }, Math.max(500, milliseconds));
    }
}
