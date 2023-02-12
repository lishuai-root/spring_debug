package com.shuai.config.advice;

import com.shuai.common.User;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2023/2/5 20:38
 * @version: 1.0
 */

@ControllerAdvice
public class BaseAdvice {

//	@InitBinder(value = "date")
//	public void timeInitBinderDate(WebDataBinder binder){
//		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//		CustomDateEditor dateEditor = new CustomDateEditor(df, true);
//		binder.registerCustomEditor(Date.class, dateEditor);
//	}
//
//	@InitBinder(value = "day")
//	public void timeInitBinderDay(WebDataBinder binder){
//		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
//		CustomDateEditor dateEditor = new CustomDateEditor(df, true);
//		binder.registerCustomEditor(Date.class, dateEditor);
//	}

	@InitBinder
	public void timeInitBinderCurrentDate(WebDataBinder binder){
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		CustomDateEditor dateEditor = new CustomDateEditor(df, true);
		binder.registerCustomEditor(Date.class, dateEditor);
	}

	@ModelAttribute
	public void endTimeLong(Model model) {
		model.addAttribute("endTimeLong", System.currentTimeMillis());
	}

	@ModelAttribute(name = "endTime")
	public String endTime() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
	}

	@ModelAttribute
	public User addRequestUser() {
		User modelUser = new User();
		modelUser.setUsername("model_liShuai");
		return modelUser;
	}
}
