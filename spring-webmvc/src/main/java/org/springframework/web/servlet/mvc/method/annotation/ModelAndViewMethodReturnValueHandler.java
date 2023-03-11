/*
 * Copyright 2002-2017 the original author or authors.
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
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.SmartView;
import org.springframework.web.servlet.View;

/**
 * Handles return values of type {@link ModelAndView} copying view and model
 * information to the {@link ModelAndViewContainer}.
 * 处理返回值类型{@link ModelAndView}复制视图和模型信息到{@link ModelAndViewContainer}。
 *
 *
 * <p>If the return value is {@code null}, the
 * {@link ModelAndViewContainer#setRequestHandled(boolean)} flag is set to
 * {@code true} to indicate the request was handled directly.
 * <p>如果返回值是{@code null}， {@link ModelAndViewContainer#setRequestHandled(boolean)}标志被设置为{@code true}，表示直接处理请求。
 *
 *
 * <p>A {@link ModelAndView} return type has a set purpose. Therefore this
 * handler should be configured ahead of handlers that support any return
 * value type annotated with {@code @ModelAttribute} or {@code @ResponseBody}
 * to ensure they don't take over.
 * 一个{@link ModelAndView}返回类型有一个设定的目的。
 * 因此，这个处理程序应该配置在支持任何带有{@code @ModelAttribute}或{@code @ResponseBody}注释的返回值类型的处理程序之前，以确保它们不会接管。
 *
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ModelAndViewMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	@Nullable
	private String[] redirectPatterns;


	/**
	 * Configure one more simple patterns (as described in {@link PatternMatchUtils#simpleMatch})
	 * to use in order to recognize custom redirect prefixes in addition to "redirect:".
	 * 配置一个更简单的模式(如{@link PatternMatchUtils#simpleMatch}所述)来识别除“redirect:”之外的自定义重定向前缀。
	 *
	 * <p>Note that simply configuring this property will not make a custom redirect prefix work.
	 * There must be a custom {@link View} that recognizes the prefix as well.
	 * 注意，简单地配置此属性将不能使自定义重定向前缀工作。必须有一个自定义{@link View}来识别前缀。
	 *
	 * @since 4.1
	 */
	public void setRedirectPatterns(@Nullable String... redirectPatterns) {
		this.redirectPatterns = redirectPatterns;
	}

	/**
	 * Return the configured redirect patterns, if any.
	 * @since 4.1
	 */
	@Nullable
	public String[] getRedirectPatterns() {
		return this.redirectPatterns;
	}


	/**
	 * 处理类型为{@link ModelAndView}的返回值
	 *
	 * @param returnType the method return type to check
	 * @return
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return ModelAndView.class.isAssignableFrom(returnType.getParameterType());
	}

	/**
	 * 处理{@link ModelAndView}类型的返回值，标识是否重定向模型视图，并将返回模型视图中的属性复制到默认模型中
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

		/**
		 * 如果{@link ModelAndView}类型的返回值为null，则表示该模型已经在处理程序中完全处理
		 * 即用户已经在Controller中自己处理了{@link ModelAndView}
		 */
		if (returnValue == null) {
			mavContainer.setRequestHandled(true);
			return;
		}

		ModelAndView mav = (ModelAndView) returnValue;
		/**
		 * 判断视图是否试图引用，即通过视图名称指向一个视图
		 */
		if (mav.isReference()) {
			String viewName = mav.getViewName();
			mavContainer.setViewName(viewName);
			/**
			 * 检查视图是否是重定向试图，默认通过检查视图名称是否"redirect:"前缀
			 */
			if (viewName != null && isRedirectViewName(viewName)) {
				/**
				 * 设置视图是重定向视图
				 */
				mavContainer.setRedirectModelScenario(true);
			}
		}
		else {
			View view = mav.getView();
			mavContainer.setView(view);
			if (view instanceof SmartView && ((SmartView) view).isRedirectView()) {
				mavContainer.setRedirectModelScenario(true);
			}
		}
		mavContainer.setStatus(mav.getStatus());
		/**
		 * 将处理程序返回的模型属性复制到默认模型中
		 */
		mavContainer.addAllAttributes(mav.getModel());
	}

	/**
	 * Whether the given view name is a redirect view reference.
	 * 给定的视图名称是否是重定向视图引用。
	 *
	 * The default implementation checks the configured redirect patterns and
	 * also if the view name starts with the "redirect:" prefix.
	 * 默认实现检查配置的重定向模式，以及视图名是否以“redirect:”前缀开头。
	 *
	 * @param viewName the view name to check, never {@code null}
	 * @return "true" if the given view name is recognized as a redirect view
	 * reference; "false" otherwise.
	 */
	protected boolean isRedirectViewName(String viewName) {
		return (PatternMatchUtils.simpleMatch(this.redirectPatterns, viewName) || viewName.startsWith("redirect:"));
	}

}
