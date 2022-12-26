package com.shuai.transactional.configuration;

import com.shuai.transactional.util.A;
import com.shuai.transactional.util.B;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/18 17:39
 * @version: 1.0
 */

@Configuration
@PropertySource(value = "classpath:spring_db.properties")
@ComponentScan({"com.shuai.transactional"})
@EnableTransactionManagement
public class JDBCConfiguratin {

	@Value("${jdbc.username}")
	String username;

	@Value("${jdbc.password}")
	String password;

	@Value("${jdbc.url}")
	String url;

	@Value("${jdbc.driverClassName}")
	String driverClassName;

	@Bean("dataSource")
	public DataSource getDataSource(){
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(driverClassName);
		dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setUrl(url);
        return dataSource;
	}

	@Bean("jdbcTemplate")
	public JdbcTemplate getJdbcTemplate(DataSource dataSource){
		return new JdbcTemplate(dataSource);
	}

	@Bean("transactionManager")
	public TransactionManager getTransactionManager(DataSource dataSource){
		return new DataSourceTransactionManager(dataSource);
	}

	@Bean
	public TransactionManagementConfigurer transactionManagementConfigurer() {
		return new TransactionManagementConfigurer() {
			@Override
			public TransactionManager annotationDrivenTransactionManager() {
				return getTransactionManager(getDataSource());
			}
		};
	}

	@Bean
	public A a(){
		b();
		return new A();
	}

	@Bean
	public B b(){
//		a();
        return new B();
    }

    @Bean("connection")
	public Connection getConnection() throws SQLException {
		return DriverManager.getConnection(url, username, password);
	}
}
