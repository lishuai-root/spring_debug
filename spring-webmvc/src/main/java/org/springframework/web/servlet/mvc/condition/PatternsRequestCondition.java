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

package org.springframework.web.servlet.mvc.condition;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;

import java.util.*;

/**
 * A logical disjunction (' || ') request condition that matches a request
 * against a set of URL path patterns.
 * 逻辑分离(' || ')请求条件，根据一组URL路径模式匹配请求。
 *
 * <p>In contrast to {@link PathPatternsRequestCondition} which uses parsed
 * {@link PathPattern}s, this condition does String pattern matching via
 * {@link org.springframework.util.AntPathMatcher AntPathMatcher}.
 *
 * 与{@link PathPatternsRequestCondition}使用解析的{@link PathPattern}s相比，
 * 这个条件通过{@link org.springframework.util.AntPathMatcher AntPathMatcher}进行字符串模式匹配。
 *
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class PatternsRequestCondition extends AbstractRequestCondition<PatternsRequestCondition> {

	private final static Set<String> EMPTY_PATH_PATTERN = Collections.singleton("");


	private final Set<String> patterns;

	private final PathMatcher pathMatcher;

	private final boolean useSuffixPatternMatch;

	private final boolean useTrailingSlashMatch;

	private final List<String> fileExtensions = new ArrayList<>();


	/**
	 * Constructor with URL patterns which are prepended with "/" if necessary.
	 * @param patterns 0 or more URL patterns; no patterns results in an empty
	 * path {@code ""} mapping which matches all requests.
	 */
	public PatternsRequestCondition(String... patterns) {
		this(patterns, true, null);
	}

	/**
	 * Variant of {@link #PatternsRequestCondition(String...)} with a
	 * {@link PathMatcher} and flag for matching trailing slashes.
	 * @since 5.3
	 */
	public PatternsRequestCondition(String[] patterns,  boolean useTrailingSlashMatch,
			@Nullable PathMatcher pathMatcher) {

		this(patterns, null, pathMatcher, useTrailingSlashMatch);
	}

	/**
	 * Variant of {@link #PatternsRequestCondition(String...)} with a
	 * {@link UrlPathHelper} and a {@link PathMatcher}, and whether to match
	 * trailing slashes.
	 * <p>As of 5.3 the path is obtained through the static method
	 * {@link UrlPathHelper#getResolvedLookupPath} and a {@code UrlPathHelper}
	 * does not need to be passed in.
	 * @since 5.2.4
	 * @deprecated as of 5.3 in favor of
	 * {@link #PatternsRequestCondition(String[], boolean, PathMatcher)}.
	 */
	@Deprecated
	public PatternsRequestCondition(String[] patterns, @Nullable UrlPathHelper urlPathHelper,
			@Nullable PathMatcher pathMatcher, boolean useTrailingSlashMatch) {

		this(patterns, urlPathHelper, pathMatcher, false, useTrailingSlashMatch);
	}

	/**
	 * Variant of {@link #PatternsRequestCondition(String...)} with a
	 * {@link UrlPathHelper} and a {@link PathMatcher}, and flags for matching
	 * with suffixes and trailing slashes.
	 * <p>As of 5.3 the path is obtained through the static method
	 * {@link UrlPathHelper#getResolvedLookupPath} and a {@code UrlPathHelper}
	 * does not need to be passed in.
	 * @deprecated as of 5.2.4. See class-level note in
	 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}
	 * on the deprecation of path extension config options.
	 */
	@Deprecated
	public PatternsRequestCondition(String[] patterns, @Nullable UrlPathHelper urlPathHelper,
			@Nullable PathMatcher pathMatcher, boolean useSuffixPatternMatch, boolean useTrailingSlashMatch) {

		this(patterns, urlPathHelper, pathMatcher, useSuffixPatternMatch, useTrailingSlashMatch, null);
	}

	/**
	 * Variant of {@link #PatternsRequestCondition(String...)} with a
	 * {@link UrlPathHelper} and a {@link PathMatcher}, and flags for matching
	 * with suffixes and trailing slashes, along with specific extensions.
	 * <p>As of 5.3 the path is obtained through the static method
	 * {@link UrlPathHelper#getResolvedLookupPath} and a {@code UrlPathHelper}
	 * does not need to be passed in.
	 * @deprecated as of 5.2.4. See class-level note in
	 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}
	 * on the deprecation of path extension config options.
	 */
	@Deprecated
	public PatternsRequestCondition(String[] patterns, @Nullable UrlPathHelper urlPathHelper,
			@Nullable PathMatcher pathMatcher, boolean useSuffixPatternMatch,
			boolean useTrailingSlashMatch, @Nullable List<String> fileExtensions) {

		this.patterns = initPatterns(patterns);
		this.pathMatcher = pathMatcher != null ? pathMatcher : new AntPathMatcher();
		this.useSuffixPatternMatch = useSuffixPatternMatch;
		this.useTrailingSlashMatch = useTrailingSlashMatch;

		if (fileExtensions != null) {
			for (String fileExtension : fileExtensions) {
				if (fileExtension.charAt(0) != '.') {
					fileExtension = "." + fileExtension;
				}
				this.fileExtensions.add(fileExtension);
			}
		}
	}

	private static Set<String> initPatterns(String[] patterns) {
		if (!hasPattern(patterns)) {
			return EMPTY_PATH_PATTERN;
		}
		Set<String> result = new LinkedHashSet<>(patterns.length);
		for (String pattern : patterns) {
			if (StringUtils.hasLength(pattern) && !pattern.startsWith("/")) {
				pattern = "/" + pattern;
			}
			result.add(pattern);
		}
		return result;
	}

	private static boolean hasPattern(String[] patterns) {
		if (!ObjectUtils.isEmpty(patterns)) {
			for (String pattern : patterns) {
				if (StringUtils.hasText(pattern)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Private constructor for use when combining and matching.
	 */
	private PatternsRequestCondition(Set<String> patterns, PatternsRequestCondition other) {
		this.patterns = patterns;
		this.pathMatcher = other.pathMatcher;
		this.useSuffixPatternMatch = other.useSuffixPatternMatch;
		this.useTrailingSlashMatch = other.useTrailingSlashMatch;
		this.fileExtensions.addAll(other.fileExtensions);
	}


	public Set<String> getPatterns() {
		return this.patterns;
	}

	@Override
	protected Collection<String> getContent() {
		return this.patterns;
	}

	@Override
	protected String getToStringInfix() {
		return " || ";
	}

	/**
	 * Whether the condition is the "" (empty path) mapping.
	 */
	public boolean isEmptyPathMapping() {
		return this.patterns == EMPTY_PATH_PATTERN;
	}

	/**
	 * Return the mapping paths that are not patterns.
	 * @since 5.3
	 */
	public Set<String> getDirectPaths() {
		if (isEmptyPathMapping()) {
			return EMPTY_PATH_PATTERN;
		}
		Set<String> result = Collections.emptySet();
		for (String pattern : this.patterns) {
			if (!this.pathMatcher.isPattern(pattern)) {
				result = (result.isEmpty() ? new HashSet<>(1) : result);
				result.add(pattern);
			}
		}
		return result;
	}

	/**
	 * Returns a new instance with URL patterns from the current instance ("this") and
	 * the "other" instance as follows:
	 * 返回一个包含当前实例("this")和"other"实例的URL模式的新实例，如下所示:
	 *
	 * <ul>
	 * <li>If there are patterns in both instances, combine the patterns in "this" with
	 * the patterns in "other" using {@link PathMatcher#combine(String, String)}.
	 * 如果两个实例中都有模式，则使用{@link PathMatcher#combine(String, String)}将“this”中的模式与“other”中的模式结合起来。
	 *
	 * <li>If only one instance has patterns, use them.
	 * 如果只有一个实例具有模式，那么就使用它们。
	 *
	 * <li>If neither instance has patterns, use an empty String (i.e. "").
	 * 如果两个实例都没有模式，则使用空String(即。“”)。
	 * </ul>
	 */
	@Override
	public PatternsRequestCondition combine(PatternsRequestCondition other) {
		if (isEmptyPathMapping() && other.isEmptyPathMapping()) {
			return this;
		}
		else if (other.isEmptyPathMapping()) {
			return this;
		}
		else if (isEmptyPathMapping()) {
			return other;
		}
		Set<String> result = new LinkedHashSet<>();
		if (!this.patterns.isEmpty() && !other.patterns.isEmpty()) {
			for (String pattern1 : this.patterns) {
				for (String pattern2 : other.patterns) {
					result.add(this.pathMatcher.combine(pattern1, pattern2));
				}
			}
		}
		return new PatternsRequestCondition(result, this);
	}

	/**
	 * Checks if any of the patterns match the given request and returns an instance
	 * that is guaranteed to contain matching patterns, sorted via
	 * {@link PathMatcher#getPatternComparator(String)}.
	 * 检查是否有任何模式匹配给定的请求，并返回一个实例，
	 * 该实例保证包含匹配的模式，通过{@link PathMatcher#getPatternComparator(String)}排序。
	 *
	 * <p>A matching pattern is obtained by making checks in the following order:
	 * 一个匹配的模式是通过以下顺序进行检查来获得的:
	 * <ul>
	 * <li>Direct match 直接匹配
	 * <li>Pattern match with ".*" appended if the pattern doesn't already contain a "."
	 * 如果模式不包含"."，则模式匹配".*"。
	 * <li>Pattern match 模式匹配
	 * <li>Pattern match with "/" appended if the pattern doesn't already end in "/"
	 * 如果模式还没有以"/"结尾，则添加"/"匹配
	 * </ul>
	 * @param request the current request
	 * @return the same instance if the condition contains no patterns;
	 * or a new condition with sorted matching patterns;
	 * or {@code null} if no patterns match.
	 * 如果条件不包含模式，则为同一实例;或者一个新的条件，匹配模式已排序;或{@code null}，如果没有匹配的模式。
	 */
	@Override
	@Nullable
	public PatternsRequestCondition getMatchingCondition(HttpServletRequest request) {
		String lookupPath = UrlPathHelper.getResolvedLookupPath(request);
		List<String> matches = getMatchingPatterns(lookupPath);
		return !matches.isEmpty() ? new PatternsRequestCondition(new LinkedHashSet<>(matches), this) : null;
	}

	/**
	 * Find the patterns matching the given lookup path. Invoking this method should
	 * yield results equivalent to those of calling {@link #getMatchingCondition}.
	 * 找到与给定查找路径匹配的模式。调用此方法应产生与调用{@link #getMatchingCondition}相同的结果。
	 *
	 * This method is provided as an alternative to be used if no request is available
	 * (e.g. introspection, tooling, etc).
	 * 如果没有可用的请求(例如内省、工具等)，则提供此方法作为替代。
	 *
	 * @param lookupPath the lookup path to match to existing patterns
	 * @return a collection of matching patterns sorted with the closest match at the top
	 */
	public List<String> getMatchingPatterns(String lookupPath) {
		List<String> matches = null;
		for (String pattern : this.patterns) {
			String match = getMatchingPattern(pattern, lookupPath);
			if (match != null) {
				matches = (matches != null ? matches : new ArrayList<>());
				matches.add(match);
			}
		}
		if (matches == null) {
			return Collections.emptyList();
		}
		if (matches.size() > 1) {
			matches.sort(this.pathMatcher.getPatternComparator(lookupPath));
		}
		return matches;
	}

	@Nullable
	private String getMatchingPattern(String pattern, String lookupPath) {
		if (pattern.equals(lookupPath)) {
			return pattern;
		}
		if (this.useSuffixPatternMatch) {
			if (!this.fileExtensions.isEmpty() && lookupPath.indexOf('.') != -1) {
				for (String extension : this.fileExtensions) {
					if (this.pathMatcher.match(pattern + extension, lookupPath)) {
						return pattern + extension;
					}
				}
			}
			else {
				boolean hasSuffix = pattern.indexOf('.') != -1;
				if (!hasSuffix && this.pathMatcher.match(pattern + ".*", lookupPath)) {
					return pattern + ".*";
				}
			}
		}
		if (this.pathMatcher.match(pattern, lookupPath)) {
			return pattern;
		}
		if (this.useTrailingSlashMatch) {
			if (!pattern.endsWith("/") && this.pathMatcher.match(pattern + "/", lookupPath)) {
				return pattern + "/";
			}
		}
		return null;
	}

	/**
	 * Compare the two conditions based on the URL patterns they contain.
	 * 根据这两个条件所包含的URL模式进行比较。
	 *
	 * Patterns are compared one at a time, from top to bottom via
	 * {@link PathMatcher#getPatternComparator(String)}. If all compared
	 * patterns match equally, but one instance has more patterns, it is
	 * considered a closer match.
	 * 模式一次比较一个，从上到下通过{@link PathMatcher#getPatternComparator(String)}进行比较。
	 * 如果所有比较的模式都相同，但一个实例有更多的模式，则认为它是更接近的匹配。
	 *
	 * <p>It is assumed that both instances have been obtained via
	 * {@link #getMatchingCondition(HttpServletRequest)} to ensure they
	 * contain only patterns that match the request and are sorted with
	 * the best matches on top.
	 * 假设这两个实例都是通过{@link #getMatchingCondition(HttpServletRequest)}获得的，
	 * 以确保它们只包含与请求匹配的模式，并以最优匹配进行排序。
	 */
	@Override
	public int compareTo(PatternsRequestCondition other, HttpServletRequest request) {
		String lookupPath = UrlPathHelper.getResolvedLookupPath(request);
		Comparator<String> patternComparator = this.pathMatcher.getPatternComparator(lookupPath);
		Iterator<String> iterator = this.patterns.iterator();
		Iterator<String> iteratorOther = other.patterns.iterator();
		while (iterator.hasNext() && iteratorOther.hasNext()) {
			int result = patternComparator.compare(iterator.next(), iteratorOther.next());
			if (result != 0) {
				return result;
			}
		}
		if (iterator.hasNext()) {
			return -1;
		}
		else if (iteratorOther.hasNext()) {
			return 1;
		}
		else {
			return 0;
		}
	}

}
