package com.shuai.aop;

import com.shuai.beans.People;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/7/3 22:35
 * @version: 1.0
 */

public class ProxyClass {

	public void before(){
		System.out.println("proxy before!");
	}

	public void after(){
		System.out.println("proxy after!");
	}
}
