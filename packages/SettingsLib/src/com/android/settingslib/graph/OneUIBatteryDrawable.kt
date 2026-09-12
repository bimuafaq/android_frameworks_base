/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settingslib.graph

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.TypedValue
import com.android.settingslib.R
import com.android.settingslib.Utils
import kotlin.math.floor

class OneUIBatteryDrawable(private val context: Context, frameColor: Int) : Drawable() {

    private val fillRect = RectF()
    private val levelRect = RectF()
    private val levelPath = Path()
    private val textPath = Path()
    private val unifiedPath = Path()
    private val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var intrinsicHeight: Int
    private var intrinsicWidth: Int

    private var baseWidth: Float = 0f
    private var baseHeight: Float = 0f
    private var baseTextSize: Float = 0f
    private var baseRadius: Float = 0f

    private var colorLevels: IntArray
    private var fillColor: Int = Color.WHITE
    private var levelColor: Int = Color.WHITE
    private var batteryLevel = 0

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
        p.color = frameColor
        p.style = Paint.Style.FILL_AND_STROKE
    }

    private val dualToneBackgroundFill = Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
        p.color = 0xFFB1B1B1.toInt()
        p.alpha = 255
        p.style = Paint.Style.FILL_AND_STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
        val res = context.resources
        val resId = res.getIdentifier("config_bodyFontFamily", "string", "android")
        val fontFamily = if (resId != 0) res.getString(resId) else "sans-serif-condensed"
        p.typeface = Typeface.create(fontFamily, Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
    }

    private var charging = false
    private var powerSaveEnabled = false
    private var mShowPercent = true
    private var drawableAlpha = 255

    init {
        val res = context.resources
        val density = res.displayMetrics.density

        val widthId = res.getIdentifier("status_bar_battery_icon_oneui_width", "dimen", context.packageName)
        val heightId = res.getIdentifier("status_bar_battery_icon_oneui_height", "dimen", context.packageName)
        val textSizeId = res.getIdentifier("status_bar_battery_icon_oneui_text_size", "dimen", context.packageName)
        val radiusId = res.getIdentifier("status_bar_battery_icon_oneui_radius", "dimen", context.packageName)

        if (widthId != 0 && heightId != 0) {
            intrinsicWidth = res.getDimensionPixelSize(widthId)
            intrinsicHeight = res.getDimensionPixelSize(heightId)
        } else {
            intrinsicWidth = (23f * density).toInt()
            intrinsicHeight = (15f * density).toInt()
        }

        if (textSizeId != 0) {
            baseTextSize = res.getDimensionPixelSize(textSizeId).toFloat()
        } else {
            baseTextSize = intrinsicHeight * 0.82f
        }

        baseWidth = intrinsicWidth.toFloat()
        baseHeight = intrinsicHeight.toFloat()

        if (radiusId != 0) {
            baseRadius = res.getDimensionPixelSize(radiusId).toFloat()
        } else {
            baseRadius = baseHeight / 2.0f
        }

        val levels = res.obtainTypedArray(R.array.batterymeter_color_levels)
        val colors = res.obtainTypedArray(R.array.batterymeter_color_values)
        val N = levels.length()
        colorLevels = IntArray(2 * N)
        for (i in 0 until N) {
            colorLevels[2 * i] = levels.getInt(i, 0)
            if (colors.getType(i) == TypedValue.TYPE_ATTRIBUTE) {
                colorLevels[2 * i + 1] = Utils.getColorAttrDefaultColor(context,
                        colors.getThemeAttributeId(i, 0))
            } else {
                colorLevels[2 * i + 1] = colors.getColor(i, 0)
            }
        }
        levels.recycle()
        colors.recycle()
    }

    fun setCharging(active: Boolean) {
        charging = active
        levelColor = batteryColorForLevel(batteryLevel)
        invalidateSelf()
    }

    fun setPowerSaveEnabled(enabled: Boolean) {
        powerSaveEnabled = enabled
        levelColor = batteryColorForLevel(batteryLevel)
        invalidateSelf()
    }

    fun setShowPercent(show: Boolean) {
        mShowPercent = show
        levelColor = batteryColorForLevel(batteryLevel)
        invalidateSelf()
    }

    fun setBatteryLevel(level: Int) {
        batteryLevel = level
        levelColor = batteryColorForLevel(batteryLevel)
        invalidateSelf()
    }

    fun getBatteryLevel(): Int = batteryLevel

    override fun draw(c: Canvas) {
        if (batteryLevel == -1) return
        alphaPaint.alpha = drawableAlpha
        c.saveLayer(null, alphaPaint)

        val attribution = attributionGlyph()

        unifiedPath.reset()
        fillRect.set(bounds)
        val radius = baseRadius * bounds.height() / baseHeight
        unifiedPath.addRoundRect(fillRect, radius, radius, Path.Direction.CW)

        levelPath.reset()
        levelRect.set(fillRect)

        val fillFraction = batteryLevel / 100f
        val fillTop = if (batteryLevel >= 95) fillRect.right
        else fillRect.right - fillRect.width() * (1 - fillFraction)

        levelRect.right = floor(fillTop)
        levelPath.addRect(levelRect, Path.Direction.CCW)
        fillPaint.color = levelColor

        val scaleFactor = if (baseHeight > 0) bounds.height() / baseHeight else 1f
        textPaint.textSize = baseTextSize * scaleFactor

        val textY = bounds.centerY() - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2
        val glyphSlot = if (attribution != null) bounds.height().toFloat() else 0f
        val textX = glyphSlot + (bounds.width() - glyphSlot) * 0.5f

        textPath.reset()
        if (mShowPercent) {
            textPaint.getTextPath(
                batteryLevel.toString(), 0, batteryLevel.toString().length, textX, textY, textPath
            )
        }

        unifiedPath.op(textPath, Path.Op.DIFFERENCE)
        if (attribution != null) {
            unifiedPath.op(BatteryAttributionRenderer.path(attribution, attrRect()), Path.Op.DIFFERENCE)
        }
        c.drawPath(unifiedPath, dualToneBackgroundFill)

        c.save()
        c.clipRect(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.left + bounds.width() * fillFraction,
            bounds.bottom.toFloat()
        )
        c.drawPath(unifiedPath, fillPaint)
        c.restore()
    }

    private fun batteryColorForLevel(level: Int): Int {
        return when {
            charging -> 0xFF34C759.toInt()
            powerSaveEnabled -> 0xFFFFCC0A.toInt()
            level > CRITICAL_LEVEL -> fillColor
            level >= 0 -> 0xFFFF0000.toInt()
            else -> getColorForLevel(level)
        }
    }

    private fun getColorForLevel(level: Int): Int {
        var thresh: Int
        var color = 0
        var i = 0
        while (i < colorLevels.size) {
            thresh = colorLevels[i]
            color = colorLevels[i + 1]
            if (level <= thresh) {
                return if (i == colorLevels.size - 2) fillColor else color
            }
            i += 2
        }
        return color
    }

    override fun setAlpha(alpha: Int) { drawableAlpha = alpha; invalidateSelf() }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        dualToneBackgroundFill.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.OPAQUE
    override fun getIntrinsicHeight(): Int = intrinsicHeight
    override fun getIntrinsicWidth(): Int = intrinsicWidth

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updateSize()
    }

    fun setColors(fgColor: Int, bgColor: Int, singleToneColor: Int) {
        fillColor = fgColor
        fillPaint.color = fillColor
        dualToneBackgroundFill.color = 0xFFB1B1B1.toInt()
        dualToneBackgroundFill.alpha = 255
        levelColor = batteryColorForLevel(batteryLevel)
        invalidateSelf()
    }

    private fun updateSize() {
        fillRect.set(bounds)
    }

    fun hasAttribution(): Boolean = attributionGlyph() != null

    fun getAttributionExtraWidth(heightPx: Int): Int = heightPx

    private fun attributionGlyph(): BatteryAttributionGlyph? = when {
        powerSaveEnabled -> BatteryAttributionGlyph.LEAF
        charging -> BatteryAttributionGlyph.BOLT
        else -> null
    }

    private fun attrRect(): RectF {
        val slot = bounds.height().toFloat()
        val size = slot * GLYPH_SIZE_FRACTION
        val left = (slot - size) / 2f
        val top = (bounds.height() - size) / 2f
        return RectF(left, top, left + size, top + size)
    }

    companion object {
        private const val CRITICAL_LEVEL = 15
        private const val GLYPH_SIZE_FRACTION = 0.6f
    }
}
