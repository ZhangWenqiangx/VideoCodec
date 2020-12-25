package com.example.video.camera.videoplay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.video.R
import com.github.chrisbanes.photoview.PhotoView

/**
 *Created by 张金瑞.
 *Data: 2020-12-25
 */
class ImageFragment: Fragment() {

    var url: String?=null
    private lateinit var imageView:PhotoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = arguments?.getString("url")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_image,container,false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        imageView = view?.findViewById(R.id.image)
        showPic()
    }

    private fun showPic() {
        url?.let {
            Glide.with(requireActivity()).load(url).into(imageView)
        }
    }
}