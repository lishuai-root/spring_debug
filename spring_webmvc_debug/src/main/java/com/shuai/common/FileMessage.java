package com.shuai.common;

/**
 * @description:
 * @author: LISHUAI
 * @createDate: 2022/12/31 17:36
 * @version: 1.0
 */

public class FileMessage {

	String fileName;

	long fileSize;

	String filePath;

	boolean uploadStatus;

	long executeTime;

	long startTime;

	long endTime;

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public FileMessage(){}

	public FileMessage(String fileName, long fileSize, String filePath, boolean uploadStatus) {
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.filePath = filePath;
		this.uploadStatus = uploadStatus;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public boolean getUploadStatus() {
		return uploadStatus;
	}

	public void setUploadStatus(boolean uploadStatus) {
		this.uploadStatus = uploadStatus;
	}

	public long getExecuteTime() {
		return executeTime;
	}

	public void setExecuteTime(long executeTime) {
		this.executeTime = executeTime;
	}
}
