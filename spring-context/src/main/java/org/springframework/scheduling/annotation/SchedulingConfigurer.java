/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.scheduling.annotation;

import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Optional interface to be implemented by {@link
 * org.springframework.context.annotation.Configuration @Configuration} classes annotated
 * with {@link EnableScheduling @EnableScheduling}. Typically used for setting a specific
 * {@link org.springframework.scheduling.TaskScheduler TaskScheduler} bean to be used when
 * executing scheduled tasks or for registering scheduled tasks in a <em>programmatic</em>
 * fashion as opposed to the <em>declarative</em> approach of using the
 * {@link Scheduled @Scheduled} annotation. For example, this may be necessary
 * when implementing {@link org.springframework.scheduling.Trigger Trigger}-based
 * tasks, which are not supported by the {@code @Scheduled} annotation.
 *
 * 由{@link EnableScheduling @EnableScheduling}标注的{@link org.springframework.context.annotation.Configuration @Configuration}类实现的可选接口。
 * 通常用于设置执行计划任务时使用的特定{@link org.springframe .scheduling.TaskScheduler TaskScheduler} bean，
 * 或用于以<em>编程式<em>方式注册计划任务，而不是使用{@link scheduled @Scheduled}注释的<em>声明式<em>方法。
 * 例如，在实现{@code @Scheduled}注释不支持的基于触发器}的任务时，这可能是必要的。
 *
 * <p>See {@link EnableScheduling @EnableScheduling} for detailed usage examples.
 *
 * @author Chris Beams
 * @since 3.1
 * @see EnableScheduling
 * @see ScheduledTaskRegistrar
 */
@FunctionalInterface
public interface SchedulingConfigurer {

	/**
	 * Callback allowing a {@link org.springframework.scheduling.TaskScheduler
	 * TaskScheduler} and specific {@link org.springframework.scheduling.config.Task Task}
	 * instances to be registered against the given the {@link ScheduledTaskRegistrar}.
	 *
	 * 回调允许{@link org.springframework.scheduling.TaskScheduler}和特定的{@link org.springframework.scheduling.config.Task Task}实例针对给定的{@link ScheduledTaskRegistrar}注册。
	 *
	 * @param taskRegistrar the registrar to be configured.
	 */
	void configureTasks(ScheduledTaskRegistrar taskRegistrar);

}
