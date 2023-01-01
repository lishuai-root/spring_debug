package com.shuai.order;

import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.Order;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/29 21:08
 * @version: 1.0
 */

@Order(3)
public class MyPriorityOrdered implements PriorityOrdered {
	@Override
	public int getOrder() {
		return 20;
	}
}
