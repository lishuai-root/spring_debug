package com.shuai.aop;

import com.shuai.beans.People;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;

import java.util.Arrays;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/7/3 22:35
 * @version: 1.0
 */

public class ProxyClass {

	public String before(JoinPoint joinPoint){
		System.out.println(joinPoint.getSignature().getName() + " : proxy before!");
		return "before";
	}

	public void after(JoinPoint joinPoint){
		System.out.println(joinPoint.getSignature().getName() + " : proxy after!");
	}

	public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
		System.out.println("this class : " + joinPoint.getThis());
		Signature signature = joinPoint.getSignature();
		Object[] args = joinPoint.getArgs();
		Object result = null;

		try {
			System.out.println("around start, executing method : " + signature.getName() + ", arguments : " + Arrays.asList(args));
			result = joinPoint.proceed(args);
			System.out.println("around over, executed over method : " + signature.getName() + ", result : " + result);
		} catch (Throwable throwable) {
			System.out.println("around executed error, method : " + signature.getName());
//			throwable.printStackTrace();
			throw throwable;
		}finally {
			System.out.println("around executed end, method : " + signature.getName());
		}
		return result;
	}

	public Object returning(JoinPoint joinPoint, Object result){
		Object obj = new Object();
		System.out.println(joinPoint.getSignature().getName() + " : proxy after-returning! result : " + result);
		return 100;
	}

	public void throwing(JoinPoint joinPoint, Exception e){
		System.out.println(joinPoint.getSignature().getName() + " : proxy after-throwing! throw message : " + e.getMessage());
	}
}
