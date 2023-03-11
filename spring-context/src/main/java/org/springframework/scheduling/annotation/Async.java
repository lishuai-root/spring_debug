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

package org.springframework.scheduling.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that marks a method as a candidate for <i>asynchronous</i> execution.
 * 将一个方法标记为<i>异步<i>执行的候选方法的注释。
 *
 * Can also be used at the type level, in which case all of the type's methods are
 * considered as asynchronous. Note, however, that {@code @Async} is not supported
 * on methods declared within a
 * {@link org.springframework.context.annotation.Configuration @Configuration} class.
 * 也可以在类型级别使用，在这种情况下，类型的所有方法都被认为是异步的。
 * 但是请注意，{@code @Async}不支持在{@link org.springframework.context.annotation.Configuration @Configuration}类中声明的方法。
 *
 *
 * <p>In terms of target method signatures, any parameter types are supported.
 * <p>就目标方法签名而言，支持任何参数类型。
 *
 * However, the return type is constrained to either {@code void} or
 * {@link java.util.concurrent.Future}. In the latter case, you may declare the
 * more specific {@link org.springframework.util.concurrent.ListenableFuture} or
 * {@link java.util.concurrent.CompletableFuture} types which allow for richer
 * interaction with the asynchronous task and for immediate composition with
 * further processing steps.
 * 但是，返回类型被限制为{@code void}或{@link java.util.concurrent.Future}。
 * 在后一种情况下，您可以声明更具体的{@link org.springframework.util.concurrent.ListenableFuture}或{@link java.util.concurrent.CompletableFuture}类型，
 * 允许与异步任务进行更丰富的交互，并允许与进一步的处理步骤立即组合。
 *
 *
 * <p>A {@code Future} handle returned from the proxy will be an actual asynchronous
 * {@code Future} that can be used to track the result of the asynchronous method
 * execution. However, since the target method needs to implement the same signature,
 * it will have to return a temporary {@code Future} handle that just passes a value
 * through: e.g. Spring's {@link AsyncResult}, EJB 3.1's {@link jakarta.ejb.AsyncResult},
 * or {@link java.util.concurrent.CompletableFuture#completedFuture(Object)}.
 *
 * 从代理返回的{@code Future}句柄将是一个实际的异步{@code Future}，可用于跟踪异步方法执行的结果。
 * 然而，由于目标方法需要实现相同的签名，它将不得不返回一个临时的{@code Future}句柄，
 * 只传递一个值:例如Spring的{@link AsyncResult}， EJB 3.1的{@link jakarta.ejb.AsyncResult}或{@link java.util.concurrent.CompletableFuture#completedFuture(Object)}。
 *
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 3.0
 * @see AnnotationAsyncExecutionInterceptor
 * @see AsyncAnnotationAdvisor
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Async {

	/**
	 * A qualifier value for the specified asynchronous operation(s).
	 * 指定异步操作的限定符值。
	 *
	 * <p>May be used to determine the target executor to be used when executing
	 * the asynchronous operation(s), matching the qualifier value (or the bean
	 * name) of a specific {@link java.util.concurrent.Executor Executor} or
	 * {@link org.springframework.core.task.TaskExecutor TaskExecutor}
	 * bean definition.
	 * <p>可用于确定执行异步操作时使用的目标执行器，
	 * 匹配特定{@link java.util.concurrent.Executor executor}或{@link org.springframework.core.task.TaskExecutor TaskExecutor} bean定义的限定符值(或bean名称)。
	 *
	 *
	 * <p>When specified on a class-level {@code @Async} annotation, indicates that the
	 * given executor should be used for all methods within the class. Method-level use
	 * of {@code Async#value} always overrides any value set at the class level.
	 * <p>当在类级{@code @Async}注释中指定时，表示给定的执行器应该用于类中的所有方法。方法级使用{@code Async#value}总是覆盖在类级设置的任何值。
	 *
	 * @since 3.1.2
	 */
	String value() default "";

}
