package com.warrior.tinker;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.warrior.network.RequestCenter;
import com.warrior.network.listener.DisposeDataListener;
import com.warrior.network.listener.DisposeDownloadListener;
import com.warrior.tinker.module.BasePatch;

import java.io.File;

/**
 * @function 应用程序Tinker更新服务：
 * 1.从服务器下载patch文件
 * 2.使用TinkerManager完成patch文件加载
 * 3.patch文件会在下次进程启动时生效
 */
public class TinkerService extends Service {

    private static final String FILE_END = ".apk"; //文件后缀名
    private static final int DOWNLOAD_PATCH = 0x01; //下载patch文件信息
    private static final int UPDATE_PATCH = 0x02; //检查是否有patch更新

    private String mPatchFileDir; //patch要保存的文件夹
    private String mFilePtch; //patch文件保存路径
    private BasePatch mBasePatchInfo; //服务器patch信息


    @Override
    public void onCreate() {
        super.onCreate();
        init();
    }


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case UPDATE_PATCH:
                    checkPatchInfo();
                    break;
                case DOWNLOAD_PATCH:
                    downloadPatch();
                    break;
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //检查是否有patch更新
        mHandler.sendEmptyMessage(UPDATE_PATCH);
        return START_NOT_STICKY; //被系统回收不再重启
    }


    //用来与被启动者通信的接口
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //对外提供启动servcie方法
    public static void runTinkerService(Context context) {
        try {
            Intent intent = new Intent(context, TinkerService.class);
            context.startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //初始化变量
    private void init() {
        mPatchFileDir = getExternalCacheDir().getAbsolutePath() + "/tpatch/";
        File patchFileDir = new File(mPatchFileDir);
        try {
            if (patchFileDir == null || !patchFileDir.exists()) {
                patchFileDir.mkdir(); //文件夹不存在则创建
            }
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf(); //无法正常创建文件，则终止服务
        }
    }


    private void checkPatchInfo() {
        RequestCenter.requestPatchUpdateInfo(new DisposeDataListener() {

            @Override
            public void onSuccess(Object responseObj) {
                mBasePatchInfo = (BasePatch) responseObj;
                mHandler.sendEmptyMessage(DOWNLOAD_PATCH);
            }

            @Override
            public void onFailure(Object reasonObj) {
                stopSelf();
            }
        });
    }

    private void downloadPatch() {

        mFilePtch = mPatchFileDir.concat(String.valueOf(System.currentTimeMillis()))
                .concat(FILE_END);
        RequestCenter.downloadFile(mBasePatchInfo.data.downloadUrl, mFilePtch,
                new DisposeDownloadListener() {
                    @Override
                    public void onProgress(int progrss) {
                        //可以打印文件下载进行
                    }

                    @Override
                    public void onSuccess(Object responseObj) {

                        TinkerManager.loadPatch(mFilePtch);
                    }

                    @Override
                    public void onFailure(Object reasonObj) {
                        stopSelf();
                    }
                });
    }
}
