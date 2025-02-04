package com.hqumath.androidnative;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.appcompat.app.AppCompatActivity;

import com.hqumath.androidnative.databinding.ActivityMainBinding;
import com.hqumath.androidnative.utils.CommonUtil;
import com.hqumath.androidnative.utils.FileUtil;

import org.freedesktop.ijkplayer.IjkVideo;

import java.io.File;

/**
 * ****************************************************************
 * 作    者: Created by gyd
 * 创建时间: 2023/9/5 16:24
 * 文件描述:
 * 注意事项:
 * ****************************************************************
 */
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private IjkVideo videoHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        CommonUtil.init(this);
        //事件监听
        initListener();
        //初始化数据
        initData();
    }


    @Override
    public void onStart() {
        super.onStart();
        if (videoHelper != null) {
            videoHelper.start();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (videoHelper != null) {
            videoHelper.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoHelper != null) {
            videoHelper.close();
            videoHelper = null;
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyUp(keyCode, event);
    }

    public void initListener() {
        binding.btnStartRecord.setOnClickListener(v -> {
            if (videoHelper.getVideoPlayer().isRecord()) {
                CommonUtil.toast("已经在录制");
                return;
            }
            File file = FileUtil.getExternalFile("record", System.currentTimeMillis() + ".mp4");
            videoHelper.getVideoPlayer().startRecord(file.getAbsolutePath());
            CommonUtil.toast("开始录制 " + file.getAbsolutePath());
        });
        binding.btnStopRecord.setOnClickListener(v -> {
            if (!videoHelper.getVideoPlayer().isRecord()) {
                CommonUtil.toast("未开始录制");
                return;
            }
            videoHelper.getVideoPlayer().stopRecord();
            CommonUtil.toast("录制完成");
        });
        binding.btnShortcut.setOnClickListener(v -> {
            Bitmap bmp = videoHelper.getVideoPlayer().getShortcut();
            binding.btnShortcut.setBackground(new BitmapDrawable(bmp));
        });
    }

    public void initData() {
        //测试地址
        //String url = "https://media.w3.org/2010/05/sintel/trailer.mp4";
        //String url = "http://vjs.zencdn.net/v/oceans.mp4";
        String url = "http://150.138.8.143/00/SNM/CHANNEL00000311/index.m3u8";

        videoHelper = new IjkVideo();
        videoHelper.init(url, binding.renderView, new IjkVideo.VideoListener() {

            @Override
            public void showLoading(boolean isShow) {

            }

            @Override
            public void showNoSignal(boolean isShow) {

            }
        });
        //设置画面缩放&纵横比
//        videoHelper.getVideoPlayer().setAspectRatio(IRenderView.AR_ASPECT_FILL_PARENT);
        //设置画面旋转角度
//        videoHelper.getVideoPlayer().setVideoRotation(90);
    }
}
