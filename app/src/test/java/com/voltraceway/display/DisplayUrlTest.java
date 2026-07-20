package com.voltraceway.display;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class DisplayUrlTest {
    @Test
    public void addsHttpsWhenSchemeIsMissing() {
        assertEquals("https://voltraceway.ca/display", DisplayUrl.normalize("voltraceway.ca/display"));
    }

    @Test
    public void keepsHttpForLocalDisplays() {
        assertEquals("http://192.168.1.10/screen", DisplayUrl.normalize("http://192.168.1.10/screen"));
    }

    @Test
    public void rejectsUnsupportedSchemes() {
        assertThrows(IllegalArgumentException.class, () -> DisplayUrl.normalize("file:///tmp/display.html"));
    }

    @Test
    public void rejectsEmptyValues() {
        assertThrows(IllegalArgumentException.class, () -> DisplayUrl.normalize("  "));
    }
}
