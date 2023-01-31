package com.shuai.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/30 20:02
 * @version: 1.0
 */

public abstract class AbstractServerLogger implements ServerLogger{

	private static final Log logger = LogFactory.getLog(AbstractServerLogger.class);

	public static final String START_SERVER = "startServer";
	public static final String END_SERVER = "endServer";
	public static final String IDENTIFICATION_CODE ="identificationCode";

	public void log(String message){
		logger.debug(message);
		System.out.println(message);
	}

	@Override
	public void log(HttpServletRequest request, HttpServletResponse response, Object handler) {
		log("request from : " + handler.getClass());
	}

	@Override
	public void startLog(HttpServletRequest request, HttpServletResponse response, Object handler){
		request.setAttribute(START_SERVER, System.currentTimeMillis());
		setIdentificationCode(request);
	}

	@Override
	public void endLog(HttpServletRequest request, HttpServletResponse response, Object handler){
		request.setAttribute(END_SERVER, System.currentTimeMillis());
	}

	public String getStartLog(HttpServletRequest request, String message){
		return "request [" + request.getAttribute(IDENTIFICATION_CODE) + "] from " + message + " start with : " + request.getAttribute(START_SERVER);
	}

	public String getEndLog(HttpServletRequest request, String message){
		Long startTime =(Long) request.getAttribute(START_SERVER);
		Long endTime =(Long) request.getAttribute(END_SERVER);
		return getStartLog(request, message) + " end with : " + endTime + " time consuming : " + (endTime - startTime);
	}

	public void setIdentificationCode(HttpServletRequest request){
		request.setAttribute(IDENTIFICATION_CODE, UUID.randomUUID());
	}

	public String getMethodDescriptor(Method method){
		if (method == null){
			return "unknown method";
		}
		return getMethodDescriptor(method, method.getParameterTypes());
	}

	public String getMethodDescriptor(Method method, Class<?>[] paramTypes){
		if (method == null){
			return "unknown method";
		}
		String name = method.getName();
		Class<?> returnType = method.getReturnType();
		StringBuilder sbr = new StringBuilder();
		sbr.append(name)
				.append("<[");
		if (!ObjectUtils.isEmpty(paramTypes)){
			for (Class<?> cl : paramTypes){
				sbr.append(cl.getName())
						.append(";");
			}
			sbr.deleteCharAt(sbr.length() - 1);
		}
		sbr.append("]")
				.append(returnType.getName())
				.append(">");
		return sbr.toString();
	}
}
