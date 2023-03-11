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

package org.springframework.web.method.annotation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ServletException;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Abstract base class for resolving method arguments from a named value.
 * 抽象基类，用于从命名值解析方法参数。
 *
 * Request parameters, request headers, and path variables are examples of named
 * values. Each may have a name, a required flag, and a default value.
 * 请求参数、请求头和路径变量都是命名值的例子。每个都可能有一个名称、一个必需的标志和一个默认值。
 *
 * <p>Subclasses define how to do the following:
 * 子类定义如何执行以下操作:
 * <ul>
 * <li>Obtain named value information for a method parameter
 * 获取方法参数的命名值信息
 * <li>Resolve names into argument values
 * 将名称解析为参数值
 * <li>Handle missing argument values when argument values are required
 * 当需要参数值时，处理缺失的参数值
 * <li>Optionally handle a resolved value
 * 可选地处理已解析的值
 * </ul>
 *
 * <p>A default value string can contain ${...} placeholders and Spring Expression
 * Language #{...} expressions. For this to work a
 * {@link ConfigurableBeanFactory} must be supplied to the class constructor.
 * <p>默认值字符串可以包含${…}占位符和Spring表达式语言#{…}表达式。为此，必须向类构造函数提供{@link ConfigurableBeanFactory}。
 *
 *
 * <p>A {@link WebDataBinder} is created to apply type conversion to the resolved
 * argument value if it doesn't match the method parameter type.
 * <p>创建一个{@link WebDataBinder}用于在解析参数值不匹配方法参数类型时应用类型转换。
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractNamedValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Nullable
	private final ConfigurableBeanFactory configurableBeanFactory;

	@Nullable
	private final BeanExpressionContext expressionContext;

	/**
	 * 缓存参数和参数名称映射
	 */
	private final Map<MethodParameter, NamedValueInfo> namedValueInfoCache = new ConcurrentHashMap<>(256);


	public AbstractNamedValueMethodArgumentResolver() {
		this.configurableBeanFactory = null;
		this.expressionContext = null;
	}

	/**
	 * Create a new {@link AbstractNamedValueMethodArgumentResolver} instance.
	 * 创建一个新的{@link AbstractNamedValueMethodArgumentResolver}实例。
	 *
	 * @param beanFactory a bean factory to use for resolving ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 * 用于解析${…}占位符和#{…}在默认值中拼写表达式，如果默认值不包含表达式，则为{@code null}
	 */
	public AbstractNamedValueMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
		this.configurableBeanFactory = beanFactory;
		this.expressionContext =
				(beanFactory != null ? new BeanExpressionContext(beanFactory, new RequestScope()) : null);
	}


	/**
	 * 解析方法参数
	 * 	1. 解析方法参数名，如果{@link RequestParam#name()}指定了参数名，就是用指定的参数名，如果没有指定参数名，就用asm解析源代码获取源代码中定义的参数名
	 * 	2. 解析参数名中的表达式
	 * 		和解析资源属性名称一样，使用环境变量解析
	 * 	3. 通过参数名在请求中解析参数值
	 * 		调用{@link #resolveName(String, MethodParameter, NativeWebRequest)} 将给定的参数类型和值名称解析为参数值
	 * 	4. 如果解析的参数值为空，则查看参数是否有指定默认值或者是否必要，如果有默认值或者不是必要的则给参数值赋默认值或者null，否则抛出异常
	 * 		参数默认值：
	 * 			参数默认值是通过{@link RequestParam#defaultValue()}指定的，默认值是一个无法从用户请求取到的参数名称(可以理解为null),
	 * 			如果参数没有{@link RequestParam}注解，则没有默认值
	 * 		参数是否必要：
	 * 			4.1. 如果参数上带有{@link RequestParam}注解，使用{@link RequestParam#required()}标识，默认为true
	 * 			4.2. 如果参数上没有{@link RequestParam}注解，则不是必要的
	 * 	5. 使用{@link org.springframework.web.bind.annotation.InitBinder}注解标注的方法初始化属性编辑器，并对参数值进行类型转换
	 * 		当转换后的参数值为null时，有可能抛出异常(没有指定默认值，且参数为必要的)
	 * 	6. 调用参数值解析后的回调，默认空实现{@link #handleResolvedValue(Object, String, MethodParameter, ModelAndViewContainer, NativeWebRequest)}
	 *
	 *
	 * @param parameter the method parameter to resolve. This parameter must
	 * have previously been passed to {@link #supportsParameter} which must
	 * have returned {@code true}.
	 * 要解析的方法参数。此参数之前必须传递给{@link #supportsParameter}，后者必须返回{@code true}。
	 *
	 * @param mavContainer the ModelAndViewContainer for the current request
	 * @param webRequest the current request
	 * @param binderFactory a factory for creating {@link WebDataBinder} instances
	 * @return
	 * @throws Exception
	 */
	@Override
	@Nullable
	public final Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		/**
		 * 解析方法参数名称
		 */
		NamedValueInfo namedValueInfo = getNamedValueInfo(parameter);
		MethodParameter nestedParameter = parameter.nestedIfOptional();

		/**
		 * 解析参数名称中的占位符，返回替换占位符后的参数名称
		 */
		Object resolvedName = resolveEmbeddedValuesAndExpressions(namedValueInfo.name);
		if (resolvedName == null) {
			throw new IllegalArgumentException(
					"Specified name must not resolve to null: [" + namedValueInfo.name + "]");
		}

		/**
		 * 解析方法参数
		 *
		 * 1. 首先尝试在表单项中解析指定名称的表单项
		 * 2. 如果是上传请求，获取指定名称的上传文件
		 * 3. 获取指定名称的请求参数
		 *
		 * *************************************************************************************************************
		 * 上述三点是解析{@link RequestParam}注解的流程，
		 * 不同的解析器会有不同的实现
		 */
		Object arg = resolveName(resolvedName.toString(), nestedParameter, webRequest);
		if (arg == null) {
			/**
			 * 如果用户在{@link RequestParam#defaultValue()} 中指定了默认值，解析默认值
			 */
			if (namedValueInfo.defaultValue != null) {
				arg = resolveEmbeddedValuesAndExpressions(namedValueInfo.defaultValue);
			}
			/**
			 * 如果用户没有指定默认值，且参数是必要参数，抛出异常
			 */
			else if (namedValueInfo.required && !nestedParameter.isOptional()) {
				/**
				 * 抛出异常
				 */
				handleMissingValue(namedValueInfo.name, nestedParameter, webRequest);
			}
			/**
			 * 如果参数值不为null，直接返回参数值
			 *
			 * 如果参数值为null，且参数类型为{@link Boolean}类型，默认取false，
			 * 如果参数值为null，且参数类型不为{@link Boolean}类型，抛出异常
			 */
			arg = handleNullValue(namedValueInfo.name, arg, nestedParameter.getNestedParameterType());
		}
		/**
		 * 解析默认值
		 */
		else if ("".equals(arg) && namedValueInfo.defaultValue != null) {
			arg = resolveEmbeddedValuesAndExpressions(namedValueInfo.defaultValue);
		}

		/**
		 * 走到这里说明解析到参数值了
		 */
		if (binderFactory != null) {
			/**
			 * 为当前属性创建{@link WebDataBinder}
			 */
			WebDataBinder binder = binderFactory.createBinder(webRequest, null, namedValueInfo.name);
			try {
				/**
				 * 使用自定义属性编辑器(带有{@link org.springframework.web.bind.annotation.InitBinder}注解的方法)，对参数进行类型转换
				 */
				arg = binder.convertIfNecessary(arg, parameter.getParameterType(), parameter);
			}
			catch (ConversionNotSupportedException ex) {
				throw new MethodArgumentConversionNotSupportedException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
			catch (TypeMismatchException ex) {
				throw new MethodArgumentTypeMismatchException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
			// Check for null value after conversion of incoming argument value
			/**
			 * 检查传入参数值转换后的空值
			 * 当转换后的值为null时，抛出异常
			 */
			if (arg == null && namedValueInfo.defaultValue == null &&
					namedValueInfo.required && !nestedParameter.isOptional()) {
				handleMissingValueAfterConversion(namedValueInfo.name, nestedParameter, webRequest);
			}
		}

		/**
		 * 参数值解析后的回调，默认空实现
		 */
		handleResolvedValue(arg, namedValueInfo.name, parameter, mavContainer, webRequest);

		return arg;
	}

	/**
	 * Obtain the named value for the given method parameter.
	 * 获取给定方法参数的命名值。
	 */
	private NamedValueInfo getNamedValueInfo(MethodParameter parameter) {
		/**
		 * 首先从缓存中获取参数名称，如果没有，使用asm解析
		 */
		NamedValueInfo namedValueInfo = this.namedValueInfoCache.get(parameter);
		if (namedValueInfo == null) {
			/**
			 * 使用{@link org.springframework.web.bind.annotation.RequestParam}注解定义的信息创建namedValueInfo
			 * 如果没有定义参数名称默认为""
			 */
			namedValueInfo = createNamedValueInfo(parameter);
			/**
			 * 如果参数名称为""，使用asm解析类，获取源代码中定义的参数名称，并创建新的namedValueInfo
			 */
			namedValueInfo = updateNamedValueInfo(parameter, namedValueInfo);
			/**
			 * 缓存
			 */
			this.namedValueInfoCache.put(parameter, namedValueInfo);
		}
		return namedValueInfo;
	}

	/**
	 * Create the {@link NamedValueInfo} object for the given method parameter. Implementations typically
	 * retrieve the method annotation by means of {@link MethodParameter#getParameterAnnotation(Class)}.
	 *
	 * 为给定的方法参数创建{@link NamedValueInfo}对象。实现通常通过{@link MethodParameter#getParameterAnnotation(Class)}获取方法注释。
	 *
	 * @param parameter the method parameter
	 * @return the named value information
	 */
	protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);

	/**
	 * 如果{@param info}中的参数名称为空，使用asm解析源代码获取源代码中定义的参数名
	 *
	 * Create a new NamedValueInfo based on the given NamedValueInfo with sanitized values.
	 * 基于给定的NamedValueInfo和经过消毒的值创建一个新的NamedValueInfo。
	 */
	private NamedValueInfo updateNamedValueInfo(MethodParameter parameter, NamedValueInfo info) {
		String name = info.name;
		if (info.name.isEmpty()) {
			/**
			 * 通过asm解析获取参数名
			 */
			name = parameter.getParameterName();
			if (name == null) {
				throw new IllegalArgumentException(
						"Name for argument of type [" + parameter.getNestedParameterType().getName() +
						"] not specified, and parameter name information not found in class file either.");
			}
		}
		String defaultValue = (ValueConstants.DEFAULT_NONE.equals(info.defaultValue) ? null : info.defaultValue);
		return new NamedValueInfo(name, info.required, defaultValue);
	}

	/**
	 * 解析字符串中的占位符
	 * eg:
	 * 		系统环境中  "USERNAME" = "是李帅啊"
	 * 		${USERNAME}	的解析结果为 "是李帅啊"
	 *
	 * Resolve the given annotation-specified value,
	 * potentially containing placeholders and expressions.
	 *
	 * 解析给定的注释指定值，其中可能包含占位符和表达式。
	 */
	@Nullable
	private Object resolveEmbeddedValuesAndExpressions(String value) {
		if (this.configurableBeanFactory == null || this.expressionContext == null) {
			return value;
		}
		/**
		 * 解析占位符
		 */
		String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(value);
		BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
		if (exprResolver == null) {
			return value;
		}
		return exprResolver.evaluate(placeholdersResolved, this.expressionContext);
	}

	/**
	 * Resolve the given parameter type and value name into an argument value.
	 * 将给定的参数类型和值名称解析为参数值。
	 *
	 * @param name the name of the value being resolved
	 * @param parameter the method parameter to resolve to an argument value
	 * (pre-nested in case of a {@link java.util.Optional} declaration)
	 * @param request the current request
	 * @return the resolved argument (may be {@code null})
	 * @throws Exception in case of errors
	 */
	@Nullable
	protected abstract Object resolveName(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception;

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * 当需要一个命名值时调用，但是{@link resolveName(String, MethodParameter, NativeWebRequest)}返回{@code null}，并且没有默认值。
	 * 在这种情况下，子类通常会抛出异常。
	 *
	 * @param name the name for the value
	 * @param parameter the method parameter
	 * @param request the current request
	 * @since 4.3
	 */
	protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {

		handleMissingValue(name, parameter);
	}

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * 当需要一个命名值时调用，但是{@link resolveName(String, MethodParameter, NativeWebRequest)}返回{@code null}，并且没有默认值。
	 * 在这种情况下，子类通常会抛出异常。
	 *
	 * @param name the name for the value
	 * @param parameter the method parameter
	 */
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletException {
		throw new ServletRequestBindingException("Missing argument '" + name +
				"' for method parameter of type " + parameter.getNestedParameterType().getSimpleName());
	}

	/**
	 * Invoked when a named value is present but becomes {@code null} after conversion.
	 * 当一个命名值存在，但转换后变成{@code null}时调用。
	 *
	 * @param name the name for the value
	 * @param parameter the method parameter
	 * @param request the current request
	 * @since 5.3.6
	 */
	protected void handleMissingValueAfterConversion(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {

		handleMissingValue(name, parameter, request);
	}

	/**
	 * A {@code null} results in a {@code false} value for {@code boolean}s or an exception for other primitives.
	 * {@code null}将导致{@code boolean}s的{@code false}值或其他原语的异常。
	 */
	@Nullable
	private Object handleNullValue(String name, @Nullable Object value, Class<?> paramType) {
		if (value == null) {
			/**
			 * 如果参数值为空，且参数类型为{@link Boolean}类型，默认取false， 否则抛出异常
			 */
			if (Boolean.TYPE.equals(paramType)) {
				return Boolean.FALSE;
			}
			else if (paramType.isPrimitive()) {
				throw new IllegalStateException("Optional " + paramType.getSimpleName() + " parameter '" + name +
						"' is present but cannot be translated into a null value due to being declared as a " +
						"primitive type. Consider declaring it as object wrapper for the corresponding primitive type.");
			}
		}
		/**
		 * 如果参数不为空，返回参数
		 */
		return value;
	}

	/**
	 * Invoked after a value is resolved.
	 * 在解析值之后调用。
	 *
	 * @param arg the resolved argument value
	 * @param name the argument name
	 * @param parameter the argument parameter type
	 * @param mavContainer the {@link ModelAndViewContainer} (may be {@code null})
	 * @param webRequest the current request
	 */
	protected void handleResolvedValue(@Nullable Object arg, String name, MethodParameter parameter,
			@Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest) {
	}


	/**
	 * Represents the information about a named value, including name, whether it's required and a default value.
	 * 表示关于已命名值的信息，包括名称、是否需要和默认值。
	 */
	protected static class NamedValueInfo {

		private final String name;

		private final boolean required;

		@Nullable
		private final String defaultValue;

		public NamedValueInfo(String name, boolean required, @Nullable String defaultValue) {
			this.name = name;
			this.required = required;
			this.defaultValue = defaultValue;
		}
	}

}
