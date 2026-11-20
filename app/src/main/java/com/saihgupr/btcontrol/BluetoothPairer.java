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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Discovers a nearby device by address or name and bonds with it.
 * Must be started on the main thread; every callback runs there, so no locking is needed.
 */
@SuppressLint("MissingPermission")
final class BluetoothPairer {

    interface Callback {
        /** address is the bonded device's address on success, null on failure. */
        void onDone(boolean ok, String message, String address);
    }

    private static final String TAG = "BtPairer";

    private final Context context;
    private final PairTarget target;
    private final long timeoutMs;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, String> seen = new LinkedHashMap<>();

    private BluetoothAdapter adapter;
    private BluetoothDevice bonding;
    private boolean receiverRegistered;
    private boolean done;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                onFound(device, intent.getStringExtra(BluetoothDevice.EXTRA_NAME));
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                onDiscoveryFinished();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                onBondStateChanged(device,
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR));
            }
        }
    };

    private final Runnable timeout = this::onTimeout;

    BluetoothPairer(Context context, PairTarget target, long timeoutMs, Callback callback) {
        this.context = context.getApplicationContext();
        this.target = target;
        this.timeoutMs = timeoutMs;
        this.callback = callback;
    }

    void start() {
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            finish(false, "Bluetooth is off", null);
            return;
        }
        String missing = missingPermission();
        if (missing != null) {
            finish(false, "Missing permission " + missing
                    + " (adb shell pm grant " + context.getPackageName() + " " + missing + ")", null);
            return;
        }

        BluetoothDevice alreadyBonded = findBonded();
        if (alreadyBonded != null) {
            finish(true, "Already paired: " + label(alreadyBonded), alreadyBonded.getAddress());
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        context.registerReceiver(receiver, filter);
        receiverRegistered = true;

        handler.postDelayed(timeout, timeoutMs);
        Log.i(TAG, "Scanning for " + target + " (timeout " + timeoutMs + " ms)");
        if (!adapter.startDiscovery()) {
            finish(false, "Could not start Bluetooth scan"
                    + (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ? " (is Location turned on?)" : ""),
                    null);
        }
    }

    private void onFound(BluetoothDevice device, String broadcastName) {
        if (done || bonding != null || device == null) return;

        String name = broadcastName != null ? broadcastName : device.getName();
        String address = device.getAddress();
        if (name != null || !seen.containsKey(address)) {
            seen.put(address, name);
        }
        Log.d(TAG, "Found " + name + " [" + address + "] type=" + device.getType());

        if (target.matches(address, name, device.getType())) {
            bond(device);
        }
    }

    private void bond(BluetoothDevice device) {
        bonding = device;
        // Inquiry and pairing share the radio; pairing is far more reliable once the scan stops.
        adapter.cancelDiscovery();
        Log.i(TAG, "Pairing with " + label(device));
        if (!device.createBond()) {
            // createBond() also returns false when a bond is already in progress, so re-check.
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                finish(true, "Paired: " + label(device), device.getAddress());
            } else if (device.getBondState() != BluetoothDevice.BOND_BONDING) {
                finish(false, "Pairing request refused for " + label(device), null);
            }
        }
    }

    private void onDiscoveryFinished() {
        if (done || bonding != null) return;
        // One inquiry lasts ~12 s; keep scanning until the overall timeout fires.
        Log.d(TAG, "Scan cycle finished, rescanning");
        if (!adapter.startDiscovery()) {
            finish(false, "Bluetooth scan stopped unexpectedly", null);
        }
    }

    private void onBondStateChanged(BluetoothDevice device, int state) {
        if (done || bonding == null || device == null
                || !bonding.getAddress().equalsIgnoreCase(device.getAddress())) {
            return;
        }
        if (state == BluetoothDevice.BOND_BONDED) {
            finish(true, "Paired: " + label(device), device.getAddress());
        } else if (state == BluetoothDevice.BOND_NONE) {
            finish(false, "Pairing failed for " + label(device)
                    + " (rejected, or the device left pairing mode)", null);
        }
    }

    private void onTimeout() {
        if (done) return;
        if (bonding != null) {
            finish(false, "Timed out while pairing with " + label(bonding)
                    + "; if a confirmation dialog is on screen, accept it", null);
        } else {
            finish(false, "Did not find " + target + ". Seen: " + PairTarget.describeSeen(seen), null);
        }
    }

    private BluetoothDevice findBonded() {
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null) return null;
        for (BluetoothDevice device : bonded) {
            if (target.matches(device.getAddress(), device.getName(), device.getType())) {
                return device;
            }
        }
        return null;
    }

    private String missingPermission() {
        String[] needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        for (String permission : needed) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return permission;
            }
        }
        return null;
    }

    private void finish(boolean ok, String message, String address) {
        if (done) return;
        done = true;
        handler.removeCallbacks(timeout);
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
            receiverRegistered = false;
        }
        if (adapter != null) {
            try {
                if (adapter.isDiscovering()) adapter.cancelDiscovery();
            } catch (SecurityException ignored) {
                // Permission was missing; nothing was started.
            }
        }
        if (ok) Log.i(TAG, message); else Log.e(TAG, message);
        callback.onDone(ok, message, ok ? address : null);
    }

    private static String label(BluetoothDevice device) {
        String name = device.getName();
        return (name != null ? name : "(no name)") + " [" + device.getAddress() + "]";
    }
}
