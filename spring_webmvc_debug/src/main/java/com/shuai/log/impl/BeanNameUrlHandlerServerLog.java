package com.shuai.log.impl;

import com.shuai.log.AbstractServerLogger;
import com.shuai.util.NetworkUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/30 21:18
 * @version: 1.0
 */

public class BeanNameUrlHandlerServerLog extends AbstractServerLogger {

	private static final String METHOD_NAME = "handleRequest";

	private static final Class<?>[] PARAM_TYPES = new Class[] {HttpServletRequest.class, HttpServletResponse.class};

	@Override
	public void startLog(HttpServletRequest request, HttpServletResponse response, Object handler) {
		super.startLog(request, response, handler);
		String message = getMessage(request, response, handler);
		log(getStartLog(request, message));
	}

	@Override
	public void endLog(HttpServletRequest request, HttpServletResponse response, Object handler) {
		super.endLog(request, response, handler);
		String message = getMessage(request, response, handler);
		log(getEndLog(request, message));
	}

	private String getMessage(HttpServletRequest request, HttpServletResponse response, Object handler){
		String address = NetworkUtil.getRequestIp(request);
		Class<?> aClass = handler.getClass();
		Method method = null;
		try {
			method = aClass.getMethod(METHOD_NAME, PARAM_TYPES);
		} catch (NoSuchMethodException e) {

		}
		return address + " : " + handler.getClass()+ "::" + getMethodDescriptor(method, PARAM_TYPES);
	}
}
