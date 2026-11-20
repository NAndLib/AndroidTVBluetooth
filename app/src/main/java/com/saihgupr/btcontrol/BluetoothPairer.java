package com.saihgupr.btcontrol;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The ACTION_PAIR flow: find one device by address or name, bond with it, all within one
 * overall timeout. Must be started on the main thread; every callback runs there.
 */
@SuppressLint("MissingPermission")
final class BluetoothPairer {

    interface Callback {
        /** address is the bonded device's address on success, null on failure. */
        void onDone(boolean ok, String message, String address);
    }

    private static final String TAG = "BtPairer";
    private static final long MIN_BOND_MS = 5000;

    private final Context context;
    private final PairTarget target;
    private final long timeoutMs;
    private final Callback callback;
    private final Map<String, String> seen = new LinkedHashMap<>();

    private long deadline;
    private BluetoothDiscovery discovery;
    private boolean done;

    BluetoothPairer(Context context, PairTarget target, long timeoutMs, Callback callback) {
        this.context = context.getApplicationContext();
        this.target = target;
        this.timeoutMs = timeoutMs;
        this.callback = callback;
    }

    void start() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            finish(false, "Bluetooth is off", null);
            return;
        }
        String[] missing = BluetoothDiscovery.missingPermissions(context, true);
        if (missing.length > 0) {
            finish(false, "Missing permission " + missing[0]
                    + " (adb shell pm grant " + context.getPackageName() + " " + missing[0] + ")", null);
            return;
        }

        BluetoothDevice alreadyBonded = findBonded(adapter);
        if (alreadyBonded != null) {
            finish(true, "Already paired: " + BluetoothBonder.label(alreadyBonded), alreadyBonded.getAddress());
            return;
        }

        if (BluetoothDiscovery.isBlockedByLocationOff(context)) {
            finish(false, "Location is off. Android 11 and older hide Bluetooth scan results "
                    + "until it is on (adb shell settings put secure location_mode 3)", null);
            return;
        }

        deadline = SystemClock.elapsedRealtime() + timeoutMs;
        discovery = new BluetoothDiscovery(context, timeoutMs, new BluetoothDiscovery.Listener() {
            @Override
            public void onFound(BluetoothDevice device, String name) {
                onDeviceFound(device, name);
            }

            @Override
            public void onFinished() {
                finish(false, "Did not find " + target + ". Seen: " + PairTarget.describeSeen(seen), null);
            }
        });
        Log.i(TAG, "Scanning for " + target + " (timeout " + timeoutMs + " ms)");
        if (!discovery.start()) {
            finish(false, "Could not start Bluetooth scan", null);
        }
    }

    private void onDeviceFound(BluetoothDevice device, String name) {
        if (done) return;
        String address = device.getAddress();
        if (name != null || !seen.containsKey(address)) {
            seen.put(address, name);
        }
        if (!target.matches(address, name, device.getType())) return;

        discovery.stop();
        long remaining = Math.max(MIN_BOND_MS, deadline - SystemClock.elapsedRealtime());
        new BluetoothBonder(context, device, remaining,
                (ok, message) -> finish(ok, message, ok ? address : null)).start();
    }

    private BluetoothDevice findBonded(BluetoothAdapter adapter) {
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null) return null;
        for (BluetoothDevice device : bonded) {
            if (target.matches(device.getAddress(), device.getName(), device.getType())) {
                return device;
            }
        }
        return null;
    }

    private void finish(boolean ok, String message, String address) {
        if (done) return;
        done = true;
        if (discovery != null) discovery.stop();
        if (ok) Log.i(TAG, message); else Log.e(TAG, message);
        callback.onDone(ok, message, address);
    }
}
