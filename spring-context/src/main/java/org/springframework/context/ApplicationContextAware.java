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

package org.springframework.context;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;

/**
 * Interface to be implemented by any object that wishes to be notified
 * of the {@link ApplicationContext} that it runs in.
 * 接口，由任何对象实现，该对象希望得到运行它的{@link ApplicationContext}的通知。
 *
 * <p>Implementing this interface makes sense for example when an object
 * requires access to a set of collaborating beans. Note that configuration
 * via bean references is preferable to implementing this interface just
 * for bean lookup purposes.
 * 例如，当一个对象需要访问一组协作bean时，实现这个接口是有意义的。
 * 注意，为了bean查找目的，通过bean引用进行配置比实现此接口更好。
 *
 * <p>This interface can also be implemented if an object needs access to file
 * resources, i.e. wants to call {@code getResource}, wants to publish
 * an application event, or requires access to the MessageSource. However,
 * it is preferable to implement the more specific {@link ResourceLoaderAware},
 * {@link ApplicationEventPublisherAware} or {@link MessageSourceAware} interface
 * in such a specific scenario.
 * 如果对象需要访问文件资源，也可以实现此接口，例如，想要调用{@code getResource}，想要发布应用程序事件，或需要访问MessageSource。
 * 然而，在这样一个特定的场景中，最好实现更具体的{@link ResourceLoaderAware}、{@link ApplicationEventPublisherAware}或{@link MessageSourceAware}接口。
 *
 *
 * <p>Note that file resource dependencies can also be exposed as bean properties
 * of type {@link org.springframework.core.io.Resource}, populated via Strings
 * with automatic type conversion by the bean factory. This removes the need
 * for implementing any callback interface just for the purpose of accessing
 * a specific file resource.
 * 注意，文件资源依赖关系也可以公开为类型为{@link org.springframework.core.io.Resource}，由bean工厂使用自动类型转换通过字符串填充。
 * 这样就不需要为了访问特定的文件资源而实现任何回调接口。
 *
 *
 * <p>{@link org.springframework.context.support.ApplicationObjectSupport} is a
 * convenience base class for application objects, implementing this interface.
 * {@link org.springframework.context.support.ApplicationObjectSupport}是应用程序对象的一个方便的基类，实现了这个接口。
 *
 * <p>For a list of all bean lifecycle methods, see the
 * {@link org.springframework.beans.factory.BeanFactory BeanFactory javadocs}.
 * 有关所有bean生命周期方法的列表，请参阅{@link org.springframework.beans.factory.BeanFactory javadocs}。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see ResourceLoaderAware
 * @see ApplicationEventPublisherAware
 * @see MessageSourceAware
 * @see org.springframework.context.support.ApplicationObjectSupport
 * @see org.springframework.beans.factory.BeanFactoryAware
 */
public interface ApplicationContextAware extends Aware {

	/**
	 * Set the ApplicationContext that this object runs in.
	 * 设置该对象运行的ApplicationContext。
	 *
	 * Normally this call will be used to initialize the object.
	 * 通常，此调用将用于初始化对象。
	 *
	 * <p>Invoked after population of normal bean properties but before an init callback such
	 * as {@link org.springframework.beans.factory.InitializingBean#afterPropertiesSet()}
	 * or a custom init-method. Invoked after {@link ResourceLoaderAware#setResourceLoader},
	 * {@link ApplicationEventPublisherAware#setApplicationEventPublisher} and
	 * {@link MessageSourceAware}, if applicable.
	 * 在填充普通bean属性之后，但在初始化回调(如{@link org.springframework.beans.factory.InitializingBean#afterPropertiesSet()}或自定义初始化方法之前调用。
	 * 在{@link ResourceLoaderAware#setResourceLoader}， {@link ApplicationEventPublisherAware#setApplicationEventPublisher}和{@link MessageSourceAware}之后调用，如果适用的话。
	 *
	 * @param applicationContext the ApplicationContext object to be used by this object
	 * @throws ApplicationContextException in case of context initialization errors
	 * @throws BeansException if thrown by application context methods
	 * @see org.springframework.beans.factory.BeanInitializationException
	 */
	void setApplicationContext(ApplicationContext applicationContext) throws BeansException;

}
