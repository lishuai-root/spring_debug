package com.shuai.configuration;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.text.DecimalFormat;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/7/8 22:38
 * @version: 1.0
 */

@Configuration
public class MyConfiguration {

	@Bean(name = {"demo", "demomo"})
	public Demo getDem(){
		return new Demo();
	}

	@Bean(name = {"demo", "demomo"},autowire = Autowire.BY_NAME)
	public Demo getDem(String va){
		return new Demo();
	}
}
