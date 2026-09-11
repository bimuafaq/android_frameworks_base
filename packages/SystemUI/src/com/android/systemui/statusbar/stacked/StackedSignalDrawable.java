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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * Drawable that renders the dual-SIM stacked signal bars.
 *
 * Direct port of the geometry in
 * {@code lineage_base/packages/SystemUI/src/com/android/systemui/statusbar/pipeline/shared/ui/composable/StackedMobileIcon.kt:141-255}
 * from Compose {@code DrawScope} to {@code android.graphics.Canvas}.
 *
 * <p>Composition:
 * <pre>
 *   primary   = bars on top (active-data SIM) — height grows per bar
 *   secondary = dots at bottom (3sp) — one dot per bar column
 *   exclamation cutout only for primary (when showExclamation==true) — same as 23.2
 * </pre>
 *
 * <p>Dimensions are taken verbatim from {@code StackedMobileIconDimensions:277} and
 * converted from {@code sp} via {@code TypedValue.applyDimension}.
 *
 * <p>This drawable is additive: 18.1's {@code SignalDrawable} is left untouched.
 */
public class StackedSignalDrawable extends Drawable {

    // --- Dimensions ported from StackedMobileIconDimensions:277 (sp values) ---
    private static final float ICON_HEIGHT_SP = 12f;
    private static final float ICON_PADDING_SP = 4f; // used by container, kept for reference
    private static final float ICON_SPACING_SP = 2f;
    private static final float BARS_VERTICAL_PADDING_SP = 1.5f;
    private static final float BARS_LEVEL_INCREMENT_SP = 1f;
    private static final float SECONDARY_BAR_HEIGHT_SP = 3f;
    private static final float ICON_WIDTH_FIVE_BARS_SP = 18.5f;
    private static final float ICON_WIDTH_FOUR_BARS_SP = 16f;
    private static final float HORIZONTAL_PADDING_FIVE_BARS_SP = 1.5f;
    private static final float HORIZONTAL_PADDING_FOUR_BARS_SP = 2f;
    private static final float BAR_BASE_HEIGHT_FIVE_BARS_SP = 3.5f;
    private static final float BAR_BASE_HEIGHT_FOUR_BARS_SP = 4.5f;
    private static final float EXCLAMATION_CUTOUT_RADIUS_SP = 5f;
    private static final float EXCLAMATION_DIAMETER_SP = 1.5f;
    private static final float EXCLAMATION_HEIGHT_SP = 4.5f;
    private static final float EXCLAMATION_VERTICAL_SPACING_SP = 1f;
    private static final float EXCLAMATION_HORIZONTAL_OFFSET_SP = 1f;

    private static final float INACTIVE_ALPHA = 0.3f;

    private final DisplayMetrics mMetrics;
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCutoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mExclamationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private DualSim mDualSim;
    private int mTintColor = Color.WHITE;
    private int mAlpha = 255;

    public StackedSignalDrawable(Context context) {
        Resources res = context.getResources();
        mMetrics = res.getDisplayMetrics();
        mCutoutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        mCutoutPaint.setColor(Color.TRANSPARENT);
    }

    public void setDualSim(DualSim dualSim) {
        mDualSim = dualSim;
        invalidateSelf();
    }

    public void setTintColor(int color) {
        mTintColor = color;
        invalidateSelf();
    }

    private float sp(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, mMetrics);
    }

    @Override
    public int getIntrinsicWidth() {
        if (mDualSim == null) return (int) sp(ICON_WIDTH_FOUR_BARS_SP);
        int n = Math.max(mDualSim.primary.numberOfLevels, mDualSim.secondary.numberOfLevels) - 1;
        if (n <= 0) n = 4;
        if (n == 5) return (int) sp(ICON_WIDTH_FIVE_BARS_SP);
        return (int) sp(ICON_WIDTH_FOUR_BARS_SP);
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) sp(ICON_HEIGHT_SP);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mDualSim == null) return;

        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;

        final boolean isRtl = getLayoutDirection() == android.util.LayoutDirection.RTL;

        // Offscreen for SRC_IN cutout — same role as Compose CompositingStrategy.Offscreen:158
        int saveCount = canvas.saveLayer(
                bounds.left, bounds.top, bounds.right, bounds.bottom, null);

        if (isRtl) {
            canvas.save();
            canvas.translate(bounds.width(), 0);
            canvas.scale(-1f, 1f);
        }

        int numberOfBars = Math.max(mDualSim.primary.numberOfLevels,
                mDualSim.secondary.numberOfLevels) - 1;
        if (numberOfBars <= 0) numberOfBars = 4;
        boolean fiveBars = numberOfBars == 5;

        float barBaseHeight = sp(fiveBars ? BAR_BASE_HEIGHT_FIVE_BARS_SP : BAR_BASE_HEIGHT_FOUR_BARS_SP);
        float hPad = sp(fiveBars ? HORIZONTAL_PADDING_FIVE_BARS_SP : HORIZONTAL_PADDING_FOUR_BARS_SP);
        float vPad = sp(BARS_VERTICAL_PADDING_SP);
        float dotH = sp(SECONDARY_BAR_HEIGHT_SP);
        float inc = sp(BARS_LEVEL_INCREMENT_SP);

        float totalHPad = hPad * (numberOfBars - 1);
        float availableW = bounds.width() - totalHPad;
        if (availableW <= 0) availableW = bounds.width();
        float barW = availableW / numberOfBars;
        float w = bounds.width();
        float h = bounds.height();

        // Compose: dotY = size.height - dotHeight; barY = dotY - vPad - barH
        float dotY = h - dotH;

        float x = 0f;
        for (int bar = 1; bar <= numberOfBars; bar++) {
            // Secondary — dots at bottom (StackedMobileIcon.kt:173)
            if (bar <= mDualSim.secondary.numberOfLevels) {
                drawBar(canvas, mDualSim.secondary.level, bar,
                        x, dotY, barW, dotH);
            }
            // Primary — bars on top (StackedMobileIcon.kt:185)
            if (bar <= mDualSim.primary.numberOfLevels) {
                float barH = barBaseHeight + inc * (bar - 1);
                float barY = dotY - vPad - barH;
                if (barY < 0) barY = 0;
                drawBar(canvas, mDualSim.primary.level, bar,
                        x, barY, barW, barH);
            }
            x += barW + hPad;
        }

        // Exclamation cutout only for primary — StackedMobileIcon.kt:200/224
        if (mDualSim.primary.showExclamation) {
            drawExclamationCutout(canvas, w, h);
        }

        if (isRtl) canvas.restore();
        canvas.restoreToCount(saveCount);
    }

    private void drawBar(Canvas canvas, int level, int bar,
            float x, float y, float w, float h) {
        int color = mTintColor;
        int alpha = mAlpha;
        if (level < bar) {
            alpha = (int) (alpha * INACTIVE_ALPHA);
        }
        mBarPaint.setColor(color);
        mBarPaint.setAlpha(alpha);
        mBarPaint.setStyle(Paint.Style.FILL);
        float radius = w / 2f;
        RectF rect = new RectF(x, y, x + w, y + h);
        canvas.drawRoundRect(rect, radius, radius, mBarPaint);
    }

    /**
     * Port of {@code drawExclamationCutout} in {@code StackedMobileIcon.kt:224}.
     * Uses {@code SRC_IN} with transparent to punch a hole (same as Compose
     * {@code drawCircle(Transparent, BlendMode.SrcIn)}).
     */
    private void drawExclamationCutout(Canvas canvas, float width, float height) {
        float diameter = sp(EXCLAMATION_DIAMETER_SP);
        float radius = diameter / 2f;
        float totalH = sp(EXCLAMATION_HEIGHT_SP) + sp(EXCLAMATION_VERTICAL_SPACING_SP) + diameter;
        float dotCx = width - sp(EXCLAMATION_HORIZONTAL_OFFSET_SP);
        float dotCy = height - radius;
        float markLeft = dotCx - radius;
        float markTop = height - totalH;
        float cutoutCx = dotCx;
        float cutoutCy = height - totalH / 2f;
        float cutoutR = sp(EXCLAMATION_CUTOUT_RADIUS_SP);

        // Transparent hole
        canvas.drawCircle(cutoutCx, cutoutCy, cutoutR, mCutoutPaint);

        // Top bar of exclamation
        mExclamationPaint.setColor(mTintColor);
        mExclamationPaint.setAlpha(mAlpha);
        mExclamationPaint.setStyle(Paint.Style.FILL);
        float r = radius;
        RectF markRect = new RectF(markLeft, markTop, markLeft + diameter, markTop + sp(EXCLAMATION_HEIGHT_SP));
        canvas.drawRoundRect(markRect, r, r, mExclamationPaint);

        // Bottom dot
        canvas.drawCircle(dotCx, dotCy, radius, mExclamationPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mBarPaint.setColorFilter(colorFilter);
        mExclamationPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }
}
