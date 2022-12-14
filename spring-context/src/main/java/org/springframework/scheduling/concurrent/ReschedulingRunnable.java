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

package org.springframework.scheduling.concurrent;

import java.time.Clock;
import java.util.Date;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.lang.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.DelegatingErrorHandlingRunnable;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.util.Assert;
import org.springframework.util.ErrorHandler;

/**
 * Internal adapter that reschedules an underlying {@link Runnable} according
 * to the next execution time suggested by a given {@link Trigger}.
 * 内部适配器，根据给定的{@link触发器}建议的下一次执行时间重新调度底层{@link Runnable}。
 *
 * <p>Necessary because a native {@link ScheduledExecutorService} supports
 * delay-driven execution only. The flexibility of the {@link Trigger} interface
 * will be translated onto a delay for the next execution time (repeatedly).
 * 必须的，因为本地{@link ScheduledExecutorService}只支持延迟驱动执行。
 * {@link Trigger}接口的灵活性将转化为下一次执行时间(重复)的延迟。
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 3.0
 */
class ReschedulingRunnable extends DelegatingErrorHandlingRunnable implements ScheduledFuture<Object> {

	private final Trigger trigger;

	private final SimpleTriggerContext triggerContext;

	private final ScheduledExecutorService executor;

	@Nullable
	private ScheduledFuture<?> currentFuture;

	@Nullable
	private Date scheduledExecutionTime;

	private final Object triggerContextMonitor = new Object();


	public ReschedulingRunnable(Runnable delegate, Trigger trigger, Clock clock,
			ScheduledExecutorService executor, ErrorHandler errorHandler) {

		super(delegate, errorHandler);
		this.trigger = trigger;
		this.triggerContext = new SimpleTriggerContext(clock);
		this.executor = executor;
	}


	@Nullable
	public ScheduledFuture<?> schedule() {
		synchronized (this.triggerContextMonitor) {
			// 获取任务下一次执行的时间
			this.scheduledExecutionTime = this.trigger.nextExecutionTime(this.triggerContext);
			if (this.scheduledExecutionTime == null) {
				return null;
			}
			// 用任务下一次的执行时间减去当前时间，得到间隔时间
			long initialDelay = this.scheduledExecutionTime.getTime() - this.triggerContext.getClock().millis();
			this.currentFuture = this.executor.schedule(this, initialDelay, TimeUnit.MILLISECONDS);
			return this;
		}
	}

	private ScheduledFuture<?> obtainCurrentFuture() {
		Assert.state(this.currentFuture != null, "No scheduled future");
		return this.currentFuture;
	}

	@Override
	public void run() {
		Date actualExecutionTime = new Date(this.triggerContext.getClock().millis());
		// 调用父类方法执行定时任务
		super.run();
		Date completionTime = new Date(this.triggerContext.getClock().millis());
		synchronized (this.triggerContextMonitor) {
			Assert.state(this.scheduledExecutionTime != null, "No scheduled execution");
			this.triggerContext.update(this.scheduledExecutionTime, actualExecutionTime, completionTime);
			// 每次执行完定时任务后，重新把定时任务注册到线程池，等待下次执行
			if (!obtainCurrentFuture().isCancelled()) {
				schedule();
			}
		}
	}


	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		synchronized (this.triggerContextMonitor) {
			return obtainCurrentFuture().cancel(mayInterruptIfRunning);
		}
	}

	@Override
	public boolean isCancelled() {
		synchronized (this.triggerContextMonitor) {
			return obtainCurrentFuture().isCancelled();
		}
	}

	@Override
	public boolean isDone() {
		synchronized (this.triggerContextMonitor) {
			return obtainCurrentFuture().isDone();
		}
	}

	@Override
	public Object get() throws InterruptedException, ExecutionException {
		ScheduledFuture<?> curr;
		synchronized (this.triggerContextMonitor) {
			curr = obtainCurrentFuture();
		}
		return curr.get();
	}

	@Override
	public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		ScheduledFuture<?> curr;
		synchronized (this.triggerContextMonitor) {
			curr = obtainCurrentFuture();
		}
		return curr.get(timeout, unit);
	}

	@Override
	public long getDelay(TimeUnit unit) {
		ScheduledFuture<?> curr;
		synchronized (this.triggerContextMonitor) {
			curr = obtainCurrentFuture();
		}
		return curr.getDelay(unit);
	}

	@Override
	public int compareTo(Delayed other) {
		if (this == other) {
			return 0;
		}
		long diff = getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);
		return (diff == 0 ? 0 : ((diff < 0)? -1 : 1));
	}

}
