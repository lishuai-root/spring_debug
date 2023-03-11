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

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.bind.support.WebRequestDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.support.StandardServletPartUtils;

/**
 * Resolve {@code @ModelAttribute} annotated method arguments and handle
 * return values from {@code @ModelAttribute} annotated methods.
 * 解析{@code @ModelAttribute}带注释的方法参数并处理{@code @ModelAttribute}带注释的方法的返回值。
 *
 * <p>Model attributes are obtained from the model or created with a default
 * constructor (and then added to the model). Once created the attribute is
 * populated via data binding to Servlet request parameters. Validation may be
 * applied if the argument is annotated with {@code @jakarta.validation.Valid}.
 * or Spring's own {@code @org.springframework.validation.annotation.Validated}.
 *
 * 模型属性从模型中获得，或者使用默认构造函数创建(然后添加到模型中)。一旦创建了属性，就会通过数据绑定到Servlet请求参数来填充属性。
 * 如果参数被注释为{@code @jakarta.validation.Valid}，则可以应用验证。
 * 或者Spring自己的{@code @org.springframework.validation.annotation.Validated}。
 *
 *
 * <p>When this handler is created with {@code annotationNotRequired=true}
 * any non-simple type argument and return value is regarded as a model
 * attribute with or without the presence of an {@code @ModelAttribute}.
 *
 * 当使用{@code annotationnotrerequired =true}创建此处理程序时，任何非简单类型参数和返回值都被视为模型属性，无论是否存在{@code @ModelAttribute}。
 *
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @author Vladislav Kisel
 * @since 3.1
 */
public class ModelAttributeMethodProcessor implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {

	protected final Log logger = LogFactory.getLog(getClass());

	private final boolean annotationNotRequired;


	/**
	 * Class constructor.
	 * @param annotationNotRequired if "true", non-simple method arguments and
	 * return values are considered model attributes with or without a
	 * 如果为"true"，非简单方法参数和返回值将被视为模型属性，无论是否使用
	 *
	 * {@code @ModelAttribute} annotation
	 */
	public ModelAttributeMethodProcessor(boolean annotationNotRequired) {
		this.annotationNotRequired = annotationNotRequired;
	}


	/**
	 * Returns {@code true} if the parameter is annotated with
	 * {@link ModelAttribute} or, if in default resolution mode, for any
	 * method parameter that is not a simple type.
	 *
	 * 如果参数带有{@link ModelAttribute}注释，则返回{@code true};如果处于默认解析模式，则返回任何非简单类型的方法参数。
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (parameter.hasParameterAnnotation(ModelAttribute.class) ||
				(this.annotationNotRequired && !BeanUtils.isSimpleProperty(parameter.getParameterType())));
	}

	/**
	 * 解析带有{@link ModelAttribute} 的参数
	 * 	1. 首先通过参数名在model中获取
	 * 	2. 如果在model中获取不到，创建参数类型的实例
	 *
	 * Resolve the argument from the model or if not found instantiate it with
	 * its default if it is available. The model attribute is then populated
	 * with request values via data binding and optionally validated
	 * if {@code @java.validation.Valid} is present on the argument.
	 * 解析来自模型的参数，如果没有找到，如果它可用，则用默认值实例化它。
	 * 然后，模型属性通过数据绑定填充请求值，并可选地验证如果{@code @java.validation.Valid}出现在参数上。
	 *
	 * @throws BindException if data binding and validation result in an error
	 * and the next method parameter is not of type {@link Errors}
	 * @throws Exception if WebDataBinder initialization fails
	 */
	@Override
	@Nullable
	public final Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		Assert.state(mavContainer != null, "ModelAttributeMethodProcessor requires ModelAndViewContainer");
		Assert.state(binderFactory != null, "ModelAttributeMethodProcessor requires WebDataBinderFactory");

		String name = ModelFactory.getNameForParameter(parameter);
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		if (ann != null) {
			mavContainer.setBinding(name, ann.binding());
		}

		Object attribute = null;
		BindingResult bindingResult = null;

		/**
		 * 首先在model中查找指定名称的属性值
		 */
		if (mavContainer.containsAttribute(name)) {
			attribute = mavContainer.getModel().get(name);
		}
		else {
			// Create attribute instance
			/**
			 * 创建属性实例
			 * 首先通过参数名称在请求的URI模板参数和请求参数中获取参数值，如果获取不到通过构造函数创建目标参数类型的参数值
			 */
			try {
				attribute = createAttribute(name, parameter, binderFactory, webRequest);
			}
			catch (BindException ex) {
				if (isBindExceptionRequired(parameter)) {
					// No BindingResult parameter -> fail with BindException 没有BindingResult参数-> fail with bindeexception
					throw ex;
				}
				// Otherwise, expose null/empty value and associated BindingResult
				/**
				 * 否则，暴露nullempty值和相关的BindingResult
				 */
				if (parameter.getParameterType() == Optional.class) {
					attribute = Optional.empty();
				}
				else {
					attribute = ex.getTarget();
				}
				bindingResult = ex.getBindingResult();
			}
		}

		if (bindingResult == null) {
			// Bean property binding and validation;
			// skipped in case of binding failure on construction.
			/**
			 * Bean属性绑定和验证;在构造绑定失败的情况下跳过。
			 */
			WebDataBinder binder = binderFactory.createBinder(webRequest, attribute, name);
			if (binder.getTarget() != null) {
				if (!mavContainer.isBindingDisabled(name)) {
					bindRequestParameters(binder, webRequest);
				}
				validateIfApplicable(binder, parameter);
				if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, parameter)) {
					throw new BindException(binder.getBindingResult());
				}
			}
			// Value type adaptation, also covering java.util.Optional
			/**
			 * 值类型适应，也包括java.util.Optional
			 */
			if (!parameter.getParameterType().isInstance(attribute)) {
				attribute = binder.convertIfNecessary(binder.getTarget(), parameter.getParameterType(), parameter);
			}
			bindingResult = binder.getBindingResult();
		}

		// Add resolved attribute and BindingResult at the end of the model
		/**
		 * 在模型的末尾添加解析属性和BindingResult
		 */
		Map<String, Object> bindingResultModel = bindingResult.getModel();
		mavContainer.removeAttributes(bindingResultModel);
		mavContainer.addAllAttributes(bindingResultModel);

		return attribute;
	}

	/**
	 * 通过构造函数创建参数值
	 *
	 * Extension point to create the model attribute if not found in the model,
	 * with subsequent parameter binding through bean properties (unless suppressed).
	 *
	 * 如果在模型中没有找到，则创建模型属性的扩展点，并通过bean属性进行后续参数绑定(除非被抑制)。
	 *
	 * <p>The default implementation typically uses the unique public no-arg constructor
	 * if available but also handles a "primary constructor" approach for data classes:
	 * It understands the JavaBeans {@code ConstructorProperties} annotation as well as
	 * runtime-retained parameter names in the bytecode, associating request parameters
	 * with constructor arguments by name. If no such constructor is found, the default
	 * constructor will be used (even if not public), assuming subsequent bean property
	 * bindings through setter methods.
	 *
	 * 默认实现通常使用唯一的公共无参数构造函数(如果可用)，但也为数据类处理“主构造函数”方法:
	 * 它理解JavaBeans {@code ConstructorProperties}注释以及字节码中运行时保留的参数名称，通过名称将请求参数与构造函数参数关联起来。
	 * 如果没有找到这样的构造函数，将使用默认构造函数(即使不是公共的)，假设通过setter方法进行后续bean属性绑定。
	 *
	 * @param attributeName the name of the attribute (never {@code null})
	 * @param parameter the method parameter declaration
	 * @param binderFactory for creating WebDataBinder instance
	 * @param webRequest the current request
	 * @return the created model attribute (never {@code null})
	 * @throws BindException in case of constructor argument binding failure
	 * @throws Exception in case of constructor invocation failure
	 * @see #constructAttribute(Constructor, String, MethodParameter, WebDataBinderFactory, NativeWebRequest)
	 * @see BeanUtils#findPrimaryConstructor(Class)
	 */
	protected Object createAttribute(String attributeName, MethodParameter parameter,
			WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {

		MethodParameter nestedParameter = parameter.nestedIfOptional();
		Class<?> clazz = nestedParameter.getNestedParameterType();

		Constructor<?> ctor = BeanUtils.getResolvableConstructor(clazz);
		/**
		 * 根据给定构造函数，解析构造函数参数，实例化
		 */
		Object attribute = constructAttribute(ctor, attributeName, parameter, binderFactory, webRequest);
		if (parameter != nestedParameter) {
			attribute = Optional.of(attribute);
		}
		return attribute;
	}

	/**
	 * Construct a new attribute instance with the given constructor.
	 * 使用给定的构造函数构造一个新的属性实例。
	 *
	 * 1. 如果构造器为无参构造，直接实例化
	 * 2. 对于每个参数：
	 * 		2.1. 通过参数名在请求参数中获取参数值
	 * 		2.2. 通过字段默认标记前缀 + 参数名在请求参数中获取参数值
	 * 	 	2.3. 判断字段是否在请求中被标记为空值，如果被标记为空值，根据参数类型创建空值(或默认值)
	 * 	 		 如果参数没有被标记为空值，通过参数名称尝试查找上传文件，上传表单项或者URI模板参数
	 * 		2.4. 根据参数解析结果是否为空和参数值是否被标记为可以为空，使用空值或者进行类型转换
	 * 3. 如果参数类型解析失败，抛出异常，如果解析成功，实例化
	 *
	 * <p>Called from
	 * {@link #createAttribute(String, MethodParameter, WebDataBinderFactory, NativeWebRequest)}
	 * after constructor resolution.
	 * <p>在构造函数解析后从{@link #createAttribute(String, MethodParameter, WebDataBinderFactory, NativeWebRequest)}调用。
	 *
	 * @param ctor the constructor to use
	 * @param attributeName the name of the attribute (never {@code null})
	 * @param binderFactory for creating WebDataBinder instance
	 * @param webRequest the current request
	 * @return the created model attribute (never {@code null})
	 * @throws BindException in case of constructor argument binding failure
	 * @throws Exception in case of constructor invocation failure
	 * @since 5.1
	 */
	@SuppressWarnings("serial")
	protected Object constructAttribute(Constructor<?> ctor, String attributeName, MethodParameter parameter,
			WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {

		if (ctor.getParameterCount() == 0) {
			// A single default constructor -> clearly a standard JavaBeans arrangement.
			/**
			 * 一个默认构造函数——>显然是一个标准的JavaBeans排列。
			 */
			return BeanUtils.instantiateClass(ctor);
		}

		// A single data class constructor -> resolve constructor arguments from request parameters.
		/**
		 * 单个数据类构造函数->从请求参数中解析构造函数参数。
		 */
		String[] paramNames = BeanUtils.getParameterNames(ctor);
		Class<?>[] paramTypes = ctor.getParameterTypes();
		Object[] args = new Object[paramTypes.length];
		WebDataBinder binder = binderFactory.createBinder(webRequest, null, attributeName);
		String fieldDefaultPrefix = binder.getFieldDefaultPrefix();
		String fieldMarkerPrefix = binder.getFieldMarkerPrefix();
		boolean bindingFailure = false;
		Set<String> failedParams = new HashSet<>(4);

		for (int i = 0; i < paramNames.length; i++) {
			String paramName = paramNames[i];
			Class<?> paramType = paramTypes[i];
			/**
			 * 首先在请求参数中获取指定名称的参数值
			 */
			Object value = webRequest.getParameterValues(paramName);

			// Since WebRequest#getParameter exposes a single-value parameter as an array
			// with a single element, we unwrap the single value in such cases, analogous
			// to WebExchangeDataBinder.addBindValue(Map<String, Object>, String, List<?>).
			/**
			 * 由于WebRequest#getParameter将单值参数公开为具有单个元素的数组，因此在这种情况下我们将单个值展开，
			 * 类似于WebExchangeDataBinder.addBindValue(Map<String, Object>, String, List<?>).
			 */
			if (ObjectUtils.isArray(value) && Array.getLength(value) == 1) {
				value = Array.get(value, 0);
			}

			/**
			 * 当原始的参数名称在请求参数中没有对应的参数值时，使用默认参数前缀 + 参数名获取参数值
			 * 如果可以获取到就用该值作为构造器的参数，
			 * 如果获取不到，则通过可能为空字段的前缀 + 参数名 判断当前参数是否在请求中被标记为空
			 * 如果被标记为空，根据参数类型创建一个空参数值(或者默认值)，如果参数没有被标记为空，
			 * 尝试通过参数名获取请求的上传文件，上传表单项和URI模板参数
			 */
			if (value == null) {
				/**
				 * 通过默认参数前缀获取请求参数值
				 */
				if (fieldDefaultPrefix != null) {
					value = webRequest.getParameter(fieldDefaultPrefix + paramName);
				}
				if (value == null) {
					/**
					 * 如果当前参数被标记为可能为null，则根据参数类型创建一个空参数值(或者默认值)
					 */
					if (fieldMarkerPrefix != null && webRequest.getParameter(fieldMarkerPrefix + paramName) != null) {
						value = binder.getEmptyValue(paramType);
					}
					else {
						/**
						 * 在请求的上传文件，表单项或者URI模板参数中获取指定名称的参数值
						 */
						value = resolveConstructorArgument(paramName, paramType, webRequest);
					}
				}
			}

			/**
			 * 如果参数值的解析结果为空，且参数被标记为可以为空时，使用空值
			 * 如果参数值不为空，获取参数值为空，但是方法参数不能为空,尝试使用类型转换器进行类型转换
			 */
			try {
				MethodParameter methodParam = new FieldAwareConstructorParameter(ctor, i, paramName);
				/**
				 * 如果参数值为空，并且方法参数可以为空，使用空值
				 */
				if (value == null && methodParam.isOptional()) {
					args[i] = (methodParam.getParameterType() == Optional.class ? Optional.empty() : null);
				}
				else {
					/**
					 * 如果参数值不为空，或者参数值为空，但是方法参数不能为空，尝试使用类型转换器转换参数值
					 */
					args[i] = binder.convertIfNecessary(value, paramType, methodParam);
				}
			}
			catch (TypeMismatchException ex) {
				ex.initPropertyName(paramName);
				/**
				 * 如果参数值类型转换异常，参数使用null，并记录参数值类型转换失败信息
				 */
				args[i] = null;
				failedParams.add(paramName);
				binder.getBindingResult().recordFieldValue(paramName, paramType, value);
				binder.getBindingErrorProcessor().processPropertyAccessException(ex, binder.getBindingResult());
				bindingFailure = true;
			}
		}

		/**
		 * 如果有参数解析失败，记录解析成功的参数，抛出异常
		 */
		if (bindingFailure) {
			BindingResult result = binder.getBindingResult();
			for (int i = 0; i < paramNames.length; i++) {
				String paramName = paramNames[i];
				if (!failedParams.contains(paramName)) {
					Object value = args[i];
					result.recordFieldValue(paramName, paramTypes[i], value);
					validateValueIfApplicable(binder, parameter, ctor.getDeclaringClass(), paramName, value);
				}
			}
			if (!parameter.isOptional()) {
				try {
					Object target = BeanUtils.instantiateClass(ctor, args);
					throw new BindException(result) {
						@Override
						public Object getTarget() {
							return target;
						}
					};
				}
				catch (BeanInstantiationException ex) {
					// swallow and proceed without target instance 吞咽并在没有目标实例的情况下继续
				}
			}
			throw new BindException(result);
		}
		/**
		 * 实例化
		 */
		return BeanUtils.instantiateClass(ctor, args);
	}

	/**
	 * Extension point to bind the request to the target object.
	 * @param binder the data binder instance to use for the binding
	 * @param request the current request
	 */
	protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
		((WebRequestDataBinder) binder).bind(request);
	}

	/**
	 * 解析构造函数参数
	 * 获取请求中指定名称的表单项并返回，或者返回null
	 *
	 * @param paramName
	 * @param paramType
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@Nullable
	public Object resolveConstructorArgument(String paramName, Class<?> paramType, NativeWebRequest request)
			throws Exception {

		/**
		 * 如果是上传请求，获取指定名称上传文件
		 */
		MultipartRequest multipartRequest = request.getNativeRequest(MultipartRequest.class);
		if (multipartRequest != null) {
			List<MultipartFile> files = multipartRequest.getFiles(paramName);
			if (!files.isEmpty()) {
				return (files.size() == 1 ? files.get(0) : files);
			}
		}
		/**
		 * 如果请求内容是{@link MediaType#MULTIPART_FORM_DATA_VALUE}，并且是{@link HttpMethod#POST}请求，获取指定名称的表单项
		 */
		else if (StringUtils.startsWithIgnoreCase(
				request.getHeader(HttpHeaders.CONTENT_TYPE), MediaType.MULTIPART_FORM_DATA_VALUE)) {
			HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
			if (servletRequest != null && HttpMethod.POST.matches(servletRequest.getMethod())) {
				List<Part> parts = StandardServletPartUtils.getParts(servletRequest, paramName);
				if (!parts.isEmpty()) {
					return (parts.size() == 1 ? parts.get(0) : parts);
				}
			}
		}
		return null;
	}

	/**
	 * Validate the model attribute if applicable.
	 * 如果适用，验证模型属性。
	 *
	 * <p>The default implementation checks for {@code @jakarta.validation.Valid},
	 * Spring's {@link org.springframework.validation.annotation.Validated},
	 * and custom annotations whose name starts with "Valid".
	 * 默认实现检查{@code @jakarta.validation.Valid}， Spring的{@link org.springframework.validation.annotation.Validated 以及名称以“Valid”开头的自定义注释。
	 *
	 * @param binder the DataBinder to be used
	 * @param parameter the method parameter declaration
	 * @see WebDataBinder#validate(Object...)
	 * @see SmartValidator#validate(Object, Errors, Object...)
	 */
	protected void validateIfApplicable(WebDataBinder binder, MethodParameter parameter) {
		for (Annotation ann : parameter.getParameterAnnotations()) {
			Object[] validationHints = ValidationAnnotationUtils.determineValidationHints(ann);
			if (validationHints != null) {
				binder.validate(validationHints);
				break;
			}
		}
	}

	/**
	 * Validate the specified candidate value if applicable.
	 * 验证指定的候选值(如果适用)。
	 *
	 * <p>The default implementation checks for {@code @jakarta.validation.Valid},
	 * Spring's {@link org.springframework.validation.annotation.Validated},
	 * and custom annotations whose name starts with "Valid".
	 * 默认实现检查{@code @jakarta.validation.Valid}， Spring的{@link org.springframework.validation.annotation.Validated}以及名称以“Valid”开头的自定义注释。
	 *
	 * @param binder the DataBinder to be used
	 * @param parameter the method parameter declaration
	 * @param targetType the target type
	 * @param fieldName the name of the field
	 * @param value the candidate value
	 * @since 5.1
	 * @see #validateIfApplicable(WebDataBinder, MethodParameter)
	 * @see SmartValidator#validateValue(Class, String, Object, Errors, Object...)
	 */
	protected void validateValueIfApplicable(WebDataBinder binder, MethodParameter parameter,
			Class<?> targetType, String fieldName, @Nullable Object value) {

		for (Annotation ann : parameter.getParameterAnnotations()) {
			Object[] validationHints = ValidationAnnotationUtils.determineValidationHints(ann);
			if (validationHints != null) {
				for (Validator validator : binder.getValidators()) {
					if (validator instanceof SmartValidator) {
						try {
							((SmartValidator) validator).validateValue(targetType, fieldName, value,
									binder.getBindingResult(), validationHints);
						}
						catch (IllegalArgumentException ex) {
							// No corresponding field on the target class...
						}
					}
				}
				break;
			}
		}
	}

	/**
	 * Whether to raise a fatal bind exception on validation errors.
	 * <p>The default implementation delegates to {@link #isBindExceptionRequired(MethodParameter)}.
	 * @param binder the data binder used to perform data binding
	 * @param parameter the method parameter declaration
	 * @return {@code true} if the next method parameter is not of type {@link Errors}
	 * @see #isBindExceptionRequired(MethodParameter)
	 */
	protected boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter parameter) {
		return isBindExceptionRequired(parameter);
	}

	/**
	 * Whether to raise a fatal bind exception on validation errors.
	 * @param parameter the method parameter declaration
	 * @return {@code true} if the next method parameter is not of type {@link Errors}
	 * @since 5.0
	 */
	protected boolean isBindExceptionRequired(MethodParameter parameter) {
		int i = parameter.getParameterIndex();
		Class<?>[] paramTypes = parameter.getExecutable().getParameterTypes();
		boolean hasBindingResult = (paramTypes.length > (i + 1) && Errors.class.isAssignableFrom(paramTypes[i + 1]));
		return !hasBindingResult;
	}

	/**
	 * Return {@code true} if there is a method-level {@code @ModelAttribute}
	 * or, in default resolution mode, for any return value type that is not
	 * a simple type.
	 *
	 * 如果存在方法级{@code @ModelAttribute}则返回{@code true}，或者在默认解析模式下，对于任何不是简单类型的返回值类型返回{@code true}。
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return (returnType.hasMethodAnnotation(ModelAttribute.class) ||
				(this.annotationNotRequired && !BeanUtils.isSimpleProperty(returnType.getParameterType())));
	}

	/**
	 * Add non-null return values to the {@link ModelAndViewContainer}.
	 * 向{@link ModelAndViewContainer}添加非空返回值。
	 */
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		if (returnValue != null) {
			/**
			 * 解析返回值的名称
			 * @see {@link BaseAdvice#endTime} {@link BaseAdvice#addRequestUser}
			 */
			String name = ModelFactory.getNameForReturnValue(returnValue, returnType);
			mavContainer.addAttribute(name, returnValue);
		}
	}


	/**
	 * {@link MethodParameter} subclass which detects field annotations as well.
	 * {@link MethodParameter}子类，用于检测字段注释。
	 *
	 * @since 5.1
	 */
	private static class FieldAwareConstructorParameter extends MethodParameter {

		private final String parameterName;

		@Nullable
		private volatile Annotation[] combinedAnnotations;

		public FieldAwareConstructorParameter(Constructor<?> constructor, int parameterIndex, String parameterName) {
			super(constructor, parameterIndex);
			this.parameterName = parameterName;
		}

		@Override
		public Annotation[] getParameterAnnotations() {
			Annotation[] anns = this.combinedAnnotations;
			if (anns == null) {
				anns = super.getParameterAnnotations();
				try {
					Field field = getDeclaringClass().getDeclaredField(this.parameterName);
					Annotation[] fieldAnns = field.getAnnotations();
					if (fieldAnns.length > 0) {
						List<Annotation> merged = new ArrayList<>(anns.length + fieldAnns.length);
						merged.addAll(Arrays.asList(anns));
						for (Annotation fieldAnn : fieldAnns) {
							boolean existingType = false;
							for (Annotation ann : anns) {
								if (ann.annotationType() == fieldAnn.annotationType()) {
									existingType = true;
									break;
								}
							}
							if (!existingType) {
								merged.add(fieldAnn);
							}
						}
						anns = merged.toArray(new Annotation[0]);
					}
				}
				catch (NoSuchFieldException | SecurityException ex) {
					// ignore
				}
				this.combinedAnnotations = anns;
			}
			return anns;
		}

		@Override
		public String getParameterName() {
			return this.parameterName;
		}
	}

}
