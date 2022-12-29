package com.shuai.property.test;

import com.shuai.property.MyConfiguration;
import com.shuai.property.MyService;
import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/27 12:22
 * @version: 1.0
 */

public class MyServiceTest {

	public static void main(String[] args) {
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "target/cglib");
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);
		MyService bean = context.getBean(MyService.class);
		System.out.println(bean);
		System.out.println(System.getProperty("user.name"));
	}
}
