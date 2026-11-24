package app.gameday.core.install;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import app.gameday.core.compat.ApkFacts;
import org.junit.Test;

public class InstallPlanTest {

    private static final String CERT = "aa11";

    private static ApkFacts apk(String pkg, long version, java.util.List<String> splits) {
        return ApkFacts.builder().packageName(pkg).versionCode(version).minSdk(25)
                .signerSha256(Collections.singletonList(CERT)).splitNames(splits)
                .sizeBytes(1000L).build();
    }

    @Test
    public void baseAloneIsAValidPlan() {
        InstallPlan.Validation v = InstallPlan.validate(
                Collections.singletonList(apk("p", 10, Collections.emptyList())));
        assertTrue(v.describe(), v.isValid());
        assertEquals("p", v.plan().packageName());
        assertEquals(1000L, v.plan().totalBytes());
    }

    @Test
    public void baseWithSplitsIsAValidPlan() {
        InstallPlan.Validation v = InstallPlan.validate(Arrays.asList(
                apk("p", 10, Collections.emptyList()),
                apk("p", 10, Collections.singletonList("config.armeabi_v7a")),
                apk("p", 10, Collections.singletonList("config.en"))));
        assertTrue(v.describe(), v.isValid());
        assertEquals(2, v.plan().splits().size());
        assertEquals(3000L, v.plan().totalBytes());
    }

    @Test
    public void missingBaseIsRejected() {
        InstallPlan.Validation v = InstallPlan.validate(
                Collections.singletonList(apk("p", 10, Collections.singletonList("config.en"))));
        assertFalse(v.isValid());
        assertTrue(v.problems().toString().contains("BASE_MISSING"));
    }

    @Test
    public void splitVersionMismatchIsRejected() {
        InstallPlan.Validation v = InstallPlan.validate(Arrays.asList(
                apk("p", 10, Collections.emptyList()),
                apk("p", 11, Collections.singletonList("config.en"))));
        assertFalse(v.isValid());
        assertTrue(v.problems().toString().contains("SPLIT_VERSION_MISMATCH"));
    }

    @Test
    public void splitPackageMismatchIsRejected() {
        InstallPlan.Validation v = InstallPlan.validate(Arrays.asList(
                apk("p", 10, Collections.emptyList()),
                apk("q", 10, Collections.singletonList("config.en"))));
        assertFalse(v.isValid());
        assertTrue(v.problems().toString().contains("SPLIT_PACKAGE_MISMATCH"));
    }

    @Test
    public void duplicateSplitNamesAreRejected() {
        InstallPlan.Validation v = InstallPlan.validate(Arrays.asList(
                apk("p", 10, Collections.emptyList()),
                apk("p", 10, Collections.singletonList("config.en")),
                apk("p", 10, Collections.singletonList("config.en"))));
        assertFalse(v.isValid());
        assertTrue(v.problems().toString().contains("SPLIT_NAME_DUPLICATE"));
    }

    @Test
    public void differingSignersAreRejected() {
        ApkFacts otherSigner = ApkFacts.builder().packageName("p").versionCode(10).minSdk(25)
                .signerSha256(Collections.singletonList("bb22"))
                .splitNames(Collections.singletonList("config.en")).build();
        InstallPlan.Validation v = InstallPlan.validate(Arrays.asList(
                apk("p", 10, Collections.emptyList()), otherSigner));
        assertFalse(v.isValid());
        assertTrue(v.problems().toString().contains("SPLIT_SIGNER_MISMATCH"));
    }

    @Test
    public void emptyPlanIsRejected() {
        assertFalse(InstallPlan.validate(Collections.<ApkFacts>emptyList()).isValid());
        assertFalse(InstallPlan.validate(null).isValid());
    }

    @Test
    public void twoBasesAreRejected() {
        InstallPlan.Validation v = InstallPlan.validate(Arrays.asList(
                apk("p", 10, Collections.emptyList()),
                apk("p", 10, Collections.emptyList())));
        assertFalse(v.isValid());
        assertTrue(v.problems().toString().contains("MULTIPLE_BASE"));
    }
}
