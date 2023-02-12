package com.shuai.config.initializer;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

import java.util.Date;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/2/12 21:47
 * @version: 1.0
 */

public class MyCustomEditorRegistry implements PropertyEditorRegistrar {

	@Override
	public void registerCustomEditors(PropertyEditorRegistry registry) {
		System.out.println("MyCustomEditorRegistry : registry");
		registry.registerCustomEditor(Date.class, new MyCustomDateEditor());
	}
}
