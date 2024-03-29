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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

/**
 * {@link ServerHttpRequest} implementation that accesses one part of a multipart
 * request. If using {@link MultipartResolver} configuration the part is accessed
 * through a {@link MultipartFile}. Or if using Servlet 3.0 multipart processing
 * the part is accessed through {@code ServletRequest.getPart}.
 *
 * {@link ServerHttpRequest}实现访问多部分请求的一部分。
 * 如果使用{@link MultipartResolver}配置，该部件将通过{@link MultipartFile}访问。
 * 或者如果使用Servlet 3.0多部分处理，则通过{@code ServletRequest.getPart}访问该部分。
 *
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class RequestPartServletServerHttpRequest extends ServletServerHttpRequest {

	private final MultipartHttpServletRequest multipartRequest;

	private final String requestPartName;

	private final HttpHeaders multipartHeaders;


	/**
	 * 创建实例，并检查是否存在指定名称的多部份内容
	 *
	 * Create a new {@code RequestPartServletServerHttpRequest} instance.
	 * 创建一个新的{@code RequestPartServletServerHttpRequest}实例。
	 *
	 * @param request the current servlet request
	 * @param requestPartName the name of the part to adapt to the {@link ServerHttpRequest} contract
	 * @throws MissingServletRequestPartException if the request part cannot be found
	 * @throws MultipartException if MultipartHttpServletRequest cannot be initialized
	 */
	public RequestPartServletServerHttpRequest(HttpServletRequest request, String requestPartName)
			throws MissingServletRequestPartException {

		super(request);

		this.multipartRequest = MultipartResolutionDelegate.asMultipartHttpServletRequest(request);
		this.requestPartName = requestPartName;

		/**
		 * 获取指定名称多部份的头名称，
		 * 如果是上传文件，返回的是文件的内容类型
		 * 如果是表单项，返回的是表单项的头名称集合
		 */
		HttpHeaders multipartHeaders = this.multipartRequest.getMultipartHeaders(requestPartName);

		/**
		 * 如果请求中没有指定名称的多部份内容，抛出异常
		 */
		if (multipartHeaders == null) {
			throw new MissingServletRequestPartException(requestPartName);
		}
		this.multipartHeaders = multipartHeaders;
	}


	@Override
	public HttpHeaders getHeaders() {
		return this.multipartHeaders;
	}

	/**
	 * 获取请求中指定名称的表单项或者请求参数，并返回其输入流，如果获取不到抛出异常
	 *
	 * @return
	 * @throws IOException
	 */
	@Override
	public InputStream getBody() throws IOException {
		// Prefer Servlet Part resolution to cover file as well as parameter streams
		/**
		 * 选择Servlet部件解析来覆盖文件和参数流
		 */
		boolean servletParts = (this.multipartRequest instanceof StandardMultipartHttpServletRequest);

		/**
		 * 如果请求是表单上传请求，获取指定名称的表单项输入流
		 */
		if (servletParts) {
			Part part = retrieveServletPart();
			if (part != null) {
				return part.getInputStream();
			}
		}

		// Spring-style distinction between MultipartFile and String parameters
		/**
		 * MultipartFile和String参数之间的spring风格区别
		 * 尝试获取指定名称的上传文件输入流
		 */
		MultipartFile file = this.multipartRequest.getFile(this.requestPartName);
		if (file != null) {
			return file.getInputStream();
		}
		/**
		 * 尝试获取指定名称的请求参数，并转换成字节输入流
		 */
		String paramValue = this.multipartRequest.getParameter(this.requestPartName);
		if (paramValue != null) {
			return new ByteArrayInputStream(paramValue.getBytes(determineCharset()));
		}

		// Fallback: Servlet Part resolution even if not indicated
		/**
		 * 回退:Servlet部件解析，即使没有指出
		 * 最后，即使请求不是上传表单请求，也尝试获取表单项
		 */
		if (!servletParts) {
			Part part = retrieveServletPart();
			if (part != null) {
				return part.getInputStream();
			}
		}

		/**
		 * 如果请求中没有指定名称的表单项和参数，抛出异常
		 */
		throw new IllegalStateException("No body available for request part '" + this.requestPartName + "'");
	}

	@Nullable
	private Part retrieveServletPart() {
		try {
			return this.multipartRequest.getPart(this.requestPartName);
		}
		catch (Exception ex) {
			throw new MultipartException("Failed to retrieve request part '" + this.requestPartName + "'", ex);
		}
	}

	private Charset determineCharset() {
		MediaType contentType = getHeaders().getContentType();
		if (contentType != null) {
			Charset charset = contentType.getCharset();
			if (charset != null) {
				return charset;
			}
		}
		String encoding = this.multipartRequest.getCharacterEncoding();
		return (encoding != null ? Charset.forName(encoding) : FORM_CHARSET);
	}

}
