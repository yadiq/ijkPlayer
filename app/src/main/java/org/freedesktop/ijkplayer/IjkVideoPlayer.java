package org.freedesktop.ijkplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;

import com.hqumath.androidnative.utils.CommonUtil;
import com.hqumath.androidnative.utils.LogUtil;

import java.io.File;
import java.io.IOException;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.widget.FileMediaDataSource;
import tv.danmaku.ijk.media.player.widget.IRenderView;
import tv.danmaku.ijk.media.player.widget.TextureRenderView;

/**
 * ****************************************************************
 * 作    者: Created by gyd
 * 创建时间: 2024/10/10 9:57
 * 文件描述:
 * 注意事项: TextureRenderView
 * ****************************************************************
 */
public class IjkVideoPlayer {
    private final String TAG = "IjkVideoPlayer";

    // all possible internal states
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    private IRenderView mRenderView;
    private IRenderView.ISurfaceHolder mSurfaceHolder = null;

    private IjkMediaPlayer mMediaPlayer = null;
    private IMediaPlayer.OnErrorListener mOnErrorListener;
    private IMediaPlayer.OnInfoListener mOnInfoListener;

    private int mCurrentState = STATE_IDLE;//当前播放状态
    private Uri mUri;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoSarNum;
    private int mVideoSarDen;

    public void setRenderView(IRenderView renderView) {
        if (mRenderView != null) {
            if (mMediaPlayer != null)
                mMediaPlayer.setDisplay(null);
            mRenderView.removeRenderCallback(mSHCallback);
            mRenderView = null;
        }
        if (renderView == null)
            return;
        mRenderView = renderView;
        if (mVideoWidth > 0 && mVideoHeight > 0)
            renderView.setVideoSize(mVideoWidth, mVideoHeight);
        if (mVideoSarNum > 0 && mVideoSarDen > 0)
            renderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
        mRenderView.addRenderCallback(mSHCallback);
    }

    public IRenderView getRenderView() {
        return mRenderView;
    }

    /**
     * Sets video path.
     *
     * @param path the path of the video.
     */
    public void setVideoPath(String path) {
        mUri = Uri.parse(path);
        openVideo();
        //requestLayout();
        //invalidate();
    }

    private void openVideo() {
        //音频
        AudioManager am = (AudioManager) CommonUtil.getContext().getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        try {
            mMediaPlayer = createPlayer();
            mMediaPlayer.setOnPreparedListener(mPreparedListener);//设置画面尺寸
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);//设置画面尺寸&纵横比
            //mMediaPlayer.setOnCompletionListener(mCompletionListener);//播放完成
            mMediaPlayer.setOnErrorListener(mErrorListener);//异常
            mMediaPlayer.setOnInfoListener(mInfoListener);//消息
            //mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);//监听缓冲进度
            //mMediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
            //mMediaPlayer.setOnTimedTextListener(mOnTimedTextListener);
            String scheme = mUri.getScheme();
            //TODO 测试api低于23 能否打开文件。x86 api21 ok
            if (TextUtils.isEmpty(scheme) || ContentResolver.SCHEME_FILE.equalsIgnoreCase(scheme)) {//为空，或忽略大小写相同
                IMediaDataSource dataSource = new FileMediaDataSource(new File(mUri.toString()));
                mMediaPlayer.setDataSource(dataSource);
            } else {
                mMediaPlayer.setDataSource(mUri.toString());
            }

            bindSurfaceHolder(mMediaPlayer, mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);//音频
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();
            mCurrentState = STATE_PREPARING;
        } catch (IOException | IllegalArgumentException ex) {
            LogUtil.e(TAG, "Unable to open content. " + ex);
            mCurrentState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        }
    }

    public IjkMediaPlayer createPlayer() {
        IjkMediaPlayer ijkMediaPlayer = new IjkMediaPlayer();
        ijkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT);//更少的日志

        //硬解比软解延迟低。gsttreamer比ffmpeg延迟低 200ms=>430ms。(未来修改参数降低延迟)
        //////////////////////// FFP_OPT_CATEGORY_PLAYER player_opts

        ////////////// original options , 参数值 (0, 0, 1) (default__, min__, max__)
        //disable audio
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "an", 1);
        //允许不符合规范的加速技巧 (0, 0, 1)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1);//实际不能降低延迟
        //不要限制输入缓冲区大小（对实时流有用） (0, 0, 1)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 0);//1 不要限制输入缓冲区大小（用于实时流）TODO
        //当CPU处理不过来的时候的丢帧帧数，(0, -1, 120)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 120);//实际未使用
        //读取并解码流，用启发式方法填充缺失的信息 (1, 0, 1)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "find_stream_info", 0);//是否探测。不探测时可加快加载速度，补全视频宽高比640：360
//            //设置视频显示格式
//            //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
//            //在资源准备好后自动播放，(1, 0, 1)
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
//            //video-pictq-size 最大图片队列帧数
//            //应预先读取的最大缓冲区大小，(15 * 1024 * 1024, 0, 15 * 1024 * 1024)
        // 数值越大延时会增大,个人感觉在直播时设置的缓存太大可能在播放一段时间后,延时时间会增大。配合下面的min-frames预读
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 0);//非直播时使用
        //停止预读的最小帧数 (50000, 2, 50000) 至少预读2帧
        //预读的 packet 超过 MIN_FRAMES 个，那么 ffplay 就会停止预读
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 2);//非直播时使用
        //暂停输出，直到在暂停后读取了足够的数据包 (1, 0, 1)
        //关闭播放器缓冲，这个必须关闭，否则会出现播放一段时间后，一直卡主，控制台打印 FFP_MSG_BUFFERING_START
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);//0 关闭缓冲区 TODO
        //自己添加 max_cached_duration https://www.jianshu.com/p/d6a5d8756eec (已通过去掉音视频同步实现，收到数据立即解码播放）
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 30);//最大缓存大小,解决因网络抖动引起缓冲区变大
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync", "ext");//尝试保持实时
//            //sync-av-start 同步a/v开始时间 (1, 0, 1)
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync-av-start", 1);
//            //no-time-adjust 从媒体流中返回播放器的实时时间，而不是调整后的时间
//            //skip-calc-frame-rate 不计算实际帧率 (0, 0, 1)
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "skip-calc-frame-rate", 1);


        //////////////////////// Android only options
        //开启 Mediacodec 硬解
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 1);
        //根据 meta 信息自动旋转视频。MediaCodec: auto rotate frame depending on meta (0, 0, 1)
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 0);
        //处理分辨率改变。MediaCodec: handle resolution change automatically 仅h264有效
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);
        //音频播放是否使用 openSL，1：使用 openSL，0：使用 AudioTrack。OpenSL ES: enable
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
        //使用msgqueue进行同步 (0, 0, 1)
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-sync", 1);

        //soundtouch 音频变调变速。SoundTouch: enable
        //mediacodec-default-name
        //ijkmeta-delay-init
        //render-wait-start

        //////////////////////// OPT_CATEGORY_FORMAT format_opts ，avformat_open_input 打开输入文件参数（所有的解复用参数）
        //探测数据
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "nobuffer");//探测的数据包不丢进队列
//            //分析媒体文件的持续时间。默认为 5,000,000微秒 （5秒）
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", "2000000");
//            //分析媒体文件时读取的数据大小 必须是一个不小于32的整数。默认情况下为 5000000 。
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", "40960");

        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);//重连模式，如果中途服务器断开了连接，让它重新连接   这个都不知道正不正确，在网上也看见将1改为"true"表示重连的.
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtbufsize", 60);//最大内存用于缓冲实时帧
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "avioflags", "direct");//减少缓冲
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1);//立即写出数据包。在每个数据包之后启用 I/O 上下文的刷新
        //ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "udp");//rtsp传输协议,默认udp。tcp时不需排序rtp包()
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max_delay", 0);//最大重新排序延迟 μs (500000=0.5 秒) TODO 现有数据源UDP不会乱序
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reorder_queue_size", 0);//设置要缓冲的数据包数量以处理重新排序的数据包,默认500. 影响"max_delay"

        //////////////////////// OPT_CATEGORY_CODEC codec_opts ，avcodec_open2 打开解码器参数
        //环路滤波 是否开启跳过 loop filter，0 为开启，画面质量高，但解码开销大，48 为关闭，画面质量稍差，但解码开销小
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);//开启探测，且帧率高于31时可用
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 0);//跳过对选定帧的解码。开启探测，且帧率高于31时可用
        //{"rtbufsize", "max memory used for buffering real-time frames", OFFSET(max_picture_buffer), AV_OPT_TYPE_INT, {.i64 = 3041280 }, 0, INT_MAX, D}, /* defaults to 1s of 15fps 352x288 YUYV422 video */
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "tune", "zerolatency");//解码器调节。快速编码和低延迟流 错误的配置

        return ijkMediaPlayer;
    }

    private void bindSurfaceHolder(IMediaPlayer mp, IRenderView.ISurfaceHolder holder) {
        if (mp == null)
            return;
        if (holder == null) {
            mp.setDisplay(null);
            return;
        }
        holder.bindToMediaPlayer(mp);
    }

    /*
     * 关闭播放器
     */
    public void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            AudioManager am = (AudioManager) CommonUtil.getContext().getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    //设置画面缩放&纵横比
    //可能会剪裁,保持原视频的大小，显示在中心,当原视频的大小超过view的大小超过部分裁剪处理
    //int AR_ASPECT_FIT_PARENT = 0;
    //可能会剪裁,等比例放大视频，直到填满View为止,超过View的部分作裁剪处理
    //int AR_ASPECT_FILL_PARENT = 1;
    //将视频的内容完整居中显示，如果视频大于view,则按比例缩视频直到完全显示在view中
    //int AR_ASPECT_WRAP_CONTENT = 2;
    //不剪裁,非等比例拉伸画面填满整个View
    //int AR_MATCH_PARENT = 3;
    //不剪裁,非等比例拉伸画面到16:9,并完全显示在View中
    //int AR_16_9_FIT_PARENT = 4;
    //不剪裁,非等比例拉伸画面到4:3,并完全显示在View中
    //int AR_4_3_FIT_PARENT = 5;
    public void setAspectRatio(int aspectRatio) {
        if (mRenderView != null)
            mRenderView.setAspectRatio(aspectRatio);
    }

    //设置画面旋转角度 0 90 180 270
    public void setVideoRotation(int degree) {
        if (mRenderView != null)
            mRenderView.setVideoRotation(degree);
    }

    //截图
    public Bitmap getShortcut() {
        if (mRenderView != null && mRenderView instanceof TextureRenderView) {
            return ((TextureRenderView)mRenderView).getBitmap();
        } else {
            return null;
        }
    }

    /**
     * 录像
     * @param recordVideoPath 文件地址
     * @return 结果
     */
    //注意：直播中断存在的问题，不会自动停止录制。
    //直播中断产生的问题，时间戳不是从 0 开始的、时间戳有跳变（不是顺序递增的）、时间戳是负数
    public boolean startRecord(String recordVideoPath) {
        boolean result = false;
        if (mMediaPlayer != null) {
            result = mMediaPlayer.startRecord(recordVideoPath) == 0;
        }
        return result;
    }

    /**
     * 停止录像
     */
    public void stopRecord() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stopRecord();
        }
    }

    /**
     * 是否在录制
     * @return
     */
    public boolean isRecord() {
        boolean result = false;
        if (mMediaPlayer != null) {
            result = mMediaPlayer.isRecord() == 1;//如果是在录像中返回的值是 1
        }
        return result;
    }

    public void start() {//自动开始播放
        mMediaPlayer.start();
        mCurrentState = STATE_PLAYING;
    }

    public void pause() {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            mCurrentState = STATE_PAUSED;
        }
    }

    /////////////////////////////监听/////////////////////////////
    IRenderView.IRenderCallback mSHCallback = new IRenderView.IRenderCallback() {

        //当TextureView准备好使用Surface的SurfaceTexture时调用。可以使用
        @Override
        public void onSurfaceCreated(IRenderView.ISurfaceHolder holder, int width, int height) {
            if (holder.getRenderView() != mRenderView) {
                LogUtil.e(TAG, "onSurfaceCreated: unmatched render callback\n");
                return;
            }
            mSurfaceHolder = holder;
            if (mMediaPlayer != null)
                bindSurfaceHolder(mMediaPlayer, holder);
        }

        //SurfaceTexture的缓冲区大小更改时调用。
        @Override
        public void onSurfaceChanged(IRenderView.ISurfaceHolder holder, int format, int width, int height) {
            if (holder.getRenderView() != mRenderView) {
                LogUtil.e(TAG, "onSurfaceChanged: unmatched render callback\n");
                return;
            }
        }

        //在将SurfaceTexture要销毁指定的对象时调用
        @Override
        public void onSurfaceDestroyed(IRenderView.ISurfaceHolder holder) {
            if (holder.getRenderView() != mRenderView) {
                LogUtil.e(TAG, "onSurfaceDestroyed: unmatched render callback\n");
                return;
            }
            mSurfaceHolder = null;
            if (mMediaPlayer != null)
                mMediaPlayer.setDisplay(null);
        }
    };

    public void setOnErrorListener(IMediaPlayer.OnErrorListener l) {
        mOnErrorListener = l;
    }

    public void setOnInfoListener(IMediaPlayer.OnInfoListener l) {
        mOnInfoListener = l;
    }

    IMediaPlayer.OnPreparedListener mPreparedListener = new IMediaPlayer.OnPreparedListener() {
        public void onPrepared(IMediaPlayer mp) {
            mCurrentState = STATE_PREPARED;
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                LogUtil.d(TAG, "onPrepared video size: " + mVideoWidth + "/" + mVideoHeight);
                if (mRenderView != null) {
                    mRenderView.setVideoSize(mVideoWidth, mVideoHeight);//设置画面尺寸
                    mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);//设置画面纵横比
                }
            }
        }
    };

    IMediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new IMediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    mVideoSarNum = mp.getVideoSarNum();
                    mVideoSarDen = mp.getVideoSarDen();
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        if (mRenderView != null) {
                            mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                            mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                        }
                        //requestLayout();
                    }
                }
            };

    private IMediaPlayer.OnErrorListener mErrorListener = new IMediaPlayer.OnErrorListener() {

        @Override
        public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
            LogUtil.e(TAG, "onError : " + framework_err + "," + impl_err);
            mCurrentState = STATE_ERROR;

            if (mOnErrorListener != null) {
                if (mOnErrorListener.onError(mMediaPlayer, framework_err, impl_err)) {
                    return true;
                }
            }
            //CommonUtil.toast("播放失败 " + framework_err);
            return false;
        }
    };

    private IMediaPlayer.OnInfoListener mInfoListener = new IMediaPlayer.OnInfoListener() {

        @Override
        public boolean onInfo(IMediaPlayer mp, int what, int extra) {
            if (mOnInfoListener != null) {
                mOnInfoListener.onInfo(mp, what, extra);
            }
            switch (what) {
                case IMediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                    LogUtil.d(TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING:");
                    break;
                case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    LogUtil.d(TAG, "MEDIA_INFO_VIDEO_RENDERING_START:");
                    break;
                case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                    LogUtil.d(TAG, "MEDIA_INFO_BUFFERING_START:");
                    break;
                case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                    LogUtil.d(TAG, "MEDIA_INFO_BUFFERING_END:");
                    break;
                case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                    LogUtil.d(TAG, "MEDIA_INFO_NETWORK_BANDWIDTH: " + extra);
                    break;
                case IMediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                    LogUtil.d(TAG, "MEDIA_INFO_BAD_INTERLEAVING:");
                    break;
                case IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                    LogUtil.d(TAG, "MEDIA_INFO_NOT_SEEKABLE:");
                    break;
                case IMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                    LogUtil.d(TAG, "MEDIA_INFO_METADATA_UPDATE:");
                    break;
                case IMediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                    LogUtil.d(TAG, "MEDIA_INFO_UNSUPPORTED_SUBTITLE:");
                    break;
                case IMediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                    LogUtil.d(TAG, "MEDIA_INFO_SUBTITLE_TIMED_OUT:");
                    break;
                case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                    LogUtil.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED: " + extra);
                            /*if (mRenderView != null) 旋转角度
                                mRenderView.setVideoRotation(arg2);*/
                    break;
                case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
                    LogUtil.d(TAG, "MEDIA_INFO_AUDIO_RENDERING_START:");
                    break;
            }
            return true;
        }
    };
}