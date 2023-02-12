package com.shuai.controller.annotation;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/2/5 20:00
 * @version: 1.0
 */

@Controller
@RequestMapping(value = "/view")
@CrossOrigin
public class ViewAnnotationController {

	@GetMapping(value = "viewTest1")
	public String viewTest1(){
		System.out.println("viewTest1!");
		return "testView";
	}
}
