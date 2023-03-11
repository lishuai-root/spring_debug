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

package org.springframework.web.bind.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * 标注将一些属性值临时保存到会话中，会话结束后会从session中清除标注的属性
 *
 * Annotation that indicates the session attributes that a specific handler uses.
 * 指示特定处理程序使用的会话属性的注释。
 *
 * <p>This will typically list the names of model attributes which should be
 * transparently stored in the session or some conversational storage,
 * serving as form-backing beans. <b>Declared at the type level</b>, applying
 * to the model attributes that the annotated handler class operates on.
 * 这通常会列出模型属性的名称，这些属性应该透明地存储在会话或一些会话存储中，作为表单支持bean。
 * <b>在类型级别<b>声明，应用于带注释的处理程序类操作的模型属性。
 *
 *
 * <p><b>NOTE:</b> Session attributes as indicated using this annotation
 * correspond to a specific handler's model attributes, getting transparently
 * stored in a conversational session. Those attributes will be removed once
 * the handler indicates completion of its conversational session. Therefore,
 * use this facility for such conversational attributes which are supposed
 * to be stored in the session <i>temporarily</i> during the course of a
 * specific handler's conversation.
 * <p><b>注意:<b>使用此注释指示的会话属性对应于特定处理程序的模型属性，透明地存储在会话会话中。
 * 一旦处理程序指示其会话会话完成，这些属性将被删除。因此，在特定处理程序的会话过程中，这些会话属性应该存储在会话<i>临时<i>中。
 *
 *
 * <p>For permanent session attributes, e.g. a user authentication object,
 * use the traditional {@code session.setAttribute} method instead.
 * 对于永久会话属性，例如用户身份验证对象，使用传统的{@code session.setAttribute}
 *
 * Alternatively, consider using the attribute management capabilities of the
 * generic {@link org.springframework.web.context.request.WebRequest} interface.
 * 或者，可以考虑使用泛型{@link org.springframework.web.context.request.WebRequest}接口。
 *
 * <p><b>NOTE:</b> When using controller interfaces (e.g. for AOP proxying),
 * make sure to consistently put <i>all</i> your mapping annotations &mdash;
 * such as {@code @RequestMapping} and {@code @SessionAttributes} &mdash; on
 * the controller <i>interface</i> rather than on the implementation class.
 * <p><b>注意:<b>当使用控制器接口(例如用于AOP代理)时，确保一致地将<i>all<i> your mapping annotations &mdash;
 * 例如{@code @RequestMapping}和{@code @SessionAttributes} &mdash;在控制器<i>接口上，而不是在实现类上。
 *
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 2.5
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface SessionAttributes {

	/**
	 * Alias for {@link #names}.
	 */
	@AliasFor("names")
	String[] value() default {};

	/**
	 * The names of session attributes in the model that should be stored in the
	 * session or some conversational storage.
	 * 模型中应该存储在会话或某些会话存储中的会话属性的名称。
	 *
	 * <p><strong>Note</strong>: This indicates the <em>model attribute names</em>.
	 * <p><strong>注<strong>:表示<em>模型属性名<em>。
	 *
	 * The <em>session attribute names</em> may or may not match the model attribute
	 * names. Applications should therefore not rely on the session attribute
	 * names but rather operate on the model only.
	 * <em>会话属性名称<em>可能匹配也可能不匹配模型属性名称。因此，应用程序不应依赖于会话属性名称，而应仅对模型进行操作。
	 *
	 * @since 4.2
	 */
	@AliasFor("value")
	String[] names() default {};

	/**
	 * The types of session attributes in the model that should be stored in the
	 * session or some conversational storage.
	 * 模型中应该存储在会话或某些会话存储中的会话属性类型。
	 *
	 * <p>All model attributes of these types will be stored in the session,
	 * regardless of attribute name.
	 * 这些类型的所有模型属性都将存储在会话中，无论属性名称如何。
	 */
	Class<?>[] types() default {};

}
