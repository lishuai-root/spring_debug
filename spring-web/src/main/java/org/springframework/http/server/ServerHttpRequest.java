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

package org.springframework.http.server;

import java.net.InetSocketAddress;
import java.security.Principal;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpRequest;
import org.springframework.lang.Nullable;

/**
 * Represents a server-side HTTP request.
 * 表示服务器端HTTP请求。
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.0
 */
public interface ServerHttpRequest extends HttpRequest, HttpInputMessage {

	/**
	 * Return a {@link java.security.Principal} instance containing the name of the
	 * authenticated user.
	 * 返回一个{@link java.security。实例，其中包含经过身份验证的用户的名称。
	 *
	 * <p>If the user has not been authenticated, the method returns <code>null</code>.
	 * <p>如果用户没有被验证，该方法返回<code>null<code>。
	 */
	@Nullable
	Principal getPrincipal();

	/**
	 * Return the address on which the request was received.
	 * 返回接收请求的地址。
	 */
	InetSocketAddress getLocalAddress();

	/**
	 * Return the address of the remote client.
	 * 返回远程客户端的地址。
	 */
	InetSocketAddress getRemoteAddress();

	/**
	 * Return a control that allows putting the request in asynchronous mode so the
	 * response remains open until closed explicitly from the current or another thread.
	 *
	 * 返回一个控件，该控件允许将请求置于异步模式，以便响应保持打开状态，直到从当前线程或另一个线程显式关闭。
	 */
	ServerHttpAsyncRequestControl getAsyncRequestControl(ServerHttpResponse response);

}
