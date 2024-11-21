package com.logicaldoc.bm.loaders;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * Loader thread that puts documents into a random selection of existing
 * folders.
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.1.2
 */
public class Upload extends AbstractLoader {

	private static Logger log = LoggerFactory.getLogger(Upload.class);

	private static EhCache<String, Long> pathCache;

	private long rootFolder = 4;

	private RandomFile randomFile;

	private int[] folderProfiles;

	protected long depth;

	private static List<String> tags = new ArrayList<String>();

	private int tagSize = 4;

	private int tagsNumber = 4;

	private void initChache() {
		if (pathCache == null) {
			System.setProperty(CacheManager.ENABLE_SHUTDOWN_HOOK_PROPERTY, "TRUE");
			try {
				CacheManager cacheManager = CacheManager
						.create(new FileInputStream(System.getProperty("bm.root") + "/conf/loader-cache.xml"));
				Cache cache = cacheManager.getCache("PathCache");
				pathCache = new EhCache<String, Long>(cache);
			} catch (Throwable e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	public Upload() {
		super(Upload.class.getName().substring(Upload.class.getName().lastIndexOf('.') + 1));

		ContextProperties config = Config.get();
		rootFolder = Long.parseLong(config.getProperty("Upload.rootFolder"));
		randomFile = new RandomFile("true".equals(config.getProperty("Upload.loadinmemory")));
		randomFile.setSourceDir(config.getProperty("Upload.sourcedir"));
		depth = config.getInt("Upload.depth");
		tagSize = config.getInt("Upload.tagsize");
		tagsNumber = config.getInt("Upload.tags");

		StringTokenizer tokenizer = new StringTokenizer(config.getProperty("Upload.folderprofile"), ",", false);
		ArrayList<Integer> folderProfilesList = new ArrayList<Integer>(5);
		while (tokenizer.hasMoreTokens()) {
			String folderProfileStr = tokenizer.nextToken().trim();
			Integer folderProfile = Integer.valueOf(folderProfileStr);
			folderProfilesList.add(folderProfile);
		}
		folderProfiles = new int[folderProfilesList.size()];
		for (int i = 0; i < folderProfiles.length; i++) {
			folderProfiles[i] = folderProfilesList.get(i);
		}
		if (folderProfiles.length == 0 || folderProfiles[0] != 1) {
			throw new RuntimeException("'Upload.folderprofile' must always start with '1', "
					+ "which represents the root of the hierarchy, and have at least one other value.  "
					+ "E.g. '1, 3'");
		}

		log.info("folderProfilesStr.length(): {}", folderProfilesList.size());
	}

	@Override
	protected String doLoading(AbstractServerProxy serverProxy) throws Exception {
		// log.debug("Upload.doLoading()");
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
		// Get a random folder
		List<String> folderPath = chooseFolderPath();

		// Make sure the folder exists
		log.debug("Creating folders");
		Long folderID = makeFolders(serverProxy.sid, serverProxy, rootFolder, folderPath);
		// Long folderID = makeFoldersFromPath(serverProxy.sid, serverProxy,
		// rootFolder, folderPath);

		SourceFile sourceFile = randomFile.getSourceFile();
		String title = formatter.format(loaderCount);

		Long docId = createDocument(serverProxy, folderID, title, sourceFile);
		if (docId == null) {
			throw new Exception("Error creating document: " + sourceFile.getFile().getName());
		}

		return null;
	}

	private Long createDocument(AbstractServerProxy serverProxy, long folderId, String title, SourceFile sfile) {

		String fileName = sfile.getFile().getName();

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
				log.debug("Created document {}", fileName);
			}
		} catch (Throwable ex) {
			log.error(ex.getMessage(), ex);
		}
		if (doc == null)
			return null;
		else
			return doc.getId();
	}

	protected List<String> chooseFolderPath() {
		// We work through these until we get the required depth.
		// The root node is ignored as it acts as the search root
		List<String> path = new ArrayList<String>((int) depth);
		for (int i = 1; i < depth; i++) {
			int folderProfile = folderProfiles[i];
			int randomFolderId = random.nextInt(folderProfile);
			String name = String.format("folder-%05d", randomFolderId);
			path.add(name);
		}
		return path;
	}

	/**
	 * Creates or find the folders based on caching.
	 */
	protected Long makeFolders(String ticket, AbstractServerProxy serverProxy, Long rootFolder, List<String> folderPath)
			throws Exception {
		initChache();
		
		// Iterate down the path, checking the cache and populating it as
		// necessary
		Long currentParentFolderID = rootFolder;
		String currentKey = "";

		for (String aFolderPath : folderPath) {
			currentKey += ("/" + aFolderPath);
			// Is this there?
			Long folderID = pathCache.get(currentKey);
			if (folderID != null) {
				// Found it
				currentParentFolderID = folderID;
				// Step into the next level
				continue;
			}

			// It is not there, so create it
			try {
				currentParentFolderID = serverProxy.create(currentParentFolderID, aFolderPath);
			} catch (Exception e) {
				currentParentFolderID = pathCache.get(currentKey);
			}

			// Cache the new node
			pathCache.put(currentKey, currentParentFolderID);
			// System.out.printf("putting in cache: %s, %d %n", currentKey,
			// currentParentFolderID);
		}
		// Done
		return currentParentFolderID;
	}

	/**
	 * Creates or find the folders based on caching.
	 */
	protected Long makeFoldersFromPath(String ticket, AbstractServerProxy serverProxy, Long rootFolder,
			List<String> folderPath) throws Exception {
		initChache();
		
		// Iterate down the path, checking the cache and populating it as
		// necessary
		String currentKey = "";
		for (String aFolderPath : folderPath) {
			currentKey += ("/" + aFolderPath);
		}
		// System.out.println("currentKey: " +currentKey);

		Long folderID = pathCache.get(currentKey);

		// It is not there, so create it
		if (folderID == null) {
			WSFolder folder = serverProxy.createPath(rootFolder, currentKey);

			folderID = folder.getId();
			// Cache the new node
			pathCache.put(currentKey, folderID);
			// System.out.println("created path: " +currentKey);
		}

		return folderID;
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