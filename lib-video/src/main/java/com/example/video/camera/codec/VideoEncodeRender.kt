package com.example.video.camera.codec

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLES32
import android.opengl.GLUtils
import android.util.DisplayMetrics
import com.example.video.R
import com.example.video.camera.surface.EglSurfaceView.Render
import com.example.video.camera.utils.ShaderUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


/**
 * @author : zhang.wenqiang
 * @date : 2020/12/24
 * description :录制视频Render
 * 处理三个纹理id     视频源  图片  文字
 */
class VideoEncodeRender(
    private val context: Context,
    /**
     * 视频源id
     */
    private val textureId: Int,
    /**
     * 滤镜相关暂不用
     */
    private val type: Int = 0,
    private val color: FloatArray = floatArrayOf(0f, 0f, 0f),
    /**
     * 文字图片水印bitmap
     */
    private val textBitmap: Bitmap?
) : Render {
    /**
     * 顶点坐标
     */
    private val vertexData = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f,

        0f, 0f,
        0f, 0f,
        0f, 0f,
        0f, 0f,

        0f, 0f,
        0f, 0f,
        0f, 0f,
        0f, 0f,
        0f, 0f
    )

    /**
     * 纹理坐标  对应顶点坐标
     */
    private val textureData = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private val vertexBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer
    private var program = 0
    private var avPosition = 0
    private var afPosition = 0
    private var vboId = 0

    /**
     * 文字水印渲染id
     */
    private var waterTextureId = 0

    /**
     * 图片水印id
     */
    private var bitmapTextureId = 0

    private var changeType = 0
    private var changeColor = 0

    init {
        adjustWatermarkPosition()

        //读取顶点坐标
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexData)
        vertexBuffer.position(0)

        //读取纹理坐标
        textureBuffer = ByteBuffer.allocateDirect(textureData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureData)
        textureBuffer.position(0)
    }

    /**
     * 计算宽高比 调整矩阵顶点位置
     * 8-15位为icon的位置 例：8,9 位为一个点的横纵坐标 共4个点 需要8位表示 /  16-23 文字位置
     */
    private fun adjustWatermarkPosition() {
        val dm: DisplayMetrics = context.resources.displayMetrics
        //相较于屏幕顶部和左侧的margin
        val margin = 0.95f
        //屏幕宽高比
        val screenScale = String.format("%.1f", 1.0f * dm.widthPixels / dm.heightPixels).toFloat()
        //左下
        vertexData[8] = -1f * margin
        vertexData[9] = screenScale * 1.75f * margin
        //右下
        vertexData[10] = -0.75f * margin
        vertexData[11] = screenScale * 1.75f * margin
        //左上
        vertexData[12] = -1f * margin
        vertexData[13] = margin
        //右上
        vertexData[14] = -0.75f * margin
        vertexData[15] = margin

        vertexData[16] = -1f
        vertexData[17] = -1f

        val x = String.format("%.3f", (textBitmap!!.width.toFloat() / dm.widthPixels) / 2).toFloat()
        val finalx = if (x > 0.2f) {
            x
        } else {
            x - 0.2f
        }

        vertexData[18] = finalx    //右下 x 宽
        vertexData[19] = -1f

        val y = String.format("%.2f", (1 - (textBitmap.height.toFloat() / dm.heightPixels * 10)))
            .toFloat()
        vertexData[20] = -1f
        vertexData[21] = -1f * y

        vertexData[22] = finalx
        vertexData[23] = -1f * y
    }

    override fun onSurfaceCreated() {
        //启用透明
        GLES32.glEnable(GLES32.GL_BLEND)
        GLES32.glBlendFunc(GLES32.GL_SRC_ALPHA, GLES32.GL_ONE_MINUS_SRC_ALPHA)

        //创建源程序 加载顶点着色器
        program = ShaderUtils.createProgram(
            context.resources,
            "vertex_shader_screen.glsl",
            "fragment_shader_screen.glsl"
        )

        if (program > 0) {
            //获取顶点坐标字段
            avPosition = GLES32.glGetAttribLocation(program, "av_Position")
            //获取纹理坐标字段
            afPosition = GLES32.glGetAttribLocation(program, "af_Position")
            //滤镜传入类型
            changeType = GLES32.glGetUniformLocation(program, "vChangeType")
            //对应滤镜需要的颜色
            changeColor = GLES32.glGetUniformLocation(program, "vChangeColor")

            //创建vbo
            createVBO()

            //创建水印纹理
            createWaterTextureId()
        }
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        GLES32.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame() {
        //清空颜色
        GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT)
        //设置背景颜色
        GLES32.glClearColor(1f, 1f, 1f, 1f)
        //使用程序
        GLES32.glUseProgram(program)
        //设置滤镜
        GLES32.glUniform1i(changeType, type)
        GLES32.glUniform3fv(changeColor, 1, color, 0)

        //绑定vbo
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vboId)
        //绘制视频源  流程为->配置、绑定id、释放
        GLES32.glEnableVertexAttribArray(avPosition)
        GLES32.glEnableVertexAttribArray(afPosition)

        GLES32.glVertexAttribPointer(avPosition, 2, GLES32.GL_FLOAT, false, 8, 0)
        GLES32.glVertexAttribPointer(afPosition, 2, GLES32.GL_FLOAT, false, 8, vertexData.size * 4)

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureId)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)

        //绘制图片水印
        GLES32.glVertexAttribPointer(avPosition, 2, GLES32.GL_FLOAT, false, 8, 32)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, bitmapTextureId)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)

        //绘制文字水印
        GLES32.glVertexAttribPointer(avPosition, 2, GLES32.GL_FLOAT, false, 8, 64)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, waterTextureId)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(avPosition)
        GLES20.glDisableVertexAttribArray(afPosition)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
    }

    /**
     * 创建vbo
     */
    private fun createVBO() {
        //1. 创建VBO
        val vbos = IntArray(1)
        GLES32.glGenBuffers(vbos.size, vbos, 0)
        vboId = vbos[0]
        //2. 绑定VBO
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vboId)
        //3. 分配VBO需要的缓存大小
        GLES32.glBufferData(
            GLES32.GL_ARRAY_BUFFER,
            vertexData.size * 4 + textureData.size * 4,
            null,
            GLES32.GL_STATIC_DRAW
        )
        //4. 为VBO设置顶点数据的值
        GLES32.glBufferSubData(GLES32.GL_ARRAY_BUFFER, 0, vertexData.size * 4, vertexBuffer)
        GLES32.glBufferSubData(
            GLES32.GL_ARRAY_BUFFER,
            vertexData.size * 4,
            textureData.size * 4,
            textureBuffer
        )
        //5. 解绑VBO
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
    }

    /**
     * 创建文字和图片的水印纹理id
     */
    private fun createWaterTextureId() {
        val textureIds = IntArray(1)
        //创建纹理
        GLES32.glGenTextures(1, textureIds, 0)
        waterTextureId = textureIds[0]
        //绑定纹理
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, waterTextureId)
        //环绕（超出纹理坐标范围）  （s==x t==y GL_REPEAT 重复）
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_REPEAT)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_REPEAT)
        //过滤（纹理像素映射到坐标点）  （缩小、放大：GL_LINEAR线性）
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)

        val bitmapBuffer = ByteBuffer.allocate(textBitmap!!.height * textBitmap.width * 4)
        textBitmap.copyPixelsToBuffer(bitmapBuffer)
        //将bitmapBuffer位置移动到初始位置
        bitmapBuffer.flip()
        //设置内存大小绑定内存地址
        GLES32.glTexImage2D(
            GLES32.GL_TEXTURE_2D, 0, GLES32.GL_RGBA, textBitmap.width, textBitmap.height,
            0, GLES32.GL_RGBA, GLES32.GL_UNSIGNED_BYTE, bitmapBuffer
        )
        //解绑纹理 指的是离开对 纹理的配置，进入下一个状态
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)

        //生成图片水印id
        bitmapTextureId = createImageTexture(context, R.drawable.ic_water_mark)
    }

    /**
     * 生成图片纹理id
     */
    fun createImageTexture(context: Context, rawID: Int): Int {
        //生产一个纹理
        val textureIds = IntArray(1)
        GLES32.glGenTextures(1, textureIds, 0)
        //绑定为 2D纹理
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureIds[0])
        //设置环绕模式
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_REPEAT)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_REPEAT)
        //设置过滤模式
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
        val bitmap = BitmapFactory.decodeResource(context.resources, rawID)
        //绑定 bitmap到 textureIds[0] 这个2D纹理上
        GLUtils.texImage2D(GLES32.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        //退出 纹理的设置，进入下一环节
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)
        return textureIds[0]
    }
}