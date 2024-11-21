package com.logicaldoc.bm.csv;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logicaldoc.bm.Config;
import com.logicaldoc.bm.RandomFile;
import com.logicaldoc.bm.SourceFile;
import com.logicaldoc.util.csv.CSVFileReader;
import com.logicaldoc.util.io.FileUtil;
import com.logicaldoc.util.time.TimeDiff;

public class PrepareCSV {

	private static Logger log = LoggerFactory.getLogger(PrepareCSV.class);

	private static final String NULL = "\\N";

	private final static String DOCUMENT_HEADER = "\"ld_id\",\"ld_lastmodified\",\"ld_deleted\",\"ld_immutable\",\"ld_customid\",\"ld_version\",\"ld_fileversion\",\"ld_date\",\"ld_creation\",\"ld_publisher\",\"ld_publisherid\",\"ld_creator\",\"ld_creatorid\",\"ld_status\",\"ld_type\",\"ld_lockuserid\""
			+ ",\"ld_language\",\"ld_filename\",\"ld_filesize\",\"ld_indexed\",\"ld_barcoded\",\"ld_signed\",\"ld_digest\",\"ld_folderid\",\"ld_templateid\",\"ld_exportstatus\",\"ld_exportid\",\"ld_exportname\",\"ld_exportversion\",\"ld_comment\""
			+ ",\"ld_workflowstatus\",\"ld_published\",\"ld_startpublishing\",\"ld_stoppublishing\",\"ld_transactionid\",\"ld_tgs\",\"ld_extresid\",\"ld_tenantid\",\"ld_recordversion\",\"ld_pages\",\"ld_stamped\",\"ld_nature\",\"ld_formid\",\"ld_lockuser\",\"ld_password\""
			+ ",\"ld_workflowstatusdisp\"";

	private final static String VERSION_HEADER = "\"ld_documentid\"," + DOCUMENT_HEADER;

	private final static String TAG_HEADER = "\"ld_docid\",\"ld_tenantid\",\"ld_tag\"";

	private final static String FOLDER_HEADER = "\"ld_id\",\"ld_lastmodified\",\"ld_deleted\",\"ld_name\",\"ld_parentid\",\"ld_securityref\",\"ld_description\",\"ld_type\",\"ld_creation\",\"ld_creator\",\"ld_creatorid\",\"ld_templateid\""
			+ ",\"ld_templocked\",\"ld_deleteuserid\",\"ld_tenantid\",\"ld_recordversion\",\"ld_position\",\"ld_hidden\",\"ld_quotadocs\",\"ld_quotasize\",\"ld_foldref\",\"ld_storage\",\"ld_maxversions\",\"ld_color\",\"ld_tgs\""
			+ ",\"ld_qthreshold\",\"ld_qrecipients\",\"ld_level\",\"ld_deleteuser\"";

	private File documentCSV = new File("ld_document.csv");

	private File versionCSV = new File("ld_version.csv");

	private File tagCSV = new File("ld_tag.csv");

	private File folderCSV = new File("ld_folder.csv");

	private long totalDocs = 1000000L;

	private Random random = new Random();

	private static List<String> tags = new ArrayList<String>();

	private int tagSize = 4;

	private int tagsNumber = 4;

	private long initialDocId = 1000;

	private long initialVersionId = 1000;

	private long rootFolerId = 4;

	private long initialFolderId = 1000;

	private long totalFolders = 1000000L;

	private long docId = 0;

	private long folderId = 0;

	private long versionId = 0;

	private List<String> folderIds = new ArrayList<String>();

	public static void main(String[] args) throws Exception {
		if (System.getProperty("bm.root") == null)
			System.setProperty("bm.root", ".");

		PrepareCSV csv = new PrepareCSV();
		csv.writeDocumentCSV();
	}

	public PrepareCSV() {
		tagSize = Config.get().getInt("csv.tags", 4);
		tagsNumber = Config.get().getInt("csv.tagsize", 4);
		rootFolerId = Config.get().getLong("csv.rootFolder", 4);
		initialVersionId = Config.get().getLong("csv.initialiversionid", 1000);
		initialDocId = Config.get().getLong("csv.initialidocumentid", 1000);
		initialFolderId = Config.get().getLong("csv.initialifolderid", 1000);
		totalDocs = Config.get().getLong("csv.totaldocuments", 1000000L);
		totalFolders = Config.get().getLong("csv.totalfolders", 1000000L);

		File target = new File(Config.get().getProperty("csv.target"));
		target.mkdirs();
		documentCSV = new File(target, "ld_document.csv");
		versionCSV = new File(target, "ld_version.csv");
		tagCSV = new File(target, "ld_tag.csv");
		folderCSV = new File(target, "ld_folder.csv");
	}

	public void prepareFolderCSV() throws IOException {
		log.info("Prepare folders");

		folderIds.clear();
		folderIds.add("" + rootFolerId);

		folderId = initialFolderId;
		if (!folderCSV.exists())
			folderCSV.createNewFile();
		if (folderCSV.length() == 0L) {
			FileUtil.writeFile(FOLDER_HEADER + "\r\n", folderCSV.getAbsolutePath());
		} else {
			ReversedLinesFileReader lr = new ReversedLinesFileReader(folderCSV, Charset.forName("UTF-8"));
			try {
				String lastLine = lr.readLine();
				if (!lastLine.equals(FOLDER_HEADER)) {
					String id = lastLine.substring(1, lastLine.indexOf("\","));
					long val = Long.parseLong(id) + 1;
					if (val > folderId)
						folderId = val;
				}
			} finally {
				lr.close();
			}
		}
		// Count the actual number of lines in the file (excluding the header)
		long lines = countLines(folderCSV) - 1;
		log.info("Current folders file {} has {} lines", folderCSV.getAbsolutePath(), lines);

		/*
		 * Read the folders in the file
		 */
		if (lines >= 1) {
			CSVFileReader reader = new CSVFileReader(folderCSV.getAbsolutePath());
			List<String> fields = reader.readFields();
			long i = 0;
			while (fields != null) {
				try {
					folderIds.add(fields.get(0));
				} catch (Throwable e) {
					log.error("Line {} error: {}", i, e.getMessage());
				}
				fields = reader.readFields();
				i++;
			}
			reader.close();
		}

		/*
		 * Complete the CSV file
		 */
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		if (lines < totalFolders) {
			Writer folderWriter = new BufferedWriter(new FileWriter(folderCSV, true));
			log.info("Initial folder ID: {}", folderId);

			try {
				for (long i = 0; i < (totalFolders - lines); i++, folderId++) {
					String id = Long.toString(folderId);
					String date = df.format(new Date());

					String foldLine = FOLDER_HEADER.replace("ld_id", id);
					foldLine = foldLine.replace("ld_lastmodified", date);
					foldLine = foldLine.replace("ld_deleted", "0");
					foldLine = foldLine.replace("ld_name", String.format("folder-%05d", folderId));

					// Choose a random folder between all the loaded ones and
					// use it as parent
					foldLine = foldLine.replace("ld_parentid", folderIds.get(random.nextInt(folderIds.size())));

					foldLine = foldLine.replace("\"ld_securityref\"", NULL);
					foldLine = foldLine.replace("\"ld_description\"", NULL);
					foldLine = foldLine.replace("ld_type", "0");
					foldLine = foldLine.replace("ld_creation", date);
					foldLine = foldLine.replace("ld_creatorid", "1");
					foldLine = foldLine.replace("ld_creator", "admin");
					foldLine = foldLine.replace("\"ld_templateid\"", NULL);
					foldLine = foldLine.replace("ld_templocked", "0");
					foldLine = foldLine.replace("\"ld_deleteuserid\"", NULL);
					foldLine = foldLine.replace("\"ld_deleteuser\"", NULL);
					foldLine = foldLine.replace("ld_tenantid", "1");
					foldLine = foldLine.replace("ld_recordversion", "0");
					foldLine = foldLine.replace("ld_position", "0");
					foldLine = foldLine.replace("ld_hidden", "0");
					foldLine = foldLine.replace("\"ld_quotadocs\"", NULL);
					foldLine = foldLine.replace("\"ld_quotasize\"", NULL);
					foldLine = foldLine.replace("\"ld_foldref\"", NULL);
					foldLine = foldLine.replace("\"ld_storage\"", NULL);
					foldLine = foldLine.replace("\"ld_maxversions\"", NULL);
					foldLine = foldLine.replace("\"ld_color\"", NULL);
					foldLine = foldLine.replace("\"ld_tgs\"", NULL);
					foldLine = foldLine.replace("\"ld_qthreshold\"", NULL);
					foldLine = foldLine.replace("\"ld_qrecipients\"", NULL);
					foldLine = foldLine.replace("\"ld_level\"", NULL);

					foldLine += "\r\n";
					folderWriter.write(foldLine);
					folderIds.add(id);
				}
			} finally {
				folderWriter.close();
			}
		}

	}

	public void printProgress(long count) {
		double progress = ((double) count / (double) totalDocs) * (double) 100;
		if (progress % 1 == 0) {
			DecimalFormat df = new DecimalFormat("###");
			log.info("Process completed at {}%", df.format(progress));
		}
	}

	public void writeDocumentCSV() throws Exception {
		Date start = new Date();

		prepareTags();

		prepareFolderCSV();

		docId = initialDocId;
		if (!documentCSV.exists())
			documentCSV.createNewFile();
		if (documentCSV.length() == 0L) {
			FileUtil.writeFile(DOCUMENT_HEADER + "\r\n", documentCSV.getAbsolutePath());
		} else {
			ReversedLinesFileReader lr = new ReversedLinesFileReader(documentCSV, Charset.forName("UTF-8"));
			try {
				String lastLine = lr.readLine();
				if (!lastLine.equals(DOCUMENT_HEADER)) {
					String id = lastLine.substring(1, lastLine.indexOf("\","));
					long val = Long.parseLong(id) + 1;
					if (val > docId)
						docId = val;
				}
			} finally {
				lr.close();
			}
		}

		versionId = initialVersionId;
		if (!versionCSV.exists())
			versionCSV.createNewFile();
		if (versionCSV.length() == 0L) {
			FileUtil.writeFile(VERSION_HEADER + "\r\n", versionCSV.getAbsolutePath());
		} else {
			ReversedLinesFileReader lr = new ReversedLinesFileReader(versionCSV, Charset.forName("UTF-8"));
			try {
				String lastLine = lr.readLine();
				if (!lastLine.equals(VERSION_HEADER)) {
					String id = lastLine.substring(1, lastLine.indexOf("\","));
					long val = Long.parseLong(id) + 1;
					if (val > versionId)
						versionId = val;
				}
			} finally {
				lr.close();
			}
		}

		if (!tagCSV.exists())
			tagCSV.createNewFile();
		if (tagCSV.length() == 0L)
			FileUtil.writeFile(TAG_HEADER + "\r\n", tagCSV.getAbsolutePath());

		// Count the actual number of lines in the document file (excluding the
		// header)
		long lines = countLines(documentCSV) - 1;
		log.info("Current documents file {} has {} lines", documentCSV.getAbsolutePath(), lines);
		printProgress(lines);

		if (lines < totalDocs) {
			log.info("Initial document ID: {}", docId);
			log.info("Initial version ID: {}", versionId);

			RandomFile randomFile = new RandomFile(false);
			randomFile.setSourceDir(Config.get().getProperty("csv.sourcedir"));

			Writer documentWriter = new BufferedWriter(new FileWriter(documentCSV, true));
			Writer versionWriter = new BufferedWriter(new FileWriter(versionCSV, true));
			Writer tagWriter = new BufferedWriter(new FileWriter(tagCSV, true));
			try {
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				for (long i = lines; i < totalDocs; i++, docId++) {
					String id = Long.toString(docId);
					String date = df.format(new Date());

					String docLine = DOCUMENT_HEADER.replace("ld_id", id);
					docLine = docLine.replace("ld_lastmodified", date);
					docLine = docLine.replace("ld_deleted", "0");
					docLine = docLine.replace("ld_immutable", "0");
					docLine = docLine.replace("ld_customid", id);
					docLine = docLine.replace("ld_version", "1.0");
					docLine = docLine.replace("ld_fileversion", "1.0");
					docLine = docLine.replace("ld_date", date);
					docLine = docLine.replace("ld_creation", date);
					docLine = docLine.replace("ld_publisherid", "1");
					docLine = docLine.replace("ld_publisher", "admin");
					docLine = docLine.replace("ld_creatorid", "1");
					docLine = docLine.replace("ld_creator", "admin");
					docLine = docLine.replace("ld_status", "0");
					docLine = docLine.replace("\"ld_lockuserid\"", NULL);
					docLine = docLine.replace("\"ld_lockuser\"", NULL);
					docLine = docLine.replace("ld_language", "en");

					SourceFile file = randomFile.getSourceFile();
					docLine = docLine.replace("ld_filename", file.getUniqueFilename());
					docLine = docLine.replace("ld_filesize", Long.toString(file.getFile().length()));
					docLine = docLine.replace("ld_type", FilenameUtils.getExtension(file.getFile().getName()));

					docLine = docLine.replace("ld_indexed", "0");
					docLine = docLine.replace("ld_barcoded", "0");
					docLine = docLine.replace("ld_signed", "0");
					docLine = docLine.replace("\"ld_digest\"", NULL);

					docLine = docLine.replace("ld_folderid", folderIds.get(random.nextInt(folderIds.size())));

					docLine = docLine.replace("\"ld_templateid\"", NULL);
					docLine = docLine.replace("ld_exportstatus", "0");
					docLine = docLine.replace("\"ld_exportid\"", NULL);
					docLine = docLine.replace("\"ld_exportname\"", NULL);
					docLine = docLine.replace("\"ld_exportversion\"", NULL);
					docLine = docLine.replace("\"ld_comment\"", NULL);
					docLine = docLine.replace("\"ld_workflowstatus\"", NULL);
					docLine = docLine.replace("ld_published", "1");
					docLine = docLine.replace("ld_startpublishing", date);
					docLine = docLine.replace("\"ld_stoppublishing\"", NULL);
					docLine = docLine.replace("\"ld_transactionid\"", NULL);

					List<String> tags = getRandomTags();
					docLine = docLine.replace("ld_tgs", "," + tags.stream().collect(Collectors.joining(",")) + ",");

					for (String tag : tags) {
						String tagLine = TAG_HEADER.replace("ld_docid", id);
						tagLine = tagLine.replace("ld_tenantid", "1");
						tagLine = tagLine.replace("ld_tag", tag);
						tagLine += "\r\n";
						tagWriter.write(tagLine);
					}

					docLine = docLine.replace("\"ld_extresid\"", NULL);
					docLine = docLine.replace("ld_tenantid", "1");
					docLine = docLine.replace("ld_recordversion", "1");
					docLine = docLine.replace("ld_pages", "1");
					docLine = docLine.replace("ld_stamped", "0");
					docLine = docLine.replace("ld_nature", "0");
					docLine = docLine.replace("\"ld_formid\"", NULL);
					docLine = docLine.replace("\"ld_password\"", NULL);
					docLine = docLine.replace("\"ld_workflowstatusdisp\"", NULL);

					docLine += "\r\n";
					documentWriter.write(docLine);

					int firstComma = docLine.indexOf(',');
					String versionLine = "\"" + id + "\",\"" + (versionId++) + "\"" + docLine.substring(firstComma);
					versionWriter.write(versionLine);
					printProgress(i);
				}
			} finally {
				documentWriter.close();
				versionWriter.close();
				tagWriter.close();
			}
		}

		Date end = new Date();
		log.info("Process completed in {}", TimeDiff.printDuration(start, end));

		saveLoadFile();

	}

	private void saveLoadFile() throws IOException {
		String template = Config.readConfigRile("load.sql");
		template = template.replaceAll("DOCUMENT_CSV", documentCSV.getAbsolutePath().replaceAll("\\\\", "/"));
		template = template.replaceAll("DOCUMENT_COLUMNS", DOCUMENT_HEADER.replaceAll("\"", ""));
		template = template.replaceAll("DOCUMENT_NEXTID", Long.toString(docId + 1));

		template = template.replaceAll("TAG_CSV", tagCSV.getAbsolutePath().replaceAll("\\\\", "/"));
		template = template.replaceAll("TAG_COLUMNS", TAG_HEADER.replaceAll("\"", ""));

		template = template.replaceAll("FOLDER_CSV", folderCSV.getAbsolutePath().replaceAll("\\\\", "/"));
		template = template.replaceAll("FOLDER_COLUMNS", FOLDER_HEADER.replaceAll("\"", ""));
		template = template.replaceAll("FOLDER_NEXTID", Long.toString(folderId + 1));

		template = template.replaceAll("VERSION_CSV", versionCSV.getAbsolutePath().replaceAll("\\\\", "/"));
		template = template.replaceAll("VERSION_COLUMNS", VERSION_HEADER.replaceAll("\"", ""));
		template = template.replaceAll("VERSION_NEXTID", Long.toString(versionId + 1));

		File loadFile = new File(documentCSV.getParent(), "load.sql");
		FileUtil.writeFile(template, loadFile.getAbsolutePath());

		log.info("Process completed in {}", loadFile.getAbsolutePath());
	}

	protected List<String> getRandomTags() {
		List<String> tgs = new ArrayList<String>();
		for (int i = 0; i < tagsNumber; i++) {
			tgs.add(chooseTag());
		}
		return tgs;
	}

	protected String chooseTag() {
		int randomIndex = random.nextInt(tags.size());
		return tags.get(randomIndex);
	}

	private void prepareTags() throws IOException {
		log.info("Prepare tags");
		tags.clear();

		String buf = Config.readConfigRile("tags.txt");
		StringTokenizer st = new StringTokenizer(buf, " \\\t\n\r\f\"'.;,()[]:/", false);
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (StringUtils.isNotEmpty(token) && token.length() > tagSize)
				tags.add(token);
		}
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