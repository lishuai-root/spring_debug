package com.shuai.controller.routerFunction;

import com.alibaba.fastjson.JSON;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/31 20:36
 * @version: 1.0
 */

@Service("defaultHandler")
public class DefaultRouterFunctionHandler {

	public ServerResponse function4Handle(ServerRequest request) throws Exception {
		return ServerResponse.status(HttpStatus.OK).body("Hello World routerFunction4!");
	}

	public ServerResponse function5Handle(ServerRequest request) throws Exception {
		return ServerResponse.status(HttpStatus.OK).body("Hello World routerFunction5!");
	}

	public ServerResponse function6Handle(ServerRequest request) throws Exception {
		Map<String, String> map = new HashMap<>();
		map.put("name", "lishuai");
		return ServerResponse.status(HttpStatus.OK).body(map);
	}

	public ServerResponse function7Handle(ServerRequest request) throws Exception {
		Map<String, String> map = new HashMap<>();
		map.put("name", "lishuai");
		return ServerResponse.status(HttpStatus.OK).body(JSON.toJSONString(map));
	}
}
