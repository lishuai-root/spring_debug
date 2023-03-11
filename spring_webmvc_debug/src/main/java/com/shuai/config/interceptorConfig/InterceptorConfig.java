package com.shuai.config.interceptorConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/1/8 21:14
 * @version: 1.0
 */

@Configuration
//@EnableWebMvc
public class InterceptorConfig implements WebMvcConfigurer {

//	public InterceptorConfig(){}
//
//	@Override
//	public void addInterceptors(InterceptorRegistry registry) {
//		InterceptorRegistration interceptor = registry.addInterceptor(new AnnotationInterceptor());
//	}

	@Bean
	public WebMvcConfigurer webMvcConfigurer(){
		return new WebMvcConfigurer() {
			@Override
			public void configurePathMatch(PathMatchConfigurer configurer) {
				UrlPathHelper urlPathHelper = new UrlPathHelper();
				// 设置不移除分号 ；后面的内容。矩阵变量功能就可以生效
				urlPathHelper.setRemoveSemicolonContent(false);
				configurer.setUrlPathHelper(urlPathHelper);
			}
		};
	}
}
