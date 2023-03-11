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
import jakarta.servlet.http.Part;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;
import org.springframework.web.multipart.support.RequestPartServletServerHttpRequest;

import java.util.List;

/**
 * Resolves the following method arguments:
 * 解决以下方法参数:
 * <ul>
 * <li>Annotated with @{@link RequestPart} 注释@{@link RequestPart}
 * <li>Of type {@link MultipartFile} in conjunction with Spring's {@link MultipartResolver} abstraction
 * 类型{@link MultipartFile}结合Spring的{@link MultipartResolver}抽象
 *
 * <li>Of type {@code jakarta.servlet.http.Part} in conjunction with Servlet 3.0 multipart requests
 * 类型为{@code jakarta.servlet.http.Part}结合Servlet 3.0多部分请求
 * </ul>
 *
 * <p>When a parameter is annotated with {@code @RequestPart}, the content of the part is
 * passed through an {@link HttpMessageConverter} to resolve the method argument with the
 * 'Content-Type' of the request part in mind. This is analogous to what @{@link RequestBody}
 * does to resolve an argument based on the content of a regular request.
 *
 * 当一个参数被{@code @RequestPart}注释时，该部分的内容将通过一个{@link HttpMessageConverter}传递，以解析方法参数，
 * 并考虑请求部分的“content-type”。这类似于@{@link RequestBody}根据常规请求的内容解析参数。
 *
 *
 * <p>When a parameter is not annotated with {@code @RequestPart} or the name of
 * the part is not specified, the request part's name is derived from the name of
 * the method argument.
 * 当一个参数没有标注{@code @RequestPart}或者没有指定部分的名称时，请求部分的名称将从方法参数的名称派生。
 *
 *
 * <p>Automatic validation may be applied if the argument is annotated with any
 * {@linkplain org.springframework.validation.annotation.ValidationAnnotationUtils#determineValidationHints
 * annotations that trigger validation}. In case of validation failure, a
 * {@link MethodArgumentNotValidException} is raised and a 400 response status code returned if the
 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}
 * is configured.
 *
 * 如果参数带有任何触发验证的注释{@linkplain org.springframework.validation.annotation.ValidationAnnotationUtils#determineValidationHints 注释}，
 * 则可能应用自动验证。在验证失败的情况下，将引发一个{@link MethodArgumentNotValidException}，
 * 如果配置了{@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}返回一个400响应状态代码。
 *
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Juergen Hoeller
 * @since 3.1
 */
public class RequestPartMethodArgumentResolver extends AbstractMessageConverterMethodArgumentResolver {

	/**
	 * Basic constructor with converters only.
	 */
	public RequestPartMethodArgumentResolver(List<HttpMessageConverter<?>> messageConverters) {
		super(messageConverters);
	}

	/**
	 * Constructor with converters and {@code RequestBodyAdvice} and
	 * {@code ResponseBodyAdvice}.
	 */
	public RequestPartMethodArgumentResolver(List<HttpMessageConverter<?>> messageConverters,
			List<Object> requestResponseBodyAdvice) {

		super(messageConverters, requestResponseBodyAdvice);
	}


	/**
	 * 参数被{@link RequestPart}注解注释，或者参数是多部份参数类型，且没有被{@link RequestParam}注解注释
	 *
	 * Whether the given {@linkplain MethodParameter method parameter} is
	 * supported as multi-part. Supports the following method parameters:
	 * 是否支持给定的{@linkplain MethodParameter 方法参数}作为多部分。支持以下方法参数:
	 *
	 * <ul>
	 * <li>annotated with {@code @RequestPart} 注释{@code @RequestPart}
	 * <li>of type {@link MultipartFile} unless annotated with {@code @RequestParam}
	 * 类型为{@link MultipartFile}，除非注释了{@code @RequestParam}
	 *
	 * <li>of type {@code jakarta.servlet.http.Part} unless annotated with
	 * {@code @RequestParam}
	 * 类型为{@code jakarta.servlet.http.Part} 除非用{@code @RequestParam}注释
	 * </ul>
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		if (parameter.hasParameterAnnotation(RequestPart.class)) {
			return true;
		}
		else {
			if (parameter.hasParameterAnnotation(RequestParam.class)) {
				return false;
			}
			/**
			 * 方法参数类型是否一个多部份参数类型
			 */
			return MultipartResolutionDelegate.isMultipartArgument(parameter.nestedIfOptional());
		}
	}

	/**
	 * 如果请求参数{@link Part}或者{@link MultipartFile}类型(或者其类型集合)，直接在表单项中获取
	 * 如果不是，获取参数名称对应的表单项或者请求参数，并使用{@link HttpMessageConverter}转换成参数类型
	 *
	 * @param parameter the method parameter to resolve. This parameter must
	 * have previously been passed to {@link #supportsParameter} which must
	 * have returned {@code true}.
	 * 要解析的方法参数。此参数之前必须传递给{@link #supportsParameter}，后者必须返回{@code true}。
	 *
	 * @param mavContainer the ModelAndViewContainer for the current request
	 * @param request
	 * @param binderFactory a factory for creating {@link WebDataBinder} instances
	 * @return
	 * @throws Exception
	 */
	@Override
	@Nullable
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest request, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
		Assert.state(servletRequest != null, "No HttpServletRequest");

		RequestPart requestPart = parameter.getParameterAnnotation(RequestPart.class);
		boolean isRequired = ((requestPart == null || requestPart.required()) && !parameter.isOptional());

		String name = getPartName(parameter, requestPart);
		parameter = parameter.nestedIfOptional();
		Object arg = null;

		Object mpArg = MultipartResolutionDelegate.resolveMultipartArgument(name, parameter, servletRequest);
		/**
		 * 当返回值为{@link MultipartResolutionDelegate#UNRESOLVABLE}时，说明参数类型不是{@link MultipartFile}和{@link Part}类型
		 * 当返回null时：
		 * 	 1. 参数类型是{@link MultipartFile}或者{@link Part}类型，但是请求不是上传请求
		 * 	 2. 参数类型是{@link MultipartFile}或者{@link Part}类型，请求是上传请求，但是没有指定名称的表单项
		 */
		if (mpArg != MultipartResolutionDelegate.UNRESOLVABLE) {
			/**
			 * 当方法参数类型为{@link MultipartFile}或者{@link Part}类型时使用解析到的值，可能为null
			 */
			arg = mpArg;
		}
		else {
			/**
			 * 解析类型不是{@link MultipartFile}或者{@link Part}类型参数
			 */
			try {
				/**
				 * 创建实例并检查是否存在参数名对应的多部份内容，如果不存在抛出异常
				 */
				HttpInputMessage inputMessage = new RequestPartServletServerHttpRequest(servletRequest, name);
				/**
				 * 通过消息转换器{@link HttpMessageConverter}将指定名称的表单项转成参数类型
				 */
				arg = readWithMessageConverters(inputMessage, parameter, parameter.getNestedGenericParameterType());
				/**
				 * 数据绑定
				 */
				if (binderFactory != null) {
					WebDataBinder binder = binderFactory.createBinder(request, arg, name);
					if (arg != null) {
						validateIfApplicable(binder, parameter);
						if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, parameter)) {
							throw new MethodArgumentNotValidException(parameter, binder.getBindingResult());
						}
					}
					if (mavContainer != null) {
						mavContainer.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, binder.getBindingResult());
					}
				}
			}
			catch (MissingServletRequestPartException | MultipartException ex) {
				if (isRequired) {
					throw ex;
				}
			}
		}

		/**
		 * 如果参数值为null，且参数是必须的，抛出异常
		 */
		if (arg == null && isRequired) {
			if (!MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
				throw new MultipartException("Current request is not a multipart request");
			}
			else {
				throw new MissingServletRequestPartException(name);
			}
		}
		/**
		 * 如果需要返回{@link java.util.Optional}值
		 */
		return adaptArgumentIfNecessary(arg, parameter);
	}

	/**
	 * 获取参数名称
	 *
	 * @param methodParam
	 * @param requestPart
	 * @return
	 */
	private String getPartName(MethodParameter methodParam, @Nullable RequestPart requestPart) {
		String partName = (requestPart != null ? requestPart.name() : "");
		if (partName.isEmpty()) {
			partName = methodParam.getParameterName();
			if (partName == null) {
				throw new IllegalArgumentException("Request part name for argument type [" +
						methodParam.getNestedParameterType().getName() +
						"] not specified, and parameter name information not found in class file either.");
			}
		}
		return partName;
	}

}
