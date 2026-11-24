package app.gameday.runtime.desktop;

/**
 * Prints this host's runtime-readiness verdict as JSON.
 *
 * <p>Run it anywhere before trusting that machine with a guest:
 *
 * <pre>  ./gradlew :runtime:desktop:hostVerdict</pre>
 *
 * <p>The output is deliberately machine-readable so a build script, a bug report or another agent can
 * consume it without parsing prose. It performs no network access and writes nothing.
 */
public final class HostVerdictMain {

    private HostVerdictMain() {
    }

    public static void main(String[] args) {
        HostFacts facts = new HostProbe.Default().probe();
        HostReadiness.Result verdict = HostReadiness.evaluate(facts);
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"backend\": \"avo-desktop\",\n");
        json.append("  \"os\": ").append(quote(facts.osName())).append(",\n");
        json.append("  \"osVersion\": ").append(quote(facts.osVersion())).append(",\n");
        json.append("  \"arch\": ").append(quote(facts.arch())).append(",\n");
        json.append("  \"cpus\": ").append(facts.cpuCount()).append(",\n");
        json.append("  \"memoryGb\": ").append(round(facts.memoryGb())).append(",\n");
        json.append("  \"freeDiskGb\": ").append(round(facts.freeDiskGb())).append(",\n");
        json.append("  \"kvmReadable\": ").append(facts.kvmReadable()).append(",\n");
        json.append("  \"adb\": ").append(quote(facts.adbPath())).append(",\n");
        json.append("  \"java\": ").append(quote(facts.javaPath())).append(",\n");
        json.append("  \"ready\": ").append(verdict.isReady()).append(",\n");
        json.append("  \"verdict\": ").append(quote(verdict.isReady() ? "READY" : "BLOCKED"))
                .append(",\n");
        json.append("  \"blockers\": [");
        for (int i = 0; i < verdict.blockers().size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(quote(verdict.blockers().get(i)));
        }
        json.append("],\n");
        json.append("  \"notes\": [");
        for (int i = 0; i < verdict.notes().size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(quote(verdict.notes().get(i)));
        }
        json.append("]\n}\n");
        System.out.print(json);
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String round(double value) {
        if (value < 0) {
            return "null";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
