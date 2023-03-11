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

package org.springframework.web.method.annotation;

import java.beans.PropertyEditor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.UriComponentsContributor;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 处理程序的方法参数解析器
 *
 * 以下参数类型将使用当前参数解析器：
 * 1. 带有{@link RequestParam}注解，且指定参数名称的{@link Map}类型的参数
 * 2. 带有{@link RequestParam}注解，且参数类型是{@link Map}以外的其他类型
 * 3. 没有带{@link RequestParam}注解，且参数类型是表单项类型，或者参数类型为表单项类型的集合
 * 4. 没有带{@link RequestParam}注解，且允许使用默认解析策略，且参数类型为简单类型或者简单类型的数组
 *
 *
 * Resolves method arguments annotated with @{@link RequestParam}, arguments of
 * type {@link MultipartFile} in conjunction with Spring's {@link MultipartResolver}
 * abstraction, and arguments of type {@code jakarta.servlet.http.Part} in conjunction
 * with Servlet 3.0 multipart requests. This resolver can also be created in default
 * resolution mode in which simple types (int, long, etc.) not annotated with
 * {@link RequestParam @RequestParam} are also treated as request parameters with
 * the parameter name derived from the argument name.
 *
 * 解析带有@{@link RequestParam}注释的方法参数，与Spring的{@link MultipartResolver}抽象结合的{@link MultipartResolver}类型的参数，
 * 以及类型为{@code jakarta.servlet.http.Part}的参数。结合Servlet 3.0多部分请求。
 * 这个解析器也可以在默认解析模式下创建，在这种模式下，没有{@link RequestParam @RequestParam}注释的简单类型(int, long等)也被视为请求参数，
 * 其参数名派生自参数名。
 *
 *
 * <p>If the method parameter type is {@link Map}, the name specified in the
 * annotation is used to resolve the request parameter String value. The value is
 * then converted to a {@link Map} via type conversion assuming a suitable
 * {@link Converter} or {@link PropertyEditor} has been registered.

 * 如果方法参数类型是{@link Map}，注释中指定的名称将用于解析请求参数String值。
 * 然后，假设已经注册了合适的{@link Converter}或{@link PropertyEditor}，该值将通过类型转换转换为{@link Map}。
 *
 * ls: 如果参数是{@link Map}类型，将请求中名称和参数名称相同的josn串解析成{@link Map}类型
 *
 * Or if a request parameter name is not specified the
 * {@link RequestParamMapMethodArgumentResolver} is used instead to provide
 * access to all request parameters in the form of a map.
 * 或者如果没有指定请求参数名，则使用{@link RequestParamMapMethodArgumentResolver}以映射的形式提供对所有请求参数的访问。
 *
 * ls: 如果没有和{@link Map}类型参数名称相同的请求参数，那就把所有的请求参数都封装成一个{@link Map}
 *
 * <p>A {@link WebDataBinder} is invoked to apply type conversion to resolved request
 * header values that don't yet match the method parameter type.
 * <p>调用一个{@link WebDataBinder}来对解析后的不匹配方法参数类型的请求头值应用类型转换。
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 3.1
 * @see RequestParamMapMethodArgumentResolver
 */
public class RequestParamMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver
		implements UriComponentsContributor {

	private static final TypeDescriptor STRING_TYPE_DESCRIPTOR = TypeDescriptor.valueOf(String.class);

	/**
	 * 是否使用默认解析策略
	 * 在默认解析模式下，一个简单类型的方法参数(如{@link BeanUtils#isSimpleProperty}中定义的那样)被视为一个请求参数，即使它没有注释，请求参数名也从方法参数名派生出来。
	 */
	private final boolean useDefaultResolution;


	/**
	 * Create a new {@link RequestParamMethodArgumentResolver} instance.
	 * 创建一个新的{@link RequestParamMethodArgumentResolver}实例。
	 *
	 * @param useDefaultResolution in default resolution mode a method argument
	 * that is a simple type, as defined in {@link BeanUtils#isSimpleProperty},
	 * is treated as a request parameter even if it isn't annotated, the
	 * request parameter name is derived from the method parameter name.
	 * 在默认解析模式下，一个简单类型的方法参数(如{@link BeanUtils#isSimpleProperty}中定义的那样)被视为一个请求参数，
	 * 即使它没有注释，请求参数名也从方法参数名派生出来。
	 *
	 */
	public RequestParamMethodArgumentResolver(boolean useDefaultResolution) {
		this.useDefaultResolution = useDefaultResolution;
	}

	/**
	 * Create a new {@link RequestParamMethodArgumentResolver} instance.
	 * @param beanFactory a bean factory used for resolving  ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 * beanFactory用于解析${…}占位符和#{…}在默认值中拼写表达式，如果默认值不包含表达式，则为{@code null}
	 *
	 * @param useDefaultResolution in default resolution mode a method argument
	 * that is a simple type, as defined in {@link BeanUtils#isSimpleProperty},
	 * is treated as a request parameter even if it isn't annotated, the
	 * request parameter name is derived from the method parameter name.
	 */
	public RequestParamMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory,
			boolean useDefaultResolution) {

		super(beanFactory);
		this.useDefaultResolution = useDefaultResolution;
	}


	/**
	 * 以下参数类型将使用当前参数解析器：
	 * 1. 带有{@link RequestParam}注解，且指定参数名称的{@link Map}类型的参数
	 * 2. 带有{@link RequestParam}注解，且参数类型是{@link Map}以外的其他类型
	 * 3. 没有带{@link RequestParam}注解，且参数类型是表单项类型，或者参数类型为表单项类型的集合
	 * 4. 没有带{@link RequestParam}注解，且允许使用默认解析策略，且参数类型为简单类型或者简单类型的数组
	 *
	 *
	 * Supports the following:
	 * 支持以下功能:
	 * <ul>
	 * <li>@RequestParam-annotated method arguments.
	 * @Requestparam注释的方法参数。
	 * This excludes {@link Map} params where the annotation does not specify a name.
	 * 这将排除注释没有指定名称的{@link Map}参数。
	 * See {@link RequestParamMapMethodArgumentResolver} instead for such params.
	 * 请参阅{@link RequestParamMapMethodArgumentResolver}以获得这样的参数。
	 * <li>Arguments of type {@link MultipartFile} unless annotated with @{@link RequestPart}.
	 * 参数类型为{@link MultipartFile}，除非带有@{@link RequestPart}注释。
	 * <li>Arguments of type {@code Part} unless annotated with @{@link RequestPart}.
	 * 参数类型为{@code Part}，除非带有@{@link RequestPart}注释。
	 * <li>In default resolution mode, simple type arguments even if not with @{@link RequestParam}.
	 * 在默认解析模式下，简单类型参数即使没有@{@link RequestParam}。
	 * </ul>
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		/**
		 * 检查当前参数是否带有{@link RequestParam}注解
		 */
		if (parameter.hasParameterAnnotation(RequestParam.class)) {
			/**
			 * 当参数是{@link Map}类型，且使用{@link RequestParam}指定了请求参数名称时，才使用当前参数解析器
			 * 否则将会使用{@link RequestParamMapMethodArgumentResolver}参数解析进行解析
			 */
			if (Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) {
				RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
				return (requestParam != null && StringUtils.hasText(requestParam.name()));
			}
			/**
			 * 除{@link Map}类型外，其他类型的参数只要使用了{@link RequestParam}注解，不管是否指定请求参数名称，都会使用当前参数解析器
			 */
			else {
				return true;
			}
		}
		else {
			/**
			 * 如果参数带有{@link RequestPart}注解，返回false
			 */
			if (parameter.hasParameterAnnotation(RequestPart.class)) {
				return false;
			}
			parameter = parameter.nestedIfOptional();
			/**
			 * 如果方法参数是请求提交的单个表单项或者表单项类型的集合，返回true
			 */
			if (MultipartResolutionDelegate.isMultipartArgument(parameter)) {
				return true;
			}
			/**
			 * 如果允许使用默认解析策略，且当前方法参数类型为简单类型
			 */
			else if (this.useDefaultResolution) {
				return BeanUtils.isSimpleProperty(parameter.getNestedParameterType());
			}
			else {
				return false;
			}
		}
	}

	/**
	 * 创建参数名称信息，如果使用了{@link RequestParam}注解，就是用{@link RequestParam#name()}属性作为参数名，
	 * 否则为""
	 *
	 * @param parameter the method parameter
	 * @return
	 */
	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		RequestParam ann = parameter.getParameterAnnotation(RequestParam.class);
		return (ann != null ? new RequestParamNamedValueInfo(ann) : new RequestParamNamedValueInfo());
	}

	/**
	 * 解析参数名称对应的值
	 * 		1. 首先尝试在表单项中解析指定名称的表单项
	 * 		2. 如果是上传请求，获取指定名称的上传文件
	 * 		3. 获取指定名称的请求参数
	 *
	 * @param name the name of the value being resolved
	 * @param parameter the method parameter to resolve to an argument value
	 * (pre-nested in case of a {@link java.util.Optional} declaration)
	 * @param request the current request
	 * @return
	 * @throws Exception
	 */
	@Override
	@Nullable
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
		HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);

		if (servletRequest != null) {
			/**
			 * 首先解析表单项
			 * 如果方法参数类型是{@link MultipartFile}或者{@link Part}类型，但是当前请求不是上传请求，则返回null
			 * 如果方法参数类型是{@link MultipartFile}或者{@link Part}类型，且当前请求是上传请求，但是没有名称为name的表单项，返回{@link MultipartResolutionDelegate.UNRESOLVABLE}
			 */
			Object mpArg = MultipartResolutionDelegate.resolveMultipartArgument(name, parameter, servletRequest);
			/**
			 * 当返回值为null时，说明请求不是上传请求
			 *
			 * 当返回值是{@link MultipartResolutionDelegate.UNRESOLVABLE}时，说明方法参数类型不是{@link MultipartFile}和{@link Part}类型
			 */
			if (mpArg != MultipartResolutionDelegate.UNRESOLVABLE) {
				return mpArg;
			}
		}

		/**
		 * 如果当前请求是文件上传请求，获取名称为name的文件
		 */
		Object arg = null;
		MultipartRequest multipartRequest = request.getNativeRequest(MultipartRequest.class);
		if (multipartRequest != null) {
			List<MultipartFile> files = multipartRequest.getFiles(name);
			if (!files.isEmpty()) {
				arg = (files.size() == 1 ? files.get(0) : files);
			}
		}
		/**
		 * 在请求中获取名称为name的值
		 */
		if (arg == null) {
			String[] paramValues = request.getParameterValues(name);
			if (paramValues != null) {
				arg = (paramValues.length == 1 ? paramValues[0] : paramValues);
			}
		}
		return arg;
	}

	@Override
	protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {

		handleMissingValueInternal(name, parameter, request, false);
	}

	@Override
	protected void handleMissingValueAfterConversion(
			String name, MethodParameter parameter, NativeWebRequest request) throws Exception {

		handleMissingValueInternal(name, parameter, request, true);
	}

	protected void handleMissingValueInternal(
			String name, MethodParameter parameter, NativeWebRequest request, boolean missingAfterConversion)
			throws Exception {

		HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
		if (MultipartResolutionDelegate.isMultipartArgument(parameter)) {
			if (servletRequest == null || !MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
				throw new MultipartException("Current request is not a multipart request");
			}
			else {
				throw new MissingServletRequestPartException(name);
			}
		}
		else {
			throw new MissingServletRequestParameterException(name,
					parameter.getNestedParameterType().getSimpleName(), missingAfterConversion);
		}
	}

	@Override
	public void contributeMethodArgument(MethodParameter parameter, @Nullable Object value,
			UriComponentsBuilder builder, Map<String, Object> uriVariables, ConversionService conversionService) {

		Class<?> paramType = parameter.getNestedParameterType();
		if (Map.class.isAssignableFrom(paramType) || MultipartFile.class == paramType || Part.class == paramType) {
			return;
		}

		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		String name = (requestParam != null && StringUtils.hasLength(requestParam.name()) ?
				requestParam.name() : parameter.getParameterName());
		Assert.state(name != null, "Unresolvable parameter name");

		parameter = parameter.nestedIfOptional();
		if (value instanceof Optional) {
			value = ((Optional<?>) value).orElse(null);
		}

		if (value == null) {
			if (requestParam != null &&
					(!requestParam.required() || !requestParam.defaultValue().equals(ValueConstants.DEFAULT_NONE))) {
				return;
			}
			builder.queryParam(name);
		}
		else if (value instanceof Collection) {
			for (Object element : (Collection<?>) value) {
				element = formatUriValue(conversionService, TypeDescriptor.nested(parameter, 1), element);
				builder.queryParam(name, element);
			}
		}
		else {
			builder.queryParam(name, formatUriValue(conversionService, new TypeDescriptor(parameter), value));
		}
	}

	@Nullable
	protected String formatUriValue(
			@Nullable ConversionService cs, @Nullable TypeDescriptor sourceType, @Nullable Object value) {

		if (value == null) {
			return null;
		}
		else if (value instanceof String) {
			return (String) value;
		}
		else if (cs != null) {
			return (String) cs.convert(value, sourceType, STRING_TYPE_DESCRIPTOR);
		}
		else {
			return value.toString();
		}
	}


	private static class RequestParamNamedValueInfo extends NamedValueInfo {

		public RequestParamNamedValueInfo() {
			super("", false, ValueConstants.DEFAULT_NONE);
		}

		public RequestParamNamedValueInfo(RequestParam annotation) {
			super(annotation.name(), annotation.required(), annotation.defaultValue());
		}
	}

}
