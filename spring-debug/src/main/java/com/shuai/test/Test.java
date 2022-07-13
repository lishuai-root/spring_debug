package com.shuai.test;


import com.shuai.aop.ProxyClass;
import com.shuai.aop.SourceClass;
import com.shuai.beans.People;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2021/12/4 20:31
 * @version: 1.0
 */

public class Test {

	public static void main(String[] args) {

		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring.xml");
		
		People people = context.getBean(People.class);
		SourceClass bean = context.getBean(SourceClass.class);
		System.out.println(bean.getPeople().getPassword());
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
