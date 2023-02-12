/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.bind.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * A convenience annotation that is itself annotated with
 * {@link ControllerAdvice @ControllerAdvice}
 * and {@link ResponseBody @ResponseBody}.
 * 一个使用{@link ControllerAdvice @ControllerAdvice}和{@link ResponseBody @ResponseBody}注释的方便注释。
 *
 * <p>Types that carry this annotation are treated as controller advice where
 * {@link ExceptionHandler @ExceptionHandler} methods assume
 * {@link ResponseBody @ResponseBody} semantics by default.
 * 带有此注释的类型被视为控制器通知，其中{@link ExceptionHandler @ExceptionHandler}方法默认采用{@link ResponseBody @ResponseBody}语义。
 *
 * <p><b>NOTE:</b> {@code @RestControllerAdvice} is processed if an appropriate
 * {@code HandlerMapping}-{@code HandlerAdapter} pair is configured such as the
 * {@code RequestMappingHandlerMapping}-{@code RequestMappingHandlerAdapter} pair
 * which are the default in the MVC Java config and the MVC namespace.
 * <p><b>注意:<b> {@code @RestControllerAdvice}如果配置了适当的{@code HandlerMapping}-{@code HandlerAdapter}对，
 * 例如{@code RequestMappingHandlerMapping}-{@code RequestMappingHandlerAdapter}对，这是MVC Java配置和MVC命名空间中的默认值，则会处理。
 *
 *
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @since 4.3
 * @see RestController
 * @see ControllerAdvice
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ControllerAdvice
@ResponseBody
public @interface RestControllerAdvice {

	/**
	 * Alias for the {@link #basePackages} attribute.
	 * {@link #basePackages}属性的别名。
	 *
	 * <p>Allows for more concise annotation declarations &mdash; for example,
	 * {@code @RestControllerAdvice("org.my.pkg")} is equivalent to
	 * {@code @RestControllerAdvice(basePackages = "org.my.pkg")}.
	 * @see #basePackages
	 */
	@AliasFor(annotation = ControllerAdvice.class)
	String[] value() default {};

	/**
	 * Array of base packages.
	 * 基本包数组。
	 *
	 * <p>Controllers that belong to those base packages or sub-packages thereof
	 * will be included &mdash; for example,
	 * {@code @RestControllerAdvice(basePackages = "org.my.pkg")} or
	 * {@code @RestControllerAdvice(basePackages = {"org.my.pkg", "org.my.other.pkg"})}.
	 * <p>{@link #value} is an alias for this attribute, simply allowing for
	 * more concise use of the annotation.
	 * 属于这些基本包或其子包的控制器将被包括在内。
	 * 例如，{@code @RestControllerAdvice(basePackages = "org.my.pkg")}或{@code @RestControllerAdvice(basePackages = {"org.my.pkg", "org.my.other.pkg"})}。
	 * <p>{@link value}是该属性的别名，只是为了更简洁地使用注释。
	 *
	 *
	 * <p>Also consider using {@link #basePackageClasses} as a type-safe
	 * alternative to String-based package names.
	 * 也可以考虑使用{@link #basePackageClasses}作为基于字符串的包名的类型安全替代。
	 */
	@AliasFor(annotation = ControllerAdvice.class)
	String[] basePackages() default {};

	/**
	 * Type-safe alternative to {@link #basePackages} for specifying the packages
	 * in which to select controllers to be advised by the {@code @RestControllerAdvice}
	 * annotated class.
	 * {@link #basePackages}的类型安全替代方案，用于指定在其中选择由{@code @RestControllerAdvice}标注类建议的控制器的包。
	 *
	 * <p>Consider creating a special no-op marker class or interface in each package
	 * that serves no purpose other than being referenced by this attribute.
	 * 考虑在每个包中创建一个特殊的无操作标记类或接口，除了被该属性引用之外没有其他用途。
	 */
	@AliasFor(annotation = ControllerAdvice.class)
	Class<?>[] basePackageClasses() default {};

	/**
	 * Array of classes.
	 * <p>Controllers that are assignable to at least one of the given types
	 * will be advised by the {@code @RestControllerAdvice} annotated class.
	 *
	 * 类数组。可分配给至少一个给定类型的控制器将由{@code @RestControllerAdvice}注释类建议。
	 */
	@AliasFor(annotation = ControllerAdvice.class)
	Class<?>[] assignableTypes() default {};

	/**
	 * Array of annotations.
	 * 注释数组。
	 *
	 * <p>Controllers that are annotated with at least one of the supplied annotation
	 * types will be advised by the {@code @RestControllerAdvice} annotated class.
	 * 带有注解类{@code @RestControllerAdvice}的控制器将被注解类建议使用至少一种提供的注解类型。
	 *
	 * <p>Consider creating a custom composed annotation or use a predefined one,
	 * like {@link RestController @RestController}.
	 * 考虑创建一个自定义组合注释或使用一个预定义的注释，如{@link RestController @RestController}。
	 */
	@AliasFor(annotation = ControllerAdvice.class)
	Class<? extends Annotation>[] annotations() default {};

}
