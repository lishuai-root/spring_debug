package com.shuai.order;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/29 21:09
 * @version: 1.0
 */

public class OrderTest {

	public static void main(String[] args) {
		List<Object> list = new ArrayList<>(4);
		list.add(new MyAnnotationOrder());
		list.add(new MyOrdered());
		list.add(new MyPriorityOrdered());
		System.out.println("-----------------before ordered-----------------");
		for (Object o : list) {
			System.out.println(o.getClass().getName());
		}
		AnnotationAwareOrderComparator.sort(list);
		System.out.println("-----------------after ordered-----------------");
		for (Object o : list) {
			System.out.println(o.getClass().getName());
		}
	}
}
