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

package org.springframework.web.servlet.mvc.method.annotation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.*;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.*;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;
import org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.WebUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extension of {@link AbstractHandlerMethodAdapter} that supports
 * {@link RequestMapping @RequestMapping} annotated {@link HandlerMethod HandlerMethods}.
 *
 * {@link AbstractHandlerMethodAdapter}的扩展，支持{@link RequestMapping @RequestMapping}注解{@link HandlerMethod HandlerMethods}。
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers},
 * or alternatively, to re-configure all argument and return value types,
 * use {@link #setArgumentResolvers} and {@link #setReturnValueHandlers}.
 * 支持自定义参数和返回值类型可以通过{@link #setCustomArgumentResolvers}和{@link #setCustomReturnValueHandlers}添加，
 * 或者，重新配置所有参数和返回值类型，使用{@link #setArgumentResolvers}和{@link #setReturnValueHandlers}。
 *
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 3.1
 * @see HandlerMethodArgumentResolver
 * @see HandlerMethodReturnValueHandler
 */
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
		implements BeanFactoryAware, InitializingBean {

	/**
	 * Boolean flag controlled by a {@code spring.xml.ignore} system property that instructs Spring to
	 * ignore XML, i.e. to not initialize the XML-related infrastructure.
	 * 由{@code spring.xml.ignore}系统属性，指示Spring忽略XML，即不初始化XML相关的基础设施。
	 *
	 * <p>The default is "false".
	 */
	private static final boolean shouldIgnoreXml = SpringProperties.getFlag("spring.xml.ignore");

	/**
	 * MethodFilter that matches {@link InitBinder @InitBinder} methods.
	 * 匹配{@link InitBinder @InitBinder}方法的MethodFilter。
	 */
	public static final MethodFilter INIT_BINDER_METHODS = method ->
			AnnotatedElementUtils.hasAnnotation(method, InitBinder.class);

	/**
	 * MethodFilter that matches {@link ModelAttribute @ModelAttribute} methods.
	 * 匹配{@link ModelAttribute @ModelAttribute}方法的MethodFilter。
	 */
	public static final MethodFilter MODEL_ATTRIBUTE_METHODS = method ->
			(!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class) &&
					AnnotatedElementUtils.hasAnnotation(method, ModelAttribute.class));


	/**
	 * 缓存自定义参数解析器集合
	 */
	@Nullable
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	/**
	 * {@link HandlerMethodArgumentResolverComposite}实例，缓存已注册参数解析器的句柄
	 * 在{@link #afterPropertiesSet()}中调用{@link #getDefaultArgumentResolvers()}方法初始化
	 */
	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;

	/**
	 * 缓存适用于带有{@link InitBinder}方法的参数解析器
	 */
	@Nullable
	private HandlerMethodArgumentResolverComposite initBinderArgumentResolvers;

	@Nullable
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	/**
	 * {@link HandlerMethodReturnValueHandlerComposite}实列，缓存已注册的方法返回值解析器的句柄
	 * 在{@link #afterPropertiesSet()}方法中调用{@link #getDefaultReturnValueHandlers()}方法初始化
	 */
	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	@Nullable
	private List<ModelAndViewResolver> modelAndViewResolvers;

	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	/**
	 * 消息转换器，在通过无参构造创建当前类实例时初始化
	 */
	private List<HttpMessageConverter<?>> messageConverters;

	/**
	 * 缓存全局实现了{@link RequestBodyAdvice}接口或者{@link ResponseBodyAdvice}接口且标注了 {@link ControllerAdvice}注解的bean实例
	 * 在{@link this#initControllerAdviceCache()}中初始化
	 */
	private final List<Object> requestResponseBodyAdvice = new ArrayList<>();

	@Nullable
	private WebBindingInitializer webBindingInitializer;

	private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("MvcAsync");

	@Nullable
	private Long asyncRequestTimeout;

	private CallableProcessingInterceptor[] callableInterceptors = new CallableProcessingInterceptor[0];

	private DeferredResultProcessingInterceptor[] deferredResultInterceptors = new DeferredResultProcessingInterceptor[0];

	private ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

	private boolean ignoreDefaultModelOnRedirect = false;

	private int cacheSecondsForSessionAttributeHandlers = 0;

	private boolean synchronizeOnSession = false;

	private SessionAttributeStore sessionAttributeStore = new DefaultSessionAttributeStore();

	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	@Nullable
	private ConfigurableBeanFactory beanFactory;

	private final Map<Class<?>, SessionAttributesHandler> sessionAttributesHandlerCache = new ConcurrentHashMap<>(64);

	private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap<>(64);

	/**
	 * 缓存全局带有{@link InitBinder}注解的方法
	 */
	private final Map<ControllerAdviceBean, Set<Method>> initBinderAdviceCache = new LinkedHashMap<>();

	private final Map<Class<?>, Set<Method>> modelAttributeCache = new ConcurrentHashMap<>(64);

	/**
	 * 缓存全局带有{@link ModelAttribute}注解的方法
	 */
	private final Map<ControllerAdviceBean, Set<Method>> modelAttributeAdviceCache = new LinkedHashMap<>();


	public RequestMappingHandlerAdapter() {
		this.messageConverters = new ArrayList<>(4);
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(new StringHttpMessageConverter());
		if (!shouldIgnoreXml) {
			try {
				this.messageConverters.add(new SourceHttpMessageConverter<>());
			}
			catch (Error err) {
				// Ignore when no TransformerFactory implementation is available
			}
		}
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}


	/**
	 * Provide resolvers for custom argument types. Custom resolvers are ordered
	 * after built-in ones. To override the built-in support for argument
	 * resolution use {@link #setArgumentResolvers} instead.
	 *
	 * 为自定义参数类型提供解析器。自定义解析器排在内置解析器之后。
	 * 要覆盖对参数解析的内置支持，请使用{@link #setArgumentResolvers}代替。
	 */
	public void setCustomArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers = argumentResolvers;
	}

	/**
	 * Return the custom argument resolvers, or {@code null}.
	 * 返回自定义参数解析器，或{@code null}。
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 *
	 * 配置支持的参数类型的完整列表，从而覆盖默认情况下配置的解析器。
	 */
	public void setArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		}
		else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the configured argument resolvers, or possibly {@code null} if
	 * not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return (this.argumentResolvers != null ? this.argumentResolvers.getResolvers() : null);
	}

	/**
	 * Configure the supported argument types in {@code @InitBinder} methods.
	 */
	public void setInitBinderArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.initBinderArgumentResolvers = null;
		}
		else {
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.initBinderArgumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the argument resolvers for {@code @InitBinder} methods, or possibly
	 * {@code null} if not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
		return (this.initBinderArgumentResolvers != null ? this.initBinderArgumentResolvers.getResolvers() : null);
	}

	/**
	 * Provide handlers for custom return value types. Custom handlers are
	 * ordered after built-in ones. To override the built-in support for
	 * return value handling use {@link #setReturnValueHandlers}.
	 */
	public void setCustomReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * Return the custom return value handlers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * Configure the complete list of supported return value types thus
	 * overriding handlers that would otherwise be configured by default.
	 */
	public void setReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		}
		else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * Return the configured handlers, or possibly {@code null} if not
	 * initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
		return (this.returnValueHandlers != null ? this.returnValueHandlers.getHandlers() : null);
	}

	/**
	 * Provide custom {@link ModelAndViewResolver ModelAndViewResolvers}.
	 * <p><strong>Note:</strong> This method is available for backwards
	 * compatibility only. However, it is recommended to re-write a
	 * {@code ModelAndViewResolver} as {@link HandlerMethodReturnValueHandler}.
	 * An adapter between the two interfaces is not possible since the
	 * {@link HandlerMethodReturnValueHandler#supportsReturnType} method
	 * cannot be implemented. Hence {@code ModelAndViewResolver}s are limited
	 * to always being invoked at the end after all other return value
	 * handlers have been given a chance.
	 * <p>A {@code HandlerMethodReturnValueHandler} provides better access to
	 * the return type and controller method information and can be ordered
	 * freely relative to other return value handlers.
	 */
	public void setModelAndViewResolvers(@Nullable List<ModelAndViewResolver> modelAndViewResolvers) {
		this.modelAndViewResolvers = modelAndViewResolvers;
	}

	/**
	 * Return the configured {@link ModelAndViewResolver ModelAndViewResolvers}, or {@code null}.
	 */
	@Nullable
	public List<ModelAndViewResolver> getModelAndViewResolvers() {
		return this.modelAndViewResolvers;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Provide the converters to use in argument resolvers and return value
	 * handlers that support reading and/or writing to the body of the
	 * request and response.
	 * 提供在参数解析器和返回值处理程序中使用的转换器，以支持对请求和响应正文的读写。
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Return the configured message body converters. 返回已配置的消息体转换器。
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Add one or more {@code RequestBodyAdvice} instances to intercept the
	 * request before it is read and converted for {@code @RequestBody} and
	 * {@code HttpEntity} method arguments.
	 * 添加一个或多个{@code RequestBodyAdvice}实例，在{@code @RequestBody}和{@code HttpEntity}方法参数读取和转换请求之前拦截请求。
	 */
	public void setRequestBodyAdvice(@Nullable List<RequestBodyAdvice> requestBodyAdvice) {
		if (requestBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(requestBodyAdvice);
		}
	}

	/**
	 * Add one or more {@code ResponseBodyAdvice} instances to intercept the
	 * response before {@code @ResponseBody} or {@code ResponseEntity} return
	 * values are written to the response body.
	 */
	public void setResponseBodyAdvice(@Nullable List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		if (responseBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	/**
	 * Provide a WebBindingInitializer with "global" initialization to apply
	 * to every DataBinder instance.
	 * 提供一个具有“全局”初始化的WebBindingInitializer，以应用于每个DataBinder实例。
	 */
	public void setWebBindingInitializer(@Nullable WebBindingInitializer webBindingInitializer) {
		this.webBindingInitializer = webBindingInitializer;
	}

	/**
	 * Return the configured WebBindingInitializer, or {@code null} if none.
	 * 返回配置的WebBindingInitializer，如果没有则返回{@code null}。
	 */
	@Nullable
	public WebBindingInitializer getWebBindingInitializer() {
		return this.webBindingInitializer;
	}

	/**
	 * Set the default {@link AsyncTaskExecutor} to use when a controller method
	 * return a {@link Callable}. Controller methods can override this default on
	 * a per-request basis by returning an {@link WebAsyncTask}.
	 * <p>By default a {@link SimpleAsyncTaskExecutor} instance is used.
	 * It's recommended to change that default in production as the simple executor
	 * does not re-use threads.
	 */
	public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * Specify the amount of time, in milliseconds, before concurrent handling
	 * should time out. In Servlet 3, the timeout begins after the main request
	 * processing thread has exited and ends when the request is dispatched again
	 * for further processing of the concurrently produced result.
	 * <p>If this value is not set, the default timeout of the underlying
	 * implementation is used.
	 * @param timeout the timeout value in milliseconds
	 */
	public void setAsyncRequestTimeout(long timeout) {
		this.asyncRequestTimeout = timeout;
	}

	/**
	 * Configure {@code CallableProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setCallableInterceptors(List<CallableProcessingInterceptor> interceptors) {
		this.callableInterceptors = interceptors.toArray(new CallableProcessingInterceptor[0]);
	}

	/**
	 * Configure {@code DeferredResultProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setDeferredResultInterceptors(List<DeferredResultProcessingInterceptor> interceptors) {
		this.deferredResultInterceptors = interceptors.toArray(new DeferredResultProcessingInterceptor[0]);
	}

	/**
	 * Configure the registry for reactive library types to be supported as
	 * return values from controller methods.
	 * @since 5.0.5
	 */
	public void setReactiveAdapterRegistry(ReactiveAdapterRegistry reactiveAdapterRegistry) {
		this.reactiveAdapterRegistry = reactiveAdapterRegistry;
	}

	/**
	 * Return the configured reactive type registry of adapters.
	 * @since 5.0
	 */
	public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
		return this.reactiveAdapterRegistry;
	}

	/**
	 * By default the content of the "default" model is used both during
	 * rendering and redirect scenarios. Alternatively a controller method
	 * can declare a {@link RedirectAttributes} argument and use it to provide
	 * attributes for a redirect.
	 * <p>Setting this flag to {@code true} guarantees the "default" model is
	 * never used in a redirect scenario even if a RedirectAttributes argument
	 * is not declared. Setting it to {@code false} means the "default" model
	 * may be used in a redirect if the controller method doesn't declare a
	 * RedirectAttributes argument.
	 * <p>The default setting is {@code false} but new applications should
	 * consider setting it to {@code true}.
	 * @see RedirectAttributes
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * Specify the strategy to store session attributes with. The default is
	 * {@link org.springframework.web.bind.support.DefaultSessionAttributeStore},
	 * storing session attributes in the HttpSession with the same attribute
	 * name as in the model.
	 */
	public void setSessionAttributeStore(SessionAttributeStore sessionAttributeStore) {
		this.sessionAttributeStore = sessionAttributeStore;
	}

	/**
	 * Cache content produced by {@code @SessionAttributes} annotated handlers
	 * for the given number of seconds.
	 * <p>Possible values are:
	 * <ul>
	 * <li>-1: no generation of cache-related headers</li>
	 * <li>0 (default value): "Cache-Control: no-store" will prevent caching</li>
	 * <li>1 or higher: "Cache-Control: max-age=seconds" will ask to cache content;
	 * not advised when dealing with session attributes</li>
	 * </ul>
	 * <p>In contrast to the "cacheSeconds" property which will apply to all general
	 * handlers (but not to {@code @SessionAttributes} annotated handlers),
	 * this setting will apply to {@code @SessionAttributes} handlers only.
	 * @see #setCacheSeconds
	 * @see org.springframework.web.bind.annotation.SessionAttributes
	 */
	public void setCacheSecondsForSessionAttributeHandlers(int cacheSecondsForSessionAttributeHandlers) {
		this.cacheSecondsForSessionAttributeHandlers = cacheSecondsForSessionAttributeHandlers;
	}

	/**
	 * Set if controller execution should be synchronized on the session,
	 * to serialize parallel invocations from the same client.
	 * 设置控制器执行是否应在会话上同步，以序列化来自同一客户端的并行调用。
	 *
	 * <p>More specifically, the execution of the {@code handleRequestInternal}
	 * method will get synchronized if this flag is "true". The best available
	 * session mutex will be used for the synchronization; ideally, this will
	 * be a mutex exposed by HttpSessionMutexListener.
	 * 更具体地说，如果该标志为“true”，{@code handleRequestInternal}方法的执行将同步。
	 * 最好的会话互斥量将用于同步;理想情况下，这将是一个由HttpSessionMutexListener公开的互斥量。
	 *
	 * <p>The session mutex is guaranteed to be the same object during
	 * the entire lifetime of the session, available under the key defined
	 * by the {@code SESSION_MUTEX_ATTRIBUTE} constant. It serves as a
	 * safe reference to synchronize on for locking on the current session.
	 * 会话互斥锁保证在会话的整个生命周期内都是相同的对象，在常量{@code SESSION_MUTEX_ATTRIBUTE}定义的键下可用。
	 * 它作为一个安全的参考来同步锁定当前会话。
	 *
	 * <p>In many cases, the HttpSession reference itself is a safe mutex
	 * as well, since it will always be the same object reference for the
	 * same active logical session. However, this is not guaranteed across
	 * different servlet containers; the only 100% safe way is a session mutex.
	 * 在许多情况下，HttpSession引用本身也是一个安全的互斥量，因为它总是同一个活动逻辑会话的同一个对象引用。
	 * 然而，这在不同的servlet容器之间是不能保证的;唯一100%安全的方法是会话互斥。
	 *
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 * @see org.springframework.web.util.WebUtils#getSessionMutex(jakarta.servlet.http.HttpSession)
	 */
	public void setSynchronizeOnSession(boolean synchronizeOnSession) {
		this.synchronizeOnSession = synchronizeOnSession;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter names if needed
	 * (e.g. for default attribute names).
	 * 如果需要，设置ParameterNameDiscoverer用于解析方法参数名(例如默认属性名)。
	 *
	 * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * A {@link ConfigurableBeanFactory} is expected for resolving expressions
	 * in method argument default values.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		}
	}

	/**
	 * Return the owning factory of this bean instance, or {@code null} if none.
	 */
	@Nullable
	protected ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	/**
	 * 初始化自定义值处理器和参数解析器
	 */
	@Override
	public void afterPropertiesSet() {
		// Do this first, it may add ResponseBody advice beans
		/**
		 * 先做这个，是否会添加ResponseBody建议bean
		 *
		 * 1.查找并缓存带有{@link org.springframework.web.bind.annotation.ControllerAdvice}注解的bean
		 * 分别缓存带有{@link ModelAttribute}或者{@link InitBinder}注解的方法
		 *
		 * 2.和实现了{@link RequestBodyAdvice}或者{@link ResponseBodyAdvice}接口的bean
		 */
		initControllerAdviceCache();

		/**
		 * 初始化方法参数和返回值解析器
		 */
		if (this.argumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			/**
			 * {@link HandlerMethodArgumentResolverComposite}是已注册的方法参数解析器的句柄，通过在已经注册的解析器中查找合适的解析器处理给定方法参数
			 */
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		/**
		 * 初始化适用于带有{@link InitBinder}方法的参数解析器，适用于带有{@link InitBinder}方法的参数解析器是{@link this#argumentResolvers}的一个子集
		 */
		if (this.initBinderArgumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultInitBinderArgumentResolvers();
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		/**
		 * 初始化方法返回值处理器
		 */
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			/**
			 * {@link HandlerMethodReturnValueHandlerComposite}是已注册的返回值处理器的句柄，通过在已注册的返回值解析中查找合适的处理，并委托给合适的处理器处理给定返回值
			 * 如果没有合适的返回值处理器，抛出异常
			 */
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initControllerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}

		/**
		 * 在给定的{@link ApplicationContext}中找到带有{@link ControllerAdvice @ControllerAdvice}注释的bean，
		 * 并将它们包装为{@code ControllerAdviceBean}实例。
		 */
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());

		List<Object> requestResponseBodyAdviceBeans = new ArrayList<>();

		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
			}
			/**
			 * 查找类中带有{@link ModelAttribute}注解，但是不带有{@link RequestMapping}注解的方法
			 * 带有{@link ModelAttribute}注解的方法会在Controller调用完成后调用，用来给{@link ModelAndView}设置自定义参数，
			 * 如果{@link ModelAttribute}注解的方法同时带有{@link RequestMapping}注解，则会重复调用
			 */
			Set<Method> attrMethods = MethodIntrospector.selectMethods(beanType, MODEL_ATTRIBUTE_METHODS);
			if (!attrMethods.isEmpty()) {
				this.modelAttributeAdviceCache.put(adviceBean, attrMethods);
			}
			/**
			 * 获取带有{@link InitBinder}注解的方法
			 */
			Set<Method> binderMethods = MethodIntrospector.selectMethods(beanType, INIT_BINDER_METHODS);
			if (!binderMethods.isEmpty()) {
				this.initBinderAdviceCache.put(adviceBean, binderMethods);
			}
			/**
			 * 保存通过{@link org.springframework.web.bind.annotation.ControllerAdvice}注入的实现了{@link RequestBodyAdvice}或者
			 * {@link ResponseBodyAdvice}接口的bean
			 */
			if (RequestBodyAdvice.class.isAssignableFrom(beanType) || ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
				requestResponseBodyAdviceBeans.add(adviceBean);
			}
		}

		/**
		 * 缓存全局实现了{@link RequestBodyAdvice}接口或者{@link ResponseBodyAdvice}接口的bean实例
		 */
		if (!requestResponseBodyAdviceBeans.isEmpty()) {
			this.requestResponseBodyAdvice.addAll(0, requestResponseBodyAdviceBeans);
		}

		if (logger.isDebugEnabled()) {
			int modelSize = this.modelAttributeAdviceCache.size();
			int binderSize = this.initBinderAdviceCache.size();
			int reqCount = getBodyAdviceCount(RequestBodyAdvice.class);
			int resCount = getBodyAdviceCount(ResponseBodyAdvice.class);
			if (modelSize == 0 && binderSize == 0 && reqCount == 0 && resCount == 0) {
				logger.debug("ControllerAdvice beans: none");
			}
			else {
				logger.debug("ControllerAdvice beans: " + modelSize + " @ModelAttribute, " + binderSize +
						" @InitBinder, " + reqCount + " RequestBodyAdvice, " + resCount + " ResponseBodyAdvice");
			}
		}
	}

	// Count all advice, including explicit registrations..

	private int getBodyAdviceCount(Class<?> adviceType) {
		List<Object> advice = this.requestResponseBodyAdvice;
		return RequestBodyAdvice.class.isAssignableFrom(adviceType) ?
				RequestResponseBodyAdviceChain.getAdviceByType(advice, RequestBodyAdvice.class).size() :
				RequestResponseBodyAdviceChain.getAdviceByType(advice, ResponseBodyAdvice.class).size();
	}

	/**
	 * 初始化可能使用到的方法参数和返回值解析器，也会添加通过{@link this#setCustomArgumentResolvers(List)}方法添加的自定义参数解析器
	 *
	 * Return the list of argument resolvers to use including built-in resolvers
	 * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 *
	 * 返回要使用的参数解析器列表，包括内置解析器和通过{@link #setCustomArgumentResolvers}提供的自定义解析器。
	 */
	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(30);

		// Annotation-based argument resolution
		/**
		 * 基于注释的参数解析
		 */
		/**
		 * {@link RequestParamMethodArgumentResolver}支持以下类型的参数解析：
		 *
		 * 1. 带有{@link RequestParam}注解，且指定参数名称的{@link Map}类型的参数
		 * 2. 带有{@link RequestParam}注解，且参数类型是{@link Map}以外的其他类型
		 * 3. 没有带{@link RequestParam}注解，且参数类型是表单项类型，或者参数类型为表单项类型的集合
		 * 4. 没有带{@link RequestParam}注解，且允许使用默认解析策略，且参数类型为简单类型或者简单类型的数组
		 *
		 * 不适用默认参数解析策略
		 */
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		/**
		 * 用于解析带有{@link org.springframework.web.bind.annotation.RequestParam}注解，
		 * 但是没有指定{@link org.springframework.web.bind.annotation.RequestParam#name()}参数，
		 * 且方法参数类型为{@link Map}类型的方法参数
		 */
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		/**
		 * 解析带有@{@link PathVariable}注释的方法参数。如果参数是{@link Map}类型，则{@link PathVariable#value()}必须指定了参数名称，否则当前处理器不适用
		 * 从请求中获取名称为{@link org.springframework.web.servlet.HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE}的{@link Map<String, String>}类型的值
		 * 该值是在匹配请求处理程序时添加进去的
		 */
		resolvers.add(new PathVariableMethodArgumentResolver());
		/**
		 * 处理参数类型为{@link Map}并且使用了{@link PathVariable}注解，且{@link PathVariable#value()}值为空的方法参数
		 * 返回一个包含所有URI模板变量的Map或一个空Map。
		 *
		 * *************************************************************************************************************
		 * {@link PathVariableMethodArgumentResolver}取请求中所有URI模板变量中指定名称的变量值
		 * {@link PathVariableMapMethodArgumentResolver}取请求中所有URI模板变量的键值对集合，或者空集合
		 */
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		/**
		 * 解析带有{@link MatrixVariable @MatrixVariable}注释的参数。
		 * 参数类型为{@link Map}并且没有指定{@link MatrixVariable#name()}的情况除外
		 */
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		/**
		 * 解析参数类型为{@link Map}并且{@link MatrixVariable#name()}属性未指定的参数，返回指定名称或全部URI路径参数矩阵
		 */
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		/**
		 * 解析带有{@link ModelAttribute} 的参数和返回值，不适用默认策略
		 */
		resolvers.add(new ServletModelAttributeMethodProcessor(false));
		/**
		 * 解析带有{@link RequestBody}注解的方法参数，通过{@link HttpMessageConverter}将请求参数转换成方法参数类型的值
		 */
		resolvers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		/**
		 * 解析被{@link RequestPart}注解注释，或者参数是多部份参数类型，且没有被{@link RequestParam}注解注释的参数
		 * 获取参数名称的表单项，或者将其转换成参数类型的值
		 */
		resolvers.add(new RequestPartMethodArgumentResolver(getMessageConverters(), this.requestResponseBodyAdvice));
		/**
		 * 解析带有{@link RequestHeader}注解，且参数类型不是{@link Map}的方法参数
		 */
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		/**
		 * 解析参数带有{@link RequestHeader}注解，且参数类型是{@link Map}类型的方法参数
		 * 将所有的请求头添加到{@link Map}结构中
		 */
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		/**
		 * 解析带有{@link CookieValue}注解的方法参数
		 */
		resolvers.add(new ServletCookieValueMethodArgumentResolver(getBeanFactory()));
		/**
		 * 解析带有{@link Value}注解的方法参数
		 * {@link ExpressionValueMethodArgumentResolver#resolveName(String, MethodParameter, NativeWebRequest)}方法默认返回null，
		 * 最终的参数值解析是在{@link ExpressionValueMethodArgumentResolver#resolveEmbeddedValuesAndExpressions(String)}中通过解析{@link Value#value()}表达式得到
		 */
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		/**
		 * 解析带有{@link SessionAttribute}注解的方法参数
		 */
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		/**
		 * 解析带有@{@link RequestAttribute}注释的方法参数。
		 */
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		/**
		 * 基于类型的参数解析
		 */
		/**
		 * 通过请求强转，获取参数，创建参数解析出方法参数类型的参数值
		 * 可解析的参数见{@link ServletRequestMethodArgumentResolver#supportsParameter(MethodParameter)}
		 */
		resolvers.add(new ServletRequestMethodArgumentResolver());
		/**
		 * 解析{@link jakarta.servlet.ServletResponse}, {@link java.io.OutputStream}, {@link java.io.Writer}类型的方法参数
		 * 通过web上下文获取响应对象，或者响应对象的输出流
		 */
		resolvers.add(new ServletResponseMethodArgumentResolver());
		/**
		 * 解析带有{@link HttpEntity}或者{@link RequestEntity}注解的方法参数
		 */
		resolvers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		/**
		 * 处理{@link RedirectAttributes}类型的方法参数
		 */
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		/**
		 * 处理参数为{@link Model}类型的方法参数和返回值
		 */
		resolvers.add(new ModelMethodProcessor());
		/**
		 * 解析不带有任何注解的{@link Map}类型的方法参数和返回值
		 */
		resolvers.add(new MapMethodProcessor());
		/**
		 * 处理类型为{@link Errors}的方法参数
		 */
		resolvers.add(new ErrorsMethodArgumentResolver());
		/**
		 * 解析参数类型为{@link SessionStatus}类型的方法参数
		 */
		resolvers.add(new SessionStatusMethodArgumentResolver());
		/**
		 * 处理参数类型为{@link UriComponentsBuilder}或者{@link ServletUriComponentsBuilder}类型的方法参数
		 * 创建请求组件构建器，包含客户端地址，端口等信息
		 */
		resolvers.add(new UriComponentsBuilderMethodArgumentResolver());
		if (KotlinDetector.isKotlinPresent()) {
			resolvers.add(new ContinuationHandlerMethodArgumentResolver());
		}

		// Custom arguments
		/**
		 * 自定义参数
		 * 添加自定义参数解析器，添加在默认参数解析器之后
		 */
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all 全方位
		/**
		 * 解析参数类型为{@link java.security.Principal}类型的方法参数，与参数是否带有注解无关
		 * 返回已验证的用户身份信息，或者null
		 */
		resolvers.add(new PrincipalMethodArgumentResolver());
		/**
		 * 上面也有一个{@link RequestParamMethodArgumentResolver}类型的参数解析器注入，但是上面注入的解析器不适用默认参数解析策略，
		 * 此处注入的解析器使用默认参数解析策略
		 *
		 * *************************************************************************************************************
		 * {@link RequestParamMethodArgumentResolver}支持以下类型的参数解析：
		 *
		 * 1. 带有{@link RequestParam}注解，且指定参数名称的{@link Map}类型的参数
		 * 2. 带有{@link RequestParam}注解，且参数类型是{@link Map}以外的其他类型
		 * 3. 没有带{@link RequestParam}注解，且参数类型是表单项类型，或者参数类型为表单项类型的集合
		 * 4. 没有带{@link RequestParam}注解，且允许使用默认解析策略，且参数类型为简单类型或者简单类型的数组
		 *
		 * *************************************************************************************************************
		 * 使用默认参数解析策略
		 * 当方法参数没有{@link RequestParam}注解，但是参数类型是简单类型时，该参数当作带有{@link RequestParam}参数处理
		 * 简单类型的定义: 
		 * @see {@link org.springframework.beans.BeanUtils#isSimpleProperty(Class)}
		 */
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		/**
		 * 解析带有{@link ModelAttribute} 的参数和返回值
		 * 使用默认策略: 不带有{@link ModelAttribute}注解的非简单类型的参数或返回值，当作带有{@link ModelAttribute}注解处理
		 * 简单类型的定义:
		 * @see {@link org.springframework.beans.BeanUtils#isSimpleProperty(Class)}
		 */
		resolvers.add(new ServletModelAttributeMethodProcessor(true));

		return resolvers;
	}

	/**
	 * 初始化适用于带有{@link InitBinder}方法的参数解析器，适用于带有{@link InitBinder}方法的参数解析器是
	 * {@link this#argumentResolvers}的一个子集
	 *
	 * Return the list of argument resolvers to use for {@code @InitBinder}
	 * methods including built-in and custom resolvers.
	 *
	 * 返回用于{@code @InitBinder}方法的参数解析器列表，包括内置和自定义解析器。
	 */
	private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(20);

		// Annotation-based argument resolution 基于注释的参数解析
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution 基于类型的参数解析
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());

		// Custom arguments 自定义参数
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all 全方位
		resolvers.add(new PrincipalMethodArgumentResolver());
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

	/**
	 * 初始化默认返回值处理器和自定义返回值处理器
	 *
	 * Return the list of return value handlers to use including built-in and
	 * custom handlers provided via {@link #setReturnValueHandlers}.
	 *
	 * 返回要使用的返回值处理程序列表，包括通过{@link #setReturnValueHandlers}提供的内置和自定义处理程序。
	 */
	private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(20);

		// Single-purpose return value types
		/**
		 * 单一用途的返回值类型
		 */
		/**
		 * 处理类型为{@link ModelAndView}的返回值
		 */
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		/**
		 * 处理类型为{@link Model}的返回值，把返回的model中的属性值复制到默认模型中
		 */
		handlers.add(new ModelMethodProcessor());
		/**
		 * 处理类型为{@link View}的返回值
		 */
		handlers.add(new ViewMethodReturnValueHandler());
		/**
		 * 处理{@link ResponseEntity}泛型为{@link ResponseBodyEmitter}类型的返回值
		 */
		handlers.add(new ResponseBodyEmitterReturnValueHandler(getMessageConverters(),
				this.reactiveAdapterRegistry, this.taskExecutor, this.contentNegotiationManager));
		/**
		 * 处理类型为{@link org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody}或者
		 * {@code ResponseEntity<StreamingResponseBody>}的返回值
		 */
		handlers.add(new StreamingResponseBodyReturnValueHandler());
		/**
		 * 将指定的泛型返回值，写入响应体
		 */
		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));
		/**
		 * 处理{@link HttpHeaders}类型的返回值，将返回的响应头添加到响应头属性集合
		 */
		handlers.add(new HttpHeadersReturnValueHandler());
		/**
		 * 处理{@link Callable}类型的返回值，使用异步管理器异步指定任务
		 */
		handlers.add(new CallableMethodReturnValueHandler());
		/**
		 * 处理类型为{@link DeferredResult}, {@link org.springframework.util.concurrent.ListenableFuture}
		 * 或者{@link java.util.concurrent.CompletionStage}的返回值
		 * 用于的异步结果值的处理
		 */
		handlers.add(new DeferredResultMethodReturnValueHandler());
		/**
		 * 处理类型为{@link WebAsyncTask}的返回值。
		 */
		handlers.add(new AsyncTaskMethodReturnValueHandler(this.beanFactory));

		// Annotation-based return value types 基于注释的返回值类型
		/**
		 * 如果存在方法级{@code @ModelAttribute}，或者在默认解析模式下，对于任何不是简单类型的返回值类型都适用
		 * 简单类型的定义:
		 * @see  {@link org.springframework.beans.BeanUtils#isSimpleProperty(Class)}
		 */
		handlers.add(new ServletModelAttributeMethodProcessor(false));
		/**
		 * 处理处理程序所属类或者处理程序带有{@link ResponseBody}注解的返回值，将返回值通过合适的消息类型转换器写入相应体
		 */
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));

		// Multi-purpose return value types
		/**
		 * 多用途返回值类型
		 */
		/**
		 * 处理{@link Void}或者{@link CharSequence}类型的返回值，把{@link CharSequence}类型的返回值当作视图名称处理
		 */
		handlers.add(new ViewNameMethodReturnValueHandler());
		/**
		 * 处理返回值类型为{@link Map}的返回值,如果返回值类型是{@link Map}直接添加到模型中，否则(null除外)抛出异常
		 */
		handlers.add(new MapMethodProcessor());

		// Custom return value types 自定义返回值类型
		/**
		 * 把自定义的返回值处理器添加到默认处理器之后
		 */
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		if (!CollectionUtils.isEmpty(getModelAndViewResolvers())) {
			handlers.add(new ModelAndViewResolverMethodReturnValueHandler(getModelAndViewResolvers()));
		}
		else {
			handlers.add(new ServletModelAttributeMethodProcessor(true));
		}

		return handlers;
	}


	/**
	 * Always return {@code true} since any method argument and return value
	 * type will be processed in some way. A method argument not recognized
	 * by any HandlerMethodArgumentResolver is interpreted as a request parameter
	 * if it is a simple type, or as a model attribute otherwise. A return value
	 * not recognized by any HandlerMethodReturnValueHandler will be interpreted
	 * as a model attribute.
	 *
	 * 总是返回{@code true}，因为任何方法参数和返回值类型都会以某种方式被处理。
	 * 任何HandlerMethodArgumentResolver都无法识别的方法参数，如果是简单类型，则解释为请求参数，否则解释为模型属性。
	 * 任何HandlerMethodReturnValueHandler都不能识别的返回值将被解释为模型属性。
	 *
	 */
	@Override
	protected boolean supportsInternal(HandlerMethod handlerMethod) {
		return true;
	}

	@Override
	protected ModelAndView handleInternal(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		ModelAndView mav;
		/**
		 * 检查是否支持请求方法和是否需要会话
		 * 默认支持GET, HEAD和POST请求
		 */
		checkRequest(request);

		// Execute invokeHandlerMethod in synchronized block if required.
		/**
		 * 如果需要，在同步块中执行invokeHandlerMethod。
		 */
		if (this.synchronizeOnSession) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				/**
				 * 返回会话的互斥量
				 */
				Object mutex = WebUtils.getSessionMutex(session);
				synchronized (mutex) {
					mav = invokeHandlerMethod(request, response, handlerMethod);
				}
			}
			else {
				// No HttpSession available -> no mutex necessary 没有可用的HttpSession ->不需要互斥
				mav = invokeHandlerMethod(request, response, handlerMethod);
			}
		}
		else {
			// No synchronization on session demanded at all...
			/**
			 * 会话上根本不需要同步…
			 */
			mav = invokeHandlerMethod(request, response, handlerMethod);
		}

		/**
		 * 响应头不包含"Cache-Control"，设置浏览器缓存时间
		 */
		if (!response.containsHeader(HEADER_CACHE_CONTROL)) {
			if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
				applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
			}
			else {
				prepareResponse(response);
			}
		}

		return mav;
	}

	/**
	 * This implementation always returns -1. An {@code @RequestMapping} method can
	 * calculate the lastModified value, call {@link WebRequest#checkNotModified(long)},
	 * and return {@code null} if the result of that call is {@code true}.
	 */
	@Override
	@SuppressWarnings("deprecation")
	protected long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod) {
		return -1;
	}


	/**
	 * Return the {@link SessionAttributesHandler} instance for the given handler type
	 * (never {@code null}).
	 *
	 * 返回给定处理程序类型的{@link SessionAttributesHandler}实例(绝不是{@code null})。
	 */
	private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
		return this.sessionAttributesHandlerCache.computeIfAbsent(
				handlerMethod.getBeanType(),
				type -> new SessionAttributesHandler(type, this.sessionAttributeStore));
	}

	/**
	 * 创建请求处理程序执行器，并设置参数解析器，返回值解析器等参数值，并调用处理程序处理请求
	 *
	 * Invoke the {@link RequestMapping} handler method preparing a {@link ModelAndView}
	 * if view resolution is required.
	 *
	 * 如果需要视图解析，调用{@link RequestMapping}处理程序方法，准备一个{@link ModelAndView}。
	 *
	 * @since 4.2
	 * @see #createInvocableHandlerMethod(HandlerMethod)
	 */
	@Nullable
	protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		try {
			/**
			 * 查找处理程序所在类及其父接口中和全局定义的带有{@link InitBinder}注解的方法,并封装成{@link ServletRequestDataBinderFactory}
			 */
			WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);

			/**
			 * 查找可用的{@link ModelAttribute}标注的方法，并封装成{@link ModelFactory}
			 */
			ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);

			ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);

			/**
			 * 设置方法参数解析器，用于根据方法参数名或类型解析参数值
			 */
			if (this.argumentResolvers != null) {
				invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
			}
			/**
			 * 设置方法参数解析器，用于处理不同类型的方法返回值
			 */
			if (this.returnValueHandlers != null) {
				invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
			}
			/**
			 * 设置参数类型转换器，如果需要用于将解析到的参数值转换成目标类型
			 */
			invocableMethod.setDataBinderFactory(binderFactory);
			/**
			 * 设置方法参数名称解析器，如果需要默认通过asm解析源码中定义的参数名称
			 */
			invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);

			/**
			 * 创建模型视图容器，用于方便的设置模型参数，设置视图参数等操作
			 */
			ModelAndViewContainer mavContainer = new ModelAndViewContainer();
			/**
			 * 设置属性中已经存在的{@link org.springframework.web.servlet.FlashMap}
			 * {@link org.springframework.web.servlet.FlashMap}用来保存重定向请求的参数
			 */
			mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
			/**
			 * 初始化模型属性
			 * 1. 从session中获取处理程序所属类上{@link SessionAttributes#names()}指定的参数集合，并绑定到模型中
			 * 2. 调用{@link ModelAttribute}注解标注的方法
			 * 3. 将处理程序参数列表中带有{@link ModelAttribute}注解并符合{@link SessionAttributes}注解设置的参数值(从session中获取)添加到模型中
			 */
			modelFactory.initModel(webRequest, mavContainer, invocableMethod);
			/**
			 * 设置重定向时是否忽略默认模型
			 */
			mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect);

			AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
			/**
			 * 设置异步请求的超时时间，单位毫秒
			 */
			asyncWebRequest.setTimeout(this.asyncRequestTimeout);

			/**
			 * 设置异步请求的执行器，回调拦截器，异步结果处理器等属性
			 */
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			asyncManager.setTaskExecutor(this.taskExecutor);
			asyncManager.setAsyncWebRequest(asyncWebRequest);
			asyncManager.registerCallableInterceptors(this.callableInterceptors);
			asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);

			if (asyncManager.hasConcurrentResult()) {
				Object result = asyncManager.getConcurrentResult();
				mavContainer = (ModelAndViewContainer) asyncManager.getConcurrentResultContext()[0];
				asyncManager.clearConcurrentResult();
				LogFormatUtils.traceDebug(logger, traceOn -> {
					String formatted = LogFormatUtils.formatValue(result, !traceOn);
					return "Resume with async result [" + formatted + "]";
				});
				invocableMethod = invocableMethod.wrapConcurrentResult(result);
			}

			/**
			 * 执行请求处理程序，并处理返回值
			 */
			invocableMethod.invokeAndHandle(webRequest, mavContainer);
			/**
			 * 如果是异步请求直接返回
			 */
			if (asyncManager.isConcurrentHandlingStarted()) {
				return null;
			}

			/**
			 * 获取模型视图，如果请求是处理程序完全处理的，返回null
			 */
			return getModelAndView(mavContainer, modelFactory, webRequest);
		}
		finally {
			/**
			 * 标记请求处理完成
			 */
			webRequest.requestCompleted();
		}
	}

	/**
	 * Create a {@link ServletInvocableHandlerMethod} from the given {@link HandlerMethod} definition.
	 * 根据给定的{@link HandlerMethod}定义创建一个{@link ServletInvocableHandlerMethod}。
	 *
	 * @param handlerMethod the {@link HandlerMethod} definition
	 * @return the corresponding {@link ServletInvocableHandlerMethod} (or custom subclass thereof)
	 * @since 4.2
	 */
	protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handlerMethod) {
		return new ServletInvocableHandlerMethod(handlerMethod);
	}

	/**
	 * 查找可用的{@link ModelAttribute}标注的方法，并封装成{@link ModelFactory}
	 *
	 * @param handlerMethod
	 * @param binderFactory
	 * @return
	 */
	private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
		/**
		 * 获取临时会话属性处理器，扫描请求处理程序所属类上的{@link SessionAttributes}注解
		 */
		SessionAttributesHandler sessionAttrHandler = getSessionAttributesHandler(handlerMethod);
		Class<?> handlerType = handlerMethod.getBeanType();
		/**
		 * 首先查看缓存，如果缓存为空，查找当前类及其父类中(包含接口)带有{@link ModelAttribute}注解，但是没有被{@link RequestMapping}注解修饰的方法
		 */
		Set<Method> methods = this.modelAttributeCache.get(handlerType);
		if (methods == null) {
			methods = MethodIntrospector.selectMethods(handlerType, MODEL_ATTRIBUTE_METHODS);
			this.modelAttributeCache.put(handlerType, methods);
		}
		List<InvocableHandlerMethod> attrMethods = new ArrayList<>();
		// Global methods first
		/**
		 * 全局方法优先
		 *
		 * {@link modelAttributeAdviceCache}是在{@link #afterPropertiesSet()} 方法中调用{@link #initControllerAdviceCache()}方法初始化的
		 */
		this.modelAttributeAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
				}
			}
		});
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
		}
		return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
	}

	private InvocableHandlerMethod createModelAttributeMethod(WebDataBinderFactory factory, Object bean, Method method) {
		InvocableHandlerMethod attrMethod = new InvocableHandlerMethod(bean, method);
		if (this.argumentResolvers != null) {
			attrMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
		attrMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		attrMethod.setDataBinderFactory(factory);
		return attrMethod;
	}

	/**
	 * 查找处理程序所在类及其父接口中和全局定义的带有{@link InitBinder}注解的方法
	 *
	 * @param handlerMethod
	 * @return
	 * @throws Exception
	 */
	private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
		Class<?> handlerType = handlerMethod.getBeanType();
		Set<Method> methods = this.initBinderCache.get(handlerType);
		if (methods == null) {
			/**
			 * 查找当前类中及其父接口中所有带有{@link InitBinder}注解的方法和默认方法
			 */
			methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS);
			/**
			 * 缓存类中带有{@link InitBinder}注解的方法集合，避免多次查找
			 */
			this.initBinderCache.put(handlerType, methods);
		}
		List<InvocableHandlerMethod> initBinderMethods = new ArrayList<>();
		// Global methods first
		/**
		 * 全局方法优先
		 */
		this.initBinderAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					initBinderMethods.add(createInitBinderMethod(bean, method));
				}
			}
		});
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			initBinderMethods.add(createInitBinderMethod(bean, method));
		}
		return createDataBinderFactory(initBinderMethods);
	}

	private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
		InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);
		if (this.initBinderArgumentResolvers != null) {
			binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
		}
		binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
		binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		return binderMethod;
	}

	/**
	 * Template method to create a new InitBinderDataBinderFactory instance.
	 * 方法来创建一个新的InitBinderDataBinderFactory实例。
	 *
	 * <p>The default implementation creates a ServletRequestDataBinderFactory.
	 * This can be overridden for custom ServletRequestDataBinder subclasses.
	 * 默认实现创建一个ServletRequestDataBinderFactory。这可以被自定义ServletRequestDataBinder子类覆盖。
	 *
	 * @param binderMethods {@code @InitBinder} methods
	 * @return the InitBinderDataBinderFactory instance to use
	 * @throws Exception in case of invalid state or arguments
	 */
	protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods)
			throws Exception {

		return new ServletRequestDataBinderFactory(binderMethods, getWebBindingInitializer());
	}

	/**
	 * 获取模型视图，如果请求是处理程序完全处理的，返回null
	 *
	 * @param mavContainer
	 * @param modelFactory
	 * @param webRequest
	 * @return
	 * @throws Exception
	 */
	@Nullable
	private ModelAndView getModelAndView(ModelAndViewContainer mavContainer,
			ModelFactory modelFactory, NativeWebRequest webRequest) throws Exception {

		modelFactory.updateModel(webRequest, mavContainer);
		/**
		 * 如果请求已经在处理程序中完成处理，返回null
		 */
		if (mavContainer.isRequestHandled()) {
			return null;
		}
		ModelMap model = mavContainer.getModel();
		ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, mavContainer.getStatus());
		/**
		 * 如果不是引用视图，直接设置到新创建的模型中
		 */
		if (!mavContainer.isViewReference()) {
			mav.setView((View) mavContainer.getView());
		}
		/**
		 * 如果模型是重定向模型，将重定向参数设置到新创建的模型中
		 */
		if (model instanceof RedirectAttributes) {
			Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
			}
		}
		return mav;
	}

}
