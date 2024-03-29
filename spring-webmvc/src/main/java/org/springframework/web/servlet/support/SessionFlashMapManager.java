/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.web.servlet.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.util.WebUtils;

import java.util.List;

/**
 * Store and retrieve {@link FlashMap} instances to and from the HTTP session.
 * 在HTTP会话中存储和检索{@link FlashMap}实例。
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1.1
 */
public class SessionFlashMapManager extends AbstractFlashMapManager {

	private static final String FLASH_MAPS_SESSION_ATTRIBUTE = SessionFlashMapManager.class.getName() + ".FLASH_MAPS";


	/**
	 * Retrieves saved FlashMap instances from the HTTP session, if any.
	 * 从HTTP会话检索保存的FlashMap实例(如果有的话)。
	 */
	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	protected List<FlashMap> retrieveFlashMaps(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return (session != null ? (List<FlashMap>) session.getAttribute(FLASH_MAPS_SESSION_ATTRIBUTE) : null);
	}

	/**
	 * Saves the given FlashMap instances in the HTTP session.
	 * 在HTTP会话中保存给定的FlashMap实例。
	 */
	@Override
	protected void updateFlashMaps(List<FlashMap> flashMaps, HttpServletRequest request, HttpServletResponse response) {
		WebUtils.setSessionAttribute(request, FLASH_MAPS_SESSION_ATTRIBUTE, (!flashMaps.isEmpty() ? flashMaps : null));
	}

	/**
	 * Exposes the best available session mutex.
	 * 暴露可用的最佳会话互斥量。
	 *
	 * @see org.springframework.web.util.WebUtils#getSessionMutex
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 */
	@Override
	protected Object getFlashMapsMutex(HttpServletRequest request) {
		return WebUtils.getSessionMutex(request.getSession());
	}

}
