package com.shuai.order;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/29 21:07
 * @version: 1.0
 */

@Order(2)
public class MyOrdered implements Ordered {
	@Override
	public int getOrder() {
		return 10;
	}
}
