package com.shuai.controller.annotation;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/3/2 22:08
 * @version: 1.0
 */

@RestController
@RequestMapping("/body")
@CrossOrigin
public class BodyAsyncController {

	@GetMapping("/async")
	public WebAsyncTask<String> asyncTask(){
		System.out.println("asyncTask : " + System.currentTimeMillis());
		return new WebAsyncTask<String>(new Callable<String>() {
			@Override
			public String call() throws Exception {
				Thread.sleep(10000);
				return "asyncTask executed successfully!";
			}
		});
	}

	@GetMapping("/asyncDef")
	public WebAsyncTask<String> asyncTaskDef(HttpServletResponse response) throws IOException {
		System.out.println("asyncTask : " + System.currentTimeMillis());
//		response.getWriter().write("asyncTask started, please wait...");
		return new WebAsyncTask<String>(new Callable<String>() {
			@Override
			public String call() throws Exception {
				Thread.sleep(10000);
				return "asyncTaskDef executed successfully!";
			}
		});
	}
}
