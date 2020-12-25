package com.example.video.camera.videoplay

import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.aliyun.player.IPlayer
import com.aliyun.player.source.LiveShift
import com.example.video.R

/**
 *Created by 张金瑞.
 *Data: 2020-12-25
 */
class VideoFragment: Fragment() {

    private lateinit var videoView: SurfaceView

    var url: String? = null

    private lateinit var player: ApsaraLiveShiftPlayer

    private lateinit var playImage: ImageView

    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = arguments?.getString("url")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video,container,false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        videoView = view.findViewById(R.id.VideoView)
        playImage = view.findViewById(R.id.playImage)

        player = ApsaraLiveShiftPlayer(requireContext().applicationContext, "123456")
        player.isLoop = false
        player.setOnCompletionListener {
            isPlaying = false
            playImage.setImageResource(R.drawable.video_play)
            isPlaying = false
            player.pause()
        }

        playImage.setOnClickListener {
            if (isPlaying) {
                playImage.setImageResource(R.drawable.video_play)
                isPlaying = false
                player.pause()
            } else {
                playImage.setImageResource(R.drawable.video_pause)
                isPlaying = true
                player.isLoop = true
                player.start()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        try {
            player.setOnCompletionListener {
//                seekBar.progress = 1000
                // 播放完成事件
                isPlaying = false
                playImage.setImageResource(R.drawable.video_play)
            }
            player.setOnErrorListener(IPlayer.OnErrorListener {
                //出错事件
            })
            player.setOnRenderingStartListener(IPlayer.OnRenderingStartListener {
                //首帧渲染显示videoView事件
//                loadingLayout.visibility = View.GONE
            })
            val urlSource = LiveShift()
            urlSource.url = url
            //设置播放源
            player.setDataSource(urlSource)
            //准备播放
            player.prepare()

            videoView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                    player.redraw()
                }

                override fun surfaceDestroyed(p0: SurfaceHolder) {
                    player.setDisplay(null)
                }

                override fun surfaceCreated(holder: SurfaceHolder) {
                    player.setDisplay(holder)
                }

            })
        } catch (e: Exception){

        }
    }

    override fun onResume() {
        super.onResume()
        playImage.setImageResource(R.drawable.video_pause)
        player.start()
        isPlaying = true
    }

    override fun onPause() {
        super.onPause()
        playImage.setImageResource(R.drawable.video_play)
        isPlaying = false
        player.pause()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}