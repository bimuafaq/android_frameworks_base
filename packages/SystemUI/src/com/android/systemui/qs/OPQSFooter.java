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

package com.android.systemui.qs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.android.systemui.R;
import com.android.systemui.qs.TouchAnimator.Builder;
import com.android.systemui.statusbar.phone.SettingsButton;

public class OPQSFooter extends LinearLayout {

    private View mSettingsContainer;
    private SettingsButton mSettingsButton;
    private View mEdit;
    private TouchAnimator mFooterAnimator;
    private FrameLayout mFooterActions;
    private boolean mExpanded;
    private boolean mIsQQSPanel;

    public OPQSFooter(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEdit = findViewById(R.id.edit);
        mSettingsButton = findViewById(R.id.settings_button);
        mSettingsContainer = findViewById(R.id.settings_button_container);
        mFooterActions = findViewById(R.id.op_qs_footer_actions);
        mFooterAnimator = createFooterAnimator();
    }

    public void setExpansion(float headerExpansionFraction) {
        if (mFooterAnimator != null) {
            mFooterAnimator.setPosition(headerExpansionFraction);
        }
    }

    public void setIsQQSPanel(boolean isQQS) {
        mIsQQSPanel = isQQS;
        updateFooterActionsVisibility();
    }

    public void setExpanded(boolean expanded) {
        mExpanded = expanded;
        mEdit.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    public View getSettingsContainer() {
        return mSettingsContainer;
    }

    public View getSettingsButton() {
        return mSettingsButton;
    }

    public View getEditButton() {
        return mEdit;
    }

    public View getFooterActions() {
        return mFooterActions;
    }

    @Nullable
    private TouchAnimator createFooterAnimator() {
        return new Builder()
                .addFloat(mEdit, "alpha", 0, 0, 1)
                .addFloat(mSettingsButton, "alpha", 0, 0, 1)
                .setStartDelay(0.5f)
                .build();
    }

    private void updateFooterActionsVisibility() {
        mFooterActions.setVisibility(mIsQQSPanel ? View.GONE : View.VISIBLE);
    }
}
