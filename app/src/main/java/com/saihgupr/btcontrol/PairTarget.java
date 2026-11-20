package com.saihgupr.btcontrol;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What an ACTION_PAIR broadcast asked for, plus the matching rules used during discovery.
 * Plain Java (no android.* calls) so it can be unit tested off-device.
 */
final class PairTarget {

    /** Same value as BluetoothDevice.DEVICE_TYPE_LE, kept local so this class stays pure Java. */
    static final int DEVICE_TYPE_LE = 2;

    private static final Pattern ADDRESS = Pattern.compile("([0-9A-F]{2}:){5}[0-9A-F]{2}");
    private static final int MAX_LISTED = 8;

    /** Normalized "AA:BB:CC:DD:EE:FF", or null when matching by name. */
    final String address;
    /** Trimmed device name, or null when matching by address. */
    final String name;

    private PairTarget(String address, String name) {
        this.address = address;
        this.name = name;
    }

    /**
     * Returns null when neither extra is usable, or when an address was given but is malformed
     * (a typo should fail loudly rather than fall back to name matching).
     */
    static PairTarget parse(String rawAddress, String rawName) {
        String address = trimToNull(rawAddress);
        String name = trimToNull(rawName);

        if (address != null) {
            address = address.toUpperCase(Locale.ROOT).replace('-', ':');
            if (!ADDRESS.matcher(address).matches()) {
                return null;
            }
            return new PairTarget(address, null);
        }
        return name != null ? new PairTarget(null, name) : null;
    }

    boolean matches(String foundAddress, String foundName, int deviceType) {
        if (address != null) {
            return address.equalsIgnoreCase(foundAddress);
        }
        // Skip LE-only devices: Sony headsets expose a same-named BLE twin that carries no audio.
        return deviceType != DEVICE_TYPE_LE && foundName != null && name.equalsIgnoreCase(foundName);
    }

    /** Human readable list of discovered devices (address -> name), for the failure message. */
    static String describeSeen(Map<String, String> seen) {
        if (seen.isEmpty()) {
            return "nothing found";
        }
        StringBuilder sb = new StringBuilder();
        int listed = 0;
        for (Map.Entry<String, String> entry : seen.entrySet()) {
            if (listed == MAX_LISTED) {
                sb.append(", +").append(seen.size() - MAX_LISTED).append(" more");
                break;
            }
            if (listed > 0) {
                sb.append(", ");
            }
            String name = entry.getValue();
            sb.append(name != null ? name : "(no name)").append(" [").append(entry.getKey()).append(']');
            listed++;
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return address != null ? address : "\"" + name + "\"";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
