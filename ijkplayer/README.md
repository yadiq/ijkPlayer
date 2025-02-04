# ijkplayer

#### 介绍
ijkplayer 播放器，自定义

#### ijkplayer源码修改
为了方便ijkplayer c代码开发、调试，现将其编译方式替换成 CMake
详见 A4ijkplayer https://github.com/alanwang4523/A4ijkplayer
c代码来自 ijkplayer-android https://github.com/Bilibili/ijkplayer.git 主分支

#### 降低延迟优化 (已弃用)
减少FFmpeg拆帧等待延时：
https://github.com/0voice/cpp_backend_awsome_blog/blob/main/%E3%80%90NO.459%E3%80%91RTSP%E7%9B%B4%E6%92%AD%E5%BB%B6%E6%97%B6%E7%9A%84%E6%B7%B1%E5%BA%A6%E4%BC%98%E5%8C%96%EF%BC%88%E5%B9%B2%E8%B4%A7%EF%BC%89.md
原理：
https://blog.csdn.net/intel1985/article/details/112864008
优点：
可降低一帧的延迟，大概30ms
缺点：
ffmpeg源码改动后，当画面不动时，帧类型为1，解析rtp包后pts=0。
导致无法录制视频，同上提示报错信息（The CSD is not calculated）
//CSD-0/CSD-1 指的就是 H264中的 PPS 和 SPS。

#### 启动流程

1.//IjkMediaPlayer.java //异步准备数据
prepareAsync()
_prepareAsync()
//ijkplayer_jni.c
IjkMediaPlayer_prepareAsync()
//ijkplayer.c
ijkmp_prepare_async(mp)
ijkmp_prepare_async_l(mp)
//ff_ffplay.c //打开源文件
ffp_prepare_async_l(mp->ffplayer, mp->data_source);
stream_open(ffp, file_name, NULL);

2.//////////创建线程-渲染画面
SDL_CreateThreadEx(&is->_video_refresh_tid, video_refresh_thread, ffp, "ff_vout");
video_refresh(ffp, &remaining_time);
//有缓存时，立即播放
video_display2(ffp);
video_image_display2(ffp);
    //从队列中取出解码后的数据    
    vp = frame_queue_peek_last(&is->pictq);
SDL_VoutDisplayYUVOverlay(ffp->vout, vp->bmp);
//ijksdl_vout_android_nativewindow.c //渲染画面
vout->display_overlay(vout, overlay); 
int retval = func_display_overlay_l(vout, overlay);
//有两种情况 switch(overlay->format)

2.1//////////case SDL_FCC__AMC: 硬解码
//解码器释放缓冲区，自动渲染到surface
IJK_EGL_terminate(opaque->egl);
SDL_VoutOverlayAMediaCodec_releaseFrame_l(overlay, NULL, true);
SDL_VoutAndroid_releaseBufferProxyP_l(opaque->vout, &opaque->buffer_proxy, render);
SDL_VoutAndroid_releaseBufferProxy_l(vout, *proxy, render);
    //java方法
    SDL_AMediaCodecJava_releaseOutputBuffer
    J4AC_MediaCodec__releaseOutputBuffer
    MediaCodec.releaseOutputBuffer

2.2//////////case SDL_FCC_RV32: 软解码 
SDL_Android_NativeWindow_display_l(native_window, overlay);
//android_nativewindow.c
android_render_rgb32_on_rgb8888(out_buffer, overlay);
android_render_rgb_on_rgb(out_buffer, overlay, 32);


3.//////////创建线程 读取数据&解码
SDL_CreateThreadEx(&is->_read_tid, read_thread, ffp, "ff_read");
//打开数据流 音频+视频+字幕
ret = stream_component_open(ffp, st_index[AVMEDIA_TYPE_VIDEO]);
//开始解码
decoder_start(&is->viddec, video_thread, ffp, "ff_video_dec")
ffpipenode_run_sync(ffp->node_vdec);
node->func_run_sync(node);
//有两种情况

3.1//////////硬解 func_run_sync
//ffpipenode_android_mediacodec_vdec.c
//线程。解码器写入数据，消耗缓冲区q->nb_packets-- 内部无限循环
opaque->enqueue_thread = SDL_CreateThreadEx(&opaque->_enqueue_thread, enqueue_thread_func, node, "amediacodec_input_thread");
    //解码器写入数据
    ret = feed_input_buffer(env, node, AMC_INPUT_TIMEOUT_US, &dequeue_count);
    //写入数据
    input_buffer_index = SDL_AMediaCodec_dequeueInputBuffer(opaque->acodec, timeUs);
    copy_size = SDL_AMediaCodec_writeInputData(opaque->acodec, input_buffer_index, d->pkt_temp.data, d->pkt_temp.size);
        //java方法
        SDL_AMediaCodecJava_writeInputData
        J4AC_MediaCodec__getInputBuffers__catchAll
        J4AC_android_media_MediaCodec__getInputBuffers__catchAll
        MediaCodec.getInputBuffers
    amc_ret = SDL_AMediaCodec_queueInputBuffer(opaque->acodec, input_buffer_index, 0, copy_size, time_stamp, queue_flags);
        //java方法
        SDL_AMediaCodecJava_queueInputBuffer
        J4AC_MediaCodec__queueInputBuffer
        J4AC_android_media_MediaCodec__queueInputBuffer
        MediaCodec.queueInputBuffer
//循环。解码器读取数据
ret = drain_output_buffer(env, node, timeUs, &dequeue_count, frame, &got_frame);
int ret = drain_output_buffer_l(env, node, timeUs, dequeue_count, frame, got_frame);
    //解码器读取数据
    output_buffer_index = SDL_AMediaCodecFake_dequeueOutputBuffer(opaque->acodec, &bufferInfo, timeUs);
        //java方法
        SDL_AMediaCodecJava_dequeueOutputBuffer
        J4AC_MediaCodec__dequeueOutputBuffer
        dequeueOutputBuffer
    //获取视频帧
    ret = amc_fill_frame(node, frame, got_frame, output_buffer_index, SDL_AMediaCodec_getSerial(opaque->acodec), &bufferInfo);
    frame->opaque = SDL_VoutAndroid_obtainBufferProxy(opaque->weak_vout, acodec_serial, output_buffer_index, buffer_info);
    proxy = SDL_VoutAndroid_obtainBufferProxy_l(vout, acodec_serial, buffer_index, buffer_info);

//将编码后的数据，添加到队列 ffp->is->pictq->queue 同软解
ret = ffp_queue_picture(ffp, frame, pts, duration, av_frame_get_pkt_pos(frame), is->viddec.pkt_serial);
queue_picture(ffp, src_frame, pts, duration, pos, serial);


3.2//////////软解 func_run_sync
//ffpipenode_ffplay_vdec.c
ffp_video_thread(opaque->ffp);
//ff_ffplay.c 播放视频线程，内部无限循环 for (;;) {}
ffplay_video_thread(ffp);

//获取视频帧
ret = get_video_frame(ffp, frame);
    //从解码器取回解码后的数据, 将编码后的数据发送给解码器
    got_picture = decoder_decode_frame(ffp, &is->viddec, frame, NULL)
    //从解码器取回解码后的数据 AVFrame
    ret = avcodec_receive_frame(d->avctx, frame);
    //将编码后的数据发送给解码器 AVPacket
    result = avcodec_send_packet(d->avctx, &pkt);
//将解码后的数据，添加到队列 ffp->is->pictq->queue
ret = queue_picture(ffp, frame, pts, duration, frame->pkt_pos, is->viddec.pkt_serial);
    //AVFrame => Frame.SDL_VoutOverlay
    SDL_VoutFillFrameYUVOverlay(vp->bmp, src_frame)

////截图
////save_png(frame, ffp->screen_file_name);
////录像
////ffp_record_file(ffp, &pkt)


