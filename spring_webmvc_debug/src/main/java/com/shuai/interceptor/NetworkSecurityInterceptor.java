package com.shuai.interceptor;

import com.shuai.util.NetworkUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/3 1:41
 * @version: 1.0
 */

public class NetworkSecurityInterceptor extends AbstractBaseHandlerInterceptor {
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//		System.out.println("NetworkSecurityInterceptor preHandle. handler : " + handler.getClass());
		NetworkUtil.getRequestIp(request);
		return super.preHandle(request, response, handler);
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
//		System.out.println("NetworkSecurityInterceptor postHandle. handler : " + handler.getClass());
		super.postHandle(request, response, handler, modelAndView);
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//		System.out.println("NetworkSecurityInterceptor afterCompletion. handler : " + handler.getClass());
		super.afterCompletion(request, response, handler, ex);
	}
}
