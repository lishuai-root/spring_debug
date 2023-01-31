/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.util;

import java.io.Serializable;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Servlet HttpSessionListener that automatically exposes the session mutex
 * when an HttpSession gets created. To be registered as a listener in
 * {@code web.xml}.
 * 创建HttpSession时自动公开会话互斥的Servlet httpessionlistener。在{@code web.xml}中注册为监听器。
 *
 * <p>The session mutex is guaranteed to be the same object during
 * the entire lifetime of the session, available under the key defined
 * by the {@code SESSION_MUTEX_ATTRIBUTE} constant. It serves as a
 * safe reference to synchronize on for locking on the current session.
 * 会话互斥锁保证在会话的整个生命周期内都是相同的对象，在常量{@code SESSION_MUTEX_ATTRIBUTE}定义的键下可用。
 * 它作为一个安全的参考来同步锁定当前会话。
 *
 *
 * <p>In many cases, the HttpSession reference itself is a safe mutex
 * as well, since it will always be the same object reference for the
 * same active logical session. However, this is not guaranteed across
 * different servlet containers; the only 100% safe way is a session mutex.
 * 在许多情况下，HttpSession引用本身也是一个安全的互斥量，因为它总是同一个活动逻辑会话的同一个对象引用。
 * 然而，这在不同的servlet容器之间是不能保证的;唯一100%安全的方法是会话互斥。
 *
 *
 * @author Juergen Hoeller
 * @since 1.2.7
 * @see WebUtils#SESSION_MUTEX_ATTRIBUTE
 * @see WebUtils#getSessionMutex(jakarta.servlet.http.HttpSession)
 * @see org.springframework.web.servlet.mvc.AbstractController#setSynchronizeOnSession
 */
public class HttpSessionMutexListener implements HttpSessionListener {

	@Override
	public void sessionCreated(HttpSessionEvent event) {
		event.getSession().setAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE, new Mutex());
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		event.getSession().removeAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE);
	}


	/**
	 * The mutex to be registered.
	 * 要注册的互斥锁。
	 *
	 * Doesn't need to be anything but a plain Object to synchronize on.
	 * 不需要任何东西，只是一个普通的对象上同步。
	 *
	 * Should be serializable to allow for HttpSession persistence.
	 * 应该是可序列化的，以允许HttpSession持久性。
	 */
	@SuppressWarnings("serial")
	private static class Mutex implements Serializable {
	}

}
