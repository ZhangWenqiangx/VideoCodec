package com.example.video.camera.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20

/**
 * shader创建工具类
 */
object ShaderUtils {
    /**
     * 创建着色器程序
     *
     * @param resources   ：Context.getResource()
     * @param vertexRes   :顶点着色器资源文件
     * @param fragmentRes ：片元着色器资源文件
     * @return int - 着色器程序
     */
    @JvmStatic
    fun createProgram(
        resources: Resources,
        vertexRes: String,
        fragmentRes: String
    ): Int {
        return createProgram(
            loadAssetSource(resources, vertexRes),
            loadAssetSource(resources, fragmentRes)
        )
    }

    /**
     * 从资源文件读取着色器代码
     *
     * @param resources ：Context.getResource()
     * @param shaderRes :assets文件夹下 着色器 代码文件
     * @return ：String -  读取的着色器代码
     */
    private fun loadAssetSource(
        resources: Resources,
        shaderRes: String
    ): String? {
        val result = StringBuilder()
        try {
            val `is` = resources.assets.open(shaderRes)
            var ch: Int
            val buffer = ByteArray(1024)
            while (-1 != `is`.read(buffer).also { ch = it }) {
                result.append(String(buffer, 0, ch))
            }
        } catch (e: Exception) {
            return null
        }
        return result.toString().replace("\\r\\n".toRegex(), "\n")
    }

    /**
     * 创建着色器程序
     *
     * @param vertexShaderCode   ：顶点着色器代码
     * @param fragmentShaderCode ：片元着色器代码
     * @return program
     */
    private fun createProgram(
        vertexShaderCode: String?,
        fragmentShaderCode: String?
    ): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        if (vertexShader == 0) {
            return 0
        }
        val fragmentShader =
            loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        if (fragmentShader == 0) {
            return 0
        }
        var program = GLES20.glCreateProgram()
        if (0 != program) {
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
        return program
    }

    /**
     * 加载shader
     *
     * @param type      ：shader类型
     * @param shadeCode ：着色器代码
     * @return shader
     */
    private fun loadShader(type: Int, shadeCode: String?): Int {
        var shader = GLES20.glCreateShader(type)
        if (0 != shader) {
            GLES20.glShaderSource(shader, shadeCode)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                GLES20.glDeleteShader(shader)
                shader = 0
            }
        }
        return shader
    }

    /**
     * 创建水印图片
     *
     * @param text      文本
     * @param textSize  文字大小
     * @param textColor 颜色
     * @param bgColor   背景色
     * @param padding   间距
     * @return Bitmap
     */
    @JvmStatic
    fun createTextImage(
        text: String,
        textSize: Int,
        textColor: String?,
        bgColor: String?,
        padding: Int
    ): Bitmap {
        val paint = Paint()
        paint.color = Color.parseColor(textColor)
        paint.textSize = textSize.toFloat()
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        val width = paint.measureText(text, 0, text.length)
        val top = paint.fontMetrics.top
        val bottom = paint.fontMetrics.bottom
        val bm = Bitmap.createBitmap(
            (width + padding * 2).toInt(),
            (bottom - top + padding * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bm)
        canvas.drawColor(Color.parseColor(bgColor))
        canvas.drawText(text, padding.toFloat(), -top + padding, paint)
        return bm
    }
}