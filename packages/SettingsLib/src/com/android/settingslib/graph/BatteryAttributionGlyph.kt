/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.settingslib.graph

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.PathParser

enum class BatteryAttributionGlyph(val width: Float, val height: Float, pathData: String) {
    BOLT(
        8f,
        9f,
        "M7.672,3.375L4.302,3.375L5.038,0.563C5.113,0.281 4.91,0 4.633,0C4.515,0 4.398,0.056 4.324,0.146L0.09,5.051C-0.102,5.276 0.047,5.625 0.335,5.625L3.705,5.625L2.969,8.438C2.895,8.719 3.097,9 3.374,9C3.492,9 3.609,8.944 3.684,8.854L7.917,3.949C8.109,3.724 7.96,3.375 7.672,3.375Z"
    ),
    PLUS(
        8.5f,
        8.5f,
        "M4.248,0C4.745,0 5.148,0.403 5.148,0.9V3.35H7.6C8.097,3.35 8.5,3.753 8.5,4.25C8.5,4.747 8.097,5.149 7.6,5.149H5.148V7.6C5.148,8.097 4.745,8.5 4.248,8.5C3.751,8.5 3.349,8.097 3.349,7.6V5.149H0.9C0.403,5.149 0,4.747 0,4.25C0,3.753 0.403,3.35 0.9,3.35H3.349V0.9C3.349,0.403 3.751,0 4.248,0Z"
    );

    val path: Path = PathParser.createPathFromPathData(pathData)
}

object BatteryAttributionRenderer {
    private const val CUTOUT_STROKE_WIDTH = 2f

    private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.TRANSPARENT
        it.style = Paint.Style.STROKE
        it.strokeWidth = CUTOUT_STROKE_WIDTH
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathBounds = RectF()

    fun draw(canvas: Canvas, glyph: BatteryAttributionGlyph, dst: RectF, color: Int) {
        glyph.path.computeBounds(pathBounds, true)
        val scale = minOf(dst.width() / glyph.width, dst.height() / glyph.height)
        val left = dst.left + (dst.width() - glyph.width * scale) / 2f - pathBounds.left * scale
        val top = dst.top + (dst.height() - glyph.height * scale) / 2f - pathBounds.top * scale

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawPath(glyph.path, cutoutPaint)
        glyphPaint.color = color
        canvas.drawPath(glyph.path, glyphPaint)
        canvas.restore()
    }
}