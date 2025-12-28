package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import javax.inject.Inject;

public class AirplaneFloaterTile extends QSTileImpl<BooleanState> {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_floater_airplane);
    private boolean isOverlayShowing = false;
    private WindowManager mWindowManager;
    private ImageView mFloatingButton;
    private WindowManager.LayoutParams mParams;
    private Handler mHandler = new Handler();

    @Inject
    public AirplaneFloaterTile(QSHost host) {
        super(host);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        if (!isOverlayShowing) {
            showFloatingButton();
        } else {
            removeFloatingButton();
        }
        refreshState();
    }

    private void showFloatingButton() {
        if (mFloatingButton == null) {
            mFloatingButton = new ImageView(mContext);
            mFloatingButton.setImageResource(R.drawable.ic_floater_airplane);
            mFloatingButton.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(Color.parseColor("#2D2D2D"));
            mFloatingButton.setBackground(background);

            mFloatingButton.setPadding(25, 25, 25, 25);
            
            mFloatingButton.setAlpha(0.6f);

            mFloatingButton.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;
                private boolean isDragAction = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = mParams.x;
                            initialY = mParams.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            isDragAction = false;
                            mFloatingButton.setAlpha(0.8f);
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            int deltaX = (int) (event.getRawX() - initialTouchX);
                            int deltaY = (int) (event.getRawY() - initialTouchY);

                            if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                                isDragAction = true;
                                mParams.x = initialX + deltaX;
                                mParams.y = initialY + deltaY;
                                mWindowManager.updateViewLayout(mFloatingButton, mParams);
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            mFloatingButton.setAlpha(0.6f);
                            
                            if (!isDragAction) {
                                performNetworkRefresh();
                            }
                            return true;
                    }
                    return false;
                }
            });

            mParams = new WindowManager.LayoutParams(
                90, 90,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

            mParams.gravity = Gravity.TOP | Gravity.START;
            mParams.x = 50;
            mParams.y = 300;

            try {
                mWindowManager.addView(mFloatingButton, mParams);
                isOverlayShowing = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void removeFloatingButton() {
        if (mFloatingButton != null) {
            try {
                if (isOverlayShowing) {
                    mWindowManager.removeView(mFloatingButton);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mFloatingButton = null;
                isOverlayShowing = false;
            }
        }
    }

    private void performNetworkRefresh() {
        setAirplaneMode(true);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                setAirplaneMode(false);
            }
        }, 1000);
    }

    private void setAirplaneMode(boolean enable) {
        Settings.Global.putInt(mContext.getContentResolver(),
                               Settings.Global.AIRPLANE_MODE_ON, enable ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enable);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_floater_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = mContext.getString(R.string.quick_settings_floater_label);
        state.icon = mIcon;
        state.state = isOverlayShowing ? 2 : 1;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_AIRPLANEMODE;
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        removeFloatingButton();
    }

    @Override
    public Intent getLongClickIntent() { return null; }

    @Override
    protected void handleSetListening(boolean listening) {}

    @Override
    protected void handleLongClick() {}
}
