package com.vaan.collectivemachine;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.ArrayList;
import java.util.List;

/** Short local NAVI sensor snapshot. No location, network or camera data. */
public final class EmbodimentEngine {
    public interface Callback {
        void onComplete(double[] features, String description);
        void onError(String message);
    }

    private EmbodimentEngine() { }

    public static void capture(Context context, int durationMs, Callback cb) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) { cb.onError("Sensor service unavailable."); return; }

        Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor light = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (acc == null && gyro == null && light == null && proximity == null) {
            cb.onError("No supported NAVI sensors found.");
            return;
        }

        HandlerThread thread = new HandlerThread("lain-embodiment");
        thread.start();
        Handler h = new Handler(thread.getLooper());
        List<double[]> accData = new ArrayList<>();
        List<double[]> gyroData = new ArrayList<>();
        double[] ambient = new double[]{0, 0};
        int[] ambientSeen = new int[]{0, 0};

        SensorEventListener listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                synchronized (accData) {
                    if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER && e.values.length >= 3) {
                        accData.add(new double[]{e.values[0], e.values[1], e.values[2]});
                    } else if (e.sensor.getType() == Sensor.TYPE_GYROSCOPE && e.values.length >= 3) {
                        gyroData.add(new double[]{e.values[0], e.values[1], e.values[2]});
                    } else if (e.sensor.getType() == Sensor.TYPE_LIGHT && e.values.length >= 1) {
                        ambient[0] = e.values[0]; ambientSeen[0] = 1;
                    } else if (e.sensor.getType() == Sensor.TYPE_PROXIMITY && e.values.length >= 1) {
                        ambient[1] = e.values[0]; ambientSeen[1] = 1;
                    }
                }
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };

        if (acc != null) sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME, h);
        if (gyro != null) sm.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME, h);
        if (light != null) sm.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL, h);
        if (proximity != null) sm.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL, h);

        h.postDelayed(() -> {
            try {
                sm.unregisterListener(listener);
                double[] f = new double[SignalEngine.FEATURES];
                double[] a;
                double[] g;
                synchronized (accData) {
                    a = accData.size() >= 8 ? SignalEngine.motionFeatures(accData) : new double[SignalEngine.FEATURES];
                    g = gyroData.size() >= 8 ? SignalEngine.motionFeatures(gyroData) : new double[SignalEngine.FEATURES];
                }
                for (int i = 0; i < 38; i++) f[i] = 0.62 * a[i] + 0.38 * g[i];
                f[38] = Math.log1p(Math.max(0, ambient[0]));
                f[39] = ambientSeen[0];
                f[40] = Math.log1p(Math.max(0, ambient[1]));
                f[41] = ambientSeen[1];
                f[42] = Math.log1p(accData.size());
                f[43] = Math.log1p(gyroData.size());
                for (int i = 44; i < f.length; i++) f[i] = a[i] * 0.5 + g[i] * 0.5;
                SignalEngine.normalize(f);
                String desc = "accelerometer " + accData.size() + " samples, gyroscope " + gyroData.size() +
                        ", light " + (ambientSeen[0] == 1 ? String.format(java.util.Locale.US, "%.1f", ambient[0]) : "n/a") +
                        ", proximity " + (ambientSeen[1] == 1 ? String.format(java.util.Locale.US, "%.1f", ambient[1]) : "n/a");
                cb.onComplete(f, desc);
            } catch (Exception e) {
                cb.onError(e.getMessage() == null ? "Embodiment capture failed." : e.getMessage());
            } finally {
                thread.quitSafely();
            }
        }, Math.max(500, durationMs));
    }
}
