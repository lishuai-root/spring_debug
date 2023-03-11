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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@link HttpEntity} and {@link RequestEntity} method argument values
 * and also handles {@link HttpEntity} and {@link ResponseEntity} return values.
 * 解析{@link HttpEntity}和{@link RequestEntity}方法参数值，并处理{@link HttpEntity}和{@link ResponseEntity}返回值。
 *
 * <p>An {@link HttpEntity} return type has a specific purpose. Therefore this
 * handler should be configured ahead of handlers that support any return
 * value type annotated with {@code @ModelAttribute} or {@code @ResponseBody}
 * to ensure they don't take over.
 *
 * {@link HttpEntity}返回类型有特定的目的。
 * 因此，这个处理程序应该配置在支持任何带有{@code @ModelAttribute}或{@code @ResponseBody}注释的返回值类型的处理程序之前，以确保它们不会接管。
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 3.1
 */
public class HttpEntityMethodProcessor extends AbstractMessageConverterMethodProcessor {

	/**
	 * Basic constructor with converters only. Suitable for resolving
	 * {@code HttpEntity}. For handling {@code ResponseEntity} consider also
	 * providing a {@code ContentNegotiationManager}.
	 */
	public HttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters) {
		super(converters);
	}

	/**
	 * Basic constructor with converters and {@code ContentNegotiationManager}.
	 * Suitable for resolving {@code HttpEntity} and handling {@code ResponseEntity}
	 * without {@code Request~} or {@code ResponseBodyAdvice}.
	 */
	public HttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters,
			ContentNegotiationManager manager) {

		super(converters, manager);
	}

	/**
	 * Complete constructor for resolving {@code HttpEntity} method arguments.
	 * For handling {@code ResponseEntity} consider also providing a
	 * {@code ContentNegotiationManager}.
	 * 用于解析{@code HttpEntity}方法参数的完整构造函数。为了处理{@code ResponseEntity}，
	 * 还可以考虑提供一个{@code ContentNegotiationManager}。
	 *
	 * @since 4.2
	 */
	public HttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters,
			List<Object> requestResponseBodyAdvice) {

		super(converters, null, requestResponseBodyAdvice);
	}

	/**
	 * Complete constructor for resolving {@code HttpEntity} and handling
	 * {@code ResponseEntity}.
	 */
	public HttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager manager, List<Object> requestResponseBodyAdvice) {

		super(converters, manager, requestResponseBodyAdvice);
	}


	/**
	 * 解析带有{@link HttpEntity}或者{@link RequestEntity}注解的方法参数
	 *
	 * @param parameter the method parameter to check
	 * @return
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (HttpEntity.class == parameter.getParameterType() ||
				RequestEntity.class == parameter.getParameterType());
	}

	/**
	 * 处理返回值类型是{@link HttpEntity}或者{@link RequestEntity}的返回值
	 *
	 * @param returnType the method return type to check
	 * @return
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return (HttpEntity.class.isAssignableFrom(returnType.getParameterType()) &&
				!RequestEntity.class.isAssignableFrom(returnType.getParameterType()));
	}

	/**
	 * 将请求内容转换成参数指定的泛型类型，并封装成{@link HttpEntity<T>}或者{@link RequestEntity<T>}类型的实例
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
	 * @throws IOException
	 * @throws HttpMediaTypeNotSupportedException
	 */
	@Override
	@Nullable
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory)
			throws IOException, HttpMediaTypeNotSupportedException {

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		/**
		 * 获取参数指定的泛型类型 {@link HttpEntity<T>}或者{@link RequestEntity<T>}中的"T"
		 */
		Type paramType = getHttpEntityType(parameter);
		if (paramType == null) {
			throw new IllegalArgumentException("HttpEntity parameter '" + parameter.getParameterName() +
					"' in method " + parameter.getMethod() + " is not parameterized");
		}

		/**
		 * 将请求内容转换成参数指定的泛型类型，并封装成{@link HttpEntity<T>}或者{@link RequestEntity<T>}类型的实例
		 */
		Object body = readWithMessageConverters(webRequest, parameter, paramType);
		if (RequestEntity.class == parameter.getParameterType()) {
			return new RequestEntity<>(body, inputMessage.getHeaders(),
					inputMessage.getMethod(), inputMessage.getURI());
		}
		else {
			return new HttpEntity<>(body, inputMessage.getHeaders());
		}
	}

	/**
	 * 获取方法参数的泛型类型
	 *
	 * @param parameter
	 * @return
	 */
	@Nullable
	private Type getHttpEntityType(MethodParameter parameter) {
		Assert.isAssignable(HttpEntity.class, parameter.getParameterType());
		Type parameterType = parameter.getGenericParameterType();
		if (parameterType instanceof ParameterizedType) {
			ParameterizedType type = (ParameterizedType) parameterType;
			if (type.getActualTypeArguments().length != 1) {
				throw new IllegalArgumentException("Expected single generic parameter on '" +
						parameter.getParameterName() + "' in method " + parameter.getMethod());
			}
			return type.getActualTypeArguments()[0];
		}
		else if (parameterType instanceof Class) {
			return Object.class;
		}
		else {
			return null;
		}
	}

	/**
	 * 将指定的泛型返回值，写入响应体
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

		mavContainer.setRequestHandled(true);
		if (returnValue == null) {
			return;
		}

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);

		Assert.isInstanceOf(HttpEntity.class, returnValue);
		HttpEntity<?> responseEntity = (HttpEntity<?>) returnValue;

		HttpHeaders outputHeaders = outputMessage.getHeaders();
		HttpHeaders entityHeaders = responseEntity.getHeaders();
		/**
		 * 设置响应头
		 */
		if (!entityHeaders.isEmpty()) {
			entityHeaders.forEach((key, value) -> {
				if (HttpHeaders.VARY.equals(key) && outputHeaders.containsKey(HttpHeaders.VARY)) {
					/**
					 * 从请求中获取响应中不存在的{@link HttpHeaders#VARY}参数值
					 */
					List<String> values = getVaryRequestHeadersToAdd(outputHeaders, entityHeaders);
					if (!values.isEmpty()) {
						outputHeaders.setVary(values);
					}
				}
				else {
					outputHeaders.put(key, value);
				}
			});
		}

		if (responseEntity instanceof ResponseEntity) {
			/**
			 * 获取响应的HTTP状态代码。
			 */
			int returnStatus = ((ResponseEntity<?>) responseEntity).getStatusCodeValue();
			outputMessage.getServletResponse().setStatus(returnStatus);
			if (returnStatus == 200) {
				HttpMethod method = inputMessage.getMethod();
				/**
				 * 请求方法是{@link HttpMethod#GET}或者{@link HttpMethod#HEAD}时，检查请求的资源是否修改过
				 */
				if ((HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method))
						&& isResourceNotModified(inputMessage, outputMessage)) {
					/**
					 * 如果请求的资源未修改过，快捷推出，不需要后续处理
					 * 写入响应内容
					 */
					outputMessage.flush();
					return;
				}
			}
			/**
			 * 3xx重定向
			 */
			else if (returnStatus / 100 == 3) {
				String location = outputHeaders.getFirst("location");
				if (location != null) {
					/**
					 * 将参数保存到{@link org.springframework.web.servlet.FlashMap}
					 * {@link org.springframework.web.servlet.FlashMap}在重定向时保存请求的参数值
					 */
					saveFlashAttributes(mavContainer, webRequest, location);
				}
			}
		}

		// Try even with null body. ResponseBodyAdvice could get involved.
		/**
		 * 试着用空体。ResponseBodyAdvice可以参与进来。
		 */
		writeWithMessageConverters(responseEntity.getBody(), returnType, inputMessage, outputMessage);

		// Ensure headers are flushed even if no body was written.
		/**
		 * 确保即使没有写入正文，也会刷新标头。
		 */
		outputMessage.flush();
	}

	/**
	 * 返回请求中存在而响应中不存在的{@link HttpHeaders#VARY}属性
	 *
	 * @param responseHeaders
	 * @param entityHeaders
	 * @return
	 */
	private List<String> getVaryRequestHeadersToAdd(HttpHeaders responseHeaders, HttpHeaders entityHeaders) {
		List<String> entityHeadersVary = entityHeaders.getVary();
		List<String> vary = responseHeaders.get(HttpHeaders.VARY);
		if (vary != null) {
			List<String> result = new ArrayList<>(entityHeadersVary);
			for (String header : vary) {
				for (String existing : StringUtils.tokenizeToStringArray(header, ",")) {
					if ("*".equals(existing)) {
						return Collections.emptyList();
					}
					/**
					 * 删除请求和响应中都有的参数值
					 */
					for (String value : entityHeadersVary) {
						if (value.equalsIgnoreCase(existing)) {
							result.remove(value);
						}
					}
				}
			}
			return result;
		}
		return entityHeadersVary;
	}

	/**
	 * 检查请求的资源是否修改过
	 *
	 * @param request
	 * @param response
	 * @return
	 */
	private boolean isResourceNotModified(ServletServerHttpRequest request, ServletServerHttpResponse response) {
		ServletWebRequest servletWebRequest =
				new ServletWebRequest(request.getServletRequest(), response.getServletResponse());
		HttpHeaders responseHeaders = response.getHeaders();
		String etag = responseHeaders.getETag();
		long lastModifiedTimestamp = responseHeaders.getLastModified();
		if (request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD) {
			responseHeaders.remove(HttpHeaders.ETAG);
			responseHeaders.remove(HttpHeaders.LAST_MODIFIED);
		}

		/**
		 * 检查请求的资源是否修改过
		 */
		return servletWebRequest.checkNotModified(etag, lastModifiedTimestamp);
	}

	private void saveFlashAttributes(ModelAndViewContainer mav, NativeWebRequest request, String location) {
		mav.setRedirectModelScenario(true);
		ModelMap model = mav.getModel();
		if (model instanceof RedirectAttributes) {
			Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
			if (!CollectionUtils.isEmpty(flashAttributes)) {
				HttpServletRequest req = request.getNativeRequest(HttpServletRequest.class);
				HttpServletResponse res = request.getNativeResponse(HttpServletResponse.class);
				if (req != null) {
					RequestContextUtils.getOutputFlashMap(req).putAll(flashAttributes);
					if (res != null) {
						RequestContextUtils.saveOutputFlashMap(location, req, res);
					}
				}
			}
		}
	}

	@Override
	protected Class<?> getReturnValueType(@Nullable Object returnValue, MethodParameter returnType) {
		if (returnValue != null) {
			return returnValue.getClass();
		}
		else {
			Type type = getHttpEntityType(returnType);
			type = (type != null ? type : Object.class);
			return ResolvableType.forMethodParameter(returnType, type).toClass();
		}
	}

}
