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

package org.springframework.web.context;

import jakarta.servlet.ServletContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performs the actual initialization work for the root application context.
 * Called by {@link ContextLoaderListener}.
 * 为根应用程序上下文执行实际的初始化工作。由{@link ContextLoaderListener}调用。
 *
 * <p>Looks for a {@link #CONTEXT_CLASS_PARAM "contextClass"} parameter at the
 * {@code web.xml} context-param level to specify the context class type, falling
 * back to {@link org.springframework.web.context.support.XmlWebApplicationContext}
 * if not found. With the default ContextLoader implementation, any context class
 * specified needs to implement the {@link ConfigurableWebApplicationContext} interface.
 * 在{@code web.xml} context-param级别查找{@link CONTEXT_CLASS_PARAM "contextClass"}参数，以指定上下文类类型，
 * 回落到{@link org.springframework.web.context.support.XmlWebApplicationContext}如果没有找到。
 * 使用默认的ContextLoader实现，任何指定的上下文类都需要实现{@link ConfigurableWebApplicationContext}接口。
 *
 *
 *
 * <p>Processes a {@link #CONFIG_LOCATION_PARAM "contextConfigLocation"} context-param
 * and passes its value to the context instance, parsing it into potentially multiple
 * file paths which can be separated by any number of commas and spaces, e.g.
 * "WEB-INF/applicationContext1.xml, WEB-INF/applicationContext2.xml".
 * Ant-style path patterns are supported as well, e.g.
 * "WEB-INF/*Context.xml,WEB-INF/spring*.xml" or "WEB-INF/&#42;&#42;/*Context.xml".
 * If not explicitly specified, the context implementation is supposed to use a
 * default location (with XmlWebApplicationContext: "/WEB-INF/applicationContext.xml").
 *
 * <p>Note: In case of multiple config locations, later bean definitions will
 * override ones defined in previously loaded files, at least when using one of
 * Spring's default ApplicationContext implementations. This can be leveraged
 * to deliberately override certain bean definitions via an extra XML file.
 *
 * <p>Above and beyond loading the root application context, this class can optionally
 * load or obtain and hook up a shared parent context to the root application context.
 * See the {@link #loadParentContext(ServletContext)} method for more information.
 *
 * <p>As of Spring 3.1, {@code ContextLoader} supports injecting the root web
 * application context via the {@link #ContextLoader(WebApplicationContext)}
 * constructor, allowing for programmatic configuration in Servlet 3.0+ environments.
 * See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
 *
 * @author Juergen Hoeller
 * @author Colin Sampaleanu
 * @author Sam Brannen
 * @since 17.02.2003
 * @see ContextLoaderListener
 * @see ConfigurableWebApplicationContext
 * @see org.springframework.web.context.support.XmlWebApplicationContext
 */
public class ContextLoader {

	/**
	 * Config param for the root WebApplicationContext id,
	 * to be used as serialization id for the underlying BeanFactory: {@value}.
	 */
	public static final String CONTEXT_ID_PARAM = "contextId";

	/**
	 * Name of servlet context parameter (i.e., {@value}) that can specify the
	 * config location for the root context, falling back to the implementation's
	 * default otherwise.
	 * servlet上下文参数的名称(即{@value})，可以指定根上下文的配置位置，否则退回到实现的默认值。
	 *
	 * @see org.springframework.web.context.support.XmlWebApplicationContext#DEFAULT_CONFIG_LOCATION
	 */
	public static final String CONFIG_LOCATION_PARAM = "contextConfigLocation";

	/**
	 * Config param for the root WebApplicationContext implementation class to use: {@value}.
	 * 要使用的根WebApplicationContext实现类的配置参数:{@value}
	 * @see #determineContextClass(ServletContext)
	 */
	public static final String CONTEXT_CLASS_PARAM = "contextClass";

	/**
	 * Config param for {@link ApplicationContextInitializer} classes to use
	 * for initializing the root web application context: {@value}.
	 * 用于初始化根web应用程序上下文的{@link ApplicationContextInitializer}类的配置参数:
	 *
	 * @see #customizeContext(ServletContext, ConfigurableWebApplicationContext)
	 */
	public static final String CONTEXT_INITIALIZER_CLASSES_PARAM = "contextInitializerClasses";

	/**
	 * Config param for global {@link ApplicationContextInitializer} classes to use
	 * for initializing all web application contexts in the current application: {@value}.
	 * 全局{@link ApplicationContextInitializer}类的配置参数，用于初始化当前应用程序中的所有web应用程序上下文:
	 *
	 * @see #customizeContext(ServletContext, ConfigurableWebApplicationContext)
	 */
	public static final String GLOBAL_INITIALIZER_CLASSES_PARAM = "globalInitializerClasses";

	/**
	 * Any number of these characters are considered delimiters between
	 * multiple values in a single init-param String value.
	 */
	private static final String INIT_PARAM_DELIMITERS = ",; \t\n";

	/**
	 * Name of the class path resource (relative to the ContextLoader class)
	 * that defines ContextLoader's default strategy names.
	 */
	private static final String DEFAULT_STRATEGIES_PATH = "ContextLoader.properties";


	private static final Properties defaultStrategies;

	static {
		// Load default strategy implementations from properties file.
		// This is currently strictly internal and not meant to be customized
		// by application developers.
		try {
			ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, ContextLoader.class);
			defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not load 'ContextLoader.properties': " + ex.getMessage());
		}
	}


	/**
	 * Map from (thread context) ClassLoader to corresponding 'current' WebApplicationContext.
	 */
	private static final Map<ClassLoader, WebApplicationContext> currentContextPerThread =
			new ConcurrentHashMap<>(1);

	/**
	 * The 'current' WebApplicationContext, if the ContextLoader class is
	 * deployed in the web app ClassLoader itself.
	 * 如果ContextLoader类部署在web应用ClassLoader本身，则“当前”WebApplicationContext。
	 */
	@Nullable
	private static volatile WebApplicationContext currentContext;


	/**
	 * The root WebApplicationContext instance that this loader manages.
	 * 这个加载器管理的根WebApplicationContext实例。
	 */
	@Nullable
	private WebApplicationContext context;

	/** Actual ApplicationContextInitializer instances to apply to the context. 应用到上下文的实际ApplicationContextInitializer实例。 */
	private final List<ApplicationContextInitializer<ConfigurableApplicationContext>> contextInitializers =
			new ArrayList<>();


	/**
	 * Create a new {@code ContextLoader} that will create a web application context
	 * based on the "contextClass" and "contextConfigLocation" servlet context-params.
	 * See class-level documentation for details on default values for each.
	 * <p>This constructor is typically used when declaring the {@code
	 * ContextLoaderListener} subclass as a {@code <listener>} within {@code web.xml}, as
	 * a no-arg constructor is required.
	 * <p>The created application context will be registered into the ServletContext under
	 * the attribute name {@link WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE}
	 * and subclasses are free to call the {@link #closeWebApplicationContext} method on
	 * container shutdown to close the application context.
	 * @see #ContextLoader(WebApplicationContext)
	 * @see #initWebApplicationContext(ServletContext)
	 * @see #closeWebApplicationContext(ServletContext)
	 */
	public ContextLoader() {
	}

	/**
	 * Create a new {@code ContextLoader} with the given application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based
	 * registration of listeners is possible through the {@link ServletContext#addListener}
	 * API.
	 * <p>The context may or may not yet be {@linkplain
	 * ConfigurableApplicationContext#refresh() refreshed}. If it (a) is an implementation
	 * of {@link ConfigurableWebApplicationContext} and (b) has <strong>not</strong>
	 * already been refreshed (the recommended approach), then the following will occur:
	 * <ul>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #customizeContext} will be called</li>
	 * <li>Any {@link ApplicationContextInitializer ApplicationContextInitializers} specified through the
	 * "contextInitializerClasses" init-param will be applied.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh refresh()} will be called</li>
	 * </ul>
	 * If the context has already been refreshed or does not implement
	 * {@code ConfigurableWebApplicationContext}, none of the above will occur under the
	 * assumption that the user has performed these actions (or not) per his or her
	 * specific needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * <p>In any case, the given application context will be registered into the
	 * ServletContext under the attribute name {@link
	 * WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE} and subclasses are
	 * free to call the {@link #closeWebApplicationContext} method on container shutdown
	 * to close the application context.
	 * @param context the application context to manage
	 * @see #initWebApplicationContext(ServletContext)
	 * @see #closeWebApplicationContext(ServletContext)
	 */
	public ContextLoader(WebApplicationContext context) {
		this.context = context;
	}


	/**
	 * Specify which {@link ApplicationContextInitializer} instances should be used
	 * to initialize the application context used by this {@code ContextLoader}.
	 * @since 4.2
	 * @see #configureAndRefreshWebApplicationContext
	 * @see #customizeContext
	 */
	@SuppressWarnings("unchecked")
	public void setContextInitializers(@Nullable ApplicationContextInitializer<?>... initializers) {
		if (initializers != null) {
			for (ApplicationContextInitializer<?> initializer : initializers) {
				this.contextInitializers.add((ApplicationContextInitializer<ConfigurableApplicationContext>) initializer);
			}
		}
	}


	/**
	 * 1.创建并根据配置文件刷新spring容器，类型是{@link org.springframework.web.context.support.XmlWebApplicationContext}
	 * 2.将刷新好的spring容器记录在当前web应用程序中，名称是：org.springframework.web.context.WebApplicationContext.ROOT
	 *
	 * Initialize Spring's web application context for the given servlet context,
	 * using the application context provided at construction time, or creating a new one
	 * according to the "{@link #CONTEXT_CLASS_PARAM contextClass}" and
	 * "{@link #CONFIG_LOCATION_PARAM contextConfigLocation}" context-params.
	 *
	 * 为给定的servlet上下文初始化Spring的web应用程序上下文，使用构造时提供的应用程序上下文，
	 * 或者根据“{@link #CONTEXT_CLASS_PARAM contextClass}”和“{@link #CONFIG_LOCATION_PARAM contextConfigLocation}”context-params
	 * 创建一个新的上下文。
	 *
	 * @param servletContext current servlet context
	 * @return the new WebApplicationContext
	 * @see #ContextLoader(WebApplicationContext)
	 * @see #CONTEXT_CLASS_PARAM
	 * @see #CONFIG_LOCATION_PARAM
	 */
	public WebApplicationContext initWebApplicationContext(ServletContext servletContext) {
		/**
		 * 如果当前应用程序已经存在上下文，直接抛出异常
		 */
		if (servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE) != null) {
			throw new IllegalStateException(
					"Cannot initialize context because there is already a root application context present - " +
					"check whether you have multiple ContextLoader* definitions in your web.xml!");
		}

		servletContext.log("Initializing Spring root WebApplicationContext");
		Log logger = LogFactory.getLog(ContextLoader.class);
		if (logger.isInfoEnabled()) {
			logger.info("Root WebApplicationContext: initialization started");
		}
		long startTime = System.currentTimeMillis();

		try {
			// Store context in local instance variable, to guarantee that
			// it is available on ServletContext shutdown.
			/**
			 * 将上下文存储在本地实例变量中，以确保它在ServletContext关闭时可用。
			 */
			if (this.context == null) {
				/**
				 * 默认创建类型为{@link org.springframework.web.context.support.XmlWebApplicationContext}的上下文对象
				 * 可以在xml文件中通过contextClass属性指定上下文对象，但是上下文对象必须是{@link ConfigurableWebApplicationContext}类型的
				 *
				 * 如果没有指定，则创建默认类型的上下文对象，默认上下文对象在ContextLoader.properties文件中指定
				 * 在{@link ContextLoader}类加载时，以静态代码块的方式加载ContextLoader.properties配置文件
				 */
				this.context = createWebApplicationContext(servletContext);
			}
			if (this.context instanceof ConfigurableWebApplicationContext) {
				ConfigurableWebApplicationContext cwac = (ConfigurableWebApplicationContext) this.context;
				if (!cwac.isActive()) {
					// The context has not yet been refreshed -> provide services such as
					// setting the parent context, setting the application context id, etc
					/**
					 * 上下文还没有刷新->提供诸如设置父上下文、设置应用程序上下文id等服务
					 */
					if (cwac.getParent() == null) {
						// The context instance was injected without an explicit parent ->
						// determine parent for root web application context, if any.
						/**
						 * 上下文实例注入时没有显式的父级->确定根web应用程序上下文的父级(如果有的话)。
						 */
						ApplicationContext parent = loadParentContext(servletContext);
						cwac.setParent(parent);
					}
					/**
					 * 配置web应用程序上下文，并刷新应用程序上下文
					 */
					configureAndRefreshWebApplicationContext(cwac, servletContext);
				}
			}
			/**
			 * 将刷新好的spring上下文添加到应用程序中
			 * 名称是：org.springframework.web.context.WebApplicationContext.ROOT
			 */
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this.context);

			/**
			 * 记录当前容器和spring上下文对象的关系
			 */
			ClassLoader ccl = Thread.currentThread().getContextClassLoader();
			if (ccl == ContextLoader.class.getClassLoader()) {
				currentContext = this.context;
			}
			else if (ccl != null) {
				currentContextPerThread.put(ccl, this.context);
			}

			if (logger.isInfoEnabled()) {
				long elapsedTime = System.currentTimeMillis() - startTime;
				logger.info("Root WebApplicationContext initialized in " + elapsedTime + " ms");
			}

			return this.context;
		}
		catch (RuntimeException | Error ex) {
			logger.error("Context initialization failed", ex);
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ex);
			throw ex;
		}
	}

	/**
	 * Instantiate the root WebApplicationContext for this loader, either the
	 * default context class or a custom context class if specified.
	 * 实例化这个加载器的根WebApplicationContext，可以是默认的上下文类，也可以是指定的自定义上下文类。
	 *
	 * <p>This implementation expects custom contexts to implement the
	 * {@link ConfigurableWebApplicationContext} interface.
	 * 这个实现期望自定义上下文来实现{@link ConfigurableWebApplicationContext}接口。
	 *
	 * Can be overridden in subclasses.
	 * 可以在子类中重写。
	 *
	 * <p>In addition, {@link #customizeContext} gets called prior to refreshing the
	 * context, allowing subclasses to perform custom modifications to the context.
	 * 此外，在刷新上下文之前调用{@link #customizeContext}，允许子类对上下文执行自定义修改。
	 *
	 * @param sc current servlet context
	 * @return the root WebApplicationContext
	 * @see ConfigurableWebApplicationContext
	 */
	protected WebApplicationContext createWebApplicationContext(ServletContext sc) {
		/**
		 * 获取{@link WebApplicationContext}上下文类型，可以是自定义的也可以是默认的
		 * 必须是{@link ConfigurableWebApplicationContext}类型的
		 */
		Class<?> contextClass = determineContextClass(sc);
		if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
			throw new ApplicationContextException("Custom context class [" + contextClass.getName() +
					"] is not of type [" + ConfigurableWebApplicationContext.class.getName() + "]");
		}
		/**
		 * 实例化上下文对象
		 */
		return (ConfigurableWebApplicationContext) BeanUtils.instantiateClass(contextClass);
	}

	/**
	 * Return the WebApplicationContext implementation class to use, either the
	 * default XmlWebApplicationContext or a custom context class if specified.
	 * 返回要使用的WebApplicationContext实现类，可以是默认的XmlWebApplicationContext，也可以是指定的自定义上下文类。
	 *
	 * 默认获取类型为{@link org.springframework.web.context.support.XmlWebApplicationContext}的上下文
	 * 可以在xml文件中通过contextClass属性指定上下文对象，但是上下文对象必须是{@link ConfigurableWebApplicationContext}类型的
	 *
	 * 如果没有指定，则使用默认类型的上下文对象，默认上下文对象在ContextLoader.properties文件中指定
	 * 在{@link ContextLoader}类加载时，以静态代码块的方式加载ContextLoader.properties配置文件
	 *
	 * @param servletContext current servlet context
	 * @return the WebApplicationContext implementation class to use
	 * @see #CONTEXT_CLASS_PARAM
	 * @see org.springframework.web.context.support.XmlWebApplicationContext
	 */
	protected Class<?> determineContextClass(ServletContext servletContext) {
		/**
		 * 首先获取通过contextClass参数指定的自定义上下文类型
		 */
		String contextClassName = servletContext.getInitParameter(CONTEXT_CLASS_PARAM);
		if (contextClassName != null) {
			try {
				return ClassUtils.forName(contextClassName, ClassUtils.getDefaultClassLoader());
			}
			catch (ClassNotFoundException ex) {
				throw new ApplicationContextException(
						"Failed to load custom context class [" + contextClassName + "]", ex);
			}
		}
		else {
			/**
			 * 如果没有自定义指定上下文对象，则使用默认的上下文对象
			 * 默认是{@link org.springframework.web.context.support.XmlWebApplicationContext}类型
			 */
			contextClassName = defaultStrategies.getProperty(WebApplicationContext.class.getName());
			try {
				return ClassUtils.forName(contextClassName, ContextLoader.class.getClassLoader());
			}
			catch (ClassNotFoundException ex) {
				throw new ApplicationContextException(
						"Failed to load default context class [" + contextClassName + "]", ex);
			}
		}
	}

	/**
	 * 配置和刷新Web应用程序上下文
	 *
	 * 1.根据配置文件为web应用程序设置id，如果没有指定，则使用默认id
	 * 2.为web应用程序设置上下文，添加配置文件，设置环境变量
	 * 3.执行web应用程序刷新前的自定义回调
	 * 4.刷新应用程序
	 *
	 * @param wac
	 * @param sc
	 */
	protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac, ServletContext sc) {
		/**
		 * 如果上下文对象的id是默认id，则根据配置设置id
		 * 默认{@link org.springframework.context.support.AbstractApplicationContext#id}
		 * 就是wac所属类的全部限定名 + "@" + wac.hashCode()的十六进制(就是默认的{@link #toString()}方法)
		 */
		if (ObjectUtils.identityToString(wac).equals(wac.getId())) {
			// The application context id is still set to its original default value
			// -> assign a more useful id based on available information
			/**
			 * 应用程序上下文id仍然被设置为其原始默认值——>根据可用信息分配一个更有用的id
			 *
			 * 可以通过contextId参数设置上下文对象的名称
			 */
			String idParam = sc.getInitParameter(CONTEXT_ID_PARAM);
			if (idParam != null) {
				wac.setId(idParam);
			}
			else {
				// Generate default id...
				/**
				 * 生成默认id…
				 *
				 * 如果配置中没有指定上下文id，则生成一个默认id
				 * 默认是{@link WebApplicationContext}类的全部限定名 + ":"
				 */
				wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX +
						ObjectUtils.getDisplayString(sc.getContextPath()));
			}
		}

		/**
		 * 为应用程序设置上下文对象，sc对象是tomcat传过来的，wac是创建的{@link org.springframework.web.context.support.XmlWebApplicationContext}对象
		 */
		wac.setServletContext(sc);
		/**
		 * 获取contextConfigLocation属性，并为web应用程序添加配置文件
		 */
		String configLocationParam = sc.getInitParameter(CONFIG_LOCATION_PARAM);
		if (configLocationParam != null) {
			wac.setConfigLocation(configLocationParam);
		}

		// The wac environment's #initPropertySources will be called in any case when the context
		// is refreshed; do it eagerly here to ensure servlet property sources are in place for
		// use in any post-processing or initialization that occurs below prior to #refresh
		/**
		 * 当上下文刷新时，wac环境的initPropertySources将被调用;
		 * 是否急于在这里确保servlet属性源已就位，以便在刷新之前发生的任何后处理或初始化中使用
		 *
		 * 创建上下文环境对象
		 */
		ConfigurableEnvironment env = wac.getEnvironment();
		if (env instanceof ConfigurableWebEnvironment) {
			((ConfigurableWebEnvironment) env).initPropertySources(sc, null);
		}

		/**
		 * 执行自定义回调
		 */
		customizeContext(sc, wac);
		/**
		 * 刷新应用程序 {@link AbstractApplicationContext#refresh()}
		 */
		wac.refresh();
	}

	/**
	 * 1.获取由参数{@link #GLOBAL_INITIALIZER_CLASSES_PARAM}和{@link #CONTEXT_INITIALIZER_CLASSES_PARAM}指定的
	 * {@link ApplicationContextInitializer}类型的web程序刷新前的自定义回调
	 * 2.检查自定义回调的泛型是否是当前应用程序的父类
	 * 3.按照{@link org.springframework.core.Ordered}接口或者{@link org.springframework.core.annotation.Order}注解排序
	 * 4.执行自定义回调
	 *
	 *
	 * Customize the {@link ConfigurableWebApplicationContext} created by this
	 * ContextLoader after config locations have been supplied to the context
	 * but before the context is <em>refreshed</em>.
	 * 自定义由这个ContextLoader创建的{@link ConfigurableWebApplicationContext}，
	 * 在配置位置被提供给上下文之后，但在上下文<em>刷新<em>之前。
	 *
	 * <p>The default implementation {@linkplain #determineContextInitializerClasses(ServletContext)
	 * determines} what (if any) context initializer classes have been specified through
	 * {@linkplain #CONTEXT_INITIALIZER_CLASSES_PARAM context init parameters} and
	 * {@linkplain ApplicationContextInitializer#initialize invokes each} with the
	 * given web application context.
	 * 默认实现{@linkplain #determineContextInitializerClasses(ServletContext)确定}
	 * 通过{@linkplain #CONTEXT_INITIALIZER_CLASSES_PARAM context init parameters}
	 * 和{@linkplain ApplicationContextInitializer#initialize 使用给定的web应用程序上下文调用每个}指定了什么(如果有)上下文初始化类。
	 *
	 * <p>Any {@code ApplicationContextInitializers} implementing
	 * {@link org.springframework.core.Ordered Ordered} or marked with @{@link
	 * org.springframework.core.annotation.Order Order} will be sorted appropriately.
	 * 任何实现了{@code ApplicationContextInitializers}或标有@{@link org.springframework.core.annotation.Order Order}的
	 * {@code ApplicationContextInitializers}将被适当地排序。
	 *
	 * @param sc the current servlet context
	 * @param wac the newly created application context
	 * @see #CONTEXT_INITIALIZER_CLASSES_PARAM
	 * @see ApplicationContextInitializer#initialize(ConfigurableApplicationContext)
	 */
	protected void customizeContext(ServletContext sc, ConfigurableWebApplicationContext wac) {
		/**
		 * 获取由参数{@link #GLOBAL_INITIALIZER_CLASSES_PARAM}和{@link #CONTEXT_INITIALIZER_CLASSES_PARAM}指定的
		 * {@link ApplicationContextInitializer}类型的web程序刷新前的自定义回调
		 */
		List<Class<ApplicationContextInitializer<ConfigurableApplicationContext>>> initializerClasses =
				determineContextInitializerClasses(sc);

		for (Class<ApplicationContextInitializer<ConfigurableApplicationContext>> initializerClass : initializerClasses) {
			/**
			 * 获取自定义初始化回调的泛型
			 */
			Class<?> initializerContextClass =
					GenericTypeResolver.resolveTypeArgument(initializerClass, ApplicationContextInitializer.class);
			/**
			 * 检查wac对象是否自定义回调泛型的类型，因为调用{@link ApplicationContextInitializer#initialize(ConfigurableApplicationContext)}
			 * 方法时需要把wac作为参数
			 */
			if (initializerContextClass != null && !initializerContextClass.isInstance(wac)) {
				throw new ApplicationContextException(String.format(
						"Could not apply context initializer [%s] since its generic parameter [%s] " +
						"is not assignable from the type of application context used by this " +
						"context loader: [%s]", initializerClass.getName(), initializerContextClass.getName(),
						wac.getClass().getName()));
			}
			this.contextInitializers.add(BeanUtils.instantiateClass(initializerClass));
		}

		/**
		 * 按照{@link org.springframework.core.Ordered}接口或者{@link org.springframework.core.annotation.Order}注解排序
		 */
		AnnotationAwareOrderComparator.sort(this.contextInitializers);
		/**
		 * 执行所有的自定义初始化回调
		 */
		for (ApplicationContextInitializer<ConfigurableApplicationContext> initializer : this.contextInitializers) {
			initializer.initialize(wac);
		}
	}

	/**
	 * Return the {@link ApplicationContextInitializer} implementation classes to use
	 * if any have been specified by {@link #CONTEXT_INITIALIZER_CLASSES_PARAM}.
	 * 如果{@link #CONTEXT_INITIALIZER_CLASSES_PARAM}指定了实现类，则返回要使用的{@link ApplicationContextInitializer}实现类。
	 *
	 * 查找并加载由参数{@link #GLOBAL_INITIALIZER_CLASSES_PARAM}和{@link #CONTEXT_INITIALIZER_CLASSES_PARAM}
	 * 指定的并且实现了{@link ApplicationContextInitializer}接口的类型，用来在应用程序刷新前做自定义初始化工作
	 *
	 * @param servletContext current servlet context
	 * @see #CONTEXT_INITIALIZER_CLASSES_PARAM
	 */
	protected List<Class<ApplicationContextInitializer<ConfigurableApplicationContext>>>
			determineContextInitializerClasses(ServletContext servletContext) {

		List<Class<ApplicationContextInitializer<ConfigurableApplicationContext>>> classes =
				new ArrayList<>();

		/**
		 * 获取由globalInitializerClasses指定的自定义程序初始化类型
		 */
		String globalClassNames = servletContext.getInitParameter(GLOBAL_INITIALIZER_CLASSES_PARAM);
		if (globalClassNames != null) {
			for (String className : StringUtils.tokenizeToStringArray(globalClassNames, INIT_PARAM_DELIMITERS)) {
				classes.add(loadInitializerClass(className));
			}
		}

		/**
		 * 获取由contextInitializerClasses指定的自定义程序初始化类型
		 */
		String localClassNames = servletContext.getInitParameter(CONTEXT_INITIALIZER_CLASSES_PARAM);
		if (localClassNames != null) {
			for (String className : StringUtils.tokenizeToStringArray(localClassNames, INIT_PARAM_DELIMITERS)) {
				classes.add(loadInitializerClass(className));
			}
		}

		/**
		 * 返回所有应用程序刷新前的初始化回调类型
		 */
		return classes;
	}

	/**
	 * 通过全部限定名加载类，并判断是否{@link ApplicationContextInitializer}类型
	 *
	 * @param className
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Class<ApplicationContextInitializer<ConfigurableApplicationContext>> loadInitializerClass(String className) {
		try {
			/**
			 * 加载类并判断类型，如果不是{@link ApplicationContextInitializer}类型的，则直接抛出异常
			 */
			Class<?> clazz = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
			if (!ApplicationContextInitializer.class.isAssignableFrom(clazz)) {
				throw new ApplicationContextException(
						"Initializer class does not implement ApplicationContextInitializer interface: " + clazz);
			}
			return (Class<ApplicationContextInitializer<ConfigurableApplicationContext>>) clazz;
		}
		catch (ClassNotFoundException ex) {
			throw new ApplicationContextException("Failed to load context initializer class [" + className + "]", ex);
		}
	}

	/**
	 * Template method with default implementation (which may be overridden by a
	 * subclass), to load or obtain an ApplicationContext instance which will be
	 * used as the parent context of the root WebApplicationContext. If the
	 * return value from the method is null, no parent context is set.
	 * <p>The main reason to load a parent context here is to allow multiple root
	 * web application contexts to all be children of a shared EAR context, or
	 * alternately to also share the same parent context that is visible to
	 * EJBs. For pure web applications, there is usually no need to worry about
	 * having a parent context to the root web application context.
	 * <p>The default implementation simply returns {@code null}, as of 5.0.
	 * @param servletContext current servlet context
	 * @return the parent application context, or {@code null} if none
	 */
	@Nullable
	protected ApplicationContext loadParentContext(ServletContext servletContext) {
		return null;
	}

	/**
	 * Close Spring's web application context for the given servlet context.
	 * <p>If overriding {@link #loadParentContext(ServletContext)}, you may have
	 * to override this method as well.
	 * @param servletContext the ServletContext that the WebApplicationContext runs in
	 */
	public void closeWebApplicationContext(ServletContext servletContext) {
		servletContext.log("Closing Spring root WebApplicationContext");
		try {
			if (this.context instanceof ConfigurableWebApplicationContext) {
				((ConfigurableWebApplicationContext) this.context).close();
			}
		}
		finally {
			ClassLoader ccl = Thread.currentThread().getContextClassLoader();
			if (ccl == ContextLoader.class.getClassLoader()) {
				currentContext = null;
			}
			else if (ccl != null) {
				currentContextPerThread.remove(ccl);
			}
			servletContext.removeAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		}
	}


	/**
	 * Obtain the Spring root web application context for the current thread
	 * (i.e. for the current thread's context ClassLoader, which needs to be
	 * the web application's ClassLoader).
	 * @return the current root web application context, or {@code null}
	 * if none found
	 * @see org.springframework.web.context.support.SpringBeanAutowiringSupport
	 */
	@Nullable
	public static WebApplicationContext getCurrentWebApplicationContext() {
		ClassLoader ccl = Thread.currentThread().getContextClassLoader();
		if (ccl != null) {
			WebApplicationContext ccpt = currentContextPerThread.get(ccl);
			if (ccpt != null) {
				return ccpt;
			}
		}
		return currentContext;
	}

}
