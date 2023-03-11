/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.RequestToViewNameTranslator;
import org.springframework.web.servlet.SmartView;
import org.springframework.web.servlet.View;

/**
 * Handles return values that are of type {@link View}.
 * 处理类型为{@link View}的返回值。
 *
 * <p>A {@code null} return value is left as-is leaving it to the configured
 * {@link RequestToViewNameTranslator} to select a view name by convention.
 * <p> {@code null}返回值保留为-将其留给配置的{@link RequestToViewNameTranslator}来按照约定选择视图名。
 *
 *
 * <p>A {@link View} return type has a set purpose. Therefore this handler
 * should be configured ahead of handlers that support any return value type
 * annotated with {@code @ModelAttribute} or {@code @ResponseBody} to ensure
 * they don't take over.
 * 一个{@link View}返回类型有一个设定的目的。
 * 因此，这个处理程序应该配置在支持任何带有{@code @ModelAttribute}或{@code @ResponseBody}注释的返回值类型的处理程序之前，以确保它们不会接管。
 *
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ViewMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	/**
	 * 处理类型为{@link View}的返回值
	 *
	 * @param returnType the method return type to check
	 * @return
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return View.class.isAssignableFrom(returnType.getParameterType());
	}

	/**
	 * 如果处理程序的返回值类型为{@link View}，那么实际返回值只能是{@link View}类型或者null，否则抛出异常
	 * 将返回的视图设置为DispatcherServlet使用的View对象。将覆盖任何预先存在的视图名称或视图。
	 * 并检查并标记返回视图是否重定向视图
	 *
	 * @param returnValue the value returned from the handler method
	 * @param returnType the type of the return value. This type must have
	 * previously been passed to {@link #supportsReturnType} which must
	 * have returned {@code true}.
	 * @param mavContainer the ModelAndViewContainer for the current request
	 * @param webRequest the current request
	 * @throws Exception
	 */
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		if (returnValue instanceof View) {
			View view = (View) returnValue;
			mavContainer.setView(view);
			if (view instanceof SmartView && ((SmartView) view).isRedirectView()) {
				mavContainer.setRedirectModelScenario(true);
			}
		}
		else if (returnValue != null) {
			// should not happen
			throw new UnsupportedOperationException("Unexpected return type: " +
					returnType.getParameterType().getName() + " in method: " + returnType.getMethod());
		}
	}

}
