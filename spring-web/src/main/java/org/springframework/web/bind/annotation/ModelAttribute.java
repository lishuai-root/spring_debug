/*
 * Copyright 2002-2016 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.ui.Model;

/**
 * @see {@link BaseAdvice}
 *
 * Annotation that binds a method parameter or method return value
 * to a named model attribute, exposed to a web view. Supported
 * for controller classes with {@link RequestMapping @RequestMapping}
 * methods.
 * 将方法参数或方法返回值绑定到已命名模型属性的注释，暴露在web视图中。支持带有{@link RequestMapping @RequestMapping}方法的控制器类。
 *
 *
 * <p>Can be used to expose command objects to a web view, using
 * specific attribute names, through annotating corresponding
 * parameters of an {@link RequestMapping @RequestMapping} method.
 * 可以使用特定的属性名，通过注释{@link RequestMapping @RequestMapping}方法的相应参数，将命令对象暴露给web视图。
 *
 *
 * <p>Can also be used to expose reference data to a web view
 * through annotating accessor methods in a controller class with
 * {@link RequestMapping @RequestMapping} methods. Such accessor
 * methods are allowed to have any arguments that
 * {@link RequestMapping @RequestMapping} methods support, returning
 * the model attribute value to expose.
 * 也可以使用{@link RequestMapping @RequestMapping}方法在控制器类中标注访问器方法，将引用数据公开给web视图。
 * 这样的访问器方法允许有任何{@link RequestMapping @RequestMapping}方法支持的参数，返回要公开的模型属性值。
 *
 *
 * <p>Note however that reference data and all other model content is
 * not available to web views when request processing results in an
 * {@code Exception} since the exception could be raised at any time
 * making the content of the model unreliable. For this reason
 * {@link ExceptionHandler @ExceptionHandler} methods do not provide
 * access to a {@link Model} argument.
 * 但是请注意，当请求处理导致{@code Exception}时，引用数据和所有其他模型内容对web视图是不可用的，因为异常可能在任何时候引发，使得模型的内容不可靠。
 * 由于这个原因，{@link ExceptionHandler @ExceptionHandler}方法不提供对{@link Model}参数的访问。
 *
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 2.5
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ModelAttribute {

	/**
	 * Alias for {@link #name}.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * The name of the model attribute to bind to.
	 * 要绑定到的模型属性的名称。
	 *
	 * <p>The default model attribute name is inferred from the declared
	 * attribute type (i.e. the method parameter type or method return type),
	 * based on the non-qualified class name:
	 * 默认的模型属性名是从声明的属性类型(即方法参数类型或方法返回类型)中推断出来的，基于非限定的类名:
	 *
	 * e.g. "orderAddress" for class "mypackage.OrderAddress",
	 * or "orderAddressList" for "List&lt;mypackage.OrderAddress&gt;".
	 * @since 4.3
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * Allows declaring data binding disabled directly on an {@code @ModelAttribute}
	 * method parameter or on the attribute returned from an {@code @ModelAttribute}
	 * method, both of which would prevent data binding for that attribute.
	 * 允许在{@code @ModelAttribute}方法参数或从{@code @ModelAttribute}方法返回的属性上直接声明禁用数据绑定，
	 * 这两者都将阻止该属性的数据绑定。
	 *
	 * <p>By default this is set to {@code true} in which case data binding applies.
	 * Set this to {@code false} to disable data binding.
	 * 默认情况下，它被设置为{@code true}，在这种情况下应用数据绑定。将此设置为{@code false}以禁用数据绑定。
	 * @since 4.3
	 */
	boolean binding() default true;

}
