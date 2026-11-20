package com.saihgupr.btcontrol;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs Bluetooth discovery for a fixed duration, restarting the ~12 s inquiry cycles as they end.
 * Must be used from the main thread; callbacks are delivered there.
 */
@SuppressLint("MissingPermission")
final class BluetoothDiscovery {

    interface Listener {
        void onFound(BluetoothDevice device, String name);

        /** The duration elapsed, or the radio refused to restart the scan. Not called after stop(). */
        void onFinished();
    }

    private static final String TAG = "BtDiscovery";

    private final Context context;
    private final long durationMs;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishRunnable = this::finishFromTimer;

    private BluetoothAdapter adapter;
    private long deadline;
    private boolean running;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (!running) return;
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) return;
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                listener.onFound(device, name != null ? name : device.getName());
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    finish(true);
                } else if (!adapter.startDiscovery()) {
                    Log.w(TAG, "Could not restart discovery");
                    finish(true);
                }
            }
        }
    };

    BluetoothDiscovery(Context context, long durationMs, Listener listener) {
        this.context = context.getApplicationContext();
        this.durationMs = durationMs;
        this.listener = listener;
    }

    /**
     * Runtime permissions discovery needs on this Android version that are not yet granted.
     * background: the scan runs with no activity on screen (the adb broadcast). Android 10/11
     * then also need background location, or the Bluetooth service refuses to scan.
     */
    static String[] missingPermissions(Context context, boolean background) {
        String[] needed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed = new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        } else if (background && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            needed = new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION};
        } else {
            needed = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }
        List<String> missing = new ArrayList<>();
        for (String permission : needed) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing.toArray(new String[0]);
    }

    /**
     * Android 11 and older hide every scan result while system Location is off, even with the
     * permission granted; startDiscovery() still "succeeds", so check up front.
     */
    static boolean isBlockedByLocationOff(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false;
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return manager == null || !LocationManagerCompat.isLocationEnabled(manager);
    }

    /** Starts scanning. Callers must have checked permissions and location. Returns false on failure. */
    boolean start() {
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return false;

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(receiver, filter);
        running = true;

        if (adapter.isDiscovering()) adapter.cancelDiscovery();
        if (!adapter.startDiscovery()) {
            finish(false);
            return false;
        }
        deadline = SystemClock.elapsedRealtime() + durationMs;
        handler.postDelayed(finishRunnable, durationMs);
        Log.d(TAG, "Discovery started for " + durationMs + " ms");
        return true;
    }

    /** Stops scanning without calling onFinished(). Safe to call repeatedly. */
    void stop() {
        finish(false);
    }

    boolean isRunning() {
        return running;
    }

    private void finishFromTimer() {
        finish(true);
    }

    private void finish(boolean notify) {
        if (!running) return;
        running = false;
        handler.removeCallbacks(finishRunnable);
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered.
        }
        try {
            if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery();
        } catch (SecurityException e) {
            Log.w(TAG, "cancelDiscovery denied", e);
        }
        if (notify) listener.onFinished();
    }
}
