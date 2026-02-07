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

class OneUIBatteryDrawable(private val context: Context, frameColor: Int) : Drawable() {

    private val perimeterPath = Path()
    private val scaledPerimeter = Path()
    private val fillMask = Path()
    private val scaledFill = Path()
    private val fillRect = RectF()
    private val levelRect = RectF()
    private val levelPath = Path()
    private val unifiedPath = Path()
    private val scaleMatrix = Matrix()
    private val padding = Rect()

    private var intrinsicHeight: Int
    private var intrinsicWidth: Int

    private var baseWidth: Float = 0f
    private var baseHeight: Float = 0f
    private var baseTextSize: Float = 0f
    private var baseRadius: Float = 0f

    private var colorLevels: IntArray
    private var fillColor: Int = Color.WHITE
    private var backgroundColor: Int = Color.WHITE
    private var levelColor: Int = Color.WHITE
    private var dualTone = true
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
        p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
    }

    private var charging = false
    private var powerSaveEnabled = false
    private var mShowPercent = true

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
    
    fun getBatteryLevel(): Int {
        return batteryLevel
    }

    override fun draw(c: Canvas) {
        c.saveLayer(null, null)
        unifiedPath.reset()
        levelPath.reset()
        levelRect.set(fillRect)
        
        val fillFraction = batteryLevel / 100f
        val fillTop = if (batteryLevel >= 95) fillRect.right
        else fillRect.right - (fillRect.width() * (1 - fillFraction))

        levelRect.right = Math.floor(fillTop.toDouble()).toFloat()
        levelPath.addRect(levelRect, Path.Direction.CCW)

        unifiedPath.addPath(scaledPerimeter)
        
        if (!dualTone) {
            unifiedPath.op(levelPath, Path.Op.UNION)
        }
        fillPaint.color = levelColor

        val mergedPath = Path()
        mergedPath.reset()

        val scaleFactor = if (baseHeight > 0) bounds.height() / baseHeight else 1f
        textPaint.textSize = baseTextSize * scaleFactor

        val textY = bounds.centerY() - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2
        val textX = bounds.width() * 0.5f
        
        val textPath = Path()
        if (mShowPercent) {
             textPaint.getTextPath(
                batteryLevel.toString(), 0, batteryLevel.toString().length, textX, textY, textPath
            )
            mergedPath.addPath(textPath)
        }
        
        unifiedPath.op(textPath, Path.Op.DIFFERENCE)
        
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
                return if (i == colorLevels.size - 2) {
                    fillColor
                } else {
                    color
                }
            }
            i += 2
        }
        return color
    }

    override fun setAlpha(alpha: Int) {}

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

    fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        updateSize()
    }

    fun setColors(fgColor: Int, bgColor: Int, singleToneColor: Int) {
        fillColor = if (dualTone) fgColor else singleToneColor
        fillPaint.color = fillColor
        dualToneBackgroundFill.color = 0xFFB1B1B1.toInt()
        dualToneBackgroundFill.alpha = 255
        levelColor = batteryColorForLevel(batteryLevel)
        invalidateSelf()
    }

    private fun updateSize() {
        val b = bounds
        if (b.isEmpty) {
            scaleMatrix.setScale(1f, 1f)
        } else {
            val sx = b.right / baseWidth
            val sy = b.bottom / baseHeight
            scaleMatrix.setScale(sx, sy)
        }
        perimeterPath.transform(scaleMatrix, scaledPerimeter)
        fillMask.transform(scaleMatrix, scaledFill)
        scaledFill.computeBounds(fillRect, true)
    }

    private fun loadPaths() {
        val radius = baseRadius        
        perimeterPath.addRoundRect(RectF(0f, 0f, baseWidth, baseHeight), radius, radius, Path.Direction.CW)
        fillMask.addRoundRect(RectF(0f, 0f, baseWidth, baseHeight), radius, radius, Path.Direction.CW)
        fillMask.computeBounds(fillRect, true)
        dualTone = true
    }

    companion object {
        private const val CRITICAL_LEVEL = 15
    }
}
