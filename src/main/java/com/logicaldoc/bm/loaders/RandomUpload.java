package com.logicaldoc.bm.loaders;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logicaldoc.bm.AbstractLoader;
import com.logicaldoc.bm.AbstractServerProxy;
import com.logicaldoc.bm.Config;
import com.logicaldoc.bm.RandomFile;
import com.logicaldoc.bm.SourceFile;
import com.logicaldoc.util.cache.EhCache;
import com.logicaldoc.util.config.ContextProperties;
import com.logicaldoc.webservice.model.WSDocument;
import com.logicaldoc.webservice.model.WSFolder;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

/**
 * Loader thread that puts documents to the remote repository.
 * 
 * @author Alessandro Gasparini - LogicalDOC
 * @since 6.5
 */
public class RandomUpload extends AbstractLoader {

	private static Logger log = LoggerFactory.getLogger(RandomUpload.class);

	private static EhCache<String, Long> foldersCache;

	private long rootFolder = 4;

	private RandomFile randomFile;

	protected long folders;

	private static List<String> tags = new ArrayList<String>();

	private int tagSize = 4;

	private int tagsNumber = 4;

	private void initCache() {
		if (foldersCache == null) {
			System.setProperty(CacheManager.ENABLE_SHUTDOWN_HOOK_PROPERTY, "TRUE");
			try {
				String cacheDef = System.getProperty("bm.root") + "/conf/loader-cache.xml";
				log.info("Loading caches defined in {}", cacheDef);
				CacheManager cacheManager = CacheManager.create(new FileInputStream(cacheDef));
				Cache cache = cacheManager.getCache("RandomUploadCache");
				foldersCache = new EhCache<String, Long>(cache);
				log.info("Loaded cache with {} elements", foldersCache.getSize());
			} catch (Throwable e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	public RandomUpload() {
		super(RandomUpload.class.getName().substring(RandomUpload.class.getName().lastIndexOf('.') + 1));

		ContextProperties config = Config.get();
		rootFolder = Long.parseLong(config.getProperty("RandomUpload.rootFolder"));
		randomFile = new RandomFile("true".equals(config.getProperty("RandomUpload.loadinmemory")));
		randomFile.setSourceDir(config.getProperty("RandomUpload.sourcedir"));
		folders = config.getInt("RandomUpload.folders");
		tagSize = config.getInt("RandomUpload.tagsize");
		tagsNumber = config.getInt("RandomUpload.tags");
	}

	@Override
	protected String doLoading(AbstractServerProxy serverProxy) throws Exception {
		synchronized (tags) {
			if (tags.isEmpty()) {
				// log.debug("tags.isEmpty()");
				try {
					prepareTags();
				} catch (Exception e) {
					e.printStackTrace();
					log.error("exception preparing tags", e);
					throw e;
				} catch (Throwable tw) {
					log.error("exception preparing tags", tw);
					throw tw;
				}
				// log.debug("Prepared {} tags", tags.size());
			}
		}

		initCache();

		// Collect the folders
		if (foldersCache != null && foldersCache.getSize() < folders)
			collectFolders(serverProxy, rootFolder, "/");

		// Pick a random folder
		List<String> keysAsArray = new ArrayList<String>(foldersCache.getKeys());
		Random r = new Random();
		long folderId = foldersCache.get(keysAsArray.get(r.nextInt((int) foldersCache.getSize())));

		// Pick a random file
		SourceFile sourceFile = randomFile.getSourceFile();

		Long docId = createDocument(serverProxy, folderId, sourceFile);		
		if (docId == null) {
			throw new Exception("Error creating document: " + sourceFile.getFile().getName());
		} else {
			log.debug("created document {} in folder {}", docId, folderId);
		}

		return null;
	}

	private Long createDocument(AbstractServerProxy serverProxy, long folderId, SourceFile sfile) {

		String fileName = sfile.getUniqueFilename();

		WSDocument doc = new WSDocument();
		doc.setFolderId(folderId);
		doc.setFileName(fileName);
		doc.setLanguage(session.getLanguage());

		/*
		 * Add the tags
		 */
		if (doc.getTags() == null || doc.getTags().size() < tagsNumber) {
			Set<String> tgs = new HashSet<String>();
			tgs.addAll(doc.getTags());
			while (tgs.size() < tagsNumber) {
				tgs.add(chooseTag());
			}
			List<String> tlist = new ArrayList<String>();
			tlist.addAll(tgs);
			doc.setTags(tlist);
		}

		try {
			if (sfile.getContent() != null) {
				doc = serverProxy.create(doc, new DataHandler(
						new ByteArrayDataSource(sfile.getContent().getBytes(), "application/octet-stream")));
			} else {
				doc = serverProxy.create(doc, sfile.getFile());
			}

			if (doc != null) {
				log.debug("Created document {} fn: {}", doc, fileName);
			}
		} catch (Throwable ex) {
			log.error(ex.getMessage(), ex);
		}
		if (doc == null)
			return null;
		else
			return doc.getId();
	}

	/**
	 * Collects a number of folders from the remote system
	 */
	protected void collectFolders(AbstractServerProxy serverProxy, long folderId, String folderPath) throws Exception {
		initCache();

		if (foldersCache.getSize() >= folders) {
			log.info("Folders cache already reached {} entries", foldersCache.getSize());
			return;
		}

		if (!foldersCache.contains(folderPath))
			foldersCache.put(folderPath, folderId);

		log.debug("Retrieving folders");
		List<WSFolder> flds = serverProxy.listChildren(folderId);
		if (flds != null)
			for (WSFolder f : flds) {
				if (foldersCache.getSize() >= folders) {
					log.info("Fodlers cache already reached {} entries", foldersCache.getSize());
					return;
				}

				String fPath = folderPath + "/" + f.getName();
				long fId = f.getId();

				if (!foldersCache.contains(fPath))
					foldersCache.put(fPath, fId);
				collectFolders(serverProxy, fId, fPath);
			}
	}

	protected String chooseTag() {
		int randomIndex = random.nextInt(tags.size());
		return tags.get(randomIndex);
	}

	private void prepareTags() throws IOException {
		tags.clear();

		String buf = Config.readConfigRile("tags.txt");
		StringTokenizer st = new StringTokenizer(buf, " \\\t\n\r\f\"'.;,()[]:/", false);
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (StringUtils.isNotEmpty(token) && token.length() > tagSize)
				tags.add(token);
		}
	}
}