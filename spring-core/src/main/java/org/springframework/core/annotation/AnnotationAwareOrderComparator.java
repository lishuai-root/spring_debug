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

package org.springframework.core.annotation;

import org.springframework.core.DecoratingProxy;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.List;

/**
 * {@code AnnotationAwareOrderComparator} is an extension of
 * {@link OrderComparator} that supports Spring's
 * {@link org.springframework.core.Ordered} interface as well as the
 * {@link Order @Order} and {@link jakarta.annotation.Priority @Priority}
 * annotations, with an order value provided by an {@code Ordered}
 * instance overriding a statically defined annotation value (if any).
 *
 * <p>Consult the Javadoc for {@link OrderComparator} for details on the
 * sort semantics for non-ordered objects.
 *
 * @author Juergen Hoeller
 * @author Oliver Gierke
 * @author Stephane Nicoll
 * @since 2.0.1
 * @see org.springframework.core.Ordered
 * @see org.springframework.core.annotation.Order
 * @see jakarta.annotation.Priority
 */
public class AnnotationAwareOrderComparator extends OrderComparator {

	/**
	 * Shared default instance of {@code AnnotationAwareOrderComparator}.
	 */
	public static final AnnotationAwareOrderComparator INSTANCE = new AnnotationAwareOrderComparator();


	/**
	 * This implementation checks for {@link Order @Order} or
	 * {@link jakarta.annotation.Priority @Priority} on various kinds of
	 * elements, in addition to the {@link org.springframework.core.Ordered}
	 * check in the superclass.
	 */
	@Override
	@Nullable
	protected Integer findOrder(Object obj) {
		Integer order = super.findOrder(obj);
		if (order != null) {
			return order;
		}
		return findOrderFromAnnotation(obj);
	}

	@Nullable
	private Integer findOrderFromAnnotation(Object obj) {
		AnnotatedElement element = (obj instanceof AnnotatedElement ? (AnnotatedElement) obj : obj.getClass());
		MergedAnnotations annotations = MergedAnnotations.from(element, SearchStrategy.TYPE_HIERARCHY);
		Integer order = OrderUtils.getOrderFromAnnotations(element, annotations);
		if (order == null && obj instanceof DecoratingProxy) {
			return findOrderFromAnnotation(((DecoratingProxy) obj).getDecoratedClass());
		}
		return order;
	}

	/**
	 * This implementation retrieves an @{@link jakarta.annotation.Priority}
	 * value, allowing for additional semantics over the regular @{@link Order}
	 * annotation: typically, selecting one object over another in case of
	 * multiple matches but only one object to be returned.
	 */
	@Override
	@Nullable
	public Integer getPriority(Object obj) {
		if (obj instanceof Class) {
			return OrderUtils.getPriority((Class<?>) obj);
		}
		Integer priority = OrderUtils.getPriority(obj.getClass());
		if (priority == null  && obj instanceof DecoratingProxy) {
			return getPriority(((DecoratingProxy) obj).getDecoratedClass());
		}
		return priority;
	}


	/**
	 * 根据{@link org.springframework.core.Ordered}接口和{@link Order}注解排序，
	 * 如果实现了{@link org.springframework.core.PriorityOrdered}接口，优先使用{@link PriorityOrdered#getOrder()}的定义
	 *
	 * 如果同时实现了{@link org.springframework.core.Ordered}接口，并使用了{@link Order}注解
	 * 优先使用{@link Ordered#getOrder()}的定义
	 *
	 * 优先级：{@link PriorityOrdered} > {@link Ordered} > {@link Order}
	 *
	 * Sort the given list with a default {@link AnnotationAwareOrderComparator}.
	 * 使用默认的{@link AnnotationAwareOrderComparator}对给定的列表进行排序
	 *
	 * <p>Optimized to skip sorting for lists with size 0 or 1,
	 * in order to avoid unnecessary array extraction.
	 * 优化为跳过大小为0或1的列表排序，以避免不必要的数组提取。
	 *
	 * @param list the List to sort
	 * @see java.util.List#sort(java.util.Comparator)
	 */
	public static void sort(List<?> list) {
		if (list.size() > 1) {
			list.sort(INSTANCE);
		}
	}

	/**
	 * Sort the given array with a default AnnotationAwareOrderComparator.
	 * <p>Optimized to skip sorting for lists with size 0 or 1,
	 * in order to avoid unnecessary array extraction.
	 * @param array the array to sort
	 * @see java.util.Arrays#sort(Object[], java.util.Comparator)
	 */
	public static void sort(Object[] array) {
		if (array.length > 1) {
			Arrays.sort(array, INSTANCE);
		}
	}

	/**
	 * Sort the given array or List with a default AnnotationAwareOrderComparator,
	 * if necessary. Simply skips sorting when given any other value.
	 * <p>Optimized to skip sorting for lists with size 0 or 1,
	 * in order to avoid unnecessary array extraction.
	 * @param value the array or List to sort
	 * @see java.util.Arrays#sort(Object[], java.util.Comparator)
	 */
	public static void sortIfNecessary(Object value) {
		if (value instanceof Object[]) {
			sort((Object[]) value);
		}
		else if (value instanceof List) {
			sort((List<?>) value);
		}
	}

}
