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
import android.graphics.Matrix
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
    SEC_BOLT(
        9f,
        9f,
        "M7.1225,3.8069L3.2436,8.3778C2.9203,8.6657 2.6059,8.3854 2.6956,8.148L4.1969,4.8537C4.2246,4.7779 4.1691,4.6984 4.0895,4.6984L2.2195,4.6984C1.8811,4.6984 1.6576,4.3448 1.8041,4.0393L3.3648,0.7626C3.4405,0.6023 3.6034,0.5 3.7802,0.5L5.2815,0.5C5.6287,0.5 5.851,0.8687 5.6893,1.1755L4.7941,2.8688C4.7524,2.9445 4.808,3.038 4.8951,3.038L6.7803,3.038C7.1793,3.038 7.3889,3.5102 7.1225,3.8069Z"
    ),
    PLUS(
        8.5f,
        8.5f,
        "M4.248,0C4.745,0 5.148,0.403 5.148,0.9V3.35H7.6C8.097,3.35 8.5,3.753 8.5,4.25C8.5,4.747 8.097,5.149 7.6,5.149H5.148V7.6C5.148,8.097 4.745,8.5 4.248,8.5C3.751,8.5 3.349,8.097 3.349,7.6V5.149H0.9C0.403,5.149 0,4.747 0,4.25C0,3.753 0.403,3.35 0.9,3.35H3.349V0.9C3.349,0.403 3.751,0 4.248,0Z"
    ),
    LEAF(
        9f,
        9f,
        "M5.1674,1.0288C3.3314,1.0288 1.842,2.5349 1.842,4.3917C1.842,4.8916 1.9675,5.3539 2.1592,5.7713C2.4601,5.6283 2.7201,5.4391 3.0118,5.2028L4.5923,3.8997C4.6461,3.8551 4.688,3.8226 4.7175,3.8026C4.9313,3.661 5.219,3.7131 5.3663,3.9221C5.5213,4.1308 5.4845,4.4217 5.2852,4.5856L3.8034,5.8162C3.1841,6.3307 2.6531,6.7108 1.8642,6.9122C1.5619,7.0166 1.2521,7.0763 0.9426,7.0763C0.6992,7.0763 0.5,7.2777 0.5,7.5238C0.5,7.7698 0.6992,7.9712 0.9426,7.9712C1.7536,7.9712 2.5498,7.6879 3.2871,7.1734C3.8253,7.5459 4.4742,7.7698 5.1747,7.7698C7.0106,7.7698 8.5,6.2634 8.5,4.4066L8.5,1.0437L5.1674,1.0288Z"
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
    private val transformMatrix = Matrix()

    fun path(glyph: BatteryAttributionGlyph, dst: RectF): Path {
        glyph.path.computeBounds(pathBounds, true)
        val scale = minOf(dst.width() / glyph.width, dst.height() / glyph.height)
        val pathW = (pathBounds.right - pathBounds.left) * scale
        val pathH = (pathBounds.bottom - pathBounds.top) * scale
        val left = dst.left + (dst.width() - pathW) / 2f - pathBounds.left * scale
        val top = dst.top + (dst.height() - pathH) / 2f - pathBounds.top * scale

        transformMatrix.setScale(scale, scale)
        transformMatrix.postTranslate(left, top)
        val out = Path()
        glyph.path.transform(transformMatrix, out)
        return out
    }

    fun draw(canvas: Canvas, glyph: BatteryAttributionGlyph, dst: RectF, color: Int) {
        val transformed = path(glyph, dst)
        canvas.drawPath(transformed, cutoutPaint)
        glyphPaint.color = color
        canvas.drawPath(transformed, glyphPaint)
    }
}