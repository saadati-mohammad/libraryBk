package ir.iau.library.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Scanner;

/**
 * Optional machine-bound license gate.
 *
 * <p>Historically this ran unconditionally from {@code main()} and terminated the JVM via
 * {@link System#exit(int)} whenever an interactive key was missing/invalid or the motherboard
 * serial could not be read. That made the application impossible to start in any headless
 * environment (Docker, CI, Liara, systemd). The gate is now <strong>disabled by default</strong>
 * and only enforced when {@code app.license.enabled=true} is set explicitly.
 *
 * <p>When enabled, it reads the key from the {@code APP_LICENSE_KEY} environment variable (or
 * {@code app.license.key}), so it never blocks on an interactive prompt in production. The
 * interactive prompt is only used when {@code app.license.interactive=true}.
 */
public final class KeyPromptValidator {

    private KeyPromptValidator() {
    }

    /**
     * @param enabled     whether the license gate should be enforced at all
     * @param interactive whether to fall back to an interactive console prompt when no key is provided
     */
    public static void verify(boolean enabled, boolean interactive) {
        if (!enabled) {
            return;
        }

        String provided = readProvidedKey(interactive);
        if (provided == null || provided.isEmpty()) {
            fail("No license key provided (set APP_LICENSE_KEY or app.license.key).");
            return;
        }

        String serial = readBaseboardSerial().trim();
        if (serial.isEmpty()) {
            fail("Couldn't read the motherboard serial on this host; license check cannot run.");
            return;
        }

        String expected = computeKeyFromSerial(serial);
        if (!expected.equalsIgnoreCase(provided)) {
            fail("Invalid license key.");
        }
    }

    private static String readProvidedKey(boolean interactive) {
        String env = System.getenv("APP_LICENSE_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty("app.license.key");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        if (!interactive) {
            return null;
        }
        System.out.print("Enter key: ");
        try (Scanner sc = new Scanner(System.in)) {
            return sc.hasNextLine() ? sc.nextLine().trim() : "";
        }
    }

    private static void fail(String reason) {
        System.err.println("[license] " + reason + " Refusing to start because app.license.enabled=true.");
        System.exit(1);
    }

    // same algorithm as the .bat generator
    private static String computeKeyFromSerial(String serial) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(serial.getBytes(StandardCharsets.UTF_8));
            String b64 = Base64.getEncoder().encodeToString(digest);
            String k = b64.replace('+', 'X').replace('/', 'Y').replace("=", "");
            k = k.replaceAll("[^A-Za-z0-9]", "");
            if (k.length() >= 15) {
                k = k.substring(0, 15);
            }
            return k.toUpperCase();
        } catch (Exception e) {
            return "";
        }
    }

    // read baseboard serial via PowerShell CIM; fallback to WMIC (Windows only).
    private static String readBaseboardSerial() {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {
                    "powershell", "-Command",
                    "(Get-CimInstance Win32_BaseBoard).SerialNumber"
            });
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                if (line != null) {
                    return line.trim();
                }
            }
        } catch (Exception ignored) {
            // not on Windows or powershell unavailable
        }

        try {
            Process p2 = Runtime.getRuntime().exec(new String[] {"cmd", "/c", "wmic baseboard get serialnumber"});
            try (BufferedReader r2 = new BufferedReader(
                    new InputStreamReader(p2.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r2.readLine()) != null) {
                    l = l.trim();
                    if (l.isEmpty() || l.equalsIgnoreCase("SerialNumber")) {
                        continue;
                    }
                    return l;
                }
            }
        } catch (Exception ignored) {
            // wmic unavailable
        }

        return "";
    }
}
