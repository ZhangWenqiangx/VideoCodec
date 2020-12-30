package com.example.video.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
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

const val REQUEST_CODE_CAMERA = 103
private const val PERMISSIONS_REQUEST_CODE = 10

private val PERMISSIONS_REQUIRED = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.RECORD_AUDIO
)

/**
 * 录像功能入口 -- 录音+录视频+编解码+合成视频
 * 压缩方式：
 * 1、视频录制完成后通过VideoCompress进行压缩（耗时比较长）
 * 2、通过集成RxFFmpeg getBoxBlur() 方法 进行ffmpeg 命令语句压缩 ，耗时短，安装包会增大
 * 3、在创建surface 视频录制的时候，对mediacodec 进行音视频编码的调节，水印+压缩同步进行，不做其他任何耗时操作
 */
class CameraActivity : AppCompatActivity(), MediaMuxerChangeListener {
    private val TAG = CameraActivity::class.java.simpleName

    /**
     * 视频路径
     */
    val EXTRA_DATA = "VIDEO_PATH"
    private lateinit var mFilePath: String
    private var fileList = ArrayList<String>()

    private var timeText: TextView? = null
    private var ivRecord: ImageView? = null
    private var ivThumbnail: ImageView? = null
    private var cameraSurfaceView: CameraSurfaceView? = null

    private var timerCount: Long = 10L

    //录音
    private var audioCapture: AudioCapture? = null
    private var mediaEncodeManager: MediaEncodeManager? = null

    //开启 -- 关闭录制
    private var isStartRecord = false
    private val calcDecibel = Handler()

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
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        requestWindowFeature(Window.FEATURE_NO_TITLE)
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
                timer.start()
                isStartRecord = true
            } else {
                stopVideoRecord()
            }
        }

        findViewById<ImageView>(R.id.camera_ok).setOnClickListener {
            if (fileList.isNotEmpty()) {
                val data = Intent()
                data.putStringArrayListExtra(EXTRA_DATA, fileList)
                setResult(Activity.RESULT_OK, data)
            }
            finish()
        }

        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            finish()
        }

        ivThumbnail = findViewById<ImageView>(R.id.image)
        ivThumbnail?.setOnClickListener {
            if (fileList.isEmpty()) {
                return@setOnClickListener
            }
            intent = Intent(this, VideoPlayActivity::class.java)
            intent.putExtra(VideoPlayActivity.EXTRA_DATA, fileList)
            startActivity(intent)
        }

        timeText = findViewById(R.id.timeText)
    }

    fun stopVideoRecord() {
        if (timerCount > 5) {
            Toast.makeText(this, "录制时间太短", Toast.LENGTH_SHORT).show()
            return
        }
        mediaEncodeManager!!.stopEncode()
        audioCapture!!.stop()
        timer.cancel()
        timeText?.text = null
        Toast.makeText(this, "end", Toast.LENGTH_SHORT).show()
        isStartRecord = false
    }

    /**
     * 视频录制定时器
     */
    private val timer = object : CountDownTimer(10000, 1000) {
        override fun onFinish() {
            stopVideoRecord()
        }

        @SuppressLint("SetTextI18n")
        override fun onTick(millisUntilFinished: Long) {
            val count: Long = millisUntilFinished / 1000
            timerCount = count
            val str = if (count < 10) "0$count" else count.toString()
            timeText?.text = "00:${str}"
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

    /**
     * 混流开始结束回调
     */
    override fun onMediaMuxerChangeListener(type: Int) {
        when (type) {
            MUXER_START -> {
                ivRecord?.setImageResource(R.drawable.camera_stop_icon)
                setPcmRecordListener()
            }
            MUXER_STOP -> {
                ivRecord?.setImageResource(R.drawable.camera_icon)
                fileList.add(mFilePath)
                ivThumbnail?.post {
                    Glide.with(this)
                        .load(R.drawable.ic_water_mark)
                        .override(100, 100)
                        .into(ivThumbnail!!)
                }
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