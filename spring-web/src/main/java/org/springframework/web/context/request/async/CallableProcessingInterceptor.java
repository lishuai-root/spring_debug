/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.context.request.async;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.concurrent.Callable;

/**
 * Intercepts concurrent request handling, where the concurrent result is
 * obtained by executing a {@link Callable} on behalf of the application with
 * an {@link AsyncTaskExecutor}.
 * 拦截并发请求处理，其中并发结果是通过代表应用程序执行{@link Callable}和{@link AsyncTaskExecutor}获得的。
 *
 *
 * <p>A {@code CallableProcessingInterceptor} is invoked before and after the
 * invocation of the {@code Callable} task in the asynchronous thread, as well
 * as on timeout/error from a container thread, or after completing for any reason
 * including a timeout or network error.
 * 在异步线程中调用{@code Callable}任务之前和之后调用{@code CallableProcessingInterceptor}，
 * 或者在容器线程中调用timeout/error，或者在由于超时或网络错误等任何原因完成之后调用。
 *
 *
 * <p>As a general rule exceptions raised by interceptor methods will cause
 * async processing to resume by dispatching back to the container and using
 * the Exception instance as the concurrent result. Such exceptions will then
 * be processed through the {@code HandlerExceptionResolver} mechanism.
 * 作为一般规则，拦截器方法引发的异常将通过分派回容器并使用Exception实例作为并发结果来恢复异步处理。
 * 这样的异常将通过{@code HandlerExceptionResolver}机制进行处理。
 *
 *
 * <p>The {@link #handleTimeout(NativeWebRequest, Callable) handleTimeout} method
 * can select a value to be used to resume processing.
 * {@link #handleTimeout(NativeWebRequest, Callable) handleTimeout}方法可以选择一个值来恢复处理。
 *
 * @author Rossen Stoyanchev
 * @author Rob Winch
 * @since 3.2
 */
public interface CallableProcessingInterceptor {

	/**
	 * Constant indicating that no result has been determined by this
	 * interceptor, giving subsequent interceptors a chance.
	 * 常量，表示此拦截器没有确定任何结果，给后续拦截器一个机会。
	 *
	 * @see #handleTimeout
	 * @see #handleError
	 */
	Object RESULT_NONE = new Object();

	/**
	 * Constant indicating that the response has been handled by this interceptor
	 * without a result and that no further interceptors are to be invoked.
	 * 常量，指示此拦截器已处理响应而没有结果，并且不再调用其他拦截器。
	 *
	 * @see #handleTimeout
	 * @see #handleError
	 */
	Object RESPONSE_HANDLED = new Object();


	/**
	 * Invoked <em>before</em> the start of concurrent handling in the original
	 * thread in which the {@code Callable} is submitted for concurrent handling.
	 * 在<em>之前调用<em>，在提交{@code Callable}进行并发处理的原始线程中开始并发处理。
	 *
	 * <p>This is useful for capturing the state of the current thread just prior to
	 * invoking the {@link Callable}. Once the state is captured, it can then be
	 * transferred to the new {@link Thread} in
	 * {@link #preProcess(NativeWebRequest, Callable)}. Capturing the state of
	 * Spring Security's SecurityContextHolder and migrating it to the new Thread
	 * is a concrete example of where this is useful.
	 * 这对于在调用{@link Callable}之前捕获当前线程的状态非常有用。
	 * 一旦状态被捕获，它就可以转移到{@link #preProcess(NativeWebRequest, Callable)}中的新{@link Thread}。
	 * 捕获Spring Security的SecurityContextHolder的状态并将其迁移到新线程是一个很好的例子。
	 *
	 * <p>The default implementation is empty.
	 * @param request the current request
	 * @param task the task for the current async request
	 * @throws Exception in case of errors
	 */
	default <T> void beforeConcurrentHandling(NativeWebRequest request, Callable<T> task) throws Exception {
	}

	/**
	 * Invoked <em>after</em> the start of concurrent handling in the async
	 * thread in which the {@code Callable} is executed and <em>before</em> the
	 * actual invocation of the {@code Callable}.
	 * 在<em>之后调用<em>，在异步线程中执行{@code Callable}的并发处理的开始，在<em>之前调用{@code Callable}。
	 *
	 * <p>The default implementation is empty.
	 * @param request the current request
	 * @param task the task for the current async request
	 * @throws Exception in case of errors
	 */
	default <T> void preProcess(NativeWebRequest request, Callable<T> task) throws Exception {
	}

	/**
	 * Invoked <em>after</em> the {@code Callable} has produced a result in the
	 * async thread in which the {@code Callable} is executed. This method may
	 * be invoked later than {@code afterTimeout} or {@code afterCompletion}
	 * depending on when the {@code Callable} finishes processing.
	 * 在<em>后调用<em> {@code Callable}已经在执行{@code Callable}的异步线程中产生了一个结果。
	 * 此方法可以在{@code afterTimeout}或{@code afterCompletion}之后调用，这取决于{@code Callable}何时完成处理。
	 *
	 * <p>The default implementation is empty.
	 * @param request the current request
	 * @param task the task for the current async request
	 * @param concurrentResult the result of concurrent processing, which could
	 * be a {@link Throwable} if the {@code Callable} raised an exception
	 * @throws Exception in case of errors
	 */
	default <T> void postProcess(NativeWebRequest request, Callable<T> task,
			Object concurrentResult) throws Exception {
	}

	/**
	 * Invoked from a container thread when the async request times out before
	 * the {@code Callable} task completes. Implementations may return a value,
	 * including an {@link Exception}, to use instead of the value the
	 * {@link Callable} did not return in time.
	 * 当async请求在{@code Callable}任务完成之前超时时，从容器线程调用。
	 * 实现可以返回一个值，包括一个{@link Exception}，来代替{@link Callable}没有及时返回的值。
	 *
	 * <p>The default implementation always returns {@link #RESULT_NONE}.
	 * @param request the current request
	 * @param task the task for the current async request
	 * @return a concurrent result value; if the value is anything other than
	 * {@link #RESULT_NONE} or {@link #RESPONSE_HANDLED}, concurrent processing
	 * is resumed and subsequent interceptors are not invoked
	 * @throws Exception in case of errors
	 */
	default <T> Object handleTimeout(NativeWebRequest request, Callable<T> task) throws Exception {
		return RESULT_NONE;
	}

	/**
	 * Invoked from a container thread when an error occurred while processing
	 * the async request before the {@code Callable} task completes.
	 * 当在{@code Callable}任务完成之前处理异步请求时发生错误时，从容器线程调用。
	 *
	 * Implementations may return a value, including an {@link Exception}, to
	 * use instead of the value the {@link Callable} did not return in time.
	 * 实现可以返回一个值，包括一个{@link Exception}，来代替{@link Callable}没有及时返回的值。
	 *
	 * <p>The default implementation always returns {@link #RESULT_NONE}.
	 * @param request the current request
	 * @param task the task for the current async request
	 * @param t the error that occurred while request processing
	 * @return a concurrent result value; if the value is anything other than
	 * {@link #RESULT_NONE} or {@link #RESPONSE_HANDLED}, concurrent processing
	 * is resumed and subsequent interceptors are not invoked
	 * @throws Exception in case of errors
	 * @since 5.0
	 */
	default <T> Object handleError(NativeWebRequest request, Callable<T> task, Throwable t) throws Exception {
		return RESULT_NONE;
	}

	/**
	 * Invoked from a container thread when async processing completes for any
	 * reason including timeout or network error.
	 * 当异步处理因任何原因(包括超时或网络错误)完成时，从容器线程调用。
	 *
	 * <p>The default implementation is empty.
	 * @param request the current request
	 * @param task the task for the current async request
	 * @throws Exception in case of errors
	 */
	default <T> void afterCompletion(NativeWebRequest request, Callable<T> task) throws Exception {
	}

}
