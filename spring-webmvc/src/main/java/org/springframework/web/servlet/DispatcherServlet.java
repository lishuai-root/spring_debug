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

package org.springframework.web.servlet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.ui.context.ThemeSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central dispatcher for HTTP request handlers/controllers, e.g. for web UI controllers
 * or HTTP-based remote service exporters. Dispatches to registered handlers for processing
 * a web request, providing convenient mapping and exception handling facilities.
 *
 * <p>This servlet is very flexible: It can be used with just about any workflow, with the
 * installation of the appropriate adapter classes. It offers the following functionality
 * that distinguishes it from other request-driven web MVC frameworks:
 *
 * <ul>
 * <li>It is based around a JavaBeans configuration mechanism.
 *
 * <li>It can use any {@link HandlerMapping} implementation - pre-built or provided as part
 * of an application - to control the routing of requests to handler objects. Default is
 * {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping} and
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}.
 * HandlerMapping objects can be defined as beans in the servlet's application context,
 * implementing the HandlerMapping interface, overriding the default HandlerMapping if
 * present. HandlerMappings can be given any bean name (they are tested by type).
 *
 * <li>It can use any {@link HandlerAdapter}; this allows for using any handler interface.
 * Default adapters are {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter},
 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter}, for Spring's
 * {@link org.springframework.web.HttpRequestHandler} and
 * {@link org.springframework.web.servlet.mvc.Controller} interfaces, respectively. A default
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
 * will be registered as well. HandlerAdapter objects can be added as beans in the
 * application context, overriding the default HandlerAdapters. Like HandlerMappings,
 * HandlerAdapters can be given any bean name (they are tested by type).
 *
 * <li>The dispatcher's exception resolution strategy can be specified via a
 * {@link HandlerExceptionResolver}, for example mapping certain exceptions to error pages.
 * Default are
 * {@link org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver},
 * {@link org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver}, and
 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}.
 * These HandlerExceptionResolvers can be overridden through the application context.
 * HandlerExceptionResolver can be given any bean name (they are tested by type).
 *
 * <li>Its view resolution strategy can be specified via a {@link ViewResolver}
 * implementation, resolving symbolic view names into View objects. Default is
 * {@link org.springframework.web.servlet.view.InternalResourceViewResolver}.
 * ViewResolver objects can be added as beans in the application context, overriding the
 * default ViewResolver. ViewResolvers can be given any bean name (they are tested by type).
 *
 * <li>If a {@link View} or view name is not supplied by the user, then the configured
 * {@link RequestToViewNameTranslator} will translate the current request into a view name.
 * The corresponding bean name is "viewNameTranslator"; the default is
 * {@link org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator}.
 *
 * <li>The dispatcher's strategy for resolving multipart requests is determined by a
 * {@link org.springframework.web.multipart.MultipartResolver} implementation.
 * Implementations for Apache Commons FileUpload and Servlet 3 are included; the typical
 * choice is {@link org.springframework.web.multipart.commons.CommonsMultipartResolver}.
 * The MultipartResolver bean name is "multipartResolver"; default is none.
 *
 * <li>Its locale resolution strategy is determined by a {@link LocaleResolver}.
 * Out-of-the-box implementations work via HTTP accept header, cookie, or session.
 * The LocaleResolver bean name is "localeResolver"; default is
 * {@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver}.
 *
 * <li>Its theme resolution strategy is determined by a {@link ThemeResolver}.
 * Implementations for a fixed theme and for cookie and session storage are included.
 * The ThemeResolver bean name is "themeResolver"; default is
 * {@link org.springframework.web.servlet.theme.FixedThemeResolver}.
 * </ul>
 *
 * <p><b>NOTE: The {@code @RequestMapping} annotation will only be processed if a
 * corresponding {@code HandlerMapping} (for type-level annotations) and/or
 * {@code HandlerAdapter} (for method-level annotations) is present in the dispatcher.</b>
 * This is the case by default. However, if you are defining custom {@code HandlerMappings}
 * or {@code HandlerAdapters}, then you need to make sure that a corresponding custom
 * {@code RequestMappingHandlerMapping} and/or {@code RequestMappingHandlerAdapter}
 * is defined as well - provided that you intend to use {@code @RequestMapping}.
 *
 * <p><b>A web application can define any number of DispatcherServlets.</b>
 * Each servlet will operate in its own namespace, loading its own application context
 * with mappings, handlers, etc. Only the root application context as loaded by
 * {@link org.springframework.web.context.ContextLoaderListener}, if any, will be shared.
 *
 * <p>As of Spring 3.1, {@code DispatcherServlet} may now be injected with a web
 * application context, rather than creating its own internally. This is useful in Servlet
 * 3.0+ environments, which support programmatic registration of servlet instances.
 * See the {@link #DispatcherServlet(WebApplicationContext)} javadoc for details.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 * @see org.springframework.web.HttpRequestHandler
 * @see org.springframework.web.servlet.mvc.Controller
 * @see org.springframework.web.context.ContextLoaderListener
 */
@SuppressWarnings("serial")
public class DispatcherServlet extends FrameworkServlet {

	/** Well-known name for the MultipartResolver object in the bean factory for this namespace. */
	public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

	/** Well-known name for the LocaleResolver object in the bean factory for this namespace. */
	public static final String LOCALE_RESOLVER_BEAN_NAME = "localeResolver";

	/** Well-known name for the ThemeResolver object in the bean factory for this namespace. */
	public static final String THEME_RESOLVER_BEAN_NAME = "themeResolver";

	/**
	 * Well-known name for the HandlerMapping object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerMappings" is turned off.
	 * @see #setDetectAllHandlerMappings
	 */
	public static final String HANDLER_MAPPING_BEAN_NAME = "handlerMapping";

	/**
	 * Well-known name for the HandlerAdapter object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerAdapters" is turned off.
	 * @see #setDetectAllHandlerAdapters
	 */
	public static final String HANDLER_ADAPTER_BEAN_NAME = "handlerAdapter";

	/**
	 * Well-known name for the HandlerExceptionResolver object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerExceptionResolvers" is turned off.
	 * @see #setDetectAllHandlerExceptionResolvers
	 */
	public static final String HANDLER_EXCEPTION_RESOLVER_BEAN_NAME = "handlerExceptionResolver";

	/**
	 * Well-known name for the RequestToViewNameTranslator object in the bean factory for this namespace.
	 */
	public static final String REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME = "viewNameTranslator";

	/**
	 * Well-known name for the ViewResolver object in the bean factory for this namespace.
	 * Only used when "detectAllViewResolvers" is turned off.
	 * @see #setDetectAllViewResolvers
	 */
	public static final String VIEW_RESOLVER_BEAN_NAME = "viewResolver";

	/**
	 * Well-known name for the FlashMapManager object in the bean factory for this namespace.
	 */
	public static final String FLASH_MAP_MANAGER_BEAN_NAME = "flashMapManager";

	/**
	 * Request attribute to hold the current web application context.
	 * 请求属性保存当前web应用程序上下文。
	 *
	 * Otherwise only the global web app context is obtainable by tags etc.
	 * 否则，只有全局web应用上下文可以通过标签等获得。
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#findWebApplicationContext
	 */
	public static final String WEB_APPLICATION_CONTEXT_ATTRIBUTE = DispatcherServlet.class.getName() + ".CONTEXT";

	/**
	 * Request attribute to hold the current LocaleResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getLocaleResolver
	 */
	public static final String LOCALE_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".LOCALE_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeResolver
	 */
	public static final String THEME_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeSource, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeSource
	 */
	public static final String THEME_SOURCE_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_SOURCE";

	/**
	 * Name of request attribute that holds a read-only {@code Map<String,?>}
	 * with "input" flash attributes saved by a previous request, if any.
	 * 持有只读{@code Map<String，?>}的"input" flash属性保存了以前的请求，如果有。
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getInputFlashMap(HttpServletRequest)
	 */
	public static final String INPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".INPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the "output" {@link FlashMap} with
	 * attributes to save for a subsequent request.
	 * 包含“输出”{@link FlashMap}的请求属性的名称，其中包含为后续请求保存的属性。
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getOutputFlashMap(HttpServletRequest)
	 */
	public static final String OUTPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".OUTPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the {@link FlashMapManager}.
	 * 包含{@link FlashMapManager}的请求属性的名称。
	 *
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getFlashMapManager(HttpServletRequest)
	 */
	public static final String FLASH_MAP_MANAGER_ATTRIBUTE = DispatcherServlet.class.getName() + ".FLASH_MAP_MANAGER";

	/**
	 * Name of request attribute that exposes an Exception resolved with a
	 * {@link HandlerExceptionResolver} but where no view was rendered
	 * (e.g. setting the status code).
	 *
	 * 请求属性的名称，该属性公开了一个用{@link HandlerExceptionResolver}解析的异常，但没有呈现视图(例如设置状态代码)。
	 */
	public static final String EXCEPTION_ATTRIBUTE = DispatcherServlet.class.getName() + ".EXCEPTION";

	/** Log category to use when no mapped handler is found for a request. */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Name of the class path resource (relative to the DispatcherServlet class)
	 * that defines DispatcherServlet's default strategy names.
	 *
	 * 类路径资源的名称(相对于DispatcherServlet类)，它定义了DispatcherServlet的默认策略名称。
	 */
	private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

	/**
	 * Common prefix that DispatcherServlet's default strategy attributes start with.
	 * DispatcherServlet默认策略属性的开头前缀。
	 */
	private static final String DEFAULT_STRATEGIES_PREFIX = "org.springframework.web.servlet";

	/** Additional logger to use when no mapped handler is found for a request. */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	/** Store default strategy implementations. 存储默认策略实现。 */
	@Nullable
	private static Properties defaultStrategies;

	/** Detect all HandlerMappings or just expect "handlerMapping" bean?.
	 * 检测所有handlerMapping还是仅仅期望“handlerMapping”bean?
	 * */
	private boolean detectAllHandlerMappings = true;

	/** Detect all HandlerAdapters or just expect "handlerAdapter" bean?.
	 * 检测所有的handlerAdapter还是仅仅期望“handlerAdapter”bean?
	 * */
	private boolean detectAllHandlerAdapters = true;

	/** Detect all HandlerExceptionResolvers or just expect "handlerExceptionResolver" bean?.
	 * 检测所有的handlerExceptionResolver还是仅仅期望“handlerExceptionResolver”bean?
	 * */
	private boolean detectAllHandlerExceptionResolvers = true;

	/** Detect all ViewResolvers or just expect "viewResolver" bean?.
	 * 检测所有的viewResolver还是仅仅期望“viewResolver”bean?
	 * */
	private boolean detectAllViewResolvers = true;

	/** Throw a NoHandlerFoundException if no Handler was found to process this request?
	 * 抛出一个NoHandlerFoundException如果没有发现处理程序处理这个请求?
	 * *.*/
	private boolean throwExceptionIfNoHandlerFound = false;

	/** Perform cleanup of request attributes after include request?.
	 * 在包含请求后执行请求属性清理。
	 * */
	private boolean cleanupAfterInclude = true;

	/** MultipartResolver used by this servlet.
	 * 这个servlet使用的MultipartResolver。
	 * */
	@Nullable
	private MultipartResolver multipartResolver;

	/** LocaleResolver used by this servlet.
	 * 这个servlet使用的LocaleResolver。
	 * */
	@Nullable
	private LocaleResolver localeResolver;

	/** ThemeResolver used by this servlet. */
	@Nullable
	private ThemeResolver themeResolver;

	/** List of HandlerMappings used by this servlet.
	 * 这个servlet使用的HandlerMappings的列表。
	 * */
	@Nullable
	private List<HandlerMapping> handlerMappings;

	/** List of HandlerAdapters used by this servlet.
	 * 这个servlet使用的handleradapter列表。
	 * */
	@Nullable
	private List<HandlerAdapter> handlerAdapters;

	/** List of HandlerExceptionResolvers used by this servlet. */
	@Nullable
	private List<HandlerExceptionResolver> handlerExceptionResolvers;

	/** RequestToViewNameTranslator used by this servlet. */
	@Nullable
	private RequestToViewNameTranslator viewNameTranslator;

	/** FlashMapManager used by this servlet. 这个servlet使用的FlashMapManager。 */
	@Nullable
	private FlashMapManager flashMapManager;

	/** List of ViewResolvers used by this servlet. */
	@Nullable
	private List<ViewResolver> viewResolvers;

	private boolean parseRequestPath;


	/**
	 * Create a new {@code DispatcherServlet} that will create its own internal web
	 * application context based on defaults and values provided through servlet
	 * init-params. Typically used in Servlet 2.5 or earlier environments, where the only
	 * option for servlet registration is through {@code web.xml} which requires the use
	 * of a no-arg constructor.
	 * <p>Calling {@link #setContextConfigLocation} (init-param 'contextConfigLocation')
	 * will dictate which XML files will be loaded by the
	 * {@linkplain #DEFAULT_CONTEXT_CLASS default XmlWebApplicationContext}
	 * <p>Calling {@link #setContextClass} (init-param 'contextClass') overrides the
	 * default {@code XmlWebApplicationContext} and allows for specifying an alternative class,
	 * such as {@code AnnotationConfigWebApplicationContext}.
	 * <p>Calling {@link #setContextInitializerClasses} (init-param 'contextInitializerClasses')
	 * indicates which {@code ApplicationContextInitializer} classes should be used to
	 * further configure the internal application context prior to refresh().
	 * @see #DispatcherServlet(WebApplicationContext)
	 */
	public DispatcherServlet() {
		super();
		setDispatchOptionsRequest(true);
	}

	/**
	 * Create a new {@code DispatcherServlet} with the given web application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based registration
	 * of servlets is possible through the {@link ServletContext#addServlet} API.
	 * <p>Using this constructor indicates that the following properties / init-params
	 * will be ignored:
	 * <ul>
	 * <li>{@link #setContextClass(Class)} / 'contextClass'</li>
	 * <li>{@link #setContextConfigLocation(String)} / 'contextConfigLocation'</li>
	 * <li>{@link #setContextAttribute(String)} / 'contextAttribute'</li>
	 * <li>{@link #setNamespace(String)} / 'namespace'</li>
	 * </ul>
	 * <p>The given web application context may or may not yet be {@linkplain
	 * ConfigurableApplicationContext#refresh() refreshed}. If it has <strong>not</strong>
	 * already been refreshed (the recommended approach), then the following will occur:
	 * <ul>
	 * <li>If the given context does not already have a {@linkplain
	 * ConfigurableApplicationContext#setParent parent}, the root application context
	 * will be set as the parent.</li>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #postProcessWebApplicationContext} will be called</li>
	 * <li>Any {@code ApplicationContextInitializer}s specified through the
	 * "contextInitializerClasses" init-param or through the {@link
	 * #setContextInitializers} property will be applied.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh refresh()} will be called if the
	 * context implements {@link ConfigurableApplicationContext}</li>
	 * </ul>
	 * If the context has already been refreshed, none of the above will occur, under the
	 * assumption that the user has performed these actions (or not) per their specific
	 * needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * @param webApplicationContext the context to use
	 * @see #initWebApplicationContext
	 * @see #configureAndRefreshWebApplicationContext
	 * @see org.springframework.web.WebApplicationInitializer
	 */
	public DispatcherServlet(WebApplicationContext webApplicationContext) {
		super(webApplicationContext);
		setDispatchOptionsRequest(true);
	}


	/**
	 * Set whether to detect all HandlerMapping beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerMapping" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerMapping, despite multiple HandlerMapping beans being defined in the context.
	 */
	public void setDetectAllHandlerMappings(boolean detectAllHandlerMappings) {
		this.detectAllHandlerMappings = detectAllHandlerMappings;
	}

	/**
	 * Set whether to detect all HandlerAdapter beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerAdapter" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerAdapter, despite multiple HandlerAdapter beans being defined in the context.
	 */
	public void setDetectAllHandlerAdapters(boolean detectAllHandlerAdapters) {
		this.detectAllHandlerAdapters = detectAllHandlerAdapters;
	}

	/**
	 * Set whether to detect all HandlerExceptionResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerExceptionResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerExceptionResolver, despite multiple HandlerExceptionResolver beans being defined in the context.
	 */
	public void setDetectAllHandlerExceptionResolvers(boolean detectAllHandlerExceptionResolvers) {
		this.detectAllHandlerExceptionResolvers = detectAllHandlerExceptionResolvers;
	}

	/**
	 * Set whether to detect all ViewResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "viewResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * ViewResolver, despite multiple ViewResolver beans being defined in the context.
	 */
	public void setDetectAllViewResolvers(boolean detectAllViewResolvers) {
		this.detectAllViewResolvers = detectAllViewResolvers;
	}

	/**
	 * Set whether to throw a NoHandlerFoundException when no Handler was found for this request.
	 * This exception can then be caught with a HandlerExceptionResolver or an
	 * {@code @ExceptionHandler} controller method.
	 * <p>Note that if {@link org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler}
	 * is used, then requests will always be forwarded to the default servlet and a
	 * NoHandlerFoundException would never be thrown in that case.
	 * <p>Default is "false", meaning the DispatcherServlet sends a NOT_FOUND error through the
	 * Servlet response.
	 * @since 4.0
	 */
	public void setThrowExceptionIfNoHandlerFound(boolean throwExceptionIfNoHandlerFound) {
		this.throwExceptionIfNoHandlerFound = throwExceptionIfNoHandlerFound;
	}

	/**
	 * Set whether to perform cleanup of request attributes after an include request, that is,
	 * whether to reset the original state of all request attributes after the DispatcherServlet
	 * has processed within an include request. Otherwise, just the DispatcherServlet's own
	 * request attributes will be reset, but not model attributes for JSPs or special attributes
	 * set by views (for example, JSTL's).
	 * <p>Default is "true", which is strongly recommended. Views should not rely on request attributes
	 * having been set by (dynamic) includes. This allows JSP views rendered by an included controller
	 * to use any model attributes, even with the same names as in the main JSP, without causing side
	 * effects. Only turn this off for special needs, for example to deliberately allow main JSPs to
	 * access attributes from JSP views rendered by an included controller.
	 */
	public void setCleanupAfterInclude(boolean cleanupAfterInclude) {
		this.cleanupAfterInclude = cleanupAfterInclude;
	}


	/**
	 * This implementation calls {@link #initStrategies}.
	 */
	@Override
	protected void onRefresh(ApplicationContext context) {
		initStrategies(context);
	}

	/**
	 * Initialize the strategy objects that this servlet uses.
	 * <p>May be overridden in subclasses in order to initialize further strategy objects.
	 *
	 * 初始化此servlet使用的策略对象。可以在子类中重写以初始化进一步的策略对象。
	 */
	protected void initStrategies(ApplicationContext context) {
		/**
		 * 初始化 MultipartResolver:主要用来处理文件上传.如果定义过当前类型的bean对象，那么直接获取，如果没有的话，可以为null
		 */
		initMultipartResolver(context);
		/**
		 * 初始化 LocaleResolver:主要用来处理国际化配置,
		 * 基于URL参数的配置(AcceptHeaderLocaleResolver)，
		 * 基于session的配置(SessionLocaleResolver)，
		 * 基于cookie的配置(CookieLocaleResolver)
		 */
		initLocaleResolver(context);
		/**
		 * 初始化 ThemeResolver:主要用来设置主题Theme
		 */
		initThemeResolver(context);
		/**
		 * 初始化 HandlerMapping:映射器，用来将对应的request跟controller进行对应
		 */
		initHandlerMappings(context);
		/**
		 * 初始化 HandlerAdapter:处理适配器，主要包含Http请求处理器适配器，简单控制器处理器适配器，注解方法处理器适配器
		 */
		initHandlerAdapters(context);
		/**
		 * 初始化 HandlerExceptionResolver:基于HandlerExceptionResolver接口的异常处理
		 */
		initHandlerExceptionResolvers(context);
		/**
		 * 初始化 RequestToViewNameTranslator:当controller处理器方法没有返回一个View对象或逻辑视图名称，
		 * 并且在该方法中没有直接往response的输出流里面写数据的时候，spring将会采用约定好的方式提供一个逻辑视图名称
		 */
		initRequestToViewNameTranslator(context);
		/**
		 * 初始化 ViewResolver: 将ModelAndView选择合适的视图进行渲染的处理器
		 */
		initViewResolvers(context);
		/**
		 * 初始化 FlashMapManager: 提供请求存储属性，可供其他请求使用
		 */
		initFlashMapManager(context);
	}

	/**
	 * Initialize the MultipartResolver used by this class.
	 * 初始化这个类使用的MultipartResolver。
	 *
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * no multipart handling is provided.
	 * 如果在BeanFactory中没有为该名称空间定义给定名称的bean，则不提供多部分处理。
	 */
	private void initMultipartResolver(ApplicationContext context) {
		try {
			/**
			 * 从工厂中获取"multipartResolver"bean
			 *
			 * {@link this#LOCALE_RESOLVER_BEAN_NAME}是在解析 <mvc:annotation-driven></mvc:annotation-driven>标签时，<em>不会</em>通过
			 * {@link org.springframework.web.servlet.config.MvcNamespaceUtils#registerDefaultComponents(ParserContext, Object)}方法注入
			 */
			this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.multipartResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.multipartResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Default is no multipart resolver.
			/**
			 * 默认情况下没有多部分解析器。
			 */
			this.multipartResolver = null;
			if (logger.isTraceEnabled()) {
				logger.trace("No MultipartResolver '" + MULTIPART_RESOLVER_BEAN_NAME + "' declared");
			}
		}
	}

	/**
	 * Initialize the LocaleResolver used by this class.
	 * 初始化这个类使用的LocaleResolver。
	 *
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to AcceptHeaderLocaleResolver.
	 * 如果在BeanFactory中没有为这个名称空间定义给定名称的bean，我们默认使用AcceptHeaderLocaleResolver。
	 */
	private void initLocaleResolver(ApplicationContext context) {
		try {
			/**
			 * {@link this#LOCALE_RESOLVER_BEAN_NAME}实在解析 <mvc:annotation-driven></mvc:annotation-driven>标签时，通过
			 * {@link org.springframework.web.servlet.config.MvcNamespaceUtils#registerDefaultComponents(ParserContext, Object)}方法
			 * 以{@link org.springframework.beans.factory.support.BeanDefinitionRegistry#registerBeanDefinition(String, BeanDefinition)}方式注入的
			 */
			this.localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.localeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.localeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			/**
			 * 如果工厂中没有，则使用默认的{@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver}
			 * 获取单个默认策略
			 */
			this.localeResolver = getDefaultStrategy(context, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No LocaleResolver '" + LOCALE_RESOLVER_BEAN_NAME +
						"': using default [" + this.localeResolver.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the ThemeResolver used by this class.
	 * 初始化这个类使用的ThemeResolver。
	 *
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to a FixedThemeResolver.
	 * 如果在BeanFactory中没有为这个名称空间定义给定名称的bean，我们默认使用FixedThemeResolver。
	 */
	private void initThemeResolver(ApplicationContext context) {
		try {
			/**
			 * {@link this#LOCALE_RESOLVER_BEAN_NAME}实在解析 <mvc:annotation-driven></mvc:annotation-driven>标签时，通过
			 * {@link org.springframework.web.servlet.config.MvcNamespaceUtils#registerDefaultComponents(ParserContext, Object)}方法
			 * 以{@link org.springframework.beans.factory.support.BeanDefinitionRegistry#registerBeanDefinition(String, BeanDefinition)}方式注入的
			 */
			this.themeResolver = context.getBean(THEME_RESOLVER_BEAN_NAME, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.themeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.themeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			/**
			 * 我们需要使用默认值。
			 * 获取"DispatcherServlet.properties"中定义的默认策略
			 */
			this.themeResolver = getDefaultStrategy(context, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ThemeResolver '" + THEME_RESOLVER_BEAN_NAME +
						"': using default [" + this.themeResolver.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * 1.优先使用用户注入的{@link HandlerMapping}策略
	 * 2.如果用户没有注入，则使用"DispatcherServlet.properties"中定义的默认策略
	 *
	 * Initialize the HandlerMappings used by this class.
	 * 初始化这个类使用的HandlerMappings。
	 *
	 * <p>If no HandlerMapping beans are defined in the BeanFactory for this namespace,
	 * we default to BeanNameUrlHandlerMapping.
	 * 如果在BeanFactory中没有为这个名称空间定义HandlerMapping bean，我们默认使用BeanNameUrlHandlerMapping。
	 */
	private void initHandlerMappings(ApplicationContext context) {
		this.handlerMappings = null;

		if (this.detectAllHandlerMappings) {
			// Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
			/**
			 * 找到ApplicationContext中的所有HandlerMappings，包括祖先上下文。
			 *
			 * 在当前工厂及其父工厂中获取{@link HandlerMapping}及其子类的实例，
			 * 如果子工厂和父工厂存在同名的bean，优先取子工厂的bean实例
			 *
			 * 此处在获取时，如果目标bean是通过FactoryBean注入的，则不会引起FactoryBean的实例化
			 */
			Map<String, HandlerMapping> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerMappings = new ArrayList<>(matchingBeans.values());
				// We keep HandlerMappings in sorted order.
				/**
				 * 我们保持HandlerMappings的排序顺序。
				 *
				 * 根据{@link org.springframework.core.Ordered}接口和{@link Order}注解排序
				 */
				AnnotationAwareOrderComparator.sort(this.handlerMappings);
			}
		}
		else {
			try {
				/**
				 * 此处在获取时，如果目标bean是通过FactoryBean注入的，则会引起FactoryBean的实例化
				 */
				HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
				this.handlerMappings = Collections.singletonList(hm);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerMapping later.
				// 忽略，稍后我们将添加一个默认HandlerMapping。
			}
		}

		// Ensure we have at least one HandlerMapping, by registering
		// a default HandlerMapping if no other mappings are found.
		/**
		 * 如果没有找到其他映射，通过注册一个默认的HandlerMapping来确保我们至少有一个HandlerMapping。
		 *
		 * 如果用户没有指定{@link HandlerMapping}类型的bean，则使用"DispatcherServlet.properties"中指定的默认策略
		 */
		if (this.handlerMappings == null) {
			this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerMappings declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}

		/**
		 * 设置解析请求路径
		 */
		for (HandlerMapping mapping : this.handlerMappings) {
			if (mapping.usesPathPatterns()) {
				this.parseRequestPath = true;
				break;
			}
		}
	}

	/**
	 * Initialize the HandlerAdapters used by this class.
	 * 初始化该类使用的HandlerAdapters。
	 *
	 * <p>If no HandlerAdapter beans are defined in the BeanFactory for this namespace,
	 * we default to SimpleControllerHandlerAdapter.
	 * 如果在BeanFactory中没有为这个名称空间定义HandlerAdapter bean，我们默认使用SimpleControllerHandlerAdapter。
	 */
	private void initHandlerAdapters(ApplicationContext context) {
		this.handlerAdapters = null;

		if (this.detectAllHandlerAdapters) {
			// Find all HandlerAdapters in the ApplicationContext, including ancestor contexts.
			/**
			 * 找到ApplicationContext中的所有HandlerAdapters，包括祖先上下文。
			 *
			 * 在当前工厂及其父工厂中获取{@link HandlerAdapter}及其子类的实例，
			 * 如果子工厂和父工厂存在同名的bean，优先取子工厂的bean实例
			 *
			 * 此处在获取时，如果目标bean是通过FactoryBean注入的，则不会引起FactoryBean的实例化
			 */
			Map<String, HandlerAdapter> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerAdapters = new ArrayList<>(matchingBeans.values());
				// We keep HandlerAdapters in sorted order.
				/**
				 * 我们将HandlerAdapters按顺序保存。
				 *
				 * 根据{@link org.springframework.core.Ordered}接口和{@link Order}注解排序
				 */
				AnnotationAwareOrderComparator.sort(this.handlerAdapters);
			}
		}
		else {
			try {
				/**
				 * 此处在获取时，如果目标bean是通过FactoryBean注入的，则会引起FactoryBean的实例化
				 */
				HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
				this.handlerAdapters = Collections.singletonList(ha);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerAdapter later.
				// 忽略，稍后我们将添加一个默认的HandlerAdapter。
			}
		}

		// Ensure we have at least some HandlerAdapters, by registering
		// default HandlerAdapters if no other adapters are found.
		/**
		 * 如果没有找到其他适配器，可以通过注册默认的handleradapter来确保我们至少拥有一些handleradapter。
		 *
		 * 如果用户没有指定{@link HandlerAdapter}类型的bean，则使用"DispatcherServlet.properties"中指定的默认策略
		 */
		if (this.handlerAdapters == null) {
			this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerAdapters declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the HandlerExceptionResolver used by this class.
	 * 初始化该类使用的HandlerExceptionResolver。
	 *
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to no exception resolver.
	 * 如果在BeanFactory中没有为该名称空间定义给定名称的bean，则默认不使用异常解析器。
	 */
	private void initHandlerExceptionResolvers(ApplicationContext context) {
		this.handlerExceptionResolvers = null;

		if (this.detectAllHandlerExceptionResolvers) {
			// Find all HandlerExceptionResolvers in the ApplicationContext, including ancestor contexts.
			/**
			 * 找到ApplicationContext中的所有HandlerExceptionResolvers，包括祖先上下文。
			 *
			 * 在当前工厂及其父工厂中获取{@link HandlerExceptionResolver}及其子类的实例，
			 * 如果子工厂和父工厂存在同名的bean，优先取子工厂的bean实例
			 *
			 * 此处在获取时，如果目标bean是通过FactoryBean注入的，则不会引起FactoryBean的实例化
			 */
			Map<String, HandlerExceptionResolver> matchingBeans = BeanFactoryUtils
					.beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerExceptionResolvers = new ArrayList<>(matchingBeans.values());
				// We keep HandlerExceptionResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerExceptionResolvers);
			}
		}
		else {
			try {
				/**
				 * 此处在获取时，如果目标bean是通过FactoryBean注入的，则会引起FactoryBean的实例化
				 */
				HandlerExceptionResolver her =
						context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME, HandlerExceptionResolver.class);
				this.handlerExceptionResolvers = Collections.singletonList(her);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, no HandlerExceptionResolver is fine too.
				// 忽略，没有HandlerExceptionResolver也可以。
			}
		}

		// Ensure we have at least some HandlerExceptionResolvers, by registering
		// default HandlerExceptionResolvers if no other resolvers are found.
		/**
		 * 如果没有找到其他的解析器，通过注册默认的HandlerExceptionResolvers，确保我们至少有一些HandlerExceptionResolvers。
		 *
		 * 如果用户没有指定{@link HandlerExceptionResolver}类型的bean，则使用"DispatcherServlet.properties"中指定的默认策略
		 */
		if (this.handlerExceptionResolvers == null) {
			this.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerExceptionResolvers declared in servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the RequestToViewNameTranslator used by this servlet instance.
	 * 初始化这个servlet实例使用的RequestToViewNameTranslator。
	 *
	 * <p>If no implementation is configured then we default to DefaultRequestToViewNameTranslator.
	 * 如果没有配置实现，则默认为DefaultRequestToViewNameTranslator。
	 */
	private void initRequestToViewNameTranslator(ApplicationContext context) {
		try {
			/**
			 * {@link this#LOCALE_RESOLVER_BEAN_NAME}实在解析 <mvc:annotation-driven></mvc:annotation-driven>标签时，通过
			 * {@link org.springframework.web.servlet.config.MvcNamespaceUtils#registerDefaultComponents(ParserContext, Object)}方法
			 * 以{@link org.springframework.beans.factory.support.BeanDefinitionRegistry#registerBeanDefinition(String, BeanDefinition)}方式注入的
			 */
			this.viewNameTranslator =
					context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME, RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.viewNameTranslator.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.viewNameTranslator);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			/**
			 * 如果工厂中没有，则使用默认的{@link org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator}
			 * 获取单个默认策略
			 */
			this.viewNameTranslator = getDefaultStrategy(context, RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No RequestToViewNameTranslator '" + REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME +
						"': using default [" + this.viewNameTranslator.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the ViewResolvers used by this class.
	 * 初始化这个类使用的ViewResolvers。
	 *
	 * <p>If no ViewResolver beans are defined in the BeanFactory for this
	 * namespace, we default to InternalResourceViewResolver.
	 * 如果在BeanFactory中没有为这个名称空间定义ViewResolver bean，我们默认使用InternalResourceViewResolver。
	 */
	private void initViewResolvers(ApplicationContext context) {
		this.viewResolvers = null;

		if (this.detectAllViewResolvers) {
			// Find all ViewResolvers in the ApplicationContext, including ancestor contexts.
			/**
			 * 在ApplicationContext中找到所有的ViewResolvers，包括祖先上下文。
			 *
			 * 在当前工厂及其父工厂中获取{@link ViewResolver}及其子类的实例，
			 * 如果子工厂和父工厂存在同名的bean，优先取子工厂的bean实例
			 *
			 * 此处在获取时，如果目标bean是通过FactoryBean注入的，则不会引起FactoryBean的实例化
			 */
			Map<String, ViewResolver> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, ViewResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.viewResolvers = new ArrayList<>(matchingBeans.values());
				// We keep ViewResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.viewResolvers);
			}
		}
		else {
			try {
				/**
				 * 此处在获取时，如果目标bean是通过FactoryBean注入的，则不会引起FactoryBean的实例化
				 */
				ViewResolver vr = context.getBean(VIEW_RESOLVER_BEAN_NAME, ViewResolver.class);
				this.viewResolvers = Collections.singletonList(vr);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default ViewResolver later.
				// 忽略，我们稍后会添加一个默认的ViewResolver。
			}
		}

		// Ensure we have at least one ViewResolver, by registering
		// a default ViewResolver if no other resolvers are found.
		/**
		 * 确保我们至少有一个ViewResolver，如果找不到其他的ViewResolver，就注册一个默认的ViewResolver。
		 *
		 * 如果用户没有指定{@link ViewResolver}类型的bean，则使用"DispatcherServlet.properties"中指定的默认策略
		 */
		if (this.viewResolvers == null) {
			this.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ViewResolvers declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the {@link FlashMapManager} used by this servlet instance.
	 * 初始化这个servlet实例使用的{@link FlashMapManager}。
	 *
	 * <p>If no implementation is configured then we default to
	 * {@code org.springframework.web.servlet.support.DefaultFlashMapManager}.
	 * 如果没有配置实现，则默认为{@code org.springframework.web.servlet.support.DefaultFlashMapManager}。
	 */
	private void initFlashMapManager(ApplicationContext context) {
		try {
			/**
			 * {@link this#LOCALE_RESOLVER_BEAN_NAME}实在解析 <mvc:annotation-driven></mvc:annotation-driven>标签时，通过
			 * {@link org.springframework.web.servlet.config.MvcNamespaceUtils#registerDefaultComponents(ParserContext, Object)}方法
			 * 以{@link org.springframework.beans.factory.support.BeanDefinitionRegistry#registerBeanDefinition(String, BeanDefinition)}方式注入的
			 */
			this.flashMapManager = context.getBean(FLASH_MAP_MANAGER_BEAN_NAME, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.flashMapManager.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.flashMapManager);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			/**
			 * 如果工厂中没有，则使用默认的{@link org.springframework.web.servlet.support.SessionFlashMapManager}
			 * 获取单个默认策略
			 */
			this.flashMapManager = getDefaultStrategy(context, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No FlashMapManager '" + FLASH_MAP_MANAGER_BEAN_NAME +
						"': using default [" + this.flashMapManager.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Return this servlet's ThemeSource, if any; else return {@code null}.
	 * <p>Default is to return the WebApplicationContext as ThemeSource,
	 * provided that it implements the ThemeSource interface.
	 * @return the ThemeSource, if any
	 * @see #getWebApplicationContext()
	 */
	@Nullable
	public final ThemeSource getThemeSource() {
		return (getWebApplicationContext() instanceof ThemeSource ? (ThemeSource) getWebApplicationContext() : null);
	}

	/**
	 * Obtain this servlet's MultipartResolver, if any.
	 * @return the MultipartResolver used by this servlet, or {@code null} if none
	 * (indicating that no multipart support is available)
	 */
	@Nullable
	public final MultipartResolver getMultipartResolver() {
		return this.multipartResolver;
	}

	/**
	 * Return the configured {@link HandlerMapping} beans that were detected by
	 * type in the {@link WebApplicationContext} or initialized based on the
	 * default set of strategies from {@literal DispatcherServlet.properties}.
	 * <p><strong>Note:</strong> This method may return {@code null} if invoked
	 * prior to {@link #onRefresh(ApplicationContext)}.
	 * @return an immutable list with the configured mappings, or {@code null}
	 * if not initialized yet
	 * @since 5.0
	 */
	@Nullable
	public final List<HandlerMapping> getHandlerMappings() {
		return (this.handlerMappings != null ? Collections.unmodifiableList(this.handlerMappings) : null);
	}

	/**
	 * 获取单个默认策略，如果有多个则抛出异常
	 *
	 * Return the default strategy object for the given strategy interface.
	 * 返回给定策略接口的默认策略对象。
	 *
	 * <p>The default implementation delegates to {@link #getDefaultStrategies},
	 * expecting a single object in the list.
	 * 默认实现委托给{@link #getDefaultStrategies}，期望列表中只有一个对象。
	 *
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the corresponding strategy object
	 * @see #getDefaultStrategies
	 */
	protected <T> T getDefaultStrategy(ApplicationContext context, Class<T> strategyInterface) {
		/**
		 * 获取默认策略列表
		 */
		List<T> strategies = getDefaultStrategies(context, strategyInterface);
		if (strategies.size() != 1) {
			throw new BeanInitializationException(
					"DispatcherServlet needs exactly 1 strategy for interface [" + strategyInterface.getName() + "]");
		}
		return strategies.get(0);
	}

	/**
	 * Create a List of default strategy objects for the given strategy interface.
	 * 为给定的策略接口创建一个默认策略对象的列表。
	 *
	 * <p>The default implementation uses the "DispatcherServlet.properties" file (in the same
	 * package as the DispatcherServlet class) to determine the class names. It instantiates
	 * the strategy objects through the context's BeanFactory.
	 * 默认实现使用“DispatcherServlet.properties”文件(与DispatcherServlet类在同一个包中)来确定类名。
	 * 它通过上下文的BeanFactory实例化策略对象。
	 *
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the List of corresponding strategy objects
	 */
	@SuppressWarnings("unchecked")
	protected <T> List<T> getDefaultStrategies(ApplicationContext context, Class<T> strategyInterface) {
		/**
		 * 初始化加载默认策略
		 */
		if (defaultStrategies == null) {
			try {
				// Load default strategy implementations from properties file.
				// This is currently strictly internal and not meant to be customized
				// by application developers.
				/**
				 * 从属性文件加载默认策略实现。目前这是严格的内部设置，应用程序开发人员不需要定制。
				 */
				ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, DispatcherServlet.class);
				defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Could not load '" + DEFAULT_STRATEGIES_PATH + "': " + ex.getMessage());
			}
		}

		String key = strategyInterface.getName();
		String value = defaultStrategies.getProperty(key);
		if (value != null) {
			/**
			 * 使用逗号分割默认策略
			 */
			String[] classNames = StringUtils.commaDelimitedListToStringArray(value);
			List<T> strategies = new ArrayList<>(classNames.length);
			for (String className : classNames) {
				try {
					/**
					 * 加载类，并实例化
					 */
					Class<?> clazz = ClassUtils.forName(className, DispatcherServlet.class.getClassLoader());
					Object strategy = createDefaultStrategy(context, clazz);
					strategies.add((T) strategy);
				}
				catch (ClassNotFoundException ex) {
					throw new BeanInitializationException(
							"Could not find DispatcherServlet's default strategy class [" + className +
							"] for interface [" + key + "]", ex);
				}
				catch (LinkageError err) {
					throw new BeanInitializationException(
							"Unresolvable class definition for DispatcherServlet's default strategy class [" +
							className + "] for interface [" + key + "]", err);
				}
			}
			return strategies;
		}
		else {
			return Collections.emptyList();
		}
	}

	/**
	 * Create a default strategy.
	 * 创建默认策略。
	 *
	 * <p>The default implementation uses
	 * {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean}.
	 * 默认实现使用{@link org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean}。
	 *
	 * @param context the current WebApplicationContext
	 * @param clazz the strategy implementation class to instantiate
	 * @return the fully configured strategy instance
	 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean
	 */
	protected Object createDefaultStrategy(ApplicationContext context, Class<?> clazz) {
		return context.getAutowireCapableBeanFactory().createBean(clazz);
	}


	/**
	 * Exposes the DispatcherServlet-specific request attributes and delegates to {@link #doDispatch}
	 * for the actual dispatching.
	 *
	 * 将特定于DispatcherServlet的请求属性和委托公开给{@link #doDispatch}以进行实际调度。
	 */
	@Override
	protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {
		logRequest(request);

		// Keep a snapshot of the request attributes in case of an include,
		// to be able to restore the original attributes after the include.
		/**
		 * 在包含的情况下保留请求属性的快照，以便能够在包含之后恢复原始属性。
		 */
		Map<String, Object> attributesSnapshot = null;
		/**
		 * 检查是否包含"jakarta.servlet.include.request_uri"属性，如果包含，为当前请求属性创建快照
		 */
		if (WebUtils.isIncludeRequest(request)) {
			attributesSnapshot = new HashMap<>();
			Enumeration<?> attrNames = request.getAttributeNames();
			while (attrNames.hasMoreElements()) {
				String attrName = (String) attrNames.nextElement();
				/**
				 * 保存所有以"org.springframework.web.servlet"开头的DispatcherServlet默认策略属性。
				 */
				if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
					attributesSnapshot.put(attrName, request.getAttribute(attrName));
				}
			}
		}

		// Make framework objects available to handlers and view objects.
		/**
		 * 使框架对象对处理程序和视图对象可用。
		 */
		/**
		 * 保存当前servlet的上下文对象，属性名称为"org.springframework.web.servlet.DispatcherServlet.CONTEXT"
		 */
		request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
		/**
		 * 保存当前servlet的本地资源处理器，"org.springframework.web.servlet.DispatcherServlet.THEME_RESOLVER"
		 */
		request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, this.localeResolver);
		/**
		 * 保存当前servlet的主题处理器，"org.springframework.web.servlet.DispatcherServlet.THEME_RESOLVER"
		 */
		request.setAttribute(THEME_RESOLVER_ATTRIBUTE, this.themeResolver);
		/**
		 * 保存当前servlet的主题资源，"org.springframework.web.servlet.DispatcherServlet.THEME_SOURCE"
		 */
		request.setAttribute(THEME_SOURCE_ATTRIBUTE, getThemeSource());

		if (this.flashMapManager != null) {
			/**
			 * 获取当前请求已经存在的{@link FlashMap}，如果由多个，则通过{@link FlashMap#compareTo(FlashMap)}排序，取第一个
			 */
			FlashMap inputFlashMap = this.flashMapManager.retrieveAndUpdate(request, response);
			/**
			 * 如果存在inputFlashMap，则保存到当前请求属性中，属性名称："org.springframework.web.servlet.DispatcherServlet.INPUT_FLASH_MAP'
			 */
			if (inputFlashMap != null) {
				request.setAttribute(INPUT_FLASH_MAP_ATTRIBUTE, Collections.unmodifiableMap(inputFlashMap));
			}
			/**
			 * org.springframework.web.servlet.DispatcherServlet.OUTPUT_FLASH_MAP
			 */
			request.setAttribute(OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
			/**
			 * org.springframework.web.servlet.DispatcherServlet.FLASH_MAP_MANAGER
			 */
			request.setAttribute(FLASH_MAP_MANAGER_ATTRIBUTE, this.flashMapManager);
		}

		RequestPath previousRequestPath = null;
		if (this.parseRequestPath) {
			/**
			 * 获取已经解析的请求路径，属性名称:"org.springframework.web.util.ServletRequestPathUtils.PATH"
			 */
			previousRequestPath = (RequestPath) request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
			/**
			 * 重新解析并缓存请求路径
			 */
			ServletRequestPathUtils.parseAndCache(request);
		}

		try {
			doDispatch(request, response);
		}
		finally {
			if (!WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
				// Restore the original attribute snapshot, in case of an include.
				/**
				 * 在包含的情况下，恢复原始属性快照。
				 */
				if (attributesSnapshot != null) {
					restoreAttributesAfterInclude(request, attributesSnapshot);
				}
			}
			if (this.parseRequestPath) {
				/**
				 * 重置原有的请求路径
				 */
				ServletRequestPathUtils.setParsedRequestPath(previousRequestPath, request);
			}
		}
	}

	private void logRequest(HttpServletRequest request) {
		LogFormatUtils.traceDebug(logger, traceOn -> {
			String params;
			if (StringUtils.startsWithIgnoreCase(request.getContentType(), "multipart/")) {
				params = "multipart";
			}
			else if (isEnableLoggingRequestDetails()) {
				params = request.getParameterMap().entrySet().stream()
						.map(entry -> entry.getKey() + ":" + Arrays.toString(entry.getValue()))
						.collect(Collectors.joining(", "));
			}
			else {
				params = (request.getParameterMap().isEmpty() ? "" : "masked");
			}

			String queryString = request.getQueryString();
			String queryClause = (StringUtils.hasLength(queryString) ? "?" + queryString : "");
			String dispatchType = (!DispatcherType.REQUEST.equals(request.getDispatcherType()) ?
					"\"" + request.getDispatcherType() + "\" dispatch for " : "");
			String message = (dispatchType + request.getMethod() + " \"" + getRequestUri(request) +
					queryClause + "\", parameters={" + params + "}");

			if (traceOn) {
				List<String> values = Collections.list(request.getHeaderNames());
				String headers = values.size() > 0 ? "masked" : "";
				if (isEnableLoggingRequestDetails()) {
					headers = values.stream().map(name -> name + ":" + Collections.list(request.getHeaders(name)))
							.collect(Collectors.joining(", "));
				}
				return message + ", headers={" + headers + "} in DispatcherServlet '" + getServletName() + "'";
			}
			else {
				return message;
			}
		});
	}

	/**
	 * Process the actual dispatching to the handler.
	 * 处理到处理程序的实际调度。
	 *
	 * <p>The handler will be obtained by applying the servlet's HandlerMappings in order.
	 * 处理程序将通过按顺序应用servlet的HandlerMappings来获得。
	 *
	 * The HandlerAdapter will be obtained by querying the servlet's installed HandlerAdapters
	 * to find the first that supports the handler class.
	 * HandlerAdapter将通过查询servlet已安装的HandlerAdapter来获得，以找到第一个支持处理程序类的HandlerAdapter。
	 *
	 * <p>All HTTP methods are handled by this method. It's up to HandlerAdapters or handlers
	 * themselves to decide which methods are acceptable.
	 * 所有HTTP方法都由该方法处理。由HandlerAdapters或处理程序本身来决定哪些方法是可接受的。
	 *
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception in case of any kind of processing failure
	 */
	@SuppressWarnings("deprecation")
	protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
		HttpServletRequest processedRequest = request;
		HandlerExecutionChain mappedHandler = null;
		boolean multipartRequestParsed = false;

		/**
		 * 获取当前请求的同步管理器，在请求第一次到达{@link #processRequest(HttpServletRequest, HttpServletResponse)}时通过
		 * {@link WebAsyncUtils#getAsyncManager(ServletRequest)}创建
		 */
		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

		try {
			ModelAndView mv = null;
			Exception dispatchException = null;

			try {
				/**
				 * 检查当前请求是否一个上传请求，如果是则创建一个{@link org.springframework.web.multipart.support.StandardMultipartHttpServletRequest}对象，
				 * 表示上传请求，并解析表单项
				 * @see org.springframework.web.multipart.support.StandardMultipartHttpServletRequest#multipartParameterNames
				 * @see org.springframework.web.multipart.support.StandardMultipartHttpServletRequest#multipartFiles
				 *
				 * 如果当前请求不是上传请求，则返回当前请求
				 */
				processedRequest = checkMultipart(request);
				/**
				 * 标识当前请求是否上传请求
				 */
				multipartRequestParsed = (processedRequest != request);

				// Determine handler for the current request.
				/**
				 * 确定当前请求的处理程序。
				 * 获取当前请求的执行链，包含拦截器
				 */
				mappedHandler = getHandler(processedRequest);
				/**
				 * 如果没有对应的处理程序，设置合适的相应结果，通常是"404"
				 */
				if (mappedHandler == null) {
					noHandlerFound(processedRequest, response);
					return;
				}

				// Determine handler adapter for the current request.
				/**
				 * 确定当前请求的处理程序适配器，默认使用以下四个之一
				 * {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter}
				 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter}
				 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
				 * {@link org.springframework.web.servlet.function.support.HandlerFunctionAdapter}
				 *
				 */
				HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

				// Process last-modified header, if supported by the handler.
				/**
				 * 如果处理程序支持，则处理上次修改的报头。
				 *
				 * 处理GET、HEAD请求的Last-Modified,当浏览器第一次跟服务器请求资源时，服务器会在返回的请求头里包含一个last_modified的属性，
				 * 代表资源最后时什么时候修改的，在浏览器以后发送请求的时候，会同时发送之前接收到的Last_modified.服务器接收到带last_modified的请求后，
				 * 会跟实际资源的最后修改时间做对比，如果过期了返回新的资源，否则直接返回304表示未过期，直接使用之前缓存的结果即可
				 */
				String method = request.getMethod();
				boolean isGet = HttpMethod.GET.matches(method);
				/**
				 * 获取上次请求时间，如果上次请求后资源一直没有变，则通知前台资源可以继续使用，不重复相应资源
				 */
				if (isGet || HttpMethod.HEAD.matches(method)) {
					/**
					 *  获取请求中服务器端最后被修改时间
					 */
					long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
					if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
						return;
					}
				}

				/**
				 * 执行拦截器的{@link HandlerInterceptor#preHandle(HttpServletRequest, HttpServletResponse, Object)}方法
				 * 如果拦截器返回false，不在继续处理请求
				 */
				if (!mappedHandler.applyPreHandle(processedRequest, response)) {
					return;
				}

				// Actually invoke the handler.
				/**
				 * 实际上调用处理程序。
				 */
				mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

				/**
				 * 判断当前请求是否需要异步处理或者正在异步处理
				 */
				if (asyncManager.isConcurrentHandlingStarted()) {
					return;
				}

				/**
				 * 为视图设置默认名称
				 */
				applyDefaultViewName(processedRequest, mv);
				/**
				 * 执行拦截器的{@link HandlerInterceptor#postHandle(HttpServletRequest, HttpServletResponse, Object, ModelAndView)}回调
				 */
				mappedHandler.applyPostHandle(processedRequest, response, mv);
			}
			catch (Exception ex) {
				dispatchException = ex;
			}
			catch (Throwable err) {
				// As of 4.3, we're processing Errors thrown from handler methods as well,
				// making them available for @ExceptionHandler methods and other scenarios.
				/**
				 * 从4.3开始，我们也在处理从处理程序方法抛出的错误，使它们可用于@ExceptionHandler方法和其他场景。
				 */
				dispatchException = new NestedServletException("Handler dispatch failed", err);
			}
			processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
		}
		catch (Exception ex) {
			triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
		}
		catch (Throwable err) {
			triggerAfterCompletion(processedRequest, response, mappedHandler,
					new NestedServletException("Handler processing failed", err));
		}
		finally {
			if (asyncManager.isConcurrentHandlingStarted()) {
				// Instead of postHandle and afterCompletion
				/**
				 * 而不是postHandle和afterCompletion
				 *
				 * 异步请求开始处理后调用{@link AsyncHandlerInterceptor#afterConcurrentHandlingStarted(HttpServletRequest, HttpServletResponse, Object)}回调
				 */
				if (mappedHandler != null) {
					mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
				}
			}
			else {
				// Clean up any resources used by a multipart request.
				/**
				 * 清理由多部分请求使用的任何资源。
				 */
				if (multipartRequestParsed) {
					/**
					 * 清理上传请求的文件缓存
					 */
					cleanupMultipart(processedRequest);
				}
			}
		}
	}

	/**
	 * Do we need view name translation? 我们需要视图名转换吗?
	 */
	private void applyDefaultViewName(HttpServletRequest request, @Nullable ModelAndView mv) throws Exception {
		if (mv != null && !mv.hasView()) {
			String defaultViewName = getDefaultViewName(request);
			if (defaultViewName != null) {
				mv.setViewName(defaultViewName);
			}
		}
	}

	/**
	 * Handle the result of handler selection and handler invocation, which is
	 * either a ModelAndView or an Exception to be resolved to a ModelAndView.
	 *
	 * 处理处理程序选择和处理程序调用的结果，该结果要么是ModelAndView，要么是要解析为ModelAndView的异常。
	 */
	private void processDispatchResult(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, @Nullable ModelAndView mv,
			@Nullable Exception exception) throws Exception {

		boolean errorView = false;

		if (exception != null) {
			if (exception instanceof ModelAndViewDefiningException) {
				logger.debug("ModelAndViewDefiningException encountered", exception);
				mv = ((ModelAndViewDefiningException) exception).getModelAndView();
			}
			else {
				Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
				mv = processHandlerException(request, response, handler, exception);
				errorView = (mv != null);
			}
		}

		// Did the handler return a view to render?
		if (mv != null && !mv.wasCleared()) {
			render(mv, request, response);
			if (errorView) {
				WebUtils.clearErrorRequestAttributes(request);
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("No view rendering, null ModelAndView returned.");
			}
		}

		if (WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
			// Concurrent handling started during a forward
			return;
		}

		if (mappedHandler != null) {
			// Exception (if any) is already handled..
			mappedHandler.triggerAfterCompletion(request, response, null);
		}
	}

	/**
	 * Build a LocaleContext for the given request, exposing the request's primary locale as current locale.
	 * <p>The default implementation uses the dispatcher's LocaleResolver to obtain the current locale,
	 * which might change during a request.
	 * @param request current HTTP request
	 * @return the corresponding LocaleContext
	 */
	@Override
	protected LocaleContext buildLocaleContext(final HttpServletRequest request) {
		LocaleResolver lr = this.localeResolver;
		if (lr instanceof LocaleContextResolver) {
			return ((LocaleContextResolver) lr).resolveLocaleContext(request);
		}
		else {
			return () -> (lr != null ? lr.resolveLocale(request) : request.getLocale());
		}
	}

	/**
	 * Convert the request into a multipart request, and make multipart resolver available.
	 * 将请求转换为多部分请求，并使多部分解析器可用。
	 *
	 * <p>If no multipart resolver is set, simply use the existing request.
	 * 如果没有设置多部分解析器，只需使用现有的请求。
	 *
	 * @param request current HTTP request
	 * @return the processed request (multipart wrapper if necessary)
	 * @see MultipartResolver#resolveMultipart
	 */
	protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
		/**
		 * 检查当前请求是否上传请求，通过请求类型判断
		 */
		if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
			if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
				if (DispatcherType.REQUEST.equals(request.getDispatcherType())) {
					logger.trace("Request already resolved to MultipartHttpServletRequest, e.g. by MultipartFilter");
				}
			}
			else if (hasMultipartException(request)) {
				logger.debug("Multipart resolution previously failed for current request - " +
						"skipping re-resolution for undisturbed error rendering");
			}
			else {
				try {
					/**
					 * 创建{@link org.springframework.web.multipart.support.StandardMultipartHttpServletRequest}对象，
					 * 表示multipart/form-data请求，并解析表单项
					 */
					return this.multipartResolver.resolveMultipart(request);
				}
				catch (MultipartException ex) {
					if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
						logger.debug("Multipart resolution failed for error dispatch", ex);
						// Keep processing error dispatch with regular request handle below
						// 保持处理错误调度与常规请求处理如下
					}
					else {
						throw ex;
					}
				}
			}
		}
		// If not returned before: return original request.
		/**
		 * 如果之前没有返回:返回原始请求。
		 */
		return request;
	}

	/**
	 * Check "jakarta.servlet.error.exception" attribute for a multipart exception.
	 * 检查“jakarta.servlet.error。属性用于多部分异常。
	 */
	private boolean hasMultipartException(HttpServletRequest request) {
		Throwable error = (Throwable) request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE);
		while (error != null) {
			if (error instanceof MultipartException) {
				return true;
			}
			error = error.getCause();
		}
		return false;
	}

	/**
	 * Clean up any resources used by the given multipart request (if any).
	 * 清除给定多部分请求使用的任何资源(如果有的话)。
	 *
	 * @param request current HTTP request
	 * @see MultipartResolver#cleanupMultipart
	 */
	protected void cleanupMultipart(HttpServletRequest request) {
		if (this.multipartResolver != null) {
			MultipartHttpServletRequest multipartRequest =
					WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
			if (multipartRequest != null) {
				/**
				 * 清理上传请求的文件缓存
				 */
				this.multipartResolver.cleanupMultipart(multipartRequest);
			}
		}
	}

	/**
	 * 获取当前请求的执行链，包含拦截器
	 * 通过{@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping}获取到的handler是bean实例
	 * 通过{@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}获取到的handler是
	 * {@link org.springframework.web.method.HandlerMethod}类型
	 *
	 * 不管是handler是那种类型，如果是跨域预检请求，handler都会被替换成{@link org.springframework.web.servlet.handler.AbstractHandlerMapping.PreFlightHandler}类型
	 *
	 *
	 * 如果是通过{@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping}对象获取的执行链，
	 * 则会在{@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping#buildPathExposingHandler(Object, String, String, Map)}
	 * 方法中为执行链添加{@link org.springframework.web.servlet.handler.AbstractUrlHandlerMapping.PathExposingHandlerInterceptor}拦截器
	 * 和{@link org.springframework.web.servlet.handler.AbstractUrlHandlerMapping.UriTemplateVariablesHandlerInterceptor}拦截器
	 *
	 * 如果当前请求是跨域请求，会在{@link org.springframework.web.servlet.handler.AbstractHandlerMapping#getHandler(HttpServletRequest)}
	 * 方法中为执行链添加{@link org.springframework.web.servlet.handler.AbstractHandlerMapping.PreFlightHandler (跨域预检请求)}处理程序或
	 * {@link org.springframework.web.servlet.handler.AbstractHandlerMapping.CorsInterceptor (跨域请求)}拦截器
	 * 
	 * 跨域请求最终都会调用{@link org.springframework.web.cors.DefaultCorsProcessor#processRequest(CorsConfiguration, HttpServletRequest, HttpServletResponse)}
	 *
	 * Return the HandlerExecutionChain for this request.
	 * 为这个请求返回HandlerExecutionChain。
	 *
	 * <p>Tries all handler mappings in order.
	 * 按顺序尝试所有处理程序映射。
	 *
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain, or {@code null} if no handler could be found
	 */
	@Nullable
	protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		if (this.handlerMappings != null) {
			for (HandlerMapping mapping : this.handlerMappings) {
				HandlerExecutionChain handler = mapping.getHandler(request);
				if (handler != null) {
					return handler;
				}
			}
		}
		return null;
	}

	/**
	 * No handler found -> set appropriate HTTP response status.
	 * 没有找到处理程序->设置适当的HTTP响应状态。
	 *
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception if preparing the response failed
	 */
	protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (pageNotFoundLogger.isWarnEnabled()) {
			pageNotFoundLogger.warn("No mapping for " + request.getMethod() + " " + getRequestUri(request));
		}
		if (this.throwExceptionIfNoHandlerFound) {
			throw new NoHandlerFoundException(request.getMethod(), getRequestUri(request),
					new ServletServerHttpRequest(request).getHeaders());
		}
		else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * Return the HandlerAdapter for this handler object.
	 * 返回此处理程序对象的HandlerAdapter。
	 *
	 * {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter}处理{@link org.springframework.web.HttpRequestHandler}类型的处理程序
	 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter}处理{@link org.springframework.web.servlet.mvc.Controller}类型的处理程序
	 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}处理{@link org.springframework.web.method.HandlerMethod}类型的处理程序
	 * {@link org.springframework.web.servlet.function.support.HandlerFunctionAdapter}处理{@link org.springframework.web.servlet.function.HandlerFunction}类型的处理程序
	 *
	 * @param handler the handler object to find an adapter for
	 * @throws ServletException if no HandlerAdapter can be found for the handler. This is a fatal error.
	 */
	protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
		if (this.handlerAdapters != null) {
			for (HandlerAdapter adapter : this.handlerAdapters) {
				if (adapter.supports(handler)) {
					return adapter;
				}
			}
		}
		throw new ServletException("No adapter for handler [" + handler +
				"]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
	}

	/**
	 * Determine an error ModelAndView via the registered HandlerExceptionResolvers.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen at the time of the exception
	 * (for example, if multipart resolution failed)
	 * @param ex the exception that got thrown during handler execution
	 * @return a corresponding ModelAndView to forward to
	 * @throws Exception if no error ModelAndView found
	 */
	@Nullable
	protected ModelAndView processHandlerException(HttpServletRequest request, HttpServletResponse response,
			@Nullable Object handler, Exception ex) throws Exception {

		// Success and error responses may use different content types
		request.removeAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);

		// Check registered HandlerExceptionResolvers...
		ModelAndView exMv = null;
		if (this.handlerExceptionResolvers != null) {
			for (HandlerExceptionResolver resolver : this.handlerExceptionResolvers) {
				exMv = resolver.resolveException(request, response, handler, ex);
				if (exMv != null) {
					break;
				}
			}
		}
		if (exMv != null) {
			if (exMv.isEmpty()) {
				request.setAttribute(EXCEPTION_ATTRIBUTE, ex);
				return null;
			}
			// We might still need view name translation for a plain error model...
			if (!exMv.hasView()) {
				String defaultViewName = getDefaultViewName(request);
				if (defaultViewName != null) {
					exMv.setViewName(defaultViewName);
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Using resolved error view: " + exMv, ex);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Using resolved error view: " + exMv);
			}
			WebUtils.exposeErrorRequestAttributes(request, ex, getServletName());
			return exMv;
		}

		throw ex;
	}

	/**
	 * Render the given ModelAndView.
	 * <p>This is the last stage in handling a request. It may involve resolving the view by name.
	 * @param mv the ModelAndView to render
	 * @param request current HTTP servlet request
	 * @param response current HTTP servlet response
	 * @throws ServletException if view is missing or cannot be resolved
	 * @throws Exception if there's a problem rendering the view
	 */
	protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Determine locale for request and apply it to the response.
		Locale locale =
				(this.localeResolver != null ? this.localeResolver.resolveLocale(request) : request.getLocale());
		response.setLocale(locale);

		View view;
		String viewName = mv.getViewName();
		if (viewName != null) {
			// We need to resolve the view name.
			view = resolveViewName(viewName, mv.getModelInternal(), locale, request);
			if (view == null) {
				throw new ServletException("Could not resolve view with name '" + mv.getViewName() +
						"' in servlet with name '" + getServletName() + "'");
			}
		}
		else {
			// No need to lookup: the ModelAndView object contains the actual View object.
			view = mv.getView();
			if (view == null) {
				throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a " +
						"View object in servlet with name '" + getServletName() + "'");
			}
		}

		// Delegate to the View object for rendering.
		if (logger.isTraceEnabled()) {
			logger.trace("Rendering view [" + view + "] ");
		}
		try {
			if (mv.getStatus() != null) {
				response.setStatus(mv.getStatus().value());
			}
			view.render(mv.getModelInternal(), request, response);
		}
		catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Error rendering view [" + view + "]", ex);
			}
			throw ex;
		}
	}

	/**
	 * Translate the supplied request into a default view name.
	 * @param request current HTTP servlet request
	 * @return the view name (or {@code null} if no default found)
	 * @throws Exception if view name translation failed
	 */
	@Nullable
	protected String getDefaultViewName(HttpServletRequest request) throws Exception {
		return (this.viewNameTranslator != null ? this.viewNameTranslator.getViewName(request) : null);
	}

	/**
	 * Resolve the given view name into a View object (to be rendered).
	 * <p>The default implementations asks all ViewResolvers of this dispatcher.
	 * Can be overridden for custom resolution strategies, potentially based on
	 * specific model attributes or request parameters.
	 * @param viewName the name of the view to resolve
	 * @param model the model to be passed to the view
	 * @param locale the current locale
	 * @param request current HTTP servlet request
	 * @return the View object, or {@code null} if none found
	 * @throws Exception if the view cannot be resolved
	 * (typically in case of problems creating an actual View object)
	 * @see ViewResolver#resolveViewName
	 */
	@Nullable
	protected View resolveViewName(String viewName, @Nullable Map<String, Object> model,
			Locale locale, HttpServletRequest request) throws Exception {

		if (this.viewResolvers != null) {
			for (ViewResolver viewResolver : this.viewResolvers) {
				View view = viewResolver.resolveViewName(viewName, locale);
				if (view != null) {
					return view;
				}
			}
		}
		return null;
	}

	private void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, Exception ex) throws Exception {

		if (mappedHandler != null) {
			mappedHandler.triggerAfterCompletion(request, response, ex);
		}
		throw ex;
	}

	/**
	 * Restore the request attributes after an include.
	 * 在include之后恢复请求属性。
	 *
	 * @param request current HTTP request
	 * @param attributesSnapshot the snapshot of the request attributes before the include
	 */
	@SuppressWarnings("unchecked")
	private void restoreAttributesAfterInclude(HttpServletRequest request, Map<?, ?> attributesSnapshot) {
		// Need to copy into separate Collection here, to avoid side effects
		// on the Enumeration when removing attributes.
		/**
		 * 此处需要复制到单独的集合中，以避免在删除属性时对枚举产生副作用。
		 * 在生成请求属性快照时只保存了{@link DEFAULT_STRATEGIES_PREFIX}开头的属性，因此此处只回复{@link DEFAULT_STRATEGIES_PREFIX}开头的属性
		 */
		Set<String> attrsToCheck = new HashSet<>();
		Enumeration<?> attrNames = request.getAttributeNames();
		while (attrNames.hasMoreElements()) {
			String attrName = (String) attrNames.nextElement();
			/**
			 * 保存需要从快照恢复的属性名称
			 */
			if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
				attrsToCheck.add(attrName);
			}
		}

		// Add attributes that may have been removed
		/**
		 * 添加可能已删除的属性
		 *
		 * 快照中的属性可能在后续的操作中删除，此处需要添加上已经删除的属性
		 */
		attrsToCheck.addAll((Set<String>) attributesSnapshot.keySet());

		// Iterate over the attributes to check, restoring the original value
		// or removing the attribute, respectively, if appropriate.
		/**
		 * 遍历属性进行检查，如果合适，分别恢复原始值或删除属性。
		 *
		 * 恢复快照，以快照为准，如果请求中存在但是快照中不存在的，删除请求属性
		 */
		for (String attrName : attrsToCheck) {
			Object attrValue = attributesSnapshot.get(attrName);
			if (attrValue == null) {
				request.removeAttribute(attrName);
			}
			else if (attrValue != request.getAttribute(attrName)) {
				request.setAttribute(attrName, attrValue);
			}
		}
	}

	private static String getRequestUri(HttpServletRequest request) {
		String uri = (String) request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE);
		if (uri == null) {
			uri = request.getRequestURI();
		}
		return uri;
	}

}
