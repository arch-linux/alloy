package net.alloymc.loader.mod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {

    @Test
    void parsesFullVersion() {
        SemanticVersion v = SemanticVersion.parse("1.21.4");
        assertEquals(1, v.major());
        assertEquals(21, v.minor());
        assertEquals(4, v.patch());
    }

    @Test
    void parsesMajorMinorOnly() {
        SemanticVersion v = SemanticVersion.parse("1.21");
        assertEquals(1, v.major());
        assertEquals(21, v.minor());
        assertEquals(0, v.patch());
    }

    @Test
    void parsesMajorOnly() {
        SemanticVersion v = SemanticVersion.parse("3");
        assertEquals(3, v.major());
        assertEquals(0, v.minor());
        assertEquals(0, v.patch());
    }

    @Test
    void stripsLeadingV() {
        SemanticVersion v = SemanticVersion.parse("v2.1.0");
        assertEquals(2, v.major());
        assertEquals(1, v.minor());
        assertEquals(0, v.patch());
    }

    @Test
    void comparison() {
        assertTrue(SemanticVersion.parse("1.0.0").compareTo(SemanticVersion.parse("2.0.0")) < 0);
        assertTrue(SemanticVersion.parse("1.1.0").compareTo(SemanticVersion.parse("1.2.0")) < 0);
        assertTrue(SemanticVersion.parse("1.1.1").compareTo(SemanticVersion.parse("1.1.2")) < 0);
        assertEquals(0, SemanticVersion.parse("1.21.4").compareTo(SemanticVersion.parse("1.21.4")));
        assertTrue(SemanticVersion.parse("2.0.0").compareTo(SemanticVersion.parse("1.99.99")) > 0);
    }

    @Test
    void toStringFormat() {
        assertEquals("1.21.4", SemanticVersion.parse("1.21.4").toString());
        assertEquals("1.21.0", SemanticVersion.parse("1.21").toString());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse(""));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse(null));
    }
}
