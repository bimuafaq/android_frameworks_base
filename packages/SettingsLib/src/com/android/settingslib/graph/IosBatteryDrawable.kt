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

class IosBatteryDrawable(private val context: Context, frameColor: Int) : Drawable() {

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
    
    private val buttonPath = Path()
    private val scaledButton = Path()

    private var intrinsicHeight: Int
    private var intrinsicWidth: Int

    private var baseWidth: Float = 0f
    private var baseHeight: Float = 0f
    private var baseTextSize: Float = 0f
    private var baseRadius: Float = 0f
    
    private var gap: Float = 0f
    private var bodyWidth: Float = 0f
    private var buttonWidth: Float = 0f
    private var buttonHeight: Float = 0f

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
    
    fun getBatteryLevel(): Int {
        return batteryLevel
    }

    override fun draw(c: Canvas) {
        if (batteryLevel == -1) return
        val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        alphaPaint.alpha = drawableAlpha
        c.saveLayer(null, alphaPaint)
        
        unifiedPath.reset()
        levelPath.reset()
        levelRect.set(fillRect)
        
        val fillFraction = batteryLevel / 100f
        val fillRight = fillRect.right * fillFraction

        levelRect.right = Math.floor(fillRight.toDouble()).toFloat()
        levelPath.addRect(levelRect, Path.Direction.CCW)

        // Setup Text Path for "DIFFERENCE" logic
        val textPath = Path()
        val bodyRect = RectF()
        scaledPerimeter.computeBounds(bodyRect, true)
        val cx = bodyRect.centerX()
        val cy = bodyRect.centerY()
        
        if (mShowPercent && batteryLevel != 100) {
            val scaleFactor = if (baseHeight > 0) bounds.height() / baseHeight else 1f
            textPaint.textSize = baseTextSize * scaleFactor
            val textY = cy - ((textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f)
            textPaint.getTextPath(batteryLevel.toString(), 0, batteryLevel.toString().length, cx, textY, textPath)
        }

        // Draw Background (Track) with Text subtracted
        val backgroundPath = Path()
        backgroundPath.addPath(scaledPerimeter)
        backgroundPath.addPath(scaledButton)
        backgroundPath.op(textPath, Path.Op.DIFFERENCE)
        c.drawPath(backgroundPath, dualToneBackgroundFill)

        // Draw Fill with Text subtracted
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

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha
        invalidateSelf()
    }

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
        buttonPath.transform(scaleMatrix, scaledButton)
        fillMask.transform(scaleMatrix, scaledFill)
        scaledFill.computeBounds(fillRect, true)
    }

    private fun loadPaths() {
        gap = baseWidth * (0.5f / 26f) // Small spasi (gap)
        bodyWidth = baseWidth * (23.5f / 26f)
        buttonWidth = baseWidth - bodyWidth - gap
        buttonHeight = baseHeight * (6f / 13f)
        
        perimeterPath.reset()
        perimeterPath.addRoundRect(RectF(0f, 0f, bodyWidth, baseHeight), baseRadius, baseRadius, Path.Direction.CW)
        
        buttonPath.reset()
        val buttonTop = (baseHeight - buttonHeight) / 2f
        val buttonLeft = bodyWidth + gap
        val buttonRight = baseWidth
        val buttonBottom = buttonTop + buttonHeight
        
        val buttonRect = RectF(buttonLeft, buttonTop, buttonRight, buttonBottom)
        
        // Draw true half-circle (D-shape)
        buttonPath.moveTo(buttonLeft, buttonTop)
        // arcTo(oval, startAngle, sweepAngle, forceMoveTo)
        // Oval should be centered at the right edge
        val oval = RectF(buttonLeft - buttonWidth, buttonTop, buttonRight, buttonBottom)
        buttonPath.arcTo(oval, 270f, 180f, false)
        buttonPath.lineTo(buttonLeft, buttonBottom)
        buttonPath.close()

        fillMask.reset()
        fillMask.addPath(perimeterPath)
        fillMask.computeBounds(fillRect, true)
        dualTone = true
    }

    companion object {
        private const val CRITICAL_LEVEL = 15
    }
}
