package com.shuai.config.interceptorConfig;

import com.shuai.interceptor.AnnotationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/8 21:14
 * @version: 1.0
 */

@Configuration
//@EnableWebMvc
public class InterceptorConfig implements WebMvcConfigurer {

	public InterceptorConfig(){}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		InterceptorRegistration interceptor = registry.addInterceptor(new AnnotationInterceptor());
	}
}
