package com.shuai.beans;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/7/24 22:17
 * @version: 1.0
 */

@Service
public class Apple {
	@Autowired
	Address address;
	public void showInfo(){
		System.out.println("apple!");
	}

	@PostConstruct
	public void initMethod(){
		System.out.println("apple init method!");
	}

	@PreDestroy
	public void destroyMethod(){
		System.out.println("apple destroy method!");
	}
}
