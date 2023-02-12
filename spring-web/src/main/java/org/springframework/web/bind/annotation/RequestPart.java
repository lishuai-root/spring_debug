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

package org.springframework.web.bind.annotation;

import java.beans.PropertyEditor;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartResolver;

/**
 * Annotation that can be used to associate the part of a "multipart/form-data" request
 * with a method argument.
 * 可用于将“multipart/form-data”请求的部分与方法参数关联的注释。
 *
 * <p>Supported method argument types include {@link MultipartFile} in conjunction with
 * Spring's {@link MultipartResolver} abstraction, {@code jakarta.servlet.http.Part} in
 * conjunction with Servlet 3.0 multipart requests, or otherwise for any other method
 * argument, the content of the part is passed through an {@link HttpMessageConverter}
 * taking into consideration the 'Content-Type' header of the request part. This is
 * analogous to what @{@link RequestBody} does to resolve an argument based on the
 * content of a non-multipart regular request.
 * <p>支持的方法参数类型包括{@link MultipartFile}结合Spring的{@link MultipartResolver}抽象，
 * {@code jakarta.servlet.http.Part}连同Servlet 3.0多部分请求，或其他任何方法参数，
 * 部分的内容通过{@link HttpMessageConverter}传递，考虑到请求部分的' content-type '头。
 * 这类似于@{@link RequestBody}基于非多部分常规请求的内容解析参数。
 *
 *
 * <p>Note that @{@link RequestParam} annotation can also be used to associate the part
 * of a "multipart/form-data" request with a method argument supporting the same method
 * argument types. The main difference is that when the method argument is not a String
 * or raw {@code MultipartFile} / {@code Part}, {@code @RequestParam} relies on type
 * conversion via a registered {@link Converter} or {@link PropertyEditor} while
 * {@link RequestPart} relies on {@link HttpMessageConverter HttpMessageConverters}
 * taking into consideration the 'Content-Type' header of the request part.
 * {@link RequestParam} is likely to be used with name-value form fields while
 * {@link RequestPart} is likely to be used with parts containing more complex content
 * e.g. JSON, XML).
 *
 * 注意，@{@link RequestParam}注释也可以用于将“multipart/form-data”请求的部分与支持相同方法参数类型的方法参数相关联。
 * 主要的区别是，当方法参数不是String或raw {@code MultipartFile} / {@code Part}时，
 * {@code @RequestParam}依赖于通过注册的{@link Converter}或{@link PropertyEditor}进行类型转换，
 * 而{@link RequestPart}依赖于{@link HttpMessageConverter HttpMessageConverters}，考虑到请求部分的'Content-Type'头。
 * {@link RequestParam}可能用于名称-值表单字段，而{@link RequestPart}可能用于包含更复杂内容的部分，如JSON, XML)。
 *
 *
 * @author Rossen Stoyanchev
 * @author Arjen Poutsma
 * @author Sam Brannen
 * @since 3.1
 * @see RequestParam
 * @see org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestPart {

	/**
	 * Alias for {@link #name}.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * The name of the part in the {@code "multipart/form-data"} request to bind to.
	 * @since 4.2
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * Whether the part is required.
	 * 是否需要该部件。
	 *
	 * <p>Defaults to {@code true}, leading to an exception being thrown
	 * if the part is missing in the request. Switch this to
	 * {@code false} if you prefer a {@code null} value if the part is
	 * not present in the request.
	 *
	 * <p>默认为{@code true}，如果请求中缺少该部分，将导致抛出异常。
	 * 如果部分在请求中不存在，则选择{@code null}值，将其切换为{@code false}。
	 *
	 */
	boolean required() default true;

}
