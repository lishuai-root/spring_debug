package com.shuai.aop;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/13 23:58
 * @version: 1.0
 */

@Component
public class MySchedulingConfigurer implements SchedulingConfigurer {

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(8);
		taskRegistrar.setScheduler(executor);
	}
}
