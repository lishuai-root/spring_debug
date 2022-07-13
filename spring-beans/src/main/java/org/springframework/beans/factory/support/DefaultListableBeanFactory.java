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

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.inject.Provider;

import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.core.OrderComparator;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.CompositeIterator;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Spring's default implementation of the {@link ConfigurableListableBeanFactory}
 * and {@link BeanDefinitionRegistry} interfaces: a full-fledged bean factory
 * based on bean definition metadata, extensible through post-processors.
 *
 * <p>Typical usage is registering all bean definitions first (possibly read
 * from a bean definition file), before accessing beans. Bean lookup by name
 * is therefore an inexpensive operation in a local bean definition table,
 * operating on pre-resolved bean definition metadata objects.
 *
 * <p>Note that readers for specific bean definition formats are typically
 * implemented separately rather than as bean factory subclasses: see for example
 * {@link org.springframework.beans.factory.xml.XmlBeanDefinitionReader}.
 *
 * <p>For an alternative implementation of the
 * {@link org.springframework.beans.factory.ListableBeanFactory} interface,
 * have a look at {@link StaticListableBeanFactory}, which manages existing
 * bean instances rather than creating new ones based on bean definitions.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 16 April 2001
 * @see #registerBeanDefinition
 * @see #addBeanPostProcessor
 * @see #getBean
 * @see #resolveDependency
 */
@SuppressWarnings("serial")
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
		implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {

	@Nullable
	private static Class<?> javaxInjectProviderClass;

	static {
		try {
			javaxInjectProviderClass =
					ClassUtils.forName("jakarta.inject.Provider", DefaultListableBeanFactory.class.getClassLoader());
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - Provider interface simply not supported then.
			javaxInjectProviderClass = null;
		}
	}


	/** Map from serialized id to factory instance. */
	private static final Map<String, Reference<DefaultListableBeanFactory>> serializableFactories =
			new ConcurrentHashMap<>(8);

	/** Optional id for this factory, for serialization purposes. */
	@Nullable
	private String serializationId;

	/** Whether to allow re-registration of a different definition with the same name. */
	private boolean allowBeanDefinitionOverriding = true;

	/** Whether to allow eager class loading even for lazy-init beans. 即使对于lazy-init bean，是否也允许加载急切类。*/
	private boolean allowEagerClassLoading = true;

	/** Optional OrderComparator for dependency Lists and arrays. 依赖列表和数组的可选 OrderComparator*/
	@Nullable
	private Comparator<Object> dependencyComparator;

	/** Resolver to use for checking if a bean definition is an autowire candidate. */
	private AutowireCandidateResolver autowireCandidateResolver = SimpleAutowireCandidateResolver.INSTANCE;

	/** Map from dependency type to corresponding autowired value. 从依赖类型映射到相应的自动装配值。
	 *
	 * 存放当同一类型有多个实现实列时，依赖类型-指定装配对象的缓存
	 *
	 * eg:
	 *    BeanFactory - defaultListableBeanFactory
	 *
	 *    在自动注入BeanFactory类型的都对象时，使用此map中缓存的defaultListableBeanFactory对象进行注入
	 * */
	private final Map<Class<?>, Object> resolvableDependencies = new ConcurrentHashMap<>(16);

	/** Map of bean definition objects, keyed by bean name. bean 定义对象的映射，以 bean 名称为键*/
	private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

	/** Map from bean name to merged BeanDefinitionHolder. */
	private final Map<String, BeanDefinitionHolder> mergedBeanDefinitionHolders = new ConcurrentHashMap<>(256);

	/** Map of singleton and non-singleton bean names, keyed by dependency type. 单例和非单例 bean 名称的映射，以依赖类型为键*/
	private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<>(64);

	/** Map of singleton-only bean names, keyed by dependency type. 单例 bean 名称映射，以依赖类型为键。*/
	private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<>(64);

	/** List of bean definition names, in registration order. bean 定义名称列表，按注册顺序排列。*/
	private volatile List<String> beanDefinitionNames = new ArrayList<>(256);

	/** List of names of manually registered singletons, in registration order. 按注册顺序手动注册的单身人士的名称列表。*/
	private volatile Set<String> manualSingletonNames = new LinkedHashSet<>(16);

	/** Cached array of bean definition names in case of frozen configuration. */
	@Nullable
	private volatile String[] frozenBeanDefinitionNames;

	/** Whether bean definition metadata may be cached for all beans. 是否可以为所有 bean 缓存 bean 定义元数据。*/
	private volatile boolean configurationFrozen;


	/**
	 * Create a new DefaultListableBeanFactory.
	 */
	public DefaultListableBeanFactory() {
		super();
	}

	/**
	 * Create a new DefaultListableBeanFactory with the given parent.
	 * @param parentBeanFactory the parent BeanFactory
	 */
	public DefaultListableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		super(parentBeanFactory);
	}


	/**
	 * Specify an id for serialization purposes, allowing this BeanFactory to be
	 * deserialized from this id back into the BeanFactory object, if needed.
	 */
	public void setSerializationId(@Nullable String serializationId) {
		if (serializationId != null) {
			serializableFactories.put(serializationId, new WeakReference<>(this));
		}
		else if (this.serializationId != null) {
			serializableFactories.remove(this.serializationId);
		}
		this.serializationId = serializationId;
	}

	/**
	 * Return an id for serialization purposes, if specified, allowing this BeanFactory
	 * to be deserialized from this id back into the BeanFactory object, if needed.
	 * @since 4.1.2
	 */
	@Nullable
	public String getSerializationId() {
		return this.serializationId;
	}

	/**
	 * Set whether it should be allowed to override bean definitions by registering
	 * a different definition with the same name, automatically replacing the former.
	 * If not, an exception will be thrown. This also applies to overriding aliases.
	 * <p>Default is "true".
	 * @see #registerBeanDefinition
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * Return whether it should be allowed to override bean definitions by registering
	 * a different definition with the same name, automatically replacing the former.
	 * 返回是否应该允许重写bean定义，方法是用相同的名称注册一个不同的定义，自动替换前者。
	 * @since 4.1.2
	 */
	public boolean isAllowBeanDefinitionOverriding() {
		return this.allowBeanDefinitionOverriding;
	}

	/**
	 * Set whether the factory is allowed to eagerly load bean classes
	 * even for bean definitions that are marked as "lazy-init".
	 * <p>Default is "true". Turn this flag off to suppress class loading
	 * for lazy-init beans unless such a bean is explicitly requested.
	 * In particular, by-type lookups will then simply ignore bean definitions
	 * without resolved class name, instead of loading the bean classes on
	 * demand just to perform a type check.
	 * @see AbstractBeanDefinition#setLazyInit
	 */
	public void setAllowEagerClassLoading(boolean allowEagerClassLoading) {
		this.allowEagerClassLoading = allowEagerClassLoading;
	}

	/**
	 * Return whether the factory is allowed to eagerly load bean classes
	 * even for bean definitions that are marked as "lazy-init".
	 *
	 * 返回是否允许工厂急切加载 bean 类，即使是标记为“lazy-init”的 bean 定义。
	 *
	 * @since 4.1.2
	 */
	public boolean isAllowEagerClassLoading() {
		return this.allowEagerClassLoading;
	}

	/**
	 * Set a {@link java.util.Comparator} for dependency Lists and arrays.
	 * @since 4.0
	 * @see org.springframework.core.OrderComparator
	 * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
	 */
	public void setDependencyComparator(@Nullable Comparator<Object> dependencyComparator) {
		this.dependencyComparator = dependencyComparator;
	}

	/**
	 * Return the dependency comparator for this BeanFactory (may be {@code null}.
	 *
	 * 返回此 BeanFactory 的依赖项比较器（可能是 {@code null}。
	 * @since 4.0
	 */
	@Nullable
	public Comparator<Object> getDependencyComparator() {
		return this.dependencyComparator;
	}

	/**
	 * Set a custom autowire candidate resolver for this BeanFactory to use
	 * when deciding whether a bean definition should be considered as a
	 * candidate for autowiring.
	 */
	public void setAutowireCandidateResolver(AutowireCandidateResolver autowireCandidateResolver) {
		Assert.notNull(autowireCandidateResolver, "AutowireCandidateResolver must not be null");
		if (autowireCandidateResolver instanceof BeanFactoryAware) {
			((BeanFactoryAware) autowireCandidateResolver).setBeanFactory(this);
		}
		this.autowireCandidateResolver = autowireCandidateResolver;
	}

	/**
	 * Return the autowire candidate resolver for this BeanFactory (never {@code null}).
	 *
	 * 返回此 BeanFactory 的自动装配候选解析器（从不{@code null}）。
	 */
	public AutowireCandidateResolver getAutowireCandidateResolver() {
		return this.autowireCandidateResolver;
	}


	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof DefaultListableBeanFactory) {
			DefaultListableBeanFactory otherListableFactory = (DefaultListableBeanFactory) otherFactory;
			this.allowBeanDefinitionOverriding = otherListableFactory.allowBeanDefinitionOverriding;
			this.allowEagerClassLoading = otherListableFactory.allowEagerClassLoading;
			this.dependencyComparator = otherListableFactory.dependencyComparator;
			// A clone of the AutowireCandidateResolver since it is potentially BeanFactoryAware
			setAutowireCandidateResolver(otherListableFactory.getAutowireCandidateResolver().cloneIfNecessary());
			// Make resolvable dependencies (e.g. ResourceLoader) available here as well
			this.resolvableDependencies.putAll(otherListableFactory.resolvableDependencies);
		}
	}


	//---------------------------------------------------------------------
	// Implementation of remaining BeanFactory methods
	//---------------------------------------------------------------------

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		return getBean(requiredType, (Object[]) null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getBean(Class<T> requiredType, @Nullable Object... args) throws BeansException {
		Assert.notNull(requiredType, "Required type must not be null");
		Object resolved = resolveBean(ResolvableType.forRawClass(requiredType), args, false);
		if (resolved == null) {
			throw new NoSuchBeanDefinitionException(requiredType);
		}
		return (T) resolved;
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
		Assert.notNull(requiredType, "Required type must not be null");
		return getBeanProvider(ResolvableType.forRawClass(requiredType), true);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		return getBeanProvider(requiredType, true);
	}


	//---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public boolean containsBeanDefinition(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return this.beanDefinitionMap.containsKey(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return this.beanDefinitionMap.size();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		String[] frozenNames = this.frozenBeanDefinitionNames;
		if (frozenNames != null) {
			return frozenNames.clone();
		}
		else {
			return StringUtils.toStringArray(this.beanDefinitionNames);
		}
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
		Assert.notNull(requiredType, "Required type must not be null");
		return getBeanProvider(ResolvableType.forRawClass(requiredType), allowEagerInit);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
		return new BeanObjectProvider<T>() {
			@Override
			public T getObject() throws BeansException {
				T resolved = resolveBean(requiredType, null, false);
				if (resolved == null) {
					throw new NoSuchBeanDefinitionException(requiredType);
				}
				return resolved;
			}
			@Override
			public T getObject(Object... args) throws BeansException {
				T resolved = resolveBean(requiredType, args, false);
				if (resolved == null) {
					throw new NoSuchBeanDefinitionException(requiredType);
				}
				return resolved;
			}
			@Override
			@Nullable
			public T getIfAvailable() throws BeansException {
				try {
					return resolveBean(requiredType, null, false);
				}
				catch (ScopeNotActiveException ex) {
					// Ignore resolved bean in non-active scope
					return null;
				}
			}
			@Override
			public void ifAvailable(Consumer<T> dependencyConsumer) throws BeansException {
				T dependency = getIfAvailable();
				if (dependency != null) {
					try {
						dependencyConsumer.accept(dependency);
					}
					catch (ScopeNotActiveException ex) {
						// Ignore resolved bean in non-active scope, even on scoped proxy invocation
					}
				}
			}
			@Override
			@Nullable
			public T getIfUnique() throws BeansException {
				try {
					return resolveBean(requiredType, null, true);
				}
				catch (ScopeNotActiveException ex) {
					// Ignore resolved bean in non-active scope
					return null;
				}
			}
			@Override
			public void ifUnique(Consumer<T> dependencyConsumer) throws BeansException {
				T dependency = getIfUnique();
				if (dependency != null) {
					try {
						dependencyConsumer.accept(dependency);
					}
					catch (ScopeNotActiveException ex) {
						// Ignore resolved bean in non-active scope, even on scoped proxy invocation
					}
				}
			}
			@SuppressWarnings("unchecked")
			@Override
			public Stream<T> stream() {
				return Arrays.stream(getBeanNamesForTypedStream(requiredType, allowEagerInit))
						.map(name -> (T) getBean(name))
						.filter(bean -> !(bean instanceof NullBean));
			}
			@SuppressWarnings("unchecked")
			@Override
			public Stream<T> orderedStream() {
				String[] beanNames = getBeanNamesForTypedStream(requiredType, allowEagerInit);
				if (beanNames.length == 0) {
					return Stream.empty();
				}
				Map<String, T> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);
				for (String beanName : beanNames) {
					Object beanInstance = getBean(beanName);
					if (!(beanInstance instanceof NullBean)) {
						matchingBeans.put(beanName, (T) beanInstance);
					}
				}
				Stream<T> stream = matchingBeans.values().stream();
				return stream.sorted(adaptOrderComparator(matchingBeans));
			}
		};
	}

	@Nullable
	private <T> T resolveBean(ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) {

		// 获取与requiredType类型匹配的bean实例包装类
		NamedBeanHolder<T> namedBean = resolveNamedBean(requiredType, args, nonUniqueAsNull);

		// 返回bean实例
		if (namedBean != null) {
			return namedBean.getBeanInstance();
		}
		BeanFactory parent = getParentBeanFactory();

		// 如果当期工厂中没有requiredType类型对应的bean实例，就去父工厂找
		if (parent instanceof DefaultListableBeanFactory) {
			return ((DefaultListableBeanFactory) parent).resolveBean(requiredType, args, nonUniqueAsNull);
		}
		else if (parent != null) {
			ObjectProvider<T> parentProvider = parent.getBeanProvider(requiredType);
			if (args != null) {
				return parentProvider.getObject(args);
			}
			else {
				return (nonUniqueAsNull ? parentProvider.getIfUnique() : parentProvider.getIfAvailable());
			}
		}
		return null;
	}

	private String[] getBeanNamesForTypedStream(ResolvableType requiredType, boolean allowEagerInit) {
		return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType, true, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		return getBeanNamesForType(type, true, true);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {

		// 获取类型
		Class<?> resolved = type.resolve();

		// 如果类型不为空，且不包含泛型
		if (resolved != null && !type.hasGenerics()) {

			// 通过类型获取bean名称
			return getBeanNamesForType(resolved, includeNonSingletons, allowEagerInit);
		}
		else {

			// 查找所有与type类型匹配的bean名称
			return doGetBeanNamesForType(type, includeNonSingletons, allowEagerInit);
		}
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		return getBeanNamesForType(type, true, true);
	}

	/**
	 *
	 * @param type the class or interface to match, or {@code null} for all bean names
	 *             要匹配的类或接口，或所有 bean 名称的 {@code null}
	 * @param includeNonSingletons whether to include prototype or scoped beans too
	 * or just singletons (also applies to FactoryBeans)
	 * @param allowEagerInit whether to initialize <i>lazy-init singletons</i> and
	 * <i>objects created by FactoryBeans</i> (or by factory methods with a
	 * "factory-bean" reference) for the type check. Note that FactoryBeans need to be
	 * eagerly initialized to determine their type: So be aware that passing in "true"
	 * for this flag will initialize FactoryBeans and "factory-bean" references.
	 * @return
	 */
	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {

		// 配置还未被冻结或者类型为null或者不允许早期初始化
		if (!isConfigurationFrozen() || type == null || !allowEagerInit) {
			return doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, allowEagerInit);
		}

		//值得注意的是，不管type是否不为空，allowEagerInit是否为true
		//只要isConfigurationFrozen()为false就一定不会走这里
		//因为isConfigurationFrozen()为false的时候表示BeanDefinition
		//可能还会发生更改和添加，所以不能进行缓存

		//如果允许非单例的bean，那么从保存所有bean的集合中获取，否则从
		//单例bean中获取
		Map<Class<?>, String[]> cache =
				(includeNonSingletons ? this.allBeanNamesByType : this.singletonBeanNamesByType);

		// 从缓存中获取要匹配的类型对应的bean
		String[] resolvedBeanNames = cache.get(type);
		if (resolvedBeanNames != null) {
			return resolvedBeanNames;
		}

		// 如果缓存中没有获取到，那么只能重新获取，获取到之后就存入缓存
		resolvedBeanNames = doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, true);
		if (ClassUtils.isCacheSafe(type, getBeanClassLoader())) {
			cache.put(type, resolvedBeanNames);
		}
		return resolvedBeanNames;
	}


	/**
	 * 返回所有与type匹配的bean的beanName数组
	 *
	 * @param type
	 * @param includeNonSingletons
	 * @param allowEagerInit
	 * @return
	 */
	private String[] doGetBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		List<String> result = new ArrayList<>();

		// Check all bean definitions.
		// 遍历BeanDefinitionNames集合
		for (String beanName : this.beanDefinitionNames) {
			// Only consider bean as eligible if the bean name is not defined as alias for some other bean.
			// 如果 bean 名称未定义为其他 bean 的别名，则仅将 bean 视为合格。
			// 如果是别名，则直接跳过
			if (!isAlias(beanName)) {
				try {

					// 获取合并的BeanDefinition，合并的BeanDefinition指的是整合了父BeanDefinition的属性，然后属性值会转换为RootBeanDefinition
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

					// Only check bean definition if it is complete. 仅检查 bean 定义是否完整。
					//抽象的BeanDefinition是不做考虑，抽象的就是拿来继承的
					//如果允许早期初始化，那么直接短路，进入方法体
					//如果不允许早期初始化，那么需要进一步判断,如果是不允许早期初始化的，
					//并且beanClass已经被加载或者它是可以早期初始化的，那么如果当前bean是工厂bean，并且指定的bean又是工厂
					//那么这个bean就必须被早期初始化，也就是说就不符合我们制定的allowEagerInit为false的情况，直接跳过
					if (!mbd.isAbstract() && (allowEagerInit ||
							(mbd.hasBeanClass() || !mbd.isLazyInit() || isAllowEagerClassLoading()) &&
									!requiresEagerInitForType(mbd.getFactoryBeanName()))) {

						// 判断当前bean是否实现了FactoryBean接口
						boolean isFactoryBean = isFactoryBean(beanName, mbd);

						// 根据RootBeanDefinition来获取BeanDefinitionHolder对象
						BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

						// 定义匹配的标志位，默认为false
						boolean matchFound = false;

						// 定义是否允许factorybean的初始化的标志位
						boolean allowFactoryBeanInit = (allowEagerInit || containsSingleton(beanName));

						// 是否是非懒加载的标志位
						boolean isNonLazyDecorated = (dbd != null && !mbd.isLazyInit());

						// 根据上述标志位来进行类型匹配的判断
						// 是否实现FactoryBean接口(如果没有实现FactoryBean接口)
						if (!isFactoryBean) {
							if (includeNonSingletons || isSingleton(beanName, mbd, dbd)) {

								// 检查beanName对应的bean类型是否与type匹配
								// 如果beanName对应的是FactoryBean，则检查beanName对应的FactoryBean创建的bean的类型是否与type匹配
								matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
							}
						}
						else {
							if (includeNonSingletons || isNonLazyDecorated ||
									(allowFactoryBeanInit && isSingleton(beanName, mbd, dbd))) {
								matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
							}
							if (!matchFound) {
								// In case of FactoryBean, try to match FactoryBean instance itself next.
								// 在 FactoryBean 的情况下，接下来尝试匹配 FactoryBean 实例本身。
								beanName = FACTORY_BEAN_PREFIX + beanName;
								matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
							}
						}
						// 成功匹配到bean类型
						if (matchFound) {
							result.add(beanName);
						}
					}
				}
				catch (CannotLoadBeanClassException | BeanDefinitionStoreException ex) {
					if (allowEagerInit) {
						throw ex;
					}
					// Probably a placeholder: let's ignore it for type matching purposes.
					LogMessage message = (ex instanceof CannotLoadBeanClassException ?
							LogMessage.format("Ignoring bean class loading failure for bean '%s'", beanName) :
							LogMessage.format("Ignoring unresolvable metadata in bean definition '%s'", beanName));
					logger.trace(message, ex);
					// Register exception, in case the bean was accidentally unresolvable.
					onSuppressedException(ex);
				}
				catch (NoSuchBeanDefinitionException ex) {
					// Bean definition got removed while we were iterating -> ignore.
				}
			}
		}

		// Check manually registered singletons too.也检查手动注册的单身人士。
		// 遍历单例bean名称的集合
		for (String beanName : this.manualSingletonNames) {
			try {
				// In case of FactoryBean, match object created by FactoryBean.
				// 在 FactoryBean 的情况下，匹配由 FactoryBean 创建的对象。
				// 如果beanName对应的bean是FactoryBean，那么久调用getObjectType去匹配是否符合指定类型
				if (isFactoryBean(beanName)) {
					if ((includeNonSingletons || isSingleton(beanName)) && isTypeMatch(beanName, type)) {
						result.add(beanName);
						// Match found for this bean: do not match FactoryBean itself anymore.
						// 找到此 bean 的匹配项：不再匹配 FactoryBean 本身.
						continue;
					}
					// In case of FactoryBean, try to match FactoryBean itself next.
					// 在 FactoryBean 的情况下，接下来尝试匹配 FactoryBean 本身。
					beanName = FACTORY_BEAN_PREFIX + beanName;
				}
				// Match raw bean instance (might be raw FactoryBean).
				// 匹配原始 bean 实例（可能是原始 FactoryBean）。
				// 如果没有匹配成功，那么匹配工厂类
				if (isTypeMatch(beanName, type)) {
					result.add(beanName);
				}
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Shouldn't happen - probably a result of circular reference resolution...
				logger.trace(LogMessage.format(
						"Failed to check manually registered singleton with name '%s'", beanName), ex);
			}
		}

		return StringUtils.toStringArray(result);
	}

	private boolean isSingleton(String beanName, RootBeanDefinition mbd, @Nullable BeanDefinitionHolder dbd) {
		return (dbd != null ? mbd.isSingleton() : isSingleton(beanName));
	}

	/**
	 * Check whether the specified bean would need to be eagerly initialized
	 * in order to determine its type.
	 *
	 * 检查指定的 bean 是否需要急切初始化以确定其类型。
	 *
	 * @param factoryBeanName a factory-bean reference that the bean definition
	 * defines a factory method for
	 * @return whether eager initialization is necessary
	 */
	private boolean requiresEagerInitForType(@Nullable String factoryBeanName) {
		return (factoryBeanName != null && isFactoryBean(factoryBeanName) && !containsSingleton(factoryBeanName));
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		return getBeansOfType(type, true, true);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Map<String, T> getBeansOfType(
			@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {

		String[] beanNames = getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
		Map<String, T> result = CollectionUtils.newLinkedHashMap(beanNames.length);
		for (String beanName : beanNames) {
			try {
				Object beanInstance = getBean(beanName);
				if (!(beanInstance instanceof NullBean)) {
					result.put(beanName, (T) beanInstance);
				}
			}
			catch (BeanCreationException ex) {
				Throwable rootCause = ex.getMostSpecificCause();
				if (rootCause instanceof BeanCurrentlyInCreationException) {
					BeanCreationException bce = (BeanCreationException) rootCause;
					String exBeanName = bce.getBeanName();
					if (exBeanName != null && isCurrentlyInCreation(exBeanName)) {
						if (logger.isTraceEnabled()) {
							logger.trace("Ignoring match to currently created bean '" + exBeanName + "': " +
									ex.getMessage());
						}
						onSuppressedException(ex);
						// Ignore: indicates a circular reference when autowiring constructors.
						// We want to find matches other than the currently created bean itself.
						continue;
					}
				}
				throw ex;
			}
		}
		return result;
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		List<String> result = new ArrayList<>();
		for (String beanName : this.beanDefinitionNames) {
			BeanDefinition bd = this.beanDefinitionMap.get(beanName);
			if (bd != null && !bd.isAbstract() && findAnnotationOnBean(beanName, annotationType) != null) {
				result.add(beanName);
			}
		}
		for (String beanName : this.manualSingletonNames) {
			if (!result.contains(beanName) && findAnnotationOnBean(beanName, annotationType) != null) {
				result.add(beanName);
			}
		}
		return StringUtils.toStringArray(result);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
		String[] beanNames = getBeanNamesForAnnotation(annotationType);
		Map<String, Object> result = CollectionUtils.newLinkedHashMap(beanNames.length);
		for (String beanName : beanNames) {
			Object beanInstance = getBean(beanName);
			if (!(beanInstance instanceof NullBean)) {
				result.put(beanName, beanInstance);
			}
		}
		return result;
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		return findMergedAnnotationOnBean(beanName, annotationType)
				.synthesize(MergedAnnotation::isPresent).orElse(null);
	}

	private <A extends Annotation> MergedAnnotation<A> findMergedAnnotationOnBean(
			String beanName, Class<A> annotationType) {

		Class<?> beanType = getType(beanName);
		if (beanType != null) {
			MergedAnnotation<A> annotation =
					MergedAnnotations.from(beanType, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
			if (annotation.isPresent()) {
				return annotation;
			}
		}
		if (containsBeanDefinition(beanName)) {
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
			// Check raw bean class, e.g. in case of a proxy.
			if (bd.hasBeanClass()) {
				Class<?> beanClass = bd.getBeanClass();
				if (beanClass != beanType) {
					MergedAnnotation<A> annotation =
							MergedAnnotations.from(beanClass, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
					if (annotation.isPresent()) {
						return annotation;
					}
				}
			}
			// Check annotations declared on factory method, if any.
			Method factoryMethod = bd.getResolvedFactoryMethod();
			if (factoryMethod != null) {
				MergedAnnotation<A> annotation =
						MergedAnnotations.from(factoryMethod, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
				if (annotation.isPresent()) {
					return annotation;
				}
			}
		}
		return MergedAnnotation.missing();
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableListableBeanFactory interface
	// 缓存当同一类型有多个实现实列时，指定的注入对象
	//---------------------------------------------------------------------

	@Override
	public void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue) {
		Assert.notNull(dependencyType, "Dependency type must not be null");
		if (autowiredValue != null) {
			if (!(autowiredValue instanceof ObjectFactory || dependencyType.isInstance(autowiredValue))) {
				throw new IllegalArgumentException("Value [" + autowiredValue +
						"] does not implement specified dependency type [" + dependencyType.getName() + "]");
			}
			this.resolvableDependencies.put(dependencyType, autowiredValue);
		}
	}

	@Override
	public boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
			throws NoSuchBeanDefinitionException {

		return isAutowireCandidate(beanName, descriptor, getAutowireCandidateResolver());
	}

	/**
	 * Determine whether the specified bean definition qualifies as an autowire candidate,
	 * to be injected into other beans which declare a dependency of matching type.
	 * @param beanName the name of the bean definition to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @param resolver the AutowireCandidateResolver to use for the actual resolution algorithm
	 * @return whether the bean should be considered as autowire candidate
	 */
	protected boolean isAutowireCandidate(
			String beanName, DependencyDescriptor descriptor, AutowireCandidateResolver resolver)
			throws NoSuchBeanDefinitionException {

		String bdName = BeanFactoryUtils.transformedBeanName(beanName);
		if (containsBeanDefinition(bdName)) {
			return isAutowireCandidate(beanName, getMergedLocalBeanDefinition(bdName), descriptor, resolver);
		}
		else if (containsSingleton(beanName)) {
			return isAutowireCandidate(beanName, new RootBeanDefinition(getType(beanName)), descriptor, resolver);
		}

		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof DefaultListableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent.
			return ((DefaultListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor, resolver);
		}
		else if (parent instanceof ConfigurableListableBeanFactory) {
			// If no DefaultListableBeanFactory, can't pass the resolver along.
			return ((ConfigurableListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor);
		}
		else {
			return true;
		}
	}

	/**
	 * Determine whether the specified bean definition qualifies as an autowire candidate,
	 * to be injected into other beans which declare a dependency of matching type.
	 * @param beanName the name of the bean definition to check
	 * @param mbd the merged bean definition to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @param resolver the AutowireCandidateResolver to use for the actual resolution algorithm
	 * @return whether the bean should be considered as autowire candidate
	 */
	protected boolean isAutowireCandidate(String beanName, RootBeanDefinition mbd,
			DependencyDescriptor descriptor, AutowireCandidateResolver resolver) {

		String bdName = BeanFactoryUtils.transformedBeanName(beanName);
		resolveBeanClass(mbd, bdName);
		if (mbd.isFactoryMethodUnique && mbd.factoryMethodToIntrospect == null) {
			new ConstructorResolver(this).resolveFactoryMethodIfPossible(mbd);
		}
		BeanDefinitionHolder holder = (beanName.equals(bdName) ?
				this.mergedBeanDefinitionHolders.computeIfAbsent(beanName,
						key -> new BeanDefinitionHolder(mbd, beanName, getAliases(bdName))) :
				new BeanDefinitionHolder(mbd, beanName, getAliases(bdName)));
		return resolver.isAutowireCandidate(holder, descriptor);
	}

	@Override
	public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {

		// 从Bean定义对象的映射中获取beanName对应的BeanDefinition对象
		BeanDefinition bd = this.beanDefinitionMap.get(beanName);

		// 如果从Bean定义对象的映射没有找到
		if (bd == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("No bean named '" + beanName + "' found in " + this);
			}
			throw new NoSuchBeanDefinitionException(beanName);
		}

		// 返回beanName对应的BeanDefinition对象
		return bd;
	}

	@Override
	public Iterator<String> getBeanNamesIterator() {
		CompositeIterator<String> iterator = new CompositeIterator<>();
		iterator.add(this.beanDefinitionNames.iterator());
		iterator.add(this.manualSingletonNames.iterator());
		return iterator;
	}

	@Override
	protected void clearMergedBeanDefinition(String beanName) {
		super.clearMergedBeanDefinition(beanName);
		this.mergedBeanDefinitionHolders.remove(beanName);
	}

	@Override
	public void clearMetadataCache() {
		super.clearMetadataCache();
		this.mergedBeanDefinitionHolders.clear();
		clearByTypeCache();
	}

	@Override
	public void freezeConfiguration() {
		this.configurationFrozen = true;
		this.frozenBeanDefinitionNames = StringUtils.toStringArray(this.beanDefinitionNames);
	}

	@Override
	public boolean isConfigurationFrozen() {
		return this.configurationFrozen;
	}

	/**
	 * Considers all beans as eligible for metadata caching
	 * if the factory's configuration has been marked as frozen.
	 * @see #freezeConfiguration()
	 */
	@Override
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return (this.configurationFrozen || super.isBeanEligibleForMetadataCaching(beanName));
	}

	@Override
	public void preInstantiateSingletons() throws BeansException {
		if (logger.isTraceEnabled()) {
			logger.trace("Pre-instantiating singletons in " + this);
		}

		// Iterate over a copy to allow for init methods which in turn register new bean definitions.
		// While this may not be part of the regular factory bootstrap, it does otherwise work fine.
		// 迭代一个副本以允许 init 方法依次注册新的 bean 定义。虽然这可能不是常规工厂引导程序的一部分，但它确实可以正常工作。
		// 将所有BeanDefinition的名字创建一个集合
		List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

		// Trigger initialization of all non-lazy singleton beans...
		// 触发所有非延迟加载单例bean的初始化，遍历集合的对象
		for (String beanName : beanNames) {
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);

			// 条件判断，抽象，单例，非懒加载
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
				if (isFactoryBean(beanName)) {
					Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);

					// 判断是否实现了FactoryBean接口
					if (bean instanceof FactoryBean) {

						// 判断这个FactoryBean是否希望立即初始化
						FactoryBean<?> factory = (FactoryBean<?>) bean;
						boolean isEagerInit = (factory instanceof SmartFactoryBean &&
								((SmartFactoryBean<?>) factory).isEagerInit());

						//  如果希望急切的初始化，则通过beanName获取bean实例
						if (isEagerInit) {
							getBean(beanName);
						}
					}
				}
				else {

					// 如果beanName对应的bean不是FactoryBean，只是普通的bean，通过beanName获取bean实例
					getBean(beanName);
				}
			}
		}

		// Trigger post-initialization callback for all applicable beans...
		// 为所有适用的 bean 触发初始化后回调...
		for (String beanName : beanNames) {
			Object singletonInstance = getSingleton(beanName);
			if (singletonInstance instanceof SmartInitializingSingleton) {

				// smartInitialize用来记录启动步骤
				StartupStep smartInitialize = this.getApplicationStartup().start("spring.beans.smart-initialize")
						.tag("beanName", beanName);
				SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;

				// 触发SmartInitializingSingleton实现类的afterSingletonsInstantiated方法
				smartSingleton.afterSingletonsInstantiated();
				smartInitialize.end();
			}
		}
	}


	//---------------------------------------------------------------------
	// Implementation of BeanDefinitionRegistry interface
	// BeanDefinitionRegistry 接口的实现
	//---------------------------------------------------------------------

	@Override
	public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
			throws BeanDefinitionStoreException {

		Assert.hasText(beanName, "Bean name must not be empty");
		Assert.notNull(beanDefinition, "BeanDefinition must not be null");

		if (beanDefinition instanceof AbstractBeanDefinition) {
			try {
				// 注册前的最后一个校验，这里的检验不同于之前的xml文件校验，主要是对应abstractBeanDefinition属性的methodOverrides校验，
				// 检验methodOverrides是否与工厂方法并存或者methodoverrides对应的方法根本不存在
				((AbstractBeanDefinition) beanDefinition).validate();
			}
			catch (BeanDefinitionValidationException ex) {
				throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName,
						"Validation of bean definition failed", ex);
			}
		}

		BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
		// 处理当前beanName已经注册的情况
		if (existingDefinition != null) {
			// 如果beanName已经注册，且不允许beanName覆盖，则抛出异常
			if (!isAllowBeanDefinitionOverriding()) {
				throw new BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition);
			}

			// 如果允许覆盖，则记录日志，并覆盖
			else if (existingDefinition.getRole() < beanDefinition.getRole()) {
				// e.g. was ROLE_APPLICATION, now overriding with ROLE_SUPPORT or ROLE_INFRASTRUCTURE
				if (logger.isInfoEnabled()) {
					logger.info("Overriding user-defined bean definition for bean '" + beanName +
							"' with a framework-generated bean definition: replacing [" +
							existingDefinition + "] with [" + beanDefinition + "]");
				}
			}
			else if (!beanDefinition.equals(existingDefinition)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Overriding bean definition for bean '" + beanName +
							"' with a different definition: replacing [" + existingDefinition +
							"] with [" + beanDefinition + "]");
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Overriding bean definition for bean '" + beanName +
							"' with an equivalent definition: replacing [" + existingDefinition +
							"] with [" + beanDefinition + "]");
				}
			}
			this.beanDefinitionMap.put(beanName, beanDefinition);
		}
		else {
			if (hasBeanCreationStarted()) {
				// Cannot modify startup-time collection elements anymore (for stable iteration)
				synchronized (this.beanDefinitionMap) {
					this.beanDefinitionMap.put(beanName, beanDefinition);
					List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
					updatedDefinitions.addAll(this.beanDefinitionNames);
					updatedDefinitions.add(beanName);
					this.beanDefinitionNames = updatedDefinitions;
					removeManualSingletonName(beanName);
				}
			}
			else {
				// Still in startup registration phase
				// 注册bean的定义信息
				this.beanDefinitionMap.put(beanName, beanDefinition);

				// 记录beanName
				this.beanDefinitionNames.add(beanName);
				removeManualSingletonName(beanName);
			}
			this.frozenBeanDefinitionNames = null;
		}

		if (existingDefinition != null || containsSingleton(beanName)) {
			resetBeanDefinition(beanName);
		}
		else if (isConfigurationFrozen()) {
			clearByTypeCache();
		}
	}

	@Override
	public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
		Assert.hasText(beanName, "'beanName' must not be empty");

		BeanDefinition bd = this.beanDefinitionMap.remove(beanName);
		if (bd == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("No bean named '" + beanName + "' found in " + this);
			}
			throw new NoSuchBeanDefinitionException(beanName);
		}

		if (hasBeanCreationStarted()) {
			// Cannot modify startup-time collection elements anymore (for stable iteration)
			synchronized (this.beanDefinitionMap) {
				List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames);
				updatedDefinitions.remove(beanName);
				this.beanDefinitionNames = updatedDefinitions;
			}
		}
		else {
			// Still in startup registration phase
			this.beanDefinitionNames.remove(beanName);
		}
		this.frozenBeanDefinitionNames = null;

		resetBeanDefinition(beanName);
	}

	/**
	 * Reset all bean definition caches for the given bean,
	 * including the caches of beans that are derived from it.
	 * <p>Called after an existing bean definition has been replaced or removed,
	 * triggering {@link #clearMergedBeanDefinition}, {@link #destroySingleton}
	 * and {@link MergedBeanDefinitionPostProcessor#resetBeanDefinition} on the
	 * given bean and on all bean definitions that have the given bean as parent.
	 * @param beanName the name of the bean to reset
	 * @see #registerBeanDefinition
	 * @see #removeBeanDefinition
	 */
	protected void resetBeanDefinition(String beanName) {
		// Remove the merged bean definition for the given bean, if already created.
		// 删除给定 bean 的合并 bean 定义（如果已创建）。
		clearMergedBeanDefinition(beanName);

		// Remove corresponding bean from singleton cache, if any. Shouldn't usually
		// be necessary, rather just meant for overriding a context's default beans
		// (e.g. the default StaticMessageSource in a StaticApplicationContext).
		/**
		 * 从单例缓存中删除相应的 bean，如果有的话。
		 * 通常不需要，而只是为了覆盖上下文的默认 bean（例如，StaticApplicationContext 中的默认 StaticMessageSource）。
		 */
		destroySingleton(beanName);

		// Notify all post-processors that the specified bean definition has been reset.
		// 通知所有后处理器指定的 bean 定义已被重置。
		for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
			processor.resetBeanDefinition(beanName);
		}

		// Reset all bean definitions that have the given bean as parent (recursively).
		// 重置所有以给定 bean 为父级的 bean 定义（递归）。
		for (String bdName : this.beanDefinitionNames) {
			if (!beanName.equals(bdName)) {
				BeanDefinition bd = this.beanDefinitionMap.get(bdName);
				// Ensure bd is non-null due to potential concurrent modification of beanDefinitionMap.
				if (bd != null && beanName.equals(bd.getParentName())) {
					resetBeanDefinition(bdName);
				}
			}
		}
	}

	/**
	 * Only allows alias overriding if bean definition overriding is allowed.
	 *
	 * 如果允许覆盖 bean 定义，则仅允许别名覆盖。
	 */
	@Override
	protected boolean allowAliasOverriding() {
		return isAllowBeanDefinitionOverriding();
	}

	/**
	 * Also checks for an alias overriding a bean definition of the same name.
	 */
	@Override
	protected void checkForAliasCircle(String name, String alias) {
		super.checkForAliasCircle(name, alias);
		if (!isAllowBeanDefinitionOverriding() && containsBeanDefinition(alias)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Alias would override bean definition '" + alias + "'");
		}
	}

	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		super.registerSingleton(beanName, singletonObject);
		updateManualSingletonNames(set -> set.add(beanName), set -> !this.beanDefinitionMap.containsKey(beanName));
		clearByTypeCache();
	}

	@Override
	public void destroySingletons() {
		super.destroySingletons();
		updateManualSingletonNames(Set::clear, set -> !set.isEmpty());
		clearByTypeCache();
	}

	@Override
	public void destroySingleton(String beanName) {
		super.destroySingleton(beanName);
		removeManualSingletonName(beanName);
		clearByTypeCache();
	}

	private void removeManualSingletonName(String beanName) {
		updateManualSingletonNames(set -> set.remove(beanName), set -> set.contains(beanName));
	}

	/**
	 * Update the factory's internal set of manual singleton names.
	 * 更新工厂的内部手动单例名称集。
	 *
	 * @param action the modification action
	 * @param condition a precondition for the modification action
	 * (if this condition does not apply, the action can be skipped)
	 */
	private void updateManualSingletonNames(Consumer<Set<String>> action, Predicate<Set<String>> condition) {
		if (hasBeanCreationStarted()) {
			// Cannot modify startup-time collection elements anymore (for stable iteration)
			// 如果bean工厂已经开始创建bean对象，则停止更新单例名称集合
			synchronized (this.beanDefinitionMap) {
				if (condition.test(this.manualSingletonNames)) {
					Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames);
					action.accept(updatedSingletons);
					this.manualSingletonNames = updatedSingletons;
				}
			}
		}
		else {
			// Still in startup registration phase
			// beanFactory处于注册状态时且beanDefinitionMap中不存在beanName时，将beanName添加到manualSingletonNames中
			if (condition.test(this.manualSingletonNames)) {
				action.accept(this.manualSingletonNames);
			}
		}
	}

	/**
	 * Remove any assumptions about by-type mappings.
	 *
	 * 按类型删除有关所有映射
	 */
	private void clearByTypeCache() {
		this.allBeanNamesByType.clear();
		this.singletonBeanNamesByType.clear();
	}


	//---------------------------------------------------------------------
	// Dependency resolution functionality
	//---------------------------------------------------------------------

	/**
	 *
	 * @param requiredType type the bean must match; can be an interface or superclass
	 *                     键入 bean 必须匹配；可以是接口或超类
	 * @param <T>
	 * @return
	 * @throws BeansException
	 */
	@Override
	public <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException {
		Assert.notNull(requiredType, "Required type must not be null");

		// 筛选出唯一的参数值候选者
		NamedBeanHolder<T> namedBean = resolveNamedBean(ResolvableType.forRawClass(requiredType), null, false);

		if (namedBean != null) {
			return namedBean;
		}
		BeanFactory parent = getParentBeanFactory();

		// 如果当前工厂中没有候选者，去父工厂中找
		if (parent instanceof AutowireCapableBeanFactory) {
			return ((AutowireCapableBeanFactory) parent).resolveNamedBean(requiredType);
		}
		throw new NoSuchBeanDefinitionException(requiredType);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private <T> NamedBeanHolder<T> resolveNamedBean(
			ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) throws BeansException {

		Assert.notNull(requiredType, "Required type must not be null");

		//	获取与requiredType.resolve()类型匹配的bean名称
		String[] candidateNames = getBeanNamesForType(requiredType);

		if (candidateNames.length > 1) {
			List<String> autowireCandidates = new ArrayList<>(candidateNames.length);
			for (String beanName : candidateNames) {

				// beanName对应的definition不存在，或者beanName对应的definition可以装配到其他bean
				if (!containsBeanDefinition(beanName) || getBeanDefinition(beanName).isAutowireCandidate()) {

					// 添加到自动装配候选集合
					autowireCandidates.add(beanName);
				}
			}
			if (!autowireCandidates.isEmpty()) {
				candidateNames = StringUtils.toStringArray(autowireCandidates);
			}
		}

		// 如果自动装配候选者只有一个
		if (candidateNames.length == 1) {
			return resolveNamedBean(candidateNames[0], requiredType, args);
		}
		else if (candidateNames.length > 1) {

			// 存放所有的候选者
			Map<String, Object> candidates = CollectionUtils.newLinkedHashMap(candidateNames.length);

			for (String beanName : candidateNames) {

				// 如果beanName存在于告诉缓存中，args传入为null
				if (containsSingleton(beanName) && args == null) {

					Object beanInstance = getBean(beanName);

					candidates.put(beanName, (beanInstance instanceof NullBean ? null : beanInstance));
				}
				else {

					candidates.put(beanName, getType(beanName));
				}
			}

			// 获取所有自动装配候选者中的主要候选者
			String candidateName = determinePrimaryCandidate(candidates, requiredType.toClass());

			// 如果没有主要候选者，就选择优先级最高的候选者
			if (candidateName == null) {

				candidateName = determineHighestPriorityCandidate(candidates, requiredType.toClass());
			}

			// 如果选出了自动装配候选者
			if (candidateName != null) {

				// 获取将要自动装配的bean实例
				Object beanInstance = candidates.get(candidateName);

				if (beanInstance == null) {
					return null;
				}

				// 如果beanInstance是Class类型，获取bean对象，并封装到NamedBeanHolder中
				if (beanInstance instanceof Class) {
					return resolveNamedBean(candidateName, requiredType, args);
				}

				// 如果beanInstance是bean实例，直接封装到NamedBeanHolder中返回
				return new NamedBeanHolder<>(candidateName, (T) beanInstance);
			}
			if (!nonUniqueAsNull) {
				throw new NoUniqueBeanDefinitionException(requiredType, candidates.keySet());
			}
		}

		// 没有找到和requiredType类型匹配的bean，返回null
		return null;
	}

	@Nullable
	private <T> NamedBeanHolder<T> resolveNamedBean(
			String beanName, ResolvableType requiredType, @Nullable Object[] args) throws BeansException {

		// 获取beanName对应的bean
		Object bean = getBean(beanName, null, args);
		if (bean instanceof NullBean) {
			return null;
		}

		//创建一个 bean 名称和实例的持有者
		return new NamedBeanHolder<T>(beanName, adaptBeanInstance(beanName, bean, requiredType.toClass()));
	}

	/**
	 * 根据descriptor的依赖类型解析出与descriptor所包装的对象匹配的候选Bean对象
	 *
	 * @param descriptor the descriptor for the dependency (field/method/constructor)
	 * @param requestingBeanName the name of the bean which declares the given dependency
	 * @param autowiredBeanNames a Set that all names of autowired beans (used for
	 * resolving the given dependency) are supposed to be added to
	 * @param typeConverter the TypeConverter to use for populating arrays and collections
	 * @return
	 * @throws BeansException
	 */
	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

		// 获取工厂的参数名发现器，设置到descriptor中。使得descriptor初始化基础方法参数的参数名发现。此时，该方法实际上
		// 并没有尝试检索参数名称；它仅允许发现再应用程序调用getDependencyName时发生
		descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());

		// 如果descriptor的依赖类型为Optional类
		if (Optional.class == descriptor.getDependencyType()) {

			//创建Optional类型的符合descriptor要求的候选Bean对象
			return createOptionalDependency(descriptor, requestingBeanName);
		}

		// 是对象工厂类型或者对象提供者
		else if (ObjectFactory.class == descriptor.getDependencyType() ||
				ObjectProvider.class == descriptor.getDependencyType()) {

			// DependencyObjectProvider:依赖对象提供者,用于延迟解析依赖项
			// 新建一个DependencyObjectProvider的实例
			return new DependencyObjectProvider(descriptor, requestingBeanName);
		}

		// javaxInjectProviderClass有可能导致空指针，不过一般情况下，我们引用Spring包的时候都有引入该类以防止空指针
		// 如果依赖类型是javax.inject.Provider类。
		else if (javaxInjectProviderClass == descriptor.getDependencyType()) {

			// Jse330Provider:javax.inject.Provider实现类.与DependencyObjectProvoid作用一样，也是用于延迟解析依赖
			// 项，但它是使用javax.inject.Provider作为依赖 对象，以减少与Springd耦合
			// 新建一个专门用于构建javax.inject.Provider对象的工厂来构建创建Jse330Provider对象
			return new Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
		}
		else {

			// 尝试获取延迟加载代理对象
			Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(
					descriptor, requestingBeanName);

			// 如果result为null，即表示现在需要得到候选Bean对象
			if (result == null) {

				// 解析出与descriptor所包装的对象匹配的候选Bean对象
				result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
			}

			// 将与descriptor所包装的对象匹配的候选Bean对象【result】返回出去

			return result;
		}
	}

	/**
	 * 解析出与descriptor所包装的对象匹配的候选Bean对象
	 *
	 * @param descriptor
	 * @param beanName
	 * @param autowiredBeanNames
	 * @param typeConverter
	 * @return
	 * @throws BeansException
	 */
	@Nullable
	public Object doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

		//设置新得当前切入点对象，得到旧的当前切入点对象
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {

			// 尝试使用descriptor的快捷方法得到最近候选Bean对象
			// resolveShortcut：解决针对给定工厂的这种依赖关系的快捷方式，例如，考虑一些预先解决的信息
			// 尝试调用该工厂解决这种依赖关系的快捷方式来获取beanName对应的bean对象,默认返回null
			// 获取针对该工厂的这种依赖关系的快捷解析最佳候选Bean对象
			Object shortcut = descriptor.resolveShortcut(this);

			//如果shortcut不为null，返回该shortcut
			if (shortcut != null) {
				return shortcut;
			}

			//获取descriptor的依赖类型
			Class<?> type = descriptor.getDependencyType();

			//尝试使用descriptor的默认值作为最近候选Bean对象
			//使用此BeanFactory的自动装配候选解析器获取descriptor的默认值
			Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
			if (value != null) {

				//如果value是String类型
				if (value instanceof String) {

					//解析嵌套的值(如果value是表达式会解析出该表达式的值)
					String strVal = resolveEmbeddedValue((String) value);

					//获取beanName的合并后RootBeanDefinition
					BeanDefinition bd = (beanName != null && containsBean(beanName) ?
							getMergedBeanDefinition(beanName) : null);

					//评估bd中包含的value,如果strVal是可解析表达式，会对其进行解析.
					value = evaluateBeanDefinitionString(strVal, bd);
				}

				//如果没有传入typeConverter,则引用工厂的类型转换器
				TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
				try {

					//将value转换为type的实例对象
					return converter.convertIfNecessary(value, type, descriptor.getTypeDescriptor());
				}
				catch (UnsupportedOperationException ex) {
					// A custom TypeConverter which does not support TypeDescriptor resolution...
					return (descriptor.getField() != null ?
							converter.convertIfNecessary(value, type, descriptor.getField()) :
							converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
				}
			}

			// 尝试针对descriptor所包装的对象类型是[stream,数组,Collection类型且对象类型是接口,Map]的情况，
			// 进行解析与依赖类型匹配的候选Bean对象并将其封装成相应的依赖类型对象
			// 并将其封装成相应的依赖类型对象
			Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
			if (multipleBeans != null) {
				return multipleBeans;
			}

			//尝试与type匹配的唯一候选bean对象

			// 查找与type匹配的候选bean对象,构建成Map，key=bean名,val=Bean对象
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (matchingBeans.isEmpty()) {

				//如果descriptor需要注入
				if (isRequired(descriptor)) {

					//抛出NoSuchBeanDefinitionException或BeanNotOfRequiredTypeException以解决不可解决的依赖关系
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}
				return null;
			}

			//定义用于存储唯一的候选Bean名变量
			String autowiredBeanName;

			//定义用于存储唯一的候选Bean对象变量
			Object instanceCandidate;

			if (matchingBeans.size() > 1) {

				//确定candidates中可以自动注入的最佳候选Bean名称
				autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);

				if (autowiredBeanName == null) {

					//descriptor需要注入 或者 type不是数组/集合类型
					if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {

						// 让descriptor尝试选择其中一个实例，默认实现是抛出NoUniqueBeanDefinitionException.
						return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
					}
					else {
						// In case of an optional Collection/Map, silently ignore a non-unique case:
						// possibly it was meant to be an empty collection of multiple regular beans
						// (before 4.3 in particular when we didn't even look for collection beans).
						// 在可选的 CollectionMap 的情况下，默默地忽略一个非唯一的情况：
						// 它可能是多个常规 bean 的空集合（特别是在 4.3 之前，当我们甚至没有寻找集合 bean 时）。
						return null;
					}
				}

				// 最佳候选Bean实例
				instanceCandidate = matchingBeans.get(autowiredBeanName);
			}
			else {
				// We have exactly one match.
				//这个时候matchingBeans不会没有元素的，因为前面已经检查了
				//获取machingBeans唯一的元素
				Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
				autowiredBeanName = entry.getKey();
				instanceCandidate = entry.getValue();
			}

			if (autowiredBeanNames != null) {

				//将autowiredBeanName添加到autowiredBeanNames中，又添加一次
				autowiredBeanNames.add(autowiredBeanName);
			}

			//如果instanceCandidate是Class实例
			if (instanceCandidate instanceof Class) {

				//让instanceCandidate引用 descriptor对autowiredBeanName解析为该工厂的Bean实例
				instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
			}
			Object result = instanceCandidate;

			if (result instanceof NullBean) {

				//如果descriptor需要注入
				if (isRequired(descriptor)) {

					//抛出NoSuchBeanDefinitionException或BeanNotOfRequiredTypeException以解决不可解决的依赖关系
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}

				//返回null，表示找不到最佳候选Bean对象
				result = null;
			}

			//如果result不是type的实例
			if (!ClassUtils.isAssignableValue(type, result)) {

				//抛出Bean不是必需类型异常
				throw new BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate.getClass());
			}

			//返回最佳候选Bean对象【result】
			return result;
		}
		finally {

			//设置上一个切入点对象
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	/**
	 * 针对desciptor所包装的对象类型是[stream,数组,Collection类型且对象类型是接口,Map]的情况，进行解析与依赖类型匹配的候选Bean对象，
	 * 并将其封装成相应的依赖类型对象
	 *
	 * @param descriptor
	 * @param beanName
	 * @param autowiredBeanNames
	 * @param typeConverter
	 * @return
	 */
	@Nullable
	private Object resolveMultipleBeans(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) {

		// 获取包装的参数/字段的声明的(非通用)类型
		Class<?> type = descriptor.getDependencyType();

		// 如果描述符是Stream依赖项描述符
		if (descriptor instanceof StreamDependencyDescriptor) {

			// 查找与type匹配的候选bean对象;构建成Map，key=bean名,val=Bean对象
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);

			// 自动注入匹配成功的候选Bean名集合不为null
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}

			// 取出除Bean对象为NullBean以外的所有候选Bean名称的Bean对象,将name解析为该Bean工厂的Bean实例
			// 只要收集bean对象不为NullBean对象
			Stream<Object> stream = matchingBeans.keySet().stream()
					.map(name -> descriptor.resolveCandidate(name, type, this))
					.filter(bean -> !(bean instanceof NullBean));

			// 如果descriptor需要排序
			if (((StreamDependencyDescriptor) descriptor).isOrdered()) {

				// 根据matchingBean构建排序比较器，交由steam进行排序
				stream = stream.sorted(adaptOrderComparator(matchingBeans));
			}

			// 返回已排好序且已存放除Bean对象为NullBean以外的所有候选Bean名称的Bean对象的stream对象
			return stream;
		}

		// 如果依赖类型是数组类型
		else if (type.isArray()) {

			// 获取type的元素Class对象
			Class<?> componentType = type.getComponentType();

			// 获取descriptor包装的参数/字段所构建出来的ResolvableType对象
			ResolvableType resolvableType = descriptor.getResolvableType();

			// 让resolvableType引用解析出的对应的数组Class对象，如果解析失败，就引用type
			Class<?> resolvedArrayType = resolvableType.resolve(type);

			// 如果resolvedArrayType与type不是同一个Class对象
			if (resolvedArrayType != type) {

				// componentType就引用resolvableType解析处理的元素Class对象
				componentType = resolvableType.getComponentType().resolve();
			}

			// 如果没有元素Class对象，就返回null，表示获取不到候选bean对象
			if (componentType == null) {
				return null;
			}

			// MultiElementDescriptor:具有嵌套元素的多元素声明的依赖描述符，表示集合/数组依赖
			// 查找与componentType匹配的候选bean对象;构建成Map，key=bean名,val=Bean对象
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType,
					new MultiElementDescriptor(descriptor));

			// 如果matchingBeans为空，表示获取不到候选bean对象
			if (matchingBeans.isEmpty()) {
				return null;
			}

			// 自动注入匹配成功的候选Bean名集合不为null
			if (autowiredBeanNames != null) {

				// 将所有的自动注入匹配成功的候选Bean名添加到autowiredBeanNames
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}

			// 如果有传入类型转换器就引用传入的类型转换器，否则获取此BeanFactory使用的类型转换器
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());

			// 将所有候选Bean对象转换为resolvedArrayType类型
			Object result = converter.convertIfNecessary(matchingBeans.values(), resolvedArrayType);

			// 如果result是数组实例
			if (result instanceof Object[]) {

				// 构建依赖比较器,用于对matchingBean的所有bean对象进行优先级排序
				Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
				if (comparator != null) {

					// 使用comparator对result数组进行排序
					Arrays.sort((Object[]) result, comparator);
				}
			}
			return result;
		}

		// 如果依赖类型属于Collection类型且依赖类型是接口
		else if (Collection.class.isAssignableFrom(type) && type.isInterface()) {

			// 将descriptor所包装的参数/字段构建出来的ResolvableType对象解析成Collection类型，然后解析出其泛型参数的Class对象
			Class<?> elementType = descriptor.getResolvableType().asCollection().resolveGeneric();
			if (elementType == null) {
				return null;
			}

			// 获取类型匹配的beanName和bean实例集合
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, elementType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}

			// 自动注入匹配成功的候选Bean名集合不为null
			if (autowiredBeanNames != null) {

				// 将所有的自动注入匹配成功的候选Bean名添加到autowiredBeanNames
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}

			// 如果有传入类型转换器就引用传入的类型转换器，否则获取此BeanFactory使用的类型转换器
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());

			// 将所有候选Bean对象转换为resolvedArrayType类型
			Object result = converter.convertIfNecessary(matchingBeans.values(), type);
			if (result instanceof List) {
				if (((List<?>) result).size() > 1) {

					// 构建依赖比较器,用于对matchingBean的所有bean对象进行优先级排序
					Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
					if (comparator != null) {

						// 使用comparator对result数组进行排序
						((List<?>) result).sort(comparator);
					}
				}
			}
			return result;
		}

		// 如果依赖类型是Map类型
		else if (Map.class == type) {

			// 将descriptor所包装的参数/字段构建出来的ResolvableType对象解析成Map类型
			ResolvableType mapType = descriptor.getResolvableType().asMap();

			// 解析出第1个泛型参数的Class对象,即key的Class对象
			Class<?> keyType = mapType.resolveGeneric(0);
			if (String.class != keyType) {
				return null;
			}

			// 解析出第2个泛型参数的Class对象,即value的Class对象
			Class<?> valueType = mapType.resolveGeneric(1);

			// 如果keyType为null，即解析不出value的Class对象或者是根本没有value的Class对象
			if (valueType == null) {
				return null;
			}

			// 查找与valueType匹配的候选bean对象;构建成Map，key=bean名,val=Bean对象
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, valueType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {

				// 返回null，表示获取不到候选bean对象
				return null;
			}

			// 自动注入匹配成功的候选Bean名集合不为null
			if (autowiredBeanNames != null) {

				// 将所有的自动注入匹配成功的候选Bean名添加到autowiredBeanNames
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			return matchingBeans;
		}
		else {

			// 返回null，表示获取不到候选bean对象
			return null;
		}
	}

	private boolean isRequired(DependencyDescriptor descriptor) {
		return getAutowireCandidateResolver().isRequired(descriptor);
	}

	private boolean indicatesMultipleBeans(Class<?> type) {
		return (type.isArray() || (type.isInterface() &&
				(Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))));
	}

	/**
	 * 构建依赖比较器,用于对matchingBean的所有bean对象进行优先级排序
	 *
	 * @param matchingBeans
	 * @return
	 */
	@Nullable
	private Comparator<Object> adaptDependencyComparator(Map<String, ?> matchingBeans) {

		// 获取该工厂的依赖关系比较器，SpringBoot默认使用 AnnotationAwareOrderComparator
		Comparator<Object> comparator = getDependencyComparator();
		if (comparator instanceof OrderComparator) {
			return ((OrderComparator) comparator).withSourceProvider(
					createFactoryAwareOrderSourceProvider(matchingBeans));
		}
		else {
			return comparator;
		}
	}

	/**
	 *  构建排序比较器,用于对matchingBean的所有bean对象进行优先级排序
	 *
	 * @param matchingBeans
	 * @return
	 */
	private Comparator<Object> adaptOrderComparator(Map<String, ?> matchingBeans) {

		// 获取该工厂的依赖关系比较器，SpringBoot默认使用 AnnotationAwareOrderComparator
		Comparator<Object> dependencyComparator = getDependencyComparator();

		// 如果dependencyComparator是OrderComparator的实例,就让comparator引用该实例，否则使用OrderComparator的默认实例
		OrderComparator comparator = (dependencyComparator instanceof OrderComparator ?
				(OrderComparator) dependencyComparator : OrderComparator.INSTANCE);

		// 创建工厂感知排序源提供者实例【FactoryAwareOrderSourceProvider】,并让comparator引用它
		return comparator.withSourceProvider(createFactoryAwareOrderSourceProvider(matchingBeans));
	}

	/**
	 * 创建工厂感知排序源提供者实例
	 *
	 * @param beans
	 * @return
	 */
	private OrderComparator.OrderSourceProvider createFactoryAwareOrderSourceProvider(Map<String, ?> beans) {

		// IdentityHashMap:允许"相同"的key保存进来,所谓的"相同"是指key的hashCode()和equal()的返回值相同.在使用get()的时候
		// 需要保证与key是同一个对象(即地址值相同)才能获取到对应的value.因为IdentityHashMap比较key值时，直接使用的是==
		// 定义一个IdentityHashMap对象,用于保存要排序的Bean对象，key为Bean对象，value为bean名
		IdentityHashMap<Object, String> instancesToBeanNames = new IdentityHashMap<>();

		// 将beans的所有key/value添加到instancesToBeanNames中
		beans.forEach((beanName, instance) -> instancesToBeanNames.put(instance, beanName));

		// 新建一个工厂感知排序源提供者实例：提供要排序对象的Order来源,用于代替obj获取优先级值。主要Order来源:
		//  1. obj对应的Bean名的合并后RootBeanDefinition的工厂方法对象
		//  2. obj对应的Bean名的合并后RootBeanDefinition的目标类型
		return new FactoryAwareOrderSourceProvider(instancesToBeanNames);
	}

	/**
	 * 查找与type匹配的候选bean对象,构建成Map，key=bean名,val=Bean对象【在自动装配指定bean期间调用】
	 *
	 * Find bean instances that match the required type.
	 * Called during autowiring for the specified bean.
	 * @param beanName the name of the bean that is about to be wired
	 * @param requiredType the actual type of bean to look for
	 * (may be an array component type or collection element type)
	 * @param descriptor the descriptor of the dependency to resolve
	 * @return a Map of candidate names and candidate instances that match
	 * the required type (never {@code null})
	 * @throws BeansException in case of errors
	 * @see #autowireByType
	 * @see #autowireConstructor
	 */
	protected Map<String, Object> findAutowireCandidates(
			@Nullable String beanName, Class<?> requiredType, DependencyDescriptor descriptor) {

		// 获取requiredType的所有bean名,包括父级工厂中定义的名称
		String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
				this, requiredType, true, descriptor.isEager());

		// 定义用于保存匹配requiredType的bean名和其实例对象的Map，即匹配成功的候选Map
		Map<String, Object> result = CollectionUtils.newLinkedHashMap(candidateNames.length);

		// 从存放着手动显示注册的依赖项类型-相应的自动装配值的缓存中匹配候选
		// 遍历从依赖项类型映射到相应的自动装配值缓存
		for (Map.Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
			Class<?> autowiringType = classObjectEntry.getKey();

			// 如果autowiringType是属于requiredType的实例
			if (autowiringType.isAssignableFrom(requiredType)) {
				Object autowiringValue = classObjectEntry.getValue();

				// 根据requiredType解析autowiringValue,并针对autowiringValue是ObjectFactory的情况进行解析
				autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);

				// 如果autowiringValue是requiredType类型
				if (requiredType.isInstance(autowiringValue)) {

					// objectUtils.identityToString:可得到(obj的全类名+'@'+obj的hashCode的十六进制字符串),如果obj为null，返回空字符串
					// 根据autowiringValue构建出唯一ID与autowiringValue绑定到result中
					result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
					break;
				}
			}
		}

		// 常规匹配候选
		for (String candidate : candidateNames) {

			// 如果beanName与candidateName所对应的Bean对象不是同一个且candidate可以自动注入
			if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {

				// 添加一个条目在result中:一个bean实例(如果可用)或仅一个已解析的类型
				addCandidateEntry(result, candidate, descriptor, requiredType);
			}
		}

		// 找不到候选时，就采用将回退模式尽可能的匹配到候选，一般情况下不会出现回退情况,除非代码非常糟糕
		if (result.isEmpty()) {

			// requiredType是否是数组/集合类型的标记
			boolean multiple = indicatesMultipleBeans(requiredType);
			// Consider fallback matches if the first pass failed to find anything...
			// 如果第一遍未能找到任何东西，请考虑回退匹配......
			// 在允许回退的情况下，候选Bean具有无法解析的泛型 || 候选Bean的Class对象是Properties类对象时，
			// 都允许成为 该描述符的可自动注入对象
			// 获取descriptor的一个旨在用于回退匹配的描述符的变体
			DependencyDescriptor fallbackDescriptor = descriptor.forFallbackMatch();

			// 先尝试匹配候选bean名符合允许回退匹配的依赖描述符的自动依赖条件且(依赖类型不是集合/数组或者描述符指定限定符)的候选Bean对象
			for (String candidate : candidateNames) {

				// getAutowireCandidateResolver()得到是QualifierAnnotationAutowireCandidateResolver实例,hasQualifier方法才有真正的限定符语义。
				// 如果beanName与candidateName所对应的Bean对象不是同一个且candidate可以自动注入且(type不是数组/集合类型或者
				// desciptor有@Qualifier注解或qualifier标准修饰)
				if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, fallbackDescriptor) &&
						(!multiple || getAutowireCandidateResolver().hasQualifier(descriptor))) {
					addCandidateEntry(result, candidate, descriptor, requiredType);
				}
			}

			// 匹配除beanName符合描述符依赖类型不是数组/集合
			// 且如果beanName与candidateName所对应的Bean对象不是同一个
			// 且(descriptor不是集合依赖或者beanName与candidate不相同)
			// 且候选bean名符合允许回退匹配的依赖描述符的自动依赖条件
			// 如果result为空且requiredType不是数组/集合类型或者
			if (result.isEmpty() && !multiple) {
				// Consider self references as a final pass...将自引用视为最后一步……
				// but in the case of a dependency collection, not the very same bean itself.
				// 但在依赖集合的情况下，不是完全相同的 bean 本身
				for (String candidate : candidateNames) {

					// 如果beanName与candidateName所对应的Bean对象不是同一个且(descriptor不是MultiElementDescriptor实例(即集合依赖)或者
					// beanName不等于candidate)且candidate可以自动注入
					if (isSelfReference(beanName, candidate) &&
							(!(descriptor instanceof MultiElementDescriptor) || !beanName.equals(candidate)) &&
							isAutowireCandidate(candidate, fallbackDescriptor)) {
						addCandidateEntry(result, candidate, descriptor, requiredType);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Add an entry to the candidate map: a bean instance if available or just the resolved
	 * type, preventing early bean initialization ahead of primary candidate selection.
	 *
	 * 向候选映射添加一个条目：一个 bean 实例（如果可用）或只是解析类型，防止在主要候选选择之前进行早期 bean 初始化。
	 *
	 */
	private void addCandidateEntry(Map<String, Object> candidates, String candidateName,
			DependencyDescriptor descriptor, Class<?> requiredType) {

		// MultiElementDescriptor:具有嵌套元素的多元素声明的依赖描述符，表示集合/数组依赖
		// 如果descriptor是MultiElementDescriptor的实例
		if (descriptor instanceof MultiElementDescriptor) {

			// 获取candidateName的该工厂的Bean实例
			Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);

			// 如果beanInstance不为空，添加到候选集合中
			if (!(beanInstance instanceof NullBean)) {
				candidates.put(candidateName, beanInstance);
			}
		}

		// StreamDependencyDescriptor:用于访问多个元素的流依赖项描述符标记，即属性依赖是stream类型且
		// 如果beanName是在该BeanFactory的单例对象的高速缓存Map集合中或者(descriptor是SteamDependencyDescriptor实例且该实例有排序标记)
		else if (containsSingleton(candidateName) || (descriptor instanceof StreamDependencyDescriptor &&
				((StreamDependencyDescriptor) descriptor).isOrdered())) {

			// 获取candidateName的该工厂的Bean实例
			Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);

			// 将beanInstance添加到候选集合，可能为null
			candidates.put(candidateName, (beanInstance instanceof NullBean ? null : beanInstance));
		}
		// candidateName所对应的bean不是单例
		else {

			// 将candidateName和其对应的Class对象绑定到candidates中
			candidates.put(candidateName, getType(candidateName));
		}
	}

	/**
	 * 候选原则，Primary优先，priority次之，最后再看名字是否对的上
	 *
	 * Determine the autowire candidate in the given set of beans.
	 * <p>Looks for {@code @Primary} and {@code @Priority} (in that order).
	 * @param candidates a Map of candidate names and candidate instances
	 * that match the required type, as returned by {@link #findAutowireCandidates}
	 * @param descriptor the target dependency to match against
	 * @return the name of the autowire candidate, or {@code null} if none found
	 */
	@Nullable
	protected String determineAutowireCandidate(Map<String, Object> candidates, DependencyDescriptor descriptor) {
		Class<?> requiredType = descriptor.getDependencyType();

		// 如果有主要候选者，返回主要候选者
		String primaryCandidate = determinePrimaryCandidate(candidates, requiredType);
		if (primaryCandidate != null) {
			return primaryCandidate;
		}

		// 如果有优先级最高的候选者，返回优先级最高的候选者
		String priorityCandidate = determineHighestPriorityCandidate(candidates, requiredType);
		if (priorityCandidate != null) {
			return priorityCandidate;
		}

		// Fallback
		// 匹配参数依赖名字是否和类型名字一样
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateName = entry.getKey();
			Object beanInstance = entry.getValue();
			if ((beanInstance != null && this.resolvableDependencies.containsValue(beanInstance)) ||
					matchesBeanName(candidateName, descriptor.getDependencyName())) {
				return candidateName;
			}
		}
		return null;
	}

	/**
	 * Determine the primary candidate in the given set of beans.
	 * 确定给定 bean 集中的主要候选者。
	 *
	 * @param candidates a Map of candidate names and candidate instances
	 * (or candidate classes if not created yet) that match the required type
	 * @param requiredType the target dependency type to match against
	 * @return the name of the primary candidate, or {@code null} if none found
	 * @see #isPrimary(String, Object)
	 */
	@Nullable
	protected String determinePrimaryCandidate(Map<String, Object> candidates, Class<?> requiredType) {
		String primaryBeanName = null;
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateBeanName = entry.getKey();
			Object beanInstance = entry.getValue();

			// 检查candidateBeanName对应的bean是否标记为主要的自动装配候选者。
			if (isPrimary(candidateBeanName, beanInstance)) {

				if (primaryBeanName != null) {

					boolean candidateLocal = containsBeanDefinition(candidateBeanName);

					boolean primaryLocal = containsBeanDefinition(primaryBeanName);

					// 如果包含多个被标记为主要的自动装配候选者的bean，则抛出异常
					if (candidateLocal && primaryLocal) {
						throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
								"more than one 'primary' bean found among candidates: " + candidates.keySet());
					}

					// 后来者优先
					else if (candidateLocal) {
						primaryBeanName = candidateBeanName;
					}
				}
				else {

					// 记录被标记为主要的自动装配候选者的bean名称
					primaryBeanName = candidateBeanName;
				}
			}
		}
		return primaryBeanName;
	}

	/**
	 * Determine the candidate with the highest priority in the given set of beans.
	 * 确定给定 bean 集中具有最高优先级的候选者。
	 *
	 * <p>Based on {@code @jakarta.annotation.Priority}. As defined by the related
	 * {@link org.springframework.core.Ordered} interface, the lowest value has
	 * the highest priority.
	 * @param candidates a Map of candidate names and candidate instances
	 * (or candidate classes if not created yet) that match the required type
	 * @param requiredType the target dependency type to match against
	 * @return the name of the candidate with the highest priority,
	 * or {@code null} if none found
	 * @see #getPriority(Object)
	 */
	@Nullable
	protected String determineHighestPriorityCandidate(Map<String, Object> candidates, Class<?> requiredType) {
		String highestPriorityBeanName = null;
		Integer highestPriority = null;
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateBeanName = entry.getKey();
			Object beanInstance = entry.getValue();
			if (beanInstance != null) {

				// 获取优先级
				Integer candidatePriority = getPriority(beanInstance);
				if (candidatePriority != null) {
					if (highestPriorityBeanName != null) {

						// 如果出现了两个优先级相等的候选者，抛出异常
						if (candidatePriority.equals(highestPriority)) {
							throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
									"Multiple beans found with the same priority ('" + highestPriority +
									"') among candidates: " + candidates.keySet());
						}
						else if (candidatePriority < highestPriority) {

							// 记录当前优先级最高的beanName
							highestPriorityBeanName = candidateBeanName;

							// 记录已有的最高优先级
							highestPriority = candidatePriority;
						}
					}
					else {
						// 记录当前优先级最高的beanName
						highestPriorityBeanName = candidateBeanName;

						// 记录已有的最高优先级
						highestPriority = candidatePriority;
					}
				}
			}
		}
		return highestPriorityBeanName;
	}

	/**
	 * Return whether the bean definition for the given bean name has been
	 * marked as a primary bean.
	 * 返回给定 bean 名称的 bean 定义是否已标记为主要 bean。
	 *
	 * @param beanName the name of the bean
	 * @param beanInstance the corresponding bean instance (can be null)
	 * @return whether the given bean qualifies as primary
	 */
	protected boolean isPrimary(String beanName, Object beanInstance) {

		// 获取beanName的规范名称
		String transformedBeanName = transformedBeanName(beanName);

		// 如果有transformedBeanName对应的definition，则直接查看primary是否标记为主要的自动装配候选者。
		if (containsBeanDefinition(transformedBeanName)) {
			return getMergedLocalBeanDefinition(transformedBeanName).isPrimary();
		}

		BeanFactory parent = getParentBeanFactory();

		// 如果当前beanFactory中没有transformedBeanName对应的definition信息，则在父工厂中检查
		return (parent instanceof DefaultListableBeanFactory &&
				((DefaultListableBeanFactory) parent).isPrimary(transformedBeanName, beanInstance));
	}

	/**
	 * Return the priority assigned for the given bean instance by
	 * the {@code jakarta.annotation.Priority} annotation.
	 * <p>The default implementation delegates to the specified
	 * {@link #setDependencyComparator dependency comparator}, checking its
	 * {@link OrderComparator#getPriority method} if it is an extension of
	 * Spring's common {@link OrderComparator} - typically, an
	 * {@link org.springframework.core.annotation.AnnotationAwareOrderComparator}.
	 * If no such comparator is present, this implementation returns {@code null}.
	 * @param beanInstance the bean instance to check (can be {@code null})
	 * @return the priority assigned to that bean or {@code null} if none is set
	 */
	@Nullable
	protected Integer getPriority(Object beanInstance) {
		Comparator<Object> comparator = getDependencyComparator();
		if (comparator instanceof OrderComparator) {
			return ((OrderComparator) comparator).getPriority(beanInstance);
		}
		return null;
	}

	/**
	 * Determine whether the given candidate name matches the bean name or the aliases
	 * stored in this bean definition.
	 */
	protected boolean matchesBeanName(String beanName, @Nullable String candidateName) {
		return (candidateName != null &&
				(candidateName.equals(beanName) || ObjectUtils.containsElement(getAliases(beanName), candidateName)));
	}

	/**
	 * Determine whether the given beanName/candidateName pair indicates a self reference,
	 * i.e. whether the candidate points back to the original bean or to a factory method
	 * on the original bean.
	 *
	 * 确定给定的 beanName/candidateName 对是否表示自引用，即候选是否指向原始 bean 或原始 bean 上的工厂方法。
	 */
	private boolean isSelfReference(@Nullable String beanName, @Nullable String candidateName) {
		return (beanName != null && candidateName != null &&
				(beanName.equals(candidateName) || (containsBeanDefinition(candidateName) &&
						beanName.equals(getMergedLocalBeanDefinition(candidateName).getFactoryBeanName()))));
	}

	/**
	 * Raise a NoSuchBeanDefinitionException or BeanNotOfRequiredTypeException
	 * for an unresolvable dependency.
	 *
	 * 为无法解析的依赖项引发 NoSuchBeanDefinitionException 或 BeanNotOfRequiredTypeException 。
	 */
	private void raiseNoMatchingBeanFound(
			Class<?> type, ResolvableType resolvableType, DependencyDescriptor descriptor) throws BeansException {

		checkBeanNotOfRequiredType(type, descriptor);

		throw new NoSuchBeanDefinitionException(resolvableType,
				"expected at least 1 bean which qualifies as autowire candidate. " +
				"Dependency annotations: " + ObjectUtils.nullSafeToString(descriptor.getAnnotations()));
	}

	/**
	 * Raise a BeanNotOfRequiredTypeException for an unresolvable dependency, if applicable,
	 * i.e. if the target type of the bean would match but an exposed proxy doesn't.
	 *
	 * 如果适用，为无法解析的依赖项引发 BeanNotOfRequiredTypeException，即如果 bean 的目标类型匹配但公开的代理不匹配。
	 */
	private void checkBeanNotOfRequiredType(Class<?> type, DependencyDescriptor descriptor) {
		for (String beanName : this.beanDefinitionNames) {
			try {
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				Class<?> targetType = mbd.getTargetType();
				if (targetType != null && type.isAssignableFrom(targetType) &&
						isAutowireCandidate(beanName, mbd, descriptor, getAutowireCandidateResolver())) {
					// Probably a proxy interfering with target type match -> throw meaningful exception.
					Object beanInstance = getSingleton(beanName, false);
					Class<?> beanType = (beanInstance != null && beanInstance.getClass() != NullBean.class ?
							beanInstance.getClass() : predictBeanType(beanName, mbd));
					if (beanType != null && !type.isAssignableFrom(beanType)) {
						throw new BeanNotOfRequiredTypeException(beanName, type, beanType);
					}
				}
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Bean definition got removed while we were iterating -> ignore.
			}
		}

		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof DefaultListableBeanFactory) {
			((DefaultListableBeanFactory) parent).checkBeanNotOfRequiredType(type, descriptor);
		}
	}

	/**
	 * 创建Optional类型的符合descriptor要求的候选Bean对象
	 *
	 * Create an {@link Optional} wrapper for the specified dependency.
	 */
	private Optional<?> createOptionalDependency(
			DependencyDescriptor descriptor, @Nullable String beanName, final Object... args) {

		// NestedDependencyDescriptor：嵌套元素的依赖项描述符标记，一般表示Optional类型依赖
		// 新建一个NestedDependencyDescriptor实例,该实例不要求一定要得到候选Bean对象，
		// 且可根据arg构建候选Bean对象(当Bean是{@link #SCOPE_PROTOTYPE}时)
		DependencyDescriptor descriptorToUse = new NestedDependencyDescriptor(descriptor) {


			//不要求一定要得到候选Bean对象
			@Override
			public boolean isRequired() {
				return false;
			}

			// 将指定的Bean名称解析为给定工厂的Bean实例，作为对此依赖项的匹配算法的候选结果
			@Override
			public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
				return (!ObjectUtils.isEmpty(args) ? beanFactory.getBean(beanName, args) :
						super.resolveCandidate(beanName, requiredType, beanFactory));
			}
		};

		// 解析出与descriptor所包装的对象匹配的后续Bean对象
		Object result = doResolveDependency(descriptorToUse, beanName, null, null);

		// 如果result是Optional的实例,就将其强转为Optional后返回出去；否则将result包装到Optional对象中再返回出去
		return (result instanceof Optional ? (Optional<?>) result : Optional.ofNullable(result));
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(ObjectUtils.identityToString(this));
		sb.append(": defining beans [");
		sb.append(StringUtils.collectionToCommaDelimitedString(this.beanDefinitionNames));
		sb.append("]; ");
		BeanFactory parent = getParentBeanFactory();
		if (parent == null) {
			sb.append("root of factory hierarchy");
		}
		else {
			sb.append("parent: ").append(ObjectUtils.identityToString(parent));
		}
		return sb.toString();
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		throw new NotSerializableException("DefaultListableBeanFactory itself is not deserializable - " +
				"just a SerializedBeanFactoryReference is");
	}

	protected Object writeReplace() throws ObjectStreamException {
		if (this.serializationId != null) {
			return new SerializedBeanFactoryReference(this.serializationId);
		}
		else {
			throw new NotSerializableException("DefaultListableBeanFactory has no serialization id");
		}
	}


	/**
	 * Minimal id reference to the factory.
	 * Resolved to the actual factory instance on deserialization.
	 */
	private static class SerializedBeanFactoryReference implements Serializable {

		private final String id;

		public SerializedBeanFactoryReference(String id) {
			this.id = id;
		}

		private Object readResolve() {
			Reference<?> ref = serializableFactories.get(this.id);
			if (ref != null) {
				Object result = ref.get();
				if (result != null) {
					return result;
				}
			}
			// Lenient fallback: dummy factory in case of original factory not found...
			DefaultListableBeanFactory dummyFactory = new DefaultListableBeanFactory();
			dummyFactory.serializationId = this.id;
			return dummyFactory;
		}
	}


	/**
	 * A dependency descriptor marker for nested elements.
	 */
	private static class NestedDependencyDescriptor extends DependencyDescriptor {

		public NestedDependencyDescriptor(DependencyDescriptor original) {
			super(original);
			increaseNestingLevel();
		}
	}


	/**
	 * A dependency descriptor for a multi-element declaration with nested elements.
	 */
	private static class MultiElementDescriptor extends NestedDependencyDescriptor {

		public MultiElementDescriptor(DependencyDescriptor original) {
			super(original);
		}
	}


	/**
	 * A dependency descriptor marker for stream access to multiple elements.
	 *
	 * 用于流访问多个元素的依赖描述符标记。
	 */
	private static class StreamDependencyDescriptor extends DependencyDescriptor {

		private final boolean ordered;

		public StreamDependencyDescriptor(DependencyDescriptor original, boolean ordered) {
			super(original);
			this.ordered = ordered;
		}

		public boolean isOrdered() {
			return this.ordered;
		}
	}


	private interface BeanObjectProvider<T> extends ObjectProvider<T>, Serializable {
	}


	/**
	 * Serializable ObjectFactory/ObjectProvider for lazy resolution of a dependency.
	 */
	private class DependencyObjectProvider implements BeanObjectProvider<Object> {

		private final DependencyDescriptor descriptor;

		private final boolean optional;

		@Nullable
		private final String beanName;

		public DependencyObjectProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
			this.descriptor = new NestedDependencyDescriptor(descriptor);
			this.optional = (this.descriptor.getDependencyType() == Optional.class);
			this.beanName = beanName;
		}

		@Override
		public Object getObject() throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName);
			}
			else {
				Object result = doResolveDependency(this.descriptor, this.beanName, null, null);
				if (result == null) {
					throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
				}
				return result;
			}
		}

		@Override
		public Object getObject(final Object... args) throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName, args);
			}
			else {
				DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
					@Override
					public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
						return beanFactory.getBean(beanName, args);
					}
				};
				Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
				if (result == null) {
					throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
				}
				return result;
			}
		}

		@Override
		@Nullable
		public Object getIfAvailable() throws BeansException {
			try {
				if (this.optional) {
					return createOptionalDependency(this.descriptor, this.beanName);
				}
				else {
					DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
						@Override
						public boolean isRequired() {
							return false;
						}
					};
					return doResolveDependency(descriptorToUse, this.beanName, null, null);
				}
			}
			catch (ScopeNotActiveException ex) {
				// Ignore resolved bean in non-active scope
				return null;
			}
		}

		@Override
		public void ifAvailable(Consumer<Object> dependencyConsumer) throws BeansException {
			Object dependency = getIfAvailable();
			if (dependency != null) {
				try {
					dependencyConsumer.accept(dependency);
				}
				catch (ScopeNotActiveException ex) {
					// Ignore resolved bean in non-active scope, even on scoped proxy invocation
				}
			}
		}

		@Override
		@Nullable
		public Object getIfUnique() throws BeansException {
			DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
				@Override
				public boolean isRequired() {
					return false;
				}

				@Override
				@Nullable
				public Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) {
					return null;
				}
			};
			try {
				if (this.optional) {
					return createOptionalDependency(descriptorToUse, this.beanName);
				}
				else {
					return doResolveDependency(descriptorToUse, this.beanName, null, null);
				}
			}
			catch (ScopeNotActiveException ex) {
				// Ignore resolved bean in non-active scope
				return null;
			}
		}

		@Override
		public void ifUnique(Consumer<Object> dependencyConsumer) throws BeansException {
			Object dependency = getIfUnique();
			if (dependency != null) {
				try {
					dependencyConsumer.accept(dependency);
				}
				catch (ScopeNotActiveException ex) {
					// Ignore resolved bean in non-active scope, even on scoped proxy invocation
				}
			}
		}

		@Nullable
		protected Object getValue() throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName);
			}
			else {
				return doResolveDependency(this.descriptor, this.beanName, null, null);
			}
		}

		@Override
		public Stream<Object> stream() {
			return resolveStream(false);
		}

		@Override
		public Stream<Object> orderedStream() {
			return resolveStream(true);
		}

		@SuppressWarnings("unchecked")
		private Stream<Object> resolveStream(boolean ordered) {
			DependencyDescriptor descriptorToUse = new StreamDependencyDescriptor(this.descriptor, ordered);
			Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
			return (result instanceof Stream ? (Stream<Object>) result : Stream.of(result));
		}
	}


	/**
	 * Separate inner class for avoiding a hard dependency on the {@code jakarta.inject} API.
	 * Actual {@code jakarta.inject.Provider} implementation is nested here in order to make it
	 * invisible for Graal's introspection of DefaultListableBeanFactory's nested classes.
	 */
	private class Jsr330Factory implements Serializable {

		public Object createDependencyProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
			return new Jsr330Provider(descriptor, beanName);
		}

		private class Jsr330Provider extends DependencyObjectProvider implements Provider<Object> {

			public Jsr330Provider(DependencyDescriptor descriptor, @Nullable String beanName) {
				super(descriptor, beanName);
			}

			@Override
			@Nullable
			public Object get() throws BeansException {
				return getValue();
			}
		}
	}


	/**
	 * An {@link org.springframework.core.OrderComparator.OrderSourceProvider} implementation
	 * that is aware of the bean metadata of the instances to sort.
	 * <p>Lookup for the method factory of an instance to sort, if any, and let the
	 * comparator retrieve the {@link org.springframework.core.annotation.Order}
	 * value defined on it. This essentially allows for the following construct:
	 */
	private class FactoryAwareOrderSourceProvider implements OrderComparator.OrderSourceProvider {

		private final Map<Object, String> instancesToBeanNames;

		public FactoryAwareOrderSourceProvider(Map<Object, String> instancesToBeanNames) {
			this.instancesToBeanNames = instancesToBeanNames;
		}

		@Override
		@Nullable
		public Object getOrderSource(Object obj) {
			String beanName = this.instancesToBeanNames.get(obj);
			if (beanName == null || !containsBeanDefinition(beanName)) {
				return null;
			}
			RootBeanDefinition beanDefinition = getMergedLocalBeanDefinition(beanName);
			List<Object> sources = new ArrayList<>(2);
			Method factoryMethod = beanDefinition.getResolvedFactoryMethod();
			if (factoryMethod != null) {
				sources.add(factoryMethod);
			}
			Class<?> targetType = beanDefinition.getTargetType();
			if (targetType != null && targetType != obj.getClass()) {
				sources.add(targetType);
			}
			return sources.toArray();
		}
	}

}
