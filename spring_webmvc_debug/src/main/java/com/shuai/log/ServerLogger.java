package com.shuai.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/30 20:00
 * @version: 1.0
 */

public interface ServerLogger {

	/**
	 * 记录系统操作日志
	 *
	 * @param request
	 * @param response
	 * @param handler
	 */
	void log(HttpServletRequest request, HttpServletResponse response, Object handler);

	/**
	 * 记录请求开始日志
	 *
	 * @param request
	 * @param response
	 * @param handler
	 */
	void startLog(HttpServletRequest request, HttpServletResponse response, Object handler);

	/**
	 * 记录请求结束日志
	 *
	 * @param request
	 * @param response
	 * @param handler
	 */
	void endLog(HttpServletRequest request, HttpServletResponse response, Object handler);
}
