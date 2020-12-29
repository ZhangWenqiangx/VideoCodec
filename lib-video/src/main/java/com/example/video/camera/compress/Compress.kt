package com.example.video.camera.compress

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception

/**
 *Created by 张金瑞.
 *Data: 2020-12-29
 */

private const val TAG = "Compress"


class Compress constructor(sourcePath: String,
                           destinationPath: String,
                           quality: Int,
                           listen: VideoCompress.CompressListener) {
    private lateinit var mListener: VideoCompress.CompressListener
    private var mQuality = 0
    init {
        this.mQuality = quality
        CoroutineScope(Dispatchers.Default).launch {
            compress(sourcePath, destinationPath, listen)
        }
    }


    /**
     * sourcePath  原视频文件
     * destinationPath  压缩后的视频文件
     */
    private suspend fun compress(
        sourcePath: String,
        destinationPath: String,
        listen: VideoCompress.CompressListener
    ) {
        try {
            Log.e(TAG, "compress: 111222" )
            withContext(Dispatchers.Main) {
                listen?.let {
                    it.onStart()
                }
            }

            var isSuccess = VideoController.getInstance().convertVideo(
                sourcePath,
                destinationPath,
                mQuality,
                object : VideoController.CompressProgressListener {
                    override fun onProgress(percent: Float) {
                        Log.e(TAG, "onProgress: ${percent}")
                        listen?.let {
                            it.onProgress(percent)
                        }
                    }

                })

            Log.e(TAG, "compress: ${isSuccess}" )

            withContext(Dispatchers.Main) {

                listen?.let {
                    if (isSuccess) {
                        it.onSuccess()
                    } else {
                        it.onFail()
                    }
                }

            }

        } catch (e: Exception) {

        }
    }
}



