package com.logicaldoc.bm.csv;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logicaldoc.bm.Config;
import com.logicaldoc.util.StringUtil;
import com.logicaldoc.util.csv.CSVFileReader;
import com.logicaldoc.util.io.FileUtil;
import com.logicaldoc.util.time.TimeDiff;

public class BuildStore {

	private static final int FILENAME_INDEX = 17;

	private static Logger log = LoggerFactory.getLogger(BuildStore.class);

	private File documentCSV = new File("ld_document.csv");

	private File storeRoot = new File("store");

	private File sourceDir = new File("docs");

	private File countFile = new File("buildstore.count");

	private long lines = 0;

	public static void main(String[] args) throws Exception {
		if (System.getProperty("bm.root") == null)
			System.setProperty("bm.root", ".");

		BuildStore csv = new BuildStore();
		csv.buildStore();
	}

	public BuildStore() {
		sourceDir = new File(Config.get().getProperty("csv.sourcedir"));
		File target = new File(Config.get().getProperty("csv.target"));
		target.mkdirs();
		countFile = new File(target, "count");
		if (!countFile.exists())
			try {
				FileUtils.touch(countFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		documentCSV = new File(target, "ld_document.csv");

		storeRoot = new File(Config.get().getProperty("csv.store"));
		storeRoot.mkdirs();
	}

	public void printProgress(long count) {
		double progress = ((double) count / (double) lines) * (double) 100;
		if (progress % 1 == 0) {
			DecimalFormat df = new DecimalFormat("###");
			log.info("Process completed at {}%", df.format(progress));
		}
	}

	public void buildStore() throws Exception {
		Date start = new Date();
		log.info("Start building the store file system at {}", storeRoot.getAbsolutePath());
		
		
		// Count the actual number of lines in the document file (excluding the
		// header)
		lines = countLines(documentCSV) - 1;
		log.info("Current documents file {} has {} lines", documentCSV.getAbsolutePath(), lines);

		long alreadyProcessedCount = 0;
		try {
			alreadyProcessedCount = Long.parseLong(FileUtil.readFile(countFile));
		} catch (Throwable t) {
		}

		long count = alreadyProcessedCount;
		if (lines >= 1) {
			CSVFileReader reader = new CSVFileReader(documentCSV.getAbsolutePath());
			// Skip first line
			reader.readFields();
			List<String> fields = reader.readFields();
			while (fields != null) {
				count++;
				if (count > alreadyProcessedCount) {
					long docId = Long.parseLong(fields.get(0));
					String docFileName = fields.get(FILENAME_INDEX);

					// Recreate the source file name
					String sourceFileName = docFileName.substring(0, docFileName.lastIndexOf('-'))
							+ docFileName.substring(docFileName.lastIndexOf('.'));
					File srcFile = new File(sourceDir, sourceFileName);
					File targetFile = computeStoreFile(docId);
					
					// Copy the source file in the storage folder
					if (!targetFile.exists())
						FileUtils.copyFile(srcFile, computeStoreFile(docId));
					FileUtil.writeFile(Long.toString(count), countFile.getAbsolutePath());
				}
				printProgress(count);
				fields = reader.readFields();
			}
			reader.close();
		}
		Date end = new Date();
		log.info("Process completed in {}", TimeDiff.printDuration(start, end));

	}

	protected File computeStoreFile(long docId) {
		return new File(storeRoot, StringUtil.split(Long.toString(docId), '/', 3) + "/doc/1.0");
	}

	public static long countLines(File file) throws IOException {
		InputStream is = new BufferedInputStream(new FileInputStream(file));
		try {
			byte[] c = new byte[1024];
			long count = 0;
			int readChars = 0;
			boolean empty = true;
			while ((readChars = is.read(c)) != -1) {
				empty = false;
				for (int i = 0; i < readChars; ++i) {
					if (c[i] == '\n') {
						++count;
					}
				}
			}
			return (count == 0 && !empty) ? 1 : count;
		} finally {
			is.close();
		}
	}
}