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

package org.springframework.web.method.annotation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;

/**
 * 用于解析带有{@link RequestParam}注解，但是没有指定{@link RequestParam#name()}参数，且方法参数类型为{@link Map}类型的方法参数
 * 可以看作是{@link RequestParamMethodArgumentResolver}解析器的补充
 *
 *
 * 根据参数类型创建{@link MultiValueMap}或者{@link Map}类型的参数
 *
 *
 * Resolves {@link Map} method arguments annotated with an @{@link RequestParam}
 * where the annotation does not specify a request parameter name.
 *
 * 解决使用@{@link RequestParam}注释的{@link Map}方法参数，其中注释没有指定请求参数名。
 *
 * <p>The created {@link Map} contains all request parameter name/value pairs,
 * or all multipart files for a given parameter name if specifically declared
 * with {@link MultipartFile} as the value type. If the method parameter type is
 * {@link MultiValueMap} instead, the created map contains all request parameters
 * and all their values for cases where request parameters have multiple values
 * (or multiple multipart files of the same name).
 *
 * 创建的{@link Map}包含所有请求参数name/value对，或者给定参数名的所有多部分文件(如果特别声明{@link MultipartFile}作为值类型)。
 * 如果方法参数类型改为{@link MultiValueMap}，则创建的映射包含所有请求参数及其所有值，用于请求参数有多个值(或多个同名的多部分文件)的情况。
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 * @see RequestParamMethodArgumentResolver
 * @see HttpServletRequest#getParameterMap()
 * @see MultipartRequest#getMultiFileMap()
 * @see MultipartRequest#getFileMap()
 */
public class RequestParamMapMethodArgumentResolver implements HandlerMethodArgumentResolver {

	/**
	 * 解析当有{@link RequestParam}注解，但是没有指定{@link RequestParam#name()}参数，且方法参数类型为{@link Map}类型的方法参数
	 *
	 * @param parameter the method parameter to check
	 * @return
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		/**
		 * 使用了{@link RequestParam}注解，且参数类型为{@link Map}，且未指定{@link RequestParam#name()} 参数
		 */
		return (requestParam != null && Map.class.isAssignableFrom(parameter.getParameterType()) &&
				!StringUtils.hasText(requestParam.name()));
	}

	/**
	 * 解析参数类型为{@link Map}，并且使用了{@link RequestParam}注解，且未指定{@link RequestParam#name()} 参数的方法参数
	 *
	 * 1. 如果参数类型为{@link MultiValueMap}类型
	 * 		1.1 如果{@link MultiValueMap}集合的泛型值是{@link MultipartFile}类型，且当前请求是上传请求，获取上传请求中的所有上传文件
	 * 		1.2 如果{@link MultiValueMap}集合的泛型值是{@link Part}类型，且当前请求是上传请求，获取上传请求中的所有表单项，并创建{@link LinkedMultiValueMap}集合返回
	 * 		1.3 如果{@link MultiValueMap}集合的泛型值不是前两项类型，获取请求中的所有参数，并创建{@link LinkedMultiValueMap}集合返回
	 * 2. 如果参数类型为{@link Map}类型
	 * 		2.1 同1.1
	 * 		2.2 同1.2，集合类型为{@link LinkedHashMap}，同名的表单项只取第一个
	 * 		2.3 同1.3，集合类型为{@link LinkedHashMap}，同名的参数只取第一个，如果参数值为空则参数名称也不会放进集合
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
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		/**
		 * {@link ResolvableType}是对java类型的封装，提供了对父类，父接口等操作的方法
		 */
		ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);

		/**
		 * 参数类型是否是一个多值映射的map类型
		 */
		if (MultiValueMap.class.isAssignableFrom(parameter.getParameterType())) {
			// MultiValueMap
			/**
			 * 多值映射
			 * 获取{@link MultiValueMap}中值的泛型，获取到的是{@link java.util.List}中的泛型
			 * 因为{@link MultiValueMap}已经指定了值的类型必须是{@link java.util.List}类型
			 *
			 * public interface MultiValueMap<K, V> extends Map<K, List<V>>
			 * 获取到的是"Map<K, List<V>>" 里面的"V"的类型
			 */
			Class<?> valueType = resolvableType.as(MultiValueMap.class).getGeneric(1).resolve();
			/**
			 * 如果MultiValueMap<K, List<V>>中"V"的类型是{@link MultipartFile}，从请求中获取上传的文件列表
			 */
			if (valueType == MultipartFile.class) {
				MultipartRequest multipartRequest = MultipartResolutionDelegate.resolveMultipartRequest(webRequest);
				/**
				 * 如果当前请求是上传请求，获取上传的文件列表，否则创建一个长度为0的空列表
				 */
				return (multipartRequest != null ? multipartRequest.getMultiFileMap() : new LinkedMultiValueMap<>(0));
			}
			/**
			 * 如果MultiValueMap<K, List<V>>中"V"的类型是{@link Part}，从请求中获取表单项
			 */
			else if (valueType == Part.class) {
				HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
				/**
				 * 如果当前请求是上传请求，请求的内容类型是否以"multipart/"开头
				 */
				if (servletRequest != null && MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
					/**
					 * 获取所有的表单项，以表单名称为key，表单项为value创建{@link LinkedMultiValueMap}并返回
					 */
					Collection<Part> parts = servletRequest.getParts();
					LinkedMultiValueMap<String, Part> result = new LinkedMultiValueMap<>(parts.size());
					for (Part part : parts) {
						result.add(part.getName(), part);
					}
					return result;
				}
				/**
				 * 如果不是上传请求，返回长度为0的空列表
				 */
				return new LinkedMultiValueMap<>(0);
			}
			else {
				/**
				 * 获取请求中所有参数，并创建{@link LinkedMultiValueMap}
				 */
				Map<String, String[]> parameterMap = webRequest.getParameterMap();
				MultiValueMap<String, String> result = new LinkedMultiValueMap<>(parameterMap.size());
				parameterMap.forEach((key, values) -> {
					for (String value : values) {
						result.add(key, value);
					}
				});
				return result;
			}
		}
		/**
		 * 如果方法参数类型是普通的map结构
		 */
		else {
			// Regular Map
			/**
			 * 普通地图
			 * 解析普通{@link Map}类型的value泛型
			 */
			Class<?> valueType = resolvableType.asMap().getGeneric(1).resolve();
			/**
			 * 如果{@link Map}的value泛型为{@link MultipartFile}类型，获取请求中所有上传的文件
			 */
			if (valueType == MultipartFile.class) {
				MultipartRequest multipartRequest = MultipartResolutionDelegate.resolveMultipartRequest(webRequest);
				/**
				 * 如果当前请求是上传请求，获取所有上传文件，否则返回空集合
				 */
				return (multipartRequest != null ? multipartRequest.getFileMap() : new LinkedHashMap<>(0));
			}
			/**
			 * 如果{@link Map}的value泛型为{@link Part}类型，获取请求中所有表单项
			 */
			else if (valueType == Part.class) {
				HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
				/**
				 * 如果当前请求是上传请求，获取所有表单项，否则返回空集合
				 * 如果有多个同名的表单项，只取第一个
				 */
				if (servletRequest != null && MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
					Collection<Part> parts = servletRequest.getParts();
					LinkedHashMap<String, Part> result = CollectionUtils.newLinkedHashMap(parts.size());
					for (Part part : parts) {
						/**
						 * 同名表单项，只取第一个
						 */
						if (!result.containsKey(part.getName())) {
							result.put(part.getName(), part);
						}
					}
					return result;
				}
				return new LinkedHashMap<>(0);
			}
			/**
			 * 获取请求中所有参数，并创建{@link LinkedHashMap}，如果一个名称对应多个值，只取第一个，如果参数值为空则参数名称也不会放进集合
			 */
			else {
				Map<String, String[]> parameterMap = webRequest.getParameterMap();
				Map<String, String> result = CollectionUtils.newLinkedHashMap(parameterMap.size());
				parameterMap.forEach((key, values) -> {
					if (values.length > 0) {
						result.put(key, values[0]);
					}
				});
				return result;
			}
		}
	}

}
