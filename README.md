# VideoCodec
AudioRecord录制原始PCM格式音频数据  
MediaCodec编码输出音频为AAC格式  
MediaCdec.createInputSurface()创建Surface,EGLContext绑定Surface并通过渲染FBO已绑定的纹理录制视频  
FBO离屏纹理绘制水印纹理并添加至录制视频文件  
MediaMuxer合成音频、视频数据并输出MP4视频文件  

