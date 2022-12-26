package com.shuai.aop;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/7 9:28
 * @version: 1.0
 */

@Component
@EnableScheduling
@Configuration
public class NoProxyClass {

	@Scheduled(cron="0/5 * * * * ? ")
	public void show(){
		System.out.println("NoProxyClass scheduled.");
	}

	@Scheduled(fixedDelay=1)
	public void show1() throws InterruptedException {
		System.out.println(Thread.currentThread()+" - 1");
		Thread.sleep(3000);
	}

	@Scheduled(fixedDelay=1)
	public void show2() throws InterruptedException {
		System.out.println(Thread.currentThread()+" - 2");
		Thread.sleep(3000);
	}

	@Scheduled(fixedDelay=1)
	public void show3() throws InterruptedException {
		System.out.println(Thread.currentThread()+" - 3");
		Thread.sleep(3000);
	}

	@Scheduled(fixedDelay=1)
	public void show4() throws InterruptedException {
		System.out.println(Thread.currentThread()+" - 4");
		Thread.sleep(3000);
	}

	@Scheduled(fixedDelay=1)
	public void show5() throws InterruptedException {
		System.out.println(Thread.currentThread()+" - 5");
		Thread.sleep(3000);
	}
}
