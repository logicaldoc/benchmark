package com.logicaldoc.bm;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.activation.DataHandler;

import com.logicaldoc.webservice.model.WSDocument;
import com.logicaldoc.webservice.model.WSFolder;
import com.logicaldoc.webservice.model.WSSearchOptions;
import com.logicaldoc.webservice.model.WSSearchResult;
import com.logicaldoc.webservice.soap.client.SoapAuthClient;
import com.logicaldoc.webservice.soap.client.SoapDocumentClient;
import com.logicaldoc.webservice.soap.client.SoapFolderClient;
import com.logicaldoc.webservice.soap.client.SoapSearchClient;

/**
 * Helper class to store remote service connections.
 * 
 * @author Alessandro Gasparini - LogicalDOC
 * @since 7.5
 */
public class SoapServerProxy extends AbstractServerProxy {

	public SoapAuthClient authClient;

	public SoapDocumentClient documentClient;

	public SoapFolderClient folderClient;

	public SoapSearchClient searchClient;

	public SoapServerProxy(String url) throws IOException {

		SoapAuthClient auth = new SoapAuthClient(url + "/services/Auth");
		SoapDocumentClient documentClient = new SoapDocumentClient(url + "/services/Document",
				Config.get().getInt("webservice.gzip"), false, 40);
		SoapFolderClient folderClient = new SoapFolderClient(url + "/services/Folder", Config.get().getInt("webservice.gzip"),
				false, 40);
		SoapSearchClient searchClient = new SoapSearchClient(url + "/services/Search", Config.get().getInt("webservice.gzip"),
				false, 40);

		this.url = url;
		this.authClient = auth;
		this.folderClient = folderClient;
		this.documentClient = documentClient;
		this.searchClient = searchClient;
	}

	public void logout() {
		System.out.println("logout sid: " + sid);
		authClient.logout(sid);
	}

	public String login(String username, String password) throws Exception {
		sid = authClient.login(username, password);
		System.out.println("login sid: " + sid);
		return sid;
	}

	public List<WSFolder> listChildren(long parentFolder) throws Exception {
		// log.debug("listChildren {}, {}", sid, parentFolder);
		return folderClient.listChildren(sid, parentFolder);
	}

	@Override
	public WSSearchResult find(WSSearchOptions options) throws Exception {
		return searchClient.find(sid, options);
	}

	@Override
	public List<WSDocument> list(long folderId) throws Exception {
		return documentClient.listDocuments(sid, folderId, null);
	}

	@Override
	public void update(WSDocument doc) throws Exception {
		documentClient.update(sid, doc);
	}

	@Override
	public WSDocument create(WSDocument doc, DataHandler dataHandler) throws Exception {
		return documentClient.create(sid, doc, dataHandler);
	}

	@Override
	public WSDocument create(WSDocument doc, File file) throws Exception {
		return documentClient.create(sid, doc, file);
	}

	@Override
	public WSFolder create(WSFolder newFolder) throws Exception {
		return folderClient.create(sid, newFolder);
	}

	@Override
	public WSFolder createPath(Long rootFolder, String currentKey) throws Exception {
		return folderClient.createPath(sid, rootFolder, currentKey);
	}

	@Override
	public long create(long parentFolder, String fname) throws Exception {
		return folderClient.createFolder(sid, parentFolder, fname);
	}
}
