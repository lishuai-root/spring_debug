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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.core.NestedIOException;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.0
 * @see ConfigurationClassBeanDefinitionReader
 */
class ConfigurationClassParser {

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className ->
			(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

	private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR =
			(o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


	private final Log logger = LogFactory.getLog(getClass());

	private final MetadataReaderFactory metadataReaderFactory;

	private final ProblemReporter problemReporter;

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanDefinitionRegistry registry;

	private final ComponentScanAnnotationParser componentScanParser;

	private final ConditionEvaluator conditionEvaluator;

	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

	private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

	private final List<String> propertySourceNames = new ArrayList<>();

	private final ImportStack importStack = new ImportStack();

	private final DeferredImportSelectorHandler deferredImportSelectorHandler = new DeferredImportSelectorHandler();

	private final SourceClass objectSourceClass = new SourceClass(Object.class);


	/**
	 * Create a new {@link ConfigurationClassParser} instance that will be used
	 * to populate the set of configuration classes.
	 */
	public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
			ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

		this.metadataReaderFactory = metadataReaderFactory;
		this.problemReporter = problemReporter;
		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.registry = registry;
		this.componentScanParser = new ComponentScanAnnotationParser(
				environment, resourceLoader, componentScanBeanNameGenerator, registry);
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	/**
	 * 解析所有被标注为配置类的beanDefinition，处理过程可能会引起其他类加载
	 *
	 * @param configCandidates
	 */
	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();

			/**
			 * 根据不同情况调用不同的parse()重载，最终都是调用ConfigurationClassParser::processConfigurationClass()
			 * 三个重载只是在parse()方法中通过ConfigurationClass的不同构造函数重载创建ConfigurationClass实列
			 */
			try {
				if (bd instanceof AnnotatedBeanDefinition) {
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				else {
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}

		// 执行找到的DeferredImportSelector
		// DeferredImportSelector是ImportSelector的一个子类
		// ImportSelector被设计成和@Import注解同样的效果，但是实现了ImportSelector的类可以条件性的决定导入某些配置
		// DeferredImportSelector的设计目的是在所有其他的配置类被处理后才进行处理
		this.deferredImportSelectorHandler.process();
	}

	protected final void parse(@Nullable String className, String beanName) throws IOException {
		Assert.notNull(className, "No bean class name for configuration class bean definition");
		MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
		processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(Class<?> clazz, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	/**
	 * Validate each {@link ConfigurationClass} object.
	 * 验证每个{@link ConfigurationClass}对象。验证每一个需要代理的配置类是否支持必要的重写
	 *
	 * @see ConfigurationClass#validate
	 */
	public void validate() {
		for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
			configClass.validate(this.problemReporter);
		}
	}

	public Set<ConfigurationClass> getConfigurationClasses() {
		return this.configurationClasses.keySet();
	}


	/**
	 * 递归解析配置类，当前配置类的内部类和当前配置类引入的类以及当前配置类的父类
	 *
	 * @param configClass
	 * @param filter
	 * @throws IOException
	 */
	protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			return;
		}

		// 第一次进入的时候，configurationClass的size为0，existingClass肯定为null，在此处处理configuration重复import
		// 如果同一个配置类被处理两次，两次都属于被import的则合并导入类，返回，如果配置类不是被导入的，则移除旧的使用新的配置类
		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		// 如果不为空表示已经处理过当前beanDefinition
		if (existingClass != null) {
			if (configClass.isImported()) {
				if (existingClass.isImported()) {
					// 如果要处理的配置类configClass在已经分析处理的配置类记录中已存在，合并两者的importBy属性
					existingClass.mergeImportedBy(configClass);
				}
				// Otherwise ignore new imported config class; existing non-imported class overrides it.
				// 否则忽略新导入的配置类;现有的非导入类将重写它。
				return;
			}
			else {
				// Explicit bean definition found, probably replacing an import.
				// Let's remove the old one and go with the new one.
				this.configurationClasses.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}

		// Recursively process the configuration class and its superclass hierarchy.
		/**
		 * SourceClass:简单的包装器，允许以统一的方式处理带注释的源类，而不管它们是如何加载的。
		 * 使用asm解析类资源(类的全限定名以"java"开头的不使用asm)
		 */
		SourceClass sourceClass = asSourceClass(configClass, filter);
		/**
		 * 递归解析当前类及其父类
		 */
		do {
			// 如果当前配置类有父类，且父类全限定名不是以"java"开头，返回的就是其父类的有数据信息，否则返回null
			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
		}
		while (sourceClass != null);

		// 将解析的配置类存储起来，这样回到parse方法时，能取到值
		this.configurationClasses.put(configClass, configClass);
	}

	/**
	 * Apply processing and build a complete {@link ConfigurationClass} by reading the
	 * annotations, members and methods from the source class. This method can be called
	 * multiple times as relevant sources are discovered.
	 * 通过从源类中读取注释、成员和方法，应用处理并构建一个完整的{@link ConfigurationClass}。当发现相关源时，可以多次调用此方法。
	 *
	 * 该方法以以下顺序解析当前配置类：
	 * 1.递归processMemberClasses()解析当前类的内部类(如果有)
	 * 2.解析@PropertySource和@PropertySources注解，并将注解引入的资源注入spring资源属性中(未解析资源)
	 * 3.解析@ComponentScan或者@ComponentScans注解，把指定包下所有符合要求的class文件解析成beanDefinition(ScannedGenericBeanDefinition类型，解析过程由asm实现，不引发类加载)
	 *	 遍历解析到的beanDefinition，如果有配置类，递归调用parse()方法解析
	 * 4.解析@Import注解，遍历当前配置类的@Import注解中引入的所有类，进行以下解析：
	 * 	 4.1.如果引入的类是ImportSelector接口类型的，且实现了DeferredImportSelector接口，则将该引入的类加入到当前解析器的deferredImportSelectorHandler中，
	 * 	 	 预留到所有的配置类加载完成后统一处理自动化配置类
	 * 	 4.2.如果引入的类是ImportSelector接口类型的，但是没有实现DeferredImportSelector接口，递归调用processImports()方法处理被引入的类，
	 * 	     被引入的类有可能也被@Import注解标注，所以需要递归处理
	 * 	 4.3.如果引入的类不是ImportSelector接口类型的，但是实现了ImportBeanDefinitionRegistrar接口，则添加到 {@param configClass}的importBeanDefinitionRegistrars中，
	 * 	     在ConfigurationClassPostProcessor处理configClass时会随着一起处理
	 * 	     ImportBeanDefinitionRegistrar接口中的registerBeanDefinitions()可以手动给spring中注入beanDefinition
	 * 	 4.4.如果当前引入的类既没有实现ImportSelector接口，也没有实现ImportBeanDefinitionRegistrar接口，
	 * 		 则将其当作带有@Configuration注解的配置类一样递归调用processConfigurationClass()方法处理，并将其标注为importedBy，意味着它是通过被@Import进来的
	 * 		 如果是通过@Import引入的普通类，后面会注册进DefaultListableBeanFactory(spring默认的beanFactory)
	 *   注：DeferredImportSelector接口继承了ImportSelector接口，DeferredImportSelector接口的目的就是在解析完配置类后做处理，springboot的自动装配就是通过该类实现。
	 * 5.解析@ImportResource注解，将需要引入的资源添加到 {@param configClass}的importedResources中
	 * 6.解析@Bean注解的方法，将@Bean方法转化为BeanMethod对象，保存在 {@param configClass}的beanMethods中
	 * 7.递归处理当前配置类的父接口中带有@Bean注解的默认方法，如果是Java 8+接口上的默认方法或其他具体方法，则添加到{@param configClass}的beanMethods中
	 *   注：beanMethods中保存的方法最后会被解析成ConfigurationClassBeanDefinition类型的beanDefinition注入到beanDefinitionMap中
	 * 8.递归解析当前配置类的父类，如果当前配置类有父类，且没有被解析，且不是java核心类(java核心类：类的全限定名以"java"开头)，
	 *   则获取当前配置类的父类元数据信息，递归调用当前doProcessConfigurationClass()方法处理父类
	 *
	 * @param configClass the configuration class being build
	 * @param sourceClass a source class
	 * @return the superclass, or {@code null} if none found or previously processed
	 */
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {
		// 处理@Component注解,@Configuration继承了@Component
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			// Recursively process any member (nested) classes first
			// 递归处理内部类，因为内部类也是一个配置类，配置类上有@configuration注解，该注解继承@Component，
			// if判断为true，调用processMemberClasses方法，递归解析配置类中的内部类
			processMemberClasses(configClass, sourceClass, filter);
		}

		// Process any @PropertySource annotations
		// 处理@PropertySource和@PropertySources注解
		// 把注解中配置的资源文件添加到spring的资源属性中
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment) {
				processPropertySource(propertySource);
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		// Process any @ComponentScan annotations
		/**
		 * 处理@ComponentScan或者@ComponentScans注解，并将扫描包下的所有bean转换成填充后的ConfigurationClass
		 */
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			// 遍历扫描@ComponentScan和@ComponentScans配置的扫描的包所包含的包
			for (AnnotationAttributes componentScan : componentScans) {
				// The config class is annotated with @ComponentScan -> perform the scan immediately
				// 配置类被注释为@ComponentScan—>执行扫描
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				// Check the set of scanned definitions for any further config classes and parse recursively if needed
				// 检查扫描的定义集，以获得进一步的配置类，如果需要，则递归解析
				/**
				 * 通过@ComponentScan和@ComponentScans解析到的beanDefinition可能也有@ComponentScan和@ComponentScans注解
				 * 所以需要递归解析，直到没有@ComponentScan和@ComponentScans为止
				 */
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					// 如果是一个配置类，递归解析
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		// Process any @Import annotations
		// 处理@Import注解
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

		// Process any @ImportResource annotations
		// 处理@ImportResource注解，导入spring的配置文件
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		// Process individual @Bean methods
		// 处理加了@Bean注解的方法，将@Bean方法转化为BeanMethod对象，保存再集合中
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}

		// Process default methods on interfaces
		// 处理接口的默认方法实现，从jdk8开始，接口中的方法可以有自己的默认实现，因此如果这个接口的方法加了@Bean注解，也需要被解析
		processInterfaces(configClass, sourceClass);

		// Process superclass, if any
		// 解析父类，如果被解析的配置类继承了某个类，那么配置类的父类也会被进行解析
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			// 如果存在父类，且不是java核心类，且还未处理，则返回父类递归处理
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);
				// Superclass found, return its annotation metadata and recurse
				// 找到的超类，返回其注释元数据并递归
				return sourceClass.getSuperClass();
			}
		}

		// No superclass -> processing is complete	没有超类->处理已完成
		return null;
	}

	/**
	 * Register member (nested) classes that happen to be configuration classes themselves.
	 * 注册恰好是配置类本身的成员(嵌套)类。
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass,
			Predicate<String> filter) throws IOException {

		// 获取当前配置类的内部类，内部类有可能也是配置类
		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();

		// 如果存在内部类，遍历处理
		if (!memberClasses.isEmpty()) {
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			for (SourceClass memberClass : memberClasses) {
				// 如果内部类是配置类，且不是当前类
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
						!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					candidates.add(memberClass);
				}
			}
			OrderComparator.sort(candidates);

			// 遍历解析是配置类的内部类
			for (SourceClass candidate : candidates) {
				// 如果出现配置类循环导入，则抛出异常
				if (this.importStack.contains(configClass)) {
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				}
				else {
					// 配置类入栈
					this.importStack.push(configClass);
					try {
						// 递归调用processConfigurationClass()方法，因为内部类有可能还包含内部类，需要递归解析
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					}
					finally {
						// 解析完出栈
						this.importStack.pop();
					}
				}
			}
		}
	}

	/**
	 * Register default methods on interfaces implemented by the configuration class.
	 * 在配置类实现的接口上注册默认方法。
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		// 遍历配置类直接实现的接口
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			// 查找接口中所有被@Bean标注的方法
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
			for (MethodMetadata methodMetadata : beanMethods) {
				// methodMetadata.isAbstract() : 确定底层方法是否有效抽象:即在类中标记为抽象，或在接口中声明为常规的非默认方法。
				if (!methodMetadata.isAbstract()) {
					// A default method or other concrete method on a Java 8+ interface...
					// 如果是Java 8+接口上的默认方法或其他具体方法，则添加到集合保存
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			// 递归处理接口的父接口
			processInterfaces(configClass, ifc);
		}
	}

	/**
	 * Retrieve the metadata for all <code>@Bean</code> methods.
	 * 检索所有<code>@Bean<code>方法的元数据。
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		AnnotationMetadata original = sourceClass.getMetadata();
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			// Try reading the class file via ASM for deterministic declaration order...
			// Unfortunately, the JVM's standard reflection returns methods in arbitrary
			// order, even between different runs of the same application on the same JVM.
			// 尝试通过ASM读取类文件以确定声明顺序…不幸的是，JVM的标准反射以任意顺序返回方法，
			// 甚至在同一JVM上相同应用程序的不同运行之间也会以不同顺序返回方法。
			try {
				AnnotationMetadata asm =
						this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
					for (MethodMetadata asmMethod : asmMethods) {
						for (MethodMetadata beanMethod : beanMethods) {
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								selectedMethods.add(beanMethod);
								break;
							}
						}
					}
					// 如果通过jvm反射获取到的方法都在通过asm找到的带有@Bean的方法中找到，
					// 则返回asm中对应的方法集合
					if (selectedMethods.size() == beanMethods.size()) {
						// All reflection-detected methods found in ASM method set -> proceed
						beanMethods = selectedMethods;
					}
				}
			}
			catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
				// No worries, let's continue with the reflection metadata we started with...
				// 0.0 别担心，让我们继续开始时的反射元数据……
			}
		}
		return beanMethods;
	}


	/**
	 * Process the given <code>@PropertySource</code> annotation metadata.
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 */
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		String[] locations = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
				DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

		for (String location : locations) {
			try {
				// 解析占位符
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
				//将指定位置的资源转换成Resource对象
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				// 把resource添加到资源属性中
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			}
			catch (IllegalArgumentException | FileNotFoundException | UnknownHostException | SocketException ex) {
				// Placeholders not resolvable or resource not found when trying to open it
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
		}
	}

	private void addPropertySource(PropertySource<?> propertySource) {
		String name = propertySource.getName();
		MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

		if (this.propertySourceNames.contains(name)) {
			// We've already added a version, we need to extend it
			PropertySource<?> existing = propertySources.get(name);
			if (existing != null) {
				PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
						((ResourcePropertySource) propertySource).withResourceName() : propertySource);
				if (existing instanceof CompositePropertySource) {
					((CompositePropertySource) existing).addFirstPropertySource(newSource);
				}
				else {
					if (existing instanceof ResourcePropertySource) {
						existing = ((ResourcePropertySource) existing).withResourceName();
					}
					CompositePropertySource composite = new CompositePropertySource(name);
					composite.addPropertySource(newSource);
					composite.addPropertySource(existing);
					propertySources.replace(name, composite);
				}
				return;
			}
		}

		if (this.propertySourceNames.isEmpty()) {
			propertySources.addLast(propertySource);
		}
		else {
			String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
			propertySources.addBefore(firstProcessed, propertySource);
		}
		this.propertySourceNames.add(name);
	}


	/**
	 * Returns {@code @Import} class, considering all meta-annotations.
	 * 返回{@code @Import}类，考虑到所有元注释。
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		// 创建集合，存储包含@Import注解的类
		Set<SourceClass> imports = new LinkedHashSet<>();
		// 创建集合，为了实现递归调用
		Set<SourceClass> visited = new LinkedHashSet<>();
		// 收集@Import注解的类
		collectImports(sourceClass, imports, visited);
		return imports;
	}

	/**
	 * Recursively collect all declared {@code @Import} values. Unlike most
	 * meta-annotations it is valid to have several {@code @Import}s declared with
	 * different values; the usual process of returning values from the first
	 * meta-annotation on a class is not sufficient.
	 * <p>For example, it is common for a {@code @Configuration} class to declare direct
	 * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
	 * annotation.
	 * @param sourceClass the class to search
	 * @param imports the imports collected so far
	 * @param visited used to track visited classes to prevent infinite recursion
	 * @throws IOException if there is any problem reading metadata from the named class
	 */
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {

		if (visited.add(sourceClass)) {
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				String annName = annotation.getMetadata().getClassName();
				// 递归处理其他可能包含@Import的注解类
				if (!annName.equals(Import.class.getName())) {
					collectImports(annotation, imports, visited);
				}
			}
			// 获取Import注解的值
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
			boolean checkForCircularImports) {

		// 如果使用@Import注解修饰的类集合为空，那么直接返回
		if (importCandidates.isEmpty()) {
			return;
		}

		// 通过一个栈结构解决循环引入
		// 如果出现配置类循环引入，抛出异常
		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		else {
			// 添加到栈中，用于处理循环引入的问题
			this.importStack.push(configClass);
			try {
				// 遍历每一个@Import注解的类
				for (SourceClass candidate : importCandidates) {
					// 检验配置类Import引入的类是否是ImportSelector子类
					if (candidate.isAssignable(ImportSelector.class)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						// 候选类是一个导入选择器->委托来确定是否进行导入
						Class<?> candidateClass = candidate.loadClass();
						// 通过反射生成一个ImportSelect对象
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);
						// 获取选择器的额外过滤器
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						if (selectorFilter != null) {
							exclusionFilter = exclusionFilter.or(selectorFilter);
						}

						// 判断引用选择器是否是DeferredImportSelector接口的实例
						// 如果是则应用选择器将会在所有的配置类都加载完毕后加载
						if (selector instanceof DeferredImportSelector) {
							// 将选择器添加到deferredImportSelectorHandler实例中，预留到所有的配置类加载完成后统一处理自动化配置类
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						}
						else {
							// 获取引入的类，然后使用递归方式将这些类中同样添加了@Import注解引用的类
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
							// 递归处理，被Import进来的类也有可能@Import注解
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
					}
					// 如果是实现了ImportBeanDefinitionRegistrar接口的beanDefinition
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
						// 候选类是一个importbeandefinitionregistry ->委托，用于注册其他bean定义
						Class<?> candidateClass = candidate.loadClass();
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);

						/**
						 * 放到当前configClass的importBeanDefinitionRegistrars中
						 * 在ConfigurationClassPostProcessor处理configClass时会随之一起处理
						 */
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
						// 候选类不是ImportSelector或ImportBeanDefinitionRegistrar ->将其作为@Configuration类处理
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());

						/**
						 * 如果Import的类型是普通类，则将其当作带有@Configuration的类一样处理
						 * 将candidate构造为ConfigurationClass，标注为importedBy，意味着它是通过被@Import进来的
						 * 后面处理会用到这个判断将这个普通类注册进DefaultListableBeanFactory
						 */
						processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
			finally {
				this.importStack.pop();
			}
		}
	}

	private boolean isChainedImportOnStack(ConfigurationClass configClass) {
		if (this.importStack.contains(configClass)) {
			String configClassName = configClass.getMetadata().getClassName();
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
		AnnotationMetadata metadata = configurationClass.getMetadata();
		if (metadata instanceof StandardAnnotationMetadata) {
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass(), filter);
		}
		return asSourceClass(metadata.getClassName(), filter);
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link Class}.
	 */
	SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
		if (classType == null || filter.test(classType.getName())) {
			return this.objectSourceClass;
		}
		try {
			// Sanity test that we can reflectively read annotations,
			// including Class attributes; if not -> fall back to ASM
			for (Annotation ann : classType.getDeclaredAnnotations()) {
				AnnotationUtils.validateAnnotation(ann);
			}
			return new SourceClass(classType);
		}
		catch (Throwable ex) {
			// Enforce ASM via class name resolution
			return asSourceClass(classType.getName(), filter);
		}
	}

	/**
	 * Factory method to obtain a {@link SourceClass} collection from class names.
	 */
	private Collection<SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
		List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
		for (String className : classNames) {
			annotatedClasses.add(asSourceClass(className, filter));
		}
		return annotatedClasses;
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a class name.
	 */
	SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
		if (className == null || filter.test(className)) {
			return this.objectSourceClass;
		}
		if (className.startsWith("java")) {
			// Never use ASM for core java types	不要将ASM用于核心java类型
			try {
				return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new NestedIOException("Failed to load class [" + className + "]", ex);
			}
		}
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
	}


	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

		private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			this.imports.add(importedClass, importingClass);
		}

		@Override
		@Nullable
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			return CollectionUtils.lastElement(this.imports.get(importedClass));
		}

		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext();) {
					if (iterator.next().getClassName().equals(importingClass)) {
						iterator.remove();
						break;
					}
				}
			}
		}

		/**
		 * Given a stack containing (in order)
		 * <ul>
		 * <li>com.acme.Foo</li>
		 * <li>com.acme.Bar</li>
		 * <li>com.acme.Baz</li>
		 * </ul>
		 * return "[Foo->Bar->Baz]".
		 */
		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner("->", "[", "]");
			for (ConfigurationClass configurationClass : this) {
				joiner.add(configurationClass.getSimpleName());
			}
			return joiner.toString();
		}
	}


	private class DeferredImportSelectorHandler {

		@Nullable
		private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

		/**
		 * Handle the specified {@link DeferredImportSelector}. If deferred import
		 * selectors are being collected, this registers this instance to the list. If
		 * they are being processed, the {@link DeferredImportSelector} is also processed
		 * immediately according to its {@link DeferredImportSelector.Group}.
		 * @param configClass the source configuration class
		 * @param importSelector the selector to handle
		 */
		public void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
			DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);
			if (this.deferredImportSelectors == null) {
				DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
				handler.register(holder);
				handler.processGroupImports();
			}
			else {
				this.deferredImportSelectors.add(holder);
			}
		}

		public void process() {
			// 此处获取@Import注解导入器
			List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
			this.deferredImportSelectors = null;
			try {
				if (deferredImports != null) {
					DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
					deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
					deferredImports.forEach(handler::register);
					// 核心处理类，此处完成自动配置功能
					handler.processGroupImports();
				}
			}
			finally {
				this.deferredImportSelectors = new ArrayList<>();
			}
		}
	}


	private class DeferredImportSelectorGroupingHandler {

		private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();

		private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

		public void register(DeferredImportSelectorHolder deferredImport) {
			Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();
			DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent(
					(group != null ? group : deferredImport),
					key -> new DeferredImportSelectorGrouping(createGroup(group)));
			grouping.add(deferredImport);
			this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(),
					deferredImport.getConfigurationClass());
		}

		// 完成自动装配的核心处理逻辑
		public void processGroupImports() {
			for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
				Predicate<String> exclusionFilter = grouping.getCandidateFilter();
				// getImports方法是读取自动化配置类的核心入口，
				grouping.getImports().forEach(entry -> {
					ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
					try {
						// 配置类中可能会包含@Import注解引入的类，通过此方法将引入的类注入
						processImports(configurationClass, asSourceClass(configurationClass, exclusionFilter),
								Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)),
								exclusionFilter, false);
					}
					catch (BeanDefinitionStoreException ex) {
						throw ex;
					}
					catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to process import candidates for configuration class [" +
										configurationClass.getMetadata().getClassName() + "]", ex);
					}
				});
			}
		}

		private Group createGroup(@Nullable Class<? extends Group> type) {
			Class<? extends Group> effectiveType = (type != null ? type : DefaultDeferredImportSelectorGroup.class);
			return ParserStrategyUtils.instantiateClass(effectiveType, Group.class,
					ConfigurationClassParser.this.environment,
					ConfigurationClassParser.this.resourceLoader,
					ConfigurationClassParser.this.registry);
		}
	}


	private static class DeferredImportSelectorHolder {

		private final ConfigurationClass configurationClass;

		private final DeferredImportSelector importSelector;

		public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
			this.configurationClass = configClass;
			this.importSelector = selector;
		}

		public ConfigurationClass getConfigurationClass() {
			return this.configurationClass;
		}

		public DeferredImportSelector getImportSelector() {
			return this.importSelector;
		}
	}


	private static class DeferredImportSelectorGrouping {

		private final DeferredImportSelector.Group group;

		private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

		DeferredImportSelectorGrouping(Group group) {
			this.group = group;
		}

		public void add(DeferredImportSelectorHolder deferredImport) {
			this.deferredImports.add(deferredImport);
		}

		/**
		 * Return the imports defined by the group.
		 * @return each import with its associated configuration class	每个导入都带有与其关联的配置类
		 */
		public Iterable<Group.Entry> getImports() {
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				// 核心方法，在springboot中此方法会去SpringFactoriesLoader类中加载自动化配置类
				this.group.process(deferredImport.getConfigurationClass().getMetadata(),
						deferredImport.getImportSelector());
			}
			// 获取已经取得的配置类
			return this.group.selectImports();
		}

		public Predicate<String> getCandidateFilter() {
			Predicate<String> mergedFilter = DEFAULT_EXCLUSION_FILTER;
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				Predicate<String> selectorFilter = deferredImport.getImportSelector().getExclusionFilter();
				if (selectorFilter != null) {
					mergedFilter = mergedFilter.or(selectorFilter);
				}
			}
			return mergedFilter;
		}
	}


	private static class DefaultDeferredImportSelectorGroup implements Group {

		private final List<Entry> imports = new ArrayList<>();

		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			for (String importClassName : selector.selectImports(metadata)) {
				this.imports.add(new Entry(metadata, importClassName));
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			return this.imports;
		}
	}


	/**
	 * Simple wrapper that allows annotated source classes to be dealt with
	 * in a uniform manner, regardless of how they are loaded.
	 *
	 * 简单的包装器，允许以统一的方式处理带注释的源类，而不管它们是如何加载的。
	 */
	private class SourceClass implements Ordered {

		private final Object source;  // Class or MetadataReader

		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				this.metadata = AnnotationMetadata.introspect((Class<?>) source);
			}
			else {
				this.metadata = ((MetadataReader) source).getAnnotationMetadata();
			}
		}

		public final AnnotationMetadata getMetadata() {
			return this.metadata;
		}

		@Override
		public int getOrder() {
			Integer order = ConfigurationClassUtils.getOrder(this.metadata);
			return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
		}

		public Class<?> loadClass() throws ClassNotFoundException {
			if (this.source instanceof Class) {
				return (Class<?>) this.source;
			}
			String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
			return ClassUtils.forName(className, resourceLoader.getClassLoader());
		}

		public boolean isAssignable(Class<?> clazz) throws IOException {
			if (this.source instanceof Class) {
				return clazz.isAssignableFrom((Class<?>) this.source);
			}
			return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
		}

		public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
			if (this.source instanceof Class) {
				return new ConfigurationClass((Class<?>) this.source, importedBy);
			}
			return new ConfigurationClass((MetadataReader) this.source, importedBy);
		}

		public Collection<SourceClass> getMemberClasses() throws IOException {
			Object sourceToProcess = this.source;
			if (sourceToProcess instanceof Class) {
				Class<?> sourceClass = (Class<?>) sourceToProcess;
				try {
					Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
					List<SourceClass> members = new ArrayList<>(declaredClasses.length);
					for (Class<?> declaredClass : declaredClasses) {
						members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER));
					}
					return members;
				}
				catch (NoClassDefFoundError err) {
					// getDeclaredClasses() failed because of non-resolvable dependencies
					// -> fall back to ASM below
					sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
				}
			}

			// ASM-based resolution - safe for non-resolvable classes as well
			MetadataReader sourceReader = (MetadataReader) sourceToProcess;
			String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
			List<SourceClass> members = new ArrayList<>(memberClassNames.length);
			for (String memberClassName : memberClassNames) {
				try {
					members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER));
				}
				catch (IOException ex) {
					// Let's skip it if it's not resolvable - we're just looking for candidates
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to resolve member class [" + memberClassName +
								"] - not considering it as a configuration class candidate");
					}
				}
			}
			return members;
		}

		public SourceClass getSuperClass() throws IOException {
			if (this.source instanceof Class) {
				return asSourceClass(((Class<?>) this.source).getSuperclass(), DEFAULT_EXCLUSION_FILTER);
			}
			return asSourceClass(
					((MetadataReader) this.source).getClassMetadata().getSuperClassName(), DEFAULT_EXCLUSION_FILTER);
		}

		public Set<SourceClass> getInterfaces() throws IOException {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Class<?> ifcClass : sourceClass.getInterfaces()) {
					result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER));
				}
			}
			else {
				for (String className : this.metadata.getInterfaceNames()) {
					result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER));
				}
			}
			return result;
		}

		public Set<SourceClass> getAnnotations() {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
					Class<?> annType = ann.annotationType();
					if (!annType.getName().startsWith("java")) {
						try {
							result.add(asSourceClass(annType, DEFAULT_EXCLUSION_FILTER));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			else {
				for (String className : this.metadata.getAnnotationTypes()) {
					if (!className.startsWith("java")) {
						try {
							result.add(getRelated(className));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			return result;
		}

		public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
			Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
			if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
				return Collections.emptySet();
			}
			String[] classNames = (String[]) annotationAttributes.get(attribute);
			Set<SourceClass> result = new LinkedHashSet<>();
			for (String className : classNames) {
				result.add(getRelated(className));
			}
			return result;
		}

		private SourceClass getRelated(String className) throws IOException {
			if (this.source instanceof Class) {
				try {
					Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
					return asSourceClass(clazz, DEFAULT_EXCLUSION_FILTER);
				}
				catch (ClassNotFoundException ex) {
					// Ignore -> fall back to ASM next, except for core java types.
					if (className.startsWith("java")) {
						throw new NestedIOException("Failed to load class [" + className + "]", ex);
					}
					return new SourceClass(metadataReaderFactory.getMetadataReader(className));
				}
			}
			return asSourceClass(className, DEFAULT_EXCLUSION_FILTER);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof SourceClass &&
					this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
		}

		@Override
		public int hashCode() {
			return this.metadata.getClassName().hashCode();
		}

		@Override
		public String toString() {
			return this.metadata.getClassName();
		}
	}


	/**
	 * {@link Problem} registered upon detection of a circular {@link Import}.
	 */
	private static class CircularImportProblem extends Problem {

		public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
			super(String.format("A circular @Import has been detected: " +
					"Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
					"already present in the current import stack %s", importStack.element().getSimpleName(),
					attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
					new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
		}
	}

}
