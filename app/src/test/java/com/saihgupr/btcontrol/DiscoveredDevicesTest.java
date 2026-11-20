package com.saihgupr.btcontrol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiscoveredDevicesTest {

    private static final int TYPE_CLASSIC = 1;
    private static final int TYPE_LE = 2;
    private static final int TYPE_DUAL = 3;

    private static DiscoveredDevices empty() {
        return new DiscoveredDevices(Collections.<String>emptySet());
    }

    @Test
    public void namedDeviceIsListed() {
        DiscoveredDevices devices = empty();
        assertTrue(devices.add("AA:BB:CC:DD:EE:FF", "WH-1000XM4", TYPE_CLASSIC));
        List<DiscoveredDevices.Entry> visible = devices.visible();
        assertEquals(1, visible.size());
        assertEquals("AA:BB:CC:DD:EE:FF", visible.get(0).address);
        assertEquals("WH-1000XM4", visible.get(0).name);
    }

    @Test
    public void unnamedDeviceIsHiddenButCounted() {
        DiscoveredDevices devices = empty();
        assertFalse(devices.add("AA:BB:CC:DD:EE:FF", null, TYPE_LE));
        assertTrue(devices.visible().isEmpty());
        assertEquals(1, devices.hiddenCount());
    }

    @Test
    public void nameArrivingLaterRevealsDevice() {
        DiscoveredDevices devices = empty();
        devices.add("AA:BB:CC:DD:EE:FF", null, TYPE_CLASSIC);
        assertTrue(devices.add("AA:BB:CC:DD:EE:FF", "WH-1000XM4", TYPE_CLASSIC));
        assertEquals(1, devices.visible().size());
        assertEquals(0, devices.hiddenCount());
    }

    @Test
    public void laterNullNameDoesNotEraseKnownName() {
        DiscoveredDevices devices = empty();
        devices.add("AA:BB:CC:DD:EE:FF", "WH-1000XM4", TYPE_CLASSIC);
        assertFalse(devices.add("aa:bb:cc:dd:ee:ff", null, TYPE_CLASSIC));
        assertEquals("WH-1000XM4", devices.visible().get(0).name);
    }

    @Test
    public void repeatedIdenticalReportIsNotAChange() {
        DiscoveredDevices devices = empty();
        devices.add("AA:BB:CC:DD:EE:FF", "WH-1000XM4", TYPE_CLASSIC);
        assertFalse(devices.add("AA:BB:CC:DD:EE:FF", "WH-1000XM4", TYPE_CLASSIC));
        assertEquals(1, devices.visible().size());
    }

    @Test
    public void alreadyPairedDevicesAreExcluded() {
        Set<String> bonded = new HashSet<>();
        bonded.add("AA:BB:CC:DD:EE:FF");
        DiscoveredDevices devices = new DiscoveredDevices(bonded);
        assertFalse(devices.add("aa:bb:cc:dd:ee:ff", "WH-1000XM4", TYPE_CLASSIC));
        assertTrue(devices.visible().isEmpty());
        assertEquals(0, devices.hiddenCount());
    }

    @Test
    public void classicDevicesSortBeforeLowEnergyThenByName() {
        DiscoveredDevices devices = empty();
        devices.add("00:00:00:00:00:01", "LE_WH-1000XM4", TYPE_LE);
        devices.add("00:00:00:00:00:02", "zeta speaker", TYPE_CLASSIC);
        devices.add("00:00:00:00:00:03", "Alpha Buds", TYPE_DUAL);
        List<DiscoveredDevices.Entry> visible = devices.visible();
        assertEquals("Alpha Buds", visible.get(0).name);
        assertEquals("zeta speaker", visible.get(1).name);
        assertEquals("LE_WH-1000XM4", visible.get(2).name);
    }

    @Test
    public void labelShowsAddressAndMarksLowEnergy() {
        DiscoveredDevices devices = empty();
        devices.add("00:00:00:00:00:01", "LE_WH-1000XM4", TYPE_LE);
        devices.add("00:00:00:00:00:02", "WH-1000XM4", TYPE_CLASSIC);
        List<DiscoveredDevices.Entry> visible = devices.visible();
        assertEquals("WH-1000XM4\n00:00:00:00:00:02", visible.get(0).label());
        assertEquals("LE_WH-1000XM4\n00:00:00:00:00:01 · Bluetooth LE", visible.get(1).label());
    }
}
