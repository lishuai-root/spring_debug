package com.shuai.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/9/3 11:22
 * @version: 1.0
 */

public class JdkProxyImp {

	public static Object getProxy(Class<?> clazz){
		InvocationHandler handler = new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				return null;
			}
		};
		return Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, handler);
	}

	public static void main(String[] args) {
		Object proxy = getProxy(JdkProxy.class);
	}
}
