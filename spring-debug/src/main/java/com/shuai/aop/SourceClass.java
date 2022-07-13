package com.shuai.aop;

import com.shuai.beans.People;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/7/3 22:35
 * @version: 1.0
 */

public class SourceClass {

	@Autowired
	People people;

	public void before(){
		System.out.println("source before!");
	}

	public void after(){
		System.out.println("source after");
	}

	public People getPeople() {
		return people;
	}

	public void setPeople(People people) {
		this.people = people;
	}
}
