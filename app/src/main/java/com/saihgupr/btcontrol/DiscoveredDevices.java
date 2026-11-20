package com.saihgupr.btcontrol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Devices seen during a scan, as shown in the pairing dialog. Deduplicates repeated reports,
 * keeps the best known name, hides unnamed devices (mostly beacons and phones) and devices
 * that are already paired. Plain Java so it can be unit tested off-device.
 */
final class DiscoveredDevices {

    static final class Entry {
        final String address;
        String name;
        int type;

        private Entry(String address, String name, int type) {
            this.address = address;
            this.name = name;
            this.type = type;
        }

        /** Two-line list label: name, then address (flagged when the device is LE-only). */
        String label() {
            return name + "\n" + address
                    + (type == PairTarget.DEVICE_TYPE_LE ? " · Bluetooth LE" : "");
        }
    }

    private final Set<String> excluded = new HashSet<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    DiscoveredDevices(Set<String> excludedAddresses) {
        for (String address : excludedAddresses) {
            excluded.add(address.toUpperCase(Locale.ROOT));
        }
    }

    /** Records a scan report. Returns true when the visible list changed. */
    boolean add(String address, String name, int type) {
        String key = address.toUpperCase(Locale.ROOT);
        if (excluded.contains(key)) {
            return false;
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            entries.put(key, new Entry(key, name, type));
            return name != null;
        }
        boolean changed = false;
        if (name != null && !name.equals(entry.name)) {
            entry.name = name;
            changed = true;
        }
        if (type != entry.type) {
            entry.type = type;
            changed |= entry.name != null;
        }
        return changed;
    }

    /** Named devices: classic/dual-mode first (those carry audio), then alphabetical. */
    List<Entry> visible() {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.name != null) {
                list.add(entry);
            }
        }
        Collections.sort(list, (a, b) -> {
            boolean leA = a.type == PairTarget.DEVICE_TYPE_LE;
            boolean leB = b.type == PairTarget.DEVICE_TYPE_LE;
            if (leA != leB) {
                return leA ? 1 : -1;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        return list;
    }

    /** Devices seen without a name, which visible() leaves out. */
    int hiddenCount() {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.name == null) {
                count++;
            }
        }
        return count;
    }
}
