package app.gameday.core.diagnostics;

import java.util.Map;

import app.gameday.core.Json;
import app.gameday.core.Redactor;

/**
 * Structured diagnostics report: JSON for machines, flattened text for humans.
 *
 * <p>Redaction happens once, on the finished document, so no caller can add a section that bypasses
 * it. Report content is limited to device capability and GameDay state; the guest application's
 * data, credentials and video content are never read.
 */
public final class DiagnosticReport {

    public static final int SCHEMA_VERSION = 1;

    private final Json root = Json.object();

    public DiagnosticReport(String appId, String appVersion, long generatedAtMs, String generatedAtIso) {
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("kind", "gameday-diagnostics");
        root.put("appId", appId);
        root.put("appVersion", appVersion);
        root.put("generatedAt", generatedAtIso);
        root.put("generatedAtMs", generatedAtMs);
    }

    public DiagnosticReport section(String name, Json content) {
        root.put(name, content == null ? Json.nul() : content);
        return this;
    }

    public DiagnosticReport field(String key, String value) {
        root.put(key, value == null ? Json.nul() : Json.of(value));
        return this;
    }

    public DiagnosticReport field(String key, long value) {
        root.put(key, Json.of(value));
        return this;
    }

    public DiagnosticReport field(String key, boolean value) {
        root.put(key, Json.of(value));
        return this;
    }

    public Json toJson() {
        return root;
    }

    public String renderJson(Redactor redactor) {
        return (redactor == null ? Redactor.standard() : redactor).redact(root.toJson());
    }

    public String renderText(Redactor redactor) {
        StringBuilder sb = new StringBuilder(2048);
        for (String key : root.keys()) {
            flatten(key, root.get(key), sb);
        }
        return (redactor == null ? Redactor.standard() : redactor).redact(sb.toString());
    }

    private static void flatten(String prefix, Json value, StringBuilder sb) {
        if (value == null || value.isNull()) {
            sb.append(prefix).append(": -\n");
            return;
        }
        switch (value.type()) {
            case OBJECT: {
                if (value.size() == 0) {
                    sb.append(prefix).append(": {}\n");
                    return;
                }
                for (String k : value.keys()) {
                    flatten(prefix.isEmpty() ? k : prefix + "." + k, value.get(k), sb);
                }
                break;
            }
            case ARRAY: {
                if (value.size() == 0) {
                    sb.append(prefix).append(": []\n");
                    return;
                }
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < value.size(); i++) {
                    if (i > 0) {
                        line.append(", ");
                    }
                    line.append(scalar(value.at(i)));
                }
                sb.append(prefix).append(": ").append(line).append('\n');
                break;
            }
            default:
                sb.append(prefix).append(": ").append(scalar(value)).append('\n');
        }
    }

    private static String scalar(Json v) {
        if (v == null || v.isNull()) {
            return "-";
        }
        if (v.isObject() || v.isArray()) {
            return v.toJson();
        }
        return v.asString(String.valueOf(v.toJson()));
    }

    /** Convenience for sections built from a map of scalar values. */
    public static Json section(Map<String, String> values) {
        Json out = Json.object();
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }
}
