package com.shuai.common;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/27 23:46
 * @version: 1.0
 */

//@Data
//@Component
public class User {

	private static int count = 0;

	public User(){
		System.out.println("user : " + count++);
	}

	String username;

	String password;

	int age;

	String email;

	String phoneNumber;

	String address;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}
}
