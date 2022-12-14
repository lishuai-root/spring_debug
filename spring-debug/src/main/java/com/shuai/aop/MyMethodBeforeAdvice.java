package com.shuai.aop;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/8 12:37
 * @version: 1.0
 */

@Component
public class MyMethodBeforeAdvice implements MethodBeforeAdvice {
	@Override
	public void before(Method method, Object[] args, Object target) throws Throwable {
		System.out.println("MyMethodBeforeAdvice.");
	}
}
