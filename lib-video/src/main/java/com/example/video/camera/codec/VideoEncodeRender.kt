package com.example.video.camera.codec

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLES32
import android.util.ArrayMap
import com.example.video.camera.surface.EglSurfaceView.Render
import com.example.video.camera.utils.GlUtils
import com.example.video.camera.utils.ShaderUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * @author : zhang.wenqiang
 * @date : 2020/12/24
 * description :录制视频渲染类
 * 处理两个纹理id     视频源  水印
 */
class VideoEncodeRender(
    private val context: Context,
    /**
     * 视频源texture id
     */
    private val textureId: Int,
    /**
     * 水印bitmap集合
     */
    private val waterMarkArr: Array<Bitmap?>
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

    private var program = 0
    private var avPosition = 0
    private var afPosition = 0
    private var vboId = 0
    private val vertexBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer

    /**
     * 存储位图与生成其对应id的map
     */
    private var waterMarkMap: ArrayMap<Bitmap, Int> = ArrayMap()

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
        waterMarkArr[0]?.let {
            val r: Float = 1.0f * it.width / it.height
            val w = r * 0.1f
            //左下
            vertexData[8] = -0.9f
            vertexData[9] = 0.75f
            //右下
            vertexData[10] = w - 0.7f
            vertexData[11] = 0.75f
            //左上
            vertexData[12] = -0.9f
            vertexData[13] = 0.9f
            //右上
            vertexData[14] = w - 0.7f
            vertexData[15] = 0.9f
        }

        waterMarkArr[1]?.let {
            val r: Float = 1.0f * it.width / it.height
            val w = r * 0.1f
            vertexData[16] = -0.9f
            vertexData[17] = -0.95f

            vertexData[18] = w - 0.3f
            vertexData[19] = -0.95f

            vertexData[20] = -0.9f
            vertexData[21] = -0.8f

            vertexData[22] = w - 0.3f
            vertexData[23] = -0.8f
        }
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
        ).also {
            //获取顶点坐标字段
            avPosition = GLES32.glGetAttribLocation(it, "av_Position")
            //获取纹理坐标字段
            afPosition = GLES32.glGetAttribLocation(it, "af_Position")
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
        //绑定vbo
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vboId)
        //绘制视频源  流程为->配置、绑定id、释放
        GLES32.glEnableVertexAttribArray(avPosition)
        GLES32.glEnableVertexAttribArray(afPosition)

        GLES32.glVertexAttribPointer(avPosition, 2, GLES32.GL_FLOAT, false, 8, 0)
        GLES32.glVertexAttribPointer(afPosition, 2, GLES32.GL_FLOAT, false, 8, vertexData.size * 4)

        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, textureId)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)

        var offset = 32
        //绘制水印
        waterMarkArr.forEach {
            it?.let {
                GLES32.glVertexAttribPointer(avPosition, 2, GLES32.GL_FLOAT, false, 8, offset)
                GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, waterMarkMap[it]!!)
                GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)
                offset += 32
            }
        }

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
     * 创建水印的纹理id
     */
    private fun createWaterTextureId() {
        waterMarkArr.forEach {
            waterMarkMap[it] = GlUtils.createTextureId(it)
        }
    }
}