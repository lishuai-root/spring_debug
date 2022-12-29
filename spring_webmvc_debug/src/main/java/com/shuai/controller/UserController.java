package com.shuai.controller;

import com.shuai.common.User;
import org.springframework.web.bind.annotation.*;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/27 23:45
 * @version: 1.0
 */

@RestController
@RequestMapping
@CrossOrigin
public class UserController {

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
}
