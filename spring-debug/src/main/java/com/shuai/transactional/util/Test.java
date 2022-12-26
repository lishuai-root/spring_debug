package com.shuai.transactional.util;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/23 21:43
 * @version: 1.0
 */

public class Test {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring_test.xml");
		System.out.println(context.getBean(A.class));
		System.out.println(context.getBean(C.class));
	}
}
