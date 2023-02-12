package com.shuai.config.initializer;

import java.beans.PropertyEditorSupport;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/2/12 21:40
 * @version: 1.0
 */

public class MyCustomDateEditor extends PropertyEditorSupport {

	private DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		try {
			System.out.println("MyCustomDateEditor : " + text);
			setValue(this.df.parse(text));
		} catch (ParseException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("--------error---------");
		}
	}
}
