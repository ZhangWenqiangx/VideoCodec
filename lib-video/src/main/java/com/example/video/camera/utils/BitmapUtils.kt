package com.example.video.camera.utils

import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.util.Log

/**
 *  @author : zhang.wenqiang
 *  @date : 2020/12/29
 *  description :
 */
object BitmapUtils {

    /**
     * 将字符串绘制为bitmap
     */
    fun drawText2Bitmap(
        assetManager: AssetManager,
        resources: Resources,
        array: Array<String>
    ): Bitmap? {
        return try {

            val scale = resources.displayMetrics.density
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.WHITE
            paint.textSize = 5 * scale
            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.createFromAsset(assetManager, "JetBrainsMono.ttf")
            paint.setShadowLayer(1f, 0f, 1f, Color.DKGRAY)

            val maxLengthStr = array.findMaxLengthStr()
            val bmpWidth: Float = paint.measureText(maxLengthStr!![0], 0, maxLengthStr[0].length)
            val bmpHeight =
                ((paint.fontMetrics.bottom - paint.fontMetrics.top) * array.size).toInt()

            val bitmap = Bitmap.createBitmap(bmpWidth.toInt(), bmpHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val rowHeight = (bmpHeight / array.size).toFloat()
            var y = rowHeight
            array.forEach {
                canvas.drawText(it, 0f, y, paint)
                y += rowHeight
            }
            Log.d(
                "BitmapUtils",
                "bitmap " + "h->${bitmap.height} w->${bitmap.width} rowHeight->{$rowHeight}"
            )
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun getThumbnail(path: String): Bitmap? {
        val media = MediaMetadataRetriever()
        media.setDataSource(path)

        var thumbnail = media.getFrameAtTime()

        media.release()

        return thumbnail
    }
}