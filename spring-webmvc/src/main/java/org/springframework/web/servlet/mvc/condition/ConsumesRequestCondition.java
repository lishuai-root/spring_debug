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

package org.springframework.web.servlet.mvc.condition;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.mvc.condition.HeadersRequestCondition.HeaderExpression;

import java.util.*;

/**
 * A logical disjunction (' || ') request condition to match a request's
 * 'Content-Type' header to a list of media type expressions. Two kinds of
 * media type expressions are supported, which are described in
 * {@link RequestMapping#consumes()} and {@link RequestMapping#headers()}
 * where the header name is 'Content-Type'. Regardless of which syntax is
 * used, the semantics are the same.
 *
 * 逻辑分离(' || ')请求条件，将请求的'Content-Type'报头匹配到媒体类型表达式列表。
 * 支持两种媒体类型表达式，分别在{@link RequestMapping#consumes()}和{@link RequestMapping#headers()}中描述，其中头名称为'Content-Type'。
 * 无论使用哪种语法，语义都是相同的。
 *
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ConsumesRequestCondition extends AbstractRequestCondition<ConsumesRequestCondition> {

	private static final ConsumesRequestCondition EMPTY_CONDITION = new ConsumesRequestCondition();


	private final List<ConsumeMediaTypeExpression> expressions;

	/**
	 * 是否期望请求有请求体，如果在使用了{@link org.springframework.web.bind.annotation.RequestBody}注解
	 * 则会通过{@link RequestBody#required()}属性设置，如果存在多个{@link org.springframework.web.bind.annotation.RequestBody}注解
	 * 只会以第一个为准
	 */
	private boolean bodyRequired = true;


	/**
	 * Creates a new instance from 0 or more "consumes" expressions.
	 * @param consumes expressions with the syntax described in
	 * {@link RequestMapping#consumes()}; if 0 expressions are provided,
	 * the condition will match to every request
	 */
	public ConsumesRequestCondition(String... consumes) {
		this(consumes, null);
	}

	/**
	 * Creates a new instance with "consumes" and "header" expressions.
	 * "Header" expressions where the header name is not 'Content-Type' or have
	 * no header value defined are ignored. If 0 expressions are provided in
	 * total, the condition will match to every request
	 * @param consumes as described in {@link RequestMapping#consumes()}
	 * @param headers as described in {@link RequestMapping#headers()}
	 */
	public ConsumesRequestCondition(String[] consumes, @Nullable String[] headers) {
		this.expressions = parseExpressions(consumes, headers);
		if (this.expressions.size() > 1) {
			Collections.sort(this.expressions);
		}
	}

	private static List<ConsumeMediaTypeExpression> parseExpressions(String[] consumes, @Nullable String[] headers) {
		Set<ConsumeMediaTypeExpression> result = null;
		if (!ObjectUtils.isEmpty(headers)) {
			for (String header : headers) {
				HeaderExpression expr = new HeaderExpression(header);
				if ("Content-Type".equalsIgnoreCase(expr.name) && expr.value != null) {
					result = (result != null ? result : new LinkedHashSet<>());
					for (MediaType mediaType : MediaType.parseMediaTypes(expr.value)) {
						result.add(new ConsumeMediaTypeExpression(mediaType, expr.isNegated));
					}
				}
			}
		}
		if (!ObjectUtils.isEmpty(consumes)) {
			result = (result != null ? result : new LinkedHashSet<>());
			for (String consume : consumes) {
				result.add(new ConsumeMediaTypeExpression(consume));
			}
		}
		return (result != null ? new ArrayList<>(result) : Collections.emptyList());
	}

	/**
	 * Private constructor for internal when creating matching conditions.
	 * Note the expressions List is neither sorted nor deep copied.
	 */
	private ConsumesRequestCondition(List<ConsumeMediaTypeExpression> expressions) {
		this.expressions = expressions;
	}


	/**
	 * Return the contained MediaType expressions.
	 */
	public Set<MediaTypeExpression> getExpressions() {
		return new LinkedHashSet<>(this.expressions);
	}

	/**
	 * Returns the media types for this condition excluding negated expressions.
	 */
	public Set<MediaType> getConsumableMediaTypes() {
		Set<MediaType> result = new LinkedHashSet<>();
		for (ConsumeMediaTypeExpression expression : this.expressions) {
			if (!expression.isNegated()) {
				result.add(expression.getMediaType());
			}
		}
		return result;
	}

	/**
	 * Whether the condition has any media type expressions.
	 * 条件是否有任何媒体类型表达式。
	 */
	@Override
	public boolean isEmpty() {
		return this.expressions.isEmpty();
	}

	@Override
	protected Collection<ConsumeMediaTypeExpression> getContent() {
		return this.expressions;
	}

	@Override
	protected String getToStringInfix() {
		return " || ";
	}

	/**
	 * Whether this condition should expect requests to have a body.
	 * 这种情况是否期望请求具有主体。
	 *
	 * <p>By default this is set to {@code true} in which case it is assumed a
	 * request body is required and this condition matches to the "Content-Type"
	 * header or falls back on "Content-Type: application/octet-stream".
	 * 默认情况下，它被设置为{@code true}，在这种情况下，假设需要一个请求体，
	 * 并且这个条件匹配“Content-Type”报头或退回到“Content-Type: application/octet-stream”。
	 *
	 * <p>If set to {@code false}, and the request does not have a body, then this
	 * condition matches automatically, i.e. without checking expressions.
	 * 如果设置为{@code false}，并且请求没有body，则该条件自动匹配，即不检查表达式。
	 *
	 * @param bodyRequired whether requests are expected to have a body
	 * @since 5.2
	 */
	public void setBodyRequired(boolean bodyRequired) {
		this.bodyRequired = bodyRequired;
	}

	/**
	 * Return the setting for {@link #setBodyRequired(boolean)}.
	 * @since 5.2
	 */
	public boolean isBodyRequired() {
		return this.bodyRequired;
	}


	/**
	 * Returns the "other" instance if it has any expressions; returns "this"
	 * instance otherwise. Practically that means a method-level "consumes"
	 * overrides a type-level "consumes" condition.
	 */
	@Override
	public ConsumesRequestCondition combine(ConsumesRequestCondition other) {
		return (!other.expressions.isEmpty() ? other : this);
	}

	/**
	 * Checks if any of the contained media type expressions match the given
	 * request 'Content-Type' header and returns an instance that is guaranteed
	 * to contain matching expressions only. The match is performed via
	 * {@link MediaType#includes(MediaType)}.
	 * @param request the current request
	 * @return the same instance if the condition contains no expressions;
	 * or a new condition with matching expressions only;
	 * or {@code null} if no expressions match
	 */
	@Override
	@Nullable
	public ConsumesRequestCondition getMatchingCondition(HttpServletRequest request) {
		if (CorsUtils.isPreFlightRequest(request)) {
			return EMPTY_CONDITION;
		}
		if (isEmpty()) {
			return this;
		}
		if (!hasBody(request) && !this.bodyRequired) {
			return EMPTY_CONDITION;
		}

		// Common media types are cached at the level of MimeTypeUtils

		MediaType contentType;
		try {
			contentType = StringUtils.hasLength(request.getContentType()) ?
					MediaType.parseMediaType(request.getContentType()) :
					MediaType.APPLICATION_OCTET_STREAM;
		}
		catch (InvalidMediaTypeException ex) {
			return null;
		}

		List<ConsumeMediaTypeExpression> result = getMatchingExpressions(contentType);
		return !CollectionUtils.isEmpty(result) ? new ConsumesRequestCondition(result) : null;
	}

	private boolean hasBody(HttpServletRequest request) {
		String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
		String transferEncoding = request.getHeader(HttpHeaders.TRANSFER_ENCODING);
		return StringUtils.hasText(transferEncoding) ||
				(StringUtils.hasText(contentLength) && !contentLength.trim().equals("0"));
	}

	@Nullable
	private List<ConsumeMediaTypeExpression> getMatchingExpressions(MediaType contentType) {
		List<ConsumeMediaTypeExpression> result = null;
		for (ConsumeMediaTypeExpression expression : this.expressions) {
			if (expression.match(contentType)) {
				result = result != null ? result : new ArrayList<>();
				result.add(expression);
			}
		}
		return result;
	}

	/**
	 * Returns:
	 * <ul>
	 * <li>0 if the two conditions have the same number of expressions
	 * <li>Less than 0 if "this" has more or more specific media type expressions
	 * <li>Greater than 0 if "other" has more or more specific media type expressions
	 * </ul>
	 * <p>It is assumed that both instances have been obtained via
	 * {@link #getMatchingCondition(HttpServletRequest)} and each instance contains
	 * the matching consumable media type expression only or is otherwise empty.
	 */
	@Override
	public int compareTo(ConsumesRequestCondition other, HttpServletRequest request) {
		if (this.expressions.isEmpty() && other.expressions.isEmpty()) {
			return 0;
		}
		else if (this.expressions.isEmpty()) {
			return 1;
		}
		else if (other.expressions.isEmpty()) {
			return -1;
		}
		else {
			return this.expressions.get(0).compareTo(other.expressions.get(0));
		}
	}


	/**
	 * Parses and matches a single media type expression to a request's 'Content-Type' header.
	 */
	static class ConsumeMediaTypeExpression extends AbstractMediaTypeExpression {

		ConsumeMediaTypeExpression(String expression) {
			super(expression);
		}

		ConsumeMediaTypeExpression(MediaType mediaType, boolean negated) {
			super(mediaType, negated);
		}

		public final boolean match(MediaType contentType) {
			boolean match = getMediaType().includes(contentType);
			return !isNegated() == match;
		}
	}

}
