package com.shuai.log.impl;

import com.shuai.log.AbstractServerLogger;
import com.shuai.util.NetworkUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/30 20:12
 * @version: 1.0
 */

public class HandlerMethodHandlerServerLog extends AbstractServerLogger {

	@Override
	public void startLog(HttpServletRequest request, HttpServletResponse response, Object handler){
		super.startLog(request, response, handler);
		String message = getMessage(request, response, (HandlerMethod) handler);
		log(getStartLog(request, message));
	}

	@Override
	public void endLog(HttpServletRequest request, HttpServletResponse response, Object handler){
		super.endLog(request, response, handler);
		String message = getMessage(request, response, (HandlerMethod) handler);
		log(getEndLog(request, message));
	}

	private String getMessage(HttpServletRequest request, HttpServletResponse response, HandlerMethod handler){
		String address = NetworkUtil.getRequestIp(request);
		return address + " : " + handler.getBean().getClass()+ "::" + getMethodDescriptor(handler.getMethod());
	}
}
