package com.saihgupr.btcontrol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class PairTargetTest {

    private static final int TYPE_CLASSIC = 1;
    private static final int TYPE_LE = 2;
    private static final int TYPE_DUAL = 3;

    @Test
    public void addressIsNormalized() {
        PairTarget target = PairTarget.parse("aa-bb-cc-dd-ee-0f", null);
        assertEquals("AA:BB:CC:DD:EE:0F", target.address);
        assertNull(target.name);
    }

    @Test
    public void addressWithSurroundingSpacesIsAccepted() {
        assertEquals("AA:BB:CC:DD:EE:FF", PairTarget.parse("  AA:BB:CC:DD:EE:FF ", null).address);
    }

    @Test
    public void invalidAddressIsRejected() {
        assertNull(PairTarget.parse("AA:BB:CC:DD:EE", null));
        assertNull(PairTarget.parse("GG:BB:CC:DD:EE:FF", null));
        assertNull(PairTarget.parse("AABBCCDDEEFF", null));
    }

    @Test
    public void invalidAddressIsRejectedEvenWithName() {
        // A typo in the address must not silently fall back to name matching.
        assertNull(PairTarget.parse("not-an-address", "WH-1000XM4"));
    }

    @Test
    public void missingTargetIsRejected() {
        assertNull(PairTarget.parse(null, null));
        assertNull(PairTarget.parse("", "   "));
    }

    @Test
    public void nameIsTrimmed() {
        PairTarget target = PairTarget.parse(null, "  WH-1000XM4 ");
        assertNull(target.address);
        assertEquals("WH-1000XM4", target.name);
    }

    @Test
    public void addressMatchIgnoresCaseAndType() {
        PairTarget target = PairTarget.parse("AA:BB:CC:DD:EE:FF", null);
        assertTrue(target.matches("aa:bb:cc:dd:ee:ff", null, TYPE_LE));
        assertFalse(target.matches("AA:BB:CC:DD:EE:00", "WH-1000XM4", TYPE_CLASSIC));
    }

    @Test
    public void nameMatchIsExactIgnoringCase() {
        PairTarget target = PairTarget.parse(null, "wh-1000xm4");
        assertTrue(target.matches("AA:BB:CC:DD:EE:FF", "WH-1000XM4", TYPE_CLASSIC));
        assertTrue(target.matches("AA:BB:CC:DD:EE:FF", "WH-1000XM4", TYPE_DUAL));
        assertFalse(target.matches("AA:BB:CC:DD:EE:FF", "WH-1000XM4 (2)", TYPE_CLASSIC));
        assertFalse(target.matches("AA:BB:CC:DD:EE:FF", null, TYPE_CLASSIC));
    }

    @Test
    public void nameMatchSkipsLowEnergyOnlyTwin() {
        // Sony headsets advertise a BLE twin with the same name; bonding it gives no audio.
        PairTarget target = PairTarget.parse(null, "WH-1000XM4");
        assertFalse(target.matches("11:22:33:44:55:66", "WH-1000XM4", TYPE_LE));
    }

    @Test
    public void describeSeenListsDevices() {
        Map<String, String> seen = new LinkedHashMap<>();
        seen.put("AA:BB:CC:DD:EE:FF", "WH-1000XM4");
        seen.put("11:22:33:44:55:66", null);
        assertEquals("WH-1000XM4 [AA:BB:CC:DD:EE:FF], (no name) [11:22:33:44:55:66]",
                PairTarget.describeSeen(seen));
    }

    @Test
    public void describeSeenHandlesNothingFound() {
        assertEquals("nothing found", PairTarget.describeSeen(new LinkedHashMap<String, String>()));
    }

    @Test
    public void describeSeenIsCapped() {
        Map<String, String> seen = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            seen.put(String.format("AA:BB:CC:DD:EE:%02X", i), "Dev" + i);
        }
        String text = PairTarget.describeSeen(seen);
        assertTrue(text.endsWith(", +4 more"));
        assertFalse(text.contains("Dev8"));
    }
}
