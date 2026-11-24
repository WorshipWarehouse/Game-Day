package app.gameday.core.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.gameday.core.compat.ApkFacts;
import app.gameday.core.compat.CompatibilityIssue;

/**
 * A validated install set: exactly one base APK plus optional configuration/feature splits.
 *
 * <p>Android's {@code PackageInstaller} requires every APK in one session to share package name,
 * version code and signing certificates, with unique split names and exactly one base. Violating
 * that produces a confusing platform error, so GameDay checks it up front and refuses.
 */
public final class InstallPlan {

    private final String packageName;
    private final long versionCode;
    private final ApkFacts base;
    private final List<ApkFacts> splits;

    private InstallPlan(String packageName, long versionCode, ApkFacts base, List<ApkFacts> splits) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.base = base;
        this.splits = Collections.unmodifiableList(new ArrayList<ApkFacts>(splits));
    }

    public String packageName() {
        return packageName;
    }

    public long versionCode() {
        return versionCode;
    }

    public ApkFacts base() {
        return base;
    }

    public List<ApkFacts> splits() {
        return splits;
    }

    public List<ApkFacts> all() {
        List<ApkFacts> out = new ArrayList<ApkFacts>();
        out.add(base);
        out.addAll(splits);
        return out;
    }

    public long totalBytes() {
        long total = base.sizeBytes();
        for (ApkFacts a : splits) {
            total += a.sizeBytes();
        }
        return total;
    }

    public static final class Validation {
        private final InstallPlan plan;
        private final List<String> problems;

        Validation(InstallPlan plan, List<String> problems) {
            this.plan = plan;
            this.problems = Collections.unmodifiableList(problems);
        }

        public boolean isValid() {
            return plan != null && problems.isEmpty();
        }

        public InstallPlan plan() {
            return plan;
        }

        public List<String> problems() {
            return problems;
        }

        public String describe() {
            return problems.isEmpty() ? "Valid install set." : "Invalid install set: " + problems;
        }
    }

    public static Validation validate(List<ApkFacts> apks) {
        List<String> problems = new ArrayList<String>();
        if (apks == null || apks.isEmpty()) {
            problems.add(CompatibilityIssue.EMPTY_APK_SET + ": no APKs supplied");
            return new Validation(null, problems);
        }

        ApkFacts base = null;
        List<ApkFacts> splits = new ArrayList<ApkFacts>();
        for (ApkFacts apk : apks) {
            if (apk == null) {
                problems.add(CompatibilityIssue.EMPTY_APK_SET + ": null APK entry");
                continue;
            }
            if (apk.splitNames().isEmpty()) {
                if (base != null) {
                    problems.add(CompatibilityIssue.MULTIPLE_BASE
                            + ": more than one base APK in the install set");
                } else {
                    base = apk;
                }
            } else {
                splits.add(apk);
            }
        }

        if (base == null) {
            problems.add(CompatibilityIssue.BASE_MISSING + ": no base APK in the install set");
            return new Validation(null, problems);
        }

        String pkg = base.packageName();
        long versionCode = base.versionCode();
        Set<String> splitNames = new LinkedHashSet<String>();
        for (ApkFacts split : splits) {
            if (!pkg.equals(split.packageName())) {
                problems.add(CompatibilityIssue.SPLIT_PACKAGE_MISMATCH + ": "
                        + split.splitNames() + " is " + split.packageName() + ", base is " + pkg);
            }
            if (split.versionCode() != versionCode) {
                problems.add(CompatibilityIssue.SPLIT_VERSION_MISMATCH + ": "
                        + split.splitNames() + " has version " + split.versionCode()
                        + ", base has " + versionCode);
            }
            if (!split.signerSha256().equals(base.signerSha256())) {
                problems.add(CompatibilityIssue.SPLIT_SIGNER_MISMATCH + ": "
                        + split.splitNames() + " is signed by a different certificate");
            }
            for (String name : split.splitNames()) {
                if (!splitNames.add(name)) {
                    problems.add(CompatibilityIssue.SPLIT_NAME_DUPLICATE + ": split '" + name
                            + "' appears more than once");
                }
            }
        }

        if (!problems.isEmpty()) {
            return new Validation(null, problems);
        }
        return new Validation(new InstallPlan(pkg, versionCode, base, splits), problems);
    }
}
