/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * MVC View for a web interaction. Implementations are responsible for rendering
 * content, and exposing the model. A single view exposes multiple model attributes.
 *
 * <p>This class and the MVC approach associated with it is discussed in Chapter 12 of
 * <a href="https://www.amazon.com/exec/obidos/tg/detail/-/0764543857/">Expert One-On-One J2EE Design and Development</a>
 * by Rod Johnson (Wrox, 2002).
 *
 * <p>View implementations may differ widely. An obvious implementation would be
 * JSP-based. Other implementations might be XSLT-based, or use an HTML generation library.
 * This interface is designed to avoid restricting the range of possible implementations.
 *
 * <p>Views should be beans. They are likely to be instantiated as beans by a ViewResolver.
 * As this interface is stateless, view implementations should be thread-safe.
 *
 * @author Rod Johnson
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @see org.springframework.web.servlet.view.AbstractView
 * @see org.springframework.web.servlet.view.InternalResourceView
 */
public interface View {

	/**
	 * Name of the {@link HttpServletRequest} attribute that contains the response status code.
	 * 包含响应状态代码的{@link HttpServletRequest}属性名。
	 *
	 * <p>Note: This attribute is not required to be supported by all View implementations.
	 * 注意:此属性并不要求所有View实现都支持。
	 *
	 * @since 3.0
	 */
	String RESPONSE_STATUS_ATTRIBUTE = View.class.getName() + ".responseStatus";

	/**
	 * Name of the {@link HttpServletRequest} attribute that contains a Map with path variables.
	 * 包含带有路径变量的Map的{@link HttpServletRequest}属性的名称。
	 *
	 * The map consists of String-based URI template variable names as keys and their corresponding
	 * Object-based values -- extracted from segments of the URL and type converted.
	 * 映射由作为键的基于字符串的URI模板变量名及其对应的基于对象的值组成——从URL段中提取并转换类型。
	 *
	 * <p>Note: This attribute is not required to be supported by all View implementations.
	 * <p>注意:该属性并不要求所有View实现都支持。
	 *
	 * @since 3.1
	 */
	String PATH_VARIABLES = View.class.getName() + ".pathVariables";

	/**
	 * The {@link org.springframework.http.MediaType} selected during content negotiation,
	 * which may be more specific than the one the View is configured with. For example:
	 * "application/vnd.example-v1+xml" vs "application/*+xml".
	 * @since 3.2
	 */
	String SELECTED_CONTENT_TYPE = View.class.getName() + ".selectedContentType";


	/**
	 * Return the content type of the view, if predetermined.
	 * <p>Can be used to check the view's content type upfront,
	 * i.e. before an actual rendering attempt.
	 * @return the content type String (optionally including a character set),
	 * or {@code null} if not predetermined
	 */
	@Nullable
	default String getContentType() {
		return null;
	}

	/**
	 * Render the view given the specified model.
	 * 根据指定的模型呈现视图。
	 *
	 * <p>The first step will be preparing the request: In the JSP case, this would mean
	 * setting model objects as request attributes. The second step will be the actual
	 * rendering of the view, for example including the JSP via a RequestDispatcher.
	 * 第一步是准备请求:在JSP案例中，这意味着将模型对象设置为请求属性。
	 * 第二步是视图的实际呈现，例如通过RequestDispatcher包含JSP。
	 *
	 * @param model a Map with name Strings as keys and corresponding model
	 * objects as values (Map can also be {@code null} in case of empty model)
	 * @param request current HTTP request
	 * @param response he HTTP response we are building
	 * @throws Exception if rendering failed
	 */
	void render(@Nullable Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
			throws Exception;

}
