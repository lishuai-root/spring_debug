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

package org.springframework.web.multipart.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

/**
 * Standard implementation of the {@link MultipartResolver} interface,
 * based on the Servlet 3.0 {@link jakarta.servlet.http.Part} API.
 * To be added as "multipartResolver" bean to a Spring DispatcherServlet context,
 * without any extra configuration at the bean level (see below).
 * {@link MultipartResolver}接口的标准实现，基于Servlet 3.0。一部分}API。
 * 作为“multipartResolver”bean添加到Spring DispatcherServlet上下文中，而不需要在bean级别上进行任何额外配置(参见下面)。
 *
 *
 * <p>This resolver variant uses your Servlet container's multipart parser as-is,
 * potentially exposing the application to container implementation differences.
 * 这个解析器变体按原样使用Servlet容器的多部分解析器，可能会将应用程序暴露给容器实现的差异。
 *
 * See {@link org.springframework.web.multipart.commons.CommonsMultipartResolver}
 * for an alternative implementation using a local Commons FileUpload library
 * within the application, providing maximum portability across Servlet containers.
 * Also, see this resolver's configuration option for
 * {@link #setStrictServletCompliance strict Servlet compliance}, narrowing the
 * applicability of Spring's {@link MultipartHttpServletRequest} to form data only.
 * 参见{@link org.springframework.web.multipart.commons.CommonsMultipartResolver}获得在应用程序中使用本地Commons FileUpload库的替代实现，
 * 提供跨Servlet容器的最大可移植性。此外，请参阅这个解析器的配置选项{@link #setStrictServletCompliance 严格的Servlet遵从性}，
 * 缩小Spring的{@link MultipartHttpServletRequest}仅用于形成数据的适用性。
 *
 *
 * <p><b>Note:</b> In order to use Servlet 3.0 based multipart parsing,
 * you need to mark the affected servlet with a "multipart-config" section in
 * {@code web.xml}, or with a {@link jakarta.servlet.MultipartConfigElement}
 * in programmatic servlet registration, or (in case of a custom servlet class)
 * possibly with a {@link jakarta.servlet.annotation.MultipartConfig} annotation
 * on your servlet class. Configuration settings such as maximum sizes or
 * storage locations need to be applied at that servlet registration level;
 * Servlet 3.0 does not allow for them to be set at the MultipartResolver level.
 * <p><b>注意:<b>为了使用基于Servlet 3.0的多部分解析，你需要在{@code web.xml}中用"multipart-config"部分标记受影响的Servlet，
 * 或者用{@link jakarta.servlet.MultipartConfigElement}，
 * 或者(在自定义servlet类的情况下)可能使用{@link jakarta.servlet.annotation.MultipartConfig}。
 * 诸如最大大小或存储位置等配置设置需要应用于该servlet注册级别;Servlet 3.0不允许在MultipartResolver级别设置它们。
 *
 *
 * <pre class="code">
 * public class AppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {
 *	 // ...
 *	 &#064;Override
 *	 protected void customizeRegistration(ServletRegistration.Dynamic registration) {
 *     // Optionally also set maxFileSize, maxRequestSize, fileSizeThreshold
 *     registration.setMultipartConfig(new MultipartConfigElement("/tmp"));
 *   }
 * }
 * </pre>
 *
 * @author Juergen Hoeller
 * @since 3.1
 * @see #setResolveLazily
 * @see #setStrictServletCompliance
 * @see HttpServletRequest#getParts()
 * @see org.springframework.web.multipart.commons.CommonsMultipartResolver
 */
public class StandardServletMultipartResolver implements MultipartResolver {

	private boolean resolveLazily = false;

	private boolean strictServletCompliance = false;


	/**
	 * Set whether to resolve the multipart request lazily at the time of
	 * file or parameter access.
	 * <p>Default is "false", resolving the multipart elements immediately, throwing
	 * corresponding exceptions at the time of the {@link #resolveMultipart} call.
	 * Switch this to "true" for lazy multipart parsing, throwing parse exceptions
	 * once the application attempts to obtain multipart files or parameters.
	 * @since 3.2.9
	 */
	public void setResolveLazily(boolean resolveLazily) {
		this.resolveLazily = resolveLazily;
	}

	/**
	 * Specify whether this resolver should strictly comply with the Servlet
	 * specification, only kicking in for "multipart/form-data" requests.
	 * 指定这个解析器是否应该严格遵守Servlet规范，只适用于“multipart/form-data”请求。
	 *
	 * <p>Default is "false", trying to process any request with a "multipart/"
	 * content type as far as the underlying Servlet container supports it
	 * (which works on e.g. Tomcat but not on Jetty). For consistent portability
	 * and in particular for consistent custom handling of non-form multipart
	 * request types outside of Spring's {@link MultipartResolver} mechanism,
	 * switch this flag to "true": Only "multipart/form-data" requests will be
	 * wrapped with a {@link MultipartHttpServletRequest} then; other kinds of
	 * requests will be left as-is, allowing for custom processing in user code.
	 * 默认值为“false”，只要底层Servlet容器支持，就会尝试处理任何具有“multipart”内容类型的请求(这在Tomcat上有效，但在Jetty上无效)。
	 * 为了保持一致的可移植性，特别是为了在Spring的{@link MultipartResolver}机制之外对非表单多部分请求类型进行一致的自定义处理，
	 * 请将此标志切换为“true”:只有“multipartform-data”请求将被{@link MultipartHttpServletRequest}包装，
	 * 然后;其他类型的请求将保持原样，允许在用户代码中进行自定义处理。
	 *
	 * <p>Note that Commons FileUpload and therefore
	 * {@link org.springframework.web.multipart.commons.CommonsMultipartResolver}
	 * supports any "multipart/" request type. However, it restricts processing
	 * to POST requests which standard Servlet multipart parsers might not do.
	 * 注意Commons FileUpload，因此{@link org.springframework.web.multipart.commons.CommonsMultipartResolver}
	 * 支持任何“multipart”请求类型。但是，它将处理限制为POST请求，这是标准Servlet多部分解析器可能无法做到的。
	 *
	 * @since 5.3.9
	 */
	public void setStrictServletCompliance(boolean strictServletCompliance) {
		this.strictServletCompliance = strictServletCompliance;
	}


	@Override
	public boolean isMultipart(HttpServletRequest request) {
		return StringUtils.startsWithIgnoreCase(request.getContentType(),
				(this.strictServletCompliance ? MediaType.MULTIPART_FORM_DATA_VALUE : "multipart/"));
	}

	@Override
	public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException {
		return new StandardMultipartHttpServletRequest(request, this.resolveLazily);
	}

	@Override
	public void cleanupMultipart(MultipartHttpServletRequest request) {
		if (!(request instanceof AbstractMultipartHttpServletRequest) ||
				((AbstractMultipartHttpServletRequest) request).isResolved()) {
			// To be on the safe side: explicitly delete the parts,
			// but only actual file parts (for Resin compatibility)
			try {
				for (Part part : request.getParts()) {
					if (request.getFile(part.getName()) != null) {
						part.delete();
					}
				}
			}
			catch (Throwable ex) {
				LogFactory.getLog(getClass()).warn("Failed to perform cleanup of multipart items", ex);
			}
		}
	}

}
