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

package org.springframework.web.bind.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import org.springframework.core.annotation.AliasFor;

/**
 * Annotation which indicates that a method parameter should be bound to a web
 * request parameter.
 * 注释，指示方法参数应绑定到web请求参数。
 *
 * <p>Supported for annotated handler methods in Spring MVC and Spring WebFlux
 * as follows:
 * 支持Spring MVC和Spring WebFlux中的注释处理方法，如下所示:
 * <ul>
 * <li>In Spring MVC, "request parameters" map to query parameters, form data,
 * and parts in multipart requests. This is because the Servlet API combines
 * query parameters and form data into a single map called "parameters", and
 * that includes automatic parsing of the request body.
 * 在Spring MVC中，“请求参数”映射到查询参数、表单数据和多部分请求中的部分。
 * 这是因为Servlet API将查询参数和表单数据组合到一个称为“参数”的映射中，其中包括对请求体的自动解析。
 *
 * <li>In Spring WebFlux, "request parameters" map to query parameters only.
 * To work with all 3, query, form data, and multipart data, you can use data
 * binding to a command object annotated with {@link ModelAttribute}.
 * 在Spring WebFlux中，“请求参数”只映射到查询参数。
 * 要同时处理查询、表单数据和多部分数据，可以使用数据绑定到带有{@link ModelAttribute}注释的命令对象。
 *
 * </ul>
 *
 * <p>If the method parameter type is {@link Map} and a request parameter name
 * is specified, then the request parameter value is converted to a {@link Map}
 * assuming an appropriate conversion strategy is available.
 * 如果方法参数类型是{@link Map}，并且指定了请求参数名，那么假设有适当的转换策略，请求参数值将转换为{@link Map}。
 *
 *
 * <p>If the method parameter is {@link java.util.Map Map&lt;String, String&gt;} or
 * {@link org.springframework.util.MultiValueMap MultiValueMap&lt;String, String&gt;}
 * and a parameter name is not specified, then the map parameter is populated
 * with all request parameter names and values.
 * 如果方法参数是{@link java.util.Map Map&lt;String, String&gt;}或{@link org.springframework.util.MultiValueMap MultiValueMap&lt;String, String&gt;}
 * 并且没有指定参数名，那么映射参数将使用所有请求参数名和值填充。
 *
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 2.5
 * @see RequestMapping
 * @see RequestHeader
 * @see CookieValue
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {

	/**
	 * Alias for {@link #name}.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * The name of the request parameter to bind to.
	 * @since 4.2
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * Whether the parameter is required.
	 * 是否为必选参数。
	 *
	 * <p>Defaults to {@code true}, leading to an exception being thrown
	 * if the parameter is missing in the request. Switch this to
	 * {@code false} if you prefer a {@code null} value if the parameter is
	 * not present in the request.
	 * <p>默认为{@code true}，如果请求中缺少参数，将导致抛出异常。如果请求中没有参数，则选择{@code null}值，将其切换为{@code false}。
	 *
	 * <p>Alternatively, provide a {@link #defaultValue}, which implicitly
	 * sets this flag to {@code false}.
	 * 或者，提供一个{@link defaultValue}，它隐式地将这个标志设置为{@code false}。
	 */
	boolean required() default true;

	/**
	 * The default value to use as a fallback when the request parameter is
	 * not provided or has an empty value.
	 * 当请求参数未提供或为空值时使用的默认值。
	 *
	 * <p>Supplying a default value implicitly sets {@link #required} to
	 * {@code false}.
	 * <p>提供一个默认值隐式地将{@link #required}设置为{@code false}。
	 */
	String defaultValue() default ValueConstants.DEFAULT_NONE;

}
