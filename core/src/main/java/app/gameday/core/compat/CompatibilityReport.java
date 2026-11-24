package app.gameday.core.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of evaluating an APK against a device and the caller's expectations. */
public final class CompatibilityReport {

    private final List<CompatibilityIssue> issues;
    private final String packageName;
    private final long versionCode;

    public CompatibilityReport(String packageName, long versionCode,
            List<CompatibilityIssue> issues) {
        List<CompatibilityIssue> copy = new ArrayList<CompatibilityIssue>();
        if (issues != null) {
            copy.addAll(issues);
        }
        Collections.sort(copy, (a, b) -> Integer.compare(a.severity().rank(), b.severity().rank()));
        this.issues = Collections.unmodifiableList(copy);
        this.packageName = packageName;
        this.versionCode = versionCode;
    }

    public List<CompatibilityIssue> issues() {
        return issues;
    }

    public String packageName() {
        return packageName;
    }

    public long versionCode() {
        return versionCode;
    }

    public boolean isInstallable() {
        return blockers().isEmpty();
    }

    public List<CompatibilityIssue> blockers() {
        List<CompatibilityIssue> out = new ArrayList<CompatibilityIssue>();
        for (CompatibilityIssue i : issues) {
            if (i.severity() == IssueSeverity.BLOCKER) {
                out.add(i);
            }
        }
        return out;
    }

    public List<CompatibilityIssue> warnings() {
        List<CompatibilityIssue> out = new ArrayList<CompatibilityIssue>();
        for (CompatibilityIssue i : issues) {
            if (i.severity() == IssueSeverity.WARNING) {
                out.add(i);
            }
        }
        return out;
    }

    public boolean hasCode(String code) {
        for (CompatibilityIssue i : issues) {
            if (i.code().equals(code)) {
                return true;
            }
        }
        return false;
    }

    public String summary() {
        if (issues.isEmpty()) {
            return "Compatible: no issues found.";
        }
        int blockers = blockers().size();
        int warnings = warnings().size();
        if (blockers > 0) {
            return "Blocked: " + blockers + (blockers == 1 ? " blocker" : " blockers")
                    + (warnings > 0 ? ", " + warnings + " warning(s)" : "") + ".";
        }
        return "Installable with " + warnings + " warning(s).";
    }
}
