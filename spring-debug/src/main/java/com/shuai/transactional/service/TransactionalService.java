package com.shuai.transactional.service;

import com.shuai.transactional.dao.TransactionalDao;
import com.shuai.transactional.exception.AException;
import com.shuai.transactional.util.JDBCUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/18 17:18
 * @version: 1.0
 */

@Service
public class TransactionalService {

	@Autowired
	TransactionalDao transactionalDao;


	@Autowired
	DataSource dataSource;

	public TransactionalDao getTransactionalDao() {
		return transactionalDao;
	}

	public void setTransactionalDao(TransactionalDao transactionalDao) {
		this.transactionalDao = transactionalDao;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public int updateOne(String sql){
		return transactionalDao.updateOne(sql);
	}

	public List<Map<String, Object>> queryAll(String sql){
		return transactionalDao.queryAll(sql);
	}

//	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void updateForTransactional(){
		transactionalDao.updateOne("UPDATE SPRING_FRAMEWORK_DEBUG.DEBUG_USER SET LOGINNAME = 'huihui8' WHERE LOGINNAME = 'huihui9'");
		transactionalDao.updateOne("UPDATE SPRING_FRAMEWORK_DEBUG.DEBUG_USER SET LOGINNAME = 'lishuaia' WHERE LOGINNAME = 'lishuai'");
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void updateForTransactional_New(){
		transactionalDao.updateForTransactional_New(JDBCUtil.UPDATE_ERROR_SQL);
		System.out.println("------------");
		transactionalDao.updateForTransactional_New(JDBCUtil.UPDATE_OK_SQL);

	}

	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = AException.class)
	public void updateForTransactional_REQUIRED() throws Exception {
		transactionalDao.updateForTransactional_REQUIRED(JDBCUtil.UPDATE_OK_SQL);
		transactionalDao.updateForTransactional_REQUIRED(JDBCUtil.UPDATE_OK_SQL);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void newConnectTest() {
		Connection connection = DataSourceUtils.getConnection(dataSource);
		System.out.println(connection);
		connection = DataSourceUtils.getConnection(dataSource);
		System.out.println(connection);
		connection = DataSourceUtils.getConnection(dataSource);
		System.out.println(connection);
		connection = DataSourceUtils.getConnection(dataSource);
		System.out.println(connection);
	}

}
