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

package org.springframework.web.bind.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;

import java.lang.annotation.*;

/**
 * Marks a method or exception class with the status {@link #code} and
 * {@link #reason} that should be returned.
 * 用应该返回的状态{@link #code}和{@link #reason}标记方法或异常类。
 *
 * <p>The status code is applied to the HTTP response when the handler
 * method is invoked and overrides status information set by other means,
 * like {@code ResponseEntity} or {@code "redirect:"}.
 * 当调用处理程序方法并覆盖通过其他方式(如{@code ResponseEntity}或{@code "redirect:"})设置的状态信息时，状态代码将应用于HTTP响应。
 *
 * <p><strong>Warning</strong>: when using this annotation on an exception
 * class, or when setting the {@code reason} attribute of this annotation,
 * the {@code HttpServletResponse.sendError} method will be used.
 * <p><strong>Warning<strong>:当在异常类上使用这个注释时，或者当设置这个注释的{@code reason}属性时，
 * {@code HttpServletResponse.sendError}方法将被使用。
 *
 *
 * <p>With {@code HttpServletResponse.sendError}, the response is considered
 * complete and should not be written to any further. Furthermore, the Servlet
 * container will typically write an HTML error page therefore making the
 * use of a {@code reason} unsuitable for REST APIs. For such cases it is
 * preferable to use a {@link org.springframework.http.ResponseEntity} as
 * a return type and avoid the use of {@code @ResponseStatus} altogether.
 *
 * 使用{@code HttpServletResponse.sendError}时，响应被认为是完整的，不应该再写下去。
 * 此外，Servlet容器通常会编写HTML错误页面，因此{@code reason}的使用不适用于REST api。
 * 对于这种情况，最好使用{@link org.springframework.http.ResponseEntity}作为返回类型，避免使用{@code @ResponseStatus}。
 *
 *
 * <p>Note that a controller class may also be annotated with
 * {@code @ResponseStatus} which is then inherited by all {@code @RequestMapping}
 * and {@code @ExceptionHandler} methods in that class and its subclasses unless
 * overridden by a local {@code @ResponseStatus} declaration on the method.
 *
 * 注意，控制器类也可以用{@code @ResponseStatus}注释，
 * 然后由该类及其子类中的所有{@code @RequestMapping}和{@code @ExceptionHandler}方法继承，除非被方法上的本地{@code @ResponseStatus}声明覆盖。
 *
 *
 * @author Arjen Poutsma
 * @author Sam Brannen
 * @since 3.0
 * @see org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver
 * @see jakarta.servlet.http.HttpServletResponse#sendError(int, String)
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ResponseStatus {

	/**
	 * Alias for {@link #code}.
	 */
	@AliasFor("code")
	HttpStatus value() default HttpStatus.INTERNAL_SERVER_ERROR;

	/**
	 * The status <em>code</em> to use for the response.
	 * status <em>code<em>用于响应。
	 *
	 * <p>Default is {@link HttpStatus#INTERNAL_SERVER_ERROR}, which should
	 * typically be changed to something more appropriate.
	 * 默认值是{@link HttpStatus#INTERNAL_SERVER_ERROR}，通常应该更改为更合适的内容。
	 *
	 * @since 4.2
	 * @see jakarta.servlet.http.HttpServletResponse#setStatus(int)
	 * @see jakarta.servlet.http.HttpServletResponse#sendError(int)
	 */
	@AliasFor("value")
	HttpStatus code() default HttpStatus.INTERNAL_SERVER_ERROR;

	/**
	 * The <em>reason</em> to be used for the response.
	 * <em>reason<em>用于响应。
	 *
	 * <p>Defaults to an empty string which will be ignored. Set the reason to a
	 * non-empty value to have it used for the response.
	 * 默认为空字符串，将被忽略。将reason设置为非空值，以便将其用于响应。
	 *
	 * @see jakarta.servlet.http.HttpServletResponse#sendError(int, String)
	 */
	String reason() default "";

}
