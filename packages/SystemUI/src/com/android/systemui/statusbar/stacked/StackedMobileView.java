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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.DualToneHandler;
import com.android.systemui.R;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.MobileIconState;

/**
 * View for the stacked dual-SIM icon on 18.1.
 *
 * Port of the layout + rendering in
 * {@code lineage_base/packages/SystemUI/src/com/android/systemui/statusbar/pipeline/shared/ui/composable/StackedMobileIcon.kt:91,153}
 *
 * <p>Structure (mirrors Compose Row):
 * <pre>
 *   [ RAT type icon ] [ stacked bars drawable ] [ roaming icon ]
 * </pre>
 * Activity indicators (in/out) are taken from the primary SIM state.
 *
 * <p>This view is additive — {@code StatusBarMobileView} is left untouched. Flag OFF
 * means this view is never instantiated.
 */
public class StackedMobileView extends FrameLayout implements DarkReceiver,
        StatusIconDisplayable {

    private static final String TAG = "StackedMobileView";

    private StatusBarIconView mDotView;
    private LinearLayout mStackedGroup;
    private String mSlot;
    private DualSim mDualSim;
    private int mVisibleState = -1;
    private DualToneHandler mDualToneHandler;

    // Views inside stacked_group
    private ImageView mStackedBars;
    private StackedSignalDrawable mStackedDrawable;
    private ImageView mRatIcon;
    private ImageView mRoamingIcon;
    private View mRoamingSpace;
    private View mInoutContainer;
    private ImageView mIn;
    private ImageView mOut;

    // Keep last applied state for diffing (primary's MobileIconState)
    private MobileIconState mPrimaryState;
    private MobileIconState mSecondaryState;

    public static StackedMobileView fromContext(Context context, String slot) {
        LayoutInflater inflater = LayoutInflater.from(context);
        StackedMobileView v = (StackedMobileView) inflater.inflate(
                R.layout.status_bar_stacked_mobile_signal_group, null);
        v.setSlot(slot);
        v.init();
        v.setVisibleState(StatusBarIconView.STATE_ICON);
        return v;
    }

    public StackedMobileView(Context context) {
        super(context);
    }

    public StackedMobileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StackedMobileView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        float tx = getTranslationX();
        float ty = getTranslationY();
        outRect.left += tx;
        outRect.right += tx;
        outRect.top += ty;
        outRect.bottom += ty;
    }

    private void init() {
        mDualToneHandler = new DualToneHandler(getContext());
        mStackedGroup = findViewById(R.id.stacked_group);
        mStackedBars = findViewById(R.id.stacked_bars);
        mRatIcon = findViewById(R.id.stacked_type);
        mRoamingIcon = findViewById(R.id.stacked_roaming);
        mRoamingSpace = findViewById(R.id.stacked_roaming_space);
        mIn = findViewById(R.id.stacked_in);
        mOut = findViewById(R.id.stacked_out);
        mInoutContainer = findViewById(R.id.stacked_inout_container);

        mStackedDrawable = new StackedSignalDrawable(getContext());
        if (mStackedBars != null) {
            mStackedBars.setImageDrawable(mStackedDrawable);
        }
        initDotView();
    }

    private void initDotView() {
        mDotView = new StatusBarIconView(mContext, mSlot, null);
        mDotView.setVisibleState(StatusBarIconView.STATE_DOT);
        int width = mContext.getResources().getDimensionPixelSize(
                R.dimen.status_bar_icon_size);
        LayoutParams lp = new LayoutParams(width, width);
        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        addView(mDotView, lp);
    }

    /**
     * Apply a stacked state derived from two MobileIconStates.
     * Caller (StatusBarIconControllerImpl) ensures isStackable == true and flag ON.
     */
    public void applyStackedState(DualSim dualSim,
            MobileIconState primary, MobileIconState secondary) {
        boolean requestLayout = false;
        if (dualSim == null) {
            requestLayout = getVisibility() != View.GONE;
            setVisibility(View.GONE);
            mDualSim = null;
            mPrimaryState = null;
            mSecondaryState = null;
            return;
        }
        if (mDualSim == null) requestLayout = true;

        boolean primaryChanged = mPrimaryState == null || !mPrimaryState.equals(primary);
        boolean secondaryChanged = mSecondaryState == null || !mSecondaryState.equals(secondary);

        mDualSim = dualSim;
        mPrimaryState = primary.copy();
        mSecondaryState = secondary.copy();

        if (mStackedDrawable != null) {
            mStackedDrawable.setDualSim(dualSim);
        }

        // Content description: join both like StackedMobileIconViewModel.kt:102
        String primaryDesc = primary.contentDescription;
        String secondaryDesc = secondary.contentDescription;
        if (primaryDesc != null && secondaryDesc != null) {
            setContentDescription(primaryDesc + " " + secondaryDesc);
        } else if (primaryDesc != null) {
            setContentDescription(primaryDesc);
        } else {
            setContentDescription(secondaryDesc);
        }

        // RAT type icon — from primary only (StackedMobileIconViewModel.kt:127)
        if (primary.typeId != 0) {
            mRatIcon.setContentDescription(primary.typeContentDescription);
            mRatIcon.setImageResource(primary.typeId);
            mRatIcon.setVisibility(View.VISIBLE);
        } else {
            mRatIcon.setVisibility(View.GONE);
        }

        // Roaming — from primary only (StackedMobileIconViewModel.kt:193)
        boolean roaming = primary.roaming;
        mRoamingIcon.setVisibility(roaming ? View.VISIBLE : View.GONE);
        mRoamingSpace.setVisibility(roaming ? View.VISIBLE : View.GONE);

        // Activity indicators from primary (StackedMobileIconViewModel.kt:139/152)
        boolean actIn = primary.activityIn;
        boolean actOut = primary.activityOut;
        mIn.setVisibility(actIn ? View.VISIBLE : View.GONE);
        mOut.setVisibility(actOut ? View.VISIBLE : View.GONE);
        mInoutContainer.setVisibility((actIn || actOut) ? View.VISIBLE : View.GONE);

        if (primaryChanged || secondaryChanged) requestLayout = true;

        setVisibility(View.VISIBLE);
        if (requestLayout) requestLayout();
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        float intensity = com.android.systemui.plugins.DarkIconDispatcher.isInArea(area, this)
                ? darkIntensity : 0;
        int singleTone = mDualToneHandler.getSingleColor(intensity);
        ColorStateList single = ColorStateList.valueOf(singleTone);
        if (mStackedDrawable != null) {
            mStackedDrawable.setTintColor(singleTone);
        }
        ColorStateList tintList = ColorStateList.valueOf(
                com.android.systemui.plugins.DarkIconDispatcher.getTint(area, this, tint));
        if (mRatIcon != null) mRatIcon.setImageTintList(tintList);
        if (mRoamingIcon != null) mRoamingIcon.setImageTintList(tintList);
        if (mIn != null) mIn.setImageTintList(tintList);
        if (mOut != null) mOut.setImageTintList(tintList);
        if (mStackedBars != null) {
            // stacked bars drawable handles tint via setTintColor above
        }
        mDotView.setDecorColor(tint);
        mDotView.setIconColor(tint, false);
    }

    @Override
    public String getSlot() {
        return mSlot;
    }

    public void setSlot(String slot) {
        mSlot = slot;
    }

    @Override
    public void setStaticDrawableColor(int color) {
        ColorStateList list = ColorStateList.valueOf(color);
        float intensity = color == Color.WHITE ? 0 : 1;
        int singleTone = mDualToneHandler.getSingleColor(intensity);
        if (mStackedDrawable != null) mStackedDrawable.setTintColor(singleTone);
        if (mRatIcon != null) mRatIcon.setImageTintList(list);
        if (mRoamingIcon != null) mRoamingIcon.setImageTintList(list);
        if (mIn != null) mIn.setImageTintList(list);
        if (mOut != null) mOut.setImageTintList(list);
        mDotView.setDecorColor(color);
    }

    @Override
    public void setDecorColor(int color) {
        mDotView.setDecorColor(color);
    }

    @Override
    public boolean isIconVisible() {
        return mDualSim != null && getVisibility() == View.VISIBLE;
    }

    @Override
    public void setVisibleState(int state, boolean animate) {
        if (state == mVisibleState) return;
        mVisibleState = state;
        switch (state) {
            case StatusBarIconView.STATE_ICON:
                if (mStackedGroup != null) mStackedGroup.setVisibility(View.VISIBLE);
                mDotView.setVisibility(View.GONE);
                break;
            case StatusBarIconView.STATE_DOT:
                if (mStackedGroup != null) mStackedGroup.setVisibility(View.INVISIBLE);
                mDotView.setVisibility(View.VISIBLE);
                break;
            case StatusBarIconView.STATE_HIDDEN:
            default:
                if (mStackedGroup != null) mStackedGroup.setVisibility(View.INVISIBLE);
                mDotView.setVisibility(View.INVISIBLE);
                break;
        }
    }

    @Override
    public void setVisibleState(int state) {
        setVisibleState(state, false);
    }

    @Override
    public int getVisibleState() {
        return mVisibleState;
    }

    @VisibleForTesting
    public DualSim getDualSim() {
        return mDualSim;
    }

    @Override
    public String toString() {
        return "StackedMobileView(slot=" + mSlot + " dual=" + mDualSim + ")";
    }
}
