package com.shuai.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/30 23:25
 * @version: 1.0
 */

public class NetworkUtil {

	public static final String REQUEST_IP = "request.ip";

	public static String getRequestIp(HttpServletRequest request){
		Object ip = request.getAttribute(REQUEST_IP);
		if (ip == null){
			/**
			 * 0:0:0:0:0:0:0:1是属于ipv6，但是本机又没有设置ipv6，后来我又进行另一台电脑做测试，
			 * 发现这种情况只有在服务器和客户端都在同一台电脑上才会出现（例如用localhost访问的时候才会出现），
			 * 原来是hosts配置文件的问题 windows的hosts文件路径：C:\Windows\System32\drivers\etc\hosts linux的host路径：/etc/hosts
			 */
			ip = request.getRemoteAddr();
		}
		request.setAttribute(REQUEST_IP, ip);
		return (String) ip;
	}
}
