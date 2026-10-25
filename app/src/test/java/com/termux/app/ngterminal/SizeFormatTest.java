package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SizeFormatTest {
    @Test public void units() {
        assertEquals("0 KB", SizeFormat.human(0));
        assertEquals("812 KB", SizeFormat.human(812L * 1024));
        assertEquals("48.3 MB", SizeFormat.human((long) (48.3 * 1024 * 1024)));
        assertEquals("1.9 GB", SizeFormat.human((long) (1.9 * 1024 * 1024 * 1024)));
    }
}
