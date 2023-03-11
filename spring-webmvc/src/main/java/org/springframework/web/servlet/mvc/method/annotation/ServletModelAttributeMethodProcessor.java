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

package org.springframework.web.servlet.mvc.method.annotation;

import jakarta.servlet.ServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Collections;
import java.util.Map;

/**
 * A Servlet-specific {@link ModelAttributeMethodProcessor} that applies data
 * binding through a WebDataBinder of type {@link ServletRequestDataBinder}.
 *
 * 特定于servlet的{@link ModelAttributeMethodProcessor}，它通过类型为{@link ServletRequestDataBinder}的WebDataBinder应用数据绑定。
 *
 * <p>Also adds a fall-back strategy to instantiate the model attribute from a
 * URI template variable or from a request parameter if the name matches the
 * model attribute name and there is an appropriate type conversion strategy.
 *<p>还添加了一个回退策略，从URI模板变量或从请求参数实例化模型属性，如果名称与模型属性名称匹配，并且有适当的类型转换策略。
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ServletModelAttributeMethodProcessor extends ModelAttributeMethodProcessor {

	/**
	 * Class constructor.
	 * @param annotationNotRequired if "true", non-simple method arguments and
	 * return values are considered model attributes with or without a
	 * {@code @ModelAttribute} annotation
	 * 如果“true”，非简单方法参数和返回值被认为是模型属性，有或没有{@code @ModelAttribute}注释
	 */
	public ServletModelAttributeMethodProcessor(boolean annotationNotRequired) {
		super(annotationNotRequired);
	}


	/**
	 * 创建参数
	 * 	1. 首先在请求URI模板参数和请求参数中获取参数，并进行类型转换，参数值不为空且可以转换成目标类型，返回转换后的值
	 * 	2. 委托给父类通过构造函数创建目标参数类型的参数值
	 *
	 * Instantiate the model attribute from a URI template variable or from a
	 * request parameter if the name matches to the model attribute name and
	 * if there is an appropriate type conversion strategy. If none of these
	 * are true delegate back to the base class.
	 * 如果名称与模型属性名称匹配，并且存在适当的类型转换策略，则从URI模板变量或从请求参数实例化模型属性。如果这些都不为真，则委托回基类。
	 *
	 * @see #createAttributeFromRequestValue
	 */
	@Override
	protected final Object createAttribute(String attributeName, MethodParameter parameter,
			WebDataBinderFactory binderFactory, NativeWebRequest request) throws Exception {

		/**
		 * 在URI模板变量和请求参数中查找指定名称的参数
		 * 首先查找与URI变量匹配的属性名，然后查找请求参数。
		 */
		String value = getRequestValueForAttribute(attributeName, request);
		if (value != null) {
			/**
			 * 通过{@link org.springframework.web.bind.annotation.InitBinder}注入的类型转换器进行参数类型转换
			 * 如果value可以转换成目标参数值类型，返回转换后的参数值，如果不能，返回null
			 */
			Object attribute = createAttributeFromRequestValue(
					value, attributeName, parameter, binderFactory, request);
			if (attribute != null) {
				return attribute;
			}
		}

		/**
		 * 如果URI模板变量和请求参数中没有指定名称的参数，委托给父类处理
		 */
		return super.createAttribute(attributeName, parameter, binderFactory, request);
	}

	/**
	 * 默认实现首先查找与URI变量匹配的属性名，然后查找请求参数。
	 *
	 * Obtain a value from the request that may be used to instantiate the
	 * model attribute through type conversion from String to the target type.
	 * 从请求中获取一个值，该值可用于通过从String到目标类型的类型转换实例化模型属性。
	 *
	 * <p>The default implementation looks for the attribute name to match
	 * a URI variable first and then a request parameter.
	 * 默认实现首先查找与URI变量匹配的属性名，然后查找请求参数。
	 *
	 * @param attributeName the model attribute name
	 * @param request the current request
	 * @return the request value to try to convert, or {@code null} if none
	 */
	@Nullable
	protected String getRequestValueForAttribute(String attributeName, NativeWebRequest request) {
		/**
		 * 首先在URI模板参数中查找指定名称的参数
		 */
		Map<String, String> variables = getUriTemplateVariables(request);
		String variableValue = variables.get(attributeName);
		if (StringUtils.hasText(variableValue)) {
			return variableValue;
		}
		/**
		 * 如果URI模板参数中不存在指定名称的参数，在请求参数中查找
		 */
		String parameterValue = request.getParameter(attributeName);
		if (StringUtils.hasText(parameterValue)) {
			return parameterValue;
		}
		return null;
	}

	protected final Map<String, String> getUriTemplateVariables(NativeWebRequest request) {
		@SuppressWarnings("unchecked")
		Map<String, String> variables = (Map<String, String>) request.getAttribute(
				HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		return (variables != null ? variables : Collections.emptyMap());
	}

	/**
	 * 通过{@link org.springframework.web.bind.annotation.InitBinder}注入的类型转换器进行参数类型转换
	 *
	 * Create a model attribute from a String request value (e.g. URI template
	 * variable, request parameter) using type conversion.
	 * 使用类型转换从String请求值(例如URI模板变量，请求参数)创建一个模型属性。
	 *
	 * <p>The default implementation converts only if there a registered
	 * 默认实现仅在有注册时才转换
	 *
	 * {@link Converter} that can perform the conversion.
	 * @param sourceValue the source value to create the model attribute from
	 * @param attributeName the name of the attribute (never {@code null})
	 * @param parameter the method parameter
	 * @param binderFactory for creating WebDataBinder instance
	 * @param request the current request
	 * @return the created model attribute, or {@code null} if no suitable
	 * conversion found
	 */
	@Nullable
	protected Object createAttributeFromRequestValue(String sourceValue, String attributeName,
			MethodParameter parameter, WebDataBinderFactory binderFactory, NativeWebRequest request)
			throws Exception {

		/**
		 * 通过{@link org.springframework.web.bind.annotation.InitBinder}注入的类型转换器进行参数类型转换
		 */
		DataBinder binder = binderFactory.createBinder(request, null, attributeName);
		ConversionService conversionService = binder.getConversionService();
		/**
		 * 如果可用与当前源类型到目标类型的类型转换器
		 */
		if (conversionService != null) {
			TypeDescriptor source = TypeDescriptor.valueOf(String.class);
			TypeDescriptor target = new TypeDescriptor(parameter);
			/**
			 * 如果有合适的从源类型到目标类型的转换器，则进行类型转换
			 */
			if (conversionService.canConvert(source, target)) {
				return binder.convertIfNecessary(sourceValue, parameter.getParameterType(), parameter);
			}
		}
		return null;
	}

	/**
	 * This implementation downcasts {@link WebDataBinder} to
	 * {@link ServletRequestDataBinder} before binding.
	 *
	 * 这个实现在绑定之前将{@link WebDataBinder}向下转换为{@link ServletRequestDataBinder}。
	 *
	 * @see ServletRequestDataBinderFactory
	 */
	@Override
	protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
		ServletRequest servletRequest = request.getNativeRequest(ServletRequest.class);
		Assert.state(servletRequest != null, "No ServletRequest");
		ServletRequestDataBinder servletBinder = (ServletRequestDataBinder) binder;
		servletBinder.bind(servletRequest);
	}

	/**
	 * 调用父类方法，查找指定名称的上传文件或者表单项，如果父类方法找到了，返回
	 * 如果父类方法没找到，在URI模板参数中查找指定参数名称的参数值并返回(或者返回null)
	 *
	 * @param paramName
	 * @param paramType
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@Override
	@Nullable
	public Object resolveConstructorArgument(String paramName, Class<?> paramType, NativeWebRequest request)
			throws Exception {

		/**
		 * 查找表单项
		 */
		Object value = super.resolveConstructorArgument(paramName, paramType, request);
		if (value != null) {
			return value;
		}
		/**
		 * 查找URI模板参数
		 */
		ServletRequest servletRequest = request.getNativeRequest(ServletRequest.class);
		if (servletRequest != null) {
			String attr = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
			@SuppressWarnings("unchecked")
			Map<String, String> uriVars = (Map<String, String>) servletRequest.getAttribute(attr);
			return uriVars.get(paramName);
		}
		return null;
	}

}
