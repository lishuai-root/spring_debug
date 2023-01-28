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

package org.springframework.web.servlet.handler;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.core.Ordered;
import org.springframework.core.log.LogDelegateFactory;
import org.springframework.http.server.RequestPath;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PathMatcher;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.cors.*;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for {@link org.springframework.web.servlet.HandlerMapping}
 * implementations. Supports ordering, a default handler, handler interceptors,
 * including handler interceptors mapped by path patterns.
 *
 * <p>Note: This base class does <i>not</i> support exposure of the
 * {@link #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}. Support for this attribute
 * is up to concrete subclasses, typically based on request URL mappings.
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 07.04.2003
 * @see #getHandlerInternal
 * @see #setDefaultHandler
 * @see #setInterceptors
 * @see org.springframework.web.servlet.HandlerInterceptor
 */
public abstract class AbstractHandlerMapping extends WebApplicationObjectSupport
		implements HandlerMapping, Ordered, BeanNameAware {

	/** Dedicated "hidden" logger for request mappings. */
	protected final Log mappingsLogger =
			LogDelegateFactory.getHiddenLog(HandlerMapping.class.getName() + ".Mappings");


	/**
	 * 表示默认处理程序，即路径为"/*"的处理程序
	 */
	@Nullable
	private Object defaultHandler;

	@Nullable
	private PathPatternParser patternParser;

	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	private PathMatcher pathMatcher = new AntPathMatcher();

	private final List<Object> interceptors = new ArrayList<>();

	private final List<HandlerInterceptor> adaptedInterceptors = new ArrayList<>();

	@Nullable
	private CorsConfigurationSource corsConfigurationSource;

	private CorsProcessor corsProcessor = new DefaultCorsProcessor();

	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered

	@Nullable
	private String beanName;


	/**
	 * Set the default handler for this handler mapping.
	 * This handler will be returned if no specific mapping was found.
	 * 为此处理程序映射设置默认处理程序。如果没有找到特定的映射，则返回此处理程序。
	 *
	 * <p>Default is {@code null}, indicating no default handler.
	 * Default是{@code null}，表示没有默认处理程序。
	 */
	public void setDefaultHandler(@Nullable Object defaultHandler) {
		this.defaultHandler = defaultHandler;
	}

	/**
	 * Return the default handler for this handler mapping,
	 * or {@code null} if none.
	 *
	 * 返回此处理程序映射的默认处理程序，如果没有则返回{@code null}。
	 */
	@Nullable
	public Object getDefaultHandler() {
		return this.defaultHandler;
	}

	/**
	 * Enable use of pre-parsed {@link PathPattern}s as an alternative to
	 * String pattern matching with {@link AntPathMatcher}. The syntax is
	 * largely the same but the {@code PathPattern} syntax is more tailored for
	 * web applications, and its implementation is more efficient.
	 * 启用预解析{@link PathPattern}s作为String模式匹配{@link AntPathMatcher}的替代方案。
	 * 语法在很大程度上是相同的，但{@code PathPattern}语法更适合web应用程序，并且它的实现更有效。
	 *
	 * <p>This property is mutually exclusive with the following others which
	 * are effectively ignored when this is set:
	 * 此属性与以下其他属性互斥，当设置此属性时，这些属性将被有效忽略:
	 * <ul>
	 * <li>{@link #setAlwaysUseFullPath} -- {@code PathPatterns} always use the
	 * full path and ignore the servletPath/pathInfo which are decoded and
	 * partially normalized and therefore not comparable against the
	 * {@link HttpServletRequest#getRequestURI() requestURI}.
	 * {@link #setAlwaysUseFullPath}——{@code PathPatterns}总是使用完整路径，
	 * 忽略经过解码和部分规范化的servletPath/pathInfo，因此不能与{@link HttpServletRequest#getRequestURI() requestURI}进行比较。
	 *
	 * <li>{@link #setRemoveSemicolonContent} -- {@code PathPatterns} always
	 * ignore semicolon content for path matching purposes, but path parameters
	 * remain available for use in controllers via {@code @MatrixVariable}.
	 * {@link #setRemoveSemicolonContent}——{@code PathPatterns}总是为了路径匹配而忽略分号内容，
	 * 但是路径参数仍然可用，可以通过{@code @MatrixVariable}在控制器中使用。
	 *
	 * <li>{@link #setUrlDecode} -- {@code PathPatterns} match one decoded path
	 * segment at a time and never need the full decoded path which can cause
	 * issues due to decoded reserved characters.
	 * {@link #setUrlDecode}——{@code PathPatterns}一次匹配一个已解码的路径段，永远不需要完整的已解码路径，
	 * 这可能会由于已解码的保留字符而导致问题。
	 *
	 * <li>{@link #setUrlPathHelper} -- the request path is pre-parsed globally
	 * by the {@link org.springframework.web.servlet.DispatcherServlet
	 * DispatcherServlet} or by
	 * {@link org.springframework.web.filter.ServletRequestPathFilter
	 * ServletRequestPathFilter} using {@link ServletRequestPathUtils} and saved
	 * in a request attribute for re-use.
	 * 请求路径是由{@link org.springframework.web.servlet.DispatcherServlet DispatcherServlet}
	 * 或由{@link org.springframework.web.filter.ServletRequestPathFilter ServletRequestPathFilter}
	 * 使用{@link ServletRequestPathUtils}全局预解析的，并保存在请求属性中以供重用。
	 *
	 * <li>{@link #setPathMatcher} -- patterns are parsed to {@code PathPatterns}
	 * and used instead of String matching with {@code PathMatcher}.
	 * {@link #setPathMatcher}——模式被解析为{@code PathPatterns}，用来代替字符串匹配{@code PathMatcher}。
	 *
	 * </ul>
	 * <p>By default this is not set.
	 * 默认情况下没有设置。
	 *
	 * @param patternParser the parser to use
	 * @since 5.3
	 */
	public void setPatternParser(PathPatternParser patternParser) {
		this.patternParser = patternParser;
	}

	/**
	 * Return the {@link #setPatternParser(PathPatternParser) configured}
	 * 返回已配置的{@link #setPatternParser(PathPatternParser)}
	 *
	 * {@code PathPatternParser}, or {@code null}.
	 * @since 5.3
	 */
	@Nullable
	public PathPatternParser getPatternParser() {
		return this.patternParser;
	}

	/**
	 * Shortcut to same property on the configured {@code UrlPathHelper}.
	 * 在配置的{@code UrlPathHelper}上相同属性的快捷方式。
	 *
	 * <p><strong>Note:</strong> This property is mutually exclusive with and
	 * ignored when {@link #setPatternParser(PathPatternParser)} is set.
	 * @see org.springframework.web.util.UrlPathHelper#setAlwaysUseFullPath(boolean)
	 *
	 * <p><strong>备注:<strong>该属性与设置{@link #setPatternParser(PathPatternParser)}时互斥，被忽略。
	 * @see org.springframework.web.util.UrlPathHelper#setAlwaysUseFullPath(boolean)
	 *
	 */
	@SuppressWarnings("deprecation")
	public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
		this.urlPathHelper.setAlwaysUseFullPath(alwaysUseFullPath);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setAlwaysUseFullPath(alwaysUseFullPath);
		}
	}

	/**
	 * Shortcut to same property on the underlying {@code UrlPathHelper}.
	 * <p><strong>Note:</strong> This property is mutually exclusive with and
	 * ignored when {@link #setPatternParser(PathPatternParser)} is set.
	 * @see org.springframework.web.util.UrlPathHelper#setUrlDecode(boolean)
	 */
	@SuppressWarnings("deprecation")
	public void setUrlDecode(boolean urlDecode) {
		this.urlPathHelper.setUrlDecode(urlDecode);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setUrlDecode(urlDecode);
		}
	}

	/**
	 * Shortcut to same property on the underlying {@code UrlPathHelper}.
	 * <p><strong>Note:</strong> This property is mutually exclusive with and
	 * ignored when {@link #setPatternParser(PathPatternParser)} is set.
	 * @see org.springframework.web.util.UrlPathHelper#setRemoveSemicolonContent(boolean)
	 */
	@SuppressWarnings("deprecation")
	public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
		this.urlPathHelper.setRemoveSemicolonContent(removeSemicolonContent);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setRemoveSemicolonContent(removeSemicolonContent);
		}
	}

	/**
	 * Configure the UrlPathHelper to use for resolution of lookup paths.
	 * <p><strong>Note:</strong> This property is mutually exclusive with and
	 * ignored when {@link #setPatternParser(PathPatternParser)} is set.
	 */
	public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
		Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
		this.urlPathHelper = urlPathHelper;
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setUrlPathHelper(urlPathHelper);
		}
	}

	/**
	 * Return the {@link #setUrlPathHelper configured} {@code UrlPathHelper}.
	 */
	public UrlPathHelper getUrlPathHelper() {
		return this.urlPathHelper;
	}

	/**
	 * Configure the PathMatcher to use.
	 * <p><strong>Note:</strong> This property is mutually exclusive with and
	 * ignored when {@link #setPatternParser(PathPatternParser)} is set.
	 * <p>By default this is {@link AntPathMatcher}.
	 * @see org.springframework.util.AntPathMatcher
	 */
	public void setPathMatcher(PathMatcher pathMatcher) {
		Assert.notNull(pathMatcher, "PathMatcher must not be null");
		this.pathMatcher = pathMatcher;
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setPathMatcher(pathMatcher);
		}
	}

	/**
	 * Return the {@link #setPathMatcher configured} {@code PathMatcher}.
	 * 返回配置的{@link #setPathMatcher} {@code PathMatcher}。
	 */
	public PathMatcher getPathMatcher() {
		return this.pathMatcher;
	}

	/**
	 * Set the interceptors to apply for all handlers mapped by this handler mapping.
	 * 将拦截器设置为应用于此处理程序映射所映射的所有处理程序。
	 *
	 * <p>Supported interceptor types are {@link HandlerInterceptor},
	 * {@link WebRequestInterceptor}, and {@link MappedInterceptor}.
	 * 支持的拦截器类型有{@link HandlerInterceptor}、{@link WebRequestInterceptor}和{@link MappedInterceptor}。
	 *
	 * Mapped interceptors apply only to request URLs that match its path patterns.
	 * 映射拦截器只应用于匹配其路径模式的请求url。
	 *
	 * Mapped interceptor beans are also detected by type during initialization.
	 * 映射的拦截器bean也在初始化期间按类型进行检测。
	 *
	 * @param interceptors array of handler interceptors
	 * @see #adaptInterceptor
	 * @see org.springframework.web.servlet.HandlerInterceptor
	 * @see org.springframework.web.context.request.WebRequestInterceptor
	 * @see MappedInterceptor
	 */
	public void setInterceptors(Object... interceptors) {
		this.interceptors.addAll(Arrays.asList(interceptors));
	}

	/**
	 * Set "global" CORS configuration mappings. The first matching URL pattern
	 * determines the {@code CorsConfiguration} to use which is then further
	 * {@link CorsConfiguration#combine(CorsConfiguration) combined} with the
	 * {@code CorsConfiguration} for the selected handler.
	 * <p>This is mutually exclusive with
	 * {@link #setCorsConfigurationSource(CorsConfigurationSource)}.
	 * @since 4.2
	 * @see #setCorsProcessor(CorsProcessor)
	 */
	public void setCorsConfigurations(Map<String, CorsConfiguration> corsConfigurations) {
		if (CollectionUtils.isEmpty(corsConfigurations)) {
			this.corsConfigurationSource = null;
			return;
		}
		UrlBasedCorsConfigurationSource source;
		if (getPatternParser() != null) {
			source = new UrlBasedCorsConfigurationSource(getPatternParser());
			source.setCorsConfigurations(corsConfigurations);
		}
		else {
			source = new UrlBasedCorsConfigurationSource();
			source.setCorsConfigurations(corsConfigurations);
			source.setPathMatcher(this.pathMatcher);
			source.setUrlPathHelper(this.urlPathHelper);
		}
		setCorsConfigurationSource(source);
	}

	/**
	 * Set a {@code CorsConfigurationSource} for "global" CORS config. The
	 * {@code CorsConfiguration} determined by the source is
	 * {@link CorsConfiguration#combine(CorsConfiguration) combined} with the
	 * {@code CorsConfiguration} for the selected handler.
	 * <p>This is mutually exclusive with {@link #setCorsConfigurations(Map)}.
	 * @since 5.1
	 * @see #setCorsProcessor(CorsProcessor)
	 */
	public void setCorsConfigurationSource(CorsConfigurationSource source) {
		Assert.notNull(source, "CorsConfigurationSource must not be null");
		this.corsConfigurationSource = source;
		if (source instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) source).setAllowInitLookupPath(false);
		}
	}

	/**
	 * Return the {@link #setCorsConfigurationSource(CorsConfigurationSource)
	 * configured} {@code CorsConfigurationSource}, if any.
	 *
	 * 如果有的话，返回{@link #setCorsConfigurationSource(CorsConfigurationSource) 配置}{@code CorsConfigurationSource}。
	 *
	 * @since 5.3
	 */
	@Nullable
	public CorsConfigurationSource getCorsConfigurationSource() {
		return this.corsConfigurationSource;
	}

	/**
	 * Configure a custom {@link CorsProcessor} to use to apply the matched
	 * {@link CorsConfiguration} for a request.
	 * <p>By default {@link DefaultCorsProcessor} is used.
	 * @since 4.2
	 */
	public void setCorsProcessor(CorsProcessor corsProcessor) {
		Assert.notNull(corsProcessor, "CorsProcessor must not be null");
		this.corsProcessor = corsProcessor;
	}

	/**
	 * Return the configured {@link CorsProcessor}.
	 */
	public CorsProcessor getCorsProcessor() {
		return this.corsProcessor;
	}

	/**
	 * Specify the order value for this HandlerMapping bean.
	 * <p>The default value is {@code Ordered.LOWEST_PRECEDENCE}, meaning non-ordered.
	 * @see org.springframework.core.Ordered#getOrder()
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	protected String formatMappingName() {
		return this.beanName != null ? "'" + this.beanName + "'" : getClass().getName();
	}


	/**
	 * Initializes the interceptors.
	 * 初始化拦截器。
	 *
	 * @see #extendInterceptors(java.util.List)
	 * @see #initInterceptors()
	 */
	@Override
	protected void initApplicationContext() throws BeansException {
		extendInterceptors(this.interceptors);
		/**
		 * 查找所有继承了{@link MappedInterceptor}类的bean实例，并添加到{@link #adaptedInterceptors}
		 */
		detectMappedInterceptors(this.adaptedInterceptors);
		initInterceptors();
	}

	/**
	 * Extension hook that subclasses can override to register additional interceptors,
	 * given the configured interceptors (see {@link #setInterceptors}).
	 * 扩展钩子，子类可以覆盖以注册额外的拦截器，给定配置的拦截器(参见{@link #setInterceptors})。
	 *
	 * <p>Will be invoked before {@link #initInterceptors()} adapts the specified
	 * interceptors into {@link HandlerInterceptor} instances.
	 * 将在{@link #initInterceptors()}将指定的拦截器改编为{@link HandlerInterceptor}实例之前调用。
	 *
	 * <p>The default implementation is empty.
	 * @param interceptors the configured interceptor List (never {@code null}), allowing
	 * to add further interceptors before as well as after the existing interceptors
	 */
	protected void extendInterceptors(List<Object> interceptors) {
	}

	/**
	 * 查找所有继承了{@link MappedInterceptor}类的bean实例
	 *
	 * 所有通过xml注入的拦截器都会被封装成{@link MappedInterceptor}类型，{@link MappedInterceptor#interceptor}指定真正的拦截器
	 * 因此此处可以查找到用户自定义的拦截器
	 *
	 * Detect beans of type {@link MappedInterceptor} and add them to the list
	 * of mapped interceptors.
	 * 检测类型为{@link MappedInterceptor}的bean，并将它们添加到映射的拦截器列表中。
	 *
	 * <p>This is called in addition to any {@link MappedInterceptor}s that may
	 * have been provided via {@link #setInterceptors}, by default adding all
	 * beans of type {@link MappedInterceptor} from the current context and its
	 * ancestors. Subclasses can override and refine this policy.
	 * 除了通过{@link #setInterceptors}提供的{@link MappedInterceptor}之外，
	 * 它还会被调用，默认情况下添加当前上下文及其祖先中{@link MappedInterceptor}类型的所有bean。子类可以重写和细化此策略。
	 *
	 * @param mappedInterceptors an empty list to add to
	 */
	protected void detectMappedInterceptors(List<HandlerInterceptor> mappedInterceptors) {
		mappedInterceptors.addAll(BeanFactoryUtils.beansOfTypeIncludingAncestors(
				obtainApplicationContext(), MappedInterceptor.class, true, false).values());
	}

	/**
	 * Initialize the specified interceptors adapting
	 * 自适应初始化指定的拦截器
	 *
	 * {@link WebRequestInterceptor}s to {@link HandlerInterceptor}.
	 * @see #setInterceptors
	 * @see #adaptInterceptor
	 */
	protected void initInterceptors() {
		if (!this.interceptors.isEmpty()) {
			for (int i = 0; i < this.interceptors.size(); i++) {
				Object interceptor = this.interceptors.get(i);
				if (interceptor == null) {
					throw new IllegalArgumentException("Entry number " + i + " in interceptors array is null");
				}
				this.adaptedInterceptors.add(adaptInterceptor(interceptor));
			}
		}
	}

	/**
	 * Adapt the given interceptor object to {@link HandlerInterceptor}.
	 * <p>By default, the supported interceptor types are
	 * {@link HandlerInterceptor} and {@link WebRequestInterceptor}. Each given
	 * {@link WebRequestInterceptor} is wrapped with
	 * {@link WebRequestHandlerInterceptorAdapter}.
	 * @param interceptor the interceptor
	 * @return the interceptor downcast or adapted to HandlerInterceptor
	 * @see org.springframework.web.servlet.HandlerInterceptor
	 * @see org.springframework.web.context.request.WebRequestInterceptor
	 * @see WebRequestHandlerInterceptorAdapter
	 */
	protected HandlerInterceptor adaptInterceptor(Object interceptor) {
		if (interceptor instanceof HandlerInterceptor) {
			return (HandlerInterceptor) interceptor;
		}
		else if (interceptor instanceof WebRequestInterceptor) {
			return new WebRequestHandlerInterceptorAdapter((WebRequestInterceptor) interceptor);
		}
		else {
			throw new IllegalArgumentException("Interceptor type not supported: " + interceptor.getClass().getName());
		}
	}

	/**
	 * Return the adapted interceptors as {@link HandlerInterceptor} array.
	 * @return the array of {@link HandlerInterceptor HandlerInterceptor}s,
	 * or {@code null} if none
	 */
	@Nullable
	protected final HandlerInterceptor[] getAdaptedInterceptors() {
		return (!this.adaptedInterceptors.isEmpty() ?
				this.adaptedInterceptors.toArray(new HandlerInterceptor[0]) : null);
	}

	/**
	 * Return all configured {@link MappedInterceptor}s as an array.
	 * @return the array of {@link MappedInterceptor}s, or {@code null} if none
	 */
	@Nullable
	protected final MappedInterceptor[] getMappedInterceptors() {
		List<MappedInterceptor> mappedInterceptors = new ArrayList<>(this.adaptedInterceptors.size());
		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
			if (interceptor instanceof MappedInterceptor) {
				mappedInterceptors.add((MappedInterceptor) interceptor);
			}
		}
		return (!mappedInterceptors.isEmpty() ? mappedInterceptors.toArray(new MappedInterceptor[0]) : null);
	}


	/**
	 * Return "true" if this {@code HandlerMapping} has been
	 * {@link #setPatternParser enabled} to use parsed {@code PathPattern}s.
	 *
	 * 如果这个{@code HandlerMapping}已启用{@link #setPatternParser}使用已解析的{@code PathPattern}则返回"true"。
	 */
	@Override
	public boolean usesPathPatterns() {
		return getPatternParser() != null;
	}

	/**
	 * 1.获取当前请求处理程序
	 * 2.添加拦截器，如果是跨域请求，会添加跨域拦截器
	 *
	 * Look up a handler for the given request, falling back to the default
	 * handler if no specific one is found.
	 * 查找给定请求的处理程序，如果没有找到特定的处理程序，则返回到默认处理程序。
	 *
	 * @param request current HTTP request
	 * @return the corresponding handler instance, or the default handler
	 * @see #getHandlerInternal
	 */
	@Override
	@Nullable
	public final HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		/**
		 * 获取处理程序
		 */
		Object handler = getHandlerInternal(request);
		if (handler == null) {
			handler = getDefaultHandler();
		}
		if (handler == null) {
			return null;
		}
		// Bean name or resolved handler?
		/**
		 * Bean名称还是已解析的处理程序?
		 */
		if (handler instanceof String) {
			String handlerName = (String) handler;
			handler = obtainApplicationContext().getBean(handlerName);
		}

		// Ensure presence of cached lookupPath for interceptors and others
		/**
		 * 确保存在拦截器和其他缓存的lookupPath
		 * 解析请求的请求路径，并保存到request属性中
		 */
		if (!ServletRequestPathUtils.hasCachedPath(request)) {
			initLookupPath(request);
		}

		/**
		 * 给处理程序添加拦截器
		 */
		HandlerExecutionChain executionChain = getHandlerExecutionChain(handler, request);

		if (logger.isTraceEnabled()) {
			logger.trace("Mapped to " + handler);
		}
		else if (logger.isDebugEnabled() && !DispatcherType.ASYNC.equals(request.getDispatcherType())) {
			logger.debug("Mapped to " + executionChain.getHandler());
		}

		/**
		 * 处理跨域请求
		 */
		if (hasCorsConfigurationSource(handler) || CorsUtils.isPreFlightRequest(request)) {
			CorsConfiguration config = getCorsConfiguration(handler, request);
			if (getCorsConfigurationSource() != null) {
				CorsConfiguration globalConfig = getCorsConfigurationSource().getCorsConfiguration(request);
				config = (globalConfig != null ? globalConfig.combine(config) : config);
			}
			if (config != null) {
				config.validateAllowCredentials();
			}
			/**
			 * 如果当前请求是跨域预检请求，使用默认处理程序{@link PreFlightHandler}
			 * 如果不是，添加一个前置跨域拦截器{@link CorsInterceptor}
			 * 
			 * 不管是不是跨域预检请求，都会调用{@link DefaultCorsProcessor#processRequest(CorsConfiguration, HttpServletRequest, HttpServletResponse)}
			 */
			executionChain = getCorsHandlerExecutionChain(request, executionChain, config);
		}

		return executionChain;
	}

	/**
	 * Look up a handler for the given request, returning {@code null} if no
	 * specific one is found. This method is called by {@link #getHandler};
	 * a {@code null} return value will lead to the default handler, if one is set.
	 * 查找给定请求的处理程序，如果没有找到特定的处理程序，则返回{@code null}。
	 * 该方法由{@link #getHandler}调用;一个{@code null}返回值将导致默认处理程序，如果设置了一个。
	 *
	 * <p>On CORS pre-flight requests this method should return a match not for
	 * the pre-flight request but for the expected actual request based on the URL
	 * path, the HTTP methods from the "Access-Control-Request-Method" header, and
	 * the headers from the "Access-Control-Request-Headers" header thus allowing
	 * the CORS configuration to be obtained via {@link #getCorsConfiguration(Object, HttpServletRequest)},
	 * 在CORS预飞行请求上，该方法应该返回一个匹配的不是预飞行请求，而是基于URL路径的预期实际请求，
	 * 来自“Access-Control-Request-Method”头的HTTP方法，以及来自“Access-Control-Request-Headers”头的头，
	 * 从而允许通过{@link #getCorsConfiguration(Object, HttpServletRequest)}获得CORS配置，
	 *
	 * <p>Note: This method may also return a pre-built {@link HandlerExecutionChain},
	 * combining a handler object with dynamically determined interceptors.
	 * 注意:此方法也可以返回一个预先构建的{@link HandlerExecutionChain}，将处理程序对象与动态确定的拦截器组合在一起。
	 *
	 * Statically specified interceptors will get merged into such an existing chain.
	 * 静态指定的拦截器将被合并到这样的现有链中。
	 *
	 * @param request current HTTP request
	 * @return the corresponding handler instance, or {@code null} if none found
	 * @throws Exception if there is an internal error
	 */
	@Nullable
	protected abstract Object getHandlerInternal(HttpServletRequest request) throws Exception;

	/**
	 * Initialize the path to use for request mapping.
	 * 初始化用于请求映射的路径。
	 *
	 * <p>When parsed patterns are {@link #usesPathPatterns() enabled} a parsed
	 * {@code RequestPath} is expected to have been
	 * {@link ServletRequestPathUtils#parseAndCache(HttpServletRequest) parsed}
	 * externally by the {@link org.springframework.web.servlet.DispatcherServlet}
	 * or {@link org.springframework.web.filter.ServletRequestPathFilter}.
	 * 当被解析的模式被{@link #usesPathPatterns() 启用}时，
	 * 一个被解析的{@code RequestPath}将被{@link ServletRequestPathUtils#parseAndCache(HttpServletRequest)
	 * 在外部被{@link org.springframework.web.servlet.DispatcherServlet}或{@link org.springframework.web.filter.ServletRequestPathFilter}。
	 *
	 * <p>Otherwise for String pattern matching via {@code PathMatcher} the
	 * path is {@link UrlPathHelper#resolveAndCacheLookupPath resolved} by this
	 * method.
	 * 否则，对于通过{@code PathMatcher}进行字符串模式匹配的路径是通过该方法解析的{@link UrlPathHelper#resolveAndCacheLookupPath。
	 *
	 * @since 5.3
	 */
	protected String initLookupPath(HttpServletRequest request) {
		if (usesPathPatterns()) {
			request.removeAttribute(UrlPathHelper.PATH_ATTRIBUTE);
			RequestPath requestPath = ServletRequestPathUtils.getParsedRequestPath(request);
			String lookupPath = requestPath.pathWithinApplication().value();
			return UrlPathHelper.defaultInstance.removeSemicolonContent(lookupPath);
		}
		else {
			return getUrlPathHelper().resolveAndCacheLookupPath(request);
		}
	}

	/**
	 * 给处理程序添加拦截器
	 *
	 * Build a {@link HandlerExecutionChain} for the given handler, including
	 * applicable interceptors.
	 * 为给定的处理程序构建一个{@link HandlerExecutionChain}，包括适用的拦截器。
	 *
	 * <p>The default implementation builds a standard {@link HandlerExecutionChain}
	 * with the given handler, the common interceptors of the handler mapping, and any
	 * {@link MappedInterceptor MappedInterceptors} matching to the current request URL. Interceptors
	 * are added in the order they were registered. Subclasses may override this
	 * in order to extend/rearrange the list of interceptors.
	 * 默认实现使用给定的处理程序、处理程序映射的公共拦截器以及与当前请求URL匹配的任何{@link MappedInterceptor MappedInterceptors}
	 * 构建一个标准{@link HandlerExecutionChain}。拦截器按照注册的顺序添加。子类可以覆盖这个，以扩展和重新排列拦截器列表。
	 *
	 * <p><b>NOTE:</b> The passed-in handler object may be a raw handler or a
	 * pre-built {@link HandlerExecutionChain}. This method should handle those
	 * two cases explicitly, either building a new {@link HandlerExecutionChain}
	 * or extending the existing chain.
	 * <p><b>注意:<b>传入的处理程序对象可以是一个原始处理程序，也可以是一个预构建的{@link HandlerExecutionChain}。
	 * 这个方法应该显式地处理这两种情况，要么构建一个新的{@link HandlerExecutionChain}，要么扩展现有的链。
	 *
	 * <p>For simply adding an interceptor in a custom subclass, consider calling
	 * {@code super.getHandlerExecutionChain(handler, request)} and invoking
	 * {@link HandlerExecutionChain#addInterceptor} on the returned chain object.
	 * 为了简单地在自定义子类中添加拦截器，可以考虑调用{@code super.getHandlerExecutionChain(handler, request)}
	 * 并在返回的链对象上调用{@link HandlerExecutionChain#addInterceptor}。
	 *
	 * @param handler the resolved handler instance (never {@code null})
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain (never {@code null})
	 * @see #getAdaptedInterceptors()
	 */
	protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
		HandlerExecutionChain chain = (handler instanceof HandlerExecutionChain ?
				(HandlerExecutionChain) handler : new HandlerExecutionChain(handler));

		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
			/**
			 * 用户通过自定义的拦截器都会被封装成{@link MappedInterceptor}类型的拦截器对象
			 * {@link MappedInterceptor#interceptor}指向用户自定义的拦截器
			 */
			if (interceptor instanceof MappedInterceptor) {
				MappedInterceptor mappedInterceptor = (MappedInterceptor) interceptor;
				if (mappedInterceptor.matches(request)) {
					chain.addInterceptor(mappedInterceptor.getInterceptor());
				}
			}
			else {
				chain.addInterceptor(interceptor);
			}
		}
		return chain;
	}

	/**
	 * Return {@code true} if there is a {@link CorsConfigurationSource} for this handler.
	 * 如果此处理程序有{@link CorsConfigurationSource}，则返回{@code true}。
	 *
	 * @since 5.2
	 */
	protected boolean hasCorsConfigurationSource(Object handler) {
		if (handler instanceof HandlerExecutionChain) {
			handler = ((HandlerExecutionChain) handler).getHandler();
		}
		return (handler instanceof CorsConfigurationSource || this.corsConfigurationSource != null);
	}

	/**
	 * Retrieve the CORS configuration for the given handler.
	 * 检索给定处理程序的CORS配置。
	 *
	 * @param handler the handler to check (never {@code null}).
	 * @param request the current request.
	 * @return the CORS configuration for the handler, or {@code null} if none
	 * @since 4.2
	 */
	@Nullable
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		Object resolvedHandler = handler;
		if (handler instanceof HandlerExecutionChain) {
			resolvedHandler = ((HandlerExecutionChain) handler).getHandler();
		}
		if (resolvedHandler instanceof CorsConfigurationSource) {
			return ((CorsConfigurationSource) resolvedHandler).getCorsConfiguration(request);
		}
		return null;
	}

	/**
	 * Update the HandlerExecutionChain for CORS-related handling.
	 * 更新HandlerExecutionChain以进行与cors相关的处理。
	 *
	 * <p>For pre-flight requests, the default implementation replaces the selected
	 * handler with a simple HttpRequestHandler that invokes the configured
	 * {@link #setCorsProcessor}.
	 * 对于预飞行请求，默认实现用一个简单的HttpRequestHandler替换所选的处理程序，该处理程序调用已配置的{@link #setCorsProcessor}。
	 *
	 * <p>For actual requests, the default implementation inserts a
	 * HandlerInterceptor that makes CORS-related checks and adds CORS headers.
	 * 对于实际的请求，默认实现会插入一个HandlerInterceptor，用于进行CORS相关的检查，并添加CORS报头。
	 *
	 * @param request the current request
	 * @param chain the handler chain
	 * @param config the applicable CORS configuration (possibly {@code null})
	 * @since 4.2
	 */
	protected HandlerExecutionChain getCorsHandlerExecutionChain(HttpServletRequest request,
			HandlerExecutionChain chain, @Nullable CorsConfiguration config) {

		/**
		 * 如果当前请求是一个跨域预检请求，创建默认处理程序
		 * @see {@link DefaultCorsProcessor#processRequest(CorsConfiguration, HttpServletRequest, HttpServletResponse)}
		 */
		if (CorsUtils.isPreFlightRequest(request)) {
			HandlerInterceptor[] interceptors = chain.getInterceptors();
			return new HandlerExecutionChain(new PreFlightHandler(config), interceptors);
		}
		else {
			/**
			 * 为跨域请求添加一个前置跨域处理拦截器，最终的处理过程
			 * @see {@link DefaultCorsProcessor#processRequest(CorsConfiguration, HttpServletRequest, HttpServletResponse)}
			 */
			chain.addInterceptor(0, new CorsInterceptor(config));
			return chain;
		}
	}


	private class PreFlightHandler implements HttpRequestHandler, CorsConfigurationSource {

		@Nullable
		private final CorsConfiguration config;

		public PreFlightHandler(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
			corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}


	private class CorsInterceptor implements HandlerInterceptor, CorsConfigurationSource {

		@Nullable
		private final CorsConfiguration config;

		public CorsInterceptor(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
				throws Exception {

			// Consistent with CorsFilter, ignore ASYNC dispatches
			/**
			 * 与CorsFilter一致，忽略ASYNC分派
			 */
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			if (asyncManager.hasConcurrentResult()) {
				return true;
			}

			return corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}

}
