package com.termux.shared.terminal.io.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.R;
import org.junit.Test;

public class ExtraKeyIconsTest {
    @Test public void mapsSpecSpecifiedKeys() {
        assertEquals(R.drawable.ic_ng_key_first_page, ExtraKeyIcons.iconFor("HOME"));
        assertEquals(R.drawable.ic_ng_key_last_page, ExtraKeyIcons.iconFor("END"));
        assertEquals(R.drawable.ic_ng_key_keyboard_arrow_up, ExtraKeyIcons.iconFor("UP"));
        assertEquals(R.drawable.ic_ng_key_keyboard_arrow_down, ExtraKeyIcons.iconFor("DOWN"));
        assertEquals(R.drawable.ic_ng_key_keyboard_arrow_left, ExtraKeyIcons.iconFor("LEFT"));
        assertEquals(R.drawable.ic_ng_key_keyboard_arrow_right, ExtraKeyIcons.iconFor("RIGHT"));
        assertEquals(R.drawable.ic_ng_key_keyboard_tab, ExtraKeyIcons.iconFor("TAB"));
        assertEquals(R.drawable.ic_ng_key_keyboard_return, ExtraKeyIcons.iconFor("CTRL j"));
        assertEquals(R.drawable.ic_ng_key_keyboard, ExtraKeyIcons.iconFor("KEYBOARD"));
        assertEquals(R.drawable.ic_ng_key_coffee, ExtraKeyIcons.iconFor("WAKELOCK"));
        assertEquals(R.drawable.ic_ng_key_claude, ExtraKeyIcons.iconFor("CLAUDE"));
    }
    @Test public void textKeysAndUnknownKeysHaveNoIcon() {
        for (String k : new String[]{"ESC", "/", "-", "CTRL", "ALT", "DRAWER", "PGUP", "x", "", null})
            assertEquals("key " + k, 0, ExtraKeyIcons.iconFor(k));
    }
    @Test public void claudeLogoKeepsItsOwnColour() {   // the user asked for the Claude logo itself, not a tinted glyph
        assertTrue(ExtraKeyIcons.keepsOwnColor(R.drawable.ic_ng_key_claude));
        assertFalse(ExtraKeyIcons.keepsOwnColor(R.drawable.ic_ng_key_coffee));
        assertFalse(ExtraKeyIcons.keepsOwnColor(0));
    }
    @Test public void caseSensitiveLikeTermuxKeyNames() {
        assertNotEquals(0, ExtraKeyIcons.iconFor("UP"));
        assertEquals(0, ExtraKeyIcons.iconFor("up"));
    }
}
