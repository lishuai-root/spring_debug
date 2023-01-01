package com.shuai.config.initializer;

import com.shuai.util.FileUploadUtil;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/31 17:22
 * @version: 1.0
 */

public class MyFileUploadConfigDispatcherServletInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

	@Override
	protected Class<?>[] getRootConfigClasses() {
		return null;
	}

	@Override
	protected Class<?>[] getServletConfigClasses() {
		return null;
	}

	@Override
	protected String[] getServletMappings() {
		return new String[]{"/"};
	}

	@Override
	protected void customizeRegistration(jakarta.servlet.ServletRegistration.Dynamic registration) {
		MultipartConfigElement element = new MultipartConfigElement(FileUploadUtil.LOCATION, FileUploadUtil.MAX_FILE_SIZE, FileUploadUtil.MAX_REQUEST_SIZE, FileUploadUtil.FILE_SIZE_THRESHOLD);
		System.out.println("custom file upload config : " + multipartConfigElementToString(element));
		registration.setMultipartConfig(element);
	}

	public String multipartConfigElementToString(MultipartConfigElement element){
		return "{location : " + element.getLocation()
				+ ", maxFileSize : " + element.getMaxFileSize()
				+ ", maxRequestSize : " + element.getMaxRequestSize()
				+ ", fileSizeThreshold : " + element.getFileSizeThreshold()
				+ "}";
	}
}
