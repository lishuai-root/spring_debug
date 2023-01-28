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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.util.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Abstract base class for {@link HandlerMapping} implementations that define
 * a mapping between a request and a {@link HandlerMethod}.
 *
 * <p>For each registered handler method, a unique mapping is maintained with
 * subclasses defining the details of the mapping type {@code <T>}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 * @param <T> the mapping for a {@link HandlerMethod} containing the conditions
 * needed to match the handler method to an incoming request.
 */
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {

	/**
	 * Bean name prefix for target beans behind scoped proxies. Used to exclude those
	 * targets from handler method detection, in favor of the corresponding proxies.
	 * 作用域代理后面的目标Bean的Bean名称前缀。用于从处理程序方法检测中排除这些目标，以支持相应的代理。
	 *
	 * <p>We're not checking the autowire-candidate status here, which is how the
	 * proxy target filtering problem is being handled at the autowiring level,
	 * since autowire-candidate may have been turned to {@code false} for other
	 * reasons, while still expecting the bean to be eligible for handler methods.
	 * 这里我们不检查autowire-candidate状态，这是在自动装配级别处理代理目标过滤问题的方式，
	 * 因为autowire-candidate可能由于其他原因被转换为{@code false}，同时仍然期望bean符合处理程序方法的条件。
	 *
	 * <p>Originally defined in {@link org.springframework.aop.scope.ScopedProxyUtils}
	 * but duplicated here to avoid a hard dependency on the spring-aop module.
	 * 最初在{@link org.springframework.aop.scope.ScopedProxyUtils}定义，但在这里复制，以避免对spring-aop模块的硬依赖。
	 */
	private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";

	private static final HandlerMethod PREFLIGHT_AMBIGUOUS_MATCH =
			new HandlerMethod(new EmptyHandler(), ClassUtils.getMethod(EmptyHandler.class, "handle"));

	private static final CorsConfiguration ALLOW_CORS_CONFIG = new CorsConfiguration();

	static {
		ALLOW_CORS_CONFIG.addAllowedOriginPattern("*");
		ALLOW_CORS_CONFIG.addAllowedMethod("*");
		ALLOW_CORS_CONFIG.addAllowedHeader("*");
		ALLOW_CORS_CONFIG.setAllowCredentials(true);
	}


	/**
	 * 是否在父上下文中查找用于处理请求的bean
	 */
	private boolean detectHandlerMethodsInAncestorContexts = false;

	@Nullable
	private HandlerMethodMappingNamingStrategy<T> namingStrategy;

	private final MappingRegistry mappingRegistry = new MappingRegistry();


	@Override
	public void setPatternParser(PathPatternParser patternParser) {
		Assert.state(this.mappingRegistry.getRegistrations().isEmpty(),
				"PathPatternParser must be set before the initialization of " +
						"request mappings through InitializingBean#afterPropertiesSet.");
		super.setPatternParser(patternParser);
	}

	/**
	 * Whether to detect handler methods in beans in ancestor ApplicationContexts.
	 * <p>Default is "false": Only beans in the current ApplicationContext are
	 * considered, i.e. only in the context that this HandlerMapping itself
	 * is defined in (typically the current DispatcherServlet's context).
	 * <p>Switch this flag on to detect handler beans in ancestor contexts
	 * (typically the Spring root WebApplicationContext) as well.
	 * @see #getCandidateBeanNames()
	 */
	public void setDetectHandlerMethodsInAncestorContexts(boolean detectHandlerMethodsInAncestorContexts) {
		this.detectHandlerMethodsInAncestorContexts = detectHandlerMethodsInAncestorContexts;
	}

	/**
	 * Configure the naming strategy to use for assigning a default name to every
	 * mapped handler method.
	 * <p>The default naming strategy is based on the capital letters of the
	 * class name followed by "#" and then the method name, e.g. "TC#getFoo"
	 * for a class named TestController with method getFoo.
	 */
	public void setHandlerMethodMappingNamingStrategy(HandlerMethodMappingNamingStrategy<T> namingStrategy) {
		this.namingStrategy = namingStrategy;
	}

	/**
	 * Return the configured naming strategy or {@code null}.
	 * 返回配置的命名策略或{@code null}。
	 */
	@Nullable
	public HandlerMethodMappingNamingStrategy<T> getNamingStrategy() {
		return this.namingStrategy;
	}

	/**
	 * Return a (read-only) map with all mappings and HandlerMethod's.
	 * 返回一个(只读)映射，包含所有映射和HandlerMethod的映射。
	 */
	public Map<T, HandlerMethod> getHandlerMethods() {
		this.mappingRegistry.acquireReadLock();
		try {
			return Collections.unmodifiableMap(
					this.mappingRegistry.getRegistrations().entrySet().stream()
							.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().handlerMethod)));
		}
		finally {
			this.mappingRegistry.releaseReadLock();
		}
	}

	/**
	 * Return the handler methods for the given mapping name.
	 * @param mappingName the mapping name
	 * @return a list of matching HandlerMethod's or {@code null}; the returned
	 * list will never be modified and is safe to iterate.
	 * @see #setHandlerMethodMappingNamingStrategy
	 */
	@Nullable
	public List<HandlerMethod> getHandlerMethodsForMappingName(String mappingName) {
		return this.mappingRegistry.getHandlerMethodsByMappingName(mappingName);
	}

	/**
	 * Return the internal mapping registry. Provided for testing purposes.
	 */
	MappingRegistry getMappingRegistry() {
		return this.mappingRegistry;
	}

	/**
	 * Register the given mapping.
	 * <p>This method may be invoked at runtime after initialization has completed.
	 * @param mapping the mapping for the handler method
	 * @param handler the handler
	 * @param method the method
	 */
	public void registerMapping(T mapping, Object handler, Method method) {
		if (logger.isTraceEnabled()) {
			logger.trace("Register \"" + mapping + "\" to " + method.toGenericString());
		}
		this.mappingRegistry.register(mapping, handler, method);
	}

	/**
	 * Un-register the given mapping.
	 * <p>This method may be invoked at runtime after initialization has completed.
	 * @param mapping the mapping to unregister
	 */
	public void unregisterMapping(T mapping) {
		if (logger.isTraceEnabled()) {
			logger.trace("Unregister mapping \"" + mapping + "\"");
		}
		this.mappingRegistry.unregister(mapping);
	}


	// Handler method detection

	/**
	 * Detects handler methods at initialization.
	 * 在初始化时检测处理程序方法。
	 *
	 * @see #initHandlerMethods
	 */
	@Override
	public void afterPropertiesSet() {
		initHandlerMethods();
	}

	/**
	 * 扫描所有带有{@link Controller}或者{@link org.springframework.web.bind.annotation.RequestMapping}注解的bean
	 * 解析bean中的处理方法并注册到缓存
	 *
	 * Scan beans in the ApplicationContext, detect and register handler methods.
	 * 扫描ApplicationContext中的bean，检测并注册处理程序方法。
	 *
	 * @see #getCandidateBeanNames()
	 * @see #processCandidateBean
	 * @see #handlerMethodsInitialized
	 */
	protected void initHandlerMethods() {
		for (String beanName : getCandidateBeanNames()) {
			/**
			 * 这里只处理单例模式的bean
			 */
			if (!beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
				/**
				 * 解析指定bean中的处理请求方法，并注册
				 */
				processCandidateBean(beanName);
			}
		}
		/**
		 * 在检测到所有处理程序方法后调用。
		 * 记录日志
		 */
		handlerMethodsInitialized(getHandlerMethods());
	}

	/**
	 * Determine the names of candidate beans in the application context.
	 * 确定应用程序上下文中候选bean的名称。
	 *
	 * @since 5.1
	 * @see #setDetectHandlerMethodsInAncestorContexts
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors
	 */
	protected String[] getCandidateBeanNames() {
		return (this.detectHandlerMethodsInAncestorContexts ?
				/**
				 * 获取当前容器及其父容器中指定类型的bean名称集合
				 */
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(obtainApplicationContext(), Object.class) :
				/**
				 * 获取当前容器中指定类型的bean名称集合
				 */
				obtainApplicationContext().getBeanNamesForType(Object.class));
	}

	/**
	 * 解析指定bean中的处理请求方法，并注册
	 *
	 * Determine the type of the specified candidate bean and call
	 * {@link #detectHandlerMethods} if identified as a handler type.
	 * 确定指定的候选bean的类型，如果标识为处理程序类型，则调用{@link #detectHandlerMethods}。
	 *
	 * <p>This implementation avoids bean creation through checking
	 * {@link org.springframework.beans.factory.BeanFactory#getType}
	 * and calling {@link #detectHandlerMethods} with the bean name.
	 * 这个实现通过检查{@link org.springframework.beans.factory.BeanFactory#getType}和使用bean名称调用{@link #detectHandlerMethods}。
	 *
	 * @param beanName the name of the candidate bean
	 * @since 5.1
	 * @see #isHandler
	 * @see #detectHandlerMethods
	 */
	protected void processCandidateBean(String beanName) {
		Class<?> beanType = null;
		try {
			beanType = obtainApplicationContext().getType(beanName);
		}
		catch (Throwable ex) {
			// An unresolvable bean type, probably from a lazy bean - let's ignore it.
			// 一个无法解析的bean类型，可能来自懒bean—让我们忽略它。
			if (logger.isTraceEnabled()) {
				logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
			}
		}
		/**
		 * 检查bean所属类型中是否存在{@link Controller}或者{@link org.springframework.web.bind.annotation.RequestMapping}注解
		 */
		if (beanType != null && isHandler(beanType)) {
			/**
			 * 解析指定bean中的处理请求方法，并注册
			 */
			detectHandlerMethods(beanName);
		}
	}

	/**
	 * 解析处理程序方法，并注册
	 *
	 * Look for handler methods in the specified handler bean.
	 * 在指定的处理程序bean中查找处理程序方法。
	 *
	 * @param handler either a bean name or an actual handler instance
	 * @see #getMappingForMethod
	 */
	protected void detectHandlerMethods(Object handler) {
		/**
		 * 获取bean的类型
		 */
		Class<?> handlerType = (handler instanceof String ?
				obtainApplicationContext().getType((String) handler) : handler.getClass());

		if (handlerType != null) {
			/**
			 * 返回用户定义的类，如果是代理类返回父类，否则返回当前类
			 */
			Class<?> userType = ClassUtils.getUserClass(handlerType);
			/**
			 * 获取请求处理方法和{@link org.springframework.web.servlet.mvc.method.RequestMappingInfo}实列的映射
			 */
			Map<Method, T> methods = MethodIntrospector.selectMethods(userType,
					/**
					 * 解析方法上的{@link org.springframework.web.bind.annotation.RequestMapping}注解
					 */
					(MethodIntrospector.MetadataLookup<T>) method -> {
						try {
							/**
							 * 通过方法和类上的{@link org.springframework.web.bind.annotation.RequestMapping}注解创建
							 * {@link org.springframework.web.servlet.mvc.method.RequestMappingInfo}实列
							 */
							return getMappingForMethod(method, userType);
						}
						catch (Throwable ex) {
							throw new IllegalStateException("Invalid mapping on handler class [" +
									userType.getName() + "]: " + method, ex);
						}
					});
			if (logger.isTraceEnabled()) {
				logger.trace(formatMappings(userType, methods));
			}
			else if (mappingsLogger.isDebugEnabled()) {
				mappingsLogger.debug(formatMappings(userType, methods));
			}
			methods.forEach((method, mapping) -> {
				/**
				 * 获取可执行方法
				 */
				Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
				/**
				 * 注册到缓存
				 * 在使用{@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}handler映射时，此处的handler是bean名称
				 */
				registerHandlerMethod(handler, invocableMethod, mapping);
			});
		}
	}

	private String formatMappings(Class<?> userType, Map<Method, T> methods) {
		String packageName = ClassUtils.getPackageName(userType);
		String formattedType = (StringUtils.hasText(packageName) ?
				Arrays.stream(packageName.split("\\."))
						.map(packageSegment -> packageSegment.substring(0, 1))
						.collect(Collectors.joining(".", "", "." + userType.getSimpleName())) :
				userType.getSimpleName());
		Function<Method, String> methodFormatter = method -> Arrays.stream(method.getParameterTypes())
				.map(Class::getSimpleName)
				.collect(Collectors.joining(",", "(", ")"));
		return methods.entrySet().stream()
				.map(e -> {
					Method method = e.getKey();
					return e.getValue() + ": " + method.getName() + methodFormatter.apply(method);
				})
				.collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
	}

	/**
	 * Register a handler method and its unique mapping. Invoked at startup for
	 * each detected handler method.
	 * 注册一个处理程序方法及其惟一映射。在启动时为每个检测到的处理程序方法调用。
	 *
	 * @param handler the bean name of the handler or the handler instance
	 * @param method the method to register
	 * @param mapping the mapping conditions associated with the handler method
	 * @throws IllegalStateException if another method was already registered
	 * under the same mapping
	 */
	protected void registerHandlerMethod(Object handler, Method method, T mapping) {
		this.mappingRegistry.register(mapping, handler, method);
	}

	/**
	 * Create the HandlerMethod instance.
	 * 创建HandlerMethod实例。
	 *
	 * @param handler either a bean name or an actual handler instance
	 * @param method the target method
	 * @return the created HandlerMethod
	 */
	protected HandlerMethod createHandlerMethod(Object handler, Method method) {
		if (handler instanceof String) {
			return new HandlerMethod((String) handler,
					obtainApplicationContext().getAutowireCapableBeanFactory(),
					obtainApplicationContext(),
					method);
		}
		return new HandlerMethod(handler, method);
	}

	/**
	 * Extract and return the CORS configuration for the mapping.
	 * 提取并返回用于映射的CORS配置。
	 */
	@Nullable
	protected CorsConfiguration initCorsConfiguration(Object handler, Method method, T mapping) {
		return null;
	}

	/**
	 * Invoked after all handler methods have been detected.
	 * 在检测到所有处理程序方法后调用。
	 *
	 * @param handlerMethods a read-only map with handler methods and mappings.
	 */
	protected void handlerMethodsInitialized(Map<T, HandlerMethod> handlerMethods) {
		// Total includes detected mappings + explicit registrations via registerMapping
		/**
		 * Total包括通过registerMapping检测到的映射+显式注册
		 */
		int total = handlerMethods.size();
		if ((logger.isTraceEnabled() && total == 0) || (logger.isDebugEnabled() && total > 0) ) {
			logger.debug(total + " mappings in " + formatMappingName());
		}
	}


	// Handler method lookup

	/**
	 * Look up a handler method for the given request.
	 *
	 * 查找给定请求的处理程序方法。
	 */
	@Override
	@Nullable
	protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
		String lookupPath = initLookupPath(request);
		this.mappingRegistry.acquireReadLock();
		try {
			/**
			 * 获取最匹配当前请求的处理程序
			 */
			HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
			/**
			 * 解析并创建调用处理程序的bean实例
			 */
			return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
		}
		finally {
			this.mappingRegistry.releaseReadLock();
		}
	}

	/**
	 * Look up the best-matching handler method for the current request.
	 * If multiple matches are found, the best match is selected.
	 * 查找当前请求的最佳匹配处理程序方法。如果找到多个匹配项，则选择最佳匹配项。
	 *
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @return the best-matching handler method, or {@code null} if no match
	 * @see #handleMatch(Object, String, HttpServletRequest)
	 * @see #handleNoMatch(Set, String, HttpServletRequest)
	 */
	@Nullable
	protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
		List<Match> matches = new ArrayList<>();
		/**
		 * 首先通过已经注册的请求路径获取当前请求路径对应的处理程序集合
		 */
		List<T> directPathMatches = this.mappingRegistry.getMappingsByDirectPath(lookupPath);
		/**
		 * 获取合适的处理程序
		 */
		if (directPathMatches != null) {
			addMatchingMappings(directPathMatches, matches, request);
		}
		/**
		 * 通过模式匹配合适当前请求的处理程序(正则表达式)
		 */
		if (matches.isEmpty()) {
			addMatchingMappings(this.mappingRegistry.getRegistrations().keySet(), matches, request);
		}
		if (!matches.isEmpty()) {
			Match bestMatch = matches.get(0);
			if (matches.size() > 1) {
				/**
				 * 如果匹配到多个合适当前请求的处理程序，通过排序确定最合适的处理程序
				 */
				Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
				matches.sort(comparator);
				bestMatch = matches.get(0);
				if (logger.isTraceEnabled()) {
					logger.trace(matches.size() + " matching mappings: " + matches);
				}
				/**
				 * 验证跨域预检请求
				 */
				if (CorsUtils.isPreFlightRequest(request)) {
					for (Match match : matches) {
						if (match.hasCorsConfig()) {
							return PREFLIGHT_AMBIGUOUS_MATCH;
						}
					}
				}
				else {
					/**
					 * 验证最匹配的处理程序只能有一个，如果有多个最匹配的处理程序，直接抛出异常
					 */
					Match secondBestMatch = matches.get(1);
					if (comparator.compare(bestMatch, secondBestMatch) == 0) {
						Method m1 = bestMatch.getHandlerMethod().getMethod();
						Method m2 = secondBestMatch.getHandlerMethod().getMethod();
						String uri = request.getRequestURI();
						throw new IllegalStateException(
								"Ambiguous handler methods mapped for '" + uri + "': {" + m1 + ", " + m2 + "}");
					}
				}
			}
			/**
			 * 为当前请求设置最匹配的处理程序
			 */
			request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, bestMatch.getHandlerMethod());
			handleMatch(bestMatch.mapping, lookupPath, request);
			return bestMatch.getHandlerMethod();
		}
		else {
			return handleNoMatch(this.mappingRegistry.getRegistrations().keySet(), lookupPath, request);
		}
	}

	private void addMatchingMappings(Collection<T> mappings, List<Match> matches, HttpServletRequest request) {
		for (T mapping : mappings) {
			T match = getMatchingMapping(mapping, request);
			if (match != null) {
				matches.add(new Match(match, this.mappingRegistry.getRegistrations().get(mapping)));
			}
		}
	}

	/**
	 * Invoked when a matching mapping is found.
	 * 在找到匹配映射时调用。
	 *
	 * @param mapping the matching mapping
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 */
	protected void handleMatch(T mapping, String lookupPath, HttpServletRequest request) {
		request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, lookupPath);
	}

	/**
	 * Invoked when no matching mapping is not found.
	 * 在没有找到匹配映射时调用。
	 *
	 * @param mappings all registered mappings
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @throws ServletException in case of errors
	 */
	@Nullable
	protected HandlerMethod handleNoMatch(Set<T> mappings, String lookupPath, HttpServletRequest request)
			throws Exception {

		return null;
	}

	@Override
	protected boolean hasCorsConfigurationSource(Object handler) {
		return super.hasCorsConfigurationSource(handler) ||
				(handler instanceof HandlerMethod &&
						this.mappingRegistry.getCorsConfiguration((HandlerMethod) handler) != null);
	}

	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		CorsConfiguration corsConfig = super.getCorsConfiguration(handler, request);
		if (handler instanceof HandlerMethod) {
			HandlerMethod handlerMethod = (HandlerMethod) handler;
			if (handlerMethod.equals(PREFLIGHT_AMBIGUOUS_MATCH)) {
				return AbstractHandlerMethodMapping.ALLOW_CORS_CONFIG;
			}
			else {
				CorsConfiguration corsConfigFromMethod = this.mappingRegistry.getCorsConfiguration(handlerMethod);
				corsConfig = (corsConfig != null ? corsConfig.combine(corsConfigFromMethod) : corsConfigFromMethod);
			}
		}
		return corsConfig;
	}


	// Abstract template methods

	/**
	 * Whether the given type is a handler with handler methods.
	 * 给定类型是否是具有处理程序方法的处理程序。
	 *
	 * @param beanType the type of the bean being checked
	 * @return "true" if this a handler type, "false" otherwise.
	 */
	protected abstract boolean isHandler(Class<?> beanType);

	/**
	 * Provide the mapping for a handler method. A method for which no
	 * mapping can be provided is not a handler method.
	 * 为处理程序方法提供映射。不能提供映射的方法不是处理程序方法。
	 *
	 * @param method the method to provide a mapping for
	 * @param handlerType the handler type, possibly a sub-type of the method's
	 * declaring class
	 * @return the mapping, or {@code null} if the method is not mapped
	 */
	@Nullable
	protected abstract T getMappingForMethod(Method method, Class<?> handlerType);

	/**
	 * Extract and return the URL paths contained in the supplied mapping.
	 * @deprecated as of 5.3 in favor of providing non-pattern mappings via
	 * {@link #getDirectPaths(Object)} instead
	 */
	@Deprecated
	protected Set<String> getMappingPathPatterns(T mapping) {
		return Collections.emptySet();
	}

	/**
	 * Return the request mapping paths that are not patterns.
	 * 返回不是模式的请求映射路径。
	 *
	 * @since 5.3
	 */
	protected Set<String> getDirectPaths(T mapping) {
		Set<String> urls = Collections.emptySet();
		for (String path : getMappingPathPatterns(mapping)) {
			if (!getPathMatcher().isPattern(path)) {
				urls = (urls.isEmpty() ? new HashSet<>(1) : urls);
				urls.add(path);
			}
		}
		return urls;
	}

	/**
	 * Check if a mapping matches the current request and return a (potentially
	 * new) mapping with conditions relevant to the current request.
	 * 检查映射是否与当前请求匹配，并返回具有与当前请求相关条件的映射(可能是新的)。
	 *
	 * @param mapping the mapping to get a match for
	 * @param request the current HTTP servlet request
	 * @return the match, or {@code null} if the mapping doesn't match
	 */
	@Nullable
	protected abstract T getMatchingMapping(T mapping, HttpServletRequest request);

	/**
	 * Return a comparator for sorting matching mappings.
	 * The returned comparator should sort 'better' matches higher.
	 * @param request the current request
	 * @return the comparator (never {@code null})
	 */
	protected abstract Comparator<T> getMappingComparator(HttpServletRequest request);


	/**
	 * A registry that maintains all mappings to handler methods, exposing methods
	 * to perform lookups and providing concurrent access.
	 * 维护到处理程序方法的所有映射的注册中心，公开方法以执行查找并提供并发访问。
	 *
	 * <p>Package-private for testing purposes.
	 * 包私有，用于测试目的。
	 */
	class MappingRegistry {

		/**
		 * 保存requestMapping和处理处理方法等信息的映射
		 */
		private final Map<T, MappingRegistration<T>> registry = new HashMap<>();

		/**
		 * 用来保存所有解析到的请求路径和requestMapping
		 */
		private final MultiValueMap<String, T> pathLookup = new LinkedMultiValueMap<>();

		/**
		 * 保存方法简单名称和可以处理请求的映射
		 * 方法的简单名称：类名中的大写字符 + "#" + 方法名称
		 */
		private final Map<String, List<HandlerMethod>> nameLookup = new ConcurrentHashMap<>();

		/**
		 * 用来保存请求处理方法和跨域配置的映射
		 */
		private final Map<HandlerMethod, CorsConfiguration> corsLookup = new ConcurrentHashMap<>();

		/**
		 * 在添加请求才处理程序和requestMapping时使用
		 */
		private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

		/**
		 * Return all registrations.
		 * @since 5.3
		 */
		public Map<T, MappingRegistration<T>> getRegistrations() {
			return this.registry;
		}

		/**
		 * Return matches for the given URL path. Not thread-safe.
		 * 返回匹配的给定URL路径。不是线程安全的。
		 *
		 * @see #acquireReadLock()
		 */
		@Nullable
		public List<T> getMappingsByDirectPath(String urlPath) {
			return this.pathLookup.get(urlPath);
		}

		/**
		 * Return handler methods by mapping name. Thread-safe for concurrent use.
		 */
		public List<HandlerMethod> getHandlerMethodsByMappingName(String mappingName) {
			return this.nameLookup.get(mappingName);
		}

		/**
		 * Return CORS configuration. Thread-safe for concurrent use.
		 */
		@Nullable
		public CorsConfiguration getCorsConfiguration(HandlerMethod handlerMethod) {
			HandlerMethod original = handlerMethod.getResolvedFromHandlerMethod();
			return this.corsLookup.get(original != null ? original : handlerMethod);
		}

		/**
		 * Acquire the read lock when using getMappings and getMappingsByUrl.
		 * 在使用getMappings和getMappingsByUrl时获取读锁。
		 */
		public void acquireReadLock() {
			this.readWriteLock.readLock().lock();
		}

		/**
		 * Release the read lock after using getMappings and getMappingsByUrl.
		 */
		public void releaseReadLock() {
			this.readWriteLock.readLock().unlock();
		}

		/**
		 * 解析并注册请求的处理程序
		 *
		 * @param mapping
		 * @param handler
		 * @param method
		 */
		public void register(T mapping, Object handler, Method method) {
			this.readWriteLock.writeLock().lock();
			try {
				/**
				 * 创建{@link HandlerMethod}实列
				 * 该实列封装了处理请求的bean，方法，beanFactory等，bean可以是bean实列也可以是bean名称
				 */
				HandlerMethod handlerMethod = createHandlerMethod(handler, method);
				/**
				 * 此处检查是否存在同样的requestMapping对应不同的处理程序,如果存在抛出异常
				 * requestMapping的比较方法{@link org.springframework.web.servlet.mvc.method.RequestMappingInfo#equals(Object)}
				 */
				validateMethodMapping(handlerMethod, mapping);

				Set<String> directPaths = AbstractHandlerMethodMapping.this.getDirectPaths(mapping);
				/**
				 * 注册请求路径和requestMapping映射
				 */
				for (String path : directPaths) {
					this.pathLookup.add(path, mapping);
				}

				String name = null;
				if (getNamingStrategy() != null) {
					/**
					 * 创建处理程序的名称
					 * 类名中大写字母 + "#" + 方法名称
					 */
					name = getNamingStrategy().getName(handlerMethod, mapping);
					/**
					 * 注册处理程序名称和处理程序的映射关系
					 */
					addMappingName(name, handlerMethod);
				}

				/**
				 * 解析处理程序的{@link org.springframework.web.bind.annotation.CrossOrigin}注解，{@link CorsConfiguration}类中包含了跨域配置的所有属性
				 */
				CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
				if (corsConfig != null) {
					/**
					 * 检查当允许请求携带凭证时，跨域允许的源必须是"*"，否则抛出异常
					 */
					corsConfig.validateAllowCredentials();
					/**
					 * 注册处理程序和跨域配置的映射关系
					 */
					this.corsLookup.put(handlerMethod, corsConfig);
				}

				/**
				 * 注册请求处理程序的相关信息
				 */
				this.registry.put(mapping,
						new MappingRegistration<>(mapping, handlerMethod, directPaths, name, corsConfig != null));
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}

		private void validateMethodMapping(HandlerMethod handlerMethod, T mapping) {
			MappingRegistration<T> registration = this.registry.get(mapping);
			HandlerMethod existingHandlerMethod = (registration != null ? registration.getHandlerMethod() : null);
			if (existingHandlerMethod != null && !existingHandlerMethod.equals(handlerMethod)) {
				throw new IllegalStateException(
						"Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" +
						handlerMethod + "\nto " + mapping + ": There is already '" +
						existingHandlerMethod.getBean() + "' bean method\n" + existingHandlerMethod + " mapped.");
			}
		}

		private void addMappingName(String name, HandlerMethod handlerMethod) {
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				oldList = Collections.emptyList();
			}

			/**
			 * 如果给定名称和处理程序已经存在映射关系，直接返回
			 */
			for (HandlerMethod current : oldList) {
				if (handlerMethod.equals(current)) {
					return;
				}
			}

			List<HandlerMethod> newList = new ArrayList<>(oldList.size() + 1);
			/**
			 * 将给定处理程序和给定名称原有的处理程序合并
			 */
			newList.addAll(oldList);
			newList.add(handlerMethod);
			this.nameLookup.put(name, newList);
		}

		public void unregister(T mapping) {
			this.readWriteLock.writeLock().lock();
			try {
				MappingRegistration<T> registration = this.registry.remove(mapping);
				if (registration == null) {
					return;
				}

				for (String path : registration.getDirectPaths()) {
					List<T> mappings = this.pathLookup.get(path);
					if (mappings != null) {
						mappings.remove(registration.getMapping());
						if (mappings.isEmpty()) {
							this.pathLookup.remove(path);
						}
					}
				}

				removeMappingName(registration);

				this.corsLookup.remove(registration.getHandlerMethod());
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}

		private void removeMappingName(MappingRegistration<T> definition) {
			String name = definition.getMappingName();
			if (name == null) {
				return;
			}
			HandlerMethod handlerMethod = definition.getHandlerMethod();
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				return;
			}
			if (oldList.size() <= 1) {
				this.nameLookup.remove(name);
				return;
			}
			List<HandlerMethod> newList = new ArrayList<>(oldList.size() - 1);
			for (HandlerMethod current : oldList) {
				if (!current.equals(handlerMethod)) {
					newList.add(current);
				}
			}
			this.nameLookup.put(name, newList);
		}
	}


	/**
	 * 请求处理程序的封装类
	 * 包含了处理程序，requestMapping，请求路径，是否允许跨域等信息
	 * @param <T>
	 */
	static class MappingRegistration<T> {

		private final T mapping;

		private final HandlerMethod handlerMethod;

		private final Set<String> directPaths;

		@Nullable
		private final String mappingName;

		private final boolean corsConfig;

		public MappingRegistration(T mapping, HandlerMethod handlerMethod,
				@Nullable Set<String> directPaths, @Nullable String mappingName, boolean corsConfig) {

			Assert.notNull(mapping, "Mapping must not be null");
			Assert.notNull(handlerMethod, "HandlerMethod must not be null");
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
			this.directPaths = (directPaths != null ? directPaths : Collections.emptySet());
			this.mappingName = mappingName;
			this.corsConfig = corsConfig;
		}

		public T getMapping() {
			return this.mapping;
		}

		public HandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public Set<String> getDirectPaths() {
			return this.directPaths;
		}

		@Nullable
		public String getMappingName() {
			return this.mappingName;
		}

		public boolean hasCorsConfig() {
			return this.corsConfig;
		}
	}


	/**
	 * A thin wrapper around a matched HandlerMethod and its mapping, for the purpose of
	 * comparing the best match with a comparator in the context of the current request.
	 */
	private class Match {

		private final T mapping;

		private final MappingRegistration<T> registration;

		public Match(T mapping, MappingRegistration<T> registration) {
			this.mapping = mapping;
			this.registration = registration;
		}

		public HandlerMethod getHandlerMethod() {
			return this.registration.getHandlerMethod();
		}

		public boolean hasCorsConfig() {
			return this.registration.hasCorsConfig();
		}

		@Override
		public String toString() {
			return this.mapping.toString();
		}
	}


	private class MatchComparator implements Comparator<Match> {

		private final Comparator<T> comparator;

		public MatchComparator(Comparator<T> comparator) {
			this.comparator = comparator;
		}

		@Override
		public int compare(Match match1, Match match2) {
			return this.comparator.compare(match1.mapping, match2.mapping);
		}
	}


	private static class EmptyHandler {

		@SuppressWarnings("unused")
		public void handle() {
			throw new UnsupportedOperationException("Not implemented");
		}
	}

}
