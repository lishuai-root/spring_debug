/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.context;

import org.springframework.beans.factory.Aware;

/**
 * Interface to be implemented by any object that wishes to be notified
 * of the MessageSource (typically the ApplicationContext) that it runs in.
 * 接口，由希望得到其运行的MessageSource(通常是ApplicationContext)通知的任何对象实现。
 *
 *
 * <p>Note that the MessageSource can usually also be passed on as bean
 * reference (to arbitrary bean properties or constructor arguments), because
 * it is defined as bean with name "messageSource" in the application context.
 * 注意，MessageSource通常也可以作为bean引用(传递给任意bean属性或构造函数参数)，因为它在应用程序上下文中被定义为名称为“MessageSource”的bean。
 *
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 1.1.1
 * @see ApplicationContextAware
 */
public interface MessageSourceAware extends Aware {

	/**
	 * Set the MessageSource that this object runs in.
	 * 设置运行此对象的MessageSource。
	 *
	 * <p>Invoked after population of normal bean properties but before an init
	 * callback like InitializingBean's afterPropertiesSet or a custom init-method.
	 * <p>在填充普通bean属性之后调用，但在初始化回调之前调用，如InitializingBean的afterPropertiesSet或自定义初始化方法。
	 *
	 * Invoked before ApplicationContextAware's setApplicationContext.
	 * 在ApplicationContextAware的setApplicationContext之前调用。
	 *
	 * @param messageSource message source to be used by this object
	 */
	void setMessageSource(MessageSource messageSource);

}
