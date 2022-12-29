package com.shuai.config.initializer;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/29 16:27
 * @version: 1.0
 */

public class MyMvcContextApplicationContextInitializer implements ApplicationContextInitializer {
	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		System.out.println("myMvcContextApplicationContextInitializer : " + applicationContext.getId());
	}
}
