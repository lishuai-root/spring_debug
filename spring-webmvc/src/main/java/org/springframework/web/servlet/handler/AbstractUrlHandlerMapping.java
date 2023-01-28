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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.*;

/**
 * Abstract base class for URL-mapped {@link HandlerMapping} implementations.
 *
 * <p>Supports literal matches and pattern matches such as "/test/*", "/test/**",
 * and others. For details on pattern syntax refer to {@link PathPattern} when
 * parsed patterns are {@link #usesPathPatterns() enabled} or see
 * {@link AntPathMatcher} otherwise. The syntax is largely the same but the
 * {@code PathPattern} syntax is more tailored for web applications, and its
 * implementation is more efficient.
 *
 * <p>All path patterns are checked in order to find the most exact match for the
 * current request path where the "most exact" is the longest path pattern that
 * matches the current request path.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @since 16.04.2003
 */
public abstract class AbstractUrlHandlerMapping extends AbstractHandlerMapping implements MatchableHandlerMapping {

	/**
	 * 表示跟处理程序，即路径为"/"的处理程序
	 */
	@Nullable
	private Object rootHandler;

	private boolean useTrailingSlashMatch = false;

	private boolean lazyInitHandlers = false;

	/**
	 * 保存请求路径和处理程序的映射关系
	 */
	private final Map<String, Object> handlerMap = new LinkedHashMap<>();

	/**
	 * 用来保存匹配模式和处理程序的映射关系
	 */
	private final Map<PathPattern, Object> pathPatternHandlerMap = new LinkedHashMap<>();


	@Override
	public void setPatternParser(PathPatternParser patternParser) {
		Assert.state(this.handlerMap.isEmpty(),
				"PathPatternParser must be set before the initialization of " +
						"the handler map via ApplicationContextAware#setApplicationContext.");
		super.setPatternParser(patternParser);
	}

	/**
	 * Set the root handler for this handler mapping, that is,
	 * the handler to be registered for the root path ("/").
	 * 为这个处理程序映射设置根处理程序，即为根路径注册的处理程序(“/”)。
	 *
	 * <p>Default is {@code null}, indicating no root handler.
	 * 默认值是{@code null}，表示没有根处理程序。
	 */
	public void setRootHandler(@Nullable Object rootHandler) {
		this.rootHandler = rootHandler;
	}

	/**
	 * Return the root handler for this handler mapping (registered for "/"),
	 * or {@code null} if none.
	 */
	@Nullable
	public Object getRootHandler() {
		return this.rootHandler;
	}

	/**
	 * Whether to match to URLs irrespective of the presence of a trailing slash.
	 * If enabled a URL pattern such as "/users" also matches to "/users/".
	 * <p>The default value is {@code false}.
	 */
	public void setUseTrailingSlashMatch(boolean useTrailingSlashMatch) {
		this.useTrailingSlashMatch = useTrailingSlashMatch;
		if (getPatternParser() != null) {
			getPatternParser().setMatchOptionalTrailingSeparator(useTrailingSlashMatch);
		}
	}

	/**
	 * Whether to match to URLs irrespective of the presence of a trailing slash.
	 * 是否与url匹配，而不考虑后面是否有斜杠。
	 */
	public boolean useTrailingSlashMatch() {
		return this.useTrailingSlashMatch;
	}

	/**
	 * Set whether to lazily initialize handlers. Only applicable to
	 * singleton handlers, as prototypes are always lazily initialized.
	 * Default is "false", as eager initialization allows for more efficiency
	 * through referencing the controller objects directly.
	 * <p>If you want to allow your controllers to be lazily initialized,
	 * make them "lazy-init" and set this flag to true. Just making them
	 * "lazy-init" will not work, as they are initialized through the
	 * references from the handler mapping in this case.
	 */
	public void setLazyInitHandlers(boolean lazyInitHandlers) {
		this.lazyInitHandlers = lazyInitHandlers;
	}

	/**
	 * Look up a handler for the URL path of the given request.
	 * 查找给定请求的URL路径的处理程序。
	 *
	 * @param request current HTTP request
	 * @return the handler instance, or {@code null} if none found
	 */
	@Override
	@Nullable
	protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
		String lookupPath = initLookupPath(request);
		Object handler;
		/**
		 * 通过请求路径获取执行链
		 */
		if (usesPathPatterns()) {
			RequestPath path = ServletRequestPathUtils.getParsedRequestPath(request);
			handler = lookupHandler(path, lookupPath, request);
		}
		else {
			handler = lookupHandler(lookupPath, request);
		}
		if (handler == null) {
			// We need to care for the default handler directly, since we need to
			// expose the PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE for it as well.
			/**
			 * 我们需要直接关注默认处理程序，因为我们还需要为它公开PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE。
			 */
			Object rawHandler = null;
			/**
			 * 获取默认处理程序，优先获取"/"路径对应的处理程序，再获取"/*"对应的处理程序
			 */
			if (StringUtils.matchesCharacter(lookupPath, '/')) {
				rawHandler = getRootHandler();
			}
			if (rawHandler == null) {
				rawHandler = getDefaultHandler();
			}
			/**
			 * 如果有处理程序，验证处理程序，并生成执行链
			 */
			if (rawHandler != null) {
				// Bean name or resolved handler?
				if (rawHandler instanceof String) {
					String handlerName = (String) rawHandler;
					rawHandler = obtainApplicationContext().getBean(handlerName);
				}
				validateHandler(rawHandler, request);
				handler = buildPathExposingHandler(rawHandler, lookupPath, lookupPath, null);
			}
		}
		return handler;
	}

	/**
	 * 通过请求路径获取处理程序，并生成执行链
	 * 首先通过请求路径获取处理程序，如果获取不到，会通过匹配模式获取
	 *
	 * Look up a handler instance for the given URL path. This method is used
	 * when parsed {@code PathPattern}s are {@link #usesPathPatterns() enabled}.
	 * 查找给定URL路径的处理程序实例。当解析的{@code PathPattern}启用{@link #usesPathPatterns()}时，使用此方法。
	 *
	 * @param path the parsed RequestPath
	 * @param lookupPath the String lookupPath for checking direct hits
	 * @param request current HTTP request
	 * @return a matching handler, or {@code null} if not found
	 * @since 5.3
	 */
	@Nullable
	protected Object lookupHandler(
			RequestPath path, String lookupPath, HttpServletRequest request) throws Exception {

		/**
		 * 获取路径对应的执行链
		 */
		Object handler = getDirectMatch(lookupPath, request);
		if (handler != null) {
			return handler;
		}

		// Pattern match?
		/**
		 * 模式匹配?
		 * 通过模式匹配(正则表达式)，获取合适的处理程序
		 */
		List<PathPattern> matches = null;
		for (PathPattern pattern : this.pathPatternHandlerMap.keySet()) {
			if (pattern.matches(path.pathWithinApplication())) {
				matches = (matches != null ? matches : new ArrayList<>());
				matches.add(pattern);
			}
		}
		if (matches == null) {
			return null;
		}
		/**
		 * 如果通过模式匹配获取到多个合适的处理程序，则进行排序，取最合适的
		 */
		if (matches.size() > 1) {
			matches.sort(PathPattern.SPECIFICITY_COMPARATOR);
			if (logger.isTraceEnabled()) {
				logger.trace("Matching patterns " + matches);
			}
		}
		PathPattern pattern = matches.get(0);
		handler = this.pathPatternHandlerMap.get(pattern);
		if (handler instanceof String) {
			String handlerName = (String) handler;
			handler = obtainApplicationContext().getBean(handlerName);
		}
		/**
		 * 根据当前请求验证处理程序。默认空实现
		 */
		validateHandler(handler, request);
		/**
		 * 获取最佳匹配路径
		 */
		PathContainer pathWithinMapping = pattern.extractPathWithinPattern(path.pathWithinApplication());
		return buildPathExposingHandler(handler, pattern.getPatternString(), pathWithinMapping.value(), null);
	}

	/**
	 * Look up a handler instance for the given URL path. This method is used
	 * when String pattern matching with {@code PathMatcher} is in use.
	 * 查找给定URL路径的处理程序实例。当使用{@code PathMatcher}进行String模式匹配时，使用此方法。
	 *
	 * @param lookupPath the path to match patterns against
	 * @param request current HTTP request
	 * @return a matching handler, or {@code null} if not found
	 * @see #exposePathWithinMapping
	 * @see AntPathMatcher
	 */
	@Nullable
	protected Object lookupHandler(String lookupPath, HttpServletRequest request) throws Exception {
		Object handler = getDirectMatch(lookupPath, request);
		if (handler != null) {
			return handler;
		}

		// Pattern match?
		List<String> matchingPatterns = new ArrayList<>();
		/**
		 * 遍历所有已经注册的请求路径，匹配合适的请求路径
		 */
		for (String registeredPattern : this.handlerMap.keySet()) {
			if (getPathMatcher().match(registeredPattern, lookupPath)) {
				matchingPatterns.add(registeredPattern);
			}
			/**
			 * 是否与url匹配，而不考虑后面是否有斜杠。
			 */
			else if (useTrailingSlashMatch()) {
				if (!registeredPattern.endsWith("/") && getPathMatcher().match(registeredPattern + "/", lookupPath)) {
					matchingPatterns.add(registeredPattern + "/");
				}
			}
		}

		String bestMatch = null;
		Comparator<String> patternComparator = getPathMatcher().getPatternComparator(lookupPath);
		/**
		 * 通过排序获取匹配度最高的路径
		 */
		if (!matchingPatterns.isEmpty()) {
			matchingPatterns.sort(patternComparator);
			if (logger.isTraceEnabled() && matchingPatterns.size() > 1) {
				logger.trace("Matching patterns " + matchingPatterns);
			}
			bestMatch = matchingPatterns.get(0);
		}
		if (bestMatch != null) {
			handler = this.handlerMap.get(bestMatch);
			/**
			 * bestMatch有可能是通过忽略url后面是否有斜杠匹配到的，所以此处需要去掉后面的斜杠获取对应处理程序
			 */
			if (handler == null) {
				if (bestMatch.endsWith("/")) {
					handler = this.handlerMap.get(bestMatch.substring(0, bestMatch.length() - 1));
				}
				if (handler == null) {
					throw new IllegalStateException(
							"Could not find handler for best pattern match [" + bestMatch + "]");
				}
			}
			// Bean name or resolved handler?
			/**
			 * Bean名称还是已解析的处理程序?
			 */
			if (handler instanceof String) {
				String handlerName = (String) handler;
				handler = obtainApplicationContext().getBean(handlerName);
			}
			validateHandler(handler, request);
			/**
			 * 获取最匹配的请求路径
			 */
			String pathWithinMapping = getPathMatcher().extractPathWithinPattern(bestMatch, lookupPath);

			// There might be multiple 'best patterns', let's make sure we have the correct URI template variables
			// for all of them
			/**
			 * 可能有多个“最佳模式”，让我们确保我们有正确的URI模板变量
			 */
			Map<String, String> uriTemplateVariables = new LinkedHashMap<>();
			for (String matchingPattern : matchingPatterns) {
				if (patternComparator.compare(bestMatch, matchingPattern) == 0) {
					Map<String, String> vars = getPathMatcher().extractUriTemplateVariables(matchingPattern, lookupPath);
					Map<String, String> decodedVars = getUrlPathHelper().decodePathVariables(request, vars);
					uriTemplateVariables.putAll(decodedVars);
				}
			}
			if (logger.isTraceEnabled() && uriTemplateVariables.size() > 0) {
				logger.trace("URI variables " + uriTemplateVariables);
			}
			/**
			 * 创建执行链
			 */
			return buildPathExposingHandler(handler, bestMatch, pathWithinMapping, uriTemplateVariables);
		}

		// No handler found...
		return null;
	}

	/**
	 * 根据请求路径获取处理程序，并生成执行链
	 *
	 * @param urlPath
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@Nullable
	private Object getDirectMatch(String urlPath, HttpServletRequest request) throws Exception {
		/**
		 * 获取请求对应的处理程序
		 */
		Object handler = this.handlerMap.get(urlPath);
		if (handler != null) {
			// Bean name or resolved handler?
			/**
			 * Bean名称还是已解析的处理程序? 在使用{@link BeanNameUrlHandlerMapping}时注入的是beanName
			 * @see {@link BeanNameUrlHandlerMapping#registerHandler(String[], String)}
			 */
			if (handler instanceof String) {
				String handlerName = (String) handler;
				handler = obtainApplicationContext().getBean(handlerName);
			}
			/**
			 * 根据当前请求验证处理程序。默认空实现
			 */
			validateHandler(handler, request);
			/**
			 * 为处理程序创建{@link HandlerExecutionChain}类型的执行链，并添加默认拦截器
			 */
			return buildPathExposingHandler(handler, urlPath, urlPath, null);
		}
		return null;
	}

	/**
	 * Validate the given handler against the current request.
	 * 根据当前请求验证给定的处理程序。
	 *
	 * <p>The default implementation is empty. Can be overridden in subclasses,
	 * for example to enforce specific preconditions expressed in URL mappings.
	 * 默认实现为空。可以在子类中重写，例如强制URL映射中表示的特定先决条件。
	 *
	 * @param handler the handler object to validate
	 * @param request current HTTP request
	 * @throws Exception if validation failed
	 */
	protected void validateHandler(Object handler, HttpServletRequest request) throws Exception {
	}

	/**
	 * 为处理程序创建执行链，默认添加{@link PathExposingHandlerInterceptor}拦截器
	 *
	 * Build a handler object for the given raw handler, exposing the actual
	 * handler, the {@link #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}, as well as
	 * the {@link #URI_TEMPLATE_VARIABLES_ATTRIBUTE} before executing the handler.
	 * 为给定的原始处理程序构建一个处理程序对象，在执行处理程序之前公开实际的处理程序、
	 * {@link #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}以及{@link #URI_TEMPLATE_VARIABLES_ATTRIBUTE}。
	 *
	 * <p>The default implementation builds a {@link HandlerExecutionChain}
	 * with a special interceptor that exposes the path attribute and URI
	 * template variables
	 * 默认实现使用一个特殊的拦截器构建一个{@link HandlerExecutionChain}，该拦截器公开路径属性和URI模板变量
	 *
	 * @param rawHandler the raw handler to expose
	 * @param pathWithinMapping the path to expose before executing the handler
	 * @param uriTemplateVariables the URI template variables, can be {@code null} if no variables found
	 * @return the final handler object
	 */
	protected Object buildPathExposingHandler(Object rawHandler, String bestMatchingPattern,
			String pathWithinMapping, @Nullable Map<String, String> uriTemplateVariables) {

		HandlerExecutionChain chain = new HandlerExecutionChain(rawHandler);
		/**
		 * 添加默认拦截器，用来公开{@link AbstractUrlHandlerMapping#PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}等属性
		 */
		chain.addInterceptor(new PathExposingHandlerInterceptor(bestMatchingPattern, pathWithinMapping));
		if (!CollectionUtils.isEmpty(uriTemplateVariables)) {
			chain.addInterceptor(new UriTemplateVariablesHandlerInterceptor(uriTemplateVariables));
		}
		return chain;
	}

	/**
	 * Expose the path within the current mapping as request attribute.
	 * 将当前映射中的路径公开为请求属性。
	 *
	 * @param pathWithinMapping the path within the current mapping
	 * @param request the request to expose the path to
	 * @see #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
	 */
	protected void exposePathWithinMapping(String bestMatchingPattern, String pathWithinMapping,
			HttpServletRequest request) {

		request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, bestMatchingPattern);
		request.setAttribute(PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, pathWithinMapping);
	}

	/**
	 * Expose the URI templates variables as request attribute.
	 * 将URI模板变量公开为请求属性。
	 *
	 * @param uriTemplateVariables the URI template variables
	 * @param request the request to expose the path to
	 * @see #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
	 */
	protected void exposeUriTemplateVariables(Map<String, String> uriTemplateVariables, HttpServletRequest request) {
		request.setAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVariables);
	}

	@Override
	@Nullable
	public RequestMatchResult match(HttpServletRequest request, String pattern) {
		Assert.isNull(getPatternParser(), "This HandlerMapping uses PathPatterns.");
		String lookupPath = UrlPathHelper.getResolvedLookupPath(request);
		if (getPathMatcher().match(pattern, lookupPath)) {
			return new RequestMatchResult(pattern, lookupPath, getPathMatcher());
		}
		else if (useTrailingSlashMatch()) {
			if (!pattern.endsWith("/") && getPathMatcher().match(pattern + "/", lookupPath)) {
				return new RequestMatchResult(pattern + "/", lookupPath, getPathMatcher());
			}
		}
		return null;
	}

	/**
	 * Register the specified handler for the given URL paths.
	 * 为给定的URL路径注册指定的处理程序。
	 *
	 * @param urlPaths the URLs that the bean should be mapped to
	 * @param beanName the name of the handler bean
	 * @throws BeansException if the handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 */
	protected void registerHandler(String[] urlPaths, String beanName) throws BeansException, IllegalStateException {
		Assert.notNull(urlPaths, "URL path array must not be null");
		for (String urlPath : urlPaths) {
			/**
			 * 注册所有名称(包含别名)和处理程序的映射关系，会通过beanName获取bean实例
			 */
			registerHandler(urlPath, beanName);
		}
	}

	/**
	 * Register the specified handler for the given URL path.
	 * 为给定的URL路径注册指定的处理程序。
	 *
	 * @param urlPath the URL the bean should be mapped to
	 * @param handler the handler instance or handler bean name String
	 * (a bean name will automatically be resolved into the corresponding handler bean)
	 * @throws BeansException if the handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 */
	protected void registerHandler(String urlPath, Object handler) throws BeansException, IllegalStateException {
		Assert.notNull(urlPath, "URL path must not be null");
		Assert.notNull(handler, "Handler object must not be null");
		Object resolvedHandler = handler;

		// Eagerly resolve handler if referencing singleton via name.
		/**
		 * 如果通过名称引用单例，则急切地解析处理程序。
		 */
		if (!this.lazyInitHandlers && handler instanceof String) {
			String handlerName = (String) handler;
			ApplicationContext applicationContext = obtainApplicationContext();
			if (applicationContext.isSingleton(handlerName)) {
				/**
				 * 获取处理程序实例
				 */
				resolvedHandler = applicationContext.getBean(handlerName);
			}
		}

		/**
		 * 如果同样的URL对应了不同的处理程序，则抛出异常
		 */
		Object mappedHandler = this.handlerMap.get(urlPath);
		if (mappedHandler != null) {
			if (mappedHandler != resolvedHandler) {
				throw new IllegalStateException(
						"Cannot map " + getHandlerDescription(handler) + " to URL path [" + urlPath +
						"]: There is already " + getHandlerDescription(mappedHandler) + " mapped.");
			}
		}
		else {
			if (urlPath.equals("/")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Root mapping to " + getHandlerDescription(handler));
				}
				/**
				 * 注册根处理程序
				 */
				setRootHandler(resolvedHandler);
			}
			else if (urlPath.equals("/*")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Default mapping to " + getHandlerDescription(handler));
				}
				/**
				 * 注册默认处理程序
				 */
				setDefaultHandler(resolvedHandler);
			}
			else {
				/**
				 * 注册请求路径和处理程序的映射关系
				 */
				this.handlerMap.put(urlPath, resolvedHandler);

				if (getPatternParser() != null) {
					/**
					 * 为当前的创建一个匹配模式，注册匹配模式和处理程序的映射
					 * 具体匹配规则看{@link PathPattern}，简单说就是支持正则表达式部分内容
					 */
					this.pathPatternHandlerMap.put(getPatternParser().parse(urlPath), resolvedHandler);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Mapped [" + urlPath + "] onto " + getHandlerDescription(handler));
				}
			}
		}
	}

	private String getHandlerDescription(Object handler) {
		return (handler instanceof String ? "'" + handler + "'" : handler.toString());
	}


	/**
	 * Return the handler mappings as a read-only Map, with the registered path
	 * or pattern as key and the handler object (or handler bean name in case of
	 * a lazy-init handler), as value.
	 * @see #getDefaultHandler()
	 */
	public final Map<String, Object> getHandlerMap() {
		return Collections.unmodifiableMap(this.handlerMap);
	}

	/**
	 * Identical to {@link #getHandlerMap()} but populated when parsed patterns
	 * are {@link #usesPathPatterns() enabled}; otherwise empty.
	 * @since 5.3
	 */
	public final Map<PathPattern, Object> getPathPatternHandlerMap() {
		return (this.pathPatternHandlerMap.isEmpty() ?
				Collections.emptyMap() : Collections.unmodifiableMap(this.pathPatternHandlerMap));
	}

	/**
	 * Indicates whether this handler mapping support type-level mappings. Default to {@code false}.
	 */
	protected boolean supportsTypeLevelMappings() {
		return false;
	}


	/**
	 * Special interceptor for exposing the
	 * {@link AbstractUrlHandlerMapping#PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE} attribute.
	 * 用于公开{@link AbstractUrlHandlerMapping#PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}属性的特殊拦截器。
	 *
	 * @see AbstractUrlHandlerMapping#exposePathWithinMapping
	 */
	private class PathExposingHandlerInterceptor implements HandlerInterceptor {

		private final String bestMatchingPattern;

		private final String pathWithinMapping;

		public PathExposingHandlerInterceptor(String bestMatchingPattern, String pathWithinMapping) {
			this.bestMatchingPattern = bestMatchingPattern;
			this.pathWithinMapping = pathWithinMapping;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
			exposePathWithinMapping(this.bestMatchingPattern, this.pathWithinMapping, request);
			request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, handler);
			request.setAttribute(INTROSPECT_TYPE_LEVEL_MAPPING, supportsTypeLevelMappings());
			return true;
		}

	}

	/**
	 * Special interceptor for exposing the
	 * {@link AbstractUrlHandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE} attribute.
	 * 用于公开{@link AbstractUrlHandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE}属性的特殊拦截器。
	 *
	 * @see AbstractUrlHandlerMapping#exposePathWithinMapping
	 */
	private class UriTemplateVariablesHandlerInterceptor implements HandlerInterceptor {

		private final Map<String, String> uriTemplateVariables;

		public UriTemplateVariablesHandlerInterceptor(Map<String, String> uriTemplateVariables) {
			this.uriTemplateVariables = uriTemplateVariables;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
			exposeUriTemplateVariables(this.uriTemplateVariables, request);
			return true;
		}
	}

}
