package com.example.video.camera.videoplay

import android.os.Bundle
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.video.R
import java.util.ArrayList

/**
 *Created by 张金瑞.
 *Data: 2020-12-24
 */
class VideoPlayActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var viewPager2: ViewPager2
    private lateinit var pagerAdapter: ImagePageAdapter
    private var postion =1

    companion object{
        val EXTRA_DATA = "VIDEO_PATH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //设置全屏展示
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_videoplay)
        titleText = findViewById(R.id.title_text)
        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }
        viewPager2 = findViewById(R.id.view_pager)

        val data: ArrayList<String>? = intent.getStringArrayListExtra(EXTRA_DATA) //此处需一个公共的参数

        data?.let {
            //创建一个装有Fragment的容器
            val fragmentList = ArrayList<Fragment>()
            //遍历 数据源data  item 其中的某一条数据
            it.forEach { item ->
                //获取当前数据在集合中的下标位置
                postion = data.indexOf(item)
                //如果 item中包含.mp4 则说明此数据是视频数据  .png/.jpeg 则代表是图片
                if (item.contains(".mp4")) {
                    if (!item.isNullOrEmpty()) {
                        val videoFragment = VideoFragment()
                        val args = Bundle()
                        args.putString("url", item)
                        videoFragment.arguments = args
                        fragmentList.add(videoFragment)
                    }

                } else if (item.contains(".png") || item.contains(".jpeg")) {
                    if (!item.isNullOrEmpty()) {
                        val imageFragment = ImageFragment()
                        val args = Bundle()
                        args.putString("url", item)
                        imageFragment.arguments = args
                        fragmentList.add(imageFragment)
                    }

                }
            }

            pagerAdapter = ImagePageAdapter(supportFragmentManager,this.lifecycle,fragmentList)
            viewPager2.adapter = pagerAdapter
            viewPager2.offscreenPageLimit = fragmentList.size
            titleText.text = "${postion+1}/${pagerAdapter.itemCount}"
            viewPager2.registerOnPageChangeCallback(object :ViewPager2.OnPageChangeCallback(){
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    postion = position
                    titleText.text = "${position+1}/${pagerAdapter.itemCount}"
                }
            })
            if (postion - 1 > 0) {
                postion -= 1
                viewPager2.currentItem = postion
                titleText.text = "${postion + 1}/${pagerAdapter.itemCount}"
            } else {
                if (pagerAdapter.dataList().isNotEmpty()) {
                    postion = pagerAdapter.dataList().size - 1
                    viewPager2.currentItem = postion
                    titleText.text = "${postion + 1}/${pagerAdapter.itemCount}"
                } else {
//                    backAction()
                    finish()
                }
            }
            viewPager2.currentItem = postion
        }
    }

    inner class ImagePageAdapter constructor(
        fm: FragmentManager,
        lifecycle: Lifecycle,
        val list: MutableList<Fragment>
    ): FragmentStateAdapter(fm,lifecycle) {

        fun dataList(): MutableList<Fragment> {
            return list
        }

        override fun getItemCount(): Int {
            return list.size
        }

        override fun createFragment(position: Int): Fragment {
            return list[position]
        }

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
        }

        return super.onKeyDown(keyCode, event)
    }
}