package com.shuai.transactional.util;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/18 17:30
 * @version: 1.0
 */


public class JDBCUtil {

	private static final String INSERT_ONE_SQL = "";
	private static final String UPDATE_ONE_SQL = "UPDATE SPRING_FRAMEWORK_DEBUG.DEBUG_USER SET LOGINNAME = 'lishuaia' WHERE LOGINNAME = 'lishuai'";
	private static final String QUERY_ALL_SQL = "SELECT * FROM SPRING_FRAMEWORK_DEBUG.DEBUG_USER";
	public static final String UPDATE_ERROR_SQL = "UPDATE SPRING_FRAMEWORK_DEBUG.DEBUG_USER SET LOGINNAME = 'lishuaiaaaaab' WHERE LOGINNAME = 'huihui7'";
	public static final String UPDATE_OK_SQL = "UPDATE SPRING_FRAMEWORK_DEBUG.DEBUG_USER SET limitLoginDuration = limitLoginDuration + 1 WHERE LOGINNAME = 'huihui7'";

	public static String getInsertOneSql() {
		return INSERT_ONE_SQL;
	}

	public static String getUpdateOneSql() {
		return UPDATE_ONE_SQL;
	}

	public static String getQueryAllSql() {
		return QUERY_ALL_SQL;
	}
}
