package com.shuai.configuration;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/8 9:50
 * @version: 1.0
 */

public class MyDeferredImportSelector implements DeferredImportSelector {


	@Override
	public Class<? extends Group> getImportGroup() {
		return DeferredImportSelector.super.getImportGroup();
	}

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		return new String[0];
	}
}
