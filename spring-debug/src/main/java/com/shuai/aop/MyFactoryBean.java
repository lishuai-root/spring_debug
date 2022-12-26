package com.shuai.aop;

import org.springframework.beans.factory.FactoryBean;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/15 9:45
 * @version: 1.0
 */

public class MyFactoryBean implements FactoryBean<MyBean> {
	@Override
	public MyBean getObject() throws Exception {
		return new MyBean();
	}

	@Override
	public Class<?> getObjectType() {
		return MyBean.class;
	}
}
