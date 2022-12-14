package com.shuai.beans;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/8/28 23:34
 * @version: 1.0
 */

@Component
public class MyScopeConfigure implements BeanFactoryPostProcessor {
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		beanFactory.registerScope("myScope", new Scope() {
			@Override
			public Object get(String name, ObjectFactory<?> objectFactory) {
				return objectFactory.getObject();
			}

			@Override
			public Object remove(String name) {
				return beanFactory.getBean(name);
			}

			@Override
			public void registerDestructionCallback(String name, Runnable callback) {

			}

			@Override
			public Object resolveContextualObject(String key) {
				return beanFactory.getBean(key);
			}

			@Override
			public String getConversationId() {
				return null;
			}
		});
	}
}
