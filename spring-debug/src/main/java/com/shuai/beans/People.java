package com.shuai.beans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2021/12/4 20:28
 * @version: 1.0
 */

@Service
public class People {

	String name;

	String email;

	String password;

	@Autowired
	Driver driverCar;

	@Autowired
	Apple apple;

	@Autowired
	Address address;

	public People(){
		System.out.println("people Instantiation.");
	}

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

	public Driver getDriverCar() {
		return driverCar;
	}

	public void setDriverCar(Driver driverCar) {
		this.driverCar = driverCar;
	}

	public Apple getApple() {
		return apple;
	}

	public void setApple(Apple apple) {
		this.apple = apple;
	}

	public Address getAddress() {
		return address;
	}
}
