package com.example.video.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.video.R
import com.example.video.camera.MediaCodecConstant.MUXER_START
import com.example.video.camera.MediaCodecConstant.MUXER_STOP
import com.example.video.camera.codec.MediaEncodeManager
import com.example.video.camera.codec.MediaMuxerChangeListener
import com.example.video.camera.codec.VideoEncodeRender
import com.example.video.camera.record.AudioCapture
import com.example.video.camera.surface.CameraSurfaceView
import com.example.video.camera.utils.BitmapUtils
import com.example.video.camera.utils.ByteUtils
import com.example.video.camera.utils.DisplayUtils
import com.example.video.camera.utils.FileUtils
import com.example.video.camera.videoplay.VideoPlayActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


private const val PERMISSIONS_REQUEST_CODE = 10

private val PERMISSIONS_REQUIRED = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.RECORD_AUDIO
)

/**
 * 录像功能入口 -- 录音+录视频+编解码+合成视频
 */
class CameraActivity : AppCompatActivity(), MediaMuxerChangeListener {
    private val TAG = CameraActivity::class.java.simpleName

    private var cameraSurfaceView: CameraSurfaceView? = null
    private var ivRecord: ImageView? = null

    //录音
    private var audioCapture: AudioCapture? = null
    private var mediaEncodeManager: MediaEncodeManager? = null

    //开启 -- 关闭录制
    private var isStartRecord = false
    private val calcDecibel = Handler()

    private lateinit var mFilePath: String
    private var fileList = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasPermissions(this)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        } else {
            init()
        }
    }

    /**
     * 请求权限回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (PackageManager.PERMISSION_GRANTED == grantResults.firstOrNull()) {
                init()
            } else {
                Toast.makeText(this, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun init() {
        DisplayUtils.adjustBrightness(0.6f, this)
        setContentView(R.layout.activity_camera)
        ivRecord = findViewById(R.id.capture)
        cameraSurfaceView = findViewById(R.id.camera_surface_view)
        audioCapture = AudioCapture()

        ivRecord!!.setOnClickListener {
            if (!isStartRecord) {
                Toast.makeText(this, "start", Toast.LENGTH_SHORT).show()
                mFilePath = initMediaCodec()
                mediaEncodeManager!!.startEncode()
                audioCapture!!.start()
                isStartRecord = true
            } else {
                Toast.makeText(this, "end", Toast.LENGTH_SHORT).show()
                isStartRecord = false
                mediaEncodeManager!!.stopEncode()
                audioCapture!!.stop()
            }
        }
    }

    private fun initMediaCodec(): String {
        val filePath = FileUtils.getDiskCachePath(this) + "/VID_${SimpleDateFormat(
            "yyyyMMdd_HHmm",
            Locale.CHINA
        )
            .format(Date())}.mp4"

        val videoEncodeRender = VideoEncodeRender(
            context = this,
            textureId = cameraSurfaceView!!.textureId,
            waterMarkArr = arrayOf(
                BitmapFactory.decodeResource(resources, R.drawable.ic_water_mark),
                BitmapUtils.drawText2Bitmap(
                    assets, resources, arrayOf(
                        "拍 照 人：系统管理员",
                        "拍照时间：2020-12-24 11：12：15",
                        "经 纬 度：东经 116.489732 北纬 40.01824"
                    )
                )
            )
        )

        mediaEncodeManager = MediaEncodeManager.Builder()
            .setVideoSavePath(filePath)
            .cameraPreviewHeight(cameraSurfaceView!!.cameraPreviewWidth)
            .cameraPreviewWidth(cameraSurfaceView!!.cameraPreviewHeight)
            .setVideoEncodeRender(videoEncodeRender)
            .setEGLContext(cameraSurfaceView!!.eglContext)
            .setMdeiaMuxerChangeListener(this)
            .build()

        return filePath
    }

    //录音线程数据回调
    private fun setPcmRecordListener() {
        if (audioCapture!!.captureListener == null) {
            audioCapture!!.captureListener =
                AudioCapture.AudioCaptureListener setCaptureListener@{ audioSource: ByteArray?, audioReadSize: Int ->
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
        when (type) {
            MUXER_START -> {
                Log.d(TAG, "onMediaMuxerChangeListener --- " + "视频录制开始")
                setPcmRecordListener()
            }
            MUXER_STOP -> {
                Log.d(TAG, "onMediaMuxerChangeListener --- " + "视频录制结束")

                intent = Intent(this, VideoPlayActivity::class.java)
                Log.d(TAG, "init: ${mFilePath}")
                fileList.add(mFilePath)
                intent.putExtra("111", fileList)
                startActivity(intent)
            }
            else -> {

            }
        }
    }

    override fun onMediaInfoListener(time: Int) {
        Log.d(TAG, "视频录制时长 --- $time")

    }

    fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}