/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.http;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * Represents an HTTP (byte) range for use with the HTTP {@code "Range"} header.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 4.2
 * @see <a href="https://tools.ietf.org/html/rfc7233">HTTP/1.1: Range Requests</a>
 * @see HttpHeaders#setRange(List)
 * @see HttpHeaders#getRange()
 */
public abstract class HttpRange {

	/** Maximum ranges per request. */
	private static final int MAX_RANGES = 100;

	private static final String BYTE_RANGE_PREFIX = "bytes=";


	/**
	 * 使用给定资源创建{@link ResourceRegion}实例
	 *
	 * Turn a {@code Resource} into a {@link ResourceRegion} using the range
	 * information contained in the current {@code HttpRange}.
	 * 使用当前{@code HttpRange}中包含的范围信息将{@code Resource}转换为{@link ResourceRegion}。
	 *
	 * @param resource the {@code Resource} to select the region from
	 * @return the selected region of the given {@code Resource}
	 * @since 4.3
	 */
	public ResourceRegion toResourceRegion(Resource resource) {
		// Don't try to determine contentLength on InputStreamResource - cannot be read afterwards...
		// Note: custom InputStreamResource subclasses could provide a pre-calculated content length!
		/**
		 * 不要尝试在InputStreamResource上确定contentLength -之后不能读取…
		 * 注意:自定义InputStreamResource子类可以提供预先计算的内容长度!
		 */
		Assert.isTrue(resource.getClass() != InputStreamResource.class,
				"Cannot convert an InputStreamResource to a ResourceRegion");
		long contentLength = getLengthFor(resource);
		long start = getRangeStart(contentLength);
		long end = getRangeEnd(contentLength);
		Assert.isTrue(start < contentLength, "'position' exceeds the resource length " + contentLength);
		/**
		 * 使用资源和读取的开始位置和长度创建{@link ResourceRegion}实例
		 */
		return new ResourceRegion(resource, start, end - start + 1);
	}

	/**
	 * Return the start of the range given the total length of a representation.
	 * 给定表示的总长度，返回范围的开始。
	 *
	 * @param length the length of the representation
	 * @return the start of this range for the representation
	 */
	public abstract long getRangeStart(long length);

	/**
	 * Return the end of the range (inclusive) given the total length of a representation.
	 * 给定表示的总长度，返回范围的结束(包括)。
	 *
	 * @param length the length of the representation
	 * @return the end of the range for the representation
	 */
	public abstract long getRangeEnd(long length);


	/**
	 * Create an {@code HttpRange} from the given position to the end.
	 * @param firstBytePos the first byte position
	 * @return a byte range that ranges from {@code firstPos} till the end
	 * @see <a href="https://tools.ietf.org/html/rfc7233#section-2.1">Byte Ranges</a>
	 */
	public static HttpRange createByteRange(long firstBytePos) {
		return new ByteRange(firstBytePos, null);
	}

	/**
	 * Create a {@code HttpRange} from the given fist to last position.
	 * @param firstBytePos the first byte position
	 * @param lastBytePos the last byte position
	 * @return a byte range that ranges from {@code firstPos} till {@code lastPos}
	 * @see <a href="https://tools.ietf.org/html/rfc7233#section-2.1">Byte Ranges</a>
	 */
	public static HttpRange createByteRange(long firstBytePos, long lastBytePos) {
		return new ByteRange(firstBytePos, lastBytePos);
	}

	/**
	 * Create an {@code HttpRange} that ranges over the last given number of bytes.
	 * @param suffixLength the number of bytes for the range
	 * @return a byte range that ranges over the last {@code suffixLength} number of bytes
	 * @see <a href="https://tools.ietf.org/html/rfc7233#section-2.1">Byte Ranges</a>
	 */
	public static HttpRange createSuffixRange(long suffixLength) {
		return new SuffixByteRange(suffixLength);
	}

	/**
	 * Parse the given, comma-separated string into a list of {@code HttpRange} objects.
	 * 将给定的逗号分隔的字符串解析为{@code HttpRange}对象列表。
	 *
	 * <p>This method can be used to parse an {@code Range} header.
	 * 此方法可用于解析{@code Range}标头。
	 *
	 * @param ranges the string to parse
	 * @return the list of ranges
	 * @throws IllegalArgumentException if the string cannot be parsed
	 * or if the number of ranges is greater than 100
	 */
	public static List<HttpRange> parseRanges(@Nullable String ranges) {
		if (!StringUtils.hasLength(ranges)) {
			return Collections.emptyList();
		}
		if (!ranges.startsWith(BYTE_RANGE_PREFIX)) {
			throw new IllegalArgumentException("Range '" + ranges + "' does not start with 'bytes='");
		}
		/**
		 * 去除"bytes="前缀
		 */
		ranges = ranges.substring(BYTE_RANGE_PREFIX.length());

		/**
		 * 多个范围按","分割
		 */
		String[] tokens = StringUtils.tokenizeToStringArray(ranges, ",");
		if (tokens.length > MAX_RANGES) {
			throw new IllegalArgumentException("Too many ranges: " + tokens.length);
		}
		List<HttpRange> result = new ArrayList<>(tokens.length);
		for (String token : tokens) {
			result.add(parseRange(token));
		}
		return result;
	}

	/**
	 * 解析单个{@link HttpHeaders#RANGE}范围
	 *
	 * @param range
	 * @return
	 */
	private static HttpRange parseRange(String range) {
		Assert.hasLength(range, "Range String must not be empty");
		int dashIdx = range.indexOf('-');
		if (dashIdx > 0) {
			/**
			 * 获取bytes范围的第一个范围值
			 */
			long firstPos = Long.parseLong(range.substring(0, dashIdx));
			/**
			 * 获取bytes范围的第二个范围值
			 */
			if (dashIdx < range.length() - 1) {
				Long lastPos = Long.parseLong(range.substring(dashIdx + 1));
				return new ByteRange(firstPos, lastPos);
			}
			else {
				/**
				 * 最后一个值可选
				 */
				return new ByteRange(firstPos, null);
			}
		}
		/**
		 * 创建后缀字节范围
		 */
		else if (dashIdx == 0) {
			long suffixLength = Long.parseLong(range.substring(1));
			return new SuffixByteRange(suffixLength);
		}
		else {
			throw new IllegalArgumentException("Range '" + range + "' does not contain \"-\"");
		}
	}

	/**
	 * Convert each {@code HttpRange} into a {@code ResourceRegion}, selecting the
	 * appropriate segment of the given {@code Resource} using HTTP Range information.
	 * 将每个{@code HttpRange}转换为一个{@code ResourceRegion}，使用HTTP范围信息选择给定{@code Resource}的适当段。
	 *
	 * @param ranges the list of ranges
	 * @param resource the resource to select the regions from
	 * @return the list of regions for the given resource
	 * @throws IllegalArgumentException if the sum of all ranges exceeds the resource length
	 * @since 4.3
	 */
	public static List<ResourceRegion> toResourceRegions(List<HttpRange> ranges, Resource resource) {
		if (CollectionUtils.isEmpty(ranges)) {
			return Collections.emptyList();
		}
		List<ResourceRegion> regions = new ArrayList<>(ranges.size());
		/**
		 * 根据range范围创建{@link ResourceRegion}实例
		 */
		for (HttpRange range : ranges) {
			regions.add(range.toResourceRegion(resource));
		}
		/**
		 * 检查请求获取的资源大小不能超过资源本省的大小，否则抛出异常
		 */
		if (ranges.size() > 1) {
			long length = getLengthFor(resource);
			long total = 0;
			for (ResourceRegion region : regions) {
				total += region.getCount();
			}
			if (total >= length) {
				throw new IllegalArgumentException("The sum of all ranges (" + total +
						") should be less than the resource length (" + length + ")");
			}
		}
		return regions;
	}

	private static long getLengthFor(Resource resource) {
		try {
			long contentLength = resource.contentLength();
			Assert.isTrue(contentLength > 0, "Resource content length should be > 0");
			return contentLength;
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Failed to obtain Resource content length", ex);
		}
	}

	/**
	 * Return a string representation of the given list of {@code HttpRange} objects.
	 * <p>This method can be used to for an {@code Range} header.
	 * @param ranges the ranges to create a string of
	 * @return the string representation
	 */
	public static String toString(Collection<HttpRange> ranges) {
		Assert.notEmpty(ranges, "Ranges Collection must not be empty");
		StringJoiner builder = new StringJoiner(", ", BYTE_RANGE_PREFIX, "");
		for (HttpRange range : ranges) {
			builder.add(range.toString());
		}
		return builder.toString();
	}


	/**
	 * Represents an HTTP/1.1 byte range, with a first and optional last position.
	 * 表示HTTP1.1字节范围，包含第一个和可选的最后一个位置。
	 *
	 * @see <a href="https://tools.ietf.org/html/rfc7233#section-2.1">Byte Ranges</a>
	 * @see HttpRange#createByteRange(long)
	 * @see HttpRange#createByteRange(long, long)
	 */
	private static class ByteRange extends HttpRange {

		private final long firstPos;

		@Nullable
		private final Long lastPos;

		public ByteRange(long firstPos, @Nullable Long lastPos) {
			assertPositions(firstPos, lastPos);
			this.firstPos = firstPos;
			this.lastPos = lastPos;
		}

		private void assertPositions(long firstBytePos, @Nullable Long lastBytePos) {
			if (firstBytePos < 0) {
				throw new IllegalArgumentException("Invalid first byte position: " + firstBytePos);
			}
			if (lastBytePos != null && lastBytePos < firstBytePos) {
				throw new IllegalArgumentException("firstBytePosition=" + firstBytePos +
						" should be less then or equal to lastBytePosition=" + lastBytePos);
			}
		}

		@Override
		public long getRangeStart(long length) {
			return this.firstPos;
		}

		@Override
		public long getRangeEnd(long length) {
			if (this.lastPos != null && this.lastPos < length) {
				return this.lastPos;
			}
			else {
				return length - 1;
			}
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ByteRange)) {
				return false;
			}
			ByteRange otherRange = (ByteRange) other;
			return (this.firstPos == otherRange.firstPos &&
					ObjectUtils.nullSafeEquals(this.lastPos, otherRange.lastPos));
		}

		@Override
		public int hashCode() {
			return (ObjectUtils.nullSafeHashCode(this.firstPos) * 31 +
					ObjectUtils.nullSafeHashCode(this.lastPos));
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append(this.firstPos);
			builder.append('-');
			if (this.lastPos != null) {
				builder.append(this.lastPos);
			}
			return builder.toString();
		}
	}


	/**
	 * Represents an HTTP/1.1 suffix byte range, with a number of suffix bytes.
	 * 表示HTTP1.1后缀字节范围，包含若干后缀字节。
	 *
	 * @see <a href="https://tools.ietf.org/html/rfc7233#section-2.1">Byte Ranges</a>
	 * @see HttpRange#createSuffixRange(long)
	 */
	private static class SuffixByteRange extends HttpRange {

		private final long suffixLength;

		public SuffixByteRange(long suffixLength) {
			if (suffixLength < 0) {
				throw new IllegalArgumentException("Invalid suffix length: " + suffixLength);
			}
			this.suffixLength = suffixLength;
		}

		@Override
		public long getRangeStart(long length) {
			if (this.suffixLength < length) {
				return length - this.suffixLength;
			}
			else {
				return 0;
			}
		}

		@Override
		public long getRangeEnd(long length) {
			return length - 1;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof SuffixByteRange)) {
				return false;
			}
			SuffixByteRange otherRange = (SuffixByteRange) other;
			return (this.suffixLength == otherRange.suffixLength);
		}

		@Override
		public int hashCode() {
			return Long.hashCode(this.suffixLength);
		}

		@Override
		public String toString() {
			return "-" + this.suffixLength;
		}
	}

}
