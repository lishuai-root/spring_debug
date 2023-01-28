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

package org.springframework.web.servlet.handler;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.util.ObjectUtils;

/**
 * Abstract implementation of the {@link org.springframework.web.servlet.HandlerMapping}
 * interface, detecting URL mappings for handler beans through introspection of all
 * defined beans in the application context.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see #determineUrlsForHandler
 */
public abstract class AbstractDetectingUrlHandlerMapping extends AbstractUrlHandlerMapping {

	private boolean detectHandlersInAncestorContexts = false;


	/**
	 * Set whether to detect handler beans in ancestor ApplicationContexts.
	 * <p>Default is "false": Only handler beans in the current ApplicationContext
	 * will be detected, i.e. only in the context that this HandlerMapping itself
	 * is defined in (typically the current DispatcherServlet's context).
	 * <p>Switch this flag on to detect handler beans in ancestor contexts
	 * (typically the Spring root WebApplicationContext) as well.
	 */
	public void setDetectHandlersInAncestorContexts(boolean detectHandlersInAncestorContexts) {
		this.detectHandlersInAncestorContexts = detectHandlersInAncestorContexts;
	}


	/**
	 * Calls the {@link #detectHandlers()} method in addition to the
	 * superclass's initialization.
	 * 除了父类的初始化外，还调用{@link #detectHandlers()}方法。
	 */
	@Override
	public void initApplicationContext() throws ApplicationContextException {
		/**
		 * 查找上下文中所有的映射拦截器，继承了{@link MappedInterceptor}的bean实例
		 */
		super.initApplicationContext();
		/**
		 * 查找上下文中所有名称或者别名中包含了以"/"开头的名称的bean，并注册请求路径和处理程序的映射关系
		 */
		detectHandlers();
	}

	/**
	 * 查找上下文中所有名称或者别名中包含了以"/"开头的名称的bean，并注册请求路径和处理程序的映射关系
	 *
	 * Register all handlers found in the current ApplicationContext.
	 * 注册当前ApplicationContext中找到的所有处理程序。
	 *
	 * <p>The actual URL determination for a handler is up to the concrete
	 * {@link #determineUrlsForHandler(String)} implementation. A bean for
	 * which no such URLs could be determined is simply not considered a handler.
	 * 处理程序的实际URL确定取决于具体的{@link #determineUrlsForHandler(String)}实现。无法确定此类url的bean不能被视为处理程序。
	 *
	 * @throws org.springframework.beans.BeansException if the handler couldn't be registered
	 * @see #determineUrlsForHandler(String)
	 */
	protected void detectHandlers() throws BeansException {
		ApplicationContext applicationContext = obtainApplicationContext();
		/**
		 * 获取上下文中所有的bean名称
		 */
		String[] beanNames = (this.detectHandlersInAncestorContexts ?
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(applicationContext, Object.class) :
				applicationContext.getBeanNamesForType(Object.class));

		// Take any bean name that we can determine URLs for.
		/**
		 * 取我们可以确定其url的任何bean名。检查bean名称是否以"/"开头
		 */
		for (String beanName : beanNames) {
			/**
			 * 获取当前bean名称及其别名中所有以"/"开头的名称
			 * 如果存在以"/"开头的名称，则认为当前bean是请求处理程序
			 */
			String[] urls = determineUrlsForHandler(beanName);
			if (!ObjectUtils.isEmpty(urls)) {
				// URL paths found: Let's consider it a handler.
				/**
				 * 找到的URL路径:让我们把它看作一个处理程序。
				 */
				registerHandler(urls, beanName);
			}
		}

		if (mappingsLogger.isDebugEnabled()) {
			mappingsLogger.debug(formatMappingName() + " " + getHandlerMap());
		}
		else if ((logger.isDebugEnabled() && !getHandlerMap().isEmpty()) || logger.isTraceEnabled()) {
			logger.debug("Detected " + getHandlerMap().size() + " mappings in " + formatMappingName());
		}
	}


	/**
	 * Determine the URLs for the given handler bean.
	 * @param beanName the name of the candidate bean
	 * @return the URLs determined for the bean, or an empty array if none
	 */
	protected abstract String[] determineUrlsForHandler(String beanName);

}
