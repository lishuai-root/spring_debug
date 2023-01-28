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

package org.springframework.web.cors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.lang.Nullable;

/**
 * Interface to be implemented by classes (usually HTTP request handlers) that
 * provides a {@link CorsConfiguration} instance based on the provided request.
 *
 * 由类(通常是HTTP请求处理程序)实现的接口，该类根据所提供的请求提供{@link CorsConfiguration}实例。
 *
 * @author Sebastien Deleuze
 * @since 4.2
 */
public interface CorsConfigurationSource {

	/**
	 * Return a {@link CorsConfiguration} based on the incoming request.
	 * 根据传入的请求返回一个{@link CorsConfiguration}。
	 *
	 * @return the associated {@link CorsConfiguration}, or {@code null} if none
	 */
	@Nullable
	CorsConfiguration getCorsConfiguration(HttpServletRequest request);

}
