package com.shuai.controller.routerFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/30 23:30
 * @version: 1.0
 */

@Configuration
public class RouterFunctionController {

	@Autowired
	RouterFunctionHandler handler;

	@Autowired
	DefaultRouterFunctionHandler defaultHandler;

	@Bean
	public RouterFunction<ServerResponse> routerFunction1() {
		return RouterFunctions.route(RequestPredicates.GET("/hello2"),
				request -> ServerResponse.ok().body("Hello World function1!"));
	}

	@Bean
	public RouterFunction<ServerResponse> routerFunction2() {
		return RouterFunctions.route().GET("/function2", RequestPredicates.accept(MediaType.APPLICATION_JSON), request -> {
			return ServerResponse.status(HttpStatus.OK).body("Hello World routerFunction2!");
		}).build() ;
	}

	@Bean
	public RouterFunction<ServerResponse> routerFunction3() {
		return RouterFunctions.route().GET("/function3", RequestPredicates.accept(MediaType.APPLICATION_JSON), handler).build() ;
	}

	@Bean
	public RouterFunction<ServerResponse> routerFunction4() {
		return RouterFunctions.route().GET("/function4", RequestPredicates.accept(MediaType.APPLICATION_JSON), defaultHandler::function4Handle).build() ;
	}

	@Bean
	public RouterFunction<ServerResponse> routerFunction5() {
		return RouterFunctions.route().POST("/function5", RequestPredicates.accept(MediaType.APPLICATION_JSON), defaultHandler::function5Handle).build();
	}

	@Bean
	public RouterFunction<ServerResponse> routerFunction6() {
		return RouterFunctions.route().POST("/function6", RequestPredicates.accept(MediaType.APPLICATION_JSON), defaultHandler::function6Handle).build();
	}

	@Bean
	public RouterFunction<ServerResponse> routerFunction7() {
		return RouterFunctions.route().POST("/function7", RequestPredicates.accept(MediaType.APPLICATION_JSON), defaultHandler::function7Handle).build();
	}
}
