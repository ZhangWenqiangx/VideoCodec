package com.eflagcomm.pmms.camera

import android.media.MediaMuxer
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.eflagcomm.pmms.base.R
import com.eflagcomm.pmms.camera.codec.MediaEncodeManager
import com.eflagcomm.pmms.camera.codec.MediaMuxerChangeListener
import com.eflagcomm.pmms.camera.codec.VideoEncodeRender
import com.eflagcomm.pmms.camera.record.AudioCapture
import com.eflagcomm.pmms.camera.record.AudioCapture.AudioCaptureListener
import com.eflagcomm.pmms.camera.surface.CameraSurfaceView
import com.eflagcomm.pmms.camera.utils.ByteUtils
import com.eflagcomm.pmms.camera.utils.DisplayUtils
import com.eflagcomm.pmms.camera.utils.FileUtils
import com.eflagcomm.pmms.camerax.utils.getOutputDirectory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 录像功能入口 -- 录音+录视频+编解码+合成视频
 *
 * 需要扩展功能-> 水印
 */
class CameraActivity : AppCompatActivity(), MediaMuxerChangeListener {
    private val TAG = "MainActivity.class"

    private var cameraSurfaceView: CameraSurfaceView? = null
    private var ivRecord: ImageView? = null

    //录音
    private var audioCapture: AudioCapture? = null
    private var mediaEncodeManager: MediaEncodeManager? = null

    //开启 -- 关闭录制
    private var isStartRecord = false
    private val calcDecibel = Handler()
    private lateinit var outputDirectory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
        DisplayUtils.adjustBrightness(0.6f, this)
    }

    private fun init() {
        setContentView(R.layout.activity_camera2)
        ivRecord = findViewById(R.id.iv_record)
        cameraSurfaceView = findViewById(R.id.camera_surface_view)
        audioCapture = AudioCapture()

        outputDirectory = getOutputDirectory(this)

        ivRecord!!.setOnClickListener {
            if (!isStartRecord) {
                initMediaCodec()
                ivRecord!!.setImageResource(R.drawable.camera_icon)
                mediaEncodeManager!!.startEncode()
                audioCapture!!.start()
                isStartRecord = true
            } else {
                ivRecord!!.setImageResource(R.drawable.camera_stop_icon)
                isStartRecord = false
                mediaEncodeManager!!.stopEncode()
                audioCapture!!.stop()
            }
        }
    }

    private fun initMediaCodec() {
        val currentDate =
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA)
                .format(Date())
        val fileName = "/VID_$currentDate.mp4"
        val filePath = FileUtils.getDiskCachePath(this) + fileName
        val mediaFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        val audioType = "audio/mp4a-latm"
        val videoType = "video/avc"
        val sampleRate = 44100
        //单声道 channelCount=1 , 双声道  channelCount=2
        val channelCount = 2
        //AudioCapture.class类中采集音频采用的位宽：AudioFormat.ENCODING_PCM_16BIT ，此处应传入16bit，
        // 用作计算pcm一帧的时间戳
        val audioFormat = 16
        //预览
        val width = cameraSurfaceView!!.cameraPreviewHeight
        val height = (cameraSurfaceView!!.cameraPreviewWidth * 0.6).toInt()
        mediaEncodeManager = MediaEncodeManager(
            VideoEncodeRender(
                this,
                cameraSurfaceView!!.textureId,
                cameraSurfaceView!!.type,
                cameraSurfaceView!!.color
            )
        )
        mediaEncodeManager!!.initMediaCodec(
            filePath, mediaFormat, audioType, sampleRate,
            channelCount, audioFormat, videoType, width, height
        )
        mediaEncodeManager!!.initThread(
            this, cameraSurfaceView!!.eglContext,
            GLSurfaceView.RENDERMODE_CONTINUOUSLY
        )
    }

    //录音线程数据回调
    private fun setPcmRecordListener() {
        if (audioCapture!!.captureListener == null) {
            audioCapture!!.captureListener =
                AudioCaptureListener setCaptureListener@{ audioSource: ByteArray?, audioReadSize: Int ->
                    if (MediaCodecConstant.audioStop || MediaCodecConstant.videoStop) {
                        return@setCaptureListener
                    }
                    mediaEncodeManager!!.setPcmSource(audioSource, audioReadSize)
                    //计算分贝的一种方式
                    calcDecibel.postDelayed({
                        val dBValue =
                            ByteUtils.calcDecibelValue(audioSource, audioReadSize)
                        Log.d(TAG, "calcDecibelLevel: 分贝值 = $dBValue")
                    }, 200)
                }
        }
    }

    override fun onMediaMuxerChangeListener(type: Int) {
        if (type == MediaCodecConstant.MUXER_START) {
            Log.d(TAG, "onMediaMuxerChangeListener --- " + "视频录制开始了")
            setPcmRecordListener()
        }
    }

    override fun onMediaInfoListener(time: Int) {
        Log.d(TAG, "视频录制时长 --- $time")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }
}