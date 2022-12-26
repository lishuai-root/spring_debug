package com.shuai.aop;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/13 23:58
 * @version: 1.0
 */

@Configuration
public class MySchedulingConfigurer implements SchedulingConfigurer {
	private static int threadPoolSize = 0;

	public static List<Object> objectList = new ArrayList<Object>();
	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
//		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(8);
//		taskRegistrar.setScheduler(executor);
	}

	@Bean
	public Executor getScheduledThreadPoolExecutor(){
		return new ScheduledThreadPoolExecutor(8,r -> {
			Thread thread = new Thread(r);
			thread.setName("scheduling" + " - " + (++threadPoolSize));
			return thread;
		});
	}

	@Bean(name = "myFactoryBean")
	public FactoryBean<MyBean> myBean(String userName, int age){
		return new MyFactoryBean();
	}


	@Bean
	public MyBeanProxy myBeanProxy() throws Exception {
		FactoryBean<MyBean> factoryBean = myBean("---", 24);
		objectList.add(factoryBean);
		MyBean object = factoryBean.getObject();
		return new MyBeanProxy();
	}

	@Bean
	public MyBeanProxy myBeanProxy2() throws Exception {
		FactoryBean<MyBean> factoryBean = myBean("---", 24);
		objectList.add(factoryBean);
		MyBean object = factoryBean.getObject();
		return new MyBeanProxy();
	}

	@Bean
	public MyBeanProxy myBeanProxy3() throws Exception {
		FactoryBean<MyBean> factoryBean = myBean("---", 24);
		objectList.add(factoryBean);
		MyBean object = factoryBean.getObject();
		return new MyBeanProxy();
	}



	@Bean
	public String userName(){
		return "lishuai";
	}

	@Bean
	public int age(){
		return 24;
	}
}
