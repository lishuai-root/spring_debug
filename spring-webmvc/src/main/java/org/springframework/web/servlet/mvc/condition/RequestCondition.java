/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.servlet.mvc.condition;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.lang.Nullable;

/**
 * Contract for request mapping conditions.
 * 请求映射条件的契约。
 *
 * <p>Request conditions can be combined via {@link #combine(Object)}, matched to
 * a request via {@link #getMatchingCondition(HttpServletRequest)}, and compared
 * to each other via {@link #compareTo(Object, HttpServletRequest)} to determine
 * which is a closer match for a given request.
 * 请求条件可以通过{@link #combine(Object)}组合，通过{@link #getMatchingCondition(HttpServletRequest)}与请求匹配，
 * 并通过{@link #compareTo(Object, HttpServletRequest)}相互比较，以确定哪个是给定请求的更接近的匹配。
 *
 *
 * @author Rossen Stoyanchev
 * @author Arjen Poutsma
 * @since 3.1
 * @param <T> the type of objects that this RequestCondition can be combined
 * with and compared to
 */
public interface RequestCondition<T> {

	/**
	 * Combine this condition with another such as conditions from a
	 * type-level and method-level {@code @RequestMapping} annotation.
	 * 将此条件与其他条件结合起来，例如来自类型级和方法级{@code @RequestMapping}注释的条件。
	 *
	 * @param other the condition to combine with.
	 * @return a request condition instance that is the result of combining
	 * the two condition instances.
	 */
	T combine(T other);

	/**
	 * 根据当前请求返回与之匹配的处理bean(bean实例，或者方法)
	 *
	 * Check if the condition matches the request returning a potentially new
	 * instance created for the current request. For example a condition with
	 * multiple URL patterns may return a new instance only with those patterns
	 * that match the request.
	 * 检查条件是否与返回为当前请求创建的可能的新实例的请求匹配。
	 * 例如，具有多个URL模式的条件可能只返回与请求匹配的模式的新实例。
	 *
	 * <p>For CORS pre-flight requests, conditions should match to the would-be,
	 * actual request (e.g. URL pattern, query parameters, and the HTTP method
	 * from the "Access-Control-Request-Method" header). If a condition cannot
	 * be matched to a pre-flight request it should return an instance with
	 * empty content thus not causing a failure to match.
	 * 对于CORS预飞行请求，条件应与潜在的实际请求相匹配(例如URL模式、查询参数和来自“Access-Control-Request-Method”报头的HTTP方法)。
	 * 如果一个条件不能匹配到预飞行请求，它应该返回一个空内容的实例，这样就不会导致匹配失败。
	 *
	 * @return a condition instance in case of a match or {@code null} otherwise.
	 * 如果匹配则为条件实例，否则为{@code null}。
	 */
	@Nullable
	T getMatchingCondition(HttpServletRequest request);

	/**
	 * Compare this condition to another condition in the context of
	 * a specific request. This method assumes both instances have
	 * been obtained via {@link #getMatchingCondition(HttpServletRequest)}
	 * to ensure they have content relevant to current request only.
	 * 将此条件与特定请求上下文中的另一个条件进行比较。
	 * 这个方法假设两个实例都是通过{@link #getMatchingCondition(HttpServletRequest)}获得的，以确保它们的内容只与当前请求相关。
	 *
	 */
	int compareTo(T other, HttpServletRequest request);

}
