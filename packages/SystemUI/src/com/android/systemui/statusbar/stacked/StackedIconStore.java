/*
 * Copyright (C) 2025 The LineageOS Project
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

package com.android.systemui.statusbar.stacked;

import android.util.ArrayMap;

import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.MobileIconState;

/**
 * Small in-memory store so {@code StatusBarIconControllerImpl.setMobileIcons} can
 * piggy-back the secondary state alongside the stacked holder (which is TYPE_MOBILE).
 * The stacked {@code IconManager} entry renders via {@link StackedMobileView} which
 * reads back from here. Additive — no change to {@code StatusBarIconHolder} wire type.
 */
public final class StackedIconStore {

    private static final ArrayMap<String, Entry> sMap = new ArrayMap<>();

    private StackedIconStore() {}

    public static final class Entry {
        public final DualSim dual;
        public final MobileIconState primary;
        public final MobileIconState secondary;

        Entry(DualSim dual, MobileIconState primary, MobileIconState secondary) {
            this.dual = dual;
            this.primary = primary;
            this.secondary = secondary;
        }
    }

    private static String key(String slot, int tag) {
        return slot + "#" + tag;
    }

    public static void put(String slot, int tag, DualSim dual,
            MobileIconState primary, MobileIconState secondary) {
        sMap.put(key(slot, tag), new Entry(dual, primary, secondary));
    }

    public static Entry get(String slot, int tag) {
        return sMap.get(key(slot, tag));
    }

    public static void clear(String slot) {
        // Remove all entries for this slot
        for (int i = sMap.size() - 1; i >= 0; i--) {
            if (sMap.keyAt(i).startsWith(slot + "#")) {
                sMap.removeAt(i);
            }
        }
    }
}
