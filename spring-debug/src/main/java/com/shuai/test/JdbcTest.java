package com.shuai.test;

import com.shuai.aop.MyConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/9 16:01
 * @version: 1.0
 */

public class JdbcTest {
	public static void main(String[] args) {
//		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring_debug.xml");
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);
		JdbcTemplate template = context.getBean(JdbcTemplate.class);
		List<Map<String, Object>> list = template.queryForList("SELECT * FROM SPRING_FRAMEWORK_DEBUG.debug_user");
		for (Map<String,Object> map:list){
			for (String key:map.keySet()){
				System.out.print(key+" : "+map.get(key) + ", ");
			}
			System.out.println();
		}
	}
}
