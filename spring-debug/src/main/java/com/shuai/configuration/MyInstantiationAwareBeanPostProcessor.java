package com.shuai.configuration;

import com.shuai.beans.BigPeople;
import com.shuai.beans.People;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;

import java.util.Random;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/7/24 23:55
 * @version: 1.0
 */

public class MyInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

	BigPeople bigPeople;

	public BigPeople getBigPeople() {
		return bigPeople;
	}

	public void setBigPeople(BigPeople bigPeople) {
		this.bigPeople = bigPeople;
	}

	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		System.out.println("postProcessBeforeInstantiation execute : " + beanName);
		System.out.println("bigPeople address : " + bigPeople.getNameStr());
		int i = new Random().nextInt(10);
		if ("people".equals(beanName) && i > 5){
			People people = new People();
			people.setName(System.currentTimeMillis() + "");
			return people;
		}
		return InstantiationAwareBeanPostProcessor.super.postProcessBeforeInstantiation(beanClass, beanName);
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		System.out.println("postProcessAfterInstantiation execute : " + beanName + " : " + bean);
		return InstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
		System.out.println("postProcessProperties execute : " + beanName);
		return InstantiationAwareBeanPostProcessor.super.postProcessProperties(pvs, bean, beanName);
	}
}
