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

package org.springframework.web.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.core.Conventions;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Assist with initialization of the {@link Model} before controller method
 * invocation and with updates to it after the invocation.
 * 在控制器方法调用之前协助初始化{@link Model}，并在调用之后对其进行更新。
 *
 * <p>On initialization the model is populated with attributes temporarily stored
 * in the session and through the invocation of {@code @ModelAttribute} methods.
 * 在初始化时，通过调用{@code @ModelAttribute}方法，用临时存储在会话中的属性填充模型。
 *
 * <p>On update model attributes are synchronized with the session and also
 * {@link BindingResult} attributes are added if missing.
 * <p>在更新模型属性与会话同步，如果缺少{@link BindingResult}属性也会添加。
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ModelFactory {

	private static final Log logger = LogFactory.getLog(ModelFactory.class);


	private final List<ModelMethod> modelMethods = new ArrayList<>();

	private final WebDataBinderFactory dataBinderFactory;

	private final SessionAttributesHandler sessionAttributesHandler;


	/**
	 * Create a new instance with the given {@code @ModelAttribute} methods.
	 * @param handlerMethods the {@code @ModelAttribute} methods to invoke
	 * @param binderFactory for preparation of {@link BindingResult} attributes
	 * @param attributeHandler for access to session attributes
	 */
	public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods,
			WebDataBinderFactory binderFactory, SessionAttributesHandler attributeHandler) {

		if (handlerMethods != null) {
			for (InvocableHandlerMethod handlerMethod : handlerMethods) {
				this.modelMethods.add(new ModelMethod(handlerMethod));
			}
		}
		this.dataBinderFactory = binderFactory;
		this.sessionAttributesHandler = attributeHandler;
	}


	/**
	 * 初始化模型属性
	 * 1. 从session中获取处理程序所属类上{@link SessionAttributes#names()}指定的参数集合，并绑定到模型中
	 * 2. 调用{@link ModelAttribute}注解标注的方法
	 * 3. 将处理程序参数列表中带有{@link ModelAttribute}注解并符合{@link SessionAttributes}注解设置的参数值(从session中获取)添加到模型中
	 *
	 * Populate the model in the following order:
	 * 按以下顺序填充模型:
	 * <ol>
	 * <li>Retrieve "known" session attributes listed as {@code @SessionAttributes}.
	 * 检索列出为{@code @SessionAttributes}的“已知”会话属性。
	 *
	 * <li>Invoke {@code @ModelAttribute} methods
	 * 调用{@code @ModelAttribute}方法
	 *
	 * <li>Find {@code @ModelAttribute} method arguments also listed as
	 * {@code @SessionAttributes} and ensure they're present in the model raising
	 * an exception if necessary.
	 * 找到{@code @ModelAttribute}方法参数，也列在{@code @SessionAttributes}中，并确保它们在必要时引发异常的模型中出现。
	 *
	 * </ol>
	 * @param request the current request
	 * @param container a container with the model to be initialized
	 * @param handlerMethod the method for which the model is initialized
	 * @throws Exception may arise from {@code @ModelAttribute} methods
	 */
	public void initModel(NativeWebRequest request, ModelAndViewContainer container, HandlerMethod handlerMethod)
			throws Exception {

		/**
		 * 获取请求中{@link org.springframework.web.bind.annotation.SessionAttributes}注解指定的所有参数名称值
		 * 此处如果session中没有指定参数值，不会抛出异常
		 */
		Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes(request);
		/**
		 * 将{@link org.springframework.web.bind.annotation.SessionAttributes}注解指定的请求参数合并到模型中，现有名称的参数不替换
		 */
		container.mergeAttributes(sessionAttributes);
		/**
		 * 执行{@link ModelAttribute}注解标志的方法，并将返回值添加到模型中
		 */
		invokeModelAttributeMethods(request, container);

		/**
		 * 获取处理程序参数名称带有{@link ModelAttribute}注解并存在于处理程序所属类上的{@link org.springframework.web.bind.annotation.SessionAttributes}
		 * 注解指定的参数名称或类型集合中的方法参数
		 * 此处如果符合条件的方法参数在session中不存在，会抛出异常
		 */
		for (String name : findSessionAttributeArguments(handlerMethod)) {
			/**
			 * 从session中获取模型中不存在的属性，并绑定到模型
			 */
			if (!container.containsAttribute(name)) {
				Object value = this.sessionAttributesHandler.retrieveAttribute(request, name);
				if (value == null) {
					throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
				}
				container.addAttribute(name, value);
			}
		}
	}

	/**
	 * 执行{@link ModelAttribute}注解标志的方法，并将返回值添加到模型中
	 *
	 * Invoke model attribute methods to populate the model.
	 * 调用模型属性方法来填充模型。
	 *
	 * Attributes are added only if not already present in the model.
	 * 只有在模型中尚未出现属性时才会添加属性。
	 */
	private void invokeModelAttributeMethods(NativeWebRequest request, ModelAndViewContainer container)
			throws Exception {

		while (!this.modelMethods.isEmpty()) {
			InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();
			ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
			Assert.state(ann != null, "No ModelAttribute annotation");
			/**
			 * 如果{@link ModelAttribute#name()}指定的参数名称已经存在，跳过
			 */
			if (container.containsAttribute(ann.name())) {
				if (!ann.binding()) {
					container.setBindingDisabled(ann.name());
				}
				continue;
			}

			/**
			 * 指定{@link ModelAttribute}注解标注的方法
			 */
			Object returnValue = modelMethod.invokeForRequest(request, container);
			/**
			 * 如果没有返回值，跳过
			 */
			if (modelMethod.isVoid()) {
				if (StringUtils.hasText(ann.value())) {
					if (logger.isDebugEnabled()) {
						logger.debug("Name in @ModelAttribute is ignored because method returns void: " +
								modelMethod.getShortLogMessage());
					}
				}
				continue;
			}

			/**
			 * 获取方法的返回值名称
			 * 1. 获取方法{@link ModelAttribute}注释值
			 * 2. 方法声明的返回类型(如果它比{@code Object}更具体)
			 * 3. 方法实际返回值类型
			 */
			String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());
			/**
			 * 如果返回值被标注为不需要绑定到模型，将返回值添加到禁止绑定集合
			 */
			if (!ann.binding()) {
				container.setBindingDisabled(returnValueName);
			}
			/**
			 * 将参数添加到模型参数集合
			 */
			if (!container.containsAttribute(returnValueName)) {
				container.addAttribute(returnValueName, returnValue);
			}
		}
	}

	private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
		for (ModelMethod modelMethod : this.modelMethods) {
			if (modelMethod.checkDependencies(container)) {
				this.modelMethods.remove(modelMethod);
				return modelMethod;
			}
		}
		ModelMethod modelMethod = this.modelMethods.get(0);
		this.modelMethods.remove(modelMethod);
		return modelMethod;
	}

	/**
	 * Find {@code @ModelAttribute} arguments also listed as {@code @SessionAttributes}.
	 *
	 * 发现{@code @ModelAttribute}参数也列在{@code @SessionAttributes}中。
	 */
	private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
		List<String> result = new ArrayList<>();
		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
				String name = getNameForParameter(parameter);
				Class<?> paramType = parameter.getParameterType();
				/**
				 * 检查带有{@link ModelAttribute}注解的参数名称是否处理程序所属类上的{@link org.springframework.web.bind.annotation.SessionAttributes}注解指定的参数名
				 */
				if (this.sessionAttributesHandler.isHandlerSessionAttribute(name, paramType)) {
					result.add(name);
				}
			}
		}
		return result;
	}

	/**
	 * 必要时清理暴漏在session中的值，或者将需要暴漏的值缓存到session中
	 *
	 * Promote model attributes listed as {@code @SessionAttributes} to the session.
	 * 将列出为{@code @SessionAttributes}的模型属性提升到会话。
	 *
	 * Add {@link BindingResult} attributes where necessary.
	 * 在必要的地方添加{@link BindingResult}属性。
	 *
	 * @param request the current request
	 * @param container contains the model to update
	 * @throws Exception if creating BindingResult attributes fails
	 */
	public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
		ModelMap defaultModel = container.getDefaultModel();
		/**
		 * {@link SessionStatus#setComplete()} 方法用于设置会话已经完成，如果用户在处理程序中调用此方法并设置为true，则表示当前会话已经完成
		 * 当会话完成时后续的处理会清理会话缓存
		 */
		if (container.getSessionStatus().isComplete()){
			/**
			 * 当前处理程序的会话完成时，清楚session中指定属性
			 */
			this.sessionAttributesHandler.cleanupAttributes(request);
		}
		else {
			/**
			 * 如果未完成，将模型中的指定名称或类型的参数保存到session中
			 */
			this.sessionAttributesHandler.storeAttributes(request, defaultModel);
		}
		if (!container.isRequestHandled() && container.getModel() == defaultModel) {
			updateBindingResult(request, defaultModel);
		}
	}

	/**
	 * 如果处理程序绑定参数时注释了@Valid和@Validated注解，那么会将校验的结果设置到BindingResult类型的参数中，
	 * 如果没有添加校验的注释，为了渲染方便，ModelFactory 会给Model设置一个跟参数相对应的BindingResult
	 *
	 * Add {@link BindingResult} attributes to the model for attributes that require it.
	 * 将{@link BindingResult}属性添加到需要它的属性模型中。
	 */
	private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
		List<String> keyNames = new ArrayList<>(model.keySet());
		for (String name : keyNames) {
			Object value = model.get(name);
			/**
			 * 遍历每一个Model中保存的参数，判断是否需要添加BindingResult，如果需要则使用WebDataBinder获取BindingResult并添加到Model，
			 * 在添加前检查Model中是否已经存在，如果已经存在就不添加了
			 */
			if (value != null && isBindingCandidate(name, value)) {
				String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + name;
				if (!model.containsAttribute(bindingResultKey)) {
					/**
					 * 通过dataBinderFactory创建webDataBinder
					 */
					WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
					model.put(bindingResultKey, dataBinder.getBindingResult());
				}
			}
		}
	}

	/**
	 * Whether the given attribute requires a {@link BindingResult} in the model.
	 * 给定属性在模型中是否需要{@link BindingResult}。
	 */
	private boolean isBindingCandidate(String attributeName, Object value) {
		if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
			return false;
		}

		if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
			return true;
		}

		return (!value.getClass().isArray() && !(value instanceof Collection) &&
				!(value instanceof Map) && !BeanUtils.isSimpleValueType(value.getClass()));
	}


	/**
	 * Derive the model attribute name for the given method parameter based on
	 * a {@code @ModelAttribute} parameter annotation (if present) or falling
	 * back on parameter type based conventions.
	 * 根据{@code @ModelAttribute}参数注释(如果存在)或退回到基于参数类型的约定，为给定的方法参数派生模型属性名。
	 *
	 * @param parameter a descriptor for the method parameter
	 * @return the derived name
	 * @see Conventions#getVariableNameForParameter(MethodParameter)
	 */
	public static String getNameForParameter(MethodParameter parameter) {
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		String name = (ann != null ? ann.value() : null);
		return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
	}

	/**
	 * Derive the model attribute name for the given return value. Results will be
	 * based on:
	 * 为给定的返回值派生模型属性名。结果将基于:
	 *
	 * <ol>
	 * <li>the method {@code ModelAttribute} annotation value
	 * 方法{@code ModelAttribute}注释值
	 * <li>the declared return type if it is more specific than {@code Object}
	 * 声明的返回类型(如果它比{@code Object}更具体)
	 * <li>the actual return value type
	 * 实际返回值类型
	 *
	 * </ol>
	 * @param returnValue the value returned from a method invocation
	 * @param returnType a descriptor for the return type of the method
	 * @return the derived name (never {@code null} or empty String)
	 */
	public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
		ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
		if (ann != null && StringUtils.hasText(ann.value())) {
			return ann.value();
		}
		else {
			Method method = returnType.getMethod();
			Assert.state(method != null, "No handler method");
			Class<?> containingClass = returnType.getContainingClass();
			Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
			return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
		}
	}


	private static class ModelMethod {

		private final InvocableHandlerMethod handlerMethod;

		private final Set<String> dependencies = new HashSet<>();

		public ModelMethod(InvocableHandlerMethod handlerMethod) {
			this.handlerMethod = handlerMethod;
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
					this.dependencies.add(getNameForParameter(parameter));
				}
			}
		}

		public InvocableHandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public boolean checkDependencies(ModelAndViewContainer mavContainer) {
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			return this.handlerMethod.getMethod().toGenericString();
		}
	}

}
