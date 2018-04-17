package com.warrior.network.listener;

import java.util.ArrayList;

/**
 * @文件描述：当需要专门处理Cookie时创建此回调接口
 */
public interface DisposeHandleCookieListener extends DisposeDataListener
{
	public void onCookie(ArrayList<String> cookieStrLists);
}
