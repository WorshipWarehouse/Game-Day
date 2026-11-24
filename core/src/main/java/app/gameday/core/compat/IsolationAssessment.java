package app.gameday.core.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates managed-profile ("work profile") isolation as a candidate for hosting the guest.
 *
 * <p>This is the only rootless isolation mechanism that is not virtualization: the platform creates a
 * real secondary Android user with its own UID, storage and app instances. It is worth evaluating
 * honestly because it preserves protected playback (it is the same OS, the same TEE and the same DRM
 * identity model), unlike in-process application virtualization.
 *
 * <p>It is <em>not</em> an emulator and it does not raise the Android API level: the profile runs the
 * same Fire OS build. The assessment therefore separates "can it be provisioned" from "does it help",
 * because those are different questions and only the first is device-dependent.
 */
public final class IsolationAssessment {

    public enum Verdict {
        /** The device cannot create a managed profile, or the provisioning flow is missing. */
        NOT_SUPPORTED,
        /** A managed profile can be created, but nothing is provisioned yet. */
        PROVISIONABLE,
        /** GameDay is already the profile owner of a managed profile. */
        ALREADY_PROVISIONED
    }

    /** Measured device facts. Every field comes from a platform query, never from a device list. */
    public static final class Facts {
        public final int sdkInt;
        public final boolean managedUsersFeature;
        public final boolean provisioningIntentResolvable;
        public final boolean profileOwnerAlreadySet;

        public Facts(int sdkInt, boolean managedUsersFeature, boolean provisioningIntentResolvable,
                boolean profileOwnerAlreadySet) {
            this.sdkInt = sdkInt;
            this.managedUsersFeature = managedUsersFeature;
            this.provisioningIntentResolvable = provisioningIntentResolvable;
            this.profileOwnerAlreadySet = profileOwnerAlreadySet;
        }
    }

    public static final class Result {
        public final Verdict verdict;
        /** Why this verdict was reached, in user-facing language. */
        public final String reason;
        /** What it would give the project, if provisionable. */
        public final List<String> benefits;
        /** What it would cost, if provisionable. Always non-empty for a real answer. */
        public final List<String> costs;
        /** A real secondary user keeps the platform DRM/secure-surface path intact. */
        public final boolean preservesProtectedPlayback;
        /** False for every profile-based design: the Android API level does not change. */
        public final boolean raisesApiLevel;

        Result(Verdict verdict, String reason, List<String> benefits, List<String> costs,
                boolean preservesProtectedPlayback, boolean raisesApiLevel) {
            this.verdict = verdict;
            this.reason = reason;
            this.benefits = Collections.unmodifiableList(new ArrayList<String>(benefits));
            this.costs = Collections.unmodifiableList(new ArrayList<String>(costs));
            this.preservesProtectedPlayback = preservesProtectedPlayback;
            this.raisesApiLevel = raisesApiLevel;
        }

        public boolean provisionable() {
            return verdict != Verdict.NOT_SUPPORTED;
        }
    }

    private IsolationAssessment() {
    }

    public static Result evaluate(Facts facts) {
        List<String> costs = new ArrayList<String>();
        costs.add("The profile runs the same Fire OS build, so it provides no Android API level "
                + "beyond API 25 and cannot run an application that requires a newer one.");
        costs.add("The guest must be installed again inside the profile and signed in again; a "
                + "profile does not share the personal profile's app instances or data.");
        costs.add("Storage is duplicated: a second copy of the guest and its downloads.");
        costs.add("Fire TV has no work-profile user interface, so provisioning and switching would "
                + "have to be driven entirely by GameDay, and the profile is only usable while the "
                + "device allows it.");

        List<String> benefits = new ArrayList<String>();
        benefits.add("A genuine second Android user: separate UID, separate storage, separate app "
                + "instances - stronger isolation than any in-process sandbox.");
        benefits.add("Protected playback stays on the normal platform path, because the guest is a "
                + "real app in a real user with the platform's DRM identity.");

        if (facts == null) {
            return new Result(Verdict.NOT_SUPPORTED, "No device facts were provided.",
                    benefits, costs, true, false);
        }
        if (facts.profileOwnerAlreadySet) {
            return new Result(Verdict.ALREADY_PROVISIONED,
                    "GameDay is already the profile owner of a managed profile on this device.",
                    benefits, costs, true, false);
        }
        if (facts.sdkInt < 24) {
            return new Result(Verdict.NOT_SUPPORTED,
                    "Managed profiles require Android 7.0 (API 24) with the managed-users feature; "
                            + "this device reports API " + facts.sdkInt + ".",
                    benefits, costs, true, false);
        }
        if (!facts.managedUsersFeature) {
            return new Result(Verdict.NOT_SUPPORTED,
                    "This device does not advertise android.software.managed_users, so the platform "
                            + "cannot create a managed profile. Fire TV builds commonly omit it.",
                    benefits, costs, true, false);
        }
        if (!facts.provisioningIntentResolvable) {
            return new Result(Verdict.NOT_SUPPORTED,
                    "The managed-profile provisioning flow (ACTION_PROVISION_MANAGED_PROFILE) does "
                            + "not resolve on this device, so a profile cannot be created without a "
                            + "system component that is not present.",
                    benefits, costs, true, false);
        }
        return new Result(Verdict.PROVISIONABLE,
                "This device can create a managed profile. It would provide real isolation but no "
                        + "new Android API level, so it is an isolation option rather than a runtime.",
                benefits, costs, true, false);
    }
}
