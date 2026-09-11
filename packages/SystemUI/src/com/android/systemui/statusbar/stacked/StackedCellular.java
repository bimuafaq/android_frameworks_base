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

/**
 * Port of {@code SignalIconModel.Cellular} from lineage-23.2
 * {@code packages/SystemUI/src/com/android/systemui/statusbar/pipeline/mobile/domain/model/SignalIconModel.kt:39}
 *
 * Pure data holder — no Compose / Flow dependency. Used by the 18.1 port
 * to reuse the same gating logic as {@code MobileIconsInteractor.kt:310}.
 */
public final class StackedCellular {
    public final int level;
    public final int numberOfLevels;
    public final boolean showExclamation;
    public final boolean carrierNetworkChange;

    public StackedCellular(int level, int numberOfLevels,
            boolean showExclamation, boolean carrierNetworkChange) {
        this.level = level;
        this.numberOfLevels = numberOfLevels;
        this.showExclamation = showExclamation;
        this.carrierNetworkChange = carrierNetworkChange;
    }

    @Override
    public String toString() {
        return "StackedCellular(level=" + level + ", n=" + numberOfLevels
                + ", exclamation=" + showExclamation + ", carrierChange=" + carrierNetworkChange + ")";
    }
}
