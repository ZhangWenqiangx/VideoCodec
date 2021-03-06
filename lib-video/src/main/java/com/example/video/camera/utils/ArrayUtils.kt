package com.example.video.camera.utils

/**
 *  @author : zhang.wenqiang
 *  @date : 2020/12/24
 *  description :
 */
/**
 * 寻找字符串集合中最长的字符串
 */
fun Array<String>.findMaxLengthStr(): List<String>? {
    var maxLength = Int.MIN_VALUE
    val maxList: MutableList<String> = ArrayList()
    for (s in this) {
        if (s.length > maxLength) {
            maxLength = s.length
            maxList.clear()
            maxList.add(s)
        } else if (s.length == maxLength) {
            maxList.add(s)
        }
    }
    return maxList
}
