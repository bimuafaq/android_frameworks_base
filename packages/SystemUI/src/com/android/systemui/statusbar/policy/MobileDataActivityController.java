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
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.widget.ImageView;

import com.android.systemui.R;

import java.lang.ref.WeakReference;

/**
 * Combined activity arrow controller — single overlay drawable (16x14, alpha 0.4 trick)
 * vs AOSP two ImageViews ic_activity_up/down.
 * 1:1 from NetworkTraffic: REFRESH_INTERVAL/JITTER/SPEED_THRESHOLD/BITS_PER_BYTE/KILO/Handler WeakReference
 * tapi MOBILE ONLY — tidak iterate LinkProperties + tether seperti NetworkTraffic.
 * Layout not touched — only net activity.
 * 4-icon mode: none/in/out/inout — inout when both directions active and balanced
 * (aligns with TelephonyManager.DATA_ACTIVITY_INOUT=3).
 */
public class MobileDataActivityController {

    private static final int MESSAGE_TYPE_PERIODIC_REFRESH = 0;
    private static final int REFRESH_INTERVAL = 2000;
    private static final float REFRESH_INTERVAL_JITTER = 0.95f;
    private static final long SPEED_THRESHOLD_KBPS = 10;
    private static final float BITS_PER_BYTE = 8f;
    private static final float KILO = 1000f;

    private final WeakReference<ImageView> mTargetRef;
    private final PollHandler mHandler;

    private long mTxKbps;
    private long mRxKbps;
    private long mLastTxBytes = -1;
    private long mLastRxBytes = -1;
    private long mLastUpdateTime;
    private int mCurrentRes = 0;
    private int mTint = 0;
    private boolean mRunning = false;

    public MobileDataActivityController(ImageView target) {
        mTargetRef = new WeakReference<>(target);
        mHandler = new PollHandler(this);
    }

    public void setTint(int tint) { mTint = tint; }

    public void start() {
        if (mRunning) return;
        mRunning = true;
        mLastTxBytes = -1;
        mLastRxBytes = -1;
        mLastUpdateTime = 0;
        mHandler.sendEmptyMessage(MESSAGE_TYPE_PERIODIC_REFRESH);
    }

    public void stop() {
        mRunning = false;
        mHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
    }

    public void destroy() {
        stop();
        mHandler.removeCallbacksAndMessages(null);
    }

    private void recalculateStats() {
        final long now = SystemClock.elapsedRealtime();
        final long timeDelta = now - mLastUpdateTime;
        if (timeDelta < REFRESH_INTERVAL * REFRESH_INTERVAL_JITTER) return;
        long txBytes = TrafficStats.getMobileTxBytes();
        long rxBytes = TrafficStats.getMobileRxBytes();
        if (txBytes == TrafficStats.UNSUPPORTED) txBytes = 0;
        if (rxBytes == TrafficStats.UNSUPPORTED) rxBytes = 0;
        final long txDelta = txBytes - mLastTxBytes;
        final long rxDelta = rxBytes - mLastRxBytes;
        if (mLastTxBytes != -1 && mLastRxBytes != -1 && timeDelta > 0 && txDelta >= 0 && rxDelta >= 0) {
            mTxKbps = (long) (txDelta * BITS_PER_BYTE / KILO / (timeDelta / KILO));
            mRxKbps = (long) (rxDelta * BITS_PER_BYTE / KILO / (timeDelta / KILO));
        } else if (mLastTxBytes == -1) {
            mTxKbps = 0;
            mRxKbps = 0;
        }
        mLastTxBytes = txBytes;
        mLastRxBytes = rxBytes;
        mLastUpdateTime = now;
    }

    private void displayStatsAndReschedule() {
        ImageView target = mTargetRef.get();
        if (target == null) return;
        // NPE guard: view detached
        if (target.getContext() == null) return;
        updateCombinedDrawable();
        mHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
        if (mRunning) mHandler.sendEmptyMessageDelayed(MESSAGE_TYPE_PERIODIC_REFRESH, REFRESH_INTERVAL);
    }

    private void updateCombinedDrawable() {
        ImageView target = mTargetRef.get();
        if (target == null || target.getContext() == null) return;
        int res;
        boolean txActive = mTxKbps > SPEED_THRESHOLD_KBPS;
        boolean rxActive = mRxKbps > SPEED_THRESHOLD_KBPS;
        if (!txActive && !rxActive) {
            res = R.drawable.stat_sys_data_no_inout;
        } else if (txActive && rxActive) {
            // Both directions active: dominant -> single, balanced -> inout (DATA_ACTIVITY_INOUT)
            if (mTxKbps > (mRxKbps + SPEED_THRESHOLD_KBPS)) res = R.drawable.stat_sys_data_out;
            else if (mRxKbps > (mTxKbps + SPEED_THRESHOLD_KBPS)) res = R.drawable.stat_sys_data_in;
            else res = R.drawable.stat_sys_data_inout;
        } else if (txActive) {
            res = R.drawable.stat_sys_data_out;
        } else {
            res = R.drawable.stat_sys_data_in;
        }
        if (res != mCurrentRes) {
            try {
                target.setImageResource(res);
                if (mTint != 0) target.setImageTintList(ColorStateList.valueOf(mTint));
                mCurrentRes = res;
            } catch (Exception ignored) {}
        }
    }

    private static class PollHandler extends Handler {
        private final WeakReference<MobileDataActivityController> mCtrl;
        PollHandler(MobileDataActivityController c) { super(Looper.getMainLooper()); mCtrl = new WeakReference<>(c); }
        @Override public void handleMessage(Message msg) {
            MobileDataActivityController c = mCtrl.get();
            if (c == null) return;
            if (msg.what == MESSAGE_TYPE_PERIODIC_REFRESH) { c.recalculateStats(); c.displayStatsAndReschedule(); }
        }
    }

    public static int getActivityDrawable(boolean in, boolean out) {
        if (in && out) return R.drawable.stat_sys_data_inout;
        if (in) return R.drawable.stat_sys_data_in;
        if (out) return R.drawable.stat_sys_data_out;
        return R.drawable.stat_sys_data_no_inout;
    }
    public static void bind(ImageView v, boolean in, boolean out, int tint) {
        if (v == null) return;
        int r = getActivityDrawable(in, out);
        try {
            v.setImageResource(r);
            if (tint != 0) v.setImageTintList(ColorStateList.valueOf(tint));
            v.setVisibility(android.view.View.VISIBLE);
        } catch (Exception ignored) {}
    }
    public static void applyTint(ImageView v, int tint) {
        if (v == null || v.getDrawable() == null) return;
        try { v.setImageTintList(ColorStateList.valueOf(tint)); } catch (Exception ignored) {}
    }
    public Drawable resolveDrawable(Context c, boolean in, boolean out) {
        if (c == null) return null;
        return c.getDrawable(getActivityDrawable(in, out));
    }
}
