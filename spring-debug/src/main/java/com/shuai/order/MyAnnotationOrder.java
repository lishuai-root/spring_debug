package com.shuai.order;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/29 21:09
 * @version: 1.0
 */

@Order(1)
public class MyAnnotationOrder implements Ordered {
	@Override
	public int getOrder() {
		return 15;
	}
}
