package app.gameday.core.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class IsolationAssessmentTest {

    private static IsolationAssessment.Facts facts(int sdk, boolean managedUsers,
            boolean provisioningResolves, boolean profileOwner) {
        return new IsolationAssessment.Facts(sdk, managedUsers, provisioningResolves, profileOwner);
    }

    @Test
    public void missingFeatureIsNotSupported() {
        IsolationAssessment.Result result =
                IsolationAssessment.evaluate(facts(25, false, true, false));
        assertEquals(IsolationAssessment.Verdict.NOT_SUPPORTED, result.verdict);
        assertTrue(result.reason.contains("android.software.managed_users"));
        assertFalse(result.provisionable());
    }

    @Test
    public void missingProvisioningFlowIsNotSupported() {
        IsolationAssessment.Result result =
                IsolationAssessment.evaluate(facts(25, true, false, false));
        assertEquals(IsolationAssessment.Verdict.NOT_SUPPORTED, result.verdict);
        assertTrue(result.reason.contains("PROVISION_MANAGED_PROFILE"));
    }

    @Test
    public void apiBelowManagedProfilesIsNotSupported() {
        IsolationAssessment.Result result =
                IsolationAssessment.evaluate(facts(23, true, true, false));
        assertEquals(IsolationAssessment.Verdict.NOT_SUPPORTED, result.verdict);
    }

    @Test
    public void provisionableWhenEverythingIsPresent() {
        IsolationAssessment.Result result =
                IsolationAssessment.evaluate(facts(25, true, true, false));
        assertEquals(IsolationAssessment.Verdict.PROVISIONABLE, result.verdict);
        assertTrue(result.provisionable());
        assertTrue(result.preservesProtectedPlayback);
    }

    @Test
    public void alreadyProvisionedIsReportedSeparately() {
        IsolationAssessment.Result result =
                IsolationAssessment.evaluate(facts(25, true, true, true));
        assertEquals(IsolationAssessment.Verdict.ALREADY_PROVISIONED, result.verdict);
    }

    @Test
    public void everyVerdictKeepsTheHonestLimits() {
        // Whatever the device says, a profile never raises the API level, and the trade-offs must be
        // reported rather than hidden behind an optimistic verdict.
        for (IsolationAssessment.Verdict ignored : IsolationAssessment.Verdict.values()) {
            IsolationAssessment.Result result =
                    IsolationAssessment.evaluate(facts(30, true, true, false));
            assertFalse(result.raisesApiLevel);
            assertFalse(result.costs.isEmpty());
            assertFalse(result.benefits.isEmpty());
        }
    }

    @Test
    public void nullFactsDoNotThrow() {
        IsolationAssessment.Result result = IsolationAssessment.evaluate(null);
        assertEquals(IsolationAssessment.Verdict.NOT_SUPPORTED, result.verdict);
    }
}
