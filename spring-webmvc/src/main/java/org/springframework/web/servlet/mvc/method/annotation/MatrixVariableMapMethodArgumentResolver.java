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

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 解析参数类型为{@link Map}并且{@link MatrixVariable#name()}属性未指定的参数
 * 返回指定名称或全部URI路径参数矩阵
 *
 * Resolves arguments of type {@link Map} annotated with {@link MatrixVariable @MatrixVariable}
 * where the annotation does not specify a name. In other words the purpose of this resolver
 * is to provide access to multiple matrix variables, either all or associated with a specific
 * path variable.
 *
 * 解析带有{@link MatrixVariable @MatrixVariable}注释的类型为{@link Map}的参数，其中注释没有指定名称。
 * 换句话说，这个解析器的目的是提供对多个矩阵变量的访问，这些变量可以是全部的，也可以是与特定路径变量相关联的。
 *
 *
 * <p>When a name is specified, an argument of type Map is considered to be a single attribute
 * with a Map value, and is resolved by {@link MatrixVariableMethodArgumentResolver} instead.
 *
 * 当指定名称时，Map类型的参数被认为是具有Map值的单个属性，并由{@link MatrixVariableMethodArgumentResolver}来解析。
 *
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public class MatrixVariableMapMethodArgumentResolver implements HandlerMethodArgumentResolver {

	/**
	 * 解析参数类型为{@link Map}并且{@link MatrixVariable#name()}属性未指定的参数
	 *
	 * @param parameter the method parameter to check
	 * @return
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		MatrixVariable matrixVariable = parameter.getParameterAnnotation(MatrixVariable.class);
		return (matrixVariable != null && Map.class.isAssignableFrom(parameter.getParameterType()) &&
				!StringUtils.hasText(matrixVariable.name()));
	}

	@Override
	@Nullable
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest request, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		@SuppressWarnings("unchecked")
		Map<String, MultiValueMap<String, String>> matrixVariables =
				(Map<String, MultiValueMap<String, String>>) request.getAttribute(
						HandlerMapping.MATRIX_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);

		if (CollectionUtils.isEmpty(matrixVariables)) {
			return Collections.emptyMap();
		}

		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		MatrixVariable ann = parameter.getParameterAnnotation(MatrixVariable.class);
		Assert.state(ann != null, "No MatrixVariable annotation");
		String pathVariable = ann.pathVar();

		/**
		 * 如果指定了{@link MatrixVariable#pathVar()} 属性，获取{@link MatrixVariable#pathVar()} 属性指定的RUI路径参数矩阵
		 */
		if (!pathVariable.equals(ValueConstants.DEFAULT_NONE)) {
			MultiValueMap<String, String> mapForPathVariable = matrixVariables.get(pathVariable);
			if (mapForPathVariable == null) {
				return Collections.emptyMap();
			}
			map.putAll(mapForPathVariable);
		}
		else {
			/**
			 * 返回所有URI路径中的矩阵键值对，不会去重
			 */
			for (MultiValueMap<String, String> vars : matrixVariables.values()) {
				vars.forEach((name, values) -> {
					for (String value : values) {
						map.add(name, value);
					}
				});
			}
		}

		/**
		 * 如果参数类型是多值映射表，直接返回结果集，如果是单值映射表，会将多值映射表转换成单值映射表并返回
		 * 多值映射表转换单值映射表时，每个key取对应的第一个值，如果key对应的值列表为空，则不取该key
		 */
		return (isSingleValueMap(parameter) ? map.toSingleValueMap() : map);
	}

	/**
	 * 判断方法参数是否单值映射表，如果{@link Map}的值时{@link List}类型时认为是多值映射表
	 *
	 * @param parameter
	 * @return
	 */
	private boolean isSingleValueMap(MethodParameter parameter) {
		if (!MultiValueMap.class.isAssignableFrom(parameter.getParameterType())) {
			/**
			 * 获取到{@link Map}类型参数的泛型类型
			 * genericTypes[0] : K
			 * genericTypes[1] : V
			 */
			ResolvableType[] genericTypes = ResolvableType.forMethodParameter(parameter).getGenerics();
			if (genericTypes.length == 2) {
				/**
				 * 如果{@link Map}的值时{@link List}类型时认为是多值映射表
				 * eg:
				 * 		{@link Map<String, List<String>>} : 多值映射表
				 * 		{@link Map<String, String>} : 单值映射表
				 */
				return !List.class.isAssignableFrom(genericTypes[1].toClass());
			}
		}
		/**
		 * {@link MultiValueMap}类型是躲值映射表
		 */
		return false;
	}

}
