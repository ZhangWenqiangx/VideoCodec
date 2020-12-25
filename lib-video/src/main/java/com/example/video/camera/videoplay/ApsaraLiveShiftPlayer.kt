package com.example.video.camera.videoplay

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import com.aliyun.liveshift.LiveTimeUpdater
import com.aliyun.player.AVPBase
import com.aliyun.player.AliLiveShiftPlayer
import com.aliyun.player.IPlayer
import com.aliyun.player.IPlayer.OnLoadingStatusListener
import com.aliyun.player.nativeclass.JniSaasPlayer
import com.aliyun.player.nativeclass.NativePlayerBase
import com.aliyun.player.source.LiveShift
import com.aliyun.player.source.UrlSource
import java.lang.ref.WeakReference

class ApsaraLiveShiftPlayer : AVPBase, AliLiveShiftPlayer {
    private var status = 0
    private var statusWhenSeek = 0
    private var liveSeekToTime: Long = -1
    private var liveSeekOffset: Long = -1
    private var liveShiftSource: LiveShift? = null
    private var liveTimeUpdater: LiveTimeUpdater? = null
    private var timeShiftUpdaterListener: InnerTimeShiftUpdaterListener? = null

    constructor(context: Context?) : super(context, null as String?) {
        timeShiftUpdaterListener = InnerTimeShiftUpdaterListener(this)
    }

    constructor(context: Context?, traceID: String?) : super(
        context,
        traceID
    ) {
        timeShiftUpdaterListener = InnerTimeShiftUpdaterListener(this)
    }

    override fun createAlivcMediaPlayer(
        context: Context,
        traceID: String
    ): NativePlayerBase {
        return JniSaasPlayer(context)
    }

    override fun setDataSource(liveShift: LiveShift) {
        liveShiftSource = liveShift
        val urlSource = UrlSource()
        urlSource.uri = liveShift.url
        val corePlayer = corePlayer
        if (corePlayer is JniSaasPlayer) {
            corePlayer.setDataSource(urlSource)
        }
    }

    override fun seekToLiveTime(liveTime: Long) {
        //已经在seek中了，就不去seek了。
        //防止下面的liveSeekPlayerState被改变，导致连续seek不能播放的问题。
//        if (status == SeekLive) {
//            return
//        }
        if (liveShiftSource == null) {
            return
        }
        statusWhenSeek = status
        status = SeekLive
        liveSeekToTime = liveTime
        liveSeekOffset = currentLiveTime - liveSeekToTime
        if (liveSeekOffset < 0) {
            liveSeekOffset = 0
            liveSeekToTime = currentLiveTime
        }
        var finalPlayUrl = liveShiftSource!!.url
        if (liveSeekToTime > 0 && liveSeekOffset > 0) {
            val queryStr = Uri.parse(finalPlayUrl).query
            finalPlayUrl = if (finalPlayUrl.endsWith("?") || finalPlayUrl.endsWith("&")) {
                finalPlayUrl + "lhs_offset_unix_s_0=" + liveSeekOffset + "&lhs_start=1&aliyunols=on"
            } else {
                if (TextUtils.isEmpty(queryStr)) {
                    "$finalPlayUrl?lhs_offset_unix_s_0=$liveSeekOffset&lhs_start=1&aliyunols=on"
                } else {
                    "$finalPlayUrl&lhs_offset_unix_s_0=$liveSeekOffset&lhs_start=1&aliyunols=on"
                }
            }
        }
        val urlSource = UrlSource()
        urlSource.uri = finalPlayUrl
        val corePlayer = corePlayer
        if (corePlayer is JniSaasPlayer) {
            stopInner()
            corePlayer.setDataSource(urlSource)
            corePlayer.prepare()
        }
    }

    override fun getCurrentTime(): Long {
        return if (liveTimeUpdater != null) {
            liveTimeUpdater!!.playTime
        } else 0
    }

    override fun setOnTimeShiftUpdaterListener(l: AliLiveShiftPlayer.OnTimeShiftUpdaterListener?) {
        mOutTimeShiftUpdaterListener = l
    }

    override fun setOnSeekLiveCompletionListener(l: AliLiveShiftPlayer.OnSeekLiveCompletionListener?) {
        mOutSeekLiveCompletionListener = l
    }

    private var mOutSeekLiveCompletionListener: AliLiveShiftPlayer.OnSeekLiveCompletionListener? =
        null
    private var mOnPreparedListener: IPlayer.OnPreparedListener? = null

    private class InnerPreparedListener internal constructor(player: ApsaraLiveShiftPlayer) :
        IPlayer.OnPreparedListener {
        private val playerWR: WeakReference<ApsaraLiveShiftPlayer> = WeakReference(player)
        override fun onPrepared() {
            val player = playerWR.get()
            player?.onPrepared()
        }

    }

    private fun onPrepared() {
        if (liveTimeUpdater != null) {
            liveTimeUpdater!!.stopUpdater()
        } else {
            liveTimeUpdater = LiveTimeUpdater(mContext, liveShiftSource)
            liveTimeUpdater!!.setUpdaterListener(timeShiftUpdaterListener)
        }
        liveTimeUpdater!!.setStartPlayTime(liveSeekToTime)
        liveTimeUpdater!!.startUpdater()
        if (status == SeekLive) {
            status = IPlayer.prepared
            if (statusWhenSeek == IPlayer.started) {
                start()
            } else {
                liveTimeUpdater!!.pauseUpdater() //暂停进度的更新
            }
            mOutSeekLiveCompletionListener?.onSeekLiveCompletion(liveSeekToTime)
            liveSeekToTime = -1
        } else {
            status = IPlayer.prepared
            if (mOnPreparedListener != null) {
                mOnPreparedListener!!.onPrepared()
            }
        }
    }

    private var mOnStateChangedListener: IPlayer.OnStateChangedListener? = null
    private val innerOnStateChangedListener: IPlayer.OnStateChangedListener =
        InnerStateChangedListener(this)

    private class InnerStateChangedListener internal constructor(apsaraLiveShiftPlayer: ApsaraLiveShiftPlayer) :
        IPlayer.OnStateChangedListener {
        private val playerWR: WeakReference<ApsaraLiveShiftPlayer>
        override fun onStateChanged(newState: Int) {
            val player = playerWR.get()
            player?.onStateChanged(newState)
        }

        init {
            playerWR = WeakReference(apsaraLiveShiftPlayer)
        }
    }

    private fun onStateChanged(newState: Int) {
        if (newState != IPlayer.prepared) {
            status = newState
        }
        if (mOnStateChangedListener != null) {
            mOnStateChangedListener!!.onStateChanged(newState)
        }
    }

    override fun setOnStateChangedListener(l: IPlayer.OnStateChangedListener) {
        mOnStateChangedListener = l
        super.setOnStateChangedListener(innerOnStateChangedListener)
    }

    private var mOnLoadingStatusListener: OnLoadingStatusListener? = null
    private val innerOnLoadingStatusListener: OnLoadingStatusListener =
        InnerOnLoadingStatusListener(this)

    private class InnerOnLoadingStatusListener internal constructor(apsaraLiveShiftPlayer: ApsaraLiveShiftPlayer) :
        OnLoadingStatusListener {

        private val playerWR: WeakReference<ApsaraLiveShiftPlayer> =
            WeakReference(apsaraLiveShiftPlayer)

        override fun onLoadingBegin() {
            val player = playerWR.get()
            player?.onLoadingBegin()
        }

        override fun onLoadingProgress(percent: Int, netSpeed: Float) {
            val player = playerWR.get()
            player?.onLoadingProgress(percent, netSpeed)
        }

        override fun onLoadingEnd() {
            val player = playerWR.get()
            player?.onLoadingEnd()
        }

    }

    private fun onLoadingBegin() {
        liveTimeUpdater?.pauseUpdater()
        if (mOnLoadingStatusListener != null) {
            mOnLoadingStatusListener!!.onLoadingBegin()
        }
    }

    private fun onLoadingProgress(percent: Int, netSpeed: Float) {
        mOnLoadingStatusListener?.onLoadingProgress(percent, netSpeed)
    }

    private fun onLoadingEnd() {
        liveTimeUpdater?.resumeUpdater()
        mOnLoadingStatusListener?.onLoadingEnd()
    }

    override fun setOnLoadingStatusListener(l: OnLoadingStatusListener) {
        mOnLoadingStatusListener = l
        super.setOnLoadingStatusListener(innerOnLoadingStatusListener)
    }

    override fun setOnPreparedListener(listener: IPlayer.OnPreparedListener) {
        mOnPreparedListener = listener
        super.setOnPreparedListener(InnerPreparedListener(this))
    }

    override fun start() {
        super.start()
        liveTimeUpdater?.resumeUpdater()
    }

    override fun pause() {
        super.pause()
        liveTimeUpdater?.pauseUpdater()
    }

    override fun getCurrentLiveTime(): Long {
        return if (liveTimeUpdater != null) {
            liveTimeUpdater!!.liveTime
        } else 0
    }

    override fun stop() {
        super.stop()
        liveTimeUpdater?.stopUpdater()
    }

    private class InnerTimeShiftUpdaterListener(shiftPlayer: ApsaraLiveShiftPlayer) :
        AliLiveShiftPlayer.OnTimeShiftUpdaterListener {
        private val playerReference: WeakReference<ApsaraLiveShiftPlayer> =
            WeakReference(shiftPlayer)

        override fun onUpdater(
            currentTime: Long,
            shiftStartTime: Long,
            shiftEndTime: Long
        ) {
            val playerProxy = playerReference.get()
            playerProxy?.onUpdater(currentTime, shiftStartTime, shiftEndTime)
        }

    }

    private var mOutTimeShiftUpdaterListener: AliLiveShiftPlayer.OnTimeShiftUpdaterListener? = null
    private fun onUpdater(
        currentTime: Long,
        shiftStartTime: Long,
        shiftEndTime: Long
    ) {
        mOutTimeShiftUpdaterListener?.onUpdater(currentTime, shiftStartTime, shiftEndTime)
    }

    companion object {
        const val SeekLive = 10
    }
}
