/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.ViewGroup;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.Dependency;
import com.android.systemui.Dumpable;
import com.android.systemui.R;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.MobileIconState;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.WifiIconState;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.stacked.DualSim;
import com.android.systemui.statusbar.stacked.StackedCellular;
import com.android.systemui.statusbar.stacked.StackedHelper;
import com.android.systemui.statusbar.stacked.StackedIconStore;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Receives the callbacks from CommandQueue related to icons and tracks the state of
 * all the icons. Dispatches this state to any IconManagers that are currently
 * registered with it.
 */
@Singleton
public class StatusBarIconControllerImpl extends StatusBarIconList implements Tunable,
        ConfigurationListener, Dumpable, CommandQueue.Callbacks, StatusBarIconController {

    private static final String TAG = "StatusBarIconController";

    private final ArrayList<IconManager> mIconGroups = new ArrayList<>();
    private final ArraySet<String> mIconBlacklist = new ArraySet<>();

    // Points to light or dark context depending on the... context?
    private Context mContext;
    private Context mLightContext;
    private Context mDarkContext;

    private boolean mIsDark = false;

    @Inject
    public StatusBarIconControllerImpl(Context context, CommandQueue commandQueue) {
        super(context.getResources().getStringArray(
                com.android.internal.R.array.config_statusBarIcons));
        Dependency.get(ConfigurationController.class).addCallback(this);

        mContext = context;

        loadDimens();

        commandQueue.addCallback(this);
        Dependency.get(TunerService.class).addTunable(this, ICON_BLACKLIST);
    }

    @Override
    public void addIconGroup(IconManager group) {
        mIconGroups.add(group);
        List<Slot> allSlots = getSlots();
        for (int i = 0; i < allSlots.size(); i++) {
            Slot slot = allSlots.get(i);
            List<StatusBarIconHolder> holders = slot.getHolderListInViewOrder();
            boolean blocked = mIconBlacklist.contains(slot.getName());

            for (StatusBarIconHolder holder : holders) {
                int tag = holder.getTag();
                int viewIndex = getViewIndex(getSlotIndex(slot.getName()), holder.getTag());
                group.onIconAdded(viewIndex, slot.getName(), blocked, holder);
            }
        }
    }

    @Override
    public void removeIconGroup(IconManager group) {
        group.destroy();
        mIconGroups.remove(group);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (!ICON_BLACKLIST.equals(key)) {
            return;
        }
        mIconBlacklist.clear();
        mIconBlacklist.addAll(StatusBarIconController.getIconBlacklist(mContext, newValue));
        ArrayList<Slot> currentSlots = getSlots();
        ArrayMap<Slot, List<StatusBarIconHolder>> slotsToReAdd = new ArrayMap<>();

        // This is a little hacky... Peel off all of the holders on all of the slots
        // but keep them around so they can be re-added

        // Remove all the icons.
        for (int i = currentSlots.size() - 1; i >= 0; i--) {
            Slot s = currentSlots.get(i);
            slotsToReAdd.put(s, s.getHolderList());
            removeAllIconsForSlot(s.getName());
        }

        // Add them all back
        for (int i = 0; i < currentSlots.size(); i++) {
            Slot item = currentSlots.get(i);
            List<StatusBarIconHolder> iconsForSlot = slotsToReAdd.get(item);
            if (iconsForSlot == null) continue;
            for (StatusBarIconHolder holder : iconsForSlot) {
                setIcon(getSlotIndex(item.getName()), holder);
            }
        }
    }

    private void loadDimens() {
    }

    private void addSystemIcon(int index, StatusBarIconHolder holder) {
        String slot = getSlotName(index);
        int viewIndex = getViewIndex(index, holder.getTag());
        boolean blocked = mIconBlacklist.contains(slot);

        mIconGroups.forEach(l -> l.onIconAdded(viewIndex, slot, blocked, holder));
    }

    @Override
    public void setIcon(String slot, int resourceId, CharSequence contentDescription) {
        int index = getSlotIndex(slot);
        StatusBarIconHolder holder = getIcon(index, 0);
        if (holder == null) {
            StatusBarIcon icon = new StatusBarIcon(UserHandle.SYSTEM, mContext.getPackageName(),
                    Icon.createWithResource(
                            mContext, resourceId), 0, 0, contentDescription);
            holder = StatusBarIconHolder.fromIcon(icon);
            setIcon(index, holder);
        } else {
            holder.getIcon().icon = Icon.createWithResource(mContext, resourceId);
            holder.getIcon().contentDescription = contentDescription;
            handleSet(index, holder);
        }
    }

    /**
     * Signal icons need to be handled differently, because they can be
     * composite views
     */
    @Override
    public void setSignalIcon(String slot, WifiIconState state) {

        int index = getSlotIndex(slot);

        if (state == null) {
            removeIcon(index, 0);
            return;
        }

        StatusBarIconHolder holder = getIcon(index, 0);
        if (holder == null) {
            holder = StatusBarIconHolder.fromWifiIconState(state);
            setIcon(index, holder);
        } else {
            holder.setWifiState(state);
            handleSet(index, holder);
        }
    }

    /**
     * Accept a list of MobileIconStates, which all live in the same slot(?!), and then are sorted
     * by subId. Don't worry this definitely makes sense and works.
     * @param slot da slot
     * @param iconStates All of the mobile icon states
     */
    @Override
    public void setMobileIcons(String slot, List<MobileIconState> iconStates) {
        // --- Stacked dual-SIM path (additive, like 23.2) ---
        // Mirrors MobileIconsInteractor.kt:310 + DualSim.kt:98 + MobileUiAdapter.kt:57
        // When exactly two states with same numberOfLevels, collapse per-SIM icons
        // and show a single stacked icon in status_bar_stacked_mobile.
        // Hide via "settings put secure icon_blacklist stacked_mobile" if needed (23.2 behaviour).
        if (iconStates != null && iconStates.size() == 2) {
            // Build StackedCellular from the packed strengthId (SignalDrawable.getState packing)
            StackedCellular a = StackedHelper.fromStrengthId(iconStates.get(0).strengthId);
            StackedCellular b = StackedHelper.fromStrengthId(iconStates.get(1).strengthId);
            if (StackedHelper.isStackable(a, b)) {
                // Order primary = active data subId (StackedMobileIconViewModel.kt:68)
                int activeSubId = StackedHelper.getActiveDataSubId(mContext);
                MobileIconState primaryState;
                MobileIconState secondaryState;
                StackedCellular primaryCell;
                StackedCellular secondaryCell;
                int primarySub;
                int secondarySub;
                if (iconStates.get(0).subId == activeSubId) {
                    primaryState = iconStates.get(0);
                    secondaryState = iconStates.get(1);
                    primaryCell = a;
                    secondaryCell = b;
                    primarySub = primaryState.subId;
                    secondarySub = secondaryState.subId;
                } else if (iconStates.get(1).subId == activeSubId) {
                    primaryState = iconStates.get(1);
                    secondaryState = iconStates.get(0);
                    primaryCell = b;
                    secondaryCell = a;
                    primarySub = primaryState.subId;
                    secondarySub = secondaryState.subId;
                } else {
                    // No active match — keep original order as primary=0
                    primaryState = iconStates.get(0);
                    secondaryState = iconStates.get(1);
                    primaryCell = a;
                    secondaryCell = b;
                    primarySub = primaryState.subId;
                    secondarySub = secondaryState.subId;
                }
                DualSim dual = new DualSim(primarySub, primaryCell, secondarySub, secondaryCell);
                // Remove any existing per-SIM mobile icons and show stacked instead
                removeAllIconsForSlot(slot);
                // Stacked slot holds a single TYPE_STACKED-like holder (reuse mobile slot with TAG_PRIMARY + tag)
                // We store the dual as two MobileIconStates but render via stacked drawable — for 18.1
                // we keep it simple: store a single holder for the stacked slot with primary's state
                // plus secondary piggy-backed via the stacked drawable's DualSim.
                String stackedSlot = mContext.getString(
                        com.android.internal.R.string.status_bar_stacked_mobile);
                Slot stacked = getSlot(stackedSlot);
                int stackedIndex = getSlotIndex(stackedSlot);
                // Use subId as tag so re-entry replaces the same holder
                int stackedTag = primarySub; // primary as tag
                StatusBarIconHolder stackedHolder = stacked.getHolderForTag(stackedTag);
                if (stackedHolder == null) {
                    // Create a mobile-type holder tagged with primarySub so IconManager routes via TYPE_MOBILE
                    // but we will render via StackedMobileView (holder carries both states via dual)
                    stackedHolder = StatusBarIconHolder.fromMobileIconState(primaryState);
                    // Attach secondary info via extra field — simplest: stash dual in a static map
                    StackedIconStore.put(stackedSlot, stackedTag, dual, primaryState, secondaryState);
                    setIcon(stackedIndex, stackedHolder);
                } else {
                    stackedHolder.setMobileState(primaryState);
                    StackedIconStore.put(stackedSlot, stackedTag, dual, primaryState, secondaryState);
                    handleSet(stackedIndex, stackedHolder);
                }
                // Ensure no leftover stacked holders with other tags
                // (when active SIM changes, old primary tag may be stale)
                for (StatusBarIconHolder h : new ArrayList<>(stacked.getHolderList())) {
                    if (h.getTag() != stackedTag) {
                        removeIcon(stackedIndex, h.getTag());
                    }
                }
                return;
            }
            // Not stackable → fall through to normal path (and clear any stale stacked icons)
            String stackedSlot = mContext.getString(
                    com.android.internal.R.string.status_bar_stacked_mobile);
            removeAllIconsForSlot(stackedSlot);
            StackedIconStore.clear(stackedSlot);
        } else {
            // Not in stacked mode — ensure stacked slot is empty
            try {
                String stackedSlot = mContext.getString(
                        com.android.internal.R.string.status_bar_stacked_mobile);
                Slot stacked = getSlot(stackedSlot);
                if (stacked.hasIconsInSlot()) {
                    removeAllIconsForSlot(stackedSlot);
                    StackedIconStore.clear(stackedSlot);
                }
            } catch (Exception ignored) {
                // string resource may not exist on some builds before config merge
            }
        }

        Slot mobileSlot = getSlot(slot);
        int slotIndex = getSlotIndex(slot);

        // Reverse the sort order to show icons with left to right([Slot1][Slot2]..).
        // StatusBarIconList has UI design that first items go to the right of second items.
        Collections.reverse(iconStates);

        for (MobileIconState state : iconStates) {
            StatusBarIconHolder holder = mobileSlot.getHolderForTag(state.subId);
            if (holder == null) {
                holder = StatusBarIconHolder.fromMobileIconState(state);
                setIcon(slotIndex, holder);
            } else {
                holder.setMobileState(state);
                handleSet(slotIndex, holder);
            }
        }
    }

    @Override
    public void setExternalIcon(String slot) {
        int viewIndex = getViewIndex(getSlotIndex(slot), 0);
        int height = mContext.getResources().getDimensionPixelSize(
                R.dimen.status_bar_icon_drawing_size);
        mIconGroups.forEach(l -> l.onIconExternal(viewIndex, height));
    }

    //TODO: remove this (used in command queue and for 3rd party tiles?)
    @Override
    public void setIcon(String slot, StatusBarIcon icon) {
        setIcon(getSlotIndex(slot), icon);
    }

    /**
     * For backwards compatibility, in the event that someone gives us a slot and a status bar icon
     */
    private void setIcon(int index, StatusBarIcon icon) {
        if (icon == null) {
            removeAllIconsForSlot(getSlotName(index));
            return;
        }

        StatusBarIconHolder holder = StatusBarIconHolder.fromIcon(icon);
        setIcon(index, holder);
    }

    @Override
    public void setIcon(int index, @NonNull StatusBarIconHolder holder) {
        boolean isNew = getIcon(index, holder.getTag()) == null;
        super.setIcon(index, holder);

        if (isNew) {
            addSystemIcon(index, holder);
        } else {
            handleSet(index, holder);
        }
    }

    public void setIconVisibility(String slot, boolean visibility) {
        int index = getSlotIndex(slot);
        StatusBarIconHolder holder = getIcon(index, 0);
        if (holder == null || holder.isVisible() == visibility) {
            return;
        }

        holder.setVisible(visibility);
        handleSet(index, holder);
    }

    @Override
    public void setIconAccessibilityLiveRegion(String slotName, int accessibilityLiveRegion) {
        Slot slot = getSlot(slotName);
        if (!slot.hasIconsInSlot()) {
            return;
        }

        int slotIndex = getSlotIndex(slotName);
        List<StatusBarIconHolder> iconsToUpdate = slot.getHolderListInViewOrder();
        for (StatusBarIconHolder holder : iconsToUpdate) {
            int viewIndex = getViewIndex(slotIndex, holder.getTag());
            mIconGroups.forEach(l -> l.mGroup.getChildAt(viewIndex)
                    .setAccessibilityLiveRegion(accessibilityLiveRegion));
        }
    }

    public void removeIcon(String slot) {
        removeAllIconsForSlot(slot);
    }

    @Override
    public void removeIcon(String slot, int tag) {
        removeIcon(getSlotIndex(slot), tag);
    }

    @Override
    public void removeAllIconsForSlot(String slotName) {
        Slot slot = getSlot(slotName);
        if (!slot.hasIconsInSlot()) {
            return;
        }

        int slotIndex = getSlotIndex(slotName);
        List<StatusBarIconHolder> iconsToRemove = slot.getHolderListInViewOrder();
        for (StatusBarIconHolder holder : iconsToRemove) {
            int viewIndex = getViewIndex(slotIndex, holder.getTag());
            slot.removeForTag(holder.getTag());
            mIconGroups.forEach(l -> l.onRemoveIcon(viewIndex));
        }
    }

    @Override
    public void removeIcon(int index, int tag) {
        if (getIcon(index, tag) == null) {
            return;
        }
        super.removeIcon(index, tag);
        int viewIndex = getViewIndex(index, 0);
        mIconGroups.forEach(l -> l.onRemoveIcon(viewIndex));
    }

    private void handleSet(int index, StatusBarIconHolder holder) {
        int viewIndex = getViewIndex(index, holder.getTag());
        mIconGroups.forEach(l -> l.onSetIconHolder(viewIndex, holder));
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println(TAG + " state:");
        for (IconManager manager : mIconGroups) {
            if (manager.shouldLog()) {
                ViewGroup group = manager.mGroup;
                int N = group.getChildCount();
                pw.println("  icon views: " + N);
                for (int i = 0; i < N; i++) {
                    StatusIconDisplayable ic = (StatusIconDisplayable) group.getChildAt(i);
                    pw.println("    [" + i + "] icon=" + ic);
                }
            }
        }

        super.dump(pw);
    }

    public void dispatchDemoCommand(String command, Bundle args) {
        for (IconManager manager : mIconGroups) {
            if (manager.isDemoable()) {
                manager.dispatchDemoCommand(command, args);
            }
        }
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        loadDimens();
    }
}
