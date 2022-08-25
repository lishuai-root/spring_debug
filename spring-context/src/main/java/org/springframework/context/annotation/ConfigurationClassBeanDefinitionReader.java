/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.context.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Reads a given fully-populated set of ConfigurationClass instances, registering bean
 * definitions with the given {@link BeanDefinitionRegistry} based on its contents.
 *
 * <p>This class was modeled after the {@link BeanDefinitionReader} hierarchy, but does
 * not implement/extend any of its artifacts as a set of configuration classes is not a
 * {@link Resource}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Sebastien Deleuze
 * @since 3.0
 * @see ConfigurationClassParser
 */
class ConfigurationClassBeanDefinitionReader {

	private static final Log logger = LogFactory.getLog(ConfigurationClassBeanDefinitionReader.class);

	private static final ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	/**
	 * Boolean flag controlled by a {@code spring.xml.ignore} system property that instructs Spring to
	 * ignore XML, i.e. to not initialize the XML-related infrastructure.
	 * <p>The default is "false".
	 */
	private static final boolean shouldIgnoreXml = SpringProperties.getFlag("spring.xml.ignore");

	private final BeanDefinitionRegistry registry;

	private final SourceExtractor sourceExtractor;

	private final ResourceLoader resourceLoader;

	private final Environment environment;

	private final BeanNameGenerator importBeanNameGenerator;

	private final ImportRegistry importRegistry;

	private final ConditionEvaluator conditionEvaluator;


	/**
	 * Create a new {@link ConfigurationClassBeanDefinitionReader} instance
	 * that will be used to populate the given {@link BeanDefinitionRegistry}.
	 */
	ConfigurationClassBeanDefinitionReader(BeanDefinitionRegistry registry, SourceExtractor sourceExtractor,
			ResourceLoader resourceLoader, Environment environment, BeanNameGenerator importBeanNameGenerator,
			ImportRegistry importRegistry) {

		this.registry = registry;
		this.sourceExtractor = sourceExtractor;
		this.resourceLoader = resourceLoader;
		this.environment = environment;
		this.importBeanNameGenerator = importBeanNameGenerator;
		this.importRegistry = importRegistry;
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	/**
	 * Read {@code configurationModel}, registering bean definitions
	 * with the registry based on its contents.
	 * 读取{@code configurationModel}，根据其内容向注册中心注册bean定义。
	 */
	public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
		TrackedConditionEvaluator trackedConditionEvaluator = new TrackedConditionEvaluator();
		for (ConfigurationClass configClass : configurationModel) {
			loadBeanDefinitionsForConfigurationClass(configClass, trackedConditionEvaluator);
		}
	}

	/**
	 * Read a particular {@link ConfigurationClass}, registering bean definitions
	 * for the class itself and all of its {@link Bean} methods.
	 */
	private void loadBeanDefinitionsForConfigurationClass(
			ConfigurationClass configClass, TrackedConditionEvaluator trackedConditionEvaluator) {

		/***
		 * 如果需要跳过当前bean定义，则在已注册的bean定义信息中删除当前bean定义
		 */
		if (trackedConditionEvaluator.shouldSkip(configClass)) {
			String beanName = configClass.getBeanName();
			if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
				this.registry.removeBeanDefinition(beanName);
			}
			this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
			return;
		}

		// 如果一个bean是通过@Import(ImportSelector)的方式添加到容器中的，那么此时configClass.isImported()返回的是true
		// 而且configClass的importedBy属性里面存储的是ConfigurationClass就是将bean导入的类
		if (configClass.isImported()) {
			registerBeanDefinitionForImportedConfigurationClass(configClass);
		}

		// 判断当前的bean中是否含有@Bean注解的方法，如果有，需要把这些方法产生的bean放入到BeanDefinitionMap当中
		for (BeanMethod beanMethod : configClass.getBeanMethods()) {
			loadBeanDefinitionsForBeanMethod(beanMethod);
		}

		// 将@ImportResource引入的资源注入IOC容器
		/**
		 * 把通过@ImportResource引入的资源加载到IOC，和refresh()方法中创建bean工厂时读取xml的方式一样
		 */
		loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
		// 如果bean上存在@Import注解，且import的是一个实现了ImportBeanDefinitionRegistrar接口的bean定义,
		// 则执行ImportBeanDefinitionRegistrar的registerBeanDefinitions()方法
		loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
	}

	/**
	 * Register the {@link Configuration} class itself as a bean definition.
	 * 将{@link Configuration}类本身注册为bean定义。
	 *
	 * 把ConfigurationClass转换成AnnotatedGenericBeanDefinition并注册到IOC
	 */
	private void registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass configClass) {
		AnnotationMetadata metadata = configClass.getMetadata();
		AnnotatedGenericBeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);

		ScopeMetadata scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(configBeanDef);
		configBeanDef.setScope(scopeMetadata.getScopeName());
		String configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
		AnnotationConfigUtils.processCommonDefinitionAnnotations(configBeanDef, metadata);

		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(configBeanDef, configBeanName);
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
		this.registry.registerBeanDefinition(definitionHolder.getBeanName(), definitionHolder.getBeanDefinition());
		configClass.setBeanName(configBeanName);

		if (logger.isTraceEnabled()) {
			logger.trace("Registered bean definition for imported class '" + configBeanName + "'");
		}
	}

	/**
	 * Read the given {@link BeanMethod}, registering bean definitions
	 * with the BeanDefinitionRegistry based on its contents.
	 */
	@SuppressWarnings("deprecation")  // for RequiredAnnotationBeanPostProcessor.SKIP_REQUIRED_CHECK_ATTRIBUTE
	private void loadBeanDefinitionsForBeanMethod(BeanMethod beanMethod) {
		ConfigurationClass configClass = beanMethod.getConfigurationClass();
		MethodMetadata metadata = beanMethod.getMetadata();
		String methodName = metadata.getMethodName();

		// Do we need to mark the bean as skipped by its condition?
		if (this.conditionEvaluator.shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN)) {
			configClass.skippedBeanMethods.add(methodName);
			return;
		}
		if (configClass.skippedBeanMethods.contains(methodName)) {
			return;
		}

		/**
		 * 获取@Bean注解的属性
		 */
		AnnotationAttributes bean = AnnotationConfigUtils.attributesFor(metadata, Bean.class);
		Assert.state(bean != null, "No @Bean annotation attributes");

		// Consider name and any aliases
		// 获取别名，就是@Bean注解中的"name"属性的值
		List<String> names = new ArrayList<>(Arrays.asList(bean.getStringArray("name")));
		String beanName = (!names.isEmpty() ? names.remove(0) : methodName);

		// Register aliases even when overridden
		/**
		 * 注册别名
		 */
		for (String alias : names) {
			this.registry.registerAlias(beanName, alias);
		}

		// Has this effectively been overridden before (e.g. via XML)?	以前是否有效地重写过(例如通过XML)?
		// 检查是否存在同名bean定义，如果存在时，检查是否覆盖已有的bean定义
		if (isOverriddenByExistingDefinition(beanMethod, beanName)) {
			if (beanName.equals(beanMethod.getConfigurationClass().getBeanName())) {
				throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
						beanName, "Bean name derived from @Bean method '" + beanMethod.getMetadata().getMethodName() +
						"' clashes with bean name for containing configuration class; please make those names unique!");
			}
			return;
		}

		// 封装为ConfigurationClassBeanDefinition,表示是来自配置类里的bean定义
		ConfigurationClassBeanDefinition beanDef = new ConfigurationClassBeanDefinition(configClass, metadata, beanName);
		// 为bean定义配置数据来源
		beanDef.setSource(this.sourceExtractor.extractSource(metadata, configClass.getResource()));

		if (metadata.isStatic()) {
			// static @Bean method
			if (configClass.getMetadata() instanceof StandardAnnotationMetadata) {
				beanDef.setBeanClass(((StandardAnnotationMetadata) configClass.getMetadata()).getIntrospectedClass());
			}
			else {
				beanDef.setBeanClassName(configClass.getMetadata().getClassName());
			}
			// 设置唯一工厂方法
			beanDef.setUniqueFactoryMethodName(methodName);
		}
		else {
			// instance @Bean method
			// 设置当前工厂方法所在类
			beanDef.setFactoryBeanName(configClass.getBeanName());
			// 设置唯一工厂方法
			beanDef.setUniqueFactoryMethodName(methodName);
		}

		// 如果方法元数据是标准方法元数据的话，就设置解析的工厂方法
		if (metadata instanceof StandardMethodMetadata) {
			beanDef.setResolvedFactoryMethod(((StandardMethodMetadata) metadata).getIntrospectedMethod());
		}

		// 设置自定义装配模式，默认是构造器
		beanDef.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		// 设置忽略属性检查
		beanDef.setAttribute(org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor.
				SKIP_REQUIRED_CHECK_ATTRIBUTE, Boolean.TRUE);

		// 处理通用注解，注解里可能还有自动装配注解，主要是为beanDefinition设置注解属性
		AnnotationConfigUtils.processCommonDefinitionAnnotations(beanDef, metadata);

		// 获取@Bean中的autowire属性值，5.1之后弃用了@Bean中的autowire属性
		Autowire autowire = bean.getEnum("autowire");
		if (autowire.isAutowire()) {
			//如果是自动装配，也就是BY_NAME 或者 BY_TYPE，设置自动装配模式
			beanDef.setAutowireMode(autowire.value());
		}

		// 自动装配候选，默认是true
		boolean autowireCandidate = bean.getBoolean("autowireCandidate");
		if (!autowireCandidate) {
			beanDef.setAutowireCandidate(false);
		}

		// 初始化方法@PostConstruct和@PreDestroy或者XML或者InitializingBean和DisposableBean接口
		String initMethodName = bean.getString("initMethod");
		if (StringUtils.hasText(initMethodName)) {
			beanDef.setInitMethodName(initMethodName);
		}

		// 销毁方法
		String destroyMethodName = bean.getString("destroyMethod");
		beanDef.setDestroyMethodName(destroyMethodName);

		// Consider scoping
		// 处理作用域
		ScopedProxyMode proxyMode = ScopedProxyMode.NO;
		AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(metadata, Scope.class);
		if (attributes != null) {
			beanDef.setScope(attributes.getString("value"));
			proxyMode = attributes.getEnum("proxyMode");
			if (proxyMode == ScopedProxyMode.DEFAULT) {
				proxyMode = ScopedProxyMode.NO;
			}
		}

		// Replace the original bean definition with the target one, if necessary
		// 如果需要，将原始bean定义替换为目标bean定义,如果作用域不是no的话就要使用代理
		BeanDefinition beanDefToRegister = beanDef;
		if (proxyMode != ScopedProxyMode.NO) {
			BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(
					new BeanDefinitionHolder(beanDef, beanName), this.registry,
					proxyMode == ScopedProxyMode.TARGET_CLASS);
			beanDefToRegister = new ConfigurationClassBeanDefinition(
					(RootBeanDefinition) proxyDef.getBeanDefinition(), configClass, metadata, beanName);
		}

		if (logger.isTraceEnabled()) {
			logger.trace(String.format("Registering bean definition for @Bean method %s.%s()",
					configClass.getMetadata().getClassName(), beanName));
		}
		// 注册beanDefinition
		this.registry.registerBeanDefinition(beanName, beanDefToRegister);
	}

	/**
	 * 检查是否覆盖已有同名beanDefinition，false：覆盖，true：不覆盖
	 *
	 * 当存在同名的bean定义时：
	 * 	1.是同一个配置类的不同重载方法注入时，不覆盖，设置工厂方法不唯一
	 * 	2.已有bean定义是通过@ComponentScan注解扫描注入的，覆盖
	 * 	3.已有bean定义是spring内部注入的，覆盖
	 *
	 * @param beanMethod
	 * @param beanName
	 * @return
	 */
	protected boolean isOverriddenByExistingDefinition(BeanMethod beanMethod, String beanName) {
		if (!this.registry.containsBeanDefinition(beanName)) {
			return false;
		}
		BeanDefinition existingBeanDef = this.registry.getBeanDefinition(beanName);

		// Is the existing bean definition one that was created from a configuration class?
		// -> allow the current bean method to override, since both are at second-pass level.
		// However, if the bean method is an overloaded case on the same configuration class,
		// preserve the existing bean definition.
		// 现有的bean定义是从配置类创建的吗?->允许重写当前bean方法，因为两者都是二次传递级别。
		// 但是，如果bean方法是同一个配置类上的重载情况，则保留现有的bean定义。
		/**
		 * 注：当前方法在ConfigurationClassBeanDefinitionReader.java中，是ConfigurationClassBeanDefinition的解析类
		 * 如果{@param beanName}已经在spring中注册过了对应的beanDefinition，且是同一个配置类中的重载方法，就保存已有的，不覆盖
		 */
		if (existingBeanDef instanceof ConfigurationClassBeanDefinition) {
			ConfigurationClassBeanDefinition ccbd = (ConfigurationClassBeanDefinition) existingBeanDef;
			if (ccbd.getMetadata().getClassName().equals(
					beanMethod.getConfigurationClass().getMetadata().getClassName())) {
				if (ccbd.getFactoryMethodMetadata().getMethodName().equals(ccbd.getFactoryMethodName())) {
					//设置工厂方法不唯一，说明有重载
					ccbd.setNonUniqueFactoryMethodName(ccbd.getFactoryMethodMetadata().getMethodName());
				}
				// 不覆盖
				return true;
			}
			else {
				// 覆盖
				return false;
			}
		}

		// A bean definition resulting from a component scan can be silently overridden
		// by an @Bean method, as of 4.2...
		// 从组件扫描产生的bean定义可以被@Bean方法静默覆盖，截至4.2……
		// 如果已有的beanDefinition是通过@ComponentScan注解扫描注入的，则覆盖已有的
		if (existingBeanDef instanceof ScannedGenericBeanDefinition) {
			return false;
		}

		// Has the existing bean definition bean marked as a framework-generated bean?
		// -> allow the current bean method to override it, since it is application-level
		// 现有的bean定义bean是否标记为框架生成的bean?->允许当前bean方法覆盖它，因为它是应用程序级的
		// 如果已有的beanDefinition是spring自己内部的，覆盖已有的(这个说法不太准确，{@link #BeanDefinition})
		if (existingBeanDef.getRole() > BeanDefinition.ROLE_APPLICATION) {
			return false;
		}

		// At this point, it's a top-level override (probably XML), just having been parsed
		// before configuration class processing kicks in...
		// 在这一点上，它是一个顶级重写(可能是XML)，只是在配置类处理开始之前被解析……
		/**
		 *
		 * {@param beanMethod}：当前要注入的@Bean注解的方法
		 * {@param beanName}：当前要通过@Bean注解注入的bean名称
		 * {@code existingBeanDef}：beanFactory中beanName对应的已存在的beanDefinition
		 * 此处判断的逻辑如下：
		 * 	如果当前bean工厂是DefaultListableBeanFactory(默认就是DefaultListableBeanFactory)，且当前bean工厂是否允许重写bean定义
		 *  可能性一：当前bean工厂不允许重写bean定义，则 !((DefaultListableBeanFactory) this.registry).isAllowBeanDefinitionOverriding() = true
		 *           此时抛出异常，没问题，bean定义出现二义性
		 *  可能性二：当前bean工厂允许重写bean定义，则 !((DefaultListableBeanFactory) this.registry).isAllowBeanDefinitionOverriding() = false
		 *           在这种情况下，只是记录日志，并返回不覆盖。
		 *  对于可能性二：既然bean工厂已经允许重写bean定义，那这里为什么要返回true ???(三个?表示大写的问号)
		 *
		 * 	解：
		 * 		如果能走到此处表示existingBeanDef(已经存在的同名bean定义)不是通过配置类注入的，也不是通过@ComponentScan注解扫描注入的，也不是spring内部定义的
		 * 	    也就是说existingBeanDef是用户手动注入的(比如：xml)，spring认为用户手动注入的bean是顶级重写，也就是优先级最高的bean定义。
		 * 	    因此，此处使用已有的bean定义(优先级最高)重写其他的
		 *
		 */
		if (this.registry instanceof DefaultListableBeanFactory &&
				!((DefaultListableBeanFactory) this.registry).isAllowBeanDefinitionOverriding()) {
			throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
					beanName, "@Bean definition illegally overridden by existing bean definition: " + existingBeanDef);
		}
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Skipping bean definition for %s: a definition for bean '%s' " +
					"already exists. This top-level bean definition is considered as an override.",
					beanMethod, beanName));
		}
		// 不覆盖
		return true;
	}

	private void loadBeanDefinitionsFromImportedResources(
			Map<String, Class<? extends BeanDefinitionReader>> importedResources) {

		Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<>();

		importedResources.forEach((resource, readerClass) -> {
			// Default reader selection necessary?
			if (BeanDefinitionReader.class == readerClass) {
				if (StringUtils.endsWithIgnoreCase(resource, ".groovy")) {
					// When clearly asking for Groovy, that's what they'll get...
					readerClass = GroovyBeanDefinitionReader.class;
				}
				else if (shouldIgnoreXml) {
					throw new UnsupportedOperationException("XML support disabled");
				}
				else {
					// Primarily ".xml" files but for any other extension as well
					readerClass = XmlBeanDefinitionReader.class;
				}
			}

			BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
			if (reader == null) {
				try {
					// Instantiate the specified BeanDefinitionReader
					reader = readerClass.getConstructor(BeanDefinitionRegistry.class).newInstance(this.registry);
					// Delegate the current ResourceLoader to it if possible
					if (reader instanceof AbstractBeanDefinitionReader) {
						AbstractBeanDefinitionReader abdr = ((AbstractBeanDefinitionReader) reader);
						abdr.setResourceLoader(this.resourceLoader);
						abdr.setEnvironment(this.environment);
					}
					readerInstanceCache.put(readerClass, reader);
				}
				catch (Throwable ex) {
					throw new IllegalStateException(
							"Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]");
				}
			}

			// TODO SPR-6310: qualify relative path locations as done in AbstractContextLoader.modifyLocations
			reader.loadBeanDefinitions(resource);
		});
	}

	/**
	 * 处理被@Import注解引入的ImportBeanDefinitionRegistrar类型的bean，核心逻辑就是调用ImportBeanDefinitionRegistrar::registerBeanDefinitions方法
	 *
	 * @param registrars
	 */
	private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
		registrars.forEach((registrar, metadata) ->
				registrar.registerBeanDefinitions(metadata, this.registry, this.importBeanNameGenerator));
	}


	/**
	 * {@link RootBeanDefinition} marker subclass used to signify that a bean definition
	 * was created from a configuration class as opposed to any other configuration source.
	 * Used in bean overriding cases where it's necessary to determine whether the bean
	 * definition was created externally.
	 */
	@SuppressWarnings("serial")
	private static class ConfigurationClassBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {

		private final AnnotationMetadata annotationMetadata;

		private final MethodMetadata factoryMethodMetadata;

		private final String derivedBeanName;

		public ConfigurationClassBeanDefinition(
				ConfigurationClass configClass, MethodMetadata beanMethodMetadata, String derivedBeanName) {

			this.annotationMetadata = configClass.getMetadata();
			this.factoryMethodMetadata = beanMethodMetadata;
			this.derivedBeanName = derivedBeanName;
			setResource(configClass.getResource());
			setLenientConstructorResolution(false);
		}

		public ConfigurationClassBeanDefinition(RootBeanDefinition original,
				ConfigurationClass configClass, MethodMetadata beanMethodMetadata, String derivedBeanName) {
			super(original);
			this.annotationMetadata = configClass.getMetadata();
			this.factoryMethodMetadata = beanMethodMetadata;
			this.derivedBeanName = derivedBeanName;
		}

		private ConfigurationClassBeanDefinition(ConfigurationClassBeanDefinition original) {
			super(original);
			this.annotationMetadata = original.annotationMetadata;
			this.factoryMethodMetadata = original.factoryMethodMetadata;
			this.derivedBeanName = original.derivedBeanName;
		}

		@Override
		public AnnotationMetadata getMetadata() {
			return this.annotationMetadata;
		}

		@Override
		@NonNull
		public MethodMetadata getFactoryMethodMetadata() {
			return this.factoryMethodMetadata;
		}

		@Override
		public boolean isFactoryMethod(Method candidate) {
			return (super.isFactoryMethod(candidate) && BeanAnnotationHelper.isBeanAnnotated(candidate) &&
					BeanAnnotationHelper.determineBeanNameFor(candidate).equals(this.derivedBeanName));
		}

		@Override
		public ConfigurationClassBeanDefinition cloneBeanDefinition() {
			return new ConfigurationClassBeanDefinition(this);
		}
	}


	/**
	 * Evaluate {@code @Conditional} annotations, tracking results and taking into
	 * account 'imported by'.
	 */
	private class TrackedConditionEvaluator {

		private final Map<ConfigurationClass, Boolean> skipped = new HashMap<>();

		public boolean shouldSkip(ConfigurationClass configClass) {
			Boolean skip = this.skipped.get(configClass);
			if (skip == null) {
				if (configClass.isImported()) {
					boolean allSkipped = true;
					for (ConfigurationClass importedBy : configClass.getImportedBy()) {
						if (!shouldSkip(importedBy)) {
							allSkipped = false;
							break;
						}
					}
					if (allSkipped) {
						// The config classes that imported this one were all skipped, therefore we are skipped...
						skip = true;
					}
				}
				if (skip == null) {
					skip = conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN);
				}
				this.skipped.put(configClass, skip);
			}
			return skip;
		}
	}

}
