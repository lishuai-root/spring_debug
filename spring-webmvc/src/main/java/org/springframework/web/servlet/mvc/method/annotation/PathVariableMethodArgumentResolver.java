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

package org.springframework.web.servlet.mvc.method.annotation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.method.support.UriComponentsContributor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.UriComponentsBuilder;

import java.beans.PropertyEditor;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves method arguments annotated with an @{@link PathVariable}.
 * 解析带有@{@link PathVariable}注释的方法参数。
 *
 * <p>An @{@link PathVariable} is a named value that gets resolved from a URI template variable.
 * It is always required and does not have a default value to fall back on. See the base class
 * {@link org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver}
 * for more information on how named values are processed.
 *
 * {@link PathVariable}是一个从URI模板变量解析出来的命名值。它总是必需的，并且没有可依赖的默认值。
 * 参见基类{@link org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver}获取关于如何处理命名值的更多信息。
 *
 *
 * <p>If the method parameter type is {@link Map}, the name specified in the annotation is used
 * to resolve the URI variable String value. The value is then converted to a {@link Map} via
 * type conversion, assuming a suitable {@link Converter} or {@link PropertyEditor} has been
 * registered.
 * 如果方法参数类型为{@link Map}，则使用注释中指定的名称解析URI变量String值。然后通过类型转换将值转换为{@link Map}，
 * 假设已经注册了合适的{@link Converter}或{@link PropertyEditor}。
 *
 * ls: 可以通过自定义的类型转换器将String转成需要的 {@link Map}
 *
 *
 * <p>A {@link WebDataBinder} is invoked to apply type conversion to resolved path variable
 * values that don't yet match the method parameter type.
 * <p>调用一个{@link WebDataBinder}，将类型转换应用到不匹配方法参数类型的已解析路径变量值。
 *
 * @author Rossen Stoyanchev
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 3.1
 */
public class PathVariableMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver
		implements UriComponentsContributor {

	private static final TypeDescriptor STRING_TYPE_DESCRIPTOR = TypeDescriptor.valueOf(String.class);


	/**
	 * 检查当前解析器是否可以解析指定方法参数，
	 * 检查条件如下，满足其一就返回，不再后续判断：
	 * 	1.如果当前方法参数没有使用{@link PathVariable}参数，返回false
	 * 	2.如果参数类型是{@link Map}并且使用了{@link PathVariable}注解，且注解的{@link PathVariable#value()}不为空，返回true，否则返回false
	 * 	3.参数带有{@link PathVariable}返回true
	 *
	 * @param parameter the method parameter to check
	 * @return
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		if (!parameter.hasParameterAnnotation(PathVariable.class)) {
			return false;
		}
		/**
		 * 参数类型为{@link Map}类型并且使用了{@link PathVariable}注解，且{@link PathVariable#value()} 指定了参数名称
		 */
		if (Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) {
			PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
			return (pathVariable != null && StringUtils.hasText(pathVariable.value()));
		}
		return true;
	}

	/**
	 * 使用{@link PathVariable}定义的参数名创建参数名称信息
	 *
	 * @param parameter the method parameter
	 * @return
	 */
	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		PathVariable ann = parameter.getParameterAnnotation(PathVariable.class);
		Assert.state(ann != null, "No PathVariable annotation");
		return new PathVariableNamedValueInfo(ann);
	}

	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
		/**
		 * {@link HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE}参数在获取请求处理程序链时添加。
		 * 
		 * eg：
		 * 	1.{@link org.springframework.web.servlet.handler.AbstractUrlHandlerMapping.UriTemplateVariablesHandlerInterceptor#preHandle(HttpServletRequest, HttpServletResponse, Object)}
		 * 		方法中设置
		 * 	2.{@link org.springframework.web.servlet.handler.AbstractHandlerMethodMapping#getHandlerInternal(HttpServletRequest)} ->
		 * 	  {@link org.springframework.web.servlet.handler.AbstractHandlerMethodMapping#lookupHandlerMethod(String, HttpServletRequest)} ->
		 * 	  {@link org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping#handleMatch(RequestMappingInfo, String, HttpServletRequest)} ->
		 * 	  (
		 * 	  	{@link org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping#extractMatchDetails(PatternsRequestCondition, String, HttpServletRequest)} or
		 * 	    {@link org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping#extractMatchDetails(PathPatternsRequestCondition, String, HttpServletRequest)}
		 * 	  ) 方法中设置
		 */
		Map<String, String> uriTemplateVars = (Map<String, String>) request.getAttribute(
				HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		/**
		 * 从所有URI模板名称值对中获取指定名称的值
		 */
		return (uriTemplateVars != null ? uriTemplateVars.get(name) : null);
	}

	@Override
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletRequestBindingException {
		throw new MissingPathVariableException(name, parameter);
	}

	@Override
	protected void handleMissingValueAfterConversion(
			String name, MethodParameter parameter, NativeWebRequest request) throws Exception {

		throw new MissingPathVariableException(name, parameter, true);
	}

	/**
	 * 参数值解析后的回调，默认空实现
	 *
	 * @param arg the resolved argument value
	 * @param name the argument name
	 * @param parameter the argument parameter type
	 * @param mavContainer the {@link ModelAndViewContainer} (may be {@code null})
	 * @param request
	 */
	@Override
	@SuppressWarnings("unchecked")
	protected void handleResolvedValue(@Nullable Object arg, String name, MethodParameter parameter,
			@Nullable ModelAndViewContainer mavContainer, NativeWebRequest request) {

		String key = View.PATH_VARIABLES;
		int scope = RequestAttributes.SCOPE_REQUEST;
		Map<String, Object> pathVars = (Map<String, Object>) request.getAttribute(key, scope);
		/**
		 * 把解析到的URI模板属性值缓存到请求中
		 */
		if (pathVars == null) {
			pathVars = new HashMap<>();
			request.setAttribute(key, pathVars, scope);
		}
		pathVars.put(name, arg);
	}

	@Override
	public void contributeMethodArgument(MethodParameter parameter, Object value,
			UriComponentsBuilder builder, Map<String, Object> uriVariables, ConversionService conversionService) {

		if (Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) {
			return;
		}

		PathVariable ann = parameter.getParameterAnnotation(PathVariable.class);
		String name = (ann != null && StringUtils.hasLength(ann.value()) ? ann.value() : parameter.getParameterName());
		String formatted = formatUriValue(conversionService, new TypeDescriptor(parameter.nestedIfOptional()), value);
		uriVariables.put(name, formatted);
	}

	@Nullable
	protected String formatUriValue(@Nullable ConversionService cs, @Nullable TypeDescriptor sourceType, Object value) {
		if (value instanceof String) {
			return (String) value;
		}
		else if (cs != null) {
			return (String) cs.convert(value, sourceType, STRING_TYPE_DESCRIPTOR);
		}
		else {
			return value.toString();
		}
	}


	private static class PathVariableNamedValueInfo extends NamedValueInfo {

		public PathVariableNamedValueInfo(PathVariable annotation) {
			super(annotation.name(), annotation.required(), ValueConstants.DEFAULT_NONE);
		}
	}

}
