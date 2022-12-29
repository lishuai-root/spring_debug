package com.shuai.property;

import org.springframework.beans.factory.annotation.Value;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/27 12:18
 * @version: 1.0
 */

public class MyService {

	@Value("${service.name}")
	String name;

	@Value("${service.age}")
	int age;

	@Value("${service.address}")
	String address;

	@Override
	public String toString() {
		return "MyService{" +
				"name='" + name + '\'' +
				", age=" + age +
				", address='" + address + '\'' +
				'}';
	}
}
