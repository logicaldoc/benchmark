package com.logicaldoc.bm;

import java.io.File;
import java.util.Date;

import com.sun.mail.iap.ByteArray;

public class SourceFile {

	private File file;

	private ByteArray content;

	private String baseName;

	private String extension;

	public File getFile() {
		return file;
	}

	public ByteArray getContent() {
		return content;
	}

	public SourceFile(File file, ByteArray byteArray) {
		this.file = file;
		this.content = byteArray;
		this.baseName = file.getName().substring(0, file.getName().lastIndexOf('.')) + "-";
		this.extension = file.getName().substring(file.getName().lastIndexOf('.'));
	}

	public String getUniqueFilename() {
		return baseName + new Date().getTime() + extension;
	}
}
