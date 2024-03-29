/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.multipart;

import jakarta.servlet.http.HttpServletRequest;

/**
 * A strategy interface for multipart file upload resolution in accordance
 * with <a href="https://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>.
 * Implementations are typically usable both within an application context
 * and standalone.
 * 根据< A href="https:www.ietf.orgrfcrfc1867.txt">RFC 1867< A >的多部分文件上传解析策略接口。
 * 实现通常可以在应用程序上下文中使用，也可以独立使用。
 *
 * <p>There are two concrete implementations included in Spring, as of Spring 3.1:
 * 从Spring 3.1开始，Spring包含了两个具体的实现:
 * <ul>
 * <li>{@link org.springframework.web.multipart.commons.CommonsMultipartResolver}
 * for Apache Commons FileUpload
 * <li>{@link org.springframework.web.multipart.support.StandardServletMultipartResolver}
 * for the Servlet 3.0+ Part API
 * </ul>
 *
 * <p>There is no default resolver implementation used for Spring
 * {@link org.springframework.web.servlet.DispatcherServlet DispatcherServlets},
 * as an application might choose to parse its multipart requests itself. To define
 * an implementation, create a bean with the id "multipartResolver" in a
 * {@link org.springframework.web.servlet.DispatcherServlet DispatcherServlet's}
 * application context. Such a resolver gets applied to all requests handled
 * by that {@link org.springframework.web.servlet.DispatcherServlet}.
 *
 * Spring {@link org.springframework.web.servlet.DispatcherServlet}没有使用默认的解析器实现，因为应用程序可能会选择自己解析它的多部分请求。
 * 为了定义一个实现，在{@link org.springframework.web.servlet.DispatcherServlet}的应用程序上下文中创建一个id为“multipartResolver”的bean。
 * 这样的解析器应用于由该{@link org.springframework.web.servlet.DispatcherServlet}处理的所有请求。
 *
 *
 *
 * <p>If a {@link org.springframework.web.servlet.DispatcherServlet} detects a
 * multipart request, it will resolve it via the configured {@link MultipartResolver}
 * and pass on a wrapped {@link jakarta.servlet.http.HttpServletRequest}. Controllers
 * can then cast their given request to the {@link MultipartHttpServletRequest}
 * interface, which allows for access to any {@link MultipartFile MultipartFiles}.
 * Note that this cast is only supported in case of an actual multipart request.
 *
 * 如果一个{@link org.springframework.web.servlet.DispatcherServlet}检测到一个多部分请求，
 * 它将通过配置的{@link MultipartResolver}解析它，并传递一个包装的{@link jakarta.servlet.http.HttpServletRequest}。
 * 然后，控制器可以将给定的请求转换到{@link MultipartHttpServletRequest}接口，该接口允许访问任何{@link MultipartFile MultipartFiles}。
 * 注意，这种类型转换仅在实际的多部分请求的情况下支持。
 *
 *
 * <pre class="code">
 * public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) {
 *   MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
 *   MultipartFile multipartFile = multipartRequest.getFile("image");
 *   ...
 * }</pre>
 *
 * Instead of direct access, command or form controllers can register a
 * {@link org.springframework.web.multipart.support.ByteArrayMultipartFileEditor}
 * or {@link org.springframework.web.multipart.support.StringMultipartFileEditor}
 * with their data binder, to automatically apply multipart content to form
 * bean properties.
 *
 * 而不是直接访问，命令或表单控制器可以注册一个{@link org.springframework.web.multipart.support.ByteArrayMultipartFileEditor}
 * 或{@link org.springframework.web.multipart.support.StringMultipartFileEditor}和它们的数据绑定器，
 * 以自动应用多部分内容来形成bean属性。
 *
 *
 * <p>As an alternative to using a {@link MultipartResolver} with a
 * {@link org.springframework.web.servlet.DispatcherServlet},
 * a {@link org.springframework.web.multipart.support.MultipartFilter} can be
 * registered in {@code web.xml}. It will delegate to a corresponding
 * {@link MultipartResolver} bean in the root application context. This is mainly
 * intended for applications that do not use Spring's own web MVC framework.
 *
 * 作为使用{@link MultipartResolver}和{@link org.springframework.web.servlet.DispatcherServlet}的替代方案，
 * 一个{@link org.springframework.web.multipart.support.MultipartFilter}可以在{@code web.xml}中注册。
 * 它将委托给根应用程序上下文中对应的{@link MultipartResolver} bean。这主要适用于不使用Spring自己的web MVC框架的应用程序。
 *
 *
 * <p>Note: There is hardly ever a need to access the {@link MultipartResolver}
 * itself from application code. It will simply do its work behind the scenes,
 * making {@link MultipartHttpServletRequest MultipartHttpServletRequests}
 * available to controllers.
 *
 * 注意:几乎不需要从应用程序代码中访问{@link MultipartResolver}本身。它将简单地在幕后完成它的工作，
 * 使{@link MultipartHttpServletRequest MultipartHttpServletRequest}对控制器可用。
 *
 *
 * @author Juergen Hoeller
 * @author Trevor D. Cook
 * @since 29.09.2003
 * @see MultipartHttpServletRequest
 * @see MultipartFile
 * @see org.springframework.web.multipart.commons.CommonsMultipartResolver
 * @see org.springframework.web.multipart.support.ByteArrayMultipartFileEditor
 * @see org.springframework.web.multipart.support.StringMultipartFileEditor
 * @see org.springframework.web.servlet.DispatcherServlet
 */
public interface MultipartResolver {

	/**
	 * Determine if the given request contains multipart content.
	 * 确定给定的请求是否包含多部分内容。
	 *
	 * <p>Will typically check for content type "multipart/form-data", but the actually
	 * accepted requests might depend on the capabilities of the resolver implementation.
	 * 通常会检查内容类型“multipart/form-data”，但实际接受的请求可能取决于解析器实现的能力。
	 *
	 * @param request the servlet request to be evaluated
	 * @return whether the request contains multipart content
	 */
	boolean isMultipart(HttpServletRequest request);

	/**
	 * Parse the given HTTP request into multipart files and parameters,
	 * and wrap the request inside a
	 * {@link org.springframework.web.multipart.MultipartHttpServletRequest}
	 * object that provides access to file descriptors and makes contained
	 * parameters accessible via the standard ServletRequest methods.
	 * 将给定的HTTP请求解析为多部分文件和参数，并将请求包装在一个{@link org.springframework.web.multipart.MultipartHttpServletRequest}对象，
	 * 该对象提供对文件描述符的访问，并通过标准ServletRequest方法访问所包含的参数。
	 *
	 * @param request the servlet request to wrap (must be of a multipart content type)
	 * @return the wrapped servlet request
	 * @throws MultipartException if the servlet request is not multipart, or if
	 * implementation-specific problems are encountered (such as exceeding file size limits)
	 * @see MultipartHttpServletRequest#getFile
	 * @see MultipartHttpServletRequest#getFileNames
	 * @see MultipartHttpServletRequest#getFileMap
	 * @see jakarta.servlet.http.HttpServletRequest#getParameter
	 * @see jakarta.servlet.http.HttpServletRequest#getParameterNames
	 * @see jakarta.servlet.http.HttpServletRequest#getParameterMap
	 */
	MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException;

	/**
	 * Cleanup any resources used for the multipart handling,
	 * like a storage for the uploaded files.
	 * @param request the request to cleanup resources for
	 */
	void cleanupMultipart(MultipartHttpServletRequest request);

}
