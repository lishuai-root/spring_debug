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

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Convenient superclass for application objects that want to be aware of
 * the application context, e.g. for custom lookup of collaborating beans
 * or for context-specific resource access. It saves the application
 * context reference and provides an initialization callback method.
 * 想要了解应用程序上下文的应用程序对象的方便超类，例如用于自定义查找协作bean或用于特定于上下文的资源访问。
 * 它保存应用程序上下文引用并提供初始化回调方法。
 *
 * Furthermore, it offers numerous convenience methods for message lookup.
 * 此外，它为消息查找提供了许多方便的方法。
 *
 * <p>There is no requirement to subclass this class: It just makes things
 * a little easier if you need access to the context, e.g. for access to
 * file resources or to the message source. Note that many application
 * objects do not need to be aware of the application context at all,
 * as they can receive collaborating beans via bean references.
 * 没有要求子类化这个类:如果你需要访问上下文，例如访问文件资源或消息源，它只是让事情变得更容易一些。
 * 请注意，许多应用程序对象根本不需要知道应用程序上下文，因为它们可以通过bean引用接收协作bean。
 *
 *
 * <p>Many framework classes are derived from this class, particularly
 * within the web support.
 * 许多框架类都派生自这个类，特别是在web支持中。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.web.context.support.WebApplicationObjectSupport
 */
public abstract class ApplicationObjectSupport implements ApplicationContextAware {

	/** Logger that is available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/** ApplicationContext this object runs in. */
	@Nullable
	private ApplicationContext applicationContext;

	/** MessageSourceAccessor for easy message access. */
	@Nullable
	private MessageSourceAccessor messageSourceAccessor;


	@Override
	public final void setApplicationContext(@Nullable ApplicationContext context) throws BeansException {
		if (context == null && !isContextRequired()) {
			// Reset internal context state.
			this.applicationContext = null;
			this.messageSourceAccessor = null;
		}
		else if (this.applicationContext == null) {
			// Initialize with passed-in context.
			if (!requiredContextClass().isInstance(context)) {
				throw new ApplicationContextException(
						"Invalid application context: needs to be of type [" + requiredContextClass().getName() + "]");
			}
			this.applicationContext = context;
			this.messageSourceAccessor = new MessageSourceAccessor(context);
			initApplicationContext(context);
		}
		else {
			// Ignore reinitialization if same context passed in.
			if (this.applicationContext != context) {
				throw new ApplicationContextException(
						"Cannot reinitialize with different application context: current one is [" +
						this.applicationContext + "], passed-in one is [" + context + "]");
			}
		}
	}

	/**
	 * Determine whether this application object needs to run in an ApplicationContext.
	 * 确定这个应用程序对象是否需要在ApplicationContext中运行。
	 *
	 * <p>Default is "false". Can be overridden to enforce running in a context
	 * (i.e. to throw IllegalStateException on accessors if outside a context).
	 * 默认为“false”。可以重写以强制在上下文中运行(例如，如果在上下文中，则在访问器上抛出IllegalStateException)。
	 *
	 * @see #getApplicationContext
	 * @see #getMessageSourceAccessor
	 */
	protected boolean isContextRequired() {
		return false;
	}

	/**
	 * Determine the context class that any context passed to
	 * {@code setApplicationContext} must be an instance of.
	 * 确定传递给{@code setApplicationContext}的任何上下文必须是其实例的上下文类。
	 *
	 * Can be overridden in subclasses.
	 * @see #setApplicationContext
	 */
	protected Class<?> requiredContextClass() {
		return ApplicationContext.class;
	}

	/**
	 * Subclasses can override this for custom initialization behavior.
	 * 子类可以覆盖这个自定义初始化行为。
	 *
	 * Gets called by {@code setApplicationContext} after setting the context instance.
	 * 在设置上下文实例后由{@code setApplicationContext}调用。
	 *
	 * <p>Note: Does <i>not</i> get called on re-initialization of the context
	 * but rather just on first initialization of this object's context reference.
	 * 注意:是否<i>而不是<i>在重新初始化上下文时被调用，而只是在该对象的上下文引用的第一次初始化时被调用。
	 *
	 * <p>The default implementation calls the overloaded {@link #initApplicationContext()}
	 * method without ApplicationContext reference.
	 * 默认实现调用重载的{@link #initApplicationContext()}方法，但没有ApplicationContext引用。
	 *
	 * @param context the containing ApplicationContext
	 * @throws ApplicationContextException in case of initialization errors
	 * @throws BeansException if thrown by ApplicationContext methods
	 * @see #setApplicationContext
	 */
	protected void initApplicationContext(ApplicationContext context) throws BeansException {
		initApplicationContext();
	}

	/**
	 * Subclasses can override this for custom initialization behavior.
	 * 子类可以覆盖这个自定义初始化行为。
	 *
	 * <p>The default implementation is empty. Called by
	 * {@link #initApplicationContext(org.springframework.context.ApplicationContext)}.
	 * @throws ApplicationContextException in case of initialization errors
	 * @throws BeansException if thrown by ApplicationContext methods
	 * @see #setApplicationContext
	 */
	protected void initApplicationContext() throws BeansException {
	}


	/**
	 * Return the ApplicationContext that this object is associated with.
	 * 返回此对象关联的ApplicationContext。
	 *
	 * @throws IllegalStateException if not running in an ApplicationContext
	 */
	@Nullable
	public final ApplicationContext getApplicationContext() throws IllegalStateException {
		if (this.applicationContext == null && isContextRequired()) {
			throw new IllegalStateException(
					"ApplicationObjectSupport instance [" + this + "] does not run in an ApplicationContext");
		}
		return this.applicationContext;
	}

	/**
	 * Obtain the ApplicationContext for actual use.
	 * 获取实际使用的ApplicationContext。
	 *
	 * @return the ApplicationContext (never {@code null})
	 * @throws IllegalStateException in case of no ApplicationContext set
	 * @since 5.0
	 */
	protected final ApplicationContext obtainApplicationContext() {
		ApplicationContext applicationContext = getApplicationContext();
		Assert.state(applicationContext != null, "No ApplicationContext");
		return applicationContext;
	}

	/**
	 * Return a MessageSourceAccessor for the application context
	 * used by this object, for easy message access.
	 * 返回此对象使用的应用程序上下文的MessageSourceAccessor，以方便消息访问。
	 *
	 * @throws IllegalStateException if not running in an ApplicationContext
	 */
	@Nullable
	protected final MessageSourceAccessor getMessageSourceAccessor() throws IllegalStateException {
		if (this.messageSourceAccessor == null && isContextRequired()) {
			throw new IllegalStateException(
					"ApplicationObjectSupport instance [" + this + "] does not run in an ApplicationContext");
		}
		return this.messageSourceAccessor;
	}

}
