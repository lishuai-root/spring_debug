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

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingMatrixVariableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver;
import org.springframework.web.servlet.HandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves arguments annotated with {@link MatrixVariable @MatrixVariable}.
 * 解析带有{@link MatrixVariable @MatrixVariable}注释的参数。
 *
 * <p>If the method parameter is of type {@link Map} it will by resolved by
 * {@link MatrixVariableMapMethodArgumentResolver} instead unless the annotation
 * specifies a name in which case it is considered to be a single attribute of
 * type map (vs multiple attributes collected in a map).
 * 如果方法参数的类型是{@link Map}，它将被{@link MatrixVariableMapMethodArgumentResolver}解析，除非注释指定了一个名称，
 * 在这种情况下，它被认为是Map类型的单个属性(而不是一个Map中收集的多个属性)。
 *
 *
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @since 3.2
 */
public class MatrixVariableMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver {

	public MatrixVariableMethodArgumentResolver() {
		super(null);
	}


	/**
	 * 检查当前解析器是否可以解析指定方法参数，
	 * 检查条件如下，满足其一就返回，不再后续判断：
	 * 	1.如果当前方法参数没有使用{@link MatrixVariable}参数，返回false
	 * 	2.如果参数类型是{@link Map}并且使用了{@link MatrixVariable}注解，且注解的{@link MatrixVariable#name()}不为空，返回true，否则返回false
	 * 	3.参数带有{@link MatrixVariable}返回true
	 *
	 * @param parameter the method parameter to check
	 * @return
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		if (!parameter.hasParameterAnnotation(MatrixVariable.class)) {
			return false;
		}
		if (Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) {
			MatrixVariable matrixVariable = parameter.getParameterAnnotation(MatrixVariable.class);
			return (matrixVariable != null && StringUtils.hasText(matrixVariable.name()));
		}
		return true;
	}

	/**
	 * 使用{@link MatrixVariable}定义的参数名创建参数名称信息
	 *
	 * @param parameter the method parameter
	 * @return
	 */
	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		MatrixVariable ann = parameter.getParameterAnnotation(MatrixVariable.class);
		Assert.state(ann != null, "No MatrixVariable annotation");
		return new MatrixVariableNamedValueInfo(ann);
	}

	/**
	 * 根据{@link MatrixVariable}注解获取URI路径中的矩阵变量
	 *
	 * @param name the name of the value being resolved
	 * @param parameter the method parameter to resolve to an argument value
	 * (pre-nested in case of a {@link java.util.Optional} declaration)
	 * @param request the current request
	 * @return
	 * @throws Exception
	 */
	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
		/**
		 * 获取请求中所有举证参数对
		 * {@link HandlerMapping#MATRIX_VARIABLES_ATTRIBUTE}值在匹配处理程序时添加到请求参数中，解析过程待定。。。
		 */
		Map<String, MultiValueMap<String, String>> pathParameters = (Map<String, MultiValueMap<String, String>>)
				request.getAttribute(HandlerMapping.MATRIX_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		if (CollectionUtils.isEmpty(pathParameters)) {
			return null;
		}

		MatrixVariable ann = parameter.getParameterAnnotation(MatrixVariable.class);
		Assert.state(ann != null, "No MatrixVariable annotation");
		String pathVar = ann.pathVar();
		List<String> paramValues = null;

		/**
		 * 如果用户指定了{@link MatrixVariable#pathVar()} 属性，获取指定名称的URI路径变量中的变量值
		 */
		if (!pathVar.equals(ValueConstants.DEFAULT_NONE)) {
			if (pathParameters.containsKey(pathVar)) {
				paramValues = pathParameters.get(pathVar).get(name);
			}
		}
		else {
			boolean found = false;
			paramValues = new ArrayList<>();
			/**
			 * 如果没有指定{@link MatrixVariable#pathVar()} 路径名称，遍历所有URI路径变量，如果有重复的URI路径变量名称，抛出异常
			 * eg：
			 * 		/body/{name}/{address}
			 * 		/body/name=xxx;age=xxx/name=xxx;address=xxx
			 * 	其中{name}路径和{address}路径中都包含了name，当获取name参数时会抛出异常
			 * 	age和address参数都只出现了一次，获取时不会抛出异常
			 */
			for (MultiValueMap<String, String> params : pathParameters.values()) {
				if (params.containsKey(name)) {
					if (found) {
						String paramType = parameter.getNestedParameterType().getName();
						throw new ServletRequestBindingException(
								"Found more than one match for URI path parameter '" + name +
								"' for parameter type [" + paramType + "]. Use 'pathVar' attribute to disambiguate.");
					}
					paramValues.addAll(params.get(name));
					found = true;
				}
			}
		}

		if (CollectionUtils.isEmpty(paramValues)) {
			return null;
		}
		else if (paramValues.size() == 1) {
			return paramValues.get(0);
		}
		else {
			return paramValues;
		}
	}

	@Override
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletRequestBindingException {
		throw new MissingMatrixVariableException(name, parameter);
	}

	@Override
	protected void handleMissingValueAfterConversion(
			String name, MethodParameter parameter, NativeWebRequest request) throws Exception {

		throw new MissingMatrixVariableException(name, parameter, true);
	}

	private static final class MatrixVariableNamedValueInfo extends NamedValueInfo {

		private MatrixVariableNamedValueInfo(MatrixVariable annotation) {
			super(annotation.name(), annotation.required(), annotation.defaultValue());
		}
	}

}
