package com.shuai.controller.annotation;

import com.shuai.common.FileMessage;
import com.shuai.common.User;
import com.shuai.util.FileUploadUtil;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/27 23:45
 * @version: 1.0
 */


@RestController
@RequestMapping(value = "/body")
@CrossOrigin
public class BodyAnnotationController {

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@GetMapping("exprName")
	public String exprName(@RequestParam("${USERNAME}") String name) {
		return name;
	}

	@GetMapping("hello")
	public String hello(Date date){
		System.out.println("Hello World! " + DATE_FORMAT.format(date));
		return "Hello World!";
	}

	@GetMapping("hello2")
	public String hello2(Date day){
		System.out.println("Hello World2! " + DATE_FORMAT.format(day));
		return "Hello World!";
	}

	@GetMapping("hello3")
	public String hello3(Date currDate){
		System.out.println("Hello World3! " + DATE_FORMAT.format(currDate));
		return "Hello World!";
	}

	@GetMapping("testView")
	public String testView(){
		System.out.println("testView");
		return "testView";
	}

	@PostMapping("user/{username}")
	public User user(@PathVariable String username){
		User user = new User();
		user.setUsername(username);
		user.setAge(25);
		return user;
	}


	@PostMapping("uploadFile")
	public FileMessage uploadFile(/*@RequestParam*/ MultipartFile testUpload) throws IOException {
		long start = System.currentTimeMillis();
		String fileName = testUpload.getName();
		long fileSize = testUpload.getSize();
		String originalFilename = testUpload.getOriginalFilename();
		File file = FileUploadUtil.createFile(FileUploadUtil.LOCATION + File.separatorChar + originalFilename, true);
		System.out.println(file.getAbsolutePath());
		boolean b = FileUploadUtil.copyFile(testUpload, file);
		long end = System.currentTimeMillis();
		FileMessage message = new FileMessage(fileName, fileSize, originalFilename, b);
		message.setStartTime(start);
		message.setEndTime(end);
		message.setExecuteTime(end - start);
		return message;
	}
}
