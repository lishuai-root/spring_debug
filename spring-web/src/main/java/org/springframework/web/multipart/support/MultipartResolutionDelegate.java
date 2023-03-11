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

package org.springframework.web.multipart.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.util.WebUtils;

/**
 * A common delegate for {@code HandlerMethodArgumentResolver} implementations
 * which need to resolve {@link MultipartFile} and {@link Part} arguments.
 *
 * @author Juergen Hoeller
 * @since 4.3
 */
public final class MultipartResolutionDelegate {

	/**
	 * Indicates an unresolvable value.
	 * 无法解析的值。
	 */
	public static final Object UNRESOLVABLE = new Object();


	private MultipartResolutionDelegate() {
	}


	@Nullable
	public static MultipartRequest resolveMultipartRequest(NativeWebRequest webRequest) {
		MultipartRequest multipartRequest = webRequest.getNativeRequest(MultipartRequest.class);
		if (multipartRequest != null) {
			return multipartRequest;
		}
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		if (servletRequest != null && isMultipartContent(servletRequest)) {
			return new StandardMultipartHttpServletRequest(servletRequest);
		}
		return null;
	}

	public static boolean isMultipartRequest(HttpServletRequest request) {
		return (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null ||
				isMultipartContent(request));
	}

	private static boolean isMultipartContent(HttpServletRequest request) {
		String contentType = request.getContentType();
		return (contentType != null && contentType.toLowerCase().startsWith("multipart/"));
	}

	static MultipartHttpServletRequest asMultipartHttpServletRequest(HttpServletRequest request) {
		MultipartHttpServletRequest unwrapped = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
		if (unwrapped != null) {
			return unwrapped;
		}
		return new StandardMultipartHttpServletRequest(request);
	}


	/**
	 * 检查指定方法参数是否需要从表单项获取参数值
	 *
	 * @param parameter
	 * @return
	 */
	public static boolean isMultipartArgument(MethodParameter parameter) {
		Class<?> paramType = parameter.getNestedParameterType();
		/**
		 * 如果当前方法参数类型为以下几个类型之一，则返回true
		 * 1. 单个上传的文件
		 * 2. 上传的文件类型的集合
		 * 3. 上传的文件类型的数组
		 * 4. 单个上传的表单内容
		 * 5. 上传表单项类型的集合
		 * 6. 上传表单项类型的数组
		 */
		return (MultipartFile.class == paramType ||
				isMultipartFileCollection(parameter) || isMultipartFileArray(parameter) ||
				(Part.class == paramType || isPartCollection(parameter) || isPartArray(parameter)));
	}

	/**
	 * 解析请求中指定名称的表单项，如果找不到则返回{@link #UNRESOLVABLE}
	 *
	 * 当返回null时：
	 * 		1. 参数类型是{@link MultipartFile}或者{@link Part}类型，但是请求不是上传请求
	 * 		2. 参数类型是{@link MultipartFile}或者{@link Part}类型，请求是上传请求，但是没有指定名称的表单项
	 * 当返回{@link #UNRESOLVABLE}时：
	 * 		1. 参数类型不是{@link MultipartFile}和{@link Part}类型
	 *
	 * @param name
	 * @param parameter
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@Nullable
	public static Object resolveMultipartArgument(String name, MethodParameter parameter, HttpServletRequest request)
			throws Exception {

		/**
		 * 如果request对象是{@link MultipartHttpServletRequest}类型的请求，则强转为{@link MultipartHttpServletRequest}类型并返回
		 * 如果不是返回null
		 */
		MultipartHttpServletRequest multipartRequest =
				WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
		/**
		 * 判断request请求类型是否是以"multipart/"开头的
		 */
		boolean isMultipart = (multipartRequest != null || isMultipartContent(request));

		/**
		 * 如果参数类型是{@link MultipartFile}类型
		 */
		if (MultipartFile.class == parameter.getNestedParameterType()) {
			if (!isMultipart) {
				return null;
			}
			if (multipartRequest == null) {
				/**
				 * 创建上传请求对象，并解析表单项
				 */
				multipartRequest = new StandardMultipartHttpServletRequest(request);
			}
			/**
			 * 获取指定名称的上传文件
			 */
			return multipartRequest.getFile(name);
		}
		/**
		 * 如果参数类型是{@link MultipartFile}类型的集合
		 */
		else if (isMultipartFileCollection(parameter)) {
			if (!isMultipart) {
				return null;
			}
			if (multipartRequest == null) {
				multipartRequest = new StandardMultipartHttpServletRequest(request);
			}
			/**
			 * 获取指定名称的所有文件
			 */
			List<MultipartFile> files = multipartRequest.getFiles(name);
			return (!files.isEmpty() ? files : null);
		}
		/**
		 * 如果参数类型是{@link MultipartFile}类型的数组
		 */
		else if (isMultipartFileArray(parameter)) {
			if (!isMultipart) {
				return null;
			}
			if (multipartRequest == null) {
				multipartRequest = new StandardMultipartHttpServletRequest(request);
			}
			/**
			 * 获取所有指定名称的文件，并创建数组
			 */
			List<MultipartFile> files = multipartRequest.getFiles(name);
			return (!files.isEmpty() ? files.toArray(new MultipartFile[0]) : null);
		}
		/**
		 * 如果参数类型是{@link Part}表单项
		 */
		else if (Part.class == parameter.getNestedParameterType()) {
			if (!isMultipart) {
				return null;
			}
			/**
			 * 获取所有指定名称的表单项
			 */
			return request.getPart(name);
		}
		/**
		 * 如果参数类型是{@link Part}表单项集合
		 */
		else if (isPartCollection(parameter)) {
			if (!isMultipart) {
				return null;
			}
			/**
			 * 遍历所有表单项，获取指定名称表单项集合
			 */
			List<Part> parts = resolvePartList(request, name);
			return (!parts.isEmpty() ? parts : null);
		}
		/**
		 * 如果参数类型是{@link Part}表单项数组
		 */
		else if (isPartArray(parameter)) {
			if (!isMultipart) {
				return null;
			}
			/**
			 * 遍历所有表单项，获取指定名称表单项集合，并创建数组返回
			 */
			List<Part> parts = resolvePartList(request, name);
			return (!parts.isEmpty() ? parts.toArray(new Part[0]) : null);
		}
		else {
			return UNRESOLVABLE;
		}
	}

	/**
	 * 返回指定方法参数是否是泛型类型为{@link MultipartFile}的集合类型
	 *
	 * @param methodParam
	 * @return
	 */
	private static boolean isMultipartFileCollection(MethodParameter methodParam) {
		/**
		 * 返回当前方法参数是否是泛型类型为{@link MultipartFile}的集合类型
		 */
		return (MultipartFile.class == getCollectionParameterType(methodParam));
	}

	/**
	 * 返回指定方法参数是否{@link MultipartFile}类型的数组
	 *
	 * @param methodParam
	 * @return
	 */
	private static boolean isMultipartFileArray(MethodParameter methodParam) {
		return (MultipartFile.class == methodParam.getNestedParameterType().getComponentType());
	}

	/**
	 * 返回指定方法参数是否{@link Part}类型的集合
	 *
	 * @param methodParam
	 * @return
	 */
	private static boolean isPartCollection(MethodParameter methodParam) {
		return (Part.class == getCollectionParameterType(methodParam));
	}

	/**
	 * 返回指定方法参数是否{@link Part}类型的数组
	 *
	 * @param methodParam
	 * @return
	 */
	private static boolean isPartArray(MethodParameter methodParam) {
		return (Part.class == methodParam.getNestedParameterType().getComponentType());
	}

	/**
	 * 如果当前方法参数是集合类型，返回集合的泛型类型，如果不是集合类型，返回null
	 *
	 * @param methodParam
	 * @return
	 */
	@Nullable
	private static Class<?> getCollectionParameterType(MethodParameter methodParam) {
		Class<?> paramType = methodParam.getNestedParameterType();
		if (Collection.class == paramType || List.class.isAssignableFrom(paramType)){
			/**
			 * 如果当前类型是一个集合类型，解析当前方法参数的泛型类型
			 */
			Class<?> valueType = ResolvableType.forMethodParameter(methodParam).asCollection().resolveGeneric();
			if (valueType != null) {
				return valueType;
			}
		}
		return null;
	}

	/**
	 * 遍历所有表单项，返回指定名称表单项
	 *
	 * @param request
	 * @param name
	 * @return
	 * @throws Exception
	 */
	private static List<Part> resolvePartList(HttpServletRequest request, String name) throws Exception {
		Collection<Part> parts = request.getParts();
		List<Part> result = new ArrayList<>(parts.size());
		for (Part part : parts) {
			if (part.getName().equals(name)) {
				result.add(part);
			}
		}
		return result;
	}

}
