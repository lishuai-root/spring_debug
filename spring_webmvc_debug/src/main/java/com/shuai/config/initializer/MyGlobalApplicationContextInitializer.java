package com.shuai.config.initializer;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/28 17:59
 * @version: 1.0
 */

public class MyGlobalApplicationContextInitializer implements ApplicationContextInitializer {
	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		System.out.println("myGlobalApplicationContextInitializer : " + applicationContext.getId());
	}
}
