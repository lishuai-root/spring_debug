package com.shuai.transactional.test;

import com.shuai.transactional.configuration.JDBCConfiguratin;
import com.shuai.transactional.service.TransactionalService;
import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/18 17:36
 * @version: 1.0
 */

public class JDBCTest {

	private static final AbstractApplicationContext context;

	static {
		context = new AnnotationConfigApplicationContext(JDBCConfiguratin.class);
	}

	public static void main(String[] args) throws Exception {
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "target/cglib");
		TransactionalService transactionalService = context.getBean(TransactionalService.class);
//		List<Map<String, Object>> mapList = transactionalService.queryAll(JDBCUtil.getQueryAllSql());
//		for(Map<String, Object> map : mapList){
//			for (String key:map.keySet()){
//				System.out.print(key+" : " + map.get(key)+", ");
//			}
//			System.out.println();
//		}
//		transactionalService.updateForTransactional();
//		transactionalService.updateForTransactional_New();
		transactionalService.updateForTransactional_REQUIRED();
	}
}
