package com.shuai.controller.xml;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.HttpRequestHandler;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/5 21:07
 * @version: 1.0
 */

public class XmlHandler implements HttpRequestHandler {
	@Override
	public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		System.out.println("--------------------- start XmlHandler ---------------------");
		PrintWriter writer = response.getWriter();
		writer.write("XmlHandler");
		writer.flush();
		writer.close();
		System.out.println("--------------------- end XmlHandler ---------------------");
	}
}
