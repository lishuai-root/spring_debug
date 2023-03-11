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

package org.springframework.web.servlet;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.lang.Nullable;

/**
 * Interface to be implemented by objects that define a mapping between
 * requests and handler objects.
 * 由定义请求和处理程序对象之间映射的对象实现的接口。
 *
 * <p>This class can be implemented by application developers, although this is not
 * necessary, as {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping}
 * and {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}
 * are included in the framework. The former is the default if no
 * HandlerMapping bean is registered in the application context.
 * 这个类可以由应用程序开发人员实现，尽管这不是必需的，
 * 如{@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping}和{@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}。
 * 如果在应用程序上下文中没有注册HandlerMapping bean，则前者是默认值。
 *
 *
 * <p>HandlerMapping implementations can support mapped interceptors but do not
 * have to. A handler will always be wrapped in a {@link HandlerExecutionChain}
 * instance, optionally accompanied by some {@link HandlerInterceptor} instances.
 * HandlerMapping实现可以支持映射的拦截器，但不是必须这样做。
 * 处理程序总是被包装在一个{@link HandlerExecutionChain}实例中，可选地伴随着一些{@link HandlerInterceptor}实例。
 *
 * The DispatcherServlet will first call each HandlerInterceptor's
 * {@code preHandle} method in the given order, finally invoking the handler
 * itself if all {@code preHandle} methods have returned {@code true}.
 * DispatcherServlet将首先按照给定的顺序调用每个HandlerInterceptor的{@code preHandle}方法，
 * 最后如果所有{@code preHandle}方法都返回{@code true}，则调用处理程序本身。
 *
 *
 * <p>The ability to parameterize this mapping is a powerful and unusual
 * capability of this MVC framework. For example, it is possible to write
 * a custom mapping based on session state, cookie state or many other
 * variables. No other MVC framework seems to be equally flexible.
 * 参数化这个映射的能力是这个MVC框架的一个强大而不寻常的功能。
 * 例如，可以根据会话状态、cookie状态或许多其他变量编写自定义映射。似乎没有其他MVC框架具有同样的灵活性。
 *
 *
 * <p>Note: Implementations can implement the {@link org.springframework.core.Ordered}
 * interface to be able to specify a sorting order and thus a priority for getting
 * applied by DispatcherServlet. Non-Ordered instances get treated as lowest priority.
 * 注意:实现可以实现{@link org.springframework.core.Ordered}接口能够指定排序顺序，
 * 从而指定DispatcherServlet应用的优先级。非有序实例被视为最低优先级。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.core.Ordered
 * @see org.springframework.web.servlet.handler.AbstractHandlerMapping
 * @see org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping
 * @see org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
 */
public interface HandlerMapping {

	/**
	 * Name of the {@link HttpServletRequest} attribute that contains the mapped
	 * handler for the best matching pattern.
	 * 包含最佳匹配模式的映射处理程序的{@link HttpServletRequest}属性的名称。
	 *
	 * @since 4.3.21
	 */
	String BEST_MATCHING_HANDLER_ATTRIBUTE = HandlerMapping.class.getName() + ".bestMatchingHandler";

	/**
	 * Name of the {@link HttpServletRequest} attribute that contains the path
	 * used to look up the matching handler, which depending on the configured
	 * {@link org.springframework.web.util.UrlPathHelper} could be the full path
	 * or without the context path, decoded or not, etc.
	 * @since 5.2
	 * @deprecated as of 5.3 in favor of
	 * {@link org.springframework.web.util.UrlPathHelper#PATH_ATTRIBUTE} and
	 * {@link org.springframework.web.util.ServletRequestPathUtils#PATH_ATTRIBUTE}.
	 * To access the cached path used for request mapping, use
	 * {@link org.springframework.web.util.ServletRequestPathUtils#getCachedPathValue(ServletRequest)}.
	 */
	@Deprecated
	String LOOKUP_PATH = HandlerMapping.class.getName() + ".lookupPath";

	/**
	 * Name of the {@link HttpServletRequest} attribute that contains the path
	 * within the handler mapping, in case of a pattern match, or the full
	 * relevant URI (typically within the DispatcherServlet's mapping) else.
	 * 在模式匹配的情况下，包含处理程序映射中的路径的{@link HttpServletRequest}属性的名称，
	 * 或者完整的相关URI(通常在DispatcherServlet的映射中)else。
	 *
	 * <p>Note: This attribute is not required to be supported by all
	 * HandlerMapping implementations. URL-based HandlerMappings will
	 * typically support it, but handlers should not necessarily expect
	 * this request attribute to be present in all scenarios.
	 * 注意:并非所有HandlerMapping实现都必须支持此属性。
	 * 基于url的HandlerMappings通常会支持它，但处理程序不一定期望在所有场景中都出现这个请求属性。
	 *
	 */
	String PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE = HandlerMapping.class.getName() + ".pathWithinHandlerMapping";

	/**
	 * Name of the {@link HttpServletRequest} attribute that contains the
	 * best matching pattern within the handler mapping.
	 * 包含处理程序映射中最佳匹配模式的{@link HttpServletRequest}属性的名称。
	 *
	 * <p>Note: This attribute is not required to be supported by all
	 * HandlerMapping implementations. URL-based HandlerMappings will
	 * typically support it, but handlers should not necessarily expect
	 * this request attribute to be present in all scenarios.
	 * 注意:并非所有HandlerMapping实现都必须支持此属性。
	 * 基于url的HandlerMappings通常会支持它，但处理程序不一定期望在所有场景中都出现这个请求属性。
	 *
	 */
	String BEST_MATCHING_PATTERN_ATTRIBUTE = HandlerMapping.class.getName() + ".bestMatchingPattern";

	/**
	 * Name of the boolean {@link HttpServletRequest} attribute that indicates
	 * whether type-level mappings should be inspected.
	 * <p>Note: This attribute is not required to be supported by all
	 * HandlerMapping implementations.
	 */
	String INTROSPECT_TYPE_LEVEL_MAPPING = HandlerMapping.class.getName() + ".introspectTypeLevelMapping";

	/**
	 * Name of the {@link HttpServletRequest} attribute that contains the URI
	 * templates map, mapping variable names to values.
	 * 包含URI模板映射的{@link HttpServletRequest}属性的名称，将变量名映射到值。
	 *
	 * <p>Note: This attribute is not required to be supported by all
	 * HandlerMapping implementations. URL-based HandlerMappings will
	 * typically support it, but handlers should not necessarily expect
	 * this request attribute to be present in all scenarios.
	 * 注意:并非所有HandlerMapping实现都必须支持此属性。
	 * 基于url的HandlerMappings通常会支持它，但处理程序不一定期望在所有场景中都出现这个请求属性。
	 *
	 */
	String URI_TEMPLATE_VARIABLES_ATTRIBUTE = HandlerMapping.class.getName() + ".uriTemplateVariables";

	/**
	 * Name of the {@link HttpServletRequest} attribute that contains a map with
	 * URI variable names and a corresponding MultiValueMap of URI matrix
	 * variables for each.
	 * {@link HttpServletRequest}属性的名称，该属性包含一个包含URI变量名的映射，以及每个URI矩阵变量的对应MultiValueMap。
	 *
	 * <p>Note: This attribute is not required to be supported by all
	 * HandlerMapping implementations and may also not be present depending on
	 * whether the HandlerMapping is configured to keep matrix variable content
	 *
	 * 注意:此属性不要求所有HandlerMapping实现都支持，也可能不存在，这取决于HandlerMapping是否配置为保留矩阵变量内容
	 */
	String MATRIX_VARIABLES_ATTRIBUTE = HandlerMapping.class.getName() + ".matrixVariables";

	/**
	 * Name of the {@link HttpServletRequest} attribute that contains the set of
	 * producible MediaTypes applicable to the mapped handler.
	 * {@link HttpServletRequest}属性的名称，该属性包含适用于映射处理程序的可生产MediaTypes集。
	 *
	 * <p>Note: This attribute is not required to be supported by all
	 * HandlerMapping implementations. Handlers should not necessarily expect
	 * this request attribute to be present in all scenarios.
	 * 注意:并非所有HandlerMapping实现都必须支持此属性。处理程序不一定期望在所有场景中都存在此请求属性。
	 *
	 */
	String PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE = HandlerMapping.class.getName() + ".producibleMediaTypes";


	/**
	 * Whether this {@code HandlerMapping} instance has been enabled to use parsed
	 * {@link org.springframework.web.util.pattern.PathPattern}s in which case
	 * the {@link DispatcherServlet} automatically
	 * {@link org.springframework.web.util.ServletRequestPathUtils#parseAndCache parses}
	 * the {@code RequestPath} to make it available for
	 * {@link org.springframework.web.util.ServletRequestPathUtils#getParsedRequestPath
	 * access} in {@code HandlerMapping}s, {@code HandlerInterceptor}s, and
	 * other components.
	 * @since 5.3
	 */
	default boolean usesPathPatterns() {
		return false;
	}

	/**
	 * Return a handler and any interceptors for this request. The choice may be made
	 * on request URL, session state, or any factor the implementing class chooses.
	 * 返回此请求的处理程序和任何拦截器。可以根据请求URL、会话状态或实现类选择的任何因素进行选择。
	 *
	 * <p>The returned HandlerExecutionChain contains a handler Object, rather than
	 * even a tag interface, so that handlers are not constrained in any way.
	 * For example, a HandlerAdapter could be written to allow another framework's
	 * handler objects to be used.
	 * 返回的HandlerExecutionChain包含一个处理程序对象，而不是一个标记接口，因此处理程序不以任何方式受到约束。
	 * 例如，可以编写HandlerAdapter来允许使用另一个框架的处理程序对象。
	 *
	 * <p>Returns {@code null} if no match was found. This is not an error.
	 * The DispatcherServlet will query all registered HandlerMapping beans to find
	 * a match, and only decide there is an error if none can find a handler.
	 * 如果没有找到匹配，则返回{@code null}。这不是错误。
	 * DispatcherServlet将查询所有已注册的HandlerMapping bean以找到匹配的bean，只有在没有一个bean可以找到处理程序时才判断是否存在错误。
	 *
	 * @param request current HTTP request
	 * @return a HandlerExecutionChain instance containing handler object and
	 * any interceptors, or {@code null} if no mapping found
	 * @throws Exception if there is an internal error
	 */
	@Nullable
	HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception;

}
