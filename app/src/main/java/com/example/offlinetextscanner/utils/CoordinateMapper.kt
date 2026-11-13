package com.example.offlinetextscanner.utils

import android.graphics.RectF
import com.example.offlinetextscanner.ocr.BoundingBox
import kotlin.math.max
import kotlin.math.min

object CoordinateMapper {
    enum class ScaleMode {
        FIT_CENTER,
        CENTER_CROP
    }

    data class ViewportTransform(val scale: Float, val offsetX: Float, val offsetY: Float)
    data class MappedPoint(val x: Float, val y: Float)
    data class MappedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    fun computeTransform(
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        scaleMode: ScaleMode = ScaleMode.FIT_CENTER,
        userZoom: Float = 1f,
        userPanX: Float = 0f,
        userPanY: Float = 0f
    ): ViewportTransform {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return ViewportTransform(1f, 0f, 0f)
        }
        val scaleX = viewWidth.toFloat() / imageWidth
        val scaleY = viewHeight.toFloat() / imageHeight
        val baseScale = when (scaleMode) {
            ScaleMode.FIT_CENTER -> min(scaleX, scaleY)
            ScaleMode.CENTER_CROP -> max(scaleX, scaleY)
        }
        val scale = baseScale * userZoom.coerceAtLeast(0.01f)
        return ViewportTransform(
            scale,
            (viewWidth - imageWidth * scale) / 2f + userPanX,
            (viewHeight - imageHeight * scale) / 2f + userPanY
        )
    }

    fun mapImagePointToView(
        imageX: Float,
        imageY: Float,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        scaleMode: ScaleMode = ScaleMode.FIT_CENTER,
        userZoom: Float = 1f,
        userPanX: Float = 0f,
        userPanY: Float = 0f
    ): MappedPoint {
        val transform = computeTransform(
            imageWidth, imageHeight, viewWidth, viewHeight,
            scaleMode, userZoom, userPanX, userPanY
        )
        return MappedPoint(
            imageX * transform.scale + transform.offsetX,
            imageY * transform.scale + transform.offsetY
        )
    }

    fun mapViewPointToImage(
        viewX: Float,
        viewY: Float,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        scaleMode: ScaleMode = ScaleMode.FIT_CENTER,
        userZoom: Float = 1f,
        userPanX: Float = 0f,
        userPanY: Float = 0f
    ): MappedPoint {
        val transform = computeTransform(
            imageWidth, imageHeight, viewWidth, viewHeight,
            scaleMode, userZoom, userPanX, userPanY
        )
        return MappedPoint(
            (viewX - transform.offsetX) / transform.scale,
            (viewY - transform.offsetY) / transform.scale
        )
    }

    fun mapImageBoxToView(
        box: BoundingBox,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        scaleMode: ScaleMode = ScaleMode.FIT_CENTER,
        userZoom: Float = 1f,
        userPanX: Float = 0f,
        userPanY: Float = 0f
    ): MappedRect {
        val transform = computeTransform(
            imageWidth, imageHeight, viewWidth, viewHeight,
            scaleMode, userZoom, userPanX, userPanY
        )
        return MappedRect(
            box.left * transform.scale + transform.offsetX,
            box.top * transform.scale + transform.offsetY,
            box.right * transform.scale + transform.offsetX,
            box.bottom * transform.scale + transform.offsetY
        )
    }

    fun mapViewRectToImage(
        viewRect: RectF,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        scaleMode: ScaleMode = ScaleMode.FIT_CENTER,
        userZoom: Float = 1f,
        userPanX: Float = 0f,
        userPanY: Float = 0f
    ): RectF {
        val first = mapViewPointToImage(
            min(viewRect.left, viewRect.right), min(viewRect.top, viewRect.bottom),
            imageWidth, imageHeight, viewWidth, viewHeight, scaleMode,
            userZoom, userPanX, userPanY
        )
        val second = mapViewPointToImage(
            max(viewRect.left, viewRect.right), max(viewRect.top, viewRect.bottom),
            imageWidth, imageHeight, viewWidth, viewHeight, scaleMode,
            userZoom, userPanX, userPanY
        )
        return RectF(first.x, first.y, second.x, second.y)
    }
}
