package com.shuai.config;

import com.shuai.config.initializer.MyCustomEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.factory.config.CustomEditorConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/2/12 22:06
 * @version: 1.0
 */

@Configuration
public class BeansConfiguration {

	@Bean
	public static CustomEditorConfigurer editorConfigurer() {
		CustomEditorConfigurer customEditorConfigurer = new CustomEditorConfigurer();
		customEditorConfigurer.setPropertyEditorRegistrars(new PropertyEditorRegistrar[]{new MyCustomEditorRegistry()});
		return customEditorConfigurer;
	}
}
