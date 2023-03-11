/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import jakarta.servlet.ServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * Resolves servlet backed response-related method arguments. Supports values of the
 * following types:
 * 解析servlet支持的响应相关方法参数。支持以下类型的值:
 * <ul>
 * <li>{@link ServletResponse}
 * <li>{@link OutputStream}
 * <li>{@link Writer}
 * </ul>
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ServletResponseMethodArgumentResolver implements HandlerMethodArgumentResolver {

	/**
	 * 解析{@link ServletResponse}, {@link OutputStream}, {@link Writer}类型的方法参数
	 *
	 * @param parameter the method parameter to check
	 * @return
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> paramType = parameter.getParameterType();
		return (ServletResponse.class.isAssignableFrom(paramType) ||
				OutputStream.class.isAssignableFrom(paramType) ||
				Writer.class.isAssignableFrom(paramType));
	}

	/**
	 * 通过web上下文获取响应对象，或者响应对象的输出流
	 *
	 * Set {@link ModelAndViewContainer#setRequestHandled(boolean)} to
	 * {@code false} to indicate that the method signature provides access
	 * to the response. If subsequently the underlying method returns
	 * {@code null}, the request is considered directly handled.
	 *
	 * 将{@link ModelAndViewContainer#setRequestHandled(boolean)} 设置为{@code false}，以指示方法签名提供对响应的访问。
	 * 如果随后底层方法返回{@code null}，则认为该请求被直接处理。
	 *
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		if (mavContainer != null) {
			mavContainer.setRequestHandled(true);
		}

		Class<?> paramType = parameter.getParameterType();

		// ServletResponse, HttpServletResponse
		/**
		 * 获取当前请求的响应
		 */
		if (ServletResponse.class.isAssignableFrom(paramType)) {
			return resolveNativeResponse(webRequest, paramType);
		}

		// ServletResponse required for all further argument types
		/**
		 * 所有其他参数类型都需要ServletResponse
		 */
		return resolveArgument(paramType, resolveNativeResponse(webRequest, ServletResponse.class));
	}

	private <T> T resolveNativeResponse(NativeWebRequest webRequest, Class<T> requiredType) {
		T nativeResponse = webRequest.getNativeResponse(requiredType);
		if (nativeResponse == null) {
			throw new IllegalStateException(
					"Current response is not of type [" + requiredType.getName() + "]: " + webRequest);
		}
		return nativeResponse;
	}

	/**
	 * 返回响应体的输出流
	 *
	 * @param paramType
	 * @param response
	 * @return
	 * @throws IOException
	 */
	private Object resolveArgument(Class<?> paramType, ServletResponse response) throws IOException {
		if (OutputStream.class.isAssignableFrom(paramType)) {
			return response.getOutputStream();
		}
		else if (Writer.class.isAssignableFrom(paramType)) {
			return response.getWriter();
		}

		// Should never happen... 不应该发生……
		throw new UnsupportedOperationException("Unknown parameter type: " + paramType);
	}

}
