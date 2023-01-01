package com.shuai.util;

import org.springframework.core.io.InputStreamSource;

import java.io.*;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/31 17:52
 * @version: 1.0
 */

public class FileUploadUtil {

	public static final String LOCATION = "E:\\All_workspace\\IDEA_workspace\\spring-framework-main\\spring_webmvc_debug\\src\\main\\resources\\uploadFiles";

	public static final long MAX_FILE_SIZE = 1024 * 10L;

	public static final long MAX_REQUEST_SIZE = -1L;

	public static final int FILE_SIZE_THRESHOLD = 0;

	public static final int BUFFER_SIZE = 1024 << 3;

	public static File createFile(String path, boolean create) throws IOException {
		File file = new File(path);
		createFile(file, create);
		return file;
	}

	public static boolean createFile(File file, boolean create) throws IOException {
		if (file.isDirectory()) {
			return file.exists() || file.mkdirs();
		}
		if (!file.exists() && create){
			try {
				File parentFile = file.getParentFile();
				if (!parentFile.exists() && !parentFile.mkdirs()){
					IOException ioe = new IOException("directory creation failed : " + parentFile.getAbsolutePath());
					ioe.printStackTrace();
				}
				if (!file.createNewFile()){
					IOException ioe = new IOException("create new file failed : " + file.getAbsolutePath());
					ioe.printStackTrace();
				}
			}catch (Exception e){
				e.printStackTrace();
				throw e;
			}
		}
		return true;
	}

	public static boolean copyFile(String oldPath, String newPath){
		try {
			File oldFile = createFile(oldPath, false);
			File newFile = createFile(newPath, true);
			return copyFile(oldFile, newFile);
		}catch (Exception e){
			e.printStackTrace();
		}
		return false;
	}

	public static boolean copyFile(InputStreamSource source, File newFile){
		try (InputStream input = source.getInputStream();
			 OutputStream out = new FileOutputStream(newFile)){
			return copyFile(input, out);
		}catch (IOException ioe){
			ioe.printStackTrace();
		}
		return false;
	}


	public static boolean copyFile(File oldFile, File newFile){

		boolean b = false;

		try(InputStream input = new FileInputStream(oldFile);
			OutputStream out = new FileOutputStream(newFile)){

			b = copyFile(input, out);
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}catch (IOException ioe){
			ioe.printStackTrace();
		}
		return b;
	}

	public static boolean copyFile(InputStream input, OutputStream out){
		byte[] buffer = new byte[BUFFER_SIZE];
		int index;

		try {
			while ((index = input.read(buffer)) != -1){
				out.write(buffer, 0, index);
			}
		}catch (Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}
}
