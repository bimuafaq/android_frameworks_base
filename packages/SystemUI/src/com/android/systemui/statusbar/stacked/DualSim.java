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

import android.util.Pair;
import java.util.List;

/**
 * Port of {@code DualSim} + {@code tryParseDualSim} from lineage-23.2
 * {@code packages/SystemUI/src/com/android/systemui/statusbar/pipeline/mobile/ui/model/DualSim.kt:29,98}
 *
 * Semantics preserved:
 * - takes the first two Cellular entries, skips anything that would be Satellite / null
 * - if a third Cellular entry exists → return null (not stackable)
 * - primary is the active-data subscription (caller is responsible for ordering)
 */
public final class DualSim {
    public final int primarySubId;
    public final int secondarySubId;
    public final StackedCellular primary;
    public final StackedCellular secondary;

    public DualSim(int primarySubId, StackedCellular primary,
            int secondarySubId, StackedCellular secondary) {
        this.primarySubId = primarySubId;
        this.secondarySubId = secondarySubId;
        this.primary = primary;
        this.secondary = secondary;
    }

    public DualSim(Pair<Integer, StackedCellular> primary,
            Pair<Integer, StackedCellular> secondary) {
        this(primary.first, primary.second, secondary.first, secondary.second);
    }

    /**
     * Tries to build a {@link DualSim} from the list.
     * Port of {@code tryParseDualSim} in {@code DualSim.kt:98}.
     *
     * @param idsToIcon list of (subId, StackedCellular) — caller already filters non-Cellular
     * @return DualSim or null if there are not exactly 2 entries or a third exists
     */
    public static DualSim tryParse(List<Pair<Integer, StackedCellular>> idsToIcon) {
        Pair<Integer, StackedCellular> first = null;
        Pair<Integer, StackedCellular> second = null;
        for (Pair<Integer, StackedCellular> entry : idsToIcon) {
            if (entry == null || entry.second == null) continue;
            if (first == null) {
                first = entry;
            } else if (second == null) {
                second = entry;
            } else {
                return null;
            }
        }
        if (first != null && second != null) {
            return new DualSim(first, second);
        }
        return null;
    }
}
