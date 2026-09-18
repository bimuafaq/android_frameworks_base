/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.os.UserHandle;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.MobileSignalController.MobileIconGroup;

import java.util.HashMap;
import java.util.Map;

import lineageos.providers.LineageSettings;

/**
 * Combined data icon provider — keeps AOSP TelephonyIcons untouched.
 * Runtime switch via LineageSettings.Secure.MOBILE_DATA_ICON_STYLE (0=separate, 1=combined).
 * Drawables are combined type+activity (16x14, #ff000000) vs AOSP ic_*_mobiledata (14x17, #FFF).
 */
public class CombinedTelephonyIcons {
    // Combined type icons (stat_sys) — same semantics as TelephonyIcons.ICON_*
    static final int ICON_LTE = R.drawable.stat_sys_data_connected_lte;
    static final int ICON_LTE_PLUS = R.drawable.stat_sys_data_connected_lte_plus;
    static final int ICON_G = R.drawable.stat_sys_data_connected_g;
    static final int ICON_E = R.drawable.stat_sys_data_connected_e;
    static final int ICON_H = R.drawable.stat_sys_data_connected_h;
    static final int ICON_H_PLUS = R.drawable.stat_sys_data_connected_h_plus;
    static final int ICON_3G = R.drawable.stat_sys_data_connected_3g;
    static final int ICON_4G = R.drawable.stat_sys_data_connected_4g;
    static final int ICON_4G_PLUS = R.drawable.stat_sys_data_connected_4g_plus;
    static final int ICON_5G_E = R.drawable.stat_sys_data_connected_5g; // fallback, 5ge_att variant optional
    static final int ICON_1X = R.drawable.stat_sys_data_connected_1x;
    static final int ICON_5G = R.drawable.stat_sys_data_connected_5g;
    static final int ICON_5G_PLUS = R.drawable.stat_sys_data_connected_5g_plus_one_shaped;

    // Combined activity overlay — single drawable with alpha trick (no_inout/in/out/inout)
    // like NetworkTraffic's stat_sys_network_traffic_up/down/updown (lineage-sdk/sdk/src/java/org/lineageos/internal/statusbar/NetworkTraffic.java:435)
    static final int ACTIVITY_NONE = R.drawable.stat_sys_data_no_inout;
    static final int ACTIVITY_IN = R.drawable.stat_sys_data_in;
    static final int ACTIVITY_OUT = R.drawable.stat_sys_data_out;
    static final int ACTIVITY_INOUT = R.drawable.stat_sys_data_inout;

    static int getActivityIcon(boolean in, boolean out) {
        if (in && out) return ACTIVITY_INOUT;
        if (in) return ACTIVITY_IN;
        if (out) return ACTIVITY_OUT;
        return ACTIVITY_NONE;
    }

    public static boolean isCombinedStyle(Context context) {
        try {
            return LineageSettings.Secure.getIntForUser(context.getContentResolver(),
                    LineageSettings.Secure.MOBILE_DATA_ICON_STYLE, 0, UserHandle.USER_CURRENT) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    static final MobileIconGroup CARRIER_NETWORK_CHANGE = new MobileIconGroup(
            "CARRIER_NETWORK_CHANGE", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.carrier_network_change_mode, 0, false);

    static final MobileIconGroup THREE_G = new MobileIconGroup("3G", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_3g, ICON_3G, true);
    static final MobileIconGroup WFC = new MobileIconGroup("WFC", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0], 0, 0, false);
    static final MobileIconGroup UNKNOWN = new MobileIconGroup("Unknown", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0], 0, 0, false);
    static final MobileIconGroup E = new MobileIconGroup("E", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_edge, ICON_E, false);
    static final MobileIconGroup ONE_X = new MobileIconGroup("1X", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_cdma, ICON_1X, true);
    static final MobileIconGroup G = new MobileIconGroup("G", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_gprs, ICON_G, false);
    static final MobileIconGroup H = new MobileIconGroup("H", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_3_5g, ICON_H, false);
    static final MobileIconGroup H_PLUS = new MobileIconGroup("H+", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_3_5g_plus, ICON_H_PLUS, false);
    static final MobileIconGroup FOUR_G = new MobileIconGroup("4G", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_4g, ICON_4G, true);
    static final MobileIconGroup FOUR_G_PLUS = new MobileIconGroup("4G+", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_4g_plus, ICON_4G_PLUS, true);
    static final MobileIconGroup LTE = new MobileIconGroup("LTE", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_lte, ICON_LTE, true);
    static final MobileIconGroup LTE_PLUS = new MobileIconGroup("LTE+", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_lte_plus, ICON_LTE_PLUS, true);
    static final MobileIconGroup LTE_CA_5G_E = new MobileIconGroup("5Ge", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_5ge_html, ICON_5G_E, true);
    static final MobileIconGroup NR_5G = new MobileIconGroup("5G", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_5g, ICON_5G, true);
    static final MobileIconGroup NR_5G_PLUS = new MobileIconGroup("5G_PLUS", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.data_connection_5g_plus, ICON_5G_PLUS, true);
    static final MobileIconGroup DATA_DISABLED = new MobileIconGroup("DataDisabled", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.cell_data_off_content_description, 0, false);
    static final MobileIconGroup NOT_DEFAULT_DATA = new MobileIconGroup("NotDefaultData", null, null,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH, 0, 0, 0, 0,
            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0],
            R.string.not_default_data_content_description, 0, false);

    static final Map<String, MobileIconGroup> ICON_NAME_TO_ICON;
    static {
        ICON_NAME_TO_ICON = new HashMap<>();
        ICON_NAME_TO_ICON.put("carrier_network_change", CARRIER_NETWORK_CHANGE);
        ICON_NAME_TO_ICON.put("3g", THREE_G);
        ICON_NAME_TO_ICON.put("wfc", WFC);
        ICON_NAME_TO_ICON.put("unknown", UNKNOWN);
        ICON_NAME_TO_ICON.put("e", E);
        ICON_NAME_TO_ICON.put("1x", ONE_X);
        ICON_NAME_TO_ICON.put("g", G);
        ICON_NAME_TO_ICON.put("h", H);
        ICON_NAME_TO_ICON.put("h+", H_PLUS);
        ICON_NAME_TO_ICON.put("4g", FOUR_G);
        ICON_NAME_TO_ICON.put("4g+", FOUR_G_PLUS);
        ICON_NAME_TO_ICON.put("5ge", LTE_CA_5G_E);
        ICON_NAME_TO_ICON.put("lte", LTE);
        ICON_NAME_TO_ICON.put("lte+", LTE_PLUS);
        ICON_NAME_TO_ICON.put("5g", NR_5G);
        ICON_NAME_TO_ICON.put("5g_plus", NR_5G_PLUS);
        ICON_NAME_TO_ICON.put("datadisable", DATA_DISABLED);
        ICON_NAME_TO_ICON.put("notdefaultdata", NOT_DEFAULT_DATA);
    }
}
