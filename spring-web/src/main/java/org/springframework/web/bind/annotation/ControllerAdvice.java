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
import org.springframework.stereotype.Component;

/**
 * Specialization of {@link Component @Component} for classes that declare
 * {@link ExceptionHandler @ExceptionHandler}, {@link InitBinder @InitBinder}, or
 * {@link ModelAttribute @ModelAttribute} methods to be shared across
 * multiple {@code @Controller} classes.
 * 对于声明{@link ExceptionHandler @ExceptionHandler}、{@link InitBinder @InitBinder}或{@link ModelAttribute @ModelAttribute}
 * 方法的类{@link Component @Component}专门化，以便在多个{@code @Controller}类之间共享。
 *
 *
 * <p>Classes annotated with {@code @ControllerAdvice} can be declared explicitly
 * as Spring beans or auto-detected via classpath scanning. All such beans are
 * sorted based on {@link org.springframework.core.Ordered Ordered} semantics or
 * {@link org.springframework.core.annotation.Order @Order} /
 * {@link jakarta.annotation.Priority @Priority} declarations, with {@code Ordered}
 * semantics taking precedence over {@code @Order} / {@code @Priority} declarations.
 * {@code @ControllerAdvice} beans are then applied in that order at runtime.
 * 用{@code @ControllerAdvice}注释的类可以显式地声明为Spring bean，也可以通过类路径扫描自动检测。
 * 所有这样的bean都是基于{@link org.springframework.core.annotation.Order @Order} {@link jakarta.annotation.Priority @Priority}
 * 声明进行排序的，其中{@code Ordered}语义优先于{@code @Order} {@code @Priority}声明。
 * 然后在运行时按此顺序应用{@code @ControllerAdvice} bean。
 *
 * Note, however, that {@code @ControllerAdvice} beans that implement
 * {@link org.springframework.core.PriorityOrdered PriorityOrdered} are <em>not</em>
 * given priority over {@code @ControllerAdvice} beans that implement {@code Ordered}.
 * In addition, {@code Ordered} is not honored for scoped {@code @ControllerAdvice}
 * beans &mdash; for example if such a bean has been configured as a request-scoped
 * or session-scoped bean.  For handling exceptions, an {@code @ExceptionHandler}
 * will be picked on the first advice with a matching exception handler method. For
 * model attributes and data binding initialization, {@code @ModelAttribute} and
 * {@code @InitBinder} methods will follow {@code @ControllerAdvice} order.
 * 但是请注意，实现{@code @ControllerAdvice}的{@code @ControllerAdvice} bean的优先级比实现{@code Ordered}
 * 的{@code @ControllerAdvice} bean的优先级是<em>而不是<em>。
 * 此外，{@code Ordered}对于作用域{@code @ControllerAdvice} bean和mdash不受尊重;
 * 例如，如果这样一个bean被配置为请求范围的bean或会话范围的bean。
 * 为了处理异常，将在第一个通知中使用匹配的异常处理方法选择{@code @ExceptionHandler}。
 * 对于模型属性和数据绑定初始化，{@code @ModelAttribute}和{@code @InitBinder}方法将遵循{@code @ControllerAdvice}的顺序。
 *
 *
 * <p>Note: For {@code @ExceptionHandler} methods, a root exception match will be
 * preferred to just matching a cause of the current exception, among the handler
 * methods of a particular advice bean. However, a cause match on a higher-priority
 * advice will still be preferred over any match (whether root or cause level)
 * on a lower-priority advice bean. As a consequence, please declare your primary
 * root exception mappings on a prioritized advice bean with a corresponding order.
 * 注意:对于{@code @ExceptionHandler}方法，在特定通知bean的处理程序方法中，根异常匹配将优先于匹配当前异常的原因。
 * 但是，高优先级通知上的原因匹配仍然优先于低优先级通知bean上的任何匹配(无论是根级别还是原因级别)。
 * 因此，请以相应的顺序在优先级建议bean上声明您的主根异常映射。
 *
 *
 * <p>By default, the methods in an {@code @ControllerAdvice} apply globally to
 * all controllers. Use selectors such as {@link #annotations},
 * {@link #basePackageClasses}, and {@link #basePackages} (or its alias
 * {@link #value}) to define a more narrow subset of targeted controllers.
 * 默认情况下，{@code @ControllerAdvice}中的方法全局应用于所有控制器。
 * 使用诸如{@link #annotation}、{@link #basePackageClasses}和{@link #basePackages}(或其别名{@link #value})这样的选择器来定义目标控制器的更窄子集。
 *
 * If multiple selectors are declared, boolean {@code OR} logic is applied, meaning
 * selected controllers should match at least one selector. Note that selector checks
 * are performed at runtime, so adding many selectors may negatively impact
 * performance and add complexity.
 * 如果声明了多个选择器，则应用布尔{@code OR}逻辑，这意味着所选控制器应该至少匹配一个选择器。
 * 请注意，选择器检查是在运行时执行的，因此添加许多选择器可能会对性能产生负面影响并增加复杂性。
 *
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Sam Brannen
 * @since 3.2
 * @see org.springframework.stereotype.Controller
 * @see RestControllerAdvice
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ControllerAdvice {

	/**
	 * Alias for the {@link #basePackages} attribute.
	 * {@link basePackages}属性的别名。
	 *
	 * <p>Allows for more concise annotation declarations &mdash; for example,
	 * {@code @ControllerAdvice("org.my.pkg")} is equivalent to
	 * {@code @ControllerAdvice(basePackages = "org.my.pkg")}.
	 * 允许更简洁的注释声明&mdash;例如，{@code @ControllerAdvice("org.my.pkg")}等价于{@code @ControllerAdvice(basePackages = "org.my.pkg")}。
	 *
	 * @since 4.0
	 * @see #basePackages
	 */
	@AliasFor("basePackages")
	String[] value() default {};

	/**
	 * Array of base packages.
	 * <p>Controllers that belong to those base packages or sub-packages thereof
	 * will be included &mdash; for example,
	 * {@code @ControllerAdvice(basePackages = "org.my.pkg")} or
	 * {@code @ControllerAdvice(basePackages = {"org.my.pkg", "org.my.other.pkg"})}.
	 * <p>{@link #value} is an alias for this attribute, simply allowing for
	 * more concise use of the annotation.
	 * <p>Also consider using {@link #basePackageClasses} as a type-safe
	 * alternative to String-based package names.
	 * @since 4.0
	 */
	@AliasFor("value")
	String[] basePackages() default {};

	/**
	 * Type-safe alternative to {@link #basePackages} for specifying the packages
	 * in which to select controllers to be advised by the {@code @ControllerAdvice}
	 * annotated class.
	 * <p>Consider creating a special no-op marker class or interface in each package
	 * that serves no purpose other than being referenced by this attribute.
	 * @since 4.0
	 */
	Class<?>[] basePackageClasses() default {};

	/**
	 * Array of classes.
	 * <p>Controllers that are assignable to at least one of the given types
	 * will be advised by the {@code @ControllerAdvice} annotated class.
	 * @since 4.0
	 */
	Class<?>[] assignableTypes() default {};

	/**
	 * Array of annotation types.
	 * <p>Controllers that are annotated with at least one of the supplied annotation
	 * types will be advised by the {@code @ControllerAdvice} annotated class.
	 * <p>Consider creating a custom composed annotation or use a predefined one,
	 * like {@link RestController @RestController}.
	 * @since 4.0
	 */
	Class<? extends Annotation>[] annotations() default {};

}
