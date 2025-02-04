package org.freedesktop.ijkplayer;

import android.view.View;

import com.hqumath.androidnative.BuildConfig;
import com.hqumath.androidnative.utils.CommonUtil;
import com.hqumath.androidnative.utils.FileUtil;
import com.hqumath.androidnative.utils.LogUtil;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.widget.IRenderView;

/**
 * ****************************************************************
 * 作    者: Created by gyd
 * 创建时间: 2024/6/6 13:34
 * 文件描述: 播放视频
 * 注意事项:
 * ****************************************************************
 */
public class IjkVideo {

    //////////////////////数据源////////////////////
    private final String TAG = "IjkVideo";
    private IjkVideoPlayer videoPlayer;

    /**
     * 初始化视频
     */
    public void init(String url, IRenderView renderView, VideoListener videoListener) {
        //初始化播放器
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        videoPlayer = new IjkVideoPlayer();
        videoPlayer.setRenderView(renderView);
        videoPlayer.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mp, int what, int extra) {
                if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {//开始渲染
                    if (videoListener != null)
                        videoListener.showLoading(false);
                }
                return false;
            }
        });
        videoPlayer.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer mp, int what, int extra) {
                CommonUtil.toast("播放失败 " + what);
                return true;
            }
        });
        if (videoListener != null)
            videoListener.showLoading(true);
        //视频地址
        videoPlayer.setVideoPath(url);
    }

    public IjkVideoPlayer getVideoPlayer() {
        return videoPlayer;
    }

    public void start() {

    }

    public void stop() {

    }

    public void close() {
        //关闭播放器
        if (videoPlayer != null) {
            videoPlayer.stop();
            IjkMediaPlayer.native_profileEnd();
            videoPlayer = null;
        }
    }

    public interface VideoListener {
        void showLoading(boolean isShow);//加载中

        void showNoSignal(boolean isShow);//无视频信号
    }
}
