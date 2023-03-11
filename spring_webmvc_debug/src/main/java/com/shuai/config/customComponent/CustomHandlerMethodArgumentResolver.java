package com.shuai.config.customComponent;

import com.shuai.common.User;
import org.springframework.core.MethodParameter;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/2/28 22:33
 * @version: 1.0
 */

public class CustomHandlerMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver {
	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		return new CustomNamedValueInfo("customTestValueName", true, "");
	}

	@Override
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
		User user = new User();
		String[] attributes = request.getParameterValues("username");
		if (!ObjectUtils.isEmpty(attributes)){
			user.setUsername(attributes[0]);
		}
		attributes = request.getParameterValues("password");
		if (!ObjectUtils.isEmpty(attributes)){
			user.setPassword(attributes[0]);
		}
		attributes = request.getParameterValues("age");
		if (!ObjectUtils.isEmpty(attributes)){
			user.setAge(Integer.parseInt(attributes[0]));
		}
		attributes = request.getParameterValues("phoneNumber");
		if (!ObjectUtils.isEmpty(attributes)){
			user.setPhoneNumber(attributes[0]);
		}
		attributes = request.getParameterValues("address");
		if (!ObjectUtils.isEmpty(attributes)){
			user.setAddress(attributes[0]);
		}
		return user;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return CustomArgument.class.isAssignableFrom(parameter.getParameterType());
	}


	static class CustomNamedValueInfo extends NamedValueInfo{

		public CustomNamedValueInfo(String name, boolean required, String defaultValue) {
			super(name, required, defaultValue);
		}
	}
}
