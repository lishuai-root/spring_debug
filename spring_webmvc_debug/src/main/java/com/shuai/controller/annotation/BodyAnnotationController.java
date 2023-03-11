package com.shuai.controller.annotation;

import com.shuai.common.FileMessage;
import com.shuai.common.User;
import com.shuai.util.FileUploadUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

	@PostMapping("/customTest")
	public User customTest(User user){
		System.out.println(user);
		return user;
	}

	@PostMapping("/login")
	public User login(){
		User user = new User();
		user.setUsername("李帅");
		user.setAge(25);
		return user;
	}

	@GetMapping("/getFile")
	public Resource getFile(String fileName, HttpServletResponse response) throws MalformedURLException {
		File file = new File("E:\\All_workspace\\IDEA_workspace\\spring-framework-main\\spring_webmvc_debug\\src\\main\\webapp\\resource\\static\\html\\" + fileName);
//		response.addHeader("content-disposition", "attachment;fileName=" + file.getName());
		return new PathResource(file.toPath());
	}

	@GetMapping("/matrix/{name}")
	public void matrixVariableValue(@MatrixVariable String name){
		System.out.println("matrixVariableValue : " + name);
	}

	@GetMapping("/matrix1/{name}/{myAge}")
	public void matrixVariableValue1(@MatrixVariable String name, @MatrixVariable(value = "myAge") String age){
		System.out.println("matrixVariableValue name : " + name);
		System.out.println("matrixVariableValue age : " + age);
	}

	@GetMapping("/matrix2/{name}/{myAge}")
	public void matrixVariableValue2(@MatrixVariable String name, @MatrixVariable(value = "myAge") int age){
		System.out.println("matrixVariableValue name : " + name);
		System.out.println("matrixVariableValue age : " + age);
	}

	@GetMapping("/matrix3/{name}/{age}")
	public void matrixVariableValue3(@MatrixVariable(pathVar = "name") String name,
									 @MatrixVariable(value = "age", pathVar = "age") int age){
		System.out.println("matrixVariableValue name : " + name);
		System.out.println("matrixVariableValue age : " + age);
	}

	@GetMapping("/matrix4/{name}/{age}")
	public void matrixVariableValue4(@MatrixVariable Map<String, String> map){
		System.out.println("matrixVariableValue4 start");
		for (String key : map.keySet()){
			System.out.println(key + " - " + map.get(key));
		}
		System.out.println("matrixVariableValue4 end");
	}

	@GetMapping("/matrix5/{name}/{age}")
	public void matrixVariableValue5(@MatrixVariable(pathVar = "name", value = "age") String[] ages){
		System.out.println("matrixVariableValue5 start");
		for (String age : ages){
			System.out.println(age);
		}
		System.out.println("matrixVariableValue5 end");
	}

	@GetMapping("/matrix6/{name}/{age}")
	public void matrixVariableValue6(@MatrixVariable(pathVar = "name", value = "age") List<String> ages){
		System.out.println("matrixVariableValue5 start");
		for (String age : ages){
			System.out.println(age);
		}
		System.out.println("matrixVariableValue5 end");
	}

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
