package com.shuai.aop;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/15 20:30
 * @version: 1.0
 */

public class TestCglibProxyImp implements TestCglibProxy{
	@Override
	public void testInterfaceMethod() {
		System.out.println("TestCglibProxyImp");
	}
}
