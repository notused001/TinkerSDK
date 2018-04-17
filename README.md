## Tinker介绍
Tinker是微信官方的Android热补丁解决方案，它支持动态下发代码、So库以及资源，让应用能够在不需要重新安装的情况下实现更新。当然，你也可以使用Tinker来更新你的插件。
## Tinker核心原理
- 基于Android原生的ClassLoader，开发了自己的ClassLoader
- 基于Android原生的aapt，开发了自己的aapt
- 微信团队自己基于Dex文件的格式，研发了DexDiff算法
## 使用Tinker完成Bug修复
###### 首先添加Tinker依赖
```
//生成appilcation时使用
    provided('com.tencent.tinker:tinker-android-anno:1.7.7')

    //tinker的核心sdk库
    compile('com.tencent.tinker:tinker-android-lib:1.7.7')
```
###### Create TinkerManager来管理Tinker的API
```
 //是否初始化Tinker
    private static boolean isInstalled = false;
    private static ApplicationLike mAppLike;
    /**
     * 完成Tinker的初始化
     * @param applicationLike
     */
    public static void installTinker(ApplicationLike applicationLike) {
        mAppLike = applicationLike;
        if (isInstalled) {
            return;
        }
        TinkerInstaller.install(mAppLike); //完成tinker初始化
        isInstalled = true;
    }
    //完成Patch文件的加载
    public static void loadPatch(String path) {
        if (Tinker.isTinkerInstalled()) {
            TinkerInstaller.onReceiveUpgradePatch(getApplicationContext(), path);
        }
    }
    //通过ApplicationLike获取Context
    private static Context getApplicationContext() {
        if (mAppLike != null) {
            return mAppLike.getApplication().getApplicationContext();
        }
        return null;
    }
```
###### 需要在继承ApplicationLike的类中调用Tinker的API，完成初始化。
这里要类要添加注解，作用就是通过ApplicationLike对象生成Application对象
```
@DefaultLifeCycle(application = ".MyTinkerApplication",
        flags = ShareConstants.TINKER_ENABLE_ALL,
        loadVerifyFlag = false)
```

需要在这个生命周期回调中完成Tinker的初始化
```
@Override
    public void onBaseContextAttached(Context base) {
        super.onBaseContextAttached(base);
        //使应用支持分包
//        MultiDex.install(base);

        TinkerManager.installTinker(this);
    }
```
###### 为什么需要ApplicationLike对象？而不直接在应用的Application中完成Tinker初始化？
是因为Tinker需要监听Application的生命周期，所以通过ApplicationLike对象进行一个委托，通过委托可以在ApplicationLike中完成对Tinker生命周期的监听，然后在不同的Application生命周期阶段去做不同的初始化工作等等，如果Tinker没有使用这种委托的模式，Tinker的初始化会非常的复杂，而且需要我们动手自己去做，而通过这种代理的方式，就会把所有的工作封装在ApplicationLike对象中。
###### 完成修复更新
要在清单文件中加上下面的配置，作用是我们的tpatch文件是否可以安装到apk中
```
<meta-data
        android:name="UMENG_CHANNEL"
        android:value="${UMENG_CHANNEL_VALUE}" />
```
步骤仍然是创建俩个apk一个是old一个是new。
- patch生成方式
1 使用命令行的方式完成patch包的生成
   官网下载命令行工具包
![](https://upload-images.jianshu.io/upload_images/11184437-e50d374affb92434.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

这四个文件是Tinker对patch文件的混淆

![](https://upload-images.jianshu.io/upload_images/11184437-2e226e5aa79a95c2.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

jar是用来patch文件的
xml是需要配置的一些参数
xml文件中loader需要改成自己app对应的包
![](https://upload-images.jianshu.io/upload_images/11184437-3568df128fe3f7d4.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

还有一些app签名信息需要修改
![](https://upload-images.jianshu.io/upload_images/11184437-6306af5b2650db46.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

输入命令
`java -jar tinker-patch-cli.jar -old old.apk -new new.apk -config tinker_config.xml -out output_path`

将生成的.apk补丁文件，push到手机上，完成安装即可。



2 使用gradle插件的方式完成patch包的生成
有时间会补充上

## Tinker组件化封装
- 具体实现思路
1 首先调用AndFix模块的checkHasPatch()向服务器检查当前是否有新的patch
2 服务器会返回一个实体对象return PatchUpdateInfo,里面包含相关的Patch信息
3 调用hasNewPatch()来判断是否有新的Patch
4 如果没有Patch，直接stopSeif()
5 如果有新的Patch，调用downLoadPatch()，startDownload下载Patch文件
6 下载完成后，把服务器return PatchFile，返回的Patch文件保存到手机存储。
7 最后调用TinkerManager来安装补丁文件

![](https://upload-images.jianshu.io/upload_images/11184437-fb785489aebb02b8.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
- 具体编码
Create TinkerService
主要完成：
 应用程序Tinker更新服务：
  1.从服务器下载patch文件
  2.使用TinkerManager完成patch文件加载
  3.patch文件会在下次进程启动时生效
具体一些变量的定义
```
    private static final String FILE_END = ".apk"; //文件后缀名
    private static final int DOWNLOAD_PATCH = 0x01; //下载patch文件信息
    private static final int UPDATE_PATCH = 0x02; //检查是否有patch更新

    private String mPatchFileDir; //patch要保存的文件夹
    private String mFilePtch; //patch文件保存路径
    private BasePatch mBasePatchInfo; //服务器patch信息
```
onCreate()  完成patch文件夹的创建
```
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
```
onStartCommand()  检查是否有patch更新
```
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
```
如果有更新则实现下载，并且调用TinkerManager.loadPatch()加载补丁文件
```
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

                        TinkerManager.loadPatch(mFilePtch, mBasePatchInfo.data.md5);
                    }

                    @Override
                    public void onFailure(Object reasonObj) {
                        stopSelf();
                    }
                });
    }
```