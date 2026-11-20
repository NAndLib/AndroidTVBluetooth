package com.saihgupr.btcontrol;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Bonds (pairs) with one device and reports the outcome once.
 * Must be used from the main thread; the callback is delivered there.
 */
@SuppressLint("MissingPermission")
final class BluetoothBonder {

    interface Callback {
        void onDone(boolean ok, String message);
    }

    private static final String TAG = "BtBonder";

    private final Context context;
    private final BluetoothDevice device;
    private final long timeoutMs;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::onTimeout;

    private boolean active;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            BluetoothDevice changed = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (!active || changed == null || !device.getAddress().equalsIgnoreCase(changed.getAddress())) {
                return;
            }
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            if (state == BluetoothDevice.BOND_BONDED) {
                done(true, "Paired: " + label(device));
            } else if (state == BluetoothDevice.BOND_NONE) {
                done(false, "Pairing failed for " + label(device)
                        + " (rejected, or the device left pairing mode)");
            }
        }
    };

    BluetoothBonder(Context context, BluetoothDevice device, long timeoutMs, Callback callback) {
        this.context = context.getApplicationContext();
        this.device = device;
        this.timeoutMs = timeoutMs;
        this.callback = callback;
    }

    void start() {
        // Register before createBond() so a fast BOND_BONDED transition is not missed.
        context.registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        active = true;
        handler.postDelayed(timeout, timeoutMs);

        // Inquiry and pairing share the radio; pairing is far more reliable with the scan stopped.
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery();

        Log.i(TAG, "Pairing with " + label(device));
        if (!device.createBond()) {
            // createBond() also returns false when a bond exists or is already in progress.
            int state = device.getBondState();
            if (state == BluetoothDevice.BOND_BONDED) {
                done(true, "Already paired: " + label(device));
            } else if (state != BluetoothDevice.BOND_BONDING) {
                done(false, "Pairing request refused for " + label(device));
            }
        }
    }

    /** Stops waiting without calling back. The system may still finish an in-flight bond. */
    void cancel() {
        release();
    }

    private void onTimeout() {
        done(false, "Timed out while pairing with " + label(device)
                + "; if a confirmation prompt is on screen, accept it");
    }

    private void done(boolean ok, String message) {
        if (!active) return;
        release();
        if (ok) Log.i(TAG, message); else Log.e(TAG, message);
        callback.onDone(ok, message);
    }

    private void release() {
        if (!active) return;
        active = false;
        handler.removeCallbacks(timeout);
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered.
        }
    }

    static String label(BluetoothDevice device) {
        String name = device.getName();
        return (name != null ? name : "(no name)") + " [" + device.getAddress() + "]";
    }
}
