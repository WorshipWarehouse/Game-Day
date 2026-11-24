package app.gameday.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SemverTest {

    @Test
    public void ordersCoreComponents() {
        assertTrue(Semver.parse("1.2.10").isNewerThan(Semver.parse("1.2.9")));
        assertTrue(Semver.parse("2.0.0").isNewerThan(Semver.parse("1.99.99")));
        assertEquals(0, Semver.parse("1.0.0").compareTo(Semver.parse("1.0")));
    }

    @Test
    public void releaseOutranksPrerelease() {
        assertTrue(Semver.parse("1.0.0").isNewerThan(Semver.parse("1.0.0-beta.2")));
        assertTrue(Semver.parse("1.0.0-beta.2").isNewerThan(Semver.parse("1.0.0-beta.1")));
        assertTrue(Semver.parse("1.0.0-beta").isNewerThan(Semver.parse("1.0.0-alpha")));
    }

    @Test
    public void numericPrereleaseIdentifiersSortBelowAlphanumeric() {
        assertTrue(Semver.parse("1.0.0-1").compareTo(Semver.parse("1.0.0-alpha")) < 0);
    }

    @Test
    public void toleratesLeadingVAndPreservesBuildMetadata() {
        assertEquals("1.2.3+build.7", Semver.parse("v1.2.3+build.7").toString());
        assertEquals("1.2.3-rc.1+sha.abc", Semver.parse("1.2.3-rc.1+sha.abc").toString());
        assertEquals(0, Semver.parse("1.2.3+one").compareTo(Semver.parse("1.2.3+two")));
    }

    @Test
    public void rejectsMalformedVersions() {
        assertNull(Semver.parseOrNull(""));
        assertNull(Semver.parseOrNull("abc"));
        assertNull(Semver.parseOrNull("1.2.3.4"));
        assertNull(Semver.parseOrNull("1..3"));
        assertNull(Semver.parseOrNull("1.2.3-"));
        assertNull(Semver.parseOrNull(null));
    }

    @Test
    public void versionCodeComparison() {
        assertTrue(VersionCode.isUpgrade(10, 11));
        assertFalse(VersionCode.isUpgrade(11, 11));
        assertTrue(VersionCode.isInstallableOver(11, 11));
        assertFalse(VersionCode.isInstallableOver(12, 11));
        assertTrue(VersionCode.isDowngrade(12, 11));
    }
}
