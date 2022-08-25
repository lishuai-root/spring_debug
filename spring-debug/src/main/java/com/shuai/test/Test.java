package com.shuai.test;


import com.shuai.aop.ProxyClass;
import com.shuai.aop.SourceClass;
import com.shuai.beans.People;
import com.shuai.configuration.AutoDemo;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.PriorityQueue;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2021/12/4 20:31
 * @version: 1.0
 */

public class Test {

	public static void main(String[] args) {

		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring.xml");

		People bean = context.getBean(People.class);
		System.out.println(bean.getDriverCar());
		bean.getDriverCar().driver();
		System.out.println(bean.getAddress().getName());
//		People people = context.getBean(People.class);
//		System.out.println(people.getName());
//		SourceClass bean = context.getBean(SourceClass.class);
//		System.out.println(bean.getPeople().getPassword());
//		AutoDemo autoDemo = context.getBean(AutoDemo.class);
//		System.out.println(autoDemo.people);
//		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "java/cglib");
//		System.setProperty("net.sf.cglib.core.DebuggingClassWriter.traceEnabled", "true");
//		System.out.println(people.getName());
//		System.out.println(people.getEmail());
//		System.out.println(people.getPassword());

//		MyCompont bean = context.getBean(MyCompont.class);
//		System.out.println(bean);

//		SourceClass bean = context.getBean(SourceClass.class);
//		System.out.println("SourceClass : " + bean);
//		bean.before();
//		bean.after();
	}
}
