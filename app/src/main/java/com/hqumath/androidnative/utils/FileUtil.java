package com.hqumath.androidnative.utils;

import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * ****************************************************************
 * 文件名称: FileUtils
 * 作    者: Created by gyd
 * 创建时间: 2019/3/1 14:35
 * 文件描述: 文件管理
 * 注意事项:
 * 版权声明:
 * ****************************************************************
 */
public class FileUtil {

    /**
     * 获取应用专属内部存储文件 /data/user/0/pacakge/files
     *
     * @param dirName  父文件名
     * @param fileName 子文件名
     */
    public static File getFile(String dirName, String fileName) {
        //父文件目录
        String dirPath = CommonUtil.getContext().getFilesDir() + File.separator + dirName;
        File dir = new File(dirPath);
        if (!dir.exists())
            dir.mkdirs();
        //子文件目录
        String filePath = dirPath + File.separator + fileName;
        return new File(filePath);
    }

    /**
     * 获取应用专属外部存储空间文件 /storage/emulated/0/Android/data/packname/files
     *
     * @param dirName  父文件名
     * @param fileName 子文件名
     */
    public static File getExternalFile(String dirName, String fileName) {
        String filePath = CommonUtil.getContext().getExternalFilesDir(dirName) + File.separator + fileName;
        return new File(filePath);
    }

    /**
     * 获取外部存储空间根目录 /storage/emulated/0/dirname/filename
     *
     * @param dirName  父文件名
     * @param fileName 子文件名
     */
    public static File getExternalRootFile(String dirName, String fileName) {
        //父文件目录
        String dirPath = Environment.getExternalStorageDirectory() + File.separator + dirName;
        File dir = new File(dirPath);
        if (!dir.exists())
            dir.mkdirs();
        //子文件目录
        String filePath = dirPath + File.separator + fileName;
        return new File(filePath);
    }

    /**
     * 根据url生成文件
     */
    public static File getFileFromUrl(String url) {
        //获取文件名称类型
        String fileNameFromUrl1 = url.substring(url.lastIndexOf("/") + 1);
        //String fileName = fileNameFromUrl1.substring(0, fileNameFromUrl1.lastIndexOf("."));//文件名
        String fileStyle = fileNameFromUrl1.substring(fileNameFromUrl1.lastIndexOf(".") + 1);//文件类型
        //生成文件目录
        File fileDir = CommonUtil.getContext().getExternalFilesDir(fileStyle);
        if (!fileDir.exists())
            fileDir.mkdirs();
        String filePath = fileDir.getAbsolutePath() + "/" + fileNameFromUrl1;
        return new File(filePath);
    }

    /**
     * 根据app版本号生成文件
     */
    public static File getFileFromVersionName(String version) {
        //获取文件名称类型
        String fileNameFromUrl1 = CommonUtil.getContext().getPackageName() + "_" + version;
        String fileStyle = "apk";//文件类型
        //生成文件目录
        File fileDir = CommonUtil.getContext().getExternalFilesDir(fileStyle);
        if (!fileDir.exists())
            fileDir.mkdirs();
        String filePath = fileDir.getAbsolutePath() + "/" + fileNameFromUrl1;
        return new File(filePath);
    }

    private static void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 以字节流读取文件
     *
     * @param file 文件
     * @return 字节数组
     */
    public static byte[] getByteStream(File file) {
        try {
            // 拿到输入流
            FileInputStream input = new FileInputStream(file);
            // 建立存储器
            byte[] buf = new byte[input.available()];
            // 读取到存储器
            input.read(buf);
            // 关闭输入流
            input.close();
            // 返回数据
            return buf;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //写入sdp文件
    public static String writeSdpFile(int localPort, int payloadType, boolean isH265) {
        File file = FileUtil.getExternalFile("sdp", localPort + ".sdp");
        StringBuilder sb = new StringBuilder()
                .append("v=0\n")
                .append("o=- 0 0 IN IP4 127.0.0.1\n")
                .append("s=No Name\n")
                .append("c=IN IP4 127.0.0.1\n")
                .append("t=0 0\n")
                .append("m=video ").append(localPort).append(" RTP/AVP ").append(payloadType).append("\n")
                .append("a=rtpmap:").append(payloadType).append(isH265 ? " H265" : " H264").append("/90000");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);//重写文件
            fos.write(sb.toString().getBytes());
            //LogUtil.d("写文件：" + file.getAbsolutePath() + " 大小：" + sb.toString().getBytes().length);
            fos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                    fos = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return file.getAbsolutePath();
    }
}
