package com.shuai.config.initializer;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/2/12 21:47
 * @version: 1.0
 */

public class MyCustomEditorRegistry implements PropertyEditorRegistrar, Serializable {

	@Serial
	private static final long serialVersionUID = 5227804423701996095L;

	@Override
	public void registerCustomEditors(PropertyEditorRegistry registry) {
//		System.out.println("MyCustomEditorRegistry : registry");
		registry.registerCustomEditor(Date.class, new MyCustomDateEditor());
	}
}
