package com.shuai.interceptor;

import com.shuai.log.ServerLogger;
import com.shuai.log.impl.BeanNameUrlHandlerServerLog;
import com.shuai.log.impl.DefaultDoNothingServerLog;
import com.shuai.log.impl.HandlerMethodHandlerServerLog;
import com.shuai.log.impl.RouterFunctionMappingServerLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.mvc.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/30 19:57
 * @version: 1.0
 */

public class ServerLogInterceptor extends AbstractBaseHandlerInterceptor {

	private static final Map<Class<?>, ServerLogger> handlerServerLoggerHandlers;

	static {
		handlerServerLoggerHandlers = new HashMap<Class<?>, ServerLogger>();
		handlerServerLoggerHandlers.put(HandlerMethod.class, new HandlerMethodHandlerServerLog());
		handlerServerLoggerHandlers.put(Controller.class, new BeanNameUrlHandlerServerLog());
		handlerServerLoggerHandlers.put(HandlerFunction.class, new RouterFunctionMappingServerLog());
		handlerServerLoggerHandlers.put(DefaultDoNothingServerLog.class, new DefaultDoNothingServerLog());
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		ServerLogger serverLogger = getServerLogger(handler);
		serverLogger.startLog(request, response, handler);
		return super.preHandle(request, response, handler);
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
		ServerLogger serverLogger = getServerLogger(handler);
		serverLogger.endLog(request, response, handler);
		super.postHandle(request, response, handler, modelAndView);
	}

	private ServerLogger getServerLogger(Object handler){
		if (handlerServerLoggerHandlers.containsKey(handler.getClass())){
			return handlerServerLoggerHandlers.get(handler.getClass());
		}else if (handler instanceof Controller || handler instanceof HttpRequestHandler){
			return handlerServerLoggerHandlers.get(Controller.class);
		}else if (handler instanceof HandlerFunction){
			return handlerServerLoggerHandlers.get(HandlerFunction.class);
		}
		return handlerServerLoggerHandlers.get(DefaultDoNothingServerLog.class);
	}

	public void addHandlerServerLogger(Class<?> clazz, ServerLogger handlerServerLogger){
		handlerServerLoggerHandlers.put(clazz, handlerServerLogger);
	}
}
