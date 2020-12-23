package com.eflagcomm.pmms.camera

import android.annotation.SuppressLint
import android.app.Application
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.*
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import com.eflagcomm.LocationEntity
import com.eflagcomm.LocationProvider
import com.eflagcomm.data.api.image.ConstructionImageVideo
import com.eflagcomm.data.api.image.ImageApiWrapper
import com.eflagcomm.data.api.user.UserEntity
import com.eflagcomm.data.api.user.UserLocalApi
import com.eflagcomm.pmms.base.R
import com.eflagcomm.pmms.camera.Utils
import com.eflagcomm.pmms.camerax.utils.*
import com.eflagcomm.utils.rx.ISubscribeObserver
import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.*

/**
 *
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _location = MutableLiveData<LocationEntity>().apply { value = null }
    private val location: LiveData<LocationEntity> = _location

    private val _compressWaterResult =
        MutableLiveData<ConstructionImageVideo>().apply { value = null }
    val compressWaterResult: LiveData<ConstructionImageVideo> = _compressWaterResult

    private val mImageApiWrapper = ImageApiWrapper(application.applicationContext)

    private var userId: String? = null
    private var userName: String? = null
    private var waterMarkIconFile: File? = null
    private var waterMarkLatLngFile: File? = null

    init {
        getUserId()
    }

    /**
     * 保存水印图片
     */
    fun saveWaterMark(resources: Resources, outDir: File) {
        viewModelScope.launch {
            var waterMarkFile: File? = null
            withContext(Dispatchers.IO) {
                val filename = "ic_water_mark_10"
                waterMarkFile = getFile(outDir, filename, PHOTO_PNG_EXTENSION)
                waterMarkFile?.let {
                    if (!it.exists()) {
                        val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_water_mark)
                        waterMarkFile = bitmap2File(bmp, outDir, filename, PHOTO_PNG_EXTENSION)
                        bmp.recycle()
                    }
                }
            }
            this@CameraViewModel.waterMarkIconFile = waterMarkFile
        }
    }

    /**
     * 获取位置信息
     */
    fun getLocation(resources: Resources, outDir: File, assetManager: AssetManager) {
        LocationProvider.getInstance().start(object : ISubscribeObserver<LocationEntity?>() {
            override fun onNext(locationEntity: LocationEntity) {
                _location.postValue(locationEntity)

                viewModelScope.launch {
                    var waterMarkFile: File? = null
                    withContext(Dispatchers.IO) {
                        waterMarkFile = createFile(outDir, "ic_water_mark_1.png")
                        waterMarkFile?.let {
                            val bmp = drawText2Bitmap(
                                assetManager,
                                resources,
                                userName,
                                "${locationEntity.mLongitude}, ${locationEntity.mLatitude}"
                            )
                            bmp?.let {
                                waterMarkFile = bitmap2File(bmp, outDir, ".png")
                                bmp.recycle()
                            }
                        }
                    }
                    this@CameraViewModel.waterMarkLatLngFile = waterMarkFile
                }
            }
        })
    }

    /**
     * 获取用户ID和用户名字
     */
    @SuppressLint("CheckResult")
    private fun getUserId() {
        val userLocalApi = UserLocalApi(getApplication())
        userLocalApi.user.subscribe({ userEntity: UserEntity? ->
            if (userEntity != null) {
                userId = userEntity.userId
                userName = userEntity.userName
            }
        }, { })
    }

    /**
     * 压缩添加水印图片
     */
    fun compressAndWater(filePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var lngLat: String? = null
                if (location.value == null) {
                    lngLat = null
                } else {
                    location.value?.let { lngLat = it.coordinate }
                }
                val user = if (userName.isNullOrEmpty()) userId else userName
                user?.let {
                    val observable: Observable<String> =
                        mImageApiWrapper.compressAddWatermark(filePath, it, lngLat)
                    observable.subscribe({ it1 ->
                        _compressWaterResult.postValue(ConstructionImageVideo(it1, null))
                    }, {
                        _compressWaterResult.postValue(null)
                    })
                }
            }
        }
    }

    /**
     * 压缩视频
     */
    fun compressVideo(resources: Resources, outDir: File, srcFile: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    val imageFile = getVideoThumbnail(srcFile, outDir)
                    _compressWaterResult.postValue(
                        ConstructionImageVideo(
                            srcFile.path,
                            imageFile?.path
                        )
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Utils.countTime {
                    waterMarkIconFile?.let {

                        Utils.d(
                            "camerax 录制后大小 srcFile:" + Utils.getFileOrFilesSize(srcFile.absolutePath, 3)
                                .toString()
                        )
                        val waterMarkVideoFile = createFile(outDir, VIDEO_EXTENSION)

                        val command = if (waterMarkLatLngFile == null) {
                            """-y -i ${srcFile.absolutePath} -vf "movie=${waterMarkIconFile?.absolutePath}[a1];[in][a1]overlay=80:60[b1]" -b 4096k -c:a copy ${waterMarkVideoFile.absolutePath}"""
                        } else {
                            """-y -i ${srcFile.absolutePath} -vf "movie=${waterMarkIconFile?.absolutePath}[a1];movie=${waterMarkLatLngFile?.absolutePath}[a2];[in][a1]overlay=80:60[b1];[b1][a2]overlay=80:1200" -b 4096k -c:a copy ${waterMarkVideoFile.absolutePath}"""
                        }
//                    val command = "-y -i ${srcFile.absolutePath} -i ${waterMarkIconFile?.absolutePath} -filter_complex [0:v][1:v]overlay=80:60 -codec:a copy ${waterMarkVideoFile.absolutePath}"
                        val rc = FFmpeg.execute(command)

                        Utils.d(
                            " ff 加水印后 waterMarkVideoFile $rc ->" + Utils.getFileOrFilesSize(
                                waterMarkVideoFile.absolutePath,
                                3
                            ).toString()
                        )
                        Timber.tag("FFMPEG").d("rc:%d", rc)
                        if (rc == RETURN_CODE_SUCCESS) {
                            // 从视频中抽取一张图片，作为预览图
                            val resultImageFile = createFile(outDir, PHOTO_EXTENSION)
                            // 使用ffmpeg命令从抽取图片
                            val getImageCommandText =
                                """-y -i ${waterMarkVideoFile.absolutePath} -f image2 -ss 00:00:02 -vframes 1 -preset superfast ${resultImageFile.absolutePath}"""
                            if (FFmpeg.execute(getImageCommandText) == RETURN_CODE_SUCCESS) {
                                _compressWaterResult.postValue(
                                    ConstructionImageVideo(
                                        waterMarkVideoFile.path,
                                        resultImageFile.path
                                    )
                                )
                            } else {
                                _compressWaterResult.postValue(null)
                            }
                        } else {
                            _compressWaterResult.postValue(null)
                        }
                    }
                }
            }

        }
    }


    /**
     * 添加用户、坐标信息到bitmap
     */
    private fun drawText2Bitmap(
        assetManager: AssetManager,
        resources: Resources,
        username: String?,
        lngLat: String?
    ): Bitmap? {
        return try {
            val scale = resources.displayMetrics.density
            val bitmap = Bitmap.createBitmap(650, 210, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = resources.getColor(R.color.col_999999)
            paint.textSize = 9 * scale + 0.5f
            paint.typeface = Typeface.createFromAsset(assetManager, "JetBrainsMono.ttf");
            paint.setShadowLayer(1f, 0f, 1f, Color.DKGRAY) // text shadow
            val content1 =
                resources.getString(R.string.water_camera_person2, username)
            canvas.drawText(content1, 0f, 120f, paint)
            val content2 = resources.getString(
                R.string.water_camera_time,
                ImageApiWrapper.DATE_TIME_FORMAT.format(Date())
            )
            canvas.drawText(content2, 0f, 160f, paint)
            if (!lngLat.isNullOrEmpty() && lngLat.contains(",")) {
                val array = lngLat.split(",").toTypedArray()
                val latLngStr =
                    resources.getString(R.string.water_mark_lat_lng2, array[0], array[1])
                canvas.drawText(latLngStr, 0f, 200f, paint)
            } else {
                val latLngStr = resources.getString(R.string.water_mark_lat_lng1)
                canvas.drawText(latLngStr, 0f, 200f, paint)
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}