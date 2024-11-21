package com.logicaldoc.bm;

import java.io.File;
import java.util.Random;

import org.apache.commons.io.FileUtils;

import com.sun.mail.iap.ByteArray;

/**
 * Gives a random file from the docs in a source folder
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 6.4
 */
public class RandomFile {

	private File sourceDir = new File("docs");

	private Random random = new Random();

	private SourceFile[] sourceFiles;

	private boolean loadInMemory = true;

	public RandomFile(boolean loadInMemory) {
		this.loadInMemory = loadInMemory;
	}

	public SourceFile getSourceFile() throws Exception {
		if (sourceFiles == null)
			init();

		int index = random.nextInt(sourceFiles.length);
		return sourceFiles[index];
	}

	public void setSourceDir(String sourceDir) {
		this.sourceDir = new File(sourceDir);
	}

	private synchronized void init() throws Exception {
		if (sourceFiles != null)
			return;

		// Ensure that the source directory is present, if specified
		if (sourceDir != null) {
			if (!sourceDir.exists()) {
				throw new Exception(
						String.format("The source directory to contain upload files is missing: %s", sourceDir));
			}
			
			File[] files = sourceDir.listFiles();
			sourceFiles = new SourceFile[files.length];

			for (int i = 0; i < files.length; i++) {
				if (loadInMemory) {
					sourceFiles[i] = new SourceFile(files[i],
							new ByteArray(FileUtils.readFileToByteArray(files[i]), 0, (int) files[i].length()));
				} else {
					sourceFiles[i] = new SourceFile(files[i], null);
				}
			}
		} else {
			sourceFiles = new SourceFile[0];
		}
	}
}
