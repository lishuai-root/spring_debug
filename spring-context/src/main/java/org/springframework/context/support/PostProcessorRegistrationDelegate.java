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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// WARNING: Although it may appear that the body of this method can be easily
		// refactored to avoid the use of multiple loops and multiple lists, the use
		// of multiple lists and multiple passes over the names of processors is
		// intentional. We must ensure that we honor the contracts for PriorityOrdered
		// and Ordered processors. Specifically, we must NOT cause processors to be
		// instantiated (via getBean() invocations) or registered in the ApplicationContext
		// in the wrong order.
		// 警告：虽然看起来这个方法的主体可以很容易地重构以避免使用多个循环和多个列表，
		// 但使用多个列表和多次遍历处理器名称是有意的。我们必须确保遵守 PriorityOrdered 和 Ordered 处理器的合同。
		// 具体来说，我们不能导致处理器被实例化（通过 getBean() 调用）或以错误的顺序在 ApplicationContext 中注册。
		//
		// Before submitting a pull request (PR) to change this method, please review the
		// list of all declined PRs involving changes to PostProcessorRegistrationDelegate
		// to ensure that your proposal does not result in a breaking change:
		// 在提交拉取请求 (PR) 以更改此方法之前，
		// 请查看所有涉及更改 PostProcessorRegistrationDelegate 的被拒绝 PR 的列表，以确保您的提案不会导致重大更改：
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		// 无论是什么情况，优先执行BeanDefinitionRegistryPostProcessors
		// 将已经执行过的BFPP存储在processedBeans中，防止重复执行
		Set<String> processedBeans = new HashSet<>();

		// 判断beanfactory是否是BeanDefinitionRegistry类型，此处是DefaultListableBeanFactory,实现了BeanDefinitionRegistry接口，所以为true
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

			/**
			 * BeanDefinitionRegistryPostProcessor是BeanFactoryPostProcessor的子接口
			 *
			 * BeanFactoryPostProcessor主要针对的操作对象是BeanFactory，
			 * BeanDefinitionRegistryPostProcessor主要针对的操作对象是BeanDefinition
			 */

			// 用来保存BeanFactoryPostProcessor的集合
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();

			// 用来保存BeanDefinitionRegistryPostProcessor的集合
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// 遍历传入的beanFactoryPostProcessors，将BeanDefinitionRegistryPostProcessor和BeanFactoryPostProcessor区分开，
			// 保存在不同的集合中
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				// 判断是否BeanDefinitionRegistryPostProcessor
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;

					// 如果是BeanDefinitionRegistryPostProcessor接口，直接执行postProcessBeanDefinitionRegistry方法
					registryProcessor.postProcessBeanDefinitionRegistry(registry);

					// 添加到registryProcessors中，后续继续执行postProcessBeanFactory方法
					registryProcessors.add(registryProcessor);
				}
				else {

					// 添加到regularPostProcessors中，后续执行postProcessBeanFactory方法
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 不要在此处初始化 FactoryBeans：我们需要保留所有常规 bean 未初始化，
			// 以便让 bean 工厂后处理器应用于它们！将实现 PriorityOrdered、Ordered 和其余部分的 BeanDefinitionRegistryPostProcessor 分开。

			// 用于保存本次要执行的BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 调用所有实现PriorityOrdered接口的BeanDefinitionRegistryPostProcessor实现类
			// 找到所有实现BeanDefinitionRegistryPostProcessor接口bean的beanName
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

			// 遍历处理所有符合规则的postProcessorNames
			for (String ppName : postProcessorNames) {

				// 检测是否实现了PriorityOrdered接口
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {

					// 获取名字对应的bean实例，添加到currentRegistryProcessors中
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));

					// 将要被执行的BFPP名称添加到processedBeans，避免后续重复执行
					processedBeans.add(ppName);
				}
			}

			// 按照优先级进行排序操作
			sortPostProcessors(currentRegistryProcessors, beanFactory);

			// 添加到registryProcessors中，用于最后执行postProcessBeanFactory方法
			registryProcessors.addAll(currentRegistryProcessors);

			// 遍历currentRegistryProcessors，执行postProcessBeanDefinitionRegistry方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());

			// 执行完毕之后，清空currentRegistryProcessors
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 调用所有实现Ordered接口的BeanDefinitionRegistryPostProcessor实现类
			// 找到所有实现BeanDefinitionRegistryPostProcessor接口bean的beanName，
			// 此处需要重复查找的原因在于上面的执行过程中可能会新增其他的BeanDefinitionRegistryPostProcessor
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {

				// 检测是否实现了Ordered接口，并且还未执行过
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {

					// 获取名字对应的bean实例，添加到currentRegistryProcessors中
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));

					// 将要被执行的BFPP名称添加到processedBeans，避免后续重复执行
					processedBeans.add(ppName);
				}
			}

			// 按照优先级进行排序操作
			sortPostProcessors(currentRegistryProcessors, beanFactory);

			// 添加到registryProcessors中，用于最后执行postProcessBeanFactory方法
			registryProcessors.addAll(currentRegistryProcessors);

			// 遍历currentRegistryProcessors，执行postProcessBeanDefinitionRegistry方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());

			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			// 最后，调用所有其他 BeanDefinitionRegistryPostProcessor 直到不再出现。
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;

				// 找出所有实现BeanDefinitionRegistryPostProcessor接口的类
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

				for (String ppName : postProcessorNames) {

					// 跳过已经执行过的BeanDefinitionRegistryPostProcessor
					if (!processedBeans.contains(ppName)) {

						// 获取名称对应的bean实例，并添加到currentRegistryProcessors中
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));

						// 将名称添加到processedBeans中，以免重复执行
						processedBeans.add(ppName);
						reiterate = true;
					}
				}

				// 按照优先级进行排序操作
				sortPostProcessors(currentRegistryProcessors, beanFactory);

				// 添加到registryProcessors中，用于最后执行postProcessBeanFactory方法
				registryProcessors.addAll(currentRegistryProcessors);

				// 遍历currentRegistryProcessors，执行postProcessBeanDefinitionRegistry方法
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// 调用所有BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);

			// 最后，调用入参beanFactoryPostProcessors中的普通BeanFactoryPostProcessor的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			// 如果beanFactory不归属于BeanDefinitionRegistry类型，那么直接执行postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// 到这里为止，入参beanFactoryPostProcessors和容器中的所有BeanDefinitionRegistryPostProcessor已经全部处理完毕，下面开始处理容器中
		// 所有的BeanFactoryPostProcessor
		// 可能会包含一些实现类，只实现了BeanFactoryPostProcessor，并没有实现BeanDefinitionRegistryPostProcessor接口

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 找到所有实现BeanFactoryPostProcessor接口的类
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.

		// 用于存放实现了PriorityOrdered接口的BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();

		// 用于存放实现了Ordered接口的BeanFactoryPostProcessor的beanName
		List<String> orderedPostProcessorNames = new ArrayList<>();

		// 用于存放普通BeanFactoryPostProcessor的beanName
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();

		for (String ppName : postProcessorNames) {

			// 跳过已经执行过的BeanFactoryPostProcessor
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}

			// 添加实现了PriorityOrdered接口的BeanFactoryPostProcessor到priorityOrderedPostProcessors
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}

			// 添加实现了Ordered接口的BeanFactoryPostProcessor的beanName到orderedPostProcessorNames
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}

			// 添加剩下的普通BeanFactoryPostProcessor的beanName到nonOrderedPostProcessorNames
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 对实现了PriorityOrdered接口的BeanFactoryPostProcessor进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// 遍历实现了PriorityOrdered接口的BeanFactoryPostProcessor，执行postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		// 创建存放实现了Ordered接口的BeanFactoryPostProcessor集合
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());

		// 遍历存放实现了Ordered接口的BeanFactoryPostProcessor名字的集合
		for (String postProcessorName : orderedPostProcessorNames) {

			// 将实现了Ordered接口的BeanFactoryPostProcessor添加到集合中
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}

		// 对实现了Ordered接口的BeanFactoryPostProcessor进行排序操作
		sortPostProcessors(orderedPostProcessors, beanFactory);

		// 遍历实现了Ordered接口的BeanFactoryPostProcessor，执行postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 最后，创建存放普通的BeanFactoryPostProcessor的集合
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());

		for (String postProcessorName : nonOrderedPostProcessorNames) {

			// 将普通的BeanFactoryPostProcessor添加到集合中
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}

		// 遍历普通的BeanFactoryPostProcessor，执行postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		// 清除元数据缓存（mergeBeanDefinitions、allBeanNamesByType、singletonBeanNameByType）
		// 因为后置处理器可能已经修改了原始元数据，例如，替换值中的占位符
		beanFactory.clearMetadataCache();
	}

	/**
	 * 注册beanPostProcessor
	 * @param beanFactory
	 * @param applicationContext
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		// WARNING: Although it may appear that the body of this method can be easily
		// refactored to avoid the use of multiple loops and multiple lists, the use
		// of multiple lists and multiple passes over the names of processors is
		// intentional. We must ensure that we honor the contracts for PriorityOrdered
		// and Ordered processors. Specifically, we must NOT cause processors to be
		// instantiated (via getBean() invocations) or registered in the ApplicationContext
		// in the wrong order.
		//
		// Before submitting a pull request (PR) to change this method, please review the
		// list of all declined PRs involving changes to PostProcessorRegistrationDelegate
		// to ensure that your proposal does not result in a breaking change:
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

		// 找到所有实现了BeanPostProcessor接口的类
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		// 记录下BeanPostProcessor的目标计数
		// 此处加1，是因为下面会给beanFactory中添加一个BeanPostProcessorChecker类
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;

		// 添加BeanPostProcessorChecker(主要用于记录信息)到beanFactory中
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 定义存放实现了PriorityOrdered接口的BeanPostProcessor集合
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();

		// 定义存放spring内部的BeanPostProcessor
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();

		// 定义存放实现了Ordered接口的BeanPostProcessor的name集合
		List<String> orderedPostProcessorNames = new ArrayList<>();

		// 定义存放普通的BeanPostProcessor的name集合
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();

		// 遍历beanFactory中存在的BeanPostProcessor的集合postProcessorNames，
		for (String ppName : postProcessorNames) {

			// 如果ppName对应的BeanPostProcessor实例实现了PriorityOrdered接口，则获取到ppName对应的BeanPostProcessor的实例添加到priorityOrderedPostProcessors中
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);

				// 如果ppName对应的BeanPostProcessor实例也实现了MergedBeanDefinitionPostProcessor接口，那么则将ppName对应的bean实例添加到internalPostProcessors中
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}

			// 如果ppName对应的BeanPostProcessor实例没有实现PriorityOrdered接口，但是实现了Ordered接口，那么将ppName对应的bean实例添加到orderedPostProcessorNames中
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}

			// 否则将ppName添加到nonOrderedPostProcessorNames中
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		// 首先，对实现了PriorityOrdered接口的BeanPostProcessor实例进行排序操作
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// 注册实现了PriorityOrdered接口的BeanPostProcessor实例添加到beanFactory中
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		// 注册所有实现Ordered的beanPostProcessor
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {

			// 根据ppName找到对应的BeanPostProcessor实例对象
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);

			// 将实现了Ordered接口的BeanPostProcessor添加到orderedPostProcessors集合中
			orderedPostProcessors.add(pp);

			// 如果ppName对应的BeanPostProcessor实例也实现了MergedBeanDefinitionPostProcessor接口，那么则将ppName对应的bean实例添加到internalPostProcessors中
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}

		// 对实现了Ordered接口的BeanPostProcessor进行排序操作
		sortPostProcessors(orderedPostProcessors, beanFactory);

		//  注册实现了Ordered接口的BeanPostProcessor实例添加到beanFactory中
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		// 创建存放没有实现PriorityOrdered和Ordered接口的BeanPostProcessor的集合
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());

		for (String ppName : nonOrderedPostProcessorNames) {

			// 根据ppName找到对应的BeanPostProcessor实例对象
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);

			// 将没有实现PriorityOrdered和Ordered接口的BeanPostProcessor添加到nonOrderedPostProcessors集合中
			nonOrderedPostProcessors.add(pp);

			// 如果ppName对应的BeanPostProcessor实例也实现了MergedBeanDefinitionPostProcessor接口，那么则将ppName对应的bean实例添加到internalPostProcessors中
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}

		//  注册没有实现PriorityOrdered和Ordered的BeanPostProcessor实例添加到beanFactory中
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		// 将所有实现了MergedBeanDefinitionPostProcessor类型的BeanPostProcessor进行排序操作
		sortPostProcessors(internalPostProcessors, beanFactory);

		// 注册所有实现了MergedBeanDefinitionPostProcessor类型的BeanPostProcessor到beanFactory中
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		// 注册ApplicationListenerDetector到beanFactory中
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort?
		if (postProcessors.size() <= 1) {
			return;
		}
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 *
	 * 调用给定的 BeanDefinitionRegistryPostProcessor bean。
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry, ApplicationStartup applicationStartup) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {

			// 5.3新加操作，记录启动步骤，默认什么也不干
			StartupStep postProcessBeanDefRegistry = applicationStartup.start("spring.context.beandef-registry.post-process")
					.tag("postProcessor", postProcessor::toString);

			//调用 postProcessor 的 postProcessBeanDefinitionRegistry以使得postProcess往registry注册BeanDefinition对象
			postProcessor.postProcessBeanDefinitionRegistry(registry);

			postProcessBeanDefRegistry.end();
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 *
	 * 调用给定的 BeanFactoryPostProcessor bean。
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {

			// 用于记录启动步骤
			StartupStep postProcessBeanFactory = beanFactory.getApplicationStartup().start("spring.context.bean-factory.post-process")
					.tag("postProcessor", postProcessor::toString);

			//回调 BeanFactoryPostProcessor 的 postProcessBeanFactory 方法，使得每个postProcessor对象都可以对beanFactory进行调整
			postProcessor.postProcessBeanFactory(beanFactory);
			postProcessBeanFactory.end();
		}
	}

	/**
	 *
	 * 注册给定的BeanPostProcessor类型Bean对象
	 *
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		if (beanFactory instanceof AbstractBeanFactory) {
			// Bulk addition is more efficient against our CopyOnWriteArrayList there
			((AbstractBeanFactory) beanFactory).addBeanPostProcessors(postProcessors);
		}
		else {
			for (BeanPostProcessor postProcessor : postProcessors) {

				//将 postProcessor 添加到beanFactory,它将应用于该工厂创建的Bean。在工厂配置期间调用
				beanFactory.addBeanPostProcessor(postProcessor);
			}
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
