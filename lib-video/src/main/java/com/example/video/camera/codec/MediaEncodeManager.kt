package com.example.video.camera.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGLContext
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.Log
import android.view.Surface
import com.example.video.camera.MediaCodecConstant
import com.example.video.camera.surface.EglSurfaceView.Render
import java.io.IOException
import java.nio.ByteBuffer

/**
 * @author : zhang.wenqiang
 * @date : 2020/12/29
 * description :配置/管理 视频/音频编解码
 */
class MediaEncodeManager(builder: Builder) {

    private val TAG = "MediaEncodeManager"

    /**
     * 基本配置
     */
    private var sampleRate = 0

    /**
     * 单声道 channelCount=1 , 双声道  channelCount=2
     */
    private var channelCount = 0

    /**
     * N标识码率低、中、高，类似可设置成1，3，5，码率越高 视频越大，越清晰
     */
    private var codeRate = 1

    /**
     * AudioCapture.class类中采集音频采用的位宽：AudioFormat.ENCODING_PCM_16BIT
     * 用作计算pcm一帧的时间戳
     */
    private var audioFormat = 0

    /**
     * 预览宽高
     */
    private var cameraPreviewWidth = 0
    private var cameraPreviewHeight = 0

    /**
     * 时间戳
     */
    private var presentationTimeUs: Long = 0

    private var surface: Surface? = null
    private var eglSurfaceRender: Render? = null

    /**
     * 混流
     */
    private var mediaMuxer: MediaMuxer? = null

    /**
     * 音视频编解码
     */
    private var audioCodec: MediaCodec? = null
    private var videoCodec: MediaCodec? = null

    /**
     * 缓冲
     */
    private var audioBuffer: MediaCodec.BufferInfo? = null
    private var videoBuffer: MediaCodec.BufferInfo? = null

    /**
     * 线程
     */
    private var audioThread: AudioCodecThread? = null
    private var videoThread: VideoCodecThread? = null
    private var eglThread: EglRenderThread? = null

    init {
        this.sampleRate = builder.sampleRate
        this.channelCount = builder.channelCount
        this.audioFormat = builder.audioFormat
        this.cameraPreviewWidth = builder.width
        this.cameraPreviewHeight = builder.height
        this.eglSurfaceRender = builder.eglSurfaceRender

        initMediaMuxer(builder.videoPath, builder.mediaFormat)
        initVideoCodec(builder.videoType, cameraPreviewWidth, cameraPreviewHeight)
        initAudioCodec(builder.audioType, sampleRate, channelCount)
        initThread(builder.listener, builder.eglContext, builder.renderMode)
    }

    private fun initThread(
        listener: MediaMuxerChangeListener?,
        eglContext: EGLContext?,
        renderMode: Int
    ) {
        eglThread =
            EglRenderThread(surface, eglContext, eglSurfaceRender, renderMode, cameraPreviewWidth, cameraPreviewHeight)
        videoThread = VideoCodecThread(videoCodec, videoBuffer, mediaMuxer, listener!!)
        audioThread = AudioCodecThread(audioCodec, audioBuffer, mediaMuxer, listener)
    }

    private fun initMediaMuxer(filePath: String, mediaFormat: Int) {
        try {
            mediaMuxer = MediaMuxer(filePath, mediaFormat)
        } catch (e: IOException) {
            Log.e(
                TAG,
                "initMediaMuxer: 文件打开失败,path=$filePath"
            )
        }
    }

    private fun initAudioCodec(
        audioType: String,
        sampleRate: Int,
        channels: Int
    ) {
        try {
            audioCodec = MediaCodec.createEncoderByType(audioType)
            val audioFormat =
                MediaFormat.createAudioFormat(audioType, sampleRate, channels)
            val BIT_RATE = 96000
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            audioFormat.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            val MAX_INOUT_SIZE = 8192
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INOUT_SIZE)
            audioCodec!!.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioBuffer = MediaCodec.BufferInfo()
        } catch (e: IOException) {
            Log.e(TAG, "initAudioCodec: 音频类型无效")
        }
    }

    private fun initVideoCodec(videoType: String, width: Int, height: Int) {
        try {
            videoCodec = MediaCodec.createEncoderByType(videoType)
            val videoFormat =
                MediaFormat.createVideoFormat(videoType, width, height)
            videoFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            //MediaFormat.KEY_FRAME_RATE -- 可通过Camera#Parameters#getSupportedPreviewFpsRange获取
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            //width*height*N
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * codeRate)
            //每秒关键帧数
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                videoFormat.setInteger(
                    MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                )
                videoFormat.setInteger(
                    MediaFormat.KEY_LEVEL,
                    MediaCodecInfo.CodecProfileLevel.AVCLevel13
                )
            }
            videoCodec!!.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoBuffer = MediaCodec.BufferInfo()
            surface = videoCodec!!.createInputSurface()
        } catch (e: IOException) {
            Log.e(TAG, "initVideoCodec: 视频类型无效")
        }
    }

    /**
     * 设置音频原始PCM数据
     */
    fun setPcmSource(pcmBuffer: ByteArray?, buffSize: Int) {
        if (audioCodec == null) {
            return
        }
        try {
            val buffIndex = audioCodec!!.dequeueInputBuffer(0)
            if (buffIndex < 0) {
                return
            }
            val byteBuffer: ByteBuffer?
            byteBuffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioCodec!!.getInputBuffer(buffIndex)
            } else {
                audioCodec!!.inputBuffers[buffIndex]
            }
            if (byteBuffer == null) {
                return
            }
            byteBuffer.clear()
            byteBuffer.put(pcmBuffer)
            //presentationTimeUs = 1000000L * (buffSize / 2) / sampleRate
            //一帧音频帧大小 int size = 采样率 x 位宽 x 采样时间 x 通道数
            // 1s时间戳计算公式  presentationTimeUs = 1000000L * (totalBytes / sampleRate/ audioFormat / channelCount / 8 )
            //totalBytes : 传入编码器的总大小
            //1000 000L : 单位为 微秒，换算后 = 1s,
            //除以8     : pcm原始单位是bit, 1 byte = 8 bit, 1 short = 16 bit, 用 Byte[]、Short[] 承载则需要进行换算
            presentationTimeUs += (1.0 * buffSize / (sampleRate * channelCount * (audioFormat / 8)) * 1000000.0).toLong()
            Log.d(
                TAG,
                "pcm一帧时间戳 = " + presentationTimeUs / 1000000.0f
            )
            audioCodec!!.queueInputBuffer(buffIndex, 0, buffSize, presentationTimeUs, 0)
        } catch (e: IllegalStateException) {
            //audioCodec 线程对象已释放MediaCodec对象
            Log.d(
                TAG,
                "setPcmSource: " + "MediaCodec对象已释放"
            )
        }
    }

    fun startEncode() {
        if (surface == null) {
            Log.e(
                TAG,
                "startEncode: createInputSurface创建失败"
            )
            return
        }
        MediaCodecConstant.encodeStart = false
        eglThread!!.start()
        videoThread!!.start()
        audioThread!!.start()
        MediaCodecConstant.surfaceCreate = true
        MediaCodecConstant.surfaceChange = true
        MediaCodecConstant.audioStop = false
        MediaCodecConstant.videoStop = false
    }

    fun stopEncode() {
        MediaCodecConstant.encodeStart = false
        audioThread!!.stopAudioCodec()
        audioThread = null
        videoThread!!.stopVideoCodec()
        videoThread = null
        eglThread!!.stopEglRender()
        eglThread = null
        MediaCodecConstant.surfaceCreate = false
        MediaCodecConstant.surfaceChange = false
    }

    class Builder {
        var width = 0
        var height = 0
        var videoPath = ""

        var sampleRate = 44100
        var channelCount = 2
        var audioFormat = 16
        var mediaFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        var renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        var audioType = "audio/mp4a-latm"
        var videoType = "video/avc"
        var eglSurfaceRender: Render? = null
        var eglContext: EGLContext? = null
        var listener: MediaMuxerChangeListener? = null

        fun setVideoEncodeRender(eglSurfaceRender: Render): Builder {
            this.eglSurfaceRender = eglSurfaceRender
            return this
        }

        fun setMdeiaMuxerChangeListener(listener: MediaMuxerChangeListener): Builder {
            this.listener = listener
            return this
        }

        fun setVideoSavePath(path: String): Builder {
            this.videoPath = path
            return this
        }

        fun setEGLContext(eglContext: EGLContext): Builder {
            this.eglContext = eglContext
            return this
        }

        fun audioType(audioType: String): Builder {
            this.audioType = audioType
            return this
        }

        fun sampleRate(sampleRate: Int): Builder {
            this.sampleRate = sampleRate
            return this
        }

        fun channelCount(channelCount: Int): Builder {
            this.channelCount = channelCount
            return this
        }

        fun audioFormat(audioFormat: Int): Builder {
            this.audioFormat = audioFormat
            return this
        }

        fun videoType(videoType: String): Builder {
            this.videoType = videoType
            return this
        }

        fun cameraPreviewWidth(width: Int): Builder {
            this.width = width
            return this
        }

        fun cameraPreviewHeight(height: Int): Builder {
            this.height = height
            return this
        }

        fun setRenderMode(mode: Int): Builder {
            this.renderMode = mode
            return this
        }

        fun build(): MediaEncodeManager {
            return MediaEncodeManager(this)
        }
    }
}