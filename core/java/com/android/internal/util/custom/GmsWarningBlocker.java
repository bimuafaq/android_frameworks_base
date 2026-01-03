package com.android.internal.util.custom;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.provider.Settings;

public class GmsWarningBlocker {

    private static final String SETTING_KEY = "disable_gms_warnings";

    public static boolean isEnabled(ContentResolver resolver) {
        return Settings.System.getInt(resolver, SETTING_KEY, 0) == 1;
    }

    public static boolean shouldDismissDialog(ContentResolver resolver, CharSequence title, CharSequence message) {
        if (!isEnabled(resolver)) {
            return false;
        }

        String t = title != null ? title.toString() : "";
        String m = message != null ? message.toString() : "";

        if (t.contains("Update Google Play services") || 
            t.contains("Enable Google Play services")) {
            return true;
        }

        if (t.equals("Something went wrong") && m.contains("Google Play")) {
            return true;
        }

        return false;
    }

    public static void silenceGmsChannel(ContentResolver resolver, NotificationChannel channel) {
        if (!isEnabled(resolver) || channel == null) {
            return;
        }
        if ("com.google.android.gms.availability".equals(channel.getId())) {
            channel.setImportance(NotificationManager.IMPORTANCE_NONE);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_SECRET);
        }
    }
}
