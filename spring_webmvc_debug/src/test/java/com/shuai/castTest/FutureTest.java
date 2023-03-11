package com.shuai.castTest;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.IOException;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/2/21 20:32
 * @version: 1.0
 */

public class FutureTest {

	public static void main(String[] args) throws IOException {
		MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
		System.out.println(converter);
	}
}
