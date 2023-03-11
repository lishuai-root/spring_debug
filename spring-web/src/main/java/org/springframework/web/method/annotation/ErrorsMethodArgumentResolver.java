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

package org.springframework.web.method.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link Errors} method arguments.
 * 解决{@link Errors}方法参数。
 *
 * <p>An {@code Errors} method argument is expected to appear immediately after
 * the model attribute in the method signature. It is resolved by expecting the
 * last two attributes added to the model to be the model attribute and its
 * {@link BindingResult}.
 *
 * {@code Errors}方法参数应该立即出现在方法签名中的model属性之后。
 * 它通过期望添加到模型中的最后两个属性是模型属性及其{@link BindingResult}来解决。
 *
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ErrorsMethodArgumentResolver implements HandlerMethodArgumentResolver {

	/**
	 * 处理类型为{@link Errors}的方法参数
	 *
	 * @param parameter the method parameter to check
	 * @return
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> paramType = parameter.getParameterType();
		return Errors.class.isAssignableFrom(paramType);
	}

	/**
	 * 获取模型中最后一个参数，并检查最后一个参数是否{@link BindingResult}类型，如果是返回结果值，否则抛出异常
	 *
	 * @param parameter the method parameter to resolve. This parameter must
	 * have previously been passed to {@link #supportsParameter} which must
	 * have returned {@code true}.
	 * 要解析的方法参数。此参数之前必须传递给{@link #supportsParameter}，后者必须返回{@code true}。
	 *
	 * @param mavContainer the ModelAndViewContainer for the current request
	 * @param webRequest the current request
	 * @param binderFactory a factory for creating {@link WebDataBinder} instances
	 * @return
	 * @throws Exception
	 */
	@Override
	@Nullable
	public Object resolveArgument(MethodParameter parameter,
			@Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
			@Nullable WebDataBinderFactory binderFactory) throws Exception {

		Assert.state(mavContainer != null,
				"Errors/BindingResult argument only supported on regular handler methods");

		ModelMap model = mavContainer.getModel();
		String lastKey = CollectionUtils.lastElement(model.keySet());
		if (lastKey != null && lastKey.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
			return model.get(lastKey);
		}

		throw new IllegalStateException(
				"An Errors/BindingResult argument is expected to be declared immediately after " +
				"the model attribute, the @RequestBody or the @RequestPart arguments " +
				"to which they apply: " + parameter.getMethod());
	}

}
