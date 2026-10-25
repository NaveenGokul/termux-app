package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;

public class DpkgStatusParserTest {
    private static final String STATUS =
        "Package: bash\nStatus: install ok installed\nVersion: 5.3.3\nInstalled-Size: 1200\nDescription: x\n continued\n\n" +
        "Package: rust\nStatus: install ok installed\nVersion: 1.99.1\nInstalled-Size: 578560\n\n" +
        "Package: old\nStatus: deinstall ok config-files\nVersion: 1\nInstalled-Size: 99999\n\n" +
        "Package: tiny\nStatus: install ok installed\nVersion: 2\n";      // no Installed-Size, no trailing blank line

    @Test public void parsesInstalledLargestFirst() {
        List<DpkgStatusParser.Pkg> p = DpkgStatusParser.parse(new StringReader(STATUS));
        assertEquals(3, p.size());
        assertEquals("rust", p.get(0).name);
        assertEquals("1.99.1", p.get(0).version);
        assertEquals(578560, p.get(0).sizeKiB);
        assertEquals("bash", p.get(1).name);
    }
    @Test public void excludesNotInstalled() {
        for (DpkgStatusParser.Pkg x : DpkgStatusParser.parse(new StringReader(STATUS))) assertEquals(false, x.name.equals("old"));
    }
    @Test public void missingSizeIsZero() {
        List<DpkgStatusParser.Pkg> p = DpkgStatusParser.parse(new StringReader(STATUS));
        assertEquals("tiny", p.get(2).name);
        assertEquals(0, p.get(2).sizeKiB);
    }
}
