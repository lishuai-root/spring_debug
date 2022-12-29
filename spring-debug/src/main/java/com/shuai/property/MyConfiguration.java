package com.shuai.property;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/27 12:21
 * @version: 1.0
 */

@Configuration
@PropertySource({"classpath:${user.name}.properties"})
public class MyConfiguration {

	@Bean
	public MyService myService(){
		return new MyService();
	}
}
