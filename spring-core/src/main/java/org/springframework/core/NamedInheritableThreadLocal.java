/*
 * Copyright 2002-2018 the original author or authors.
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

import org.springframework.util.Assert;

/**
 * {@link InheritableThreadLocal} subclass that exposes a specified name
 * as {@link #toString()} result (allowing for introspection).
 * {@link InheritableThreadLocal}子类，将指定的名称公开为{@link toString()}结果(允许自省)。
 *
 * 这个类扩展{@code ThreadLocal}以提供从父线程到子线程的值继承:当创建子线程时，子线程接收父线程具有值的所有可继承线程局部变量的初始值。
 * 通常情况下，子进程的值将与父进程的值相同;然而，子类的值可以通过重写这个类中的{@code childValue}方法来成为父类的任意函数。
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 * @param <T> the value type
 * @see NamedThreadLocal
 */
public class NamedInheritableThreadLocal<T> extends InheritableThreadLocal<T> {

	private final String name;


	/**
	 * Create a new NamedInheritableThreadLocal with the given name.
	 * 用给定的名称创建一个新的NamedInheritableThreadLocal。
	 *
	 * @param name a descriptive name for this ThreadLocal
	 */
	public NamedInheritableThreadLocal(String name) {
		Assert.hasText(name, "Name must not be empty");
		this.name = name;
	}

	@Override
	public String toString() {
		return this.name;
	}

}
