package com.shuai.controller.annotation;

import com.shuai.common.FileMessage;
import com.shuai.common.User;
import com.shuai.util.FileUploadUtil;
import jakarta.servlet.annotation.MultipartConfig;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/27 23:45
 * @version: 1.0
 */

@MultipartConfig(location = FileUploadUtil.LOCATION, maxFileSize = FileUploadUtil.MAX_FILE_SIZE)
@RestController
@RequestMapping
@CrossOrigin
public class AnnotationController {

	@GetMapping("hello")
	public String hello(){
		return "Hello World!";
	}

	@PostMapping("user/{username}")
	public User user(@PathVariable String username){
		User user = new User();
		user.setUsername(username);
		user.setAge(25);
		return user;
	}


	@PostMapping("uploadFile")
	public FileMessage uploadFile(@RequestParam MultipartFile testUpload) throws IOException {
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
