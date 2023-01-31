package com.shuai.controller.routerFunction;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/30 23:33
 * @version: 1.0
 */

@Service("handler")
public class RouterFunctionHandler implements HandlerFunction<ServerResponse> {


	@Override
	public ServerResponse handle(ServerRequest request) throws Exception {
		return ServerResponse.status(HttpStatus.OK).body("Hello World routerFunction3!");
	}
}
