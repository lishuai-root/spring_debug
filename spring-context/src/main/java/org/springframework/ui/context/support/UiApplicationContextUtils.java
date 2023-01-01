/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ui.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.ui.context.HierarchicalThemeSource;
import org.springframework.ui.context.ThemeSource;

/**
 * Utility class for UI application context implementations.
 * Provides support for a special bean named "themeSource",
 * of type {@link org.springframework.ui.context.ThemeSource}.
 *
 * @author Jean-Pierre Pawlak
 * @author Juergen Hoeller
 * @since 17.06.2003
 */
public abstract class UiApplicationContextUtils {

	/**
	 * Name of the ThemeSource bean in the factory.
	 * If none is supplied, theme resolution is delegated to the parent.
	 * 工厂中的ThemeSource bean的名称。如果没有提供，则将主题解析委托给父对象。
	 *
	 * @see org.springframework.ui.context.ThemeSource
	 */
	public static final String THEME_SOURCE_BEAN_NAME = "themeSource";


	private static final Log logger = LogFactory.getLog(UiApplicationContextUtils.class);


	/**
	 * Initialize the ThemeSource for the given application context,
	 * autodetecting a bean with the name "themeSource". If no such
	 * bean is found, a default (empty) ThemeSource will be used.
	 * 初始化给定应用程序上下文的ThemeSource，自动检测名称为“ThemeSource”的bean。如果没有找到这样的bean，将使用默认的(空的)ThemeSource。
	 *
	 * @param context current application context
	 * @return the initialized theme source (will never be {@code null})
	 * @see #THEME_SOURCE_BEAN_NAME
	 */
	public static ThemeSource initThemeSource(ApplicationContext context) {
		/**
		 * 首先在工厂中查找名字为"themeSource"的bean
		 */
		if (context.containsLocalBean(THEME_SOURCE_BEAN_NAME)) {
			/**
			 * 获取用户指定的名称为"themeSource"的主题来源
			 */
			ThemeSource themeSource = context.getBean(THEME_SOURCE_BEAN_NAME, ThemeSource.class);
			// Make ThemeSource aware of parent ThemeSource.
			/**
			 * 使ThemeSource意识到父ThemeSource。
			 *
			 * 如果需要为当前主题来源设置父主题来源
			 */
			if (context.getParent() instanceof ThemeSource && themeSource instanceof HierarchicalThemeSource) {
				HierarchicalThemeSource hts = (HierarchicalThemeSource) themeSource;
				if (hts.getParentThemeSource() == null) {
					// Only set parent context as parent ThemeSource if no parent ThemeSource
					// registered already.
					/**
					 * 如果没有注册父ThemeSource，则只将父上下文设置为父ThemeSource。
					 */
					hts.setParentThemeSource((ThemeSource) context.getParent());
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Using ThemeSource [" + themeSource + "]");
			}
			return themeSource;
		}
		else {
			// Use default ThemeSource to be able to accept getTheme calls, either
			// delegating to parent context's default or to local ResourceBundleThemeSource.
			/**
			 * 使用默认的ThemeSource能够接受getTheme调用，可以委托给父上下文的默认值，也可以委托给本地ResourceBundleThemeSource。
			 */
			HierarchicalThemeSource themeSource = null;
			if (context.getParent() instanceof ThemeSource) {
				themeSource = new DelegatingThemeSource();
				themeSource.setParentThemeSource((ThemeSource) context.getParent());
			}
			else {
				themeSource = new ResourceBundleThemeSource();
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate ThemeSource with name '" + THEME_SOURCE_BEAN_NAME +
						"': using default [" + themeSource + "]");
			}
			return themeSource;
		}
	}

}
