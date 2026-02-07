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

class IosBatteryDrawable(private val context: Context, frameColor: Int) : Drawable() {

    private val perimeterPath = Path()
    private val scaledPerimeter = Path()
    private val fillRect = RectF()
    private val levelRect = RectF()
    private val levelPath = Path()
    private val textPath = Path()
    private val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scaleMatrix = Matrix()

    private val buttonPath = Path()
    private val scaledButton = Path()

    private var intrinsicHeight: Int
    private var intrinsicWidth: Int

    private var baseWidth: Float = 0f
    private var baseHeight: Float = 0f
    private var baseTextSize: Float = 0f
    private var baseRadius: Float = 0f

    private var bodyWidth: Float = 0f

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

        val widthId = res.getIdentifier("status_bar_battery_icon_ios_width", "dimen", context.packageName)
        val heightId = res.getIdentifier("status_bar_battery_icon_ios_height", "dimen", context.packageName)
        val textSizeId = res.getIdentifier("status_bar_battery_icon_ios_text_size", "dimen", context.packageName)
        val radiusId = res.getIdentifier("status_bar_battery_icon_ios_radius", "dimen", context.packageName)

        if (widthId != 0 && heightId != 0) {
            intrinsicWidth = res.getDimensionPixelSize(widthId)
            intrinsicHeight = res.getDimensionPixelSize(heightId)
        } else {
            intrinsicWidth = (26f * density).toInt()
            intrinsicHeight = (13f * density).toInt()
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
            baseRadius = 4f * density
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

        loadPaths()
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

        levelPath.reset()
        textPath.reset()
        levelRect.set(fillRect)

        val fillFraction = batteryLevel / 100f
        val fillRight = fillRect.right * fillFraction

        levelRect.right = floor(fillRight)
        levelPath.addRect(levelRect, Path.Direction.CCW)

        val cx = fillRect.centerX()
        val cy = fillRect.centerY()

        if (mShowPercent && batteryLevel != 100) {
            val scaleFactor = if (baseHeight > 0) bounds.height() / baseHeight else 1f
            textPaint.textSize = baseTextSize * scaleFactor
            val textY = cy - ((textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f)
            textPaint.getTextPath(batteryLevel.toString(), 0, batteryLevel.toString().length, cx, textY, textPath)
        }

        // Background (track) with text subtracted
        val backgroundPath = Path()
        backgroundPath.addPath(scaledPerimeter)
        backgroundPath.addPath(scaledButton)
        backgroundPath.op(textPath, Path.Op.DIFFERENCE)
        c.drawPath(backgroundPath, dualToneBackgroundFill)

        // Fill with text subtracted
        val fillDrawPath = Path()
        fillDrawPath.set(scaledPerimeter)
        fillDrawPath.op(levelPath, Path.Op.INTERSECT)
        fillDrawPath.op(textPath, Path.Op.DIFFERENCE)

        fillPaint.color = levelColor
        c.drawPath(fillDrawPath, fillPaint)

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
        textPaint.colorFilter = colorFilter
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
        val b = bounds
        scaleMatrix.setScale(
            if (b.isEmpty) 1f else b.right / baseWidth,
            if (b.isEmpty) 1f else b.bottom / baseHeight
        )
        perimeterPath.transform(scaleMatrix, scaledPerimeter)
        buttonPath.transform(scaleMatrix, scaledButton)
        scaledPerimeter.computeBounds(fillRect, true)
    }

    private fun loadPaths() {
        bodyWidth = baseWidth * (23.5f / 26f)
        val gap = baseWidth * (0.5f / 26f)
        val buttonWidth = baseWidth - bodyWidth - gap
        val buttonHeight = baseHeight * (6f / 13f)

        perimeterPath.reset()
        perimeterPath.addRoundRect(RectF(0f, 0f, bodyWidth, baseHeight), baseRadius, baseRadius, Path.Direction.CW)

        buttonPath.reset()
        val buttonTop = (baseHeight - buttonHeight) / 2f
        val buttonLeft = bodyWidth + gap
        val buttonRight = baseWidth
        val buttonBottom = buttonTop + buttonHeight

        // D-shape (half-circle) battery nub
        val oval = RectF(buttonLeft - buttonWidth, buttonTop, buttonRight, buttonBottom)
        buttonPath.moveTo(buttonLeft, buttonTop)
        buttonPath.arcTo(oval, 270f, 180f, false)
        buttonPath.lineTo(buttonLeft, buttonBottom)
        buttonPath.close()

        perimeterPath.computeBounds(fillRect, true)
    }

    companion object {
        private const val CRITICAL_LEVEL = 15
    }
}
