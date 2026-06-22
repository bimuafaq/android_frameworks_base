package android.util;

import android.os.SystemProperties;

/**
 * Reads developer compatibility flags from SystemProperties.
 * Used to relax certain platform restrictions for developer workflows.
 *
 * All flags are cached in static volatile fields for zero-overhead reads
 * after initial load. Call {@link #refreshAll()} to re-read from properties.
 *
 * @hide
 */
public final class DeviceCompatConfig {

    private static volatile boolean sDigestEnabled;
    private static volatile boolean sSignatureFlexible;
    private static volatile boolean sSplitSignatureEnabled;

    private DeviceCompatConfig() {}

    /** Re-read all flags from SystemProperties. */
    public static void refreshAll() {
        sDigestEnabled = SystemProperties.getBoolean(
                "sys.compat.digest", false);
        sSignatureFlexible = SystemProperties.getBoolean(
                "sys.compat.signature", false);
        sSplitSignatureEnabled = SystemProperties.getBoolean(
                "sys.compat.split_sig", false);
    }

    // -- Getters --

    /** Relax digest verification during APK install. */
    public static boolean isDigestEnabled() {
        return sDigestEnabled;
    }

    /** Relax signature comparison during APK install. */
    public static boolean isSignatureFlexible() {
        return sSignatureFlexible;
    }

    /** Allow split APKs with mismatched signatures. */
    public static boolean isSplitSignatureEnabled() {
        return sSplitSignatureEnabled;
    }
}
