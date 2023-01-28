package com.shuai.controller.xml;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/5 21:02
 * @version: 1.0
 */

public class XmlController implements Controller {

	@Override
	public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
		System.out.println("--------------------- start XmlController ---------------------");
		ModelAndView mv = new ModelAndView();
		mv.addObject("rep", "XmlController");
		mv.setStatus(HttpStatus.OK);
		System.out.println("--------------------- end XmlController ---------------------");
		return null;
	}
}
