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

package org.springframework.util.concurrent;

/**
 * Failure callback for a {@link ListenableFuture}.
 * {@link ListenableFuture}的失败回调。
 *
 * @author Sebastien Deleuze
 * @since 4.1
 */
@FunctionalInterface
public interface FailureCallback {

	/**
	 * Called when the {@link ListenableFuture} completes with failure.
	 * <p>Note that Exceptions raised by this method are ignored.
	 * 当{@link ListenableFuture}失败时调用。<p>注意由此方法引发的异常将被忽略。
	 *
	 * @param ex the failure
	 */
	void onFailure(Throwable ex);

}
