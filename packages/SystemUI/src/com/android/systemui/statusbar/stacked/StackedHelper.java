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

import android.content.Context;
import android.telephony.SubscriptionManager;

import com.android.settingslib.graph.SignalDrawable;

/**
 * Static helpers — Java equivalent of the gating logic in lineage-23.2:
 * <ul>
 *   <li>{@code MobileIconsInteractor.kt:310} {@code isStackable}</li>
 *   <li>{@code StackedMobileIconViewModel.kt:68} ordering by {@code activeMobileDataSubscriptionId}</li>
 *   <li>{@code MobileIconInteractor.kt:311/320} {@code showExclamation} / {@code cellularShownLevel}</li>
 * </ul>
 *
 * Kept static so 18.1 does not need a Dagger / Flow controller. Callers
 * construct {@link StackedCellular} from {@code MobileSignalController.MobileState}
 * (or {@code StatusBarSignalPolicy.MobileIconState} via {@link SignalDrawable} level)
 * and then call {@link #isStackable} / {@link DualSim#tryParse}.
 */
public final class StackedHelper {

    private StackedHelper() {}

    /** Port of the three conditions in {@code MobileIconsInteractor.kt:310}. */
    public static boolean isStackable(StackedCellular a, StackedCellular b) {
        if (a == null || b == null) return false;
        // filterIsInstance<Cellular>.size == 2 is already caller-filtered (no Satellite in 18.1)
        // third would be caught by DualSim.tryParse returning null
        return a.numberOfLevels == b.numberOfLevels;
    }

    public static boolean isStackable(DualSim dual) {
        return dual != null && isStackable(dual.primary, dual.secondary);
    }

    /**
     * Active data subscription id — mirrors {@code MobileIconsViewModel.kt:70}
     * {@code activeMobileDataSubscriptionId} / {@code StackedMobileIconViewModel.kt:68}
     * {@code sortedByDescending { it.subscriptionId == activeSubId }}.
     * Returns {@link SubscriptionManager#INVALID_SUBSCRIPTION_ID} when unknown.
     */
    public static int getActiveDataSubId(Context context) {
        return SubscriptionManager.getDefaultDataSubscriptionId();
    }

    /**
     * Builds a {@link StackedCellular} from the 18.1 {@code MobileIconState.strengthId}
     * packed int (see {@code SignalDrawable.getState} / {@code MobileSignalController.getCurrentIconId}).
     * The packed int already encodes {@code level} + {@code numberOfLevels} + {@code cutOut} flag.
     *
     * For faithful port we also need {@code showExclamation} from the packed state and
     * {@code numberOfLevels} extracted via {@link SignalDrawable} constants.
     */
    public static StackedCellular fromStrengthId(int strengthId) {
        // SignalDrawable packs: [ STATE(8) <<16 | NUM_LEVELS(8)<<8 | LEVEL(8) ]
        // Unpack using the same constants as SignalDrawable.java:62-62
        int level = strengthId & 0xff;
        int numLevels = (strengthId >> 8) & 0xff;
        int state = (strengthId >> 16) & 0xff;
        boolean cutOut = state == 2; // STATE_CUT
        boolean carrierChange = state == 3; // STATE_CARRIER_CHANGE
        // 18.1 default numLevels when not yet set — fall back to standard 4
        if (numLevels == 0) numLevels = 4;
        return new StackedCellular(level, numLevels, cutOut, carrierChange);
    }

    /**
     * Extracts {@code (subId, cellular)} pairs from a list of {@code MobileIconState}
     * — caller can feed directly into {@link DualSim#tryParse}.
     * Not used by the SignalDrawable path; provided for symmetry with 23.2.
     */
    public static boolean canStackFromStrengthIds(int strengthIdA, int strengthIdB) {
        StackedCellular a = fromStrengthId(strengthIdA);
        StackedCellular b = fromStrengthId(strengthIdB);
        return isStackable(a, b);
    }
}
