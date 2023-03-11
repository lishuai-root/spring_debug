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

/**
 * 标注该方法参数从session中获取
 *
 * Annotation to bind a method parameter to a session attribute.
 * 注释将方法参数绑定到会话属性。
 *
 * <p>The main motivation is to provide convenient access to existing, permanent
 * session attributes (e.g. user authentication object) with an optional/required
 * check and a cast to the target method parameter type.
 * 主要动机是通过可选的检查和转换到目标方法参数类型来方便地访问现有的、永久的会话属性(例如用户身份验证对象)。
 *
 * <p>For use cases that require adding or removing session attributes consider
 * injecting {@code org.springframework.web.context.request.WebRequest} or
 * {@code jakarta.servlet.http.HttpSession} into the controller method.
 * 对于需要添加或删除会话属性的用例，可以考虑注入{@code org.springframework.web.context.request.WebRequest}
 * 或{@code jakarta.servlet.http.HttpSession}导入到控制器方法中。
 *
 *
 * <p>For temporary storage of model attributes in the session as part of the
 * workflow for a controller, consider using {@link SessionAttributes} instead.
 * 对于临时存储会话中的模型属性作为控制器工作流的一部分，可以考虑使用{@link SessionAttributes}代替。
 *
 * @author Rossen Stoyanchev
 * @since 4.3
 * @see RequestMapping
 * @see SessionAttributes
 * @see RequestAttribute
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SessionAttribute {

	/**
	 * Alias for {@link #name}.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * The name of the session attribute to bind to.
	 * 要绑定到的会话属性的名称。
	 *
	 * <p>The default name is inferred from the method parameter name.
	 * <p>默认名称是从方法参数名推断出来的。
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * Whether the session attribute is required.
	 * 是否需要会话属性。
	 *
	 * <p>Defaults to {@code true}, leading to an exception being thrown
	 * if the attribute is missing in the session or there is no session.
	 * <p>默认为{@code true}，如果会话中缺少该属性或没有会话，将导致抛出异常。
	 *
	 * Switch this to {@code false} if you prefer a {@code null} or Java 8
	 * {@code java.util.Optional} if the attribute doesn't exist.
	 * 如果您喜欢{@code null}或Java 8 {@code java.util.Optional}则将其切换为{@code false}。如果属性不存在。
	 *
	 */
	boolean required() default true;

}
