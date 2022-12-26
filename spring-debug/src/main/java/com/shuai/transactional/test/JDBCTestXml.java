package com.shuai.transactional.test;

import com.shuai.transactional.service.TransactionalService;
import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/18 22:03
 * @version: 1.0
 */

public class JDBCTestXml {

	public static void main(String[] args) {
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "target/cglib");
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring_db.xml");
		TransactionalService service = context.getBean(TransactionalService.class);
//		service.updateForTransactional();
		Connection connection = null;
		try {
			connection = context.getBean(JdbcTemplate.class).getDataSource().getConnection();
			connection.setAutoCommit(false);
			service.updateForTransactional();
			connection.commit();
		} catch (SQLException throwables) {
			try {
				if (connection != null){
					connection.rollback();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			throwables.printStackTrace();
		}
	}
}
