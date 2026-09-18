/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.statusbar;

import static com.android.systemui.plugins.DarkIconDispatcher.getTint;
import static com.android.systemui.plugins.DarkIconDispatcher.isInArea;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_DOT;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_HIDDEN;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_ICON;

import android.content.Context;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.graph.SignalDrawable;
import com.android.systemui.DualToneHandler;
import com.android.systemui.R;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.MobileIconState;
import com.android.systemui.statusbar.policy.MobileDataActivityController;
import com.android.systemui.statusbar.policy.CombinedTelephonyIcons;

import lineageos.providers.LineageSettings;

public class StatusBarMobileView extends FrameLayout implements DarkReceiver,
        StatusIconDisplayable {
    private static final String TAG = "StatusBarMobileView";

    /// Used to show etc dots
    private StatusBarIconView mDotView;
    /// The main icon view
    private LinearLayout mMobileGroup;
    private String mSlot;
    private MobileIconState mState;
    private SignalDrawable mMobileDrawable;
    private View mInoutContainer;
    private ImageView mIn;
    private ImageView mOut;
    private ImageView mMobile, mMobileType, mMobileRoaming;
    private View mMobileRoamingSpace;
    private View mMobileTypeContainer;
    private ImageView mDataActivity; // Combined overlay
    private MobileDataActivityController mActivityController;
    private int mLastTint = 0;
    private int mVisibleState = -1;
    private DualToneHandler mDualToneHandler;
    private boolean mUseCombined;

    public static StatusBarMobileView fromContext(Context context, String slot) {
        LayoutInflater inflater = LayoutInflater.from(context);
        StatusBarMobileView v = (StatusBarMobileView)
                inflater.inflate(R.layout.status_bar_mobile_signal_group, null);

        v.setSlot(slot);
        v.init();
        v.setVisibleState(STATE_ICON);
        return v;
    }

    public StatusBarMobileView(Context context) {
        super(context);
    }

    public StatusBarMobileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusBarMobileView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StatusBarMobileView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        float translationX = getTranslationX();
        float translationY = getTranslationY();
        outRect.left += translationX;
        outRect.right += translationX;
        outRect.top += translationY;
        outRect.bottom += translationY;
    }

    private boolean mStyleObserverRegistered = false;

    private void init() {
        mDualToneHandler = new DualToneHandler(getContext());
        mMobileGroup = findViewById(R.id.mobile_group);
        mMobile = findViewById(R.id.mobile_signal);
        mMobileType = findViewById(R.id.mobile_type);
        mMobileRoaming = findViewById(R.id.mobile_roaming);
        mMobileRoamingSpace = findViewById(R.id.mobile_roaming_space);
        mIn = findViewById(R.id.mobile_in);
        mOut = findViewById(R.id.mobile_out);
        mInoutContainer = findViewById(R.id.inout_container);
        mMobileTypeContainer = findViewById(R.id.mobile_type_container);
        mDataActivity = findViewById(R.id.data_activity);

        mMobileDrawable = new SignalDrawable(getContext());
        if (mMobile != null) mMobile.setImageDrawable(mMobileDrawable);

        if (mDataActivity != null) {
            mActivityController = new MobileDataActivityController(mDataActivity);
        }
        registerStyleObserver();

        initDotView();
    }

    private void registerStyleObserver() {
        if (mStyleObserverRegistered) return;
        try {
            mUseCombined = CombinedTelephonyIcons.isCombinedStyle(getContext());
            getContext().getContentResolver().registerContentObserver(
                    LineageSettings.Secure.getUriFor(
                            LineageSettings.Secure.MOBILE_DATA_ICON_STYLE),
                    false, mStyleObserver, UserHandle.USER_ALL);
            mStyleObserverRegistered = true;
        } catch (Exception e) {
            // provider not ready / no permission — fallback OFF, no leak
            mUseCombined = false;
        }
    }

    private void unregisterStyleObserver() {
        if (!mStyleObserverRegistered) return;
        try {
            getContext().getContentResolver().unregisterContentObserver(mStyleObserver);
        } catch (Exception ignored) {}
        mStyleObserverRegistered = false;
    }

    private final ContentObserver mStyleObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) {
            boolean now;
            try {
                now = CombinedTelephonyIcons.isCombinedStyle(getContext());
            } catch (Exception e) {
                now = false;
            }
            if (now != mUseCombined) {
                mUseCombined = now;
                if (mState != null) {
                    initViewState();
                    requestLayout();
                }
            }
        }
    };

    private void initDotView() {
        mDotView = new StatusBarIconView(mContext, mSlot, null);
        mDotView.setVisibleState(STATE_DOT);

        int width = mContext.getResources().getDimensionPixelSize(R.dimen.status_bar_icon_size);
        LayoutParams lp = new LayoutParams(width, width);
        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        addView(mDotView, lp);
    }

    public void applyMobileState(MobileIconState state) {
        boolean requestLayout = false;
        if (state == null) {
            requestLayout = getVisibility() != View.GONE;
            setVisibility(View.GONE);
            mState = null;
        } else if (mState == null) {
            requestLayout = true;
            mState = state.copy();
            initViewState();
        } else if (!mState.equals(state)) {
            requestLayout = updateState(state.copy());
        }

        if (requestLayout) {
            requestLayout();
        }
    }

    private void initViewState() {
        if (mState == null) return;
        setContentDescription(mState.contentDescription);
        if (mMobileGroup != null) {
            mMobileGroup.setVisibility(mState.visible ? View.VISIBLE : View.GONE);
        }
        if (mUseCombined && !mState.visible && mActivityController != null) {
            mActivityController.stop();
        }
        if (mMobileDrawable != null) mMobileDrawable.setLevel(mState.strengthId);
        if (mState.typeId > 0) {
            if (mMobileType != null) {
                mMobileType.setContentDescription(mState.typeContentDescription);
                mMobileType.setImageResource(mState.typeId);
                mMobileType.setVisibility(View.VISIBLE);
            }
            if (mMobileTypeContainer != null) mMobileTypeContainer.setVisibility(View.VISIBLE);
        } else {
            if (mMobileType != null) mMobileType.setVisibility(View.GONE);
            if (mMobileTypeContainer != null) mMobileTypeContainer.setVisibility(View.GONE);
        }

        if (mMobileRoaming != null) mMobileRoaming.setVisibility(mState.roaming ? View.VISIBLE : View.GONE);
        if (mMobileRoamingSpace != null) mMobileRoamingSpace.setVisibility(mState.roaming ? View.VISIBLE : View.GONE);
        if (mUseCombined) {
            // Combined: TrafficStats polling like NetworkTraffic — not PhoneStateListener state
            if (mDataActivity != null && mState.typeId != 0 && mState.visible) {
                mDataActivity.setVisibility(View.VISIBLE);
                if (mActivityController != null) {
                    mActivityController.setTint(mLastTint);
                    mActivityController.start();
                }
            } else if (mDataActivity != null) {
                mDataActivity.setVisibility(View.GONE);
                if (mActivityController != null) mActivityController.stop();
            }
            if (mIn != null) mIn.setVisibility(View.GONE);
            if (mOut != null) mOut.setVisibility(View.GONE);
            if (mInoutContainer != null) mInoutContainer.setVisibility(View.GONE);
        } else {
            if (mDataActivity != null) {
                mDataActivity.setVisibility(View.GONE);
                if (mActivityController != null) mActivityController.stop();
            }
            if (mIn != null) mIn.setVisibility(mState.activityIn ? View.VISIBLE : View.GONE);
            if (mOut != null) mOut.setVisibility(mState.activityOut ? View.VISIBLE : View.GONE);
            if (mInoutContainer != null) {
                mInoutContainer.setVisibility((mState.activityIn || mState.activityOut)
                        ? View.VISIBLE : View.GONE);
            }
        }
    }

    private boolean updateState(MobileIconState state) {
        if (state == null || mState == null) return false;
        boolean needsLayout = false;

        setContentDescription(state.contentDescription);
        if (mMobileGroup != null && mState.visible != state.visible) {
            mMobileGroup.setVisibility(state.visible ? View.VISIBLE : View.GONE);
            needsLayout = true;
        }
        if (!state.visible && mUseCombined && mActivityController != null) {
            mActivityController.stop();
        }
        if (mMobileDrawable != null && mState.strengthId != state.strengthId) {
            mMobileDrawable.setLevel(state.strengthId);
        }
        if (mState.typeId != state.typeId) {
            needsLayout |= state.typeId == 0 || mState.typeId == 0;
            if (state.typeId != 0) {
                if (mMobileType != null) {
                    mMobileType.setContentDescription(state.typeContentDescription);
                    mMobileType.setImageResource(state.typeId);
                    mMobileType.setVisibility(View.VISIBLE);
                }
                if (mMobileTypeContainer != null) mMobileTypeContainer.setVisibility(View.VISIBLE);
            } else {
                if (mMobileType != null) mMobileType.setVisibility(View.GONE);
                if (mMobileTypeContainer != null) mMobileTypeContainer.setVisibility(View.GONE);
            }
        }

        if (mMobileRoaming != null) mMobileRoaming.setVisibility(state.roaming ? View.VISIBLE : View.GONE);
        if (mMobileRoamingSpace != null) mMobileRoamingSpace.setVisibility(state.roaming ? View.VISIBLE : View.GONE);
        if (mUseCombined) {
            if (mDataActivity != null && state.typeId != 0 && state.visible) {
                mDataActivity.setVisibility(View.VISIBLE);
                if (mActivityController != null) {
                    mActivityController.setTint(mLastTint);
                    mActivityController.start();
                }
            } else if (mDataActivity != null) {
                mDataActivity.setVisibility(View.GONE);
                if (mActivityController != null) mActivityController.stop();
            }
            if (mIn != null) mIn.setVisibility(View.GONE);
            if (mOut != null) mOut.setVisibility(View.GONE);
            if (mInoutContainer != null) mInoutContainer.setVisibility(View.GONE);
        } else {
            if (mDataActivity != null) {
                mDataActivity.setVisibility(View.GONE);
                if (mActivityController != null) mActivityController.stop();
            }
            if (mIn != null) mIn.setVisibility(state.activityIn ? View.VISIBLE : View.GONE);
            if (mOut != null) mOut.setVisibility(state.activityOut ? View.VISIBLE : View.GONE);
            if (mInoutContainer != null) {
                mInoutContainer.setVisibility((state.activityIn || state.activityOut)
                        ? View.VISIBLE : View.GONE);
            }
        }

        needsLayout |= state.roaming != mState.roaming
                || state.activityIn != mState.activityIn
                || state.activityOut != mState.activityOut;

        mState = state;
        return needsLayout;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerStyleObserver();
        if (mUseCombined && mDataActivity != null && mState != null
                && mState.typeId != 0 && mState.visible && mActivityController != null) {
            mActivityController.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mActivityController != null) mActivityController.stop();
        // keep observer for reattach — status bar toggles visibility, not destroy
        super.onDetachedFromWindow();
    }

    /** Called when view is permanently removed (e.g. slot removed). Prevents observer leak. */
    public void destroy() {
        if (mActivityController != null) mActivityController.destroy();
        unregisterStyleObserver();
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        float intensity = isInArea(area, this) ? darkIntensity : 0;
        mMobileDrawable.setTintList(
                ColorStateList.valueOf(mDualToneHandler.getSingleColor(intensity)));
        ColorStateList color = ColorStateList.valueOf(getTint(area, this, tint));
        mLastTint = getTint(area, this, tint);
        if (mActivityController != null) mActivityController.setTint(mLastTint);
        mIn.setImageTintList(color);
        mOut.setImageTintList(color);
        mMobileType.setImageTintList(color);
        if (mDataActivity != null) mDataActivity.setImageTintList(color);
        mMobileRoaming.setImageTintList(color);
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
        mLastTint = color;
        if (mActivityController != null) mActivityController.setTint(color);
        // We want the ability to change the theme from the one set by SignalDrawable in certain
        // surfaces. In this way, we can pass a theme to the view.
        mMobileDrawable.setTintList(
                ColorStateList.valueOf(mDualToneHandler.getSingleColor(intensity)));
        mIn.setImageTintList(list);
        mOut.setImageTintList(list);
        mMobileType.setImageTintList(list);
        if (mDataActivity != null) mDataActivity.setImageTintList(list);
        mMobileRoaming.setImageTintList(list);
        mDotView.setDecorColor(color);
    }

    @Override
    public void setDecorColor(int color) {
        mDotView.setDecorColor(color);
    }

    @Override
    public boolean isIconVisible() {
        return mState.visible;
    }

    @Override
    public void setVisibleState(int state, boolean animate) {
        if (state == mVisibleState) {
            return;
        }

        mVisibleState = state;
        switch (state) {
            case STATE_ICON:
                mMobileGroup.setVisibility(View.VISIBLE);
                mDotView.setVisibility(View.GONE);
                break;
            case STATE_DOT:
                mMobileGroup.setVisibility(View.INVISIBLE);
                mDotView.setVisibility(View.VISIBLE);
                break;
            case STATE_HIDDEN:
            default:
                mMobileGroup.setVisibility(View.INVISIBLE);
                mDotView.setVisibility(View.INVISIBLE);
                break;
        }
    }

    @Override
    public int getVisibleState() {
        return mVisibleState;
    }

    @VisibleForTesting
    public MobileIconState getState() {
        return mState;
    }

    @Override
    public String toString() {
        return "StatusBarMobileView(slot=" + mSlot + " state=" + mState + ")";
    }
}
