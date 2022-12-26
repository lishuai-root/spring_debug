package com.shuai.transactional.configuration;

import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/18 18:31
 * @version: 1.0
 */

public class MyTransactionManagementConfigurer implements TransactionManagementConfigurer {
	@Override
	public TransactionManager annotationDrivenTransactionManager() {
		return null;
	}
}
