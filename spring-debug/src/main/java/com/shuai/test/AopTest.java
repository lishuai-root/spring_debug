package com.shuai.test;

import com.shuai.aop.MyBean;
import com.shuai.aop.MySchedulingConfigurer;
import com.shuai.aop.NoProxyClass;
import com.shuai.aop.SourceClass;
import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/8/30 22:27
 * @version: 1.0
 */

public class AopTest {
	public static void main(String[] args)  {
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "target/cglib");
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("aop.xml");
		SourceClass bean = context.getBean(SourceClass.class);

//		int add = bean.add(2, 5);
//		System.out.println(add);
//		bean.show();
//		System.out.println(bean);
		NoProxyClass bean1 = context.getBean(NoProxyClass.class);
		MyBean bean2 = context.getBean(MyBean.class);
		Object myBean = context.getBean("proxyClass");
		System.out.println("bean2 : " + bean2);

		System.out.println(bean2);
		System.out.println(bean1.toString());
		int chu = bean.chu(2, 2);
		System.out.println(chu);
		System.out.println(bean);
//		AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(MyConfiguration.class);
		System.out.println("-------------------");
		List<Object> objectList = MySchedulingConfigurer.objectList;
		for (int i = 0; i < objectList.size(); i++) {
			for (int j = i + 1; j < objectList.size(); j++) {
				System.out.println(objectList.get(i) == objectList.get(j));
			}
		}
	}
}
