/*
 * Copyright (C) 2021 The LineageOS Project
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

package com.android.systemui.qs;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.DateView;

/**
 * Self-contained container for clock + date in the QQS header.
 * Handles its own expand/collapse animation so QuickStatusBarHeader doesn't need
 * to know about the internals. Swap this class to change the clock/date design.
 *
 * Expects two child views: {@link Clock} (id=@id/clock) and {@link DateView} (id=@id/date).
 */
public class ClockDateContainer extends LinearLayout {

    private Clock mClockView;
    private DateView mDateView;
    private TouchAnimator mClockDateAnimator;

    // ponytail: configurable via setters, defaults match original commit
    private float mCollapsedScale = 1.0f;
    private float mExpandedScale = 2.0f;
    private float mGapDp = 6f;

    public ClockDateContainer(Context context) {
        this(context, null);
    }

    public ClockDateContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClockDateContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mClockView = findViewById(R.id.clock);
        mDateView = findViewById(R.id.date);
        rebuildAnimator();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) {
            rebuildAnimator();
        }
    }

    /**
     * Rebuild the expand/collapse animator.
     * Called on initial inflation and when layout changes (orientation, font scale, etc.).
     */
    private void rebuildAnimator() {
        if (mClockView == null || mDateView == null) return;

        // Pin clock scale pivot to left edge so it expands to the right
        mClockView.setPivotX(0);
        mClockView.setPivotY(mClockView.getHeight() / 2f);

        float density = getResources().getDisplayMetrics().density;
        float gap = mGapDp * density;

        // Estimate date dimensions for positioning — hardcode 14sp same as layout
        float clockTextSize = 14f * density;
        float estimatedHeight = clockTextSize * 1.3f;

        // Calculate date's expanded position: below the scaled clock
        float dateNaturalLeft = mDateView.getLeft();
        float dateExpandedX;
        if (dateNaturalLeft > 0) {
            dateExpandedX = -dateNaturalLeft;
        } else {
            float estimatedWidth = clockTextSize * 3.2f;
            dateExpandedX = -(estimatedWidth + gap);
        }
        float dateExpandedY = estimatedHeight + gap;

        // Adjust for RTL
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            dateExpandedX = -dateExpandedX;
        }

        mClockDateAnimator = new TouchAnimator.Builder()
                .addFloat(mClockView, "scaleX", mCollapsedScale, mExpandedScale)
                .addFloat(mClockView, "scaleY", mCollapsedScale, mExpandedScale)
                .addFloat(mDateView, "translationX", 0, dateExpandedX)
                .addFloat(mDateView, "translationY", 0, dateExpandedY)
                .build();
    }

    /**
     * Set the expansion fraction for the clock-date animation.
     * 0 = collapsed (QQS), 1 = fully expanded (QS).
     */
    public void setExpansion(float fraction) {
        if (mClockDateAnimator != null
                && fraction >= 0 && fraction <= 1f
                && !Float.isNaN(fraction)) {
            mClockDateAnimator.setPosition(fraction);
        }
    }

    // --- Configuration helpers — call before rebuildAnimator() takes effect ---

    public void setExpandedScale(float scale) {
        mExpandedScale = scale;
    }

    public void setCollapsedScale(float scale) {
        mCollapsedScale = scale;
    }

    public void setGapDp(float gapDp) {
        mGapDp = gapDp;
    }

    public Clock getClockView() {
        return mClockView;
    }

    public DateView getDateView() {
        return mDateView;
    }
}
