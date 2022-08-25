package com.shuai.beans;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/8/16 20:49
 * @version: 1.0
 */

@Component
public class MyFactoryBean implements FactoryBean<MyFactoryBeanInstance> {

	@Override
	public MyFactoryBeanInstance getObject() throws Exception {
		MyFactoryBeanInstance myFactoryBeanInstance = new MyFactoryBeanInstance();
		myFactoryBeanInstance.setInstanceName("myFactoryBeanInstance");
		myFactoryBeanInstance.setInstanceAddress("shanghai myFactoryBeanInstance。");
		return myFactoryBeanInstance;
	}

	@Override
	public Class<?> getObjectType() {
		return MyFactoryBeanInstance.class;
	}

	@Override
	public boolean isSingleton() {
		return FactoryBean.super.isSingleton();
	}
}
