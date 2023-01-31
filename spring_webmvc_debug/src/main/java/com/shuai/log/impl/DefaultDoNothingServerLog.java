package com.shuai.log.impl;

import com.shuai.log.AbstractServerLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/30 21:40
 * @version: 1.0
 */

public class DefaultDoNothingServerLog extends AbstractServerLogger {

	@Override
	public void log(HttpServletRequest request, HttpServletResponse response, Object handler) {
		super.log(request, response, handler);
	}

	@Override
	public void startLog(HttpServletRequest request, HttpServletResponse response, Object handler) {
		super.startLog(request, response, handler);
	}

	@Override
	public void endLog(HttpServletRequest request, HttpServletResponse response, Object handler) {
		super.endLog(request, response, handler);
	}
}
