/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Conventions;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Utilities for identifying {@link Configuration} classes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 */
abstract class ConfigurationClassUtils {

	public static final String CONFIGURATION_CLASS_FULL = "full";

	public static final String CONFIGURATION_CLASS_LITE = "lite";

	public static final String CONFIGURATION_CLASS_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "configurationClass");

	private static final String ORDER_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "order");


	private static final Log logger = LogFactory.getLog(ConfigurationClassUtils.class);

	private static final Set<String> candidateIndicators = new HashSet<>(8);

	static {
		candidateIndicators.add(Component.class.getName());
		candidateIndicators.add(ComponentScan.class.getName());
		candidateIndicators.add(Import.class.getName());
		candidateIndicators.add(ImportResource.class.getName());
	}


	/**
	 * Check whether the given bean definition is a candidate for a configuration class
	 * (or a nested component class declared within a configuration/component class,
	 * to be auto-registered as well), and mark it accordingly.
	 * 检查给定的bean定义是否为配置类(或在配置组件类中声明的嵌套组件类，也要自动注册)的候选者，并相应地标记它。
	 *
	 * 检查当前beanDefinition是否一个被注解标注的配置类,包含了一下注解可以认为是配置类：
	 * 1.@Configuration且使用了代理模式
	 * 2.@Configuration(没有使用代理模式), @Component, @ComponentScan, @Import, @ImportResource, @Bean
	 *
	 * 对于是否配置类的判断@Configuration优先级高于其他
	 * 如果被@Configuration注解标注且使用了代理模式则beanDefinition中的
	 * "org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass"属性会被设置为"full"
	 *
	 * 如果被@Configuration(没有使用代理模式), @Component, @ComponentScan, @Import, @ImportResource, @Bean注解并标注则beanDefinition中的
	 * "org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass"属性会被设置为"lite"
	 *
	 * @param beanDef the bean definition to check
	 * @param metadataReaderFactory the current factory in use by the caller
	 * @return whether the candidate qualifies as (any kind of) configuration class
	 */
	public static boolean checkConfigurationClassCandidate(
			BeanDefinition beanDef, MetadataReaderFactory metadataReaderFactory) {

		String className = beanDef.getBeanClassName();
		if (className == null || beanDef.getFactoryMethodName() != null) {
			return false;
		}

		AnnotationMetadata metadata;
		/**
		 * 通过xml配置文件注入的bean定义信息都是GenericBeanDefinition,实现了AbstractBeanDefinition
		 * spring内部的bean都是rootBeanDefinition,实现了AbstractBeanDefinition
		 * 通过component-scan扫描到的bean都是ScannedGenericBeanDefinition,实现了AnnotatedBeanDefinition,继承了GenericBeanDefinition
		 * 所有通过注解注入的beanDefinition都是属于AnnotatedBeanDefinition接口
		 *
		 * 此处主要是判断beanDefinition是否属于AnnotatedBeanDefinition
		 */
		if (beanDef instanceof AnnotatedBeanDefinition &&
				className.equals(((AnnotatedBeanDefinition) beanDef).getMetadata().getClassName())) {
			// Can reuse the pre-parsed metadata from the given BeanDefinition...
			// 获取bean的元数据信息，如果当前bean是ScannedGenericBeanDefinition,那么元数据信息是spring从class文件中解析到的
			metadata = ((AnnotatedBeanDefinition) beanDef).getMetadata();
		}
		/**
		 * 检查是否spring默认的beanDefinition
		 * AbstractBeanDefinition实现了BeanDefinition
		 * AnnotatedBeanDefinition继承了BeanDefinition
		 * AnnotatedBeanDefinition的子类有ScannedGenericBeanDefinition,AnnotatedGenericBeanDefinition,ConfigurationClassBeanDefinition
		 * spring中默认除了AnnotatedBeanDefinition及其子类其他都是AbstractBeanDefinition
		 */
		else if (beanDef instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) beanDef).hasBeanClass()) {
			// Check already loaded Class if present...
			// since we possibly can't even load the class file for this Class.
			// 获取bean对象的Class
			Class<?> beanClass = ((AbstractBeanDefinition) beanDef).getBeanClass();
			/**
			 * 如果class实例是下面四种类或接口的子类、父接口等任何一种情况，直接返回
			 * BeanFactoryPostProcessor:beanFactory后置处理器
			 * BeanPostProcessor:beanDefinition后置处理器
			 * AopInfrastructureBean:aop
			 * EventListenerFactory:事件监听
			 */
			if (BeanFactoryPostProcessor.class.isAssignableFrom(beanClass) ||
					BeanPostProcessor.class.isAssignableFrom(beanClass) ||
					AopInfrastructureBean.class.isAssignableFrom(beanClass) ||
					EventListenerFactory.class.isAssignableFrom(beanClass)) {
				return false;
			}
			// 为给定类创建新的AnnotationMetadata元数据实例
			metadata = AnnotationMetadata.introspect(beanClass);
		}
		// 如果以上两种都不是
		else {
			try {
				/**
				 * 获取className对应的元数据读取器
				 * className:类的完全限定名
				 * MetadataReader是在解析component-scan扫描时通过class文件创建的元数据对象
				 */
				MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(className);
				// 获取类完整的注解元数据，包括带注解的方法元数据
				metadata = metadataReader.getAnnotationMetadata();
			}
			catch (IOException ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Could not find class file for introspecting configuration annotations: " +
							className, ex);
				}
				return false;
			}
		}

		// 获取bean定义中被@Configuration注解标注的属性集合(属性集合是@Configuration的属性)
		Map<String, Object> config = metadata.getAnnotationAttributes(Configuration.class.getName());

		/**
		 * 如果bean被@Configuration注解标注，且属性proxyBeanMethods为true(使用代理模式)，则将bean定义记为full
		 * {@link Configuration}中{@link Configuration#proxyBeanMethods()}默认为true，因此如果没有明确指定proxyBeanMethods属性时，
		 * 这里总是将配置类设置为需要代理
		 */
		if (config != null && !Boolean.FALSE.equals(config.get("proxyBeanMethods"))) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_FULL);
		}
		// 如果bean被@configuration注解标注，或者被注解@Component，@ComponentScan、@Import、@ImportResource或者@Bean标记的方法，则将bean定义标记为lite
		else if (config != null || isConfigurationCandidate(metadata)) {
			beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_LITE);
		}
		else {
			return false;
		}

		// It's a full or lite configuration candidate... Let's determine the order value, if any.
		/**
		 * bean定义信息是一个完整或精简的配置候选…让我们确定顺序值，如果有的话。
		 * 走到这里说明当前bean被认为是一个配置类，如果bean有指定顺序，则为beanDefinition设置order值
		 */
		Integer order = getOrder(metadata);
		// 如果order值不为空的话，那么直接设置值到具体的beanDefinition
		if (order != null) {
			beanDef.setAttribute(ORDER_ATTRIBUTE, order);
		}

		return true;
	}

	/**
	 * 检查beanDefinition是否配置类候选者
	 * 如果bean被一下注解标注，则认为是配置类的候选者：
	 *  @Component, @ComponentScan, @Import, @ImportResource, @Bean
	 *
	 * 配置类候选者：虽然不是一个标准的被@Configuration注解标注的配置类，
	 * 	但是被有可能引起其他类加载或者产生新的beanDefinition的注解标注的beanDefinition
	 *
	 * Check the given metadata for a configuration class candidate
	 * (or nested component class declared within a configuration/component class).
	 * @param metadata the metadata of the annotated class
	 * @return {@code true} if the given class is to be registered for
	 * configuration class processing; {@code false} otherwise
	 */
	public static boolean isConfigurationCandidate(AnnotationMetadata metadata) {
		// Do not consider an interface or an annotation...
		// 不要考虑接口或注释……
		if (metadata.isInterface()) {
			return false;
		}

		// Any of the typical annotations found?
		// 检查是否被注解@Component、@ComponentScan、@Import、@ImportResource标注
		for (String indicator : candidateIndicators) {
			if (metadata.isAnnotated(indicator)) {
				return true;
			}
		}

		// Finally, let's look for @Bean methods...
		// 最后检查是否有@Bean标注的方法
		return hasBeanMethods(metadata);
	}

	/**
	 * 检查beanDefinition是否被@Bean注解标注
	 *
	 * @param metadata
	 * @return
	 */
	static boolean hasBeanMethods(AnnotationMetadata metadata) {
		try {
			return metadata.hasAnnotatedMethods(Bean.class.getName());
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to introspect @Bean methods on class [" + metadata.getClassName() + "]: " + ex);
			}
			return false;
		}
	}

	/**
	 * Determine the order for the given configuration class metadata.
	 * @param metadata the metadata of the annotated class
	 * @return the {@code @Order} annotation value on the configuration class,
	 * or {@code Ordered.LOWEST_PRECEDENCE} if none declared
	 * @since 5.0
	 */
	@Nullable
	public static Integer getOrder(AnnotationMetadata metadata) {
		Map<String, Object> orderAttributes = metadata.getAnnotationAttributes(Order.class.getName());
		return (orderAttributes != null ? ((Integer) orderAttributes.get(AnnotationUtils.VALUE)) : null);
	}

	/**
	 * Determine the order for the given configuration class bean definition,
	 * as set by {@link #checkConfigurationClassCandidate}.
	 * @param beanDef the bean definition to check
	 * @return the {@link Order @Order} annotation value on the configuration class,
	 * or {@link Ordered#LOWEST_PRECEDENCE} if none declared
	 * @since 4.2
	 */
	public static int getOrder(BeanDefinition beanDef) {
		Integer order = (Integer) beanDef.getAttribute(ORDER_ATTRIBUTE);
		return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
	}

}
