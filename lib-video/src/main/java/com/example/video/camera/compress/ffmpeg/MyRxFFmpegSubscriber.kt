package com.example.video.camera.compress.ffmpeg

import android.util.Log
import com.example.video.camera.CameraActivity
import io.microshow.rxffmpeg.RxFFmpegSubscriber
import java.lang.ref.WeakReference

/**
 *Created by 张金瑞.
 *Data: 2020-12-29
 */

private const val TAG = "MyRxFFmpegSubscriber"
class MyRxFFmpegSubscriber constructor( var T: CameraActivity): RxFFmpegSubscriber() {


    init {
        var mWeakReference= WeakReference(T)
    }

    override fun onFinish() {

    }

    override fun onCancel() {

    }

    override fun onProgress(progress: Int, progressTime: Long) {
        Log.e(TAG, "onProgress: ${progress}   progressTime:${progressTime}" )
    }

    override fun onError(message: String?) {
        Log.e(TAG, "onError: ${message}" )
    }
}