package app.gameday.runtime.desktop;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Reads {@link HostFacts}. An interface so tests and the backend can supply their own readings. */
public interface HostProbe {

    HostFacts probe();

    /** Real implementation: reads the OS, /proc or sysctl, the disk and the PATH. No network. */
    final class Default implements HostProbe {

        private final String adbPath;

        public Default() {
            this(null);
        }

        public Default(String adbPathOverride) {
            this.adbPath = adbPathOverride;
        }

        @Override
        public HostFacts probe() {
            String os = System.getProperty("os.name", "");
            String version = System.getProperty("os.version", "");
            String arch = System.getProperty("os.arch", "");
            return new HostFacts(os, version, arch, Runtime.getRuntime().availableProcessors(),
                    totalMemoryBytes(), freeDiskBytes(), kvmReadable(), locateAdb(),
                    locateOnPath("java"));
        }

        /**
         * /dev/kvm existing is not the same as being usable: on shared hosts the node is present and
         * group-restricted, which is exactly the difference between "virtualization is available"
         * and "this process may use it".
         */
        private static boolean kvmReadable() {
            Path kvm = Paths.get("/dev/kvm");
            if (!Files.exists(kvm)) {
                return false;
            }
            return Files.isReadable(kvm) && Files.isWritable(kvm);
        }

        private static long totalMemoryBytes() {
            long fromProc = readMemTotalFromProc();
            if (fromProc > 0) {
                return fromProc;
            }
            Long fromSysctl = readLong(new String[]{"sysctl", "-n", "hw.memsize"});
            return fromSysctl == null ? -1L : fromSysctl.longValue();
        }

        private static long readMemTotalFromProc() {
            Path meminfo = Paths.get("/proc/meminfo");
            if (!Files.isReadable(meminfo)) {
                return -1L;
            }
            try {
                for (String line : Files.readAllLines(meminfo, StandardCharsets.UTF_8)) {
                    if (line.startsWith("MemTotal:")) {
                        String digits = line.replaceAll("[^0-9]", "");
                        if (!digits.isEmpty()) {
                            return Long.parseLong(digits) * 1024L;
                        }
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
                return -1L;
            }
            return -1L;
        }

        private static long freeDiskBytes() {
            try {
                File home = new File(System.getProperty("user.home", "."));
                return home.getUsableSpace();
            } catch (RuntimeException e) {
                return -1L;
            }
        }

        private static Long readLong(String[] command) {
            Process process = null;
            try {
                process = new ProcessBuilder(command).redirectErrorStream(true).start();
                String text = readAll(process.getInputStream()).trim();
                process.waitFor();
                if (text.isEmpty()) {
                    return null;
                }
                // sysctl may print "hw.memsize: 17179869184" on some versions.
                String digits = text.replaceAll("[^0-9]", "");
                return digits.isEmpty() ? null : Long.valueOf(Long.parseLong(digits));
            } catch (IOException | InterruptedException | RuntimeException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return null;
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }

        private String locateAdb() {
            if (adbPath != null && !adbPath.isEmpty()) {
                return adbPath;
            }
            String fromPath = locateOnPath("adb");
            if (fromPath != null) {
                return fromPath;
            }
            String androidHome = System.getenv("ANDROID_HOME");
            if (androidHome == null || androidHome.isEmpty()) {
                androidHome = System.getenv("ANDROID_SDK_ROOT");
            }
            if (androidHome != null && !androidHome.isEmpty()) {
                File candidate = new File(new File(androidHome, "platform-tools"), "adb");
                if (candidate.canExecute()) {
                    return candidate.getAbsolutePath();
                }
            }
            return null;
        }

        private static String locateOnPath(String binary) {
            String path = System.getenv("PATH");
            if (path == null) {
                return null;
            }
            for (String dir : path.split(File.pathSeparator)) {
                if (dir.isEmpty()) {
                    continue;
                }
                File candidate = new File(dir, binary);
                if (candidate.isFile() && candidate.canExecute()) {
                    return candidate.getAbsolutePath();
                }
            }
            return null;
        }

        private static String readAll(InputStream in) throws IOException {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }
}
