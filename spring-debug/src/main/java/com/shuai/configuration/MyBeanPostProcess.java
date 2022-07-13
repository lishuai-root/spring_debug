package com.shuai.configuration;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/7/10 17:17
 * @version: 1.0
 */

public class MyBeanPostProcess implements BeanPostProcessor {
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("postProcessBeforeInitialization execute : " + beanName);
		return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("postProcessAfterInitialization execute : " + beanName);
		return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
	}
}
