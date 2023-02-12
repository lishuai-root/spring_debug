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

package org.springframework.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import kotlin.Unit;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.jvm.ReflectJvmMapping;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

/**
 * Helper class that encapsulates the specification of a method parameter, i.e. a {@link Method}
 * or {@link Constructor} plus a parameter index and a nested type index for a declared generic
 * type. Useful as a specification object to pass along.
 *
 * <p>As of 4.2, there is a {@link org.springframework.core.annotation.SynthesizingMethodParameter}
 * subclass available which synthesizes annotations with attribute aliases. That subclass is used
 * for web and message endpoint processing, in particular.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Andy Clement
 * @author Sam Brannen
 * @author Sebastien Deleuze
 * @author Phillip Webb
 * @since 2.0
 * @see org.springframework.core.annotation.SynthesizingMethodParameter
 */
public class MethodParameter {

	/**
	 * 空注解数组
	 */
	private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];


	/**
	 * 当前Method或构造函数对象
	 * <p>Executable类是Method和Constructor的父类，一个抽象类，直接继承AccessibleObject类，并实现Member与GenericDeclaration接口，
	 *  主要作用是实现类型变量的相关操作以及展示方法的信息。</p>
	 *
	 */
	private final Executable executable;

	/**
	 * 通过检查后的参数索引
	 */
	private final int parameterIndex;

	/**
	 * 当前方法/构造函数 {@link #parameterIndex} 位置的参数
	 */
	@Nullable
	private volatile Parameter parameter;

	/**
	 * 目标类型的嵌套等级（通常为1；比如，在列表的列表的情况下，则1表示嵌套列表，而2表示嵌套列表的元素）
	 */
	private int nestingLevel;

	/** Map from Integer level to Integer type index.
	 *
	 * 从整数嵌套等级到整数类型索引的映射
	 * */
	@Nullable
	Map<Integer, Integer> typeIndexesPerLevel;

	/** The containing class. Could also be supplied by overriding {@link #getContainingClass()}
	 *
	 * 包含类的。也可以通过覆盖 {@link getContainingClass()} 来提供
	 * */
	@Nullable
	private volatile Class<?> containingClass;

	/**
	 * 当前参数类型
	 */
	@Nullable
	private volatile Class<?> parameterType;

	/**
	 * 当前泛型参数类型
	 */
	@Nullable
	private volatile Type genericParameterType;

	/**
	 * 当前参数的注解
	 */
	@Nullable
	private volatile Annotation[] parameterAnnotations;

	/**
	 * 用于发现方法和构造函数的参数名的发现器
	 */
	@Nullable
	private volatile ParameterNameDiscoverer parameterNameDiscoverer;

	/**
	 * 当前参数名
	 */
	@Nullable
	private volatile String parameterName;

	/**
	 * 内嵌的方法参数
	 */
	@Nullable
	private volatile MethodParameter nestedMethodParameter;


	/**
	 * Create a new {@code MethodParameter} for the given method, with nesting level 1.
	 * 为给定的方法创建一个新的 {@code MethodParameter}，嵌套级别为 1。
	 *
	 * @param method the Method to specify a parameter for
	 * @param parameterIndex the index of the parameter: -1 for the method
	 * return type; 0 for the first method parameter; 1 for the second method
	 * parameter, etc.
	 *           参数的索引位置：-1表示方法的返回类型；0表示第一个方法参数,1表示第二个方法参数，以此类推
	 */
	public MethodParameter(Method method, int parameterIndex) {
		this(method, parameterIndex, 1);
	}

	/**
	 * Create a new {@code MethodParameter} for the given method.
	 * @param method the Method to specify a parameter for
	 * @param parameterIndex the index of the parameter: -1 for the method
	 * return type; 0 for the first method parameter; 1 for the second method
	 * parameter, etc.
	 * @param nestingLevel the nesting level of the target type
	 * (typically 1; e.g. in case of a List of Lists, 1 would indicate the
	 * nested List, whereas 2 would indicate the element of the nested List)
	 */
	public MethodParameter(Method method, int parameterIndex, int nestingLevel) {
		Assert.notNull(method, "Method must not be null");
		this.executable = method;
		this.parameterIndex = validateIndex(method, parameterIndex);
		this.nestingLevel = nestingLevel;
	}

	/**
	 * Create a new MethodParameter for the given constructor, with nesting level 1.
	 * @param constructor the Constructor to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 */
	public MethodParameter(Constructor<?> constructor, int parameterIndex) {
		this(constructor, parameterIndex, 1);
	}

	/**
	 * Create a new MethodParameter for the given constructor.
	 * @param constructor the Constructor to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 * @param nestingLevel the nesting level of the target type
	 * (typically 1; e.g. in case of a List of Lists, 1 would indicate the
	 * nested List, whereas 2 would indicate the element of the nested List)
	 */
	public MethodParameter(Constructor<?> constructor, int parameterIndex, int nestingLevel) {
		Assert.notNull(constructor, "Constructor must not be null");
		this.executable = constructor;
		this.parameterIndex = validateIndex(constructor, parameterIndex);
		this.nestingLevel = nestingLevel;
	}

	/**
	 * Internal constructor used to create a {@link MethodParameter} with a
	 * containing class already set.
	 * @param executable the Executable to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 * @param containingClass the containing class
	 * @since 5.2
	 */
	MethodParameter(Executable executable, int parameterIndex, @Nullable Class<?> containingClass) {
		Assert.notNull(executable, "Executable must not be null");
		this.executable = executable;
		this.parameterIndex = validateIndex(executable, parameterIndex);
		this.nestingLevel = 1;
		this.containingClass = containingClass;
	}

	/**
	 * Copy constructor, resulting in an independent MethodParameter object
	 * based on the same metadata and cache state that the original object was in.
	 * 复制构造函数，导致基于原始对象所在的相同元数据和缓存状态的独立MethodParameter对象。
	 *
	 * @param original the original MethodParameter object to copy from
	 */
	public MethodParameter(MethodParameter original) {
		Assert.notNull(original, "Original must not be null");
		this.executable = original.executable;
		this.parameterIndex = original.parameterIndex;
		this.parameter = original.parameter;
		this.nestingLevel = original.nestingLevel;
		this.typeIndexesPerLevel = original.typeIndexesPerLevel;
		this.containingClass = original.containingClass;
		this.parameterType = original.parameterType;
		this.genericParameterType = original.genericParameterType;
		this.parameterAnnotations = original.parameterAnnotations;
		this.parameterNameDiscoverer = original.parameterNameDiscoverer;
		this.parameterName = original.parameterName;
	}


	/**
	 * 返回包装的方法，如果有
	 *
	 * Return the wrapped Method, if any.
	 * <p>Note: Either Method or Constructor is available.
	 * @return the Method, or {@code null} if none
	 */
	@Nullable
	public Method getMethod() {
		return (this.executable instanceof Method ? (Method) this.executable : null);
	}

	/**
	 * Return the wrapped Constructor, if any.
	 * <p>Note: Either Method or Constructor is available.
	 * @return the Constructor, or {@code null} if none
	 */
	@Nullable
	public Constructor<?> getConstructor() {
		return (this.executable instanceof Constructor ? (Constructor<?>) this.executable : null);
	}

	/**
	 * Return the class that declares the underlying Method or Constructor.
	 */
	public Class<?> getDeclaringClass() {
		return this.executable.getDeclaringClass();
	}

	/**
	 * Return the wrapped member.
	 * @return the Method or Constructor as Member
	 */
	public Member getMember() {
		return this.executable;
	}

	/**
	 * Return the wrapped annotated element.
	 * <p>Note: This method exposes the annotations declared on the method/constructor
	 * itself (i.e. at the method/constructor level, not at the parameter level).
	 * @return the Method or Constructor as AnnotatedElement
	 */
	public AnnotatedElement getAnnotatedElement() {
		return this.executable;
	}

	/**
	 * Return the wrapped executable.
	 * @return the Method or Constructor as Executable
	 * @since 5.0
	 */
	public Executable getExecutable() {
		return this.executable;
	}

	/**
	 * Return the {@link Parameter} descriptor for method/constructor parameter.
	 * @since 5.0
	 */
	public Parameter getParameter() {
		if (this.parameterIndex < 0) {
			throw new IllegalStateException("Cannot retrieve Parameter descriptor for method return type");
		}
		Parameter parameter = this.parameter;
		if (parameter == null) {
			parameter = getExecutable().getParameters()[this.parameterIndex];
			this.parameter = parameter;
		}
		return parameter;
	}

	/**
	 * Return the index of the method/constructor parameter.
	 * @return the parameter index (-1 in case of the return type)
	 */
	public int getParameterIndex() {
		return this.parameterIndex;
	}

	/**
	 * Increase this parameter's nesting level.
	 * @see #getNestingLevel()
	 * @deprecated since 5.2 in favor of {@link #nested(Integer)}
	 */
	@Deprecated
	public void increaseNestingLevel() {
		this.nestingLevel++;
	}

	/**
	 * Decrease this parameter's nesting level.
	 * @see #getNestingLevel()
	 * @deprecated since 5.2 in favor of retaining the original MethodParameter and
	 * using {@link #nested(Integer)} if nesting is required
	 */
	@Deprecated
	public void decreaseNestingLevel() {
		getTypeIndexesPerLevel().remove(this.nestingLevel);
		this.nestingLevel--;
	}

	/**
	 * Return the nesting level of the target type
	 * (typically 1; e.g. in case of a List of Lists, 1 would indicate the
	 * nested List, whereas 2 would indicate the element of the nested List).
	 */
	public int getNestingLevel() {
		return this.nestingLevel;
	}

	/**
	 * Return a variant of this {@code MethodParameter} with the type
	 * for the current level set to the specified value.
	 * @param typeIndex the new type index
	 * @since 5.2
	 */
	public MethodParameter withTypeIndex(int typeIndex) {
		return nested(this.nestingLevel, typeIndex);
	}

	/**
	 * Set the type index for the current nesting level.
	 * @param typeIndex the corresponding type index
	 * (or {@code null} for the default type index)
	 * @see #getNestingLevel()
	 * @deprecated since 5.2 in favor of {@link #withTypeIndex}
	 */
	@Deprecated
	public void setTypeIndexForCurrentLevel(int typeIndex) {
		getTypeIndexesPerLevel().put(this.nestingLevel, typeIndex);
	}

	/**
	 * Return the type index for the current nesting level.
	 * @return the corresponding type index, or {@code null}
	 * if none specified (indicating the default type index)
	 * @see #getNestingLevel()
	 */
	@Nullable
	public Integer getTypeIndexForCurrentLevel() {
		return getTypeIndexForLevel(this.nestingLevel);
	}

	/**
	 * Return the type index for the specified nesting level.
	 * 返回指定嵌套级别的类型索引。
	 *
	 * @param nestingLevel the nesting level to check
	 * @return the corresponding type index, or {@code null}
	 * if none specified (indicating the default type index)
	 */
	@Nullable
	public Integer getTypeIndexForLevel(int nestingLevel) {

		//从typeIndexPerLevel中获取传入的nestingLevel对应的类型索引
		return getTypeIndexesPerLevel().get(nestingLevel);
	}

	/**
	 * Obtain the (lazily constructed) type-indexes-per-level Map.
	 */
	private Map<Integer, Integer> getTypeIndexesPerLevel() {
		if (this.typeIndexesPerLevel == null) {
			this.typeIndexesPerLevel = new HashMap<>(4);
		}
		return this.typeIndexesPerLevel;
	}

	/**
	 * Return a variant of this {@code MethodParameter} which points to the
	 * same parameter but one nesting level deeper.
	 * 返回这个{@code MethodParameter}的一个变体，它指向相同的参数，但更深一层嵌套。
	 *
	 * @since 4.3
	 */
	public MethodParameter nested() {
		return nested(null);
	}

	/**
	 * Return a variant of this {@code MethodParameter} which points to the
	 * same parameter but one nesting level deeper.
	 * 返回这个{@code MethodParameter}的一个变体，它指向相同的参数，但更深一层嵌套。
	 *
	 * @param typeIndex the type index for the new nesting level
	 * @since 5.2
	 */
	public MethodParameter nested(@Nullable Integer typeIndex) {
		MethodParameter nestedParam = this.nestedMethodParameter;
		if (nestedParam != null && typeIndex == null) {
			return nestedParam;
		}
		nestedParam = nested(this.nestingLevel + 1, typeIndex);
		if (typeIndex == null) {
			this.nestedMethodParameter = nestedParam;
		}
		return nestedParam;
	}

	private MethodParameter nested(int nestingLevel, @Nullable Integer typeIndex) {
		MethodParameter copy = clone();
		copy.nestingLevel = nestingLevel;
		if (this.typeIndexesPerLevel != null) {
			copy.typeIndexesPerLevel = new HashMap<>(this.typeIndexesPerLevel);
		}
		if (typeIndex != null) {
			copy.getTypeIndexesPerLevel().put(copy.nestingLevel, typeIndex);
		}
		copy.parameterType = null;
		copy.genericParameterType = null;
		return copy;
	}

	/**
	 * Return whether this method indicates a parameter which is not required:
	 * 返回该方法是否指示一个非必需参数:
	 *
	 * either in the form of Java 8's {@link java.util.Optional}, any variant
	 * of a parameter-level {@code Nullable} annotation (such as from JSR-305
	 * or the FindBugs set of annotations), or a language-level nullable type
	 * declaration or {@code Continuation} parameter in Kotlin.
	 * 或者以Java 8的{@link java.util.Optional}、参数级{@code Nullable}注释(如来自JSR-305或FindBugs注释集)的任何变体，或Kotlin中的语言级可空类型声明或{@code Continuation}参数。
	 *
	 * @since 4.3
	 */
	public boolean isOptional() {
		return (getParameterType() == Optional.class || hasNullableAnnotation() ||
				(KotlinDetector.isKotlinReflectPresent() &&
						KotlinDetector.isKotlinType(getContainingClass()) &&
						KotlinDelegate.isOptional(this)));
	}

	/**
	 * Check whether this method parameter is annotated with any variant of a
	 * {@code Nullable} annotation, e.g. {@code jakarta.annotation.Nullable} or
	 * {@code edu.umd.cs.findbugs.annotations.Nullable}.
	 */
	private boolean hasNullableAnnotation() {
		for (Annotation ann : getParameterAnnotations()) {
			if ("Nullable".equals(ann.annotationType().getSimpleName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return a variant of this {@code MethodParameter} which points to
	 * the same parameter but one nesting level deeper in case of a
	 * {@link java.util.Optional} declaration.
	 * 返回这个{@code MethodParameter}的一个变体，它指向相同的参数，但在{@link java.util.Optional}声明。
	 *
	 * @since 4.3
	 * @see #isOptional()
	 * @see #nested()
	 */
	public MethodParameter nestedIfOptional() {
		return (getParameterType() == Optional.class ? nested() : this);
	}

	/**
	 * Return a variant of this {@code MethodParameter} which refers to the
	 * given containing class.
	 * @param containingClass a specific containing class (potentially a
	 * subclass of the declaring class, e.g. substituting a type variable)
	 * @since 5.2
	 * @see #getParameterType()
	 */
	public MethodParameter withContainingClass(@Nullable Class<?> containingClass) {
		MethodParameter result = clone();
		result.containingClass = containingClass;
		result.parameterType = null;
		return result;
	}

	/**
	 * Set a containing class to resolve the parameter type against.
	 */
	@Deprecated
	void setContainingClass(Class<?> containingClass) {
		this.containingClass = containingClass;
		this.parameterType = null;
	}

	/**
	 * Return the containing class for this method parameter.
	 * @return a specific containing class (potentially a subclass of the
	 * declaring class), or otherwise simply the declaring class itself
	 * @see #getDeclaringClass()
	 */
	public Class<?> getContainingClass() {
		Class<?> containingClass = this.containingClass;
		return (containingClass != null ? containingClass : getDeclaringClass());
	}

	/**
	 * Set a resolved (generic) parameter type.
	 */
	@Deprecated
	void setParameterType(@Nullable Class<?> parameterType) {
		this.parameterType = parameterType;
	}

	/**
	 * Return the type of the method/constructor parameter.
	 * 返回methodconstructor形参的类型。
	 *
	 * @return the parameter type (never {@code null})
	 */
	public Class<?> getParameterType() {
		Class<?> paramType = this.parameterType;
		if (paramType != null) {
			return paramType;
		}
		if (getContainingClass() != getDeclaringClass()) {
			paramType = ResolvableType.forMethodParameter(this, null, 1).resolve();
		}
		if (paramType == null) {
			paramType = computeParameterType();
		}
		this.parameterType = paramType;
		return paramType;
	}

	/**
	 * 返回Method或Constructor的当前参数索引的泛型参数类型
	 *
	 * Return the generic type of the method/constructor parameter.
	 * @return the parameter type (never {@code null})
	 * @since 3.0
	 */
	public Type getGenericParameterType() {

		// 获取当前泛型参数类型
		Type paramType = this.genericParameterType;

		if (paramType == null) {

			// 如果当前参数索引小于0
			if (this.parameterIndex < 0) {

				// 获取当前Method对象
				Method method = getMethod();

				// 如果method不为null, 则(如果存在Kotlin反射且声明当前Method对象的类为Kotlin类型,
				// 则得到返回方法的泛型返回类型,通过Kotlin反射支持暂停功能;否则得到一个Type对象，该对象表示此Method
				// 对象表示的方法的正式返回类型);否则为void类型
				paramType = (method != null ?
						(KotlinDetector.isKotlinReflectPresent() && KotlinDetector.isKotlinType(getContainingClass()) ?
						KotlinDelegate.getGenericReturnType(method) : method.getGenericReturnType()) : void.class);
			}
			else {

				// Method.getGenericParameterTypes()方法返回一个Type对象的数组，它以声明顺序表示此
				// Method对象表示的方法的形式参数类型。如果底层方法没有参数，则返回长度为0的数组
				Type[] genericParameterTypes = this.executable.getGenericParameterTypes();

				// 获取当前参数索引
				int index = this.parameterIndex;

				// 如果executable是Constructor的实例且声明executable的类是内部类
				// 且genericParameterTypes的长度等于executable的参数数量-1
				if (this.executable instanceof Constructor &&
						ClassUtils.isInnerClass(this.executable.getDeclaringClass()) &&
						genericParameterTypes.length == this.executable.getParameterCount() - 1) {

					// JDK<9中的javac bug：注解数组不包含内部类的实例参数，所以以实际参数索引降低1的的方式进行访问

					// Bug in javac: type array excludes enclosing instance parameter
					// for inner classes with at least one generic constructor parameter,
					// so access it with the actual parameter index lowered by 1
					// javac 中的错误：类型数组不包括包含至少一个泛型构造函数参数的内部类的封闭实例参数，
					// 因此访问它时将实际参数索引降低 1
					index = this.parameterIndex - 1;
				}

				// 如果index属于[0,genericParameterTypes长度)范围内，获取第index个genericParameterTypes元素对象
				// 赋值给paramType；否则计算参数类型，如果当前参数索引小于0，返回method的返回类型；否则返回executable的参数类型
				// 数组中当前参数索引的参数类型
				paramType = (index >= 0 && index < genericParameterTypes.length ?
						genericParameterTypes[index] : computeParameterType());
			}

			// 设置当前泛型参数类型,缓存起来，避免下次调用该方法时需要再次解析
			this.genericParameterType = paramType;
		}
		return paramType;
	}

	/**
	 *  计算参数类型，如果当前参数索引小于0，返回method的返回类型；否则返回executable的参数类型数组中当前参数索引的参数类型
	 *
	 * @return
	 */
	private Class<?> computeParameterType() {

		// 如果参数的索引小于0，返回方法的返回值类型
		if (this.parameterIndex < 0) {
			Method method = getMethod();
			if (method == null) {
				return void.class;
			}

			// 如果存在Kotlin反射且方法参数的包含类为Kotlin类型(上面带有Kotlin元数据)
			if (KotlinDetector.isKotlinReflectPresent() && KotlinDetector.isKotlinType(getContainingClass())) {

				// 返回method的返回类型,通过Kotlin反射支持暂停功能
				return KotlinDelegate.getReturnType(method);
			}

			// 返回method的返回类型
			return method.getReturnType();
		}

		// 返回executable的参数类型数组中当前参数索引的参数类型
		return this.executable.getParameterTypes()[this.parameterIndex];
	}

	/**
	 * Return the nested type of the method/constructor parameter.
	 *
	 * 返回 method/constructor 参数的嵌套类型。
	 *
	 * @return the parameter type (never {@code null})
	 * @since 3.1
	 * @see #getNestingLevel()
	 */
	public Class<?> getNestedParameterType() {

		// 嵌套级别大于1
		if (this.nestingLevel > 1) {

			// 返回Method或Contructor的泛型参数类型
			Type type = getGenericParameterType();

			// 从2开始遍历传进来的嵌套等级,因为1表示其本身，所以从2开始
			for (int i = 2; i <= this.nestingLevel; i++) {

				// 如果type是ParameterizedType对象
				if (type instanceof ParameterizedType) {

					// ParameterizedType.getActualTypeArguments:获取泛型中的实际类型，可能会存在多个泛型，
					// 例如Map<K,V>,所以会返回Type[]数组；
					Type[] args = ((ParameterizedType) type).getActualTypeArguments();

					// 返回i的类型索引
					Integer index = getTypeIndexForLevel(i);

					// 如果index不为null,获取第index个args元素；否则取最后一个元素
					type = args[index != null ? index : args.length - 1];
				}
				// TODO: Object.class if unresolvable Object.class 如果无法解析
			}

			// 如果type是Class实例
			if (type instanceof Class) {

				return (Class<?>) type;
			}

			// 如果type是ParameterizedType实例
			else if (type instanceof ParameterizedType) {

				// ParameterizeType.getRowType:返回最外层<>前面那个类型，即Map<K ,V>的Map。
				Type arg = ((ParameterizedType) type).getRawType();

				// 如果arg是Class的子类
				if (arg instanceof Class) {
					return (Class<?>) arg;
				}
			}

			// 所有条件不满足，直接返回Object.class，因为java的所有类需要继承Object
			return Object.class;
		}
		else {

			// 返回方法或构造函数的当前参数索引参数类型
			return getParameterType();
		}
	}

	/**
	 * Return the nested generic type of the method/constructor parameter.
	 * @return the parameter type (never {@code null})
	 * @since 4.2
	 * @see #getNestingLevel()
	 */
	public Type getNestedGenericParameterType() {
		if (this.nestingLevel > 1) {
			Type type = getGenericParameterType();
			for (int i = 2; i <= this.nestingLevel; i++) {
				if (type instanceof ParameterizedType) {
					Type[] args = ((ParameterizedType) type).getActualTypeArguments();
					Integer index = getTypeIndexForLevel(i);
					type = args[index != null ? index : args.length - 1];
				}
			}
			return type;
		}
		else {
			return getGenericParameterType();
		}
	}

	/**
	 * Return the annotations associated with the target method/constructor itself.
	 */
	public Annotation[] getMethodAnnotations() {
		return adaptAnnotationArray(getAnnotatedElement().getAnnotations());
	}

	/**
	 * Return the method/constructor annotation of the given type, if available.
	 * @param annotationType the annotation type to look for
	 * @return the annotation object, or {@code null} if not found
	 */
	@Nullable
	public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
		A annotation = getAnnotatedElement().getAnnotation(annotationType);
		return (annotation != null ? adaptAnnotation(annotation) : null);
	}

	/**
	 * Return whether the method/constructor is annotated with the given type.
	 * @param annotationType the annotation type to look for
	 * @since 4.3
	 * @see #getMethodAnnotation(Class)
	 */
	public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
		return getAnnotatedElement().isAnnotationPresent(annotationType);
	}

	/**
	 * Return the annotations associated with the specific method/constructor parameter.
	 */
	public Annotation[] getParameterAnnotations() {
		Annotation[] paramAnns = this.parameterAnnotations;
		if (paramAnns == null) {
			Annotation[][] annotationArray = this.executable.getParameterAnnotations();
			int index = this.parameterIndex;
			if (this.executable instanceof Constructor &&
					ClassUtils.isInnerClass(this.executable.getDeclaringClass()) &&
					annotationArray.length == this.executable.getParameterCount() - 1) {
				// Bug in javac in JDK <9: annotation array excludes enclosing instance parameter
				// for inner classes, so access it with the actual parameter index lowered by 1
				index = this.parameterIndex - 1;
			}
			paramAnns = (index >= 0 && index < annotationArray.length ?
					adaptAnnotationArray(annotationArray[index]) : EMPTY_ANNOTATION_ARRAY);
			this.parameterAnnotations = paramAnns;
		}
		return paramAnns;
	}

	/**
	 * Return {@code true} if the parameter has at least one annotation,
	 * {@code false} if it has none.
	 * @see #getParameterAnnotations()
	 */
	public boolean hasParameterAnnotations() {
		return (getParameterAnnotations().length != 0);
	}

	/**
	 * Return the parameter annotation of the given type, if available.
	 * 返回给定类型的参数注释(如果可用)。
	 *
	 * @param annotationType the annotation type to look for
	 * @return the annotation object, or {@code null} if not found
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public <A extends Annotation> A getParameterAnnotation(Class<A> annotationType) {
		Annotation[] anns = getParameterAnnotations();
		for (Annotation ann : anns) {
			if (annotationType.isInstance(ann)) {
				return (A) ann;
			}
		}
		return null;
	}

	/**
	 * Return whether the parameter is declared with the given annotation type.
	 * 返回参数是否使用给定的注释类型声明。
	 *
	 * @param annotationType the annotation type to look for
	 * @see #getParameterAnnotation(Class)
	 */
	public <A extends Annotation> boolean hasParameterAnnotation(Class<A> annotationType) {
		return (getParameterAnnotation(annotationType) != null);
	}

	/**
	 * Initialize parameter name discovery for this method parameter.
	 * 初始化此方法参数的参数名发现。
	 *
	 * <p>This method does not actually try to retrieve the parameter name at
	 * this point; it just allows discovery to happen when the application calls
	 * {@link #getParameterName()} (if ever).
	 * 此时，该方法实际上并不尝试检索参数名称;它只允许在应用程序调用{@link #getParameterName()}时发现(如果有的话)。
	 */
	public void initParameterNameDiscovery(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * 通过asm解析class获取参数名
	 *
	 * Return the name of the method/constructor parameter.
	 * 返回method/constructor参数的名称。
	 *
	 * @return the parameter name (may be {@code null} if no
	 * parameter name metadata is contained in the class file or no
	 * {@link #initParameterNameDiscovery ParameterNameDiscoverer}
	 * has been set to begin with)
	 */
	@Nullable
	public String getParameterName() {
		if (this.parameterIndex < 0) {
			return null;
		}

		ParameterNameDiscoverer discoverer = this.parameterNameDiscoverer;
		if (discoverer != null) {
			String[] parameterNames = null;
			/**
			 * 这里最终都会调用{@link LocalVariableTableParameterNameDiscoverer#doGetParameterNames(Executable)}获取方法参数名
			 * 通过asm解析
			 */
			if (this.executable instanceof Method) {
				/**
				 * 这里会调用{@link LocalVariableTableParameterNameDiscoverer#getParameterNames(Method)}获取方法参数名
				 * asm解析
				 */
				parameterNames = discoverer.getParameterNames((Method) this.executable);
			}
			else if (this.executable instanceof Constructor) {
				/**
				 * 这里会调用{@link LocalVariableTableParameterNameDiscoverer#getParameterNames(Constructor)}获取方法参数名
				 * asm解析
				 */
				parameterNames = discoverer.getParameterNames((Constructor<?>) this.executable);
			}
			if (parameterNames != null) {
				this.parameterName = parameterNames[this.parameterIndex];
			}
			this.parameterNameDiscoverer = null;
		}
		return this.parameterName;
	}


	/**
	 * A template method to post-process a given annotation instance before
	 * returning it to the caller.
	 * <p>The default implementation simply returns the given annotation as-is.
	 * @param annotation the annotation about to be returned
	 * @return the post-processed annotation (or simply the original one)
	 * @since 4.2
	 */
	protected <A extends Annotation> A adaptAnnotation(A annotation) {
		return annotation;
	}

	/**
	 * A template method to post-process a given annotation array before
	 * returning it to the caller.
	 * <p>The default implementation simply returns the given annotation array as-is.
	 * @param annotations the annotation array about to be returned
	 * @return the post-processed annotation array (or simply the original one)
	 * @since 4.2
	 */
	protected Annotation[] adaptAnnotationArray(Annotation[] annotations) {
		return annotations;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MethodParameter)) {
			return false;
		}
		MethodParameter otherParam = (MethodParameter) other;
		return (getContainingClass() == otherParam.getContainingClass() &&
				ObjectUtils.nullSafeEquals(this.typeIndexesPerLevel, otherParam.typeIndexesPerLevel) &&
				this.nestingLevel == otherParam.nestingLevel &&
				this.parameterIndex == otherParam.parameterIndex &&
				this.executable.equals(otherParam.executable));
	}

	@Override
	public int hashCode() {
		return (31 * this.executable.hashCode() + this.parameterIndex);
	}

	@Override
	public String toString() {
		Method method = getMethod();
		return (method != null ? "method '" + method.getName() + "'" : "constructor") +
				" parameter " + this.parameterIndex;
	}

	@Override
	public MethodParameter clone() {
		return new MethodParameter(this);
	}

	/**
	 * Create a new MethodParameter for the given method or constructor.
	 * <p>This is a convenience factory method for scenarios where a
	 * Method or Constructor reference is treated in a generic fashion.
	 * @param methodOrConstructor the Method or Constructor to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 * @return the corresponding MethodParameter instance
	 * @deprecated as of 5.0, in favor of {@link #forExecutable}
	 */
	@Deprecated
	public static MethodParameter forMethodOrConstructor(Object methodOrConstructor, int parameterIndex) {
		if (!(methodOrConstructor instanceof Executable)) {
			throw new IllegalArgumentException(
					"Given object [" + methodOrConstructor + "] is neither a Method nor a Constructor");
		}
		return forExecutable((Executable) methodOrConstructor, parameterIndex);
	}

	/**
	 * Create a new MethodParameter for the given method or constructor.
	 * <p>This is a convenience factory method for scenarios where a
	 * Method or Constructor reference is treated in a generic fashion.
	 * @param executable the Method or Constructor to specify a parameter for
	 * @param parameterIndex the index of the parameter
	 * @return the corresponding MethodParameter instance
	 * @since 5.0
	 */
	public static MethodParameter forExecutable(Executable executable, int parameterIndex) {
		if (executable instanceof Method) {
			return new MethodParameter((Method) executable, parameterIndex);
		}
		else if (executable instanceof Constructor) {
			return new MethodParameter((Constructor<?>) executable, parameterIndex);
		}
		else {
			throw new IllegalArgumentException("Not a Method/Constructor: " + executable);
		}
	}

	/**
	 * Create a new MethodParameter for the given parameter descriptor.
	 * <p>This is a convenience factory method for scenarios where a
	 * Java 8 {@link Parameter} descriptor is already available.
	 * @param parameter the parameter descriptor
	 * @return the corresponding MethodParameter instance
	 * @since 5.0
	 */
	public static MethodParameter forParameter(Parameter parameter) {
		return forExecutable(parameter.getDeclaringExecutable(), findParameterIndex(parameter));
	}

	protected static int findParameterIndex(Parameter parameter) {
		Executable executable = parameter.getDeclaringExecutable();
		Parameter[] allParams = executable.getParameters();
		// Try first with identity checks for greater performance.
		for (int i = 0; i < allParams.length; i++) {
			if (parameter == allParams[i]) {
				return i;
			}
		}
		// Potentially try again with object equality checks in order to avoid race
		// conditions while invoking java.lang.reflect.Executable.getParameters().
		for (int i = 0; i < allParams.length; i++) {
			if (parameter.equals(allParams[i])) {
				return i;
			}
		}
		throw new IllegalArgumentException("Given parameter [" + parameter +
				"] does not match any parameter in the declaring executable");
	}

	private static int validateIndex(Executable executable, int parameterIndex) {
		int count = executable.getParameterCount();
		Assert.isTrue(parameterIndex >= -1 && parameterIndex < count,
				() -> "Parameter index needs to be between -1 and " + (count - 1));
		return parameterIndex;
	}


	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		/**
		 * Check whether the specified {@link MethodParameter} represents a nullable Kotlin type,
		 * an optional parameter (with a default value in the Kotlin declaration) or a
		 * {@code Continuation} parameter used in suspending functions.
		 */
		public static boolean isOptional(MethodParameter param) {
			Method method = param.getMethod();
			int index = param.getParameterIndex();
			if (method != null && index == -1) {
				KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
				return (function != null && function.getReturnType().isMarkedNullable());
			}
			KFunction<?> function;
			Predicate<KParameter> predicate;
			if (method != null) {
				if (param.getParameterType().getName().equals("kotlin.coroutines.Continuation")) {
					return true;
				}
				function = ReflectJvmMapping.getKotlinFunction(method);
				predicate = p -> KParameter.Kind.VALUE.equals(p.getKind());
			}
			else {
				Constructor<?> ctor = param.getConstructor();
				Assert.state(ctor != null, "Neither method nor constructor found");
				function = ReflectJvmMapping.getKotlinFunction(ctor);
				predicate = p -> (KParameter.Kind.VALUE.equals(p.getKind()) ||
						KParameter.Kind.INSTANCE.equals(p.getKind()));
			}
			if (function != null) {
				int i = 0;
				for (KParameter kParameter : function.getParameters()) {
					if (predicate.test(kParameter)) {
						if (index == i++) {
							return (kParameter.getType().isMarkedNullable() || kParameter.isOptional());
						}
					}
				}
			}
			return false;
		}

		/**
		 * 返回方法的泛型返回类型,通过Kotlin反射支持暂停功能
		 *
		 * Return the generic return type of the method, with support of suspending
		 * functions via Kotlin reflection.
		 */
		private static Type getGenericReturnType(Method method) {
			try {

				// 获取与method相应的KFunction实例；如果此方法不能由Kotlin函数表示，则返回null
				KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);

				// 如果function不为null且function是挂起
				if (function != null && function.isSuspend()) {

					// 获取与给定Kotlin类型相对应的Java Type实例。请注意，根据出现的位置，一种
					// Kotlin类型可能对应于不同的JVM类型。例如,当Unit是参数的类型时,它对应于
					// JVM类Unit；当它是函数的返回类型时，它对应于void
					return ReflectJvmMapping.getJavaType(function.getReturnType());
				}
			}
			catch (UnsupportedOperationException ex) {
				// probably a synthetic class - let's use java reflection instead
				// 可能是一个合成类 - 让我们改用 Java 反射
			}

			// 返回一个Type对象，该对象表示此Method对象表示的方法的正式返回类型。
			return method.getGenericReturnType();
		}

		/**
		 * 返回method的返回类型,通过Kotlin反射支持暂停功能
		 *
		 * Return the return type of the method, with support of suspending
		 * functions via Kotlin reflection.
		 */
		private static Class<?> getReturnType(Method method) {
			try {

				// 获取与method相应的KFunction实例；如果此方法不能由Kotlin函数表示，则返回null
				KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);

				// 如果function不为null且function是挂起
				if (function != null && function.isSuspend()) {

					// 获取与给定Kotlin类型相对应的Java Type实例。请注意，根据出现的位置，一种
					// Kotlin类型可能对应于不同的JVM类型。例如,当Unit是参数的类型时,它对应于
					// JVM类Unit；当它是函数的返回类型时，它对应于void
					Type paramType = ReflectJvmMapping.getJavaType(function.getReturnType());
					if (paramType == Unit.class) {
						paramType = void.class;
					}

					// 获取paramType的ResolvableType对象，将此类型解析为Class，如果无法解析该类型，
					// 则返回method的返回类型 如果直接解析失败，则此方法考虑和WildcardTypes的bounds，
					// 但是Object.class的bounds将被忽略
					return ResolvableType.forType(paramType).resolve(method.getReturnType());
				}
			}
			catch (UnsupportedOperationException ex) {
				// probably a synthetic class - let's use java reflection instead
				// 可能是一个合成类 - 让我们改用 Java 反射
			}

			// 返回method的返回类型
			return method.getReturnType();
		}
	}

}
