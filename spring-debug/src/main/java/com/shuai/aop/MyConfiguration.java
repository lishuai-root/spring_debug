package com.shuai.aop;

import jakarta.annotation.Resource;
import jakarta.annotation.Resources;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/15 18:46
 * @version: 1.0
 */

@Configuration
@PropertySource(value = "classpath:spring_db.properties")
@ComponentScan({"com.shuai.aop"})
@EnableAspectJAutoProxy
public class MyConfiguration {

	@Value("${jdbc.username}")
	String username;

	@Value("${jdbc.password}")
	String password;

	@Value("${jdbc.url}")
    String url;

	@Value("${jdbc.driverClassName}")
    String driverClassName;

	@Bean
	public DataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(driverClassName);
		dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setUrl(url);
        return dataSource;
	}

	@Bean
	public JdbcTemplate jdbcTemplate(){
		JdbcTemplate template = new JdbcTemplate();
		template.setDataSource(dataSource());
        return template;
	}

	@Test
	public void springAnnotationTest(){
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(this.getClass());
		JdbcTemplate template = context.getBean(JdbcTemplate.class);
		List<Map<String, Object>> list = template.queryForList("SELECT * FROM BASEDB.base_user");
		for (Map<String,Object> map:list){
			for (String key:map.keySet()){
				System.out.print(key+" : "+map.get(key) + ", ");
			}
			System.out.println();
		}
	}
}
