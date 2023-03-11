/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.web.servlet.mvc.support;

import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.web.servlet.FlashMap;

import java.util.Collection;
import java.util.Map;

/**
 * A specialization of the {@link Model} interface that controllers can use to
 * select attributes for a redirect scenario. Since the intent of adding
 * redirect attributes is very explicit --  i.e. to be used for a redirect URL,
 * attribute values may be formatted as Strings and stored that way to make
 * them eligible to be appended to the query string or expanded as URI
 * variables in {@code org.springframework.web.servlet.view.RedirectView}.
 *
 * 控制器可以使用{@link Model}接口的专门化来为重定向场景选择属性。由于添加重定向属性的目的是非常明确的——即用于重定向URL，
 * 属性值可以被格式化为字符串并以这种方式存储，以使它们有资格被追加到查询字符串中或扩展为{@code org.springframework.web.servlet.view.RedirectView}中的URI变量。
 *
 *
 * <p>This interface also provides a way to add flash attributes. For a
 * general overview of flash attributes see {@link FlashMap}. You can use
 * {@link RedirectAttributes} to store flash attributes and they will be
 * automatically propagated to the "output" FlashMap of the current request.
 * <p>增加flash属性。有关flash属性的一般概述，请参阅{@link FlashMap}。你可以使用{@link RedirectAttributes}来存储flash属性，
 * 它们将自动传播到当前请求的“输出”FlashMap。
 *
 *
 * <p>Example usage in an {@code @Controller}:
 * <p> {@code @Controller}中的用法示例:
 *
 * <pre class="code">
 * &#064;RequestMapping(value = "/accounts", method = RequestMethod.POST)
 * public String handle(Account account, BindingResult result, RedirectAttributes redirectAttrs) {
 *   if (result.hasErrors()) {
 *     return "accounts/new";
 *   }
 *   // Save account ...
 *   redirectAttrs.addAttribute("id", account.getId()).addFlashAttribute("message", "Account created!");
 *   return "redirect:/accounts/{id}";
 * }
 * </pre>
 *
 * <p>A RedirectAttributes model is empty when the method is called and is never
 * used unless the method returns a redirect view name or a RedirectView.
 * 当方法被调用时，RedirectAttributes模型是空的，除非该方法返回一个重定向视图名称或一个重定向视图，否则该模型永远不会被使用。
 *
 *
 * <p>After the redirect, flash attributes are automatically added to the model
 * of the controller that serves the target URL.
 *重定向后，flash属性会自动添加到为目标URL服务的控制器的模型中。
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public interface RedirectAttributes extends Model {

	@Override
	RedirectAttributes addAttribute(String attributeName, @Nullable Object attributeValue);

	@Override
	RedirectAttributes addAttribute(Object attributeValue);

	@Override
	RedirectAttributes addAllAttributes(Collection<?> attributeValues);

	@Override
	RedirectAttributes mergeAttributes(Map<String, ?> attributes);

	/**
	 * Add the given flash attribute.
	 * @param attributeName the attribute name; never {@code null}
	 * @param attributeValue the attribute value; may be {@code null}
	 */
	RedirectAttributes addFlashAttribute(String attributeName, @Nullable Object attributeValue);

	/**
	 * Add the given flash storage using a
	 * {@link org.springframework.core.Conventions#getVariableName generated name}.
	 * @param attributeValue the flash attribute value; never {@code null}
	 */
	RedirectAttributes addFlashAttribute(Object attributeValue);

	/**
	 * Return the attributes candidate for flash storage or an empty Map.
	 *
	 * 返回用于闪存或空Map的候选属性。
	 */
	Map<String, ?> getFlashAttributes();
}
