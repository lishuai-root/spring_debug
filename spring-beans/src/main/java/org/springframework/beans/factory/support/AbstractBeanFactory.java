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

package org.springframework.beans.factory.support;

import java.beans.PropertyEditor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Abstract base class for {@link org.springframework.beans.factory.BeanFactory}
 * implementations, providing the full capabilities of the
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} SPI.
 * Does <i>not</i> assume a listable bean factory: can therefore also be used
 * as base class for bean factory implementations which obtain bean definitions
 * from some backend resource (where bean definition access is an expensive operation).
 *
 * <p>This class provides a singleton cache (through its base class
 * {@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * singleton/prototype determination, {@link org.springframework.beans.factory.FactoryBean}
 * handling, aliases, bean definition merging for child bean definitions,
 * and bean destruction ({@link org.springframework.beans.factory.DisposableBean}
 * interface, custom destroy methods). Furthermore, it can manage a bean factory
 * hierarchy (delegating to the parent in case of an unknown bean), through implementing
 * the {@link org.springframework.beans.factory.HierarchicalBeanFactory} interface.
 *
 * <p>The main template methods to be implemented by subclasses are
 * {@link #getBeanDefinition} and {@link #createBean}, retrieving a bean definition
 * for a given bean name and creating a bean instance for a given bean definition,
 * respectively. Default implementations of those operations can be found in
 * {@link DefaultListableBeanFactory} and {@link AbstractAutowireCapableBeanFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @since 15 April 2001
 * @see #getBeanDefinition
 * @see #createBean
 * @see AbstractAutowireCapableBeanFactory#createBean
 * @see DefaultListableBeanFactory#getBeanDefinition
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

	/** Parent bean factory, for bean inheritance support. */
	@Nullable
	private BeanFactory parentBeanFactory;

	/** ClassLoader to resolve bean class names with, if necessary. */
	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	/** ClassLoader to temporarily resolve bean class names with, if necessary.
	 *
	 * 如有必要，ClassLoader 用于临时解析 bean 类名。
	 * */
	@Nullable
	private ClassLoader tempClassLoader;

	/** Whether to cache bean metadata or rather reobtain it for every access. */
	private boolean cacheBeanMetadata = true;

	/** Resolution strategy for expressions in bean definition values. bean 定义值中表达式的解析策略。*/
	@Nullable
	private BeanExpressionResolver beanExpressionResolver;

	/** Spring ConversionService to use instead of PropertyEditors.
	 *
	 * 使用 Spring ConversionService 代替 PropertyEditors。
	 * */
	@Nullable
	private ConversionService conversionService;

	/** Custom PropertyEditorRegistrars to apply to the beans of this factory.
	 *
	 *  自定义 PropertyEditorRegistrars 应用于此工厂的 bean
	 * */
	private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

	/** Custom PropertyEditors to apply to the beans of this factory.
	 *
	 * 定制PropertyEditor应用于该工厂的bean
	 * */
	private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

	/** A custom TypeConverter to use, overriding the default PropertyEditor mechanism. */
	@Nullable
	private TypeConverter typeConverter;

	/** String resolvers to apply e.g. to annotation attribute values.
	 *
	 * 字符串解析器适用于注解属性值
	 * */
	private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

	/** BeanPostProcessors to apply. */
	private final List<BeanPostProcessor> beanPostProcessors = new BeanPostProcessorCacheAwareList();

	/** Cache of pre-filtered post-processors. */
	@Nullable
	private volatile BeanPostProcessorCache beanPostProcessorCache;

	/** Map from scope identifier String to corresponding Scope. 从范围标识符字符串映射到相应的范围。*/
	private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

	/** Map from bean name to merged RootBeanDefinition. 从 bean 名称映射到合并的 RootBeanDefinition*/
	private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

	/** Names of beans that have already been created at least once. 已至少创建一次的 bean 的名称*/
	private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	/** Names of beans that are currently in creation. 当前正在创建的 bean 的名称。*/
	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
			new NamedThreadLocal<>("Prototype beans currently in creation");

	/** Application startup metrics. 应用程序启动指标。5.3新加**/
	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	/**
	 * Create a new AbstractBeanFactory.
	 */
	public AbstractBeanFactory() {
	}

	/**
	 * Create a new AbstractBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 * @see #getBean
	 */
	public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this.parentBeanFactory = parentBeanFactory;
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {

		// 此方法是实际获取bean的方法，也是触发依赖注入的方法
		return doGetBean(name, null, null, false);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return doGetBean(name, requiredType, null, false);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		return doGetBean(name, null, args, false);
	}

	/**
	 * 返回一个实例，该实例可以指定bean的共享或独立
	 *
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object... args)
			throws BeansException {

		// 返回一个实例，该实例可以指定bean的共享或独立
		return doGetBean(name, requiredType, args, false);
	}

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 *
	 * 返回指定 bean 的一个实例，该实例可以是共享的，也可以是独立的。
	 *
	 * 1.通过缓存获取bean实例，解决循环依赖问题
	 * 2.检查bean是否原型模式，并且正在创建，如果是，抛出异常
	 * 		如果bean是原型模式，且正在创建，会无休止的递归创建
	 * 3.如果当前工厂不存在bean定义信息，且存在夫工厂，则委托给夫工厂创建
	 * 4.合并bean定义信息，并检查是否抽象类
	 * 5.如果bean存在前置依赖bean，递归创建依赖bean
	 * 6.根据不同模式创建bean实例
	 *   6.1.单例模式：调用getSingleton(String beanName, ObjectFactory<?> singletonFactory)方法创建
	 *   6.2.原型模式：调用createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)方法创建
	 *   6.3.Scope：根据不同的作用域采用不同的策略创建实例
	 * 7.对bean实例进行类型转换，转换成需要的类型返回
	 *
	 *
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @param typeCheckOnly whether the instance is obtained for a type check,
	 * not for actual use
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(
			String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
			throws BeansException {

		//提取对应的beanName，当bean对象实现FactoryBean接口之后就会变成&beanName，同时如果存在别名，也需要把别名进行转换
		String beanName = transformedBeanName(name);
		Object beanInstance;

		if ("org.springframework.aop.aspectj.AspectJPointcutAdvisor#0".equals(beanName)){
			System.out.println(beanName);
		}

		// Eagerly check singleton cache for manually registered singletons.
		// 提前检查单例缓存中是否有手动注册的单例对象，跟循环依赖有关联
		/**
		 * 此处主要用来解决循环依赖问题，
		 * eg：
		 *
		 * class A{
		 * 		@Autowired
		 * 	 	B b;
		 * }
		 *
		 * class B{
		 *     @Autowired
		 *     A a;
		 * }
		 * 假设先创建了A实例，A实例中引用了B实例，在A实例填充属性时，会引起B实例的创建(递归创建)，但是在填充B实例的属性时，
		 * 又会引起A实例的创建，从而进入死循环。
		 *
		 * spirng使用earlySingletonObjects(俗称二级缓存)保存了已经实例化但是并未初始化的bean实例。
		 *
		 * 假设先创建了A实例，并把A实例保存在earlySingletonObjects中，并标记A实例正在创建，A实例中引用了B实例，在A实例填充属性时，
		 * 会引起B实例的创建(递归创建)，但是在填充B实例的属性时，又会引起A实例的创建，此时getSingleton(beanName);可以从earlySingletonObjects
		 * 中已经实例化的A实例对象(此时的A实例并未初始化)，从而避免循环依赖问题。
		 *
		 */
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			if (logger.isTraceEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}

			// 返回对象的实例，当你实现了FactoryBean接口的对象，需要获取具体的对象的时候就需要此方法来进行获取了
			beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}

		else {
			// Fail if we're already creating this bean instance:如果我们已经在创建这个 bean 实例，则失败：
			// We're assumably within a circular reference.我们大概处于循环引用中。
			// 当对象都是单例的时候会尝试解决循环依赖的问题，但是原型模式下如果存在循环依赖的情况，那么直接抛出异常
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			// Check if bean definition exists in this factory.检查此工厂中是否存在 bean 定义。
			// 如果bean定义不存在，就检查父工厂是否有
			BeanFactory parentBeanFactory = getParentBeanFactory();

			// 如果beanDefinitionMap中也就是在所有已经加载的类中不包含beanName，那么就尝试从父容器中获取
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				// Not found -> check parent.
				// 获取name对应的规范名称【全类名】，如果name前面有'&'，则会返回'&'+规范名称【全类名】
				String nameToLookup = originalBeanName(name);

				// 如果父工厂是AbstractBeanFactory的实例
				if (parentBeanFactory instanceof AbstractBeanFactory) {

					// 调用父工厂的doGetBean方法，就是该方法。【递归】
					return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
							nameToLookup, requiredType, args, typeCheckOnly);
				}
				else if (args != null) {
					// Delegation to parent with explicit args.使用显式参数委托给父母。
					// 如果有创建bean实例时要使用的参数
					// 使用父工厂获取该bean对象,通bean全类名和创建bean实例时要使用的参数
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else if (requiredType != null) {
					// No args -> delegate to standard getBean method.没有参数 -> 委托给标准的 getBean 方法。
					// 使用父工厂获取该bean对象,通bean全类名和所需的bean类型
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
				else {

					// 使用父工厂获取bean，通过bean全类名
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}

			// 如果不是做类型检查，那么表示要创建bean，此处在集合中做一个记录
			if (!typeCheckOnly) {

				// 为beanName标记为已经创建（或将要创建）
				markBeanAsCreated(beanName);
			}

			// 记录启动步骤，默认什么也不干
			StartupStep beanCreation = this.applicationStartup.start("spring.beans.instantiate")
					.tag("beanName", name);
			try {
				if (requiredType != null) {

					//	记录启动步骤，默认什么也不干
					beanCreation.tag("beanType", requiredType::toString);
				}

				//	获取beanName对应的RootBeanDefinition
				// 此处做了BeanDefinition对象的转换，当从xml文件中加载beanDefinition对象的时候，封装的对象是GenericBeanDefinition,
				// 此处要做类型转换，如果是子类bean的话，会合并父类的相关属性
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

				// 检查合并 Bean 定义，不合格会引发验证异常。只检查了是否抽象类
				checkMergedBeanDefinition(mbd, beanName, args);

				// Guarantee initialization of beans that the current bean depends on.保证当前 bean 所依赖的 bean 的初始化。
				// 如果存在依赖的bean的话，那么则优先实例化依赖的bean
				String[] dependsOn = mbd.getDependsOn();

				/**
				 * 优先递归实例化依赖bean
				 */
				if (dependsOn != null) {

					// 如果存在依赖，则需要递归实例化依赖的bean
					for (String dep : dependsOn) {

						// 如果beanName已注册依赖于dependentBeanName的关系
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}

						// 注册各个bean的依赖关系，方便进行销毁
						registerDependentBean(dep, beanName);
						try {

							// 递归优先实例化被依赖的Bean
							getBean(dep);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

				// Create bean instance.
				// 创建单例bean的实例对象
				if (mbd.isSingleton()) {

					/**
					 * 返回以beanName的(原始)单例对象，如果尚未注册，则使用singletonFactory创建并注册一个对象
					 */
					sharedInstance = getSingleton(beanName, () -> {
						try {

							// 为给定的合并后BeanDefinition(和参数)创建一个bean实例
							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							// Explicitly remove instance from singleton cache: It might have been put there
							// eagerly by the creation process, to allow for circular reference resolution.
							// Also remove any beans that received a temporary reference to the bean.
							// 从单例缓存中显式删除实例：它可能已被创建过程急切地放在那里，以允许循环引用解析。
							// 还要删除收到对 bean 的临时引用的所有 bean。

							// 销毁给定的bean。如果找到相应的一次性Bean实例，则委托给destroyBean
							destroySingleton(beanName);
							throw ex;
						}
					});

					// 从beanInstance中获取公开的Bean对象，主要处理beanInstance是FactoryBean对象的情况，如果不是
					// FactoryBean会直接返回beanInstance实例
					beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}

				// 原型模式(非单例)的bean对象创建
				else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.这是一个原型 -> 创建一个新实例。
					Object prototypeInstance = null;
					try {

						// 创建Prototype对象前的准备工作，默认实现将beanName添加到prototypesCurrentlyInCreation中
						beforePrototypeCreation(beanName);

						// 为mbd(和参数)创建一个bean实例
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {

						// 创建完prototype实例后的回调，默认是将beanName从prototypesCurrentlyInCreation移除
						afterPrototypeCreation(beanName);
					}

					// 从beanInstance中获取公开的Bean对象，主要处理beanInstance是FactoryBean对象的情况，如果不是
					// FactoryBean会直接返回beanInstance实例
					beanInstance = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}
				// 自定义Scope创建bean实例
				else {

					// 指定的scope上实例化bean
					String scopeName = mbd.getScope();

					// 检测scopeName是否为空(全部是空格也算空)
					if (!StringUtils.hasLength(scopeName)) {
						throw new IllegalStateException("No scope name defined for bean ´" + beanName + "'");
					}

					// 从scopes中获取scopeName对于的Scope对象
					Scope scope = this.scopes.get(scopeName);
					if (scope == null) {

						// 抛出非法状态异常：没有名为'scopeName'的scope注册
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {

						// 从scope中获取beanName对应的实例对象
						Object scopedInstance = scope.get(beanName, () -> {

							// 创建Prototype对象前的准备工作，默认实现 将beanName添加到prototypesCurrentlyInCreation中
							beforePrototypeCreation(beanName);
							try {

								// 为mbd(和参数)创建一个bean实例
								return createBean(beanName, mbd, args);
							}
							finally {

								// 创建完prototype实例后的回调，默认是将beanName从prototypesCurrentlyInCreation移除
								afterPrototypeCreation(beanName);
							}
						});

						// 从beanInstance中获取公开的Bean对象，主要处理beanInstance是FactoryBean对象的情况，如果不是
						// FactoryBean会直接返回beanInstance实例
						beanInstance = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					}
					catch (IllegalStateException ex) {

						// 抛出Bean创建异常：作用域 'scopeName' 对于当前线程是不活动的；如果您打算从单个实例引用它，请考虑为此
						// beanDefinition一个作用域代理
						throw new ScopeNotActiveException(beanName, scopeName, ex);
					}
				}
			}
			catch (BeansException ex) {

				// 记录步骤
				beanCreation.tag("exception", ex.getClass().toString());
				beanCreation.tag("message", String.valueOf(ex.getMessage()));

				// 在Bean创建失败后，对缓存的元数据执行适当的清理
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
			finally {
				beanCreation.end();
			}
		}

		// 对bean进行类型检查，beanInstance是否是requiredType类型
		return adaptBeanInstance(name, beanInstance, requiredType);
	}

	/**
	 * 检查bean是否requiredType类型，如果不是将bean转换成requiredType类型，如果失败抛出异常
	 *
	 * @param name
	 * @param bean
	 * @param requiredType
	 * @param <T>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	<T> T adaptBeanInstance(String name, Object bean, @Nullable Class<?> requiredType) {
		// Check if required type matches the type of the actual bean instance.
		// 检查所需类型是否与实际 bean 实例的类型匹配。
		// 如果requiredType不为null&&bean不是requiredType的实例
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				// 获取此BeanFactory使用的类型转换器，将bean转换为requiredType
				Object convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);

				if (convertedBean == null) {
					// 抛出Bean不是必要类型的异常
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				return (T) convertedBean;
			}
			catch (TypeMismatchException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}

		// 如果bean是requiredType类型，直接返回bean
		return (T) bean;
	}

	/**
	 * 该bean工厂是否包含具有给定名称的bean定义或外部注册的singleton实例
	 *
	 * @param name the name of the bean to query
	 * @return
	 */
	@Override
	public boolean containsBean(String name) {
		// 获取name最终的规范名称【最终别名】
		String beanName = transformedBeanName(name);
		// 如果beanName存在于singletonObjects【单例对象的高速缓存Map集合】中，
		// 或者beanDefinitionMap【Bean定义对象映射】中存在该beanName的BeanDefinition对象
		if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
		}
		// Not found -> check parent.
		// 获取父工厂
		BeanFactory parentBeanFactory = getParentBeanFactory();
		return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			if (beanInstance instanceof FactoryBean) {
				return (BeanFactoryUtils.isFactoryDereference(name) || ((FactoryBean<?>) beanInstance).isSingleton());
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isSingleton(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// In case of FactoryBean, return singleton status of created object if not a dereference.
		if (mbd.isSingleton()) {
			if (isFactoryBean(beanName, mbd)) {
				if (BeanFactoryUtils.isFactoryDereference(name)) {
					return true;
				}
				FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
				return factoryBean.isSingleton();
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isPrototype(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isPrototype()) {
			// In case of FactoryBean, return singleton status of created object if not a dereference.
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
		}

		// Singleton or scoped - not a prototype.
		// However, FactoryBean may still produce a prototype object...
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			return false;
		}
		if (isFactoryBean(beanName, mbd)) {
			FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
			return ((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
					!fb.isSingleton());
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, typeToMatch, true);
	}

	/**
	 *
	 * 检查具有给定名称的bean是否与指定的类型匹配
	 *
	 * 检查beanName对应的bean类型是否与typeToMatch匹配
	 * 如果beanName对应的是FactoryBean，则检查beanName对应的FactoryBean创建的bean的类型是否与typeToMatch匹配
	 *
	 * Internal extended variant of {@link #isTypeMatch(String, ResolvableType)}
	 * to check whether the bean with the given name matches the specified type. Allow
	 * additional constraints to be applied to ensure that beans are not created early.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a
	 * {@code ResolvableType})
	 * @return {@code true} if the bean type matches, {@code false} if it
	 * doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.2
	 * @see #getBean
	 * @see #getType
	 */
	protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		// 去除name开头的'&'字符,获取name最终的规范名称【最终别名或者是全类名】
		String beanName = transformedBeanName(name);

		// 判断name是否为FactoryBean的解引用名
		// name是以'&'开头，就是FactoryBean的解引用
		boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);

		// Check manually registered singletons.
		// 检查手动注册的单例
		// 获取beanName的单例对象，但不允许创建引用
		Object beanInstance = getSingleton(beanName, false);

		// 如果成功获取到单例对象而且该单例对象的类型又不是NullBean
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {

			// 如果单例对象是FactoryBean的实例
			if (beanInstance instanceof FactoryBean) {

				// 如果name不是FactoryBean的解引用名
				if (!isFactoryDereference) {

					// 获取beanInstance(工厂ean)创建出来的bean的类型
					Class<?> type = getTypeForFactoryBean((FactoryBean<?>) beanInstance);

					// 获取到的bean类型，是否不为空且属于要匹配的类型
					return (type != null && typeToMatch.isAssignableFrom(type));
				}
				else {

					// 返回单例对象是否属于要匹配的类型的实例
					return typeToMatch.isInstance(beanInstance);
				}
			}

			// 如果name不是FactoryBean的解引用名
			else if (!isFactoryDereference) {

				// 返回单例对象是否属于要匹配的类型的实例
				if (typeToMatch.isInstance(beanInstance)) {
					// Direct match for exposed instance?
					// 直接匹配暴露的实例？
					return true;
				}

				// 如果要匹配的类型包含泛型参数而且此bean工厂包含beanName所指的BeanDefinition定义
				else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
					// Generics potentially only match on the target class, not on the proxy...
					// 泛型可能只匹配目标类，而不匹配代理......
					// 获取beanName所对应的合并RootBeanDefinition
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

					// 取mbd的目标类型
					Class<?> targetType = mbd.getTargetType();

					// 如果成功获取到了mbd的目标类型而且目标类型与单例对象的类型不同
					if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {

						// Check raw class match as well, making sure it's exposed on the proxy.
						// 还要检查原始类匹配，确保它在代理上公开。
						// 获取TypeToMatch的封装Class对象
						Class<?> classToMatch = typeToMatch.resolve();

						// 如果成功获取Class对象而且单例对象不是该Class对象的实例
						if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {

							// 表示要查询的Bean名与要匹配的类型不匹配
							return false;
						}

						// 如果mbd的目标类型属于要匹配的类型
						if (typeToMatch.isAssignableFrom(targetType)) {

							// 表示要查询的Bean名与要匹配的类型匹配
							return true;
						}
					}
					// 获取mbd的目标类型
					ResolvableType resolvableType = mbd.targetType;

					// 如果获取mbd的目标类型失败
					if (resolvableType == null) {

						// 获取mbd的工厂方法返回类型作为mbd的目标类型
						resolvableType = mbd.factoryMethodReturnType;
					}

					// 如果成功获取到了mbd的目标类型而且该目标类型属于要匹配的类型 就返回true，否则返回false。
					return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
				}
			}

			// 如果beanName的单例对象不是FactoryBean的实例或者name是FactoryBean的解引用名
			return false;
		}
		// 如果该工厂的单例对象注册器包含beanName所指的单例对象 但该工厂没有beanName对应的BeanDefinition对象
		else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			// null instance registered
			// 注册了null实例,即 beanName对应的实例是NullBean实例，因前面已经处理了beanName不是NullBean的情况，
			// 再加上该工厂没有对应beanName的BeanDefinition对象
			return false;
		}

		// No singleton instance found -> check bean definition. 未找到单例实例 -> 检查 bean 定义。
		// 获取该工厂的父级工厂
		BeanFactory parentBeanFactory = getParentBeanFactory();

		// 如果父级工厂不为null且该工厂没有包含beanName的BeanDefinition
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent. 在此工厂中找不到 bean 定义 -> 委托给父级
			// 递归交给父工厂判断，将判断结果返回出去
			return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
		}

		// Retrieve corresponding bean definition. 检索相应的 bean 定义.
		// 获取beanName对应的合并后的本地RootBeanDefinition
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// 获取mbd的BeanDefinitionHolder
		// BeanDefinitionHolder就是BeanDefinition的持有，同时持有的包括BeanDefinition的名称和别名
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

		// Setup the types that we want to match against 设置我们想要匹配的类型
		// 获取要匹配的类型
		Class<?> classToMatch = typeToMatch.resolve();

		// 如果typeToMatch未设置类型
		if (classToMatch == null) {

			// 默认使用FactoryBean作为要匹配的Class类型
			classToMatch = FactoryBean.class;
		}

		// 如果FactoryBean不是要匹配的类型，则在要匹配的类型数组加上FactoryBean的Class
		Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
				new Class<?>[] {classToMatch} : new Class<?>[] {FactoryBean.class, classToMatch});


		// Attempt to predict the bean type 尝试预测 bean 类型
		Class<?> predictedType = null;

		// We're looking for a regular reference but we're a factory bean that has
		// a decorated bean definition. The target bean should be the same type
		// as FactoryBean would ultimately return.
		// 我们正在寻找一个常规引用，但我们是一个工厂 bean，它有一个装饰过的 bean 定义。
		// 目标 bean 应该与 FactoryBean 最终返回的类型相同。
		/**
		 * isFactoryDereference : FactoryBean是否解引用
		 *
		 * isFactoryBean(beanName, mbd) : 判断beanName，mbd所指的bean是否FactoryBean类型
		 *
		 * 如果不是FactoryBean解引用且mbd有配置BeanDefinitionHolder且beanName,mbd所指的bean是FactoryBean
		 */
		if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
			// We should only attempt if the user explicitly set lazy-init to true
			// and we know the merged bean definition is for a factory bean.
			// 我们应该只在用户明确地将 lazy-init 设置为 true 并且我们知道合并的 bean 定义是针对工厂 bean 时才尝试。
			if (!mbd.isLazyInit() || allowFactoryBeanInit) {

				// 获取dbd的beanName，dbd的BeanDefinition，mbd所对应的合并后RootBeanDefinition
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);

				// 预测dbd的beanName,tbd,typesToMatch的Bean类型
				Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);

				// 如果目标类型不为null，且targetType不属于FactoryBean
				if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {

					// 预测bean类型就为该目标类型
					predictedType = targetType;
				}
			}
		}

		// If we couldn't use the target type, try regular prediction. 如果我们无法使用目标类型，请尝试常规预测。
		// 如果无法获得预测bean类型
		if (predictedType == null) {

			// 获取beanName，mbd，typeToMatch所对应的Bean类型作为预测bean类型
			predictedType = predictBeanType(beanName, mbd, typesToMatch);

			// 如果没有成功获取到预测bean类型，返回false，表示不匹配
			if (predictedType == null) {
				return false;
			}
		}

		// Attempt to get the actual ResolvableType for the bean. 尝试获取 bean 的实际 ResolvableType。
		// ResolvableType：可以看作是封装JavaType的元信息类
		ResolvableType beanType = null;

		// If it's a FactoryBean, we want to look at what it creates, not the factory class.
		// 如果它是 FactoryBean，我们想看看它创建了什么，而不是工厂类。
		// 如果predictedType属于FactoryBean
		if (FactoryBean.class.isAssignableFrom(predictedType)) {

			// 如果没有beanName的单例对象且beanName不是指FactoryBean解引用
			if (beanInstance == null && !isFactoryDereference) {

				// 获取beanName,mbd对应的FactoryBean要创建的bean的类型赋值给beanType
				beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);

				// 解析beanType以得到predictedType
				predictedType = beanType.resolve();

				// 如果无法获取beanName，mbd对应的FactoryBean要创建的bean类型
				if (predictedType == null) {

					// 返回false表示类型不匹配
					return false;
				}
			}
		}
		// beanName是指FactoryBean解引用
		else if (isFactoryDereference) {
			// Special case: A SmartInstantiationAwareBeanPostProcessor returned a non-FactoryBean
			// type but we nevertheless are being asked to dereference a FactoryBean...
			// Let's check the original bean class and proceed with it if it is a FactoryBean.
			// 特殊情况：SmartInstantiationAwareBeanPostProcessor 返回了一个非 FactoryBean 类型，
			// 但我们仍然被要求取消对 FactoryBean 的引用...
			// 让我们检查原始 bean 类，如果它是 FactoryBean，则继续。

			// 预测mbd所指的bean的最终类型
			predictedType = predictBeanType(beanName, mbd, FactoryBean.class);

			// 如果预测不到，或者预测到的类型为FactoryBean
			if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
				return false;
			}
		}

		// We don't have an exact type but if bean definition target type or the factory
		// method return type matches the predicted type then we can use that.
		// 我们没有确切的类型，但如果 bean 定义目标类型或工厂方法返回类型与预测类型匹配，那么我们可以使用它。
		// 如果没有拿到beanName,mbd对应的FactoryBean要创建的bean的类型
		if (beanType == null) {

			// 声明一个已定义类型，默认使用mbd的目标类型
			ResolvableType definedType = mbd.targetType;

			// 如果没有拿到definedType
			if (definedType == null) {

				// 获取mbd的工厂方法的返回类型
				definedType = mbd.factoryMethodReturnType;
			}

			// 如果拿到了definedType且definedType所封装的Class对象与预测类型相同
			if (definedType != null && definedType.resolve() == predictedType) {

				// beanType就为definedType
				beanType = definedType;
			}
		}

		// If we have a bean type use it so that generics are considered 如果我们有一个 bean 类型，请使用它，以便考虑泛型
		// 如果拿到了beanType
		if (beanType != null) {

			// 返回beanType是否属于typeToMatch的结果
			return typeToMatch.isAssignableFrom(beanType);
		}

		// If we don't have a bean type, fallback to the predicted type 如果我们没有 bean 类型，则回退到预测的类型
		// 如果我们没有bean类型，返回predictedType否属于typeToMatch的结果
		return typeToMatch.isAssignableFrom(predictedType);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		return getType(name, true);
	}

	/**
	 * 确定具有给定名称的bean类型。更具体地说，确定getBean将针对给定名称返回的对象的类型
	 *
	 * @param name the name of the bean to query
	 * @param allowFactoryBeanInit whether a {@code FactoryBean} may get initialized
	 * just for the purpose of determining its object type
	 * @return
	 * @throws NoSuchBeanDefinitionException
	 */
	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {

		// 获取name对应的规范名称【全类名】,包括以'&'开头的name
		String beanName = transformedBeanName(name);

		// Check manually registered singletons.
		// 检查手动注册的单例,获取beanName注册的单例对象，但不会创建早期引用
		Object beanInstance = getSingleton(beanName, false);

		// 如果成功获取到beanName的单例对象，且该单例对象又不是NullBean,NullBean用于表示null
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {

			// 如果bean的单例对象是FactoryBean的实例且name不是FactoryBean的解引用
			if (beanInstance instanceof FactoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
				return getTypeForFactoryBean((FactoryBean<?>) beanInstance);
			}
			else {
				return beanInstance.getClass();
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.getType(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// Check decorated bean definition, if any: We assume it'll be easier
		// to determine the decorated bean's type than the proxy's type.
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
		if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
			RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
			Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
			if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
				return targetClass;
			}
		}

		Class<?> beanClass = predictBeanType(beanName, mbd);

		// Check bean class whether we're dealing with a FactoryBean.
		if (beanClass != null && FactoryBean.class.isAssignableFrom(beanClass)) {
			if (!BeanFactoryUtils.isFactoryDereference(name)) {
				// If it's a FactoryBean, we want to look at what it creates, not at the factory class.
				return getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
			}
			else {
				return beanClass;
			}
		}
		else {
			return (!BeanFactoryUtils.isFactoryDereference(name) ? beanClass : null);
		}
	}

	@Override
	public String[] getAliases(String name) {
		String beanName = transformedBeanName(name);
		List<String> aliases = new ArrayList<>();
		boolean factoryPrefix = name.startsWith(FACTORY_BEAN_PREFIX);
		String fullBeanName = beanName;
		if (factoryPrefix) {
			fullBeanName = FACTORY_BEAN_PREFIX + beanName;
		}
		if (!fullBeanName.equals(name)) {
			aliases.add(fullBeanName);
		}
		String[] retrievedAliases = super.getAliases(beanName);
		String prefix = factoryPrefix ? FACTORY_BEAN_PREFIX : "";
		for (String retrievedAlias : retrievedAliases) {
			String alias = prefix + retrievedAlias;
			if (!alias.equals(name)) {
				aliases.add(alias);
			}
		}
		if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null) {
				aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
			}
		}
		return StringUtils.toStringArray(aliases);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return this.parentBeanFactory;
	}

	@Override
	public boolean containsLocalBean(String name) {
		String beanName = transformedBeanName(name);
		return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
				(!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		// 如果当前已经有一个父级bean工厂，且传进来的父级bean工厂与当前父级bean工厂不是同一个
		if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
			throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
		}
		if (this == parentBeanFactory) {
			throw new IllegalStateException("Cannot set parent bean factory to self");
		}
		this.parentBeanFactory = parentBeanFactory;
	}

	/**
	 * 为beanFactory设置类加载器
	 *
	 * @param beanClassLoader the class loader to use,
	 */
	@Override
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		/**
		 *  如果beanClassLoader为空，则按照以下顺序使用类加载
		 *
		 *  当前线程上下文类加载器 -> org.springframework.util.ClassUtils类的类加载器 -> 系统类加载器 -> null
		 */
		this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	@Nullable
	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	/**
	 * 指定用于类型匹配目的的临时类加载器。默认为 none，只需使用标准 bean ClassLoader。
	 *
	 * @param tempClassLoader
	 */
	@Override
	public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
		this.tempClassLoader = tempClassLoader;
	}

	@Override
	@Nullable
	public ClassLoader getTempClassLoader() {
		return this.tempClassLoader;
	}

	@Override
	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	@Override
	public boolean isCacheBeanMetadata() {
		return this.cacheBeanMetadata;
	}

	@Override
	public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
		this.beanExpressionResolver = resolver;
	}

	@Override
	@Nullable
	public BeanExpressionResolver getBeanExpressionResolver() {
		return this.beanExpressionResolver;
	}

	@Override
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
		Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
		this.propertyEditorRegistrars.add(registrar);
	}

	/**
	 * Return the set of PropertyEditorRegistrars.
	 */
	public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
		return this.propertyEditorRegistrars;
	}

	@Override
	public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
		Assert.notNull(requiredType, "Required type must not be null");
		Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
		this.customEditors.put(requiredType, propertyEditorClass);
	}

	@Override
	public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
		registerCustomEditors(registry);
	}

	/**
	 * Return the map of custom editors, with Classes as keys and PropertyEditor classes as values.
	 */
	public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
		return this.customEditors;
	}

	@Override
	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}

	/**
	 * Return the custom TypeConverter to use, if any.返回要使用的自定义 TypeConverter（如果有）。
	 * @return the custom TypeConverter, or {@code null} if none specified
	 */
	@Nullable
	protected TypeConverter getCustomTypeConverter() {
		return this.typeConverter;
	}

	/**
	 * 获取此BeanFactory使用的类型转换器。这可能是每次调用都有新实例，因TypeConverters通常 不是线程安全的.
	 *
	 * @return 此BeanFactory使用的类型转换器:默认情况下优先返回自定义的类型转换器【{@link #getCustomTypeConverter()}】;
	 * 			获取不到时,返回一个新的SimpleTypeConverter对象
	 */
	@Override
	public TypeConverter getTypeConverter() {

		// 获取自定义的TypeConverter
		TypeConverter customConverter = getCustomTypeConverter();

		// 如果有自定义的类型转换器，返回自定义的
		if (customConverter != null) {
			return customConverter;
		}
		else {
			// Build default TypeConverter, registering custom editors.
			// 构建默认的TypeConverter，注册自定义编辑器
			// SimpleTypeConverter:不在特定目标对象上运行的TypeConverter接口的简单实现。
			// 这是使用完整的BeanWrapperImpl实例来实现 任意类型转换需求的替代方法，同时
			// 使用相同的转换算法（包括委托给PropertyEditor和ConversionService）。
			// 每次调用该方法都会新建一个类型转换器，因为SimpleTypeConverter不是线程安全的
			// 新建一个SimpleTypeConverter对象
			SimpleTypeConverter typeConverter = new SimpleTypeConverter();

			// 让typeConverter引用该工厂的类型转换的服务接口
			typeConverter.setConversionService(getConversionService());

			// 将工厂中所有PropertyEditor注册到typeConverter中
			registerCustomEditors(typeConverter);
			return typeConverter;
		}
	}

	@Override
	public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		this.embeddedValueResolvers.add(valueResolver);
	}

	@Override
	public boolean hasEmbeddedValueResolver() {
		return !this.embeddedValueResolvers.isEmpty();
	}

	/**
	 * 解析嵌套的值(如果value是表达式会解析出该表达式的值)
	 *
	 * @param value the value to resolve
	 * @return
	 */
	@Override
	@Nullable
	public String resolveEmbeddedValue(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String result = value;

		// SpringBoot默认存放一个PropertySourcesPlaceholderConfigurer，该类注意用于针对当前
		// Spring Environment 及其PropertySource解析bean定义属性值和@Value注释中的${...}占位符
		// 遍历该工厂的所有字符串解析器
		for (StringValueResolver resolver : this.embeddedValueResolvers) {

			// 解析result，将解析后的值重新赋值给result
			result = resolver.resolveStringValue(result);

			// 如果result为null,结束该循环，并返回null
			if (result == null) {
				return null;
			}
		}

		// 返回解析结果
		return result;
	}

	/**
	 * 添加一个新的 BeanPostProcessor，它将应用于此工厂创建的 bean。在出厂配置期间调用。
	 *
	 * 注：此处提交的后处理器将按注册顺序申请；通过实现 {@link org.springframework.core.Ordered}
	 * 接口表达的任何排序语义都将被忽略。请注意，自动检测的后处理器（例如作为 ApplicationContext 中的 bean）
	 * 将始终在以编程方式注册的后处理器之后应用。
	 *
	 * @param beanPostProcessor the post-processor to register
	 */
	@Override
	public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
		// Remove from old position, if any
		// 删除原有的beanPostProcessor
		this.beanPostProcessors.remove(beanPostProcessor);

		/**
		 * 		老版本会有这些标志位的修改
		 *
		 * 		// Track whether it is instantiation/destruction aware
		 * 		// 此处是为了设置某些状态变量，这些状态变量会影响后续的执行流程，只需要判断是否是指定的类型，然后设置标志位即可
		 * 		if (beanPostProcessor instanceof InstantiationAwareBeanPostProcessor) {
		 * 			// 该变量表示beanfactory是否已注册过InstantiationAwareBeanPostProcessor
		 * 			this.hasInstantiationAwareBeanPostProcessors = true;
		 *                }
		 * 		if (beanPostProcessor instanceof DestructionAwareBeanPostProcessor) {
		 * 			// 该变量表示beanfactory是否已注册过DestructionAwareBeanPostProcessor
		 * 			this.hasDestructionAwareBeanPostProcessors = true;
		 *        }
		 */

		// Add to end of list
		// 将beanPostProcessor添加到beanPostProcessors中
		this.beanPostProcessors.add(beanPostProcessor);
	}

	/**
	 * Add new BeanPostProcessors that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * @since 5.3
	 * @see #addBeanPostProcessor
	 */
	public void addBeanPostProcessors(Collection<? extends BeanPostProcessor> beanPostProcessors) {
		this.beanPostProcessors.removeAll(beanPostProcessors);
		this.beanPostProcessors.addAll(beanPostProcessors);
	}

	@Override
	public int getBeanPostProcessorCount() {
		return this.beanPostProcessors.size();
	}

	/**
	 * Return the list of BeanPostProcessors that will get applied
	 * to beans created with this factory.
	 */
	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}

	/**
	 * Return the internal cache of pre-filtered post-processors,
	 * freshly (re-)building it if necessary.
	 *
	 * 返回预过滤后处理器的内部缓存，必要时重新（重新）构建它。
	 *
	 * @since 5.3
	 */
	BeanPostProcessorCache getBeanPostProcessorCache() {
		BeanPostProcessorCache bpCache = this.beanPostProcessorCache;
		if (bpCache == null) {
			bpCache = new BeanPostProcessorCache();
			for (BeanPostProcessor bp : this.beanPostProcessors) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					bpCache.instantiationAware.add((InstantiationAwareBeanPostProcessor) bp);
					if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
						bpCache.smartInstantiationAware.add((SmartInstantiationAwareBeanPostProcessor) bp);
					}
				}
				if (bp instanceof DestructionAwareBeanPostProcessor) {
					bpCache.destructionAware.add((DestructionAwareBeanPostProcessor) bp);
				}
				if (bp instanceof MergedBeanDefinitionPostProcessor) {
					bpCache.mergedDefinition.add((MergedBeanDefinitionPostProcessor) bp);
				}
			}
			this.beanPostProcessorCache = bpCache;
		}
		return bpCache;
	}

	/**
	 * Return whether this factory holds a InstantiationAwareBeanPostProcessor
	 * that will get applied to singleton beans on creation.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
	 */
	protected boolean hasInstantiationAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().instantiationAware.isEmpty();
	}

	/**
	 * Return whether this factory holds a DestructionAwareBeanPostProcessor
	 * that will get applied to singleton beans on shutdown.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean hasDestructionAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().destructionAware.isEmpty();
	}

	@Override
	public void registerScope(String scopeName, Scope scope) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		Assert.notNull(scope, "Scope must not be null");
		if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
			throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
		}
		Scope previous = this.scopes.put(scopeName, scope);
		if (previous != null && previous != scope) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
			}
		}
	}

	@Override
	public String[] getRegisteredScopeNames() {
		return StringUtils.toStringArray(this.scopes.keySet());
	}

	@Override
	@Nullable
	public Scope getRegisteredScope(String scopeName) {

		// 如果传入的作用域名为null，抛出异常
		Assert.notNull(scopeName, "Scope identifier must not be null");

		// 从映射的linkedHashMap中获取传入的作用域名对应的作用域对象并返回
		return this.scopes.get(scopeName);
	}

	@Override
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		Assert.notNull(applicationStartup, "applicationStartup should not be null");
		this.applicationStartup = applicationStartup;
	}

	@Override
	public ApplicationStartup getApplicationStartup() {
		return this.applicationStartup;
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		Assert.notNull(otherFactory, "BeanFactory must not be null");
		setBeanClassLoader(otherFactory.getBeanClassLoader());
		setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
		setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
		setConversionService(otherFactory.getConversionService());
		if (otherFactory instanceof AbstractBeanFactory) {
			AbstractBeanFactory otherAbstractFactory = (AbstractBeanFactory) otherFactory;
			this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
			this.customEditors.putAll(otherAbstractFactory.customEditors);
			this.typeConverter = otherAbstractFactory.typeConverter;
			this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
			this.scopes.putAll(otherAbstractFactory.scopes);
		}
		else {
			setTypeConverter(otherFactory.getTypeConverter());
			String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
			for (String scopeName : otherScopeNames) {
				this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
			}
		}
	}

	/**
	 * Return a 'merged' BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * <p>This {@code getMergedBeanDefinition} considers bean definition
	 * in ancestors as well.
	 * @param name the name of the bean to retrieve the merged definition for
	 * (may be an alias)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	@Override
	public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {

		// 获取name对应的规范名称【全类名】
		String beanName = transformedBeanName(name);
		// Efficiently check whether bean definition exists in this factory.
		// 有效检查该工厂中是否存在bean定义。
		// 如果当前bean工厂不包含具有beanName的bean定义且父工厂是ConfigurableBeanFactory的实例
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {

			// 使用父工厂返回beanName的合并BeanDefinition【如有必要，将子bean定义与其父级合并】
			return ((ConfigurableBeanFactory) getParentBeanFactory()).getMergedBeanDefinition(beanName);
		}
		// Resolve merged bean definition locally.
		// 本地解决合并的bean定义
		return getMergedLocalBeanDefinition(beanName);
	}

	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		//去除name开头的'&'字符,获取name最终的规范名称【最终别名或者是全类名】：
		String beanName = transformedBeanName(name);

		/**
		 * 获取beanName注册的（原始）单例对象（如果单例对象不存在，且处于正在创建状态）
		 * 1.如果单例对象已经创建完成，返回bean
		 * 2.如果单例对象正在创建：
		 * 		2.1.如果对象已经放到二级缓存，返回实例完但是没有填充属性的bean
		 * 		2.2.如果对象没有放到二级缓存，返回null
		 */
		Object beanInstance = getSingleton(beanName, false);

		// 如果beanName对应的单例对象不为空，
		if (beanInstance != null) {

			// 返回beanName对应的单例对象，是否工厂bean
			return (beanInstance instanceof FactoryBean);
		}

		// No singleton instance found -> check bean definition. 未找到单例实例 -> 检查 bean 定义。
		// 如果不存在beanName对应的Definition，&& 父级beanFactory是ConfigurableBeanFactory，则调用父BeanFactory判断是否为FactoryBean
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent. 在此工厂中找不到 bean 定义 -> 委托给父级。
		// 尝试在父工厂中确定name是否为FactoryBean，【递归】
			return ((ConfigurableBeanFactory) getParentBeanFactory()).isFactoryBean(name);
		}

		// 如果该bean对应于子bean定义，则遍历父bean定义。
		// 判断(beanName和beanName对应的合并后BeanDefinition)所指的bean是否FactoryBean并将结果返回出去
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}

	@Override
	public boolean isActuallyInCreation(String beanName) {
		return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
	}

	/**
	 * Return whether the specified prototype bean is currently in creation
	 *
	 * 返回指定的原型 bean 当前是否正在创建中
	 *
	 * (within the current thread在当前线程中).
	 * @param beanName the name of the bean
	 */
	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
	}

	/**
	 * Callback before prototype creation.创建原型之前的回调。
	 * <p>The default implementation register the prototype as currently in creation.
	 * 默认实现将原型注册为当前正在创建。
	 *
	 * @param beanName the name of the prototype about to be created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {

		// prototypesCurrentlyInCreation：当前正在创建的bean名称
		// 从prototypesCurrentlyInCreation中获取线程安全的当前正在创建的Bean对象名
		Object curVal = this.prototypesCurrentlyInCreation.get();

		if (curVal == null) {

			// 将beanName设置到prototypesCurrentlyInCreation中
			this.prototypesCurrentlyInCreation.set(beanName);
		}
		else if (curVal instanceof String) {

			// 定义一个HashSet对象存放prototypesCurrentlyInCreation原有Bean名和beanName
			Set<String> beanNameSet = new HashSet<>(2);

			beanNameSet.add((String) curVal);
			beanNameSet.add(beanName);

			// 将beanNameSet设置到prototypesCurrentlyInCreation中
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		}
		else {

			// 否则，curlValue就只会是HashSet对象将curlVal强转为Set对象
			Set<String> beanNameSet = (Set<String>) curVal;

			// 将beanName添加到beanNameSet中
			beanNameSet.add(beanName);
		}
	}

	/**
	 * 创建原型后回调。 默认实现将原型标记为不再创建。
	 *
	 * Callback after prototype creation.
	 * <p>The default implementation marks the prototype as not in creation anymore.
	 * @param beanName the name of the prototype that has been created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {

		// 从prototypesCurrentlyInCreation获取当前正在创建的Bean名
		Object curVal = this.prototypesCurrentlyInCreation.get();

		// 如果是String，直接删除
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		}

		// 如果是set，从set中删除beanName，删除后set如果为空，删除线程本地变量的值
		else if (curVal instanceof Set) {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	@Override
	public void destroyBean(String beanName, Object beanInstance) {
		destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
	}

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to the given bean definition.
	 * @param beanName the name of the bean definition
	 * @param bean the bean instance to destroy
	 * @param mbd the merged bean definition
	 */
	protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
		new DisposableBeanAdapter(
				bean, beanName, mbd, getBeanPostProcessorCache().destructionAware).destroy();
	}

	@Override
	public void destroyScopedBean(String beanName) {
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isSingleton() || mbd.isPrototype()) {
			throw new IllegalArgumentException(
					"Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
		}
		String scopeName = mbd.getScope();
		Scope scope = this.scopes.get(scopeName);
		if (scope == null) {
			throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
		}
		Object bean = scope.remove(beanName);
		if (bean != null) {
			destroyBean(beanName, bean, mbd);
		}
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Return the bean name, stripping out the factory dereference prefix if necessary,
	 * and resolving aliases to canonical names.
	 * 返回 bean 名称，必要时去除工厂取消引用前缀，并将别名解析为规范名称。
	 *
	 * @param name the user-specified name
	 * @return the transformed bean name
	 */
	protected String transformedBeanName(String name) {
		// 去除开头的'&'字符，返回剩余的字符串得到转换后的Bean名称，然后通过递归形式在aliasMap【别名映射到规范名称集合】中得到最终的规范名称
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

	/**
	 * Determine the original bean name, resolving locally defined aliases to canonical names.
	 * @param name the user-specified name
	 * @return the original bean name
	 */
	protected String originalBeanName(String name) {
		String beanName = transformedBeanName(name);
		if (name.startsWith(FACTORY_BEAN_PREFIX)) {
			beanName = FACTORY_BEAN_PREFIX + beanName;
		}
		return beanName;
	}

	/**
	 * Initialize the given BeanWrapper with the custom editors registered
	 * with this factory. To be called for BeanWrappers that will create
	 * and populate bean instances.
	 * 使用在此工厂注册的自定义编辑器初始化给定的 BeanWrapper。为将创建和填充 bean 实例的 BeanWrappers 调用。
	 *
	 * <p>The default implementation delegates to {@link #registerCustomEditors}.
	 * Can be overridden in subclasses.
	 * 默认实现委托给 {@link registerCustomEditors}。可以在子类中覆盖。
	 *
	 * @param bw the BeanWrapper to initialize
	 */
	protected void initBeanWrapper(BeanWrapper bw) {

		// 使用该工厂的ConversionService来作为bw的ConversionService，用于转换属性值，以替换JavaBeans PropertyEditor
		bw.setConversionService(getConversionService());

		// 将工厂中所有PropertyEditor注册到bw中
		registerCustomEditors(bw);
	}

	/**
	 * 将工厂中所有PropertyEditor注册到PropertyEditorRegistry中
	 *
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * <p>To be called for BeanWrappers that will create and populate bean
	 * instances, and for SimpleTypeConverter used for constructor argument
	 * and factory method type conversion.
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	protected void registerCustomEditors(PropertyEditorRegistry registry) {

		// PropertyEditorRegistrySupport是PropertyEditorRegistry接口的默认实现
		if (registry instanceof PropertyEditorRegistrySupport) {

			// 激活仅用于配置目的的配置值编辑器
			((PropertyEditorRegistrySupport) registry).useConfigValueEditors();
		}

		// PropertyEditorRegistrar：各种业务的PropertyEditorSupport一般都会先注册到PropertyEditorRegistrar中，再通过PropertyEditorRegistrar
		// 将PropertyEditorSupport注册到PropertyEditorRegistry中
		// 如果该工厂的propertyEditorRegistrar列表不为空
		if (!this.propertyEditorRegistrars.isEmpty()) {

			// propertyEditorRegistrars默认情况下只有一个元素对象，该对象为ResourceEditorRegistrar。
			// 遍历propertyEditorRegistrars
			for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
				try {

					// ResourceEditorRegistrar会将ResourceEditor, InputStreamEditor, InputSourceEditor,
					// FileEditor, URLEditor, URIEditor, ClassEditor, ClassArrayEditor注册到registry中，
					// 如果registry已配置了ResourcePatternResolver,则还将注册ResourceArrayPropertyEditor
					// 将registrar中的所有PropertyEditor注册到PropertyEditorRegistry中
					registrar.registerCustomEditors(registry);
				}
				catch (BeanCreationException ex) {
					Throwable rootCause = ex.getMostSpecificCause();
					if (rootCause instanceof BeanCurrentlyInCreationException) {
						BeanCreationException bce = (BeanCreationException) rootCause;
						String bceBeanName = bce.getBeanName();
						if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
							if (logger.isDebugEnabled()) {
								logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
										"] failed because it tried to obtain currently created bean '" +
										ex.getBeanName() + "': " + ex.getMessage());
							}
							onSuppressedException(ex);
							continue;
						}
					}
					throw ex;
				}
			}
		}

		// 如果该工厂的自定义PropertyEditor集合有元素，在SpringBoot中，customEditors默认是空的
		if (!this.customEditors.isEmpty()) {

			// 遍历自定义PropertyEditor集合,将其元素注册到registry中
			this.customEditors.forEach((requiredType, editorClass) ->
					registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
		}
	}


	/**
	 * 获取beanName合并后的本地RootBeanDefinition
	 *
	 * 如果已经存在了beanName对应的RootBeanDefinition则直接返回对应的RootBeanDefinition，
	 *
	 * 如果不存在，或者需要重新定义，则获取将beanName对应的BeanDefinition和父级BeanDefinition或者已经存在的RootBeanDefinition进行合并，
	 *
	 * 产生一个新的RootBeanDefinition，并返回新的RootBeanDefinition
	 *
	 * Return a merged RootBeanDefinition, traversing the parent bean definition
	 * if the specified bean corresponds to a child bean definition.
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		// Quick check on the concurrent map first, with minimal locking.
		// 首先以最小的锁定快速检测并发映射。
		// 从bean名称映射到合并的RootBeanDefinition的集合中获取beanName对应的RootBeanDefinition
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);

		// 如果mbd不为null 且 不需要重新合并定义
		if (mbd != null && !mbd.stale) {

			// 返回对应的RootBeanDefinition
			return mbd;
		}

		// 获取beanName对应的合并Bean定义，如果beanName对应的BeanDefinition是子BeanDefinition,
		// 则通过与父级合并返回RootBeanDefinition
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}

	/**
	 *
	 * 获取beanName对应的合并后的RootBeanDefinition:直接交给
	 * getMergedBeanDefinition(String, BeanDefinition,BeanDefinition)处理，第三个参数传null
	 *
	 * Return a RootBeanDefinition for the given top-level bean, by merging with
	 * the parent if the given bean's definition is a child bean definition.
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {

		return getMergedBeanDefinition(beanName, bd, null);
	}

	/**
	 *
	 *  获取beanName对应的合并后的RootBeanDefinition
	 *  spring中用户自定义的的beanDefinition大致分为三种：GenericBeanDefinition， AnnotatedBeanDefinition， RootBeanDefinition
	 *  GenericBeanDefinition：从配置文件中解析到的beanDifinition
	 *  AnnotatedBeanDefinition：被注解扫描到的beanDefinition
	 *  RootBeanDefinition：解析后的beanDefinition
	 *
	 *  GenericBeanDefinition和AnnotatedBeanDefinition都是在解析配置文件或者扫描component-scan时创建的原始beanDifinition，此时的
	 *  beanDifinition完全是按照配置文件或者class定义生成的，并未做任何解析工作，只是记录的bean的基本定义信息，可以称之为原始定义(源信息)
	 *
	 *  RootBeanDefinition：是被解析后的GenericBeanDefinition或AnnotatedBeanDefinition，RootBeanDefinition中不仅包含了bean的原始定义信息，还包含了解析后的bean定义信息
	 *  比如：bean的类型，bean是否工厂方法，bean的具体工厂方法等
	 *
	 *  GenericBeanDefinition和AnnotatedBeanDefinition只是bean定义信息暂时的形态，RootBeanDefinition是最终的目标形态，RootBeanDefinition包含了创建实例所需的所有信息
	 *
	 *  RootBeanDefinition可以理解为GenericBeanDefinition或者AnnotatedBeanDefinition更详细的描述(最终形态)
	 *
	 *  根据给定beanDefinition创建RootBeanDifiniition
	 *  	1.如果没有父类，则用当前beanDefinition创建RootBeanDefinition
	 *  	2.如果有父类，则用父类的beanDefinition创建RootBeanDefinition，并用当前beanDefinition信息覆盖部分父类beanDefinition定义信息
	 *
	 * Return a RootBeanDefinition for the given bean, by merging with the
	 * parent if the given bean's definition is a child bean definition.
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @param containingBd the containing bean definition in case of inner bean,
	 * or {@code null} in case of a top-level bean
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {

		// 同步：使用从bean名称映射到合并的RootBeanDefinition集合进行加锁
		synchronized (this.mergedBeanDefinitions) {

			// 用于存储bd的MergedBeanDefinition
			RootBeanDefinition mbd = null;

			//该变量表示从bean名称映射到合并的RootBeanDefinition集合中取到的mbd且该mbd需要重新合并定义
			RootBeanDefinition previous = null;

			// Check with full lock now in order to enforce the same merged instance.
			// 立即检查完全锁定，以强制执行相同的合并实例,如果没有包含bean定义
			if (containingBd == null) {

				// 从bean名称映射到合并的RootBeanDefinition集合中获取beanName对应的BeanDefinition作为mbd
				mbd = this.mergedBeanDefinitions.get(beanName);
			}

			// 如果mbd为null或者mdb需要重新合并定义
			if (mbd == null || mbd.stale) {

				// 将mdn作为previous，如果mbd不为空，previous则保存重新定义之前的RootBeanDefinition
				previous = mbd;

				// 如果获取不到原始BeanDefinition的父Bean名
				if (bd.getParentName() == null) {

					// Use copy of given root bean definition.
					// 如果原始BeanDefinition是RootBeanDefinition对象，使用给定的RootBeanDefinition的副本
					if (bd instanceof RootBeanDefinition) {

						// 克隆一份bd的Bean定义赋值给mdb
						mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
					}
					else {

						// 创建一个新的RootBeanDefinition作为bd的深层副本并赋值给mbd
						mbd = new RootBeanDefinition(bd);
					}
				}
				else {
					// Child bean definition: needs to be merged with parent.
					// 子bean定义：需要与父bean合并,定义一个父级BeanDefinition变量
					BeanDefinition pbd;
					try {
						// 获取bd的父级Bean对应的最终别名
						String parentBeanName = transformedBeanName(bd.getParentName());

						// 如果当前bean名不等于父级bean名
						if (!beanName.equals(parentBeanName)) {

							// 获取parentBeanName的"合并的"BeanDefinition赋值给pdb
							pbd = getMergedBeanDefinition(parentBeanName);
						}
						// 如果父定义的beanName与bd的beanName相同，则拿到父BeanFactory，
						// 只有在存在父BeanFactory的情况下，才允许父定义beanName与自己相同，否则就是将自己设置为父定义
						else {
							BeanFactory parent = getParentBeanFactory();

							// 如果父BeanFactory是ConfigurableBeanFactory，则通过父BeanFactory获取父定义的MergedBeanDefinition
							if (parent instanceof ConfigurableBeanFactory) {

								// 使用父工厂获取parentBeanName对应的合并BeanDefinition赋值给pdb
								pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
							}
							else {

								// 如果父工厂不是ConfigurableBeanFactory,抛出没有此类bean定义异常，父级bean名为parentBeanName等于名为beanName的bean名；
								// 没有AbstractBeanFactory父级无法解决
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
												"': cannot be resolved without a ConfigurableBeanFactory parent");
							}
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}
					// Deep copy with overridden values.
					// 使用父定义pbd构建一个新的RootBeanDefinition对象
					mbd = new RootBeanDefinition(pbd);
					// 使用原始bd定义信息覆盖父级的定义信息:
					// 1. 如果在给定的bean定义中指定，则将覆盖beanClass
					// 2. 将始终从给定的bean定义中获取abstract,scope,lazyInit,autowireMode,
					// 			dependencyCheck和dependsOn
					// 3. 将给定bean定义中ConstructorArgumentValues,propertyValues,
					// 			methodOverrides 添加到现有的bean定义中
					// 4. 如果在给定的bean定义中指定，将覆盖factoryBeanName,factoryMethodName,
					// 		initMethodName,和destroyMethodName
					mbd.overrideFrom(bd);
				}

				// Set default singleton scope, if not configured before.
				// 设置默认的单例作用域（如果之前未配置）,如果mbd之前没有配置过作用域
				if (!StringUtils.hasLength(mbd.getScope())) {
					mbd.setScope(SCOPE_SINGLETON);
				}

				// A bean contained in a non-singleton bean cannot be a singleton itself.
				// Let's correct this on the fly here, since this might be the result of
				// parent-child merging for the outer bean, in which case the original inner bean
				// definition will not have inherited the merged outer bean's singleton status.
				// 非单例bean中包含的bean本身不能是单例。
				// 让我们在此即时进行更正，因为这可能是外部bean的父子合并的结果，在这种情况下，
				// 原始内部bean定义将不会继承合并的外部bean的单例状态。
				// 如果有传包含bean定义且包含bean定义不是单例但mbd又是单例
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {

					// 让mbd的作用域设置为跟containingBd的作用域一样
					mbd.setScope(containingBd.getScope());
				}

				// Cache the merged bean definition for the time being
				// (it might still get re-merged later on in order to pick up metadata changes)
				// 暂时缓存合并的bean定义(稍后可能仍会重新合并以获取元数据更正),
				// 如果没有传入包含bean定义 且 当前工厂是同意缓存bean元数据
				if (containingBd == null && isCacheBeanMetadata()) {

					//将beanName和mbd的关系添加到 从bean名称映射到合并的RootBeanDefinition集合中
					this.mergedBeanDefinitions.put(beanName, mbd);
				}
			}

			// 如果存在上一个从bean名称映射到合并的RootBeanDefinition集合中取出的mbd
			// 且该mbd需要重新合并定义
			if (previous != null) {

				// 拿previous来对mdb进行重新合并定义：
				// 1. 设置mbd的目标类型为previous的目标类型
				// 2. 设置mbd的工厂bean标记为previous的工厂bean标记
				// 3. 设置mbd的用于缓存给定bean定义的确定的Class为previous的用于缓存给定bean定义的确定的Class
				// 4. 设置mbd的工厂方法返回类型为previous的工厂方法返回类型
				// 5. 设置mbd的用于缓存用于自省的唯一工厂方法候选为previous的用于缓存用于自省的唯一工厂方法候选
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}

			// 返回MergedBeanDefinition
			return mbd;
		}
	}

	private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {

		// ObjectUtils.nullSafeEquals:确定给定的对象是否相等，如果两个都为null返回true,
		// 如果其中一个为null，返回false,mbd和previous的当前Bean类名称相同，工厂bean名称相同，工厂方法名相同
		if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {

			// 获取mbd的目标类型
			ResolvableType targetType = mbd.targetType;

			// 获取previous的目标类型
			ResolvableType previousTargetType = previous.targetType;

			// 如果mdb的目标类型为null或者mdb的目标类型与previous的目标类型相同
			// 如果上一个版本的RootBeanDefinition和当前版本的RootBeanDefinition的targetType属性不同，
			// 用上一个版本的RootBeanDefinition的targetType属性覆盖当前版本的RootBeanDefinition的targetType属性
			if (targetType == null || targetType.equals(previousTargetType)) {

				// 设置mbd的目标类型为previous的目标类型
				mbd.targetType = previousTargetType;

				// 设置mbd的工厂bean标记为previous的工厂bean标记
				mbd.isFactoryBean = previous.isFactoryBean;

				// 设置mbd的用于缓存给定bean定义的确定的Class为previous的用于缓存给定bean定义的确定的Class
				mbd.resolvedTargetType = previous.resolvedTargetType;

				// 设置mbd的工厂方法返回类型为previous的工厂方法返回类型
				mbd.factoryMethodReturnType = previous.factoryMethodReturnType;

				// 设置mbd的用于缓存用于自省的唯一工厂方法候选为previous的用于缓存用于自省的唯一工厂方法候选
				mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
			}
		}
	}

	/**
	 *
	 * 检测当前BeanDefinition是否是抽象的，如果是抽象的，那么就抛出异常
	 *
	 * Check the given merged bean definition,
	 * potentially throwing validation exceptions.
	 * @param mbd the merged bean definition to check
	 * @param beanName the name of the bean
	 * @param args the arguments for bean creation, if any
	 * @throws BeanDefinitionStoreException in case of validation failure
	 */
	protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object[] args)
			throws BeanDefinitionStoreException {

		// 如果mbd所配置的bean是抽象的
		if (mbd.isAbstract()) {

			// 抛出Bean为抽象异常
			throw new BeanIsAbstractException(beanName);
		}
	}

	/**
	 * 删除指定 bean 的合并 bean 定义，在下次访问时重新创建它。
	 *
	 * 将beanName对应的合并后RootBeanDefinition对象标记为重新合并定义
	 *
	 * Remove the merged bean definition for the specified bean,
	 * recreating it on next access.
	 * @param beanName the bean name to clear the merged definition for
	 */
	protected void clearMergedBeanDefinition(String beanName) {

		// 从合并后的RootBeanDefinition集合中获取到beanName对应的RootBeanDefinition
		RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);

		// 如果存在，将beanName对应的RootBeanDefinition状态设置为需要重新合并定义
		if (bd != null) {
			bd.stale = true;
		}
	}

	/**
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * <p>Typically triggered after changes to the original bean definitions,
	 * e.g. after applying a {@code BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * @since 4.2
	 */
	public void clearMetadataCache() {
		this.mergedBeanDefinitions.forEach((beanName, bd) -> {
			if (!isBeanEligibleForMetadataCaching(beanName)) {
				bd.stale = true;
			}
		});
	}

	/**
	 * Resolve the bean class for the specified bean definition,
	 * resolving a bean class name into a Class reference (if necessary)
	 * and storing the resolved Class in the bean definition for further use.
	 *
	 * 为指定的 bean 定义解析 bean 类，将 bean 类名称解析为 Class 引用（如果需要）并将解析的 Class 存储在 bean 定义中以供进一步使用。
	 *
	 * @param mbd the merged bean definition to determine the class for
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the resolved bean class (or {@code null} if none)
	 * @throws CannotLoadBeanClassException if we failed to load the class
	 */
	@Nullable
	protected Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {

		try {

			// 判断mbd的定义信息中是否包含beanClass，并且是Class类型的，如果是直接返回，否则的话进行详细的解析
			if (mbd.hasBeanClass()) {

				// 如果mbd指定了bean类
				return mbd.getBeanClass();
			}

			// 进行详细的处理解析过程
			return doResolveBeanClass(mbd, typesToMatch);
		}
		catch (ClassNotFoundException ex) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (LinkageError err) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
		}
	}

	/**
	 *
	 * 获取mbd配置的bean类名，将bean类名解析为Class对象,并将解析后的Class对象缓存在mdb中以备将来使用
	 *
	 * @param mbd
	 * @param typesToMatch
	 * @return
	 * @throws ClassNotFoundException
	 */
	@Nullable
	private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
			throws ClassNotFoundException {

		// 获取该工厂用来加载bean的类加载器
		// 当前线程上下文类加载器 -> org.springframework.util.ClassUtils类的类加载器 -> 系统类加载器 -> null
		ClassLoader beanClassLoader = getBeanClassLoader();

		// 初始化动态类加载器为该工厂的加载bean用的类加载器
		// 如果该工厂有临时类加载器器时，该动态类加载器就是该工厂的临时类加载器
		ClassLoader dynamicLoader = beanClassLoader;

		// 表示mdb的配置的bean类名需要重新被dynamicLoader加载的标记，默认不需要
		boolean freshResolve = false;

		//如果有传入要匹配的类型
		if (!ObjectUtils.isEmpty(typesToMatch)) {

			// When just doing type checks (i.e. not creating an actual instance yet),
			// use the specified temporary class loader (e.g. in a weaving scenario).
			// 当只进行类型检查（即尚未创建实际实例）时，使用指定的临时类加载器（例如在编织场景中）。
			// 获取该工厂的临时类加载器，该临时类加载器专门用于类型匹配
			ClassLoader tempClassLoader = getTempClassLoader();

			if (tempClassLoader != null) {

				// 动态类加载器使用临时类加载器
				dynamicLoader = tempClassLoader;

				// 标记mdb的配置的bean类名需要重新被dynameicLoader加载
				freshResolve = true;

				// DecoratingClassLoader:装饰ClassLoader的基类,提供对排除的包和类的通用处理
				// 如果临时类加载器是DecoratingClassLoader的基类
				if (tempClassLoader instanceof DecoratingClassLoader) {

					// 将临时类加载器强转为DecoratingClassLoader实例
					DecoratingClassLoader dcl = (DecoratingClassLoader) tempClassLoader;

					// 对要匹配的类型进行在装饰类加载器中的排除，以交由父ClassLoader以常规方式处理
					for (Class<?> typeToMatch : typesToMatch) {

						dcl.excludeClass(typeToMatch.getName());
					}
				}
			}
		}

		// 从mbd中获取配置的bean类名
		String className = mbd.getBeanClassName();

		if (className != null) {

			//评估beanDefinition中包含的className,如果className是可解析表达式，会对其进行解析，否则直接返回className
			Object evaluated = evaluateBeanDefinitionString(className, mbd);

			// 判断className是否等于计算出的表达式的结果，如果不等于，那么判断evaluated的类型
			if (!className.equals(evaluated)) {
				// A dynamically resolved expression, supported as of 4.2...动态解析的表达式，从 4.2 开始支持...
				// 如果evaluated属于Class实例
				if (evaluated instanceof Class) {

					// 强转evaluated为Class对象并返回出去
					return (Class<?>) evaluated;
				}
				// 如果evaluated属于String实例
				else if (evaluated instanceof String) {

					// 将evaluated作为className的值
					className = (String) evaluated;

					// 标记mdb的配置的bean类名需要重新被dynamicLoader加载
					freshResolve = true;
				}
				else {

					// 抛出非法状态异常：无效的类名表达式结果：evaluated
					throw new IllegalStateException("Invalid class name expression result: " + evaluated);
				}
			}

			// 如果mdb的配置的bean类名需要重新被dynamicLoader加载
			if (freshResolve) {
				// When resolving against a temporary class loader, exit early in order
				// to avoid storing the resolved Class in the bean definition.
				// 当针对临时类加载器进行解析时，请尽早退出以避免将解析的 Class 存储在 bean 定义中
				// 如果动态类加载器不为null
				if (dynamicLoader != null) {
					try {

						// 使用dynamicLoader加载className对应的类型，并返回加载成功的Class对象
						return dynamicLoader.loadClass(className);
					}
					catch (ClassNotFoundException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
						}
					}
				}

				// 使用classLoader加载name对应的Class对象,该方式是Spring用于代替Class.forName()的方法，支持返回原始的类实例(如'int')
				// 和数组类名 (如'String[]')。此外，它还能够以Java source样式解析内部类名(如:'java.lang.Thread.State'
				// 而不是'java.lang.Thread$State')
				return ClassUtils.forName(className, dynamicLoader);
			}
		}

		// Resolve regularly, caching the result in the BeanDefinition...定期解析，将结果缓存在BeanDefinition中...
		// 使用classLoader加载当前BeanDefinition对象所配置的Bean类名的Class对象（每次调用都会重新加载,可通过
		// AbstractBeanDefinition#getBeanClass 获取缓存）
		return mbd.resolveBeanClass(beanClassLoader);
	}

	/**
	 * 通过表达式处理器解析beanDefinition中给定的字符串
	 *
	 * Evaluate the given String as contained in a bean definition,
	 * potentially resolving it as an expression.
	 *
	 * 评估包含在 bean 定义中的给定字符串，可能将其解析为表达式。
	 *
	 * @param value the value to check
	 * @param beanDefinition the bean definition that the value comes from
	 * @return the resolved value
	 * @see #setBeanExpressionResolver
	 */
	@Nullable
	protected Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {

		// 如果该工厂没有设置bean定义值中表达式的解析策略
		if (this.beanExpressionResolver == null) {

			// 直接返回要检查的值
			return value;
		}

		// 值所来自的bean定义的当前目标作用域
		Scope scope = null;

		if (beanDefinition != null) {

			// 获取值所来自的bean定义的当前目标作用域名
			String scopeName = beanDefinition.getScope();

			// 如果成功获得值所来自的bean定义的当前目标作用域名
			if (scopeName != null) {
				scope = getRegisteredScope(scopeName);
			}
		}

		// 评估value作为表达式（如果适用）；否则按原样返回值
		return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
	}


	/**
	 *
	 * 预测mdb所指的bean的最终bean类型(已处理bean实例的类型)
	 *
	 * Predict the eventual bean type (of the processed bean instance) for the
	 * specified bean. Called by {@link #getType} and {@link #isTypeMatch}.
	 * Does not need to handle FactoryBeans specifically, since it is only
	 * supposed to operate on the raw bean type.
	 * <p>This implementation is simplistic in that it is not able to
	 * handle factory methods and InstantiationAwareBeanPostProcessors.
	 * It only predicts the bean type correctly for a standard bean.
	 * To be overridden in subclasses, applying more sophisticated type detection.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition to determine the type for
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type of the bean, or {@code null} if not predictable
	 */
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {

		// 获取mbd的目标类型
		Class<?> targetType = mbd.getTargetType();

		// 如果成功获得mbd的目标类型
		if (targetType != null) {

			// 返回 mbd的目标类型
			return targetType;
		}

		// 如果有设置mbd的工厂方法名
		if (mbd.getFactoryMethodName() != null) {

			// 返回null，表示不可预测
			return null;
		}

		// 为mbd解析bean类，将beanName解析为Class引用（如果需要）,并将解析后的Class存储在mbd中以备将来使用。
		return resolveBeanClass(mbd, beanName, typesToMatch);
	}

	/**
	 *
	 *  根据名字和bean定义信息判断是否是FactoryBean
	 *  如果定义本身定义了isFactoryBean,那么直接返回结果，否则需要进行类型预测，会通过反射来判断名字对应的类是否是FactoryBean类型，如果是
	 *  返回true，如果不是返回false
	 * Check whether the given bean is defined as a {@link FactoryBean}.
	 * @param beanName the name of the bean
	 * @param mbd the corresponding bean definition
	 */
	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {

		// 定义一个存储mbd是否是FactoryBean的标记
		Boolean result = mbd.isFactoryBean;

		// 如果没有配置mbd的工厂Bean
		if (result == null) {

			// 根据预测指定bean的最终bean类型
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);

			// 如果成功获取最终bean类型，且最终bean类型属于FactoryBean类型
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));

			// 将result缓存在mbd中
			mbd.isFactoryBean = result;
		}

		// 如果不为空，直接返回
		return result;
	}

	/**
	 *
	 * 获取beanName,mbd所指的FactoryBean要创建的bean类型
	 *
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean
	 * already. The implementation is allowed to instantiate the target factory bean if
	 * {@code allowInit} is {@code true} and the type cannot be determined another way;
	 * otherwise it is restricted to introspecting signatures and related metadata.
	 * <p>If no {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} if set on the bean definition
	 * and {@code allowInit} is {@code true}, the default implementation will create
	 * the FactoryBean via {@code getBean} to call its {@code getObjectType} method.
	 * Subclasses are encouraged to optimize this, typically by inspecting the generic
	 * signature of the factory bean class or the factory method that creates it.
	 * If subclasses do instantiate the FactoryBean, they should consider trying the
	 * {@code getObjectType} method without fully populating the bean. If this fails,
	 * a full FactoryBean creation as performed by this implementation should be used
	 * as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param allowInit if initialization of the FactoryBean is permitted if the type
	 * cannot be determined another way
	 * @return the type for the bean if determinable, otherwise {@code ResolvableType.NONE}
	 * @since 5.2
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 */
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		if (allowInit && mbd.isSingleton()) {
			try {
				FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
				Class<?> objectType = getTypeForFactoryBean(factoryBean);
				return (objectType != null ? ResolvableType.forClass(objectType) : ResolvableType.NONE);
			}
			catch (BeanCreationException ex) {
				if (ex.contains(BeanCurrentlyInCreationException.class)) {
					logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
				}
				else if (mbd.isLazyInit()) {
					logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
				}
				else {
					logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
				}
				onSuppressedException(ex);
			}
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for a FactoryBean by inspecting its attributes for a
	 * {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} value.
	 * @param attributes the attributes to inspect
	 * @return a {@link ResolvableType} extracted from the attributes or
	 * {@code ResolvableType.NONE}
	 * @since 5.2
	 */
	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		if (attribute instanceof ResolvableType) {
			return (ResolvableType) attribute;
		}
		if (attribute instanceof Class) {
			return ResolvableType.forClass((Class<?>) attribute);
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean already.
	 * <p>The default implementation creates the FactoryBean via {@code getBean}
	 * to call its {@code getObjectType} method. Subclasses are encouraged to optimize
	 * this, typically by just instantiating the FactoryBean but not populating it yet,
	 * trying whether its {@code getObjectType} method already returns a type.
	 * If no type found, a full FactoryBean creation as performed by this implementation
	 * should be used as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 * @deprecated since 5.2 in favor of {@link #getTypeForFactoryBean(String, RootBeanDefinition, boolean)}
	 */
	@Nullable
	@Deprecated
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * Mark the specified bean as already created (or about to be created). 将指定的bean标记为已经创建(或即将创建)。
	 * <p>This allows the bean factory to optimize its caching for repeated
	 * creation of the specified bean. 这允许bean工厂优化其缓存，以重复创建指定的bean。
	 * @param beanName the name of the bean
	 */
	protected void markBeanAsCreated(String beanName) {

		// 如果beanName对应的实例还没有创建
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.mergedBeanDefinitions) {

				// DCL
				if (!this.alreadyCreated.contains(beanName)) {
					// Let the bean definition get re-merged now that we're actually creating
					// the bean... just in case some of its metadata changed in the meantime.
					// 现在我们实际上正在创建 bean，让 bean 定义重新合并……以防万一它的一些元数据在此期间发生了变化。
					// 删除beanName合并bean定义，在下次访问时重新创建
					clearMergedBeanDefinition(beanName);

					// 将beanName添加到已创建过集合中
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}

	/**
	 * Perform appropriate cleanup of cached metadata after bean creation failed.
	 *
	 * 在 bean 创建失败后对缓存的元数据执行适当的清理。
	 * @param beanName the name of the bean
	 */
	protected void cleanupAfterBeanCreationFailure(String beanName) {

		// mergedBeanDefinitions:从bean名称映射到合并的RootBeanDefinition
		// 使用mergedBeanDefinitions加锁，保证线程安全
		synchronized (this.mergedBeanDefinitions) {

			// alreadyCreated:至少已经创建一次的bean名称
			// 将beanName从alreadyCreated中删除
			this.alreadyCreated.remove(beanName);
		}
	}

	/**
	 * Determine whether the specified bean is eligible for having
	 * its bean definition metadata cached.
	 * @param beanName the name of the bean
	 * @return {@code true} if the bean's metadata may be cached
	 * at this point already
	 */
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return this.alreadyCreated.contains(beanName);
	}

	/**
	 * Remove the singleton instance (if any) for the given bean name,
	 * but only if it hasn't been used for other purposes than type checking.
	 * 删除给定 bean 名称的单例实例（如果有），但前提是它没有用于类型检查以外的其他目的。
	 *
	 * @param beanName the name of the bean
	 * @return {@code true} if actually removed, {@code false} otherwise
	 */
	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {

		// 如果已创建的bean名称中没有该beanName对应对象
		if (!this.alreadyCreated.contains(beanName)) {

			// 1.从该工厂单例缓存中删除具有给定名称的Bean。如果创建失败，则能够清理饿汉式注册 的单例
			// 2.FactoryBeanRegistrySupport重写以清除FactoryBean对象缓存
			// 3.AbstractAutowireCapableBeanFactory重写 以清除FactoryBean对象缓存
			removeSingleton(beanName);
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Check whether this factory's bean creation phase already started,
	 * i.e. whether any bean has been marked as created in the meantime.
	 * @since 4.2.2
	 * @see #markBeanAsCreated
	 */
	protected boolean hasBeanCreationStarted() {
		// 只要alreadyCreated【至少已经创建一次的bean名称集合】不为空，返回true，表示该工厂已经开始创建Bean,否则返回false
		return !this.alreadyCreated.isEmpty();
	}

	/**
	 * 从 beanInstannce 中获取公开的Bean对象，主要处理beanInstance是FactoryBean对象的情况，
	 * 如果不是FactoryBean会直接返回beanInstance实例
	 *
	 * Get the object for the given bean instance, either the bean
	 * instance itself or its created object in case of a FactoryBean.
	 * 获取给定 bean 实例的对象，如果是 FactoryBean，则是 bean 实例本身或其创建的对象。
	 *
	 * @param beanInstance the shared bean instance
	 * @param name the name that may include factory dereference prefix
	 * @param beanName the canonical bean name
	 * @param mbd the merged bean definition
	 * @return the object to expose for the bean
	 */
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		// Don't let calling code try to dereference the factory if the bean isn't a factory.
		// 如果 bean 不是工厂，不要让调用代码尝试取消对工厂的引用。
		// 如果name为FactoryBean的解引用.name是以'&'开头，就是FactoryBean的解引用
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			if (beanInstance instanceof NullBean) {
				return beanInstance;
			}
			if (!(beanInstance instanceof FactoryBean)) {
				throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
			}
			if (mbd != null) {

				// 更新mbd的是否是FactoryBean标记为true
				mbd.isFactoryBean = true;
			}
			return beanInstance;
		}

		// Now we have the bean instance, which may be a normal bean or a FactoryBean.
		// If it's a FactoryBean, we use it to create a bean instance, unless the
		// caller actually wants a reference to the factory.
		// 现在我们有了 bean 实例，它可能是普通 bean 或 FactoryBean。如果它是一个 FactoryBean，
		// 我们就用它来创建一个 bean 实例，除非调用者实际上想要一个对工厂的引用。
		// 如果beanInstance不是FactoryBean实例
		if (!(beanInstance instanceof FactoryBean)) {
			return beanInstance;
		}

		Object object = null;
		if (mbd != null) {

			// 更新mbd的是否是FactoryBean标记为true
			mbd.isFactoryBean = true;
		}
		else {

			// 从FactoryBean获得的对象缓存集中获取beanName对应的Bean对象
			object = getCachedObjectForFactoryBean(beanName);
		}
		if (object == null) {
			// Return bean instance from factory.从工厂返回 bean 实例。
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
			// Caches object obtained from FactoryBean if it is a singleton.如果是单例，则缓存从 FactoryBean 获得的对象。
			// 如果mbd为null&&该BeanFactory包含beanName的BeanDefinition对象。
			if (mbd == null && containsBeanDefinition(beanName)) {

				//获取beanName合并后的本地RootBeanDefinition对象
				mbd = getMergedLocalBeanDefinition(beanName);
			}

			// 是否是'synthetic'标记：mbd不为null && 返回此bean定义是否是"synthetic"【一般是指只有AOP相关的printCut配置或者
			// Advice配置才会将 synthetic设置为true】
			boolean synthetic = (mbd != null && mbd.isSynthetic());

			// 从BeanFactory对象中获取管理的对象.如果不是synthetic会对其对象进行该工厂的后置处理
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		return object;
	}

	/**
	 * Determine whether the given bean name is already in use within this factory,
	 * i.e. whether there is a local bean or alias registered under this name or
	 * an inner bean created with this name.
	 * @param beanName the name to check
	 */
	public boolean isBeanNameInUse(String beanName) {
		return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
	}

	/**
	 * Determine whether the given bean requires destruction on shutdown.
	 * <p>The default implementation checks the DisposableBean interface as well as
	 * a specified destroy method and registered DestructionAwareBeanPostProcessors.
	 * @param bean the bean instance to check
	 * @param mbd the corresponding bean definition
	 * @see org.springframework.beans.factory.DisposableBean
	 * @see AbstractBeanDefinition#getDestroyMethodName()
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
		return (bean.getClass() != NullBean.class && (DisposableBeanAdapter.hasDestroyMethod(bean, mbd) ||
				(hasDestructionAwareBeanPostProcessors() && DisposableBeanAdapter.hasApplicableProcessors(
						bean, getBeanPostProcessorCache().destructionAware))));
	}

	/**
	 * Add the given bean to the list of disposable beans in this factory,
	 * registering its DisposableBean interface and/or the given destroy method
	 * to be called on factory shutdown (if applicable). Only applies to singletons.
	 * 将给定的 bean 添加到此工厂的一次性 bean 列表中，注册其 DisposableBean 接口和/或在工厂关闭时调用的给定销毁方法（如果适用）。
	 * 仅适用于单身人士。
	 *
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 * @param mbd the bean definition for the bean
	 * @see RootBeanDefinition#isSingleton
	 * @see RootBeanDefinition#getDependsOn
	 * @see #registerDisposableBean
	 * @see #registerDependentBean
	 */
	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			if (mbd.isSingleton()) {
				// Register a DisposableBean implementation that performs all destruction
				// work for the given bean: DestructionAwareBeanPostProcessors,
				// DisposableBean interface, custom destroy method.
				// 注册一个 DisposableBean 实现，它为给定的 bean 执行所有销毁工作：
				// DestructionAwareBeanPostProcessors、DisposableBean 接口、自定义销毁方法。

				// 构建Bean对应的DisposableBeanAdapter对象，与beanName绑定到 注册中心的一次性Bean列表中
				registerDisposableBean(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware));
			}
			else {
				// A bean with a custom scope...
				// 具有自定已作用域的Bean
				// 获取mdb的作用域
				Scope scope = this.scopes.get(mbd.getScope());

				if (scope == null) {

					// 非法状态异常：无作用登记为作用名称'mbd.getScope'
					throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
				}

				// 注册一个回调，在销毁作用域中将构建Bean对应的DisposableBeanAdapter对象指定(或者在销毁整个作用域时执行，
				// 如果作用域没有销毁单个对象，而是全部终止)
				scope.registerDestructionCallback(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware));
			}
		}
	}


	//---------------------------------------------------------------------
	// Abstract methods to be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * Check if this bean factory contains a bean definition with the given name.
	 * Does not consider any hierarchy this factory may participate in.
	 * Invoked by {@code containsBean} when no cached singleton instance is found.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a bean definition with the given name
	 * @see #containsBean
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 */
	protected abstract boolean containsBeanDefinition(String beanName);

	/**
	 * Return the bean definition for the given bean name.
	 * Subclasses should normally implement caching, as this method is invoked
	 * by this class every time bean definition metadata is needed.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to find a definition for
	 * @return the BeanDefinition for this prototype name (never {@code null})
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException
	 * if the bean definition cannot be resolved
	 * @throws BeansException in case of errors
	 * @see RootBeanDefinition
	 * @see ChildBeanDefinition
	 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#getBeanDefinition
	 */
	protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

	/**
	 * Create a bean instance for the given merged bean definition (and arguments).
	 * The bean definition will already have been merged with the parent definition
	 * in case of a child definition.
	 * 为给定的合并 bean 定义（和参数）创建一个 bean 实例。如果是子定义，bean 定义将已经与父定义合并。
	 *
	 * <p>All bean retrieval methods delegate to this method for actual bean creation.
	 * 所有 bean 检索方法都委托给此方法以进行实际的 bean 创建。
	 *
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 */
	protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException;


	/**
	 * CopyOnWriteArrayList which resets the beanPostProcessorCache field on modification.
	 *
	 * @since 5.3
	 */
	private class BeanPostProcessorCacheAwareList extends CopyOnWriteArrayList<BeanPostProcessor> {

		@Override
		public BeanPostProcessor set(int index, BeanPostProcessor element) {
			BeanPostProcessor result = super.set(index, element);
			beanPostProcessorCache = null;
			return result;
		}

		@Override
		public boolean add(BeanPostProcessor o) {
			boolean success = super.add(o);
			beanPostProcessorCache = null;
			return success;
		}

		@Override
		public void add(int index, BeanPostProcessor element) {
			super.add(index, element);
			beanPostProcessorCache = null;
		}

		@Override
		public BeanPostProcessor remove(int index) {
			BeanPostProcessor result = super.remove(index);
			beanPostProcessorCache = null;
			return result;
		}

		@Override
		public boolean remove(Object o) {
			boolean success = super.remove(o);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean success = super.removeAll(c);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean success = super.retainAll(c);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean addAll(Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(c);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean addAll(int index, Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(index, c);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean removeIf(Predicate<? super BeanPostProcessor> filter) {
			boolean success = super.removeIf(filter);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public void replaceAll(UnaryOperator<BeanPostProcessor> operator) {
			super.replaceAll(operator);
			beanPostProcessorCache = null;
		}
	}


	/**
	 * Internal cache of pre-filtered post-processors.	预过滤后处理器的内部缓存。
	 *
	 * @since 5.3
	 */
	static class BeanPostProcessorCache {

		final List<InstantiationAwareBeanPostProcessor> instantiationAware = new ArrayList<>();

		final List<SmartInstantiationAwareBeanPostProcessor> smartInstantiationAware = new ArrayList<>();

		final List<DestructionAwareBeanPostProcessor> destructionAware = new ArrayList<>();

		final List<MergedBeanDefinitionPostProcessor> mergedDefinition = new ArrayList<>();
	}

}
