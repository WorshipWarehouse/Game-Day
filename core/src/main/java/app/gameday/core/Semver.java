package app.gameday.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic version parsing and comparison for GameDay's own release train.
 *
 * <p>GameDay uses {@code major.minor.patch[-prerelease][+build]}. Android's versionCode remains the
 * authority for installation decisions; Semver is only used for display and release-channel
 * filtering, because it is what humans read in release notes.
 */
public final class Semver implements Comparable<Semver> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String prerelease;
    private final String build;

    private Semver(int major, int minor, int patch, String prerelease, String build) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
        this.build = build;
    }

    public static Semver parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("version is null");
        }
        String s = text.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("version is empty");
        }
        String build = null;
        int plus = s.indexOf('+');
        if (plus >= 0) {
            build = s.substring(plus + 1);
            s = s.substring(0, plus);
        }
        String prerelease = null;
        int dash = s.indexOf('-');
        if (dash >= 0) {
            prerelease = s.substring(dash + 1);
            s = s.substring(0, dash);
        }
        String[] parts = s.split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            throw new IllegalArgumentException("expected 1-3 numeric components: " + text);
        }
        int[] nums = new int[3];
        for (int i = 0; i < parts.length; i++) {
            nums[i] = parseNumber(parts[i], text);
        }
        if (prerelease != null && prerelease.isEmpty()) {
            throw new IllegalArgumentException("empty prerelease: " + text);
        }
        return new Semver(nums[0], nums[1], nums[2], prerelease, build);
    }

    public static Semver parseOrNull(String text) {
        try {
            return parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int parseNumber(String s, String original) {
        if (s.isEmpty()) {
            throw new IllegalArgumentException("empty component in " + original);
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                throw new IllegalArgumentException("non-numeric component '" + s + "' in " + original);
            }
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("component out of range in " + original);
        }
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public boolean isPrerelease() {
        return prerelease != null;
    }

    @Override
    public int compareTo(Semver o) {
        if (major != o.major) {
            return Integer.compare(major, o.major);
        }
        if (minor != o.minor) {
            return Integer.compare(minor, o.minor);
        }
        if (patch != o.patch) {
            return Integer.compare(patch, o.patch);
        }
        // A release outranks any prerelease of the same core version.
        if (prerelease == null && o.prerelease == null) {
            return 0;
        }
        if (prerelease == null) {
            return 1;
        }
        if (o.prerelease == null) {
            return -1;
        }
        List<String> a = splitIdentifiers(prerelease);
        List<String> b = splitIdentifiers(o.prerelease);
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            String x = a.get(i);
            String y = b.get(i);
            boolean xn = isNumeric(x);
            boolean yn = isNumeric(y);
            if (xn && yn) {
                int c = Integer.compare(Integer.parseInt(x), Integer.parseInt(y));
                if (c != 0) {
                    return c;
                }
            } else if (xn != yn) {
                return xn ? -1 : 1;
            } else {
                int c = x.compareTo(y);
                if (c != 0) {
                    return c;
                }
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private static List<String> splitIdentifiers(String s) {
        List<String> out = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '.') {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean isNewerThan(Semver other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (prerelease != null) {
            sb.append('-').append(prerelease);
        }
        if (build != null) {
            sb.append('+').append(build);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Semver)) {
            return false;
        }
        return compareTo((Semver) o) == 0;
    }

    @Override
    public int hashCode() {
        return (major * 31 + minor) * 31 + patch;
    }
}
