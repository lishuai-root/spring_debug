package com.shuai.beans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/7/21 22:44
 * @version: 1.0
 */


@Service
public class BigPeople {
	String nameStr;

	@Autowired
	Address address;

	public String getNameStr() {
		return nameStr;
	}

	public void setNameStr(String nameStr) {
		this.nameStr = nameStr;
	}
}
