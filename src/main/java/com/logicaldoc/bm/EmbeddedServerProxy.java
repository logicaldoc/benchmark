package com.logicaldoc.bm;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.activation.DataHandler;

import com.logicaldoc.core.document.Document;
import com.logicaldoc.core.document.DocumentEvent;
import com.logicaldoc.core.document.DocumentHistory;
import com.logicaldoc.core.document.DocumentManager;
import com.logicaldoc.core.security.Tenant;
import com.logicaldoc.util.Context;
import com.logicaldoc.webservice.model.WSDocument;
import com.logicaldoc.webservice.model.WSFolder;
import com.logicaldoc.webservice.model.WSSearchOptions;
import com.logicaldoc.webservice.model.WSSearchResult;
import com.logicaldoc.webservice.model.WSUtil;
import com.logicaldoc.webservice.soap.endpoint.SoapDocumentService;
import com.logicaldoc.webservice.soap.endpoint.SoapFolderService;
import com.logicaldoc.webservice.soap.endpoint.SoapSearchService;

/**
 * This proxy uses the LogicalDOC API itself and is designed to execute inside
 * the main application as Plugin.
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.2
 */
public class EmbeddedServerProxy extends AbstractServerProxy {

	private SoapFolderService folderService = new SoapFolderService();

	private SoapSearchService searchService = new SoapSearchService();

	private SoapDocumentService documentService = new SoapDocumentService();

	public EmbeddedServerProxy(String url) throws IOException {
		folderService.setValidateSession(false);
		searchService.setValidateSession(false);
		documentService.setValidateSession(false);
	}

	public void logout() {

	}

	public String login(String username, String password) throws Exception {
		return "pseudosid";
	}

	public List<WSFolder> listChildren(long parentFolder) throws Exception {
		return folderService.listChildren(sid, parentFolder);
	}

	@Override
	public WSSearchResult find(WSSearchOptions opt) throws Exception {
		return searchService.find(sid, opt);
	}

	@Override
	public List<WSDocument> list(long folderId) throws Exception {
		return documentService.listDocuments(sid, folderId, null);
	}

	@Override
	public void update(WSDocument doc) throws Exception {
		documentService.update(sid, doc);
	}

	@Override
	public WSDocument create(WSDocument doc, DataHandler dataHandler) throws Exception {
		return documentService.create(sid, doc, dataHandler);
	}

	@Override
	public WSDocument create(WSDocument doc, File file) throws Exception {
		// Create the document history event
		DocumentHistory transaction = new DocumentHistory();
		transaction.setSessionId(sid);
		transaction.setEvent(DocumentEvent.STORED.toString());
		transaction.setComment(doc.getComment());
		transaction.setUserId(1L);
		transaction.setUsername("admin");
		transaction.setTenantId(Tenant.DEFAULT_ID);
		
		Document document = WSUtil.toDocument(doc);
		document.setTenantId(Tenant.DEFAULT_ID);
		DocumentManager documentManager = (DocumentManager) Context.get().getBean(DocumentManager.class);
		document = documentManager.create(file, document, transaction);
		return WSUtil.toWSDocument(document);
	}

	@Override
	public WSFolder create(WSFolder newFolder) throws Exception {
		return folderService.create(sid, newFolder);
	}

	@Override
	public WSFolder createPath(Long rootFolder, String currentKey) throws Exception {
		return folderService.createPath(sid, rootFolder, currentKey);
	}

	@Override
	public long create(long parentFolder, String fname) throws Exception {
		return folderService.createFolder(sid, parentFolder, fname);
	}
}
