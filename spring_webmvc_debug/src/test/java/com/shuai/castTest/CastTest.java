package com.shuai.castTest;

import org.springframework.core.Conventions;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/31 16:17
 * @version: 1.0
 */

public class CastTest {

	public static void main(String[] args) throws NoSuchMethodException {
		Method method = People.class.getDeclaredMethod("testName", new Class<?>[]{String.class, int.class});
		Parameter[] parameters = method.getParameters();
		for (int i = 0; i < parameters.length; i++) {
			MethodParameter methodParameter = new MethodParameter(method, i);
			String name = Conventions.getVariableNameForParameter(methodParameter);
			System.out.println(name);
		}

		System.out.println("--------------------------------");

		String[] parameterNames = new LocalVariableTableParameterNameDiscoverer().getParameterNames(method);
		for (String name : parameterNames) {
			System.out.println(name);
		}
	}
}
