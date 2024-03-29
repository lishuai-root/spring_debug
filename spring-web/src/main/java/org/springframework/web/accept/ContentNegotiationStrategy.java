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

package org.springframework.web.accept;

import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Collections;
import java.util.List;

/**
 * A strategy for resolving the requested media types for a request.
 * 为请求解析所请求的媒体类型的策略。
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
@FunctionalInterface
public interface ContentNegotiationStrategy {

	/**
	 * A singleton list with {@link MediaType#ALL} that is returned from
	 * {@link #resolveMediaTypes} when no specific media types are requested.
	 * 当没有请求特定的媒体类型时，由{@link #resolveMediaTypes}返回的带有{@link MediaType#ALL}的单例列表。
	 *
	 * @since 5.0.5
	 */
	List<MediaType> MEDIA_TYPE_ALL_LIST = Collections.singletonList(MediaType.ALL);


	/**
	 * Resolve the given request to a list of media types. The returned list is
	 * ordered by specificity first and by quality parameter second.
	 * 将给定的请求解析为媒体类型列表。返回的列表首先按特异性排序，其次按质量参数排序。
	 *
	 * @param webRequest the current request
	 * @return the requested media types, or {@link #MEDIA_TYPE_ALL_LIST} if none
	 * were requested.
	 * @throws HttpMediaTypeNotAcceptableException if the requested media
	 * types cannot be parsed
	 */
	List<MediaType> resolveMediaTypes(NativeWebRequest webRequest)
			throws HttpMediaTypeNotAcceptableException;

}
