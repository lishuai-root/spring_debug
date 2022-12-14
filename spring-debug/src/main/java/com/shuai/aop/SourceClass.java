package com.shuai.aop;

import com.shuai.beans.People;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Random;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/7/3 22:35
 * @version: 1.0
 */


public class SourceClass implements JdkProxy{

	public int add(int a, int b){
		return a + b;
	}

	public String getStr(String str){
		return str + ", base over!";
	}

	public Object getObj(){
		return new Object();
	}

	public char[] getChars(int size){
		char[] chars = new char[size];
		Random random = new Random();
		for (int i = 0; i < size; i++) {
			chars[i] = (char)(random.nextInt(26) + 'a');
		}
		return chars;
	}

	public void show(){
		System.out.println("show!");
	}


	public int chu(int m, int n) {
		System.out.println("chu : [" + m + ", " + n + "]");
		return m / n;
	}
}
