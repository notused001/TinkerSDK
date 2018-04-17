package com.warrior.network;

/**
 * @author: Jamie
 * @function: all url in the sdk
 */
public class HttpConstant {

    private static final String ROOT_URL = "http://www.imooc.com/api";
    /**
     * 检查是否有patch文件更新
     */
    public static String UPDATE_PATCH_URL = ROOT_URL + "/tinker/update.php";

    /**
     * patch文件下载地址
     */
    public static String DOWNLOAD_PATCH_URL = ROOT_URL + "/tinker/download_patch.php";

}
