package com.logicaldoc.bm;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

import com.logicaldoc.util.config.ContextProperties;
import com.logicaldoc.util.io.FileUtil;

public class Config {

	public static ContextProperties config;

	public final static void reset() {
		config = null;
	}

	public final static ContextProperties get() {
		if (config == null)
			try {
				File file = new File(System.getProperty("bm.root") + "/conf/context.properties");
				if (!file.exists())
					throw new IOException("Unexisting config file");
				config = new ContextProperties(file);
			} catch (IOException e) {
				try {
					config = new ContextProperties();
				} catch (IOException e1) {
				}
			}
		return config;
	}

	public static String readConfigRile(String fileName) throws IOException {
		File file = new File(System.getProperty("bm.root") + "/conf/" + fileName);
		if (file.exists())
			return FileUtil.readFile(file);
		else
			return IOUtils.toString(Config.class.getResourceAsStream("/" + fileName), "UTF-8");
	}
}