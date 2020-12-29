package com.example.video.camera.utils

import android.graphics.Bitmap
import android.opengl.GLES32
import android.opengl.GLUtils
import java.nio.ByteBuffer

/**
 *  @author : zhang.wenqiang
 *  @date : 2020/12/29
 *  description :
 */
object GlUtils {

    /**
     * 生成TextureId
     * @param bitmap 位图
     */
    @JvmStatic
    fun createTextureId(bitmap: Bitmap?): Int {
        var result = 0
        bitmap?.let {
            //生产一个纹理
            val textureIds = IntArray(1)
            GLES32.glGenTextures(1, textureIds, 0)
            //绑定为 2D纹理
            GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureIds[0])
            //设置环绕模式
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_REPEAT)
            GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_REPEAT)
            //设置过滤模式
            GLES32.glTexParameteri(
                GLES32.GL_TEXTURE_2D,
                GLES32.GL_TEXTURE_MIN_FILTER,
                GLES32.GL_LINEAR
            )
            GLES32.glTexParameteri(
                GLES32.GL_TEXTURE_2D,
                GLES32.GL_TEXTURE_MAG_FILTER,
                GLES32.GL_LINEAR
            )
            //绑定 bitmap到 textureIds[0] 这个2D纹理上
            GLUtils.texImage2D(GLES32.GL_TEXTURE_2D, 0, bitmap, 0)

            val bitmapBuffer = ByteBuffer.allocate(bitmap.height * bitmap.width * 4)
            bitmap.copyPixelsToBuffer(bitmapBuffer)
            //将bitmapBuffer位置移动到初始位置
            bitmapBuffer.flip()
            //设置内存大小绑定内存地址
            GLES32.glTexImage2D(
                GLES32.GL_TEXTURE_2D, 0, GLES32.GL_RGBA, bitmap.width, bitmap.height,
                0, GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, bitmapBuffer
            )
            //退出 纹理的设置，进入下一环节
            GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)
            result = textureIds[0]
        }
        return result
    }
}