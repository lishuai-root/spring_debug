/*
 * Copyright 2002-2020 the original author or authors.
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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.Assert;
import org.springframework.web.servlet.HandlerInterceptor;
import org.w3c.dom.Element;

import java.io.IOException;

/**
 * 在解析<mvc:annotation-driven></mvc:annotation-driven>标签时以beanDefinition的方式添加到工厂中
 * *** 添加时添加的是{@link MappedInterceptor}beanDefinition，当前类是通过构造函数的方式传入，被{@link MappedInterceptor#interceptor}引用
 * {@link org.springframework.web.servlet.config.AnnotationDrivenBeanDefinitionParser#parse(Element, ParserContext)}
 *
 * Interceptor that places the configured {@link ConversionService} in request scope
 * so it's available during request processing. The request attribute name is
 * "org.springframework.core.convert.ConversionService", the value of
 * {@code ConversionService.class.getName()}.
 * 拦截器，将配置的{@link ConversionService}放置在请求范围内，以便在请求处理期间可用。
 * 请求属性名是“org.springframework.core.convert.ConversionService”，{@code ConversionService.class.getName()}的值。
 *
 *
 * <p>Mainly for use within JSP tags such as the spring:eval tag.
 * 主要用于JSP标记中，例如spring:eval标记。
 *
 * @author Keith Donald
 * @since 3.0.1
 */
public class ConversionServiceExposingInterceptor implements HandlerInterceptor {

	private final ConversionService conversionService;


	/**
	 * Creates a new {@link ConversionServiceExposingInterceptor}.
	 * @param conversionService the conversion service to export to request scope when this interceptor is invoked
	 */
	public ConversionServiceExposingInterceptor(ConversionService conversionService) {
		Assert.notNull(conversionService, "The ConversionService may not be null");
		this.conversionService = conversionService;
	}


	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws ServletException, IOException {

		request.setAttribute(ConversionService.class.getName(), this.conversionService);
		return true;
	}

}
