package com.logicaldoc.bm;

import java.io.File;
import java.util.List;

import javax.activation.DataHandler;

import com.logicaldoc.webservice.model.WSDocument;
import com.logicaldoc.webservice.model.WSFolder;
import com.logicaldoc.webservice.model.WSSearchOptions;
import com.logicaldoc.webservice.model.WSSearchResult;

/**
 * @author Alessandro Gasparini - LogicalDOC
 * @since 7.5
 */
public abstract class AbstractServerProxy {
	
	public String url;
	public String sid;

	public AbstractServerProxy() {
	}
	
	public abstract void logout();

	public abstract String login(String username, String password) throws Exception;

	public abstract List<WSFolder> listChildren(long parentFolder) throws Exception;

	public abstract WSSearchResult find(WSSearchOptions options) throws Exception;

	public abstract List<WSDocument> list(long folderId) throws Exception;

	public abstract void update(WSDocument doc) throws Exception;

	public abstract WSDocument create(WSDocument doc, DataHandler dataHandler) throws Exception;

	public abstract WSDocument create(WSDocument doc, File file) throws Exception;

	public abstract WSFolder create(WSFolder newFolder) throws Exception;

	public abstract WSFolder createPath(Long rootFolder, String currentKey) throws Exception;

	public abstract long create(long parentFolder, String fname) throws Exception;
	

}