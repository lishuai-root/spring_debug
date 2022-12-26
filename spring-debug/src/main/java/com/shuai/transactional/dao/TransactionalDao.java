package com.shuai.transactional.dao;

import com.shuai.transactional.exception.BException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/18 17:19
 * @version: 1.0
 */

@Service
public class TransactionalDao {

	@Autowired
	JdbcTemplate template ;

	public JdbcTemplate getTemplate() {
		return template;
	}

	public void setTemplate(JdbcTemplate template) {
		this.template = template;
	}

	public int updateOne(String sql){
		try {
			System.out.println("transactionalDao : " + template.getDataSource().getConnection());
		}catch (Exception e){
			System.out.println(e.getMessage());
		}
		return template.update(sql);
	}

	public List<Map<String, Object>> queryAll(String sql) {
		return template.queryForList(sql);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public int updateForTransactional_New(String sql){
		return template.update(sql);
	}

	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = BException.class)
	public int updateForTransactional_REQUIRED(String sql) throws Exception {
//		return template.update(sql);
		int update = template.update(sql);
		throw new Exception();
	}
}
