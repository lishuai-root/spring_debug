package com.shuai.beans;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2021/12/4 20:28
 * @version: 1.0
 */

public class People {

	String name;

	String email;

	String password;

	public People(){}

	public Object customerInitMethod(){
		System.out.println("people init method execute.");
		return this;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
