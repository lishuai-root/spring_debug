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

package org.springframework.web.servlet.handler;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前类用来解析和匹配通过实现{@link org.springframework.web.servlet.mvc.Controller}或者
 * {@link org.springframework.web.HttpRequestHandler}接口处理请求的bean
 *
 * Implementation of the {@link org.springframework.web.servlet.HandlerMapping}
 * interface that maps from URLs to beans with names that start with a slash ("/"),
 * similar to how Struts maps URLs to action names.
 * 实现了{@link org.springframework.web.servlet.HandlerMapping}接口，
 * 从url映射到名称以斜杠("/")开头的bean，类似于Struts将url映射到操作名的方式。
 *
 * <p>This is the default implementation used by the
 * {@link org.springframework.web.servlet.DispatcherServlet}, along with
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}.
 * Alternatively, {@link SimpleUrlHandlerMapping} allows for customizing a
 * handler mapping declaratively.
 * <p>这是{@link org.springframework.web.servlet.DispatcherServlet}，
 * 以及{@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}。
 * 或者，{@link SimpleUrlHandlerMapping}允许声明式地自定义处理程序映射。
 *
 *
 * <p>The mapping is from URL to bean name. Thus an incoming URL "/foo" would map
 * to a handler named "/foo", or to "/foo /foo2" in case of multiple mappings to
 * a single handler.
 * 映射是从URL到bean名称。因此，一个传入URL“/foo”将映射到一个名为“/foo”的处理程序，或者在多个映射到单个处理程序的情况下映射到“/foo /foo2”。
 *
 * <p>Supports direct matches (given "/test" -&gt; registered "/test") and "*"
 * matches (given "/test" -&gt; registered "/t*"). Note that the default is
 * to map within the current servlet mapping if applicable; see the
 * {@link #setAlwaysUseFullPath "alwaysUseFullPath"} property for details.
 *
 * 支持直接匹配(给定"/test" -&gt;注册“/test”)和“*”匹配项(给定“/test”-&gt;注册“/t”)。
 * 注意，默认是在当前servlet映射中映射(如果适用的话);详见{@link #setAlwaysUseFullPath "alwaysUseFullPath"}属性。
 *
 * For details on the pattern options, see the
 * {@link org.springframework.util.AntPathMatcher} javadoc.
 * 有关模式选项的详细信息，请参见{@link org.springframework.util。AntPathMatcher} javadoc。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see SimpleUrlHandlerMapping
 */
public class BeanNameUrlHandlerMapping extends AbstractDetectingUrlHandlerMapping {

	/**
	 * Checks name and aliases of the given bean for URLs, starting with "/".
	 * 检查给定bean的名称和别名以“”开头的url。
	 */
	@Override
	protected String[] determineUrlsForHandler(String beanName) {
		List<String> urls = new ArrayList<>();
		if (beanName.startsWith("/")) {
			urls.add(beanName);
		}
		String[] aliases = obtainApplicationContext().getAliases(beanName);
		for (String alias : aliases) {
			if (alias.startsWith("/")) {
				urls.add(alias);
			}
		}
		return StringUtils.toStringArray(urls);
	}

}
