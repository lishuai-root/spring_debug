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

import java.beans.ConstructorProperties;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MethodInvoker;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Delegate for resolving constructors and factory methods.
 *
 * <p>Performs constructor resolution through argument matching.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 2.0
 * @see #autowireConstructor
 * @see #instantiateUsingFactoryMethod
 * @see AbstractAutowireCapableBeanFactory
 */
class ConstructorResolver {

	private static final Object[] EMPTY_ARGS = new Object[0];

	/**
	 * Marker for autowired arguments in a cached argument array, to be replaced
	 * by a {@linkplain #resolveAutowiredArgument resolved autowired argument}.
	 */
	private static final Object autowiredArgumentMarker = new Object();

	private static final NamedThreadLocal<InjectionPoint> currentInjectionPoint =
			new NamedThreadLocal<>("Current injection point");


	private final AbstractAutowireCapableBeanFactory beanFactory;

	private final Log logger;


	/**
	 * Create a new ConstructorResolver for the given factory and instantiation strategy.
	 * 为给定的工厂和实例化策略创建一个新的 ConstructorResolver。
	 *
	 * @param beanFactory the BeanFactory to work with
	 */
	public ConstructorResolver(AbstractAutowireCapableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.logger = beanFactory.getLogger();
	}


	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * “自动装配构造函数”（按类型带有构造函数参数）行为。如果指定了显式构造函数参数值，
	 * 也适用，将所有剩余参数与来自 bean 工厂的 bean 匹配。
	 *
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * 这对应于构造函数注入：在这种模式下，Spring bean 工厂能够承载期望基于构造函数的依赖项解析的组件。
	 *
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param chosenCtors chosen candidate constructors (or {@code null} if none)
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd,
			@Nullable Constructor<?>[] chosenCtors, @Nullable Object[] explicitArgs) {

		BeanWrapperImpl bw = new BeanWrapperImpl();
		this.beanFactory.initBeanWrapper(bw);

		Constructor<?> constructorToUse = null;
		ArgumentsHolder argsHolderToUse = null;
		Object[] argsToUse = null;

		if (explicitArgs != null) {
			argsToUse = explicitArgs;
		}
		else {
			Object[] argsToResolve = null;
			synchronized (mbd.constructorArgumentLock) {
				constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached constructor...
					argsToUse = mbd.resolvedConstructorArguments;
					if (argsToUse == null) {
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			if (argsToResolve != null) {
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve);
			}
		}

		if (constructorToUse == null || argsToUse == null) {
			// Take specified constructors, if any.
			Constructor<?>[] candidates = chosenCtors;
			if (candidates == null) {
				Class<?> beanClass = mbd.getBeanClass();
				try {
					candidates = (mbd.isNonPublicAccessAllowed() ?
							beanClass.getDeclaredConstructors() : beanClass.getConstructors());
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Resolution of declared constructors on bean Class [" + beanClass.getName() +
							"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
				}
			}

			if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Constructor<?> uniqueCandidate = candidates[0];
				if (uniqueCandidate.getParameterCount() == 0) {
					synchronized (mbd.constructorArgumentLock) {
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						mbd.constructorArgumentsResolved = true;
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					bw.setBeanInstance(instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS));
					return bw;
				}
			}

			// Need to resolve the constructor.
			boolean autowiring = (chosenCtors != null ||
					mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
			ConstructorArgumentValues resolvedValues = null;

			int minNrOfArgs;
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			}
			else {
				ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
				resolvedValues = new ConstructorArgumentValues();
				minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
			}

			AutowireUtils.sortConstructors(candidates);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			Set<Constructor<?>> ambiguousConstructors = null;
			Deque<UnsatisfiedDependencyException> causes = null;

			for (Constructor<?> candidate : candidates) {
				int parameterCount = candidate.getParameterCount();

				if (constructorToUse != null && argsToUse != null && argsToUse.length > parameterCount) {
					// Already found greedy constructor that can be satisfied ->
					// do not look any further, there are only less greedy constructors left.
					break;
				}
				if (parameterCount < minNrOfArgs) {
					continue;
				}

				ArgumentsHolder argsHolder;
				Class<?>[] paramTypes = candidate.getParameterTypes();
				if (resolvedValues != null) {
					try {
						String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, parameterCount);
						if (paramNames == null) {
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {
								paramNames = pnd.getParameterNames(candidate);
							}
						}
						argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
								getUserDeclaredConstructor(candidate), autowiring, candidates.length == 1);
					}
					catch (UnsatisfiedDependencyException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Ignoring constructor [" + candidate + "] of bean '" + beanName + "': " + ex);
						}
						// Swallow and try next constructor.
						if (causes == null) {
							causes = new ArrayDeque<>(1);
						}
						causes.add(ex);
						continue;
					}
				}
				else {
					// Explicit arguments given -> arguments length must match exactly.
					if (parameterCount != explicitArgs.length) {
						continue;
					}
					argsHolder = new ArgumentsHolder(explicitArgs);
				}

				int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
						argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
				// Choose this constructor if it represents the closest match.
				if (typeDiffWeight < minTypeDiffWeight) {
					constructorToUse = candidate;
					argsHolderToUse = argsHolder;
					argsToUse = argsHolder.arguments;
					minTypeDiffWeight = typeDiffWeight;
					ambiguousConstructors = null;
				}
				else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
					if (ambiguousConstructors == null) {
						ambiguousConstructors = new LinkedHashSet<>();
						ambiguousConstructors.add(constructorToUse);
					}
					ambiguousConstructors.add(candidate);
				}
			}

			if (constructorToUse == null) {
				if (causes != null) {
					UnsatisfiedDependencyException ex = causes.removeLast();
					for (Exception cause : causes) {
						this.beanFactory.onSuppressedException(cause);
					}
					throw ex;
				}
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Could not resolve matching constructor on bean class [" + mbd.getBeanClassName() + "] " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
			}
			else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous constructor matches found on bean class [" + mbd.getBeanClassName() + "] " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousConstructors);
			}

			if (explicitArgs == null && argsHolderToUse != null) {
				argsHolderToUse.storeCache(mbd, constructorToUse);
			}
		}

		Assert.state(argsToUse != null, "Unresolved constructor arguments");
		bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse, argsToUse));
		return bw;
	}

	private Object instantiate(
			String beanName, RootBeanDefinition mbd, Constructor<?> constructorToUse, Object[] argsToUse) {

		try {
			InstantiationStrategy strategy = this.beanFactory.getInstantiationStrategy();
			return strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via constructor failed", ex);
		}
	}

	/**
	 * Resolve the factory method in the specified bean definition, if possible.
	 * {@link RootBeanDefinition#getResolvedFactoryMethod()} can be checked for the result.
	 * @param mbd the bean definition to check
	 */
	public void resolveFactoryMethodIfPossible(RootBeanDefinition mbd) {
		Class<?> factoryClass;
		boolean isStatic;
		if (mbd.getFactoryBeanName() != null) {
			factoryClass = this.beanFactory.getType(mbd.getFactoryBeanName());
			isStatic = false;
		}
		else {
			factoryClass = mbd.getBeanClass();
			isStatic = true;
		}
		Assert.state(factoryClass != null, "Unresolvable factory class");
		factoryClass = ClassUtils.getUserClass(factoryClass);

		Method[] candidates = getCandidateMethods(factoryClass, mbd);
		Method uniqueCandidate = null;
		for (Method candidate : candidates) {
			if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
				if (uniqueCandidate == null) {
					uniqueCandidate = candidate;
				}
				else if (isParamMismatch(uniqueCandidate, candidate)) {
					uniqueCandidate = null;
					break;
				}
			}
		}
		mbd.factoryMethodToIntrospect = uniqueCandidate;
	}

	private boolean isParamMismatch(Method uniqueCandidate, Method candidate) {
		int uniqueCandidateParameterCount = uniqueCandidate.getParameterCount();
		int candidateParameterCount = candidate.getParameterCount();
		return (uniqueCandidateParameterCount != candidateParameterCount ||
				!Arrays.equals(uniqueCandidate.getParameterTypes(), candidate.getParameterTypes()));
	}

	/**
	 * Retrieve all candidate methods for the given class, considering
	 * the {@link RootBeanDefinition#isNonPublicAccessAllowed()} flag.
	 * Called as the starting point for factory method determination.
	 */
	private Method[] getCandidateMethods(Class<?> factoryClass, RootBeanDefinition mbd) {
		return (mbd.isNonPublicAccessAllowed() ?
				ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods());
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * bean definition parameter specifies a class, rather than a "factory-bean", or
	 * an instance variable on a factory object itself configured using Dependency Injection.
	 * 使用命名工厂方法实例化 bean。如果 bean 定义参数指定一个类，而不是“工厂 bean”，
	 * 或者使用依赖注入配置的工厂对象本身的实例变量，则该方法可能是静态的。
	 *
	 * <p>Implementation requires iterating over the static or instance methods with the
	 * name specified in the RootBeanDefinition (the method may be overloaded) and trying
	 * to match with the parameters. We don't have the types attached to constructor args,
	 * so trial and error is the only way to go here. The explicitArgs array may contain
	 * argument values passed in programmatically via the corresponding getBean method.
	 * 实现需要迭代具有在 RootBeanDefinition 中指定的名称的静态或实例方法（该方法可能被重载）并尝试与参数匹配。
	 * 我们没有附加到构造函数 args 的类型，所以反复试验是唯一的方法。
	 * explicitArgs 数组可能包含通过相应的 getBean 方法以编程方式传入的参数值。
	 *
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean
	 * method, or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	public BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		// 新建一个BeanWrapperImp实例，用于封装使用工厂方法生成与beanName对应的Bean对象
		BeanWrapperImpl bw = new BeanWrapperImpl();

		// 初始化实例包装类
		this.beanFactory.initBeanWrapper(bw);

		// 获取工厂Bean对象，工厂Bean对象的类对象，确定工厂方法是否是静态
		// 定义一个用于存放工厂Bean对象的Object
		Object factoryBean;

		// 定义一个用于存放工厂Bean对象的类对象的Class
		Class<?> factoryClass;

		// 定义一个表示是静态工厂方法的标记
		boolean isStatic;

		// 从mbd中获取配置的FactoryBean名
		String factoryBeanName = mbd.getFactoryBeanName();

		if (factoryBeanName != null) {

			// 如果factoryBean名与beanName相同
			if (factoryBeanName.equals(beanName)) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"factory-bean reference points back to the same bean definition");
			}

			// 从bean工厂中获取factoryBeanName所指的factoryBean对象
			factoryBean = this.beanFactory.getBean(factoryBeanName);

			// 如果mbd配置为单例作用域 且 beanName已经在bean工厂的单例对象的高速缓存Map中
			if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {

				// 这个时候意味着Bean工厂中已经有beanName的Bean对象，而这个还要生成多一个Bean名为BeanName的Bean对象，导致了冲突
				// 抛出ImplicitlyAppearedSingletonException，
				throw new ImplicitlyAppearedSingletonException();
			}

			// 注册factoryBeanName到beanName的依赖
			this.beanFactory.registerDependentBean(factoryBeanName, beanName);

			// 过去factoryBean的Class对象
			factoryClass = factoryBean.getClass();

			// 设置isStatic为false,表示不是静态方法
			isStatic = false;
		}
		else {
			// It's a static factory method on the bean class. 这是bean类上的静态工厂方法
			// 如果mbd没有指定bean类
			if (!mbd.hasBeanClass()) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"bean definition declares neither a bean class nor a factory-bean reference");
			}

			// 将factoryBean设为null
			factoryBean = null;

			// 指定factoryClass引用mbd指定的bean类
			factoryClass = mbd.getBeanClass();

			// 设置isStatic为true，表示是静态方法
			isStatic = true;
		}

		// 尝试从mbd的缓存属性中获取要使用的工厂方法，要使用的参数值数组
		// 声明一个要使用的工厂方法，默认为null
		Method factoryMethodToUse = null;

		// 声明一个用于存储不同形式的参数值的ArgumentsHolder，默认为null
		ArgumentsHolder argsHolderToUse = null;

		// 声明一个要使用的参数值数组,默认为null
		Object[] argsToUse = null;

		// 如果explicitArgs不为null
		if (explicitArgs != null) {

			// argsToUse就引用explicitArgs
			argsToUse = explicitArgs;
		}
		else {

			// 如果没有传,声明一个要解析的参数值数组，默认为null
			Object[] argsToResolve = null;
			synchronized (mbd.constructorArgumentLock) {

				// 指定factoryMethodToUser引用mbd已解析的构造函数或工厂方法对象
				factoryMethodToUse = (Method) mbd.resolvedConstructorOrFactoryMethod;

				// 如果factoryMethodToUser不为null且mbd已解析构造函数参数
				if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {

					// Found a cached factory method...  找到了缓存的工厂方法
					// 指定argsToUser引用mbd完全解析的构造函数参数值
					argsToUse = mbd.resolvedConstructorArguments;

					if (argsToUse == null) {

						// 指定argsToResolve引用mbd部分准备好的构造函数参数值
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}

			// 如果argsToResolve不为null,即表示mbd还没有完全解析的构造函数参数值
			if (argsToResolve != null) {

				// 解析缓存在mbd中准备好的参数值,允许在没有此类BeanDefinition的时候回退
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve);
			}
		}


		// 如果没解析过，就获取factoryClass的用户定义类型，因为此时factoryClass可能是CGLIB动态代理类型，
		// 所以要获取用父类的类型。如果工厂方法是唯一的，就是没重载的，就获取解析的工厂方法，如果不为空，就添加到一个不可变列表里，
		// 如果为空的话，就要去找出factoryClass的以及父类的所有的方法，进一步找出方法修饰符一致且名字跟工厂方法名字相同的且是bean注解的方法，并放入列表里。
		if (factoryMethodToUse == null || argsToUse == null) {
			// Need to determine the factory method...
			// Try all methods with this name to see if they match the given arguments.
			// 获取工厂类的所有候选工厂方法
			factoryClass = ClassUtils.getUserClass(factoryClass);

			// 定义一个用于存储候选方法的集合
			List<Method> candidates = null;

			// mbd配置的工程方法是否唯一
			if (mbd.isFactoryMethodUnique) {
				if (factoryMethodToUse == null) {

					// 获取mbd解析后的工厂方法对象
					factoryMethodToUse = mbd.getResolvedFactoryMethod();
				}
				if (factoryMethodToUse != null) {

					// Collections.singletonList()返回的是不可变的集合，但是这个长度的集合只有1，可以减少内存空间。但是返回的值依然是Collections的内部实现类，
					// 同样没有add的方法，调用add，set方法会报错
					// 新建一个不可变，只能存一个对象的集合，将factoryMethodToUse添加进行
					candidates = Collections.singletonList(factoryMethodToUse);
				}
			}

			// 如果candidateList为null，表示mbd配置的工程方法不唯一
			if (candidates == null) {

				candidates = new ArrayList<>();

				// 根据mbd的是否允许访问非公共构造函数和方法标记【RootBeanDefinition.isNonPublicAccessAllowed】来获取factoryClass的所有候选方法
				Method[] rawCandidates = getCandidateMethods(factoryClass, mbd);

				// 遍历rawCandidates,元素名为candidate
				for (Method candidate : rawCandidates) {

					// 如果candidate的修饰符与isStatic一致且candidate有资格作为mdb的工厂方法
					if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {

						// 将candidate添加到candidateList中
						candidates.add(candidate);
					}
				}
			}

			// 候选方法只有一个且没有构造函数时，就直接使用该候选方法生成与beanName对应的Bean对象封装到bw中返回出去
			// 如果candidateList只有一个元素且没有传入构造函数值且mbd也没有构造函数参数值
			if (candidates.size() == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {

				// 获取candidateList中唯一的方法
				Method uniqueCandidate = candidates.get(0);

				// 如果uniqueCandidate是不需要参数
				if (uniqueCandidate.getParameterCount() == 0) {

					// 让mbd缓存uniqueCandidate[{@link RootBeanDefinition#factoryMethodToIntrospect}]
					mbd.factoryMethodToIntrospect = uniqueCandidate;
					synchronized (mbd.constructorArgumentLock) {

						// 让mbd缓存已解析的构造函数或工厂方法[{@link RootBeanDefinition#resolvedConstructorOrFactoryMethod}]
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;

						// 让mbd标记构造函数参数已解析[{@link RootBeanDefinition#constructorArgumentsResolved}]
						mbd.constructorArgumentsResolved = true;

						// 让mbd缓存完全解析的构造函数参数[{@link RootBeanDefinition#resolvedConstructorArguments}]
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}

					// 使用factoryBean生成与beanName对应的Bean对象,并将该Bean对象保存到bw中
					bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, uniqueCandidate, EMPTY_ARGS));

					return bw;
				}
			}

			// 如果有多个工厂方法的话进行排序操作
			if (candidates.size() > 1) {  // explicitly skip immutable singletonList 显式跳过不可变单例列表

				candidates.sort(AutowireUtils.EXECUTABLE_COMPARATOR);
			}

			// ConstructorArgumentValues：构造函数参数值的Holder,通常作为BeanDefinition的一部分,支持构造函数参数列表中特定索引的值
			// 以及按类型的通用参数匹配
			// 定义一个用于存放解析后的构造函数参数值的ConstructorArgumentValues对象
			ConstructorArgumentValues resolvedValues = null;

			// 定义一个mbd是否支持使用构造函数进行自动注入的标记
			boolean autowiring = (mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);

			// 定义一个最小类型差异权重，默认是Integer最大值
			int minTypeDiffWeight = Integer.MAX_VALUE;

			// 定义一个存储摸棱两可的工厂方法的Set集合,以用于抛出BeanCreationException时描述异常信息
			Set<Method> ambiguousFactoryMethods = null;

			// 定义一个最少参数数，默认为0
			int minNrOfArgs;

			// 如果传入了构造方法参数值，就是用传入的。如果没有就从definition中获取
			// 如果definition中也没有，那就真的没有
			if (explicitArgs != null) {

				// minNrOfArgs引用explicitArgs的数组长度
				minNrOfArgs = explicitArgs.length;
			}
			else {
				// We don't have arguments passed in programmatically, so we need to resolve the
				// arguments specified in the constructor arguments held in the bean definition.
				// 我们没有以编程方式传入的参数，因此我们需要解析 bean 定义中保存的构造函数参数中指定的参数。
				// 如果mbd中有构造方法参数值
				if (mbd.hasConstructorArgumentValues()) {

					// 获取definition中的构造方法参数值
					ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
					resolvedValues = new ConstructorArgumentValues();

					// 将cargs解析后值保存到resolveValues中，并让minNrOfArgs引用解析后的最小(索引参数值数+泛型参数值数)
					minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
				}
				else {

					// 意味着mbd没有构造函数参数值时，将minNrOfArgs设为0
					minNrOfArgs = 0;
				}
			}

			Deque<UnsatisfiedDependencyException> causes = null;

			for (Method candidate : candidates) {

				// 获取参数的个数
				int parameterCount = candidate.getParameterCount();

				// 当前候选方法的参数个数大于等于最小个数
				if (parameterCount >= minNrOfArgs) {

					// ArgumentsHolder:用于保存参数数组
					// 定义一个封装参数数组的ArgumentsHolder对象
					ArgumentsHolder argsHolder;

					Class<?>[] paramTypes = candidate.getParameterTypes();
					if (explicitArgs != null) {
						// Explicit arguments given -> arguments length must match exactly.
						// 给定的显式参数 -> 参数长度必须完全匹配。
						// 如果当前方法的形参列表和给定的参数列表长度不一致，跳过当前方法的后续处理
						if (paramTypes.length != explicitArgs.length) {
							continue;
						}

						// 实例化argsHolder，封装explicitArgs到argsHolder
						argsHolder = new ArgumentsHolder(explicitArgs);
					}
					else {
						// Resolved constructor arguments: type conversion and/or autowiring necessary.
						// 解析构造函数参数：需要类型转换和/或自动装配。
						try {

							// 定义用于保存参数名的数组
							String[] paramNames = null;

							// 获取beanFactory的参数名发现器
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {

								// 通过pnd解析candidate的参数名
								paramNames = pnd.getParameterNames(candidate);
							}

							// 将resolvedValues转换成一个封装着参数数组ArgumentsHolder实例，当candidate只有一个时，支持可在抛
							// 出没有此类BeanDefinition的异常返回null，而不抛出异常
							argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw,
									paramTypes, paramNames, candidate, autowiring, candidates.size() == 1);
						}
						catch (UnsatisfiedDependencyException ex) {
							if (logger.isTraceEnabled()) {
								logger.trace("Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
							}
							// Swallow and try next overloaded factory method.
							// 吞下并尝试下一个重载的工厂方法。
							if (causes == null) {
								causes = new ArrayDeque<>(1);
							}
							causes.add(ex);
							continue;
						}
					}

					// mbd支持的构造函数解析模式,默认使用宽松模式:
					// 1. 严格模式如果摸棱两可的构造函数在转换参数时都匹配，则抛出异常
					// 2. 宽松模式将使用"最接近类型匹配"的构造函数
					// 如果bd支持的构造函数解析模式是宽松模式,引用获取类型差异权重值，否则引用获取Assignability权重值
					int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
							argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));


					// Choose this factory method if it represents the closest match.
					// 如果它代表最接近的匹配，请选择此工厂方法。
					// 如果typeDiffWeight小于minTypeDiffWeight
					if (typeDiffWeight < minTypeDiffWeight) {
						factoryMethodToUse = candidate;
						argsHolderToUse = argsHolder;
						argsToUse = argsHolder.arguments;
						minTypeDiffWeight = typeDiffWeight;
						ambiguousFactoryMethods = null;
					}
					// Find out about ambiguity: In case of the same type difference weight
					// for methods with the same number of parameters, collect such candidates
					// and eventually raise an ambiguity exception.
					// However, only perform that check in non-lenient constructor resolution mode,
					// and explicitly ignore overridden methods (with the same parameter signature).
					// 找出歧义：如果具有相同数量参数的方法具有相同类型的差异权重，则收集此类候选并最终引发歧义异常。
					// 但是，仅在非宽松构造函数解析模式下执行该检查，并显式忽略覆盖的方法（具有相同的参数签名）。

					// 如果factoryMethodToUse不为null且typeDiffWeight与minTypeDiffWeight相等
					// 且mbd指定了严格模式解析构造函数且paramTypes的数组长度与factoryMethodToUse的参数数组长度相等且
					// paramTypes的数组元素与factoryMethodToUse的参数数组元素不相等
					else if (factoryMethodToUse != null && typeDiffWeight == minTypeDiffWeight &&
							!mbd.isLenientConstructorResolution() &&
							paramTypes.length == factoryMethodToUse.getParameterCount() &&
							!Arrays.equals(paramTypes, factoryMethodToUse.getParameterTypes())) {
						if (ambiguousFactoryMethods == null) {
							ambiguousFactoryMethods = new LinkedHashSet<>();
							ambiguousFactoryMethods.add(factoryMethodToUse);
						}
						ambiguousFactoryMethods.add(candidate);
					}
				}
			}

			// 整合无法筛选出候选方法或者无法解析出要使用的参数值的情况，抛出BeanCreationException并加以描述
			// 如果factoryMethodToUse为null或者argsToUse为null
			if (factoryMethodToUse == null || argsToUse == null) {
				if (causes != null) {

					// 从cause中移除最新的UnsatisfiedDependencyException
					UnsatisfiedDependencyException ex = causes.removeLast();

					for (Exception cause : causes) {

						// 将cause添加到该Bean工厂的抑制异常列表【{@link DefaultSingletonBeanRegistry#suppressedExceptions】中
						this.beanFactory.onSuppressedException(cause);
					}
					throw ex;
				}

				// 定义一个用于存放参数类型的简单类名的ArrayList对象，长度为minNrOfArgs
				List<String> argTypes = new ArrayList<>(minNrOfArgs);
				if (explicitArgs != null) {
					for (Object arg : explicitArgs) {

						// 如果arg不为null，将arg的简单类名添加到argTypes中；否则将"null"添加到argTypes中
						argTypes.add(arg != null ? arg.getClass().getSimpleName() : "null");
					}
				}
				else if (resolvedValues != null) {

					// 定义一个用于存放resolvedValues的泛型参数值和方法参数值的LinkedHashSet对象
					Set<ValueHolder> valueHolders = new LinkedHashSet<>(resolvedValues.getArgumentCount());

					// 将resolvedValues的方法参数值添加到valueHolders中
					valueHolders.addAll(resolvedValues.getIndexedArgumentValues().values());

					// 将resolvedValues的泛型参数值添加到valueHolders中
					valueHolders.addAll(resolvedValues.getGenericArgumentValues());

					for (ValueHolder value : valueHolders) {

						// 如果value的参数类型不为null，就获取该参数类型的简单类名；否则(如果value的参数值不为null，
						// 即获取该参数值的简单类名;否则为"null")
						String argType = (value.getType() != null ? ClassUtils.getShortName(value.getType()) :
								(value.getValue() != null ? value.getValue().getClass().getSimpleName() : "null"));
						argTypes.add(argType);
					}
				}

				// 将argType转换成字符串，以","隔开元素.用于描述Bean创建异常
				String argDesc = StringUtils.collectionToCommaDelimitedString(argTypes);

				// 抛出BeanCreationException:找不到匹配的工厂方法：工厂Bean 'mbd.getFactoryBeanName()';工厂方法
				// 'mbd.getFactoryMethodName()(argDesc)'.检查是否存在具体指定名称和参数的方法，并且该方法时静态/非静态的.
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"No matching factory method found on class [" + factoryClass.getName() + "]: " +
						(mbd.getFactoryBeanName() != null ?
							"factory bean '" + mbd.getFactoryBeanName() + "'; " : "") +
						"factory method '" + mbd.getFactoryMethodName() + "(" + argDesc + ")'. " +
						"Check that a method with the specified name " +
						(minNrOfArgs > 0 ? "and arguments " : "") +
						"exists and that it is " +
						(isStatic ? "static" : "non-static") + ".");
			}

			// 如果factoryMethodToUse时无返回值方法
			else if (void.class == factoryMethodToUse.getReturnType()) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid factory method '" + mbd.getFactoryMethodName() + "' on class [" +
						factoryClass.getName() + "]: needs to have a non-void return type!");
			}

			// 如果ambiguousFactoryMethods不为null
			else if (ambiguousFactoryMethods != null) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous factory method matches found on class [" + factoryClass.getName() + "] " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousFactoryMethods);
			}

			// 将筛选出来的工厂方法和解析出来的参数值缓存到mdb中
			// 如果explicitArgs为null且argsHolderToUser不为null
			if (explicitArgs == null && argsHolderToUse != null) {

				// 让mbd的唯一方法候选【{@link RootBeanDefinition#factoryMethodToIntrospect}】引用factoryMethodToUse
				mbd.factoryMethodToIntrospect = factoryMethodToUse;

				// 将argsHolderToUse所得到的参数值属性缓存到mbd对应的属性中
				argsHolderToUse.storeCache(mbd, factoryMethodToUse);
			}
		}

		// 使用factoryBean生成与beanName对应的Bean对象,并将该Bean对象保存到bw中
		bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse));
		return bw;
	}

	/**
	 * 使用factoryBean生成与beanName对应的Bean对象
	 *
	 * @param beanName
	 * @param mbd
	 * @param factoryBean
	 * @param factoryMethod
	 * @param args
	 * @return
	 */
	private Object instantiate(String beanName, RootBeanDefinition mbd,
			@Nullable Object factoryBean, Method factoryMethod, Object[] args) {

		try {

			// 在beanFactory中返回beanName的Bean实例，并通过factoryMethod创建它
			return this.beanFactory.getInstantiationStrategy().instantiate(
					mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via factory method failed", ex);
		}
	}

	/**
	 * Resolve the constructor arguments for this bean into the resolvedValues object.
	 * This may involve looking up other beans.
	 * 将此 bean 的构造函数参数解析为 resolveValues 对象。这可能涉及查找其他 bean。
	 *
	 * <p>This method is also used for handling invocations of static factory methods.
	 * 此方法也用于处理静态工厂方法的调用。
	 */
	private int resolveConstructorArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			ConstructorArgumentValues cargs, ConstructorArgumentValues resolvedValues) {

		// 获取Bean工厂的类型转换器
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();

		TypeConverter converter = (customConverter != null ? customConverter : bw);

		// BeanDefinitionValueResolver:在bean工厂实现中使用Helper类，它将beanDefinition对象中包含的值解析为应用于
		// 目标bean实例的实际值
		// 新建一个BeanDefinitionValueResolver对象
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);

		// 返回此实例中保存的参数值的数量，同时计算索引参数值和泛型参数值
		// 获取cargs的参数值数量和泛型参数值数量作为 最小(索引参数值数+泛型参数值数)
		int minNrOfArgs = cargs.getArgumentCount();

		// ConstructorArgumentValues.ValueHolder：构造函数参数值的Holder,带有可选的type属性，指示实际构造函数参数的目标类型
		// 遍历cargs所封装的索引参数值的Map，元素为entry(key=参数值的参数索引,value=ConstructorArgumentValues.ValueHolder对象)
		for (Map.Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : cargs.getIndexedArgumentValues().entrySet()) {

			// 获取参数值的参数索引
			int index = entry.getKey();

			if (index < 0) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid constructor argument index: " + index);
			}

			// 如果index大于最小参数值数量
			if (index + 1 > minNrOfArgs) {

				// minNrOfArgs就为index+1
				minNrOfArgs = index + 1;
			}

			// ConstructorArgumentValues.ValueHolder封装了参数类型以及参数值等信息
			ConstructorArgumentValues.ValueHolder valueHolder = entry.getValue();

			// 如果valueHolder已经包含转换后的值
			if (valueHolder.isConverted()) {

				// 将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中
				resolvedValues.addIndexedArgumentValue(index, valueHolder);
			}
			else {

				// 使用valueResolver解析出valueHolder实例的构造函数参数值所封装的对象
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());

				// 使用valueHolder所封装的type,name属性以及解析出来的resolvedValue构造出一个ConstructorArgumentValues.ValueHolder对象
				ConstructorArgumentValues.ValueHolder resolvedValueHolder =
						new ConstructorArgumentValues.ValueHolder(resolvedValue, valueHolder.getType(), valueHolder.getName());

				// 将valueHolder作为resolvedValueHolder的配置源对象设置到resolvedValueHolder中
				resolvedValueHolder.setSource(valueHolder);

				// 将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中
				resolvedValues.addIndexedArgumentValue(index, resolvedValueHolder);
			}
		}

		// 遍历cargs的泛型参数值的列表
		for (ConstructorArgumentValues.ValueHolder valueHolder : cargs.getGenericArgumentValues()) {

			// 如果valueHolder已经包含转换后的值
			if (valueHolder.isConverted()) {

				// 将index和valueHolder添加到resolvedValues的泛型参数值的列表中
				resolvedValues.addGenericArgumentValue(valueHolder);
			}
			else {

				// 使用valueResolver解析出valueHolder实例的构造函数参数值所封装的对象
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());

				// 使用valueHolder所封装的type,name属性以及解析出来的resolvedValue构造出一个ConstructorArgumentValues.ValueHolder对象
				ConstructorArgumentValues.ValueHolder resolvedValueHolder = new ConstructorArgumentValues.ValueHolder(
						resolvedValue, valueHolder.getType(), valueHolder.getName());

				// 将valueHolder作为resolvedValueHolder的配置源对象设置到resolvedValueHolder中
				resolvedValueHolder.setSource(valueHolder);

				// 将index和valueHolder添加到resolvedValues所封装的索引参数值的Map中
				resolvedValues.addGenericArgumentValue(resolvedValueHolder);
			}
		}


		// 返回最小(索引参数值数+泛型参数值数)
		return minNrOfArgs;
	}

	/**
	 * Create an array of arguments to invoke a constructor or factory method,
	 * given the resolved constructor argument values.
	 *
	 * 给定解析的构造函数参数值，创建一个参数数组来调用构造函数或工厂方法。
	 */
	private ArgumentsHolder createArgumentArray(
			String beanName, RootBeanDefinition mbd, @Nullable ConstructorArgumentValues resolvedValues,
			BeanWrapper bw, Class<?>[] paramTypes, @Nullable String[] paramNames, Executable executable,
			boolean autowiring, boolean fallback) throws UnsatisfiedDependencyException {

		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		TypeConverter converter = (customConverter != null ? customConverter : bw);

		ArgumentsHolder args = new ArgumentsHolder(paramTypes.length);
		Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);

		for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
			Class<?> paramType = paramTypes[paramIndex];
			String paramName = (paramNames != null ? paramNames[paramIndex] : "");
			// Try to find matching constructor argument value, either indexed or generic.
			ConstructorArgumentValues.ValueHolder valueHolder = null;
			if (resolvedValues != null) {
				valueHolder = resolvedValues.getArgumentValue(paramIndex, paramType, paramName, usedValueHolders);
				// If we couldn't find a direct match and are not supposed to autowire,
				// let's try the next generic, untyped argument value as fallback:
				// it could match after type conversion (for example, String -> int).
				if (valueHolder == null && (!autowiring || paramTypes.length == resolvedValues.getArgumentCount())) {
					valueHolder = resolvedValues.getGenericArgumentValue(null, null, usedValueHolders);
				}
			}
			if (valueHolder != null) {
				// We found a potential match - let's give it a try.
				// Do not consider the same value definition multiple times!
				usedValueHolders.add(valueHolder);
				Object originalValue = valueHolder.getValue();
				Object convertedValue;
				if (valueHolder.isConverted()) {
					convertedValue = valueHolder.getConvertedValue();
					args.preparedArguments[paramIndex] = convertedValue;
				}
				else {
					MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
					try {
						convertedValue = converter.convertIfNecessary(originalValue, paramType, methodParam);
					}
					catch (TypeMismatchException ex) {
						throw new UnsatisfiedDependencyException(
								mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
								"Could not convert argument value of type [" +
										ObjectUtils.nullSafeClassName(valueHolder.getValue()) +
										"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
					}
					Object sourceHolder = valueHolder.getSource();
					if (sourceHolder instanceof ConstructorArgumentValues.ValueHolder) {
						Object sourceValue = ((ConstructorArgumentValues.ValueHolder) sourceHolder).getValue();
						args.resolveNecessary = true;
						args.preparedArguments[paramIndex] = sourceValue;
					}
				}
				args.arguments[paramIndex] = convertedValue;
				args.rawArguments[paramIndex] = originalValue;
			}
			else {
				MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
				// No explicit match found: we're either supposed to autowire or
				// have to fail creating an argument array for the given constructor.
				if (!autowiring) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
							"Ambiguous argument values for parameter of type [" + paramType.getName() +
							"] - did you specify the correct bean references as arguments?");
				}
				try {
					Object autowiredArgument = resolveAutowiredArgument(
							methodParam, beanName, autowiredBeanNames, converter, fallback);
					args.rawArguments[paramIndex] = autowiredArgument;
					args.arguments[paramIndex] = autowiredArgument;
					args.preparedArguments[paramIndex] = autowiredArgumentMarker;
					args.resolveNecessary = true;
				}
				catch (BeansException ex) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam), ex);
				}
			}
		}

		for (String autowiredBeanName : autowiredBeanNames) {
			this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
			if (logger.isDebugEnabled()) {
				logger.debug("Autowiring by type from bean name '" + beanName +
						"' via " + (executable instanceof Constructor ? "constructor" : "factory method") +
						" to bean named '" + autowiredBeanName + "'");
			}
		}

		return args;
	}

	/**
	 * Resolve the prepared arguments stored in the given bean definition.
	 */
	private Object[] resolvePreparedArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			Executable executable, Object[] argsToResolve) {

		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);
		Class<?>[] paramTypes = executable.getParameterTypes();

		Object[] resolvedArgs = new Object[argsToResolve.length];
		for (int argIndex = 0; argIndex < argsToResolve.length; argIndex++) {
			Object argValue = argsToResolve[argIndex];
			MethodParameter methodParam = MethodParameter.forExecutable(executable, argIndex);
			if (argValue == autowiredArgumentMarker) {
				argValue = resolveAutowiredArgument(methodParam, beanName, null, converter, true);
			}
			else if (argValue instanceof BeanMetadataElement) {
				argValue = valueResolver.resolveValueIfNecessary("constructor argument", argValue);
			}
			else if (argValue instanceof String) {
				argValue = this.beanFactory.evaluateBeanDefinitionString((String) argValue, mbd);
			}
			Class<?> paramType = paramTypes[argIndex];
			try {
				resolvedArgs[argIndex] = converter.convertIfNecessary(argValue, paramType, methodParam);
			}
			catch (TypeMismatchException ex) {
				throw new UnsatisfiedDependencyException(
						mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
						"Could not convert argument value of type [" + ObjectUtils.nullSafeClassName(argValue) +
						"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
			}
		}
		return resolvedArgs;
	}

	protected Constructor<?> getUserDeclaredConstructor(Constructor<?> constructor) {
		Class<?> declaringClass = constructor.getDeclaringClass();
		Class<?> userClass = ClassUtils.getUserClass(declaringClass);
		if (userClass != declaringClass) {
			try {
				return userClass.getDeclaredConstructor(constructor.getParameterTypes());
			}
			catch (NoSuchMethodException ex) {
				// No equivalent constructor on user class (superclass)...
				// Let's proceed with the given constructor as we usually would.
			}
		}
		return constructor;
	}

	/**
	 * Template method for resolving the specified argument which is supposed to be autowired.
	 */
	@Nullable
	protected Object resolveAutowiredArgument(MethodParameter param, String beanName,
			@Nullable Set<String> autowiredBeanNames, TypeConverter typeConverter, boolean fallback) {

		Class<?> paramType = param.getParameterType();
		if (InjectionPoint.class.isAssignableFrom(paramType)) {
			InjectionPoint injectionPoint = currentInjectionPoint.get();
			if (injectionPoint == null) {
				throw new IllegalStateException("No current InjectionPoint available for " + param);
			}
			return injectionPoint;
		}
		try {
			return this.beanFactory.resolveDependency(
					new DependencyDescriptor(param, true), beanName, autowiredBeanNames, typeConverter);
		}
		catch (NoUniqueBeanDefinitionException ex) {
			throw ex;
		}
		catch (NoSuchBeanDefinitionException ex) {
			if (fallback) {
				// Single constructor or factory method -> let's return an empty array/collection
				// for e.g. a vararg or a non-null List/Set/Map parameter.
				if (paramType.isArray()) {
					return Array.newInstance(paramType.getComponentType(), 0);
				}
				else if (CollectionFactory.isApproximableCollectionType(paramType)) {
					return CollectionFactory.createCollection(paramType, 0);
				}
				else if (CollectionFactory.isApproximableMapType(paramType)) {
					return CollectionFactory.createMap(paramType, 0);
				}
			}
			throw ex;
		}
	}

	/**
	 * 设置新得当前切入点对象，返回旧的当前切入点对象
	 *
	 * @param injectionPoint
	 * @return
	 */
	static InjectionPoint setCurrentInjectionPoint(@Nullable InjectionPoint injectionPoint) {

		// 从线程本地的当前切入点对象中获取旧的InjectionPoint对象
		InjectionPoint old = currentInjectionPoint.get();

		// 如果injectionPoint不为null
		if (injectionPoint != null) {

			// 将injectionPoint设置成当前切入点对象
			currentInjectionPoint.set(injectionPoint);
		}
		else {

			// 移除当前切入点对象
			currentInjectionPoint.remove();
		}
		return old;
	}


	/**
	 * Private inner class for holding argument combinations.
	 *
	 * 用于保存参数组合的私有内部类。
	 */
	private static class ArgumentsHolder {

		public final Object[] rawArguments;

		public final Object[] arguments;

		public final Object[] preparedArguments;

		public boolean resolveNecessary = false;

		public ArgumentsHolder(int size) {
			this.rawArguments = new Object[size];
			this.arguments = new Object[size];
			this.preparedArguments = new Object[size];
		}

		public ArgumentsHolder(Object[] args) {
			this.rawArguments = args;
			this.arguments = args;
			this.preparedArguments = args;
		}

		public int getTypeDifferenceWeight(Class<?>[] paramTypes) {
			// If valid arguments found, determine type difference weight.
			// Try type difference weight on both the converted arguments and
			// the raw arguments. If the raw weight is better, use it.
			// Decrease raw weight by 1024 to prefer it over equal converted weight.
			// 如果找到有效参数，则确定类型差异权重。尝试对转换后的参数和原始参数进行类型差异权重。
			// 如果原始重量更好，请使用它。将原始重量减少 1024 以使其优于相等的转换重量。
			// MethodInvoker.getTypeDifferenceWeight-确定表示类型和参数之间的类层次结构差异的权重：
			// 1. arguments的类型不是paramTypes类型的子类，直接返回 Integer.MAX_VALUE,最大重量，也就是直接不匹配
			// 2. paramTypes类型是arguments类型的父类则+2
			// 3. paramTypes类型是arguments类型的接口，则+1
			// 4. arguments的类型直接就是paramTypes类型,则+0
			// 获取表示paramTypes和arguments之间的类层次结构差异的权重
			int typeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.arguments);

			// 获取表示paramTypes和rawArguments之间的类层次结构差异的权重
			int rawTypeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.rawArguments) - 1024;

			// 取最小权重，但是还是以原始类型优先，因为差异值还-1024
			return Math.min(rawTypeDiffWeight, typeDiffWeight);
		}

		/**
		 * 获取Assignability权重，严格模式下使用
		 *
		 * @param paramTypes
		 * @return
		 */
		public int getAssignabilityWeight(Class<?>[] paramTypes) {
			for (int i = 0; i < paramTypes.length; i++) {

				// 如果确定arguments不是paramTypes的实例
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.arguments[i])) {

					// 返回Integer最大值，意味着既然连最终的转换后参数值都不能匹配，这个情况下的paramTypes所对应的工厂方法是不可以接受的
					return Integer.MAX_VALUE;
				}
			}
			for (int i = 0; i < paramTypes.length; i++) {

				// 如果确定rawArguments不是paramTypes的实例
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.rawArguments[i])) {

					// 返回Integer最大值-512，意味着虽然转换后的参数值匹配，但是原始的参数值不匹配，
					// 这个情况下的paramTypes所对应的工厂方法还是可以接受的
					return Integer.MAX_VALUE - 512;
				}
			}

			// 在完全匹配的情况下，返回Integer最大值-1024；
			// 意味着因为最终的转换后参数值和原始参数值都匹配，这种情况下paramTypes所对应的工厂方法非常可以接收
			return Integer.MAX_VALUE - 1024;
		}

		/**
		 * 将ArgumentsHolder所得到的参数值属性缓存到mbd对应的属性中
		 *
		 * @param mbd
		 * @param constructorOrFactoryMethod
		 */
		public void storeCache(RootBeanDefinition mbd, Executable constructorOrFactoryMethod) {
			synchronized (mbd.constructorArgumentLock) {

				// 让mbd的已解析的构造函数或工厂方法【{@link RootBeanDefinition#resolvedConstructorOrFactoryMethod}】引用constructorOrFactoryMethod
				mbd.resolvedConstructorOrFactoryMethod = constructorOrFactoryMethod;

				// 将mdb的构造函数参数已解析标记【{@link RootBeanDefinition#constructorArgumentsResolved}】设置为true
				mbd.constructorArgumentsResolved = true;

				// 如果resolveNecessary为true，表示参数还需要进一步解析
				if (this.resolveNecessary) {

					// 让mbd的缓存部分准备好的构造函数参数值属性【{@link RootBeanDefinition#preparedConstructorArguments}】引用preparedArguments
					mbd.preparedConstructorArguments = this.preparedArguments;
				}
				else {

					// 让mbd的缓存完全解析的构造函数参数属性【{@link RootBeanDefinition#resolvedConstructorArguments}】引用arguments
					mbd.resolvedConstructorArguments = this.arguments;
				}
			}
		}
	}


	/**
	 * Delegate for checking Java 6's {@link ConstructorProperties} annotation.
	 */
	private static class ConstructorPropertiesChecker {

		@Nullable
		public static String[] evaluate(Constructor<?> candidate, int paramCount) {
			ConstructorProperties cp = candidate.getAnnotation(ConstructorProperties.class);
			if (cp != null) {
				String[] names = cp.value();
				if (names.length != paramCount) {
					throw new IllegalStateException("Constructor annotated with @ConstructorProperties but not " +
							"corresponding to actual number of parameters (" + paramCount + "): " + candidate);
				}
				return names;
			}
			else {
				return null;
			}
		}
	}

}
