package com.shuai.beans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/8/6 20:06
 * @version: 1.0
 */

@Service
public class DriverCar implements Driver{

	@Autowired
	Apple apple;

	@Override
	public Object driver() {
		System.out.println("driver car!");
		return null;
	}
}
