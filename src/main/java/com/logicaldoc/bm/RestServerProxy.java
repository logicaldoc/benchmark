package com.logicaldoc.bm;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.activation.DataHandler;

import com.logicaldoc.webservice.model.WSDocument;
import com.logicaldoc.webservice.model.WSFolder;
import com.logicaldoc.webservice.model.WSSearchOptions;
import com.logicaldoc.webservice.model.WSSearchResult;
import com.logicaldoc.webservice.rest.client.RestAuthClient;
import com.logicaldoc.webservice.rest.client.RestDocumentClient;
import com.logicaldoc.webservice.rest.client.RestFolderClient;
import com.logicaldoc.webservice.rest.client.RestSearchClient;

/**
 * Helper class to store remote service connections.
 * 
 * @author Alessandro Gasparini - LogicalDOC
 * @since 7.5
 */
public class RestServerProxy extends AbstractServerProxy {

	public RestAuthClient authClient;

	public RestDocumentClient documentClient;

	public RestFolderClient folderClient;

	public RestSearchClient searchClient;

	public RestServerProxy(String url) throws IOException {
		this.url = url;

		String username = Config.get().getProperty("session.username");
		String pasword = Config.get().getProperty("session.password");

		this.authClient = new RestAuthClient(url + "/services/rest/auth");
		this.folderClient = new RestFolderClient(url + "/services/rest/folder", username, pasword, 40);
		this.documentClient = new RestDocumentClient(url + "/services/rest/document", username, pasword, 40);
		this.searchClient = new RestSearchClient(url + "/services/rest/search", username, pasword, 40);
	}

	public void logout() {
		System.out.println("logout sid: " + sid);
		authClient.logout(sid);
	}

	public String login(String username, String password) throws Exception {
		sid = authClient.login(username, password);
		return sid;
	}

	public List<WSFolder> listChildren(long parentFolder) throws Exception {
		return folderClient.listChildren(parentFolder);
	}

	@Override
	public WSSearchResult find(WSSearchOptions options) throws Exception {
		return searchClient.find(options);
	}

	@Override
	public List<WSDocument> list(long folderId) throws Exception {
		return documentClient.listDocuments(folderId, null);
	}

	@Override
	public void update(WSDocument doc) throws Exception {
		documentClient.update(doc);
	}

	@Override
	public WSDocument create(WSDocument doc, DataHandler dataHandler) throws Exception {
		return documentClient.create(doc, dataHandler);
	}

	@Override
	public WSDocument create(WSDocument doc, File file) throws Exception {
		return documentClient.create(doc, file);
	}

	@Override
	public WSFolder create(WSFolder newFolder) throws Exception {
		return folderClient.create(newFolder);
	}

	@Override
	public WSFolder createPath(Long rootFolder, String path) throws Exception {
		return folderClient.createPath(rootFolder, path);
	}

	@Override
	public long create(long parentFolder, String fname) throws Exception {
		return folderClient.createFolder(parentFolder, fname);
	}
}