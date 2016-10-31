package org.igov.service.business.document.access.handler;

import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;
import org.igov.util.VariableMultipartFile;
import org.igov.io.GeneralConfig;
import org.igov.io.web.RestRequest;
import org.igov.io.web.SSLCertificateValidation;

import javax.mail.internet.ContentDisposition;
import javax.mail.internet.ParseException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

import org.igov.model.document.Document;
import org.igov.service.business.action.task.systemtask.doc.util.UkrDocUtil;
import org.igov.service.exception.DocumentNotFoundException;
import org.igov.model.document.DocumentTypeDao;
import org.igov.service.exception.DocumentTypeNotSupportedException;
import org.igov.model.document.access.DocumentAccess;
import org.igov.model.document.access.DocumentAccessDao;
import org.igov.model.subject.SubjectDao;

import static org.apache.commons.lang3.StringUtils.isBlank;
import org.igov.service.business.promin.ProminSession_Singleton;

/**
 * Created by Dmytro Tsapko on 8/22/2015.
 */

public class DocumentAccessHandler_PB extends AbstractDocumentAccessHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentAccessHandler_PB.class);

    @Autowired
    GeneralConfig generalConfig;

    @Autowired
    private DocumentAccessDao documentAccessDao;

    @Autowired
    private SubjectDao subjectDao;

    @Autowired
    private DocumentTypeDao documentTypeDao;
    
    @Autowired
    private ProminSession_Singleton prominSession_Singleton;

    @Override
    public DocumentAccess getAccess() {
        return documentAccessDao.getDocumentAccess(accessCode);
    }

    public Document getDocument() {
        Document doc = new Document();
        String sessionId;
        String keyIdParam;
        String callBackKey = "&callbackUrl=";
        String callBackValue = generalConfig.getURL_DocumentCallback_Receipt_PB_Bank();
        String keyID = this.accessCode;
        Collection<Long> correctDocTypes = Lists.newArrayList(0L, 1L);
        String uriDoc;

        if (this.documentTypeId == null || !correctDocTypes.contains(this.documentTypeId)) {
            LOG.error("DocumentTypeId = {}",  this.documentTypeId);
            throw new DocumentTypeNotSupportedException(
                    "Incorrect DocumentTypeId. DocumentTypeId = " + this.documentTypeId);
        } else {
            uriDoc = Long.valueOf(0L).equals(this.documentTypeId) ?
                    generalConfig.getURL_DocumentSimple_Receipt_PB_Bank() : generalConfig.getURL_DocumentByAccounts_Receipt_PB_Bank();

            keyIdParam = Long.valueOf(0L).equals(this.documentTypeId) ? "?keyID=" : "?id=";
        }

        String finalUri = uriDoc + keyIdParam + keyID + callBackKey + callBackValue;

        //if (generalConfig.isSelfTest()) {
            SSLCertificateValidation.disable();
        //}

        try {
            sessionId = prominSession_Singleton.getSid_Auth_Receipt_PB_Bank();
            String authHeader = "sid:" + sessionId;
            byte[] authHeaderBytes = Base64.encode(authHeader.getBytes(StandardCharsets.UTF_8));
            String authHeaderEncoded = new String(authHeaderBytes);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.ALL));
            headers.set("Authorization", "Basic " + authHeaderEncoded);
            LOG.debug("try to final url: {}", finalUri);
            ResponseEntity<byte[]> documentEntity = new RestRequest().getEntity(finalUri,
                    null, StandardCharsets.UTF_8, byte[].class, headers);

            String contentType = documentEntity.getHeaders().getContentType().toString();
            String contentDispositionHeader = documentEntity.getHeaders().get("Content-Disposition").get(0);
            ContentDisposition header = new ContentDisposition(contentDispositionHeader);
            String documentName = header.getParameter("name");

            if (isBlank(documentName)) {
                documentName = header.getParameter("filename");
            }

            if (this.withContent) {
                doc.setFileBody(getFileFromRespEntity(documentEntity));
            }

            doc.setDocumentType(documentTypeDao.findByIdExpected(0L));
            doc.setSubject(subjectDao.getSubject(this.nID_Subject));
            doc.setFile(documentName);
            doc.setContentType(contentType);
            doc.setDate_Upload(DateTime.now());
            doc.setsID_subject_Upload(null);
            doc.setContentKey(null);
            doc.setoSignData(null);

        } catch (ParseException | ResourceAccessException e) {
            LOG.error("Can't get document: ", e);
            throw new DocumentNotFoundException("Can't get document: ", e);
        }

        return doc;
    }

    private MultipartFile getFileFromRespEntity(ResponseEntity<byte[]> documentEntity) throws ParseException {
        String contentType = documentEntity.getHeaders().getContentType().toString();
        String contentDispositionHeader = documentEntity.getHeaders().get("Content-Disposition").get(0);
        ContentDisposition header = new ContentDisposition(contentDispositionHeader);
        String documentName = header.getParameter("name");
        if (isBlank(documentName)) {
            documentName = header.getParameter("filename");
        }
        String[] parts = contentType.split("/");
        String fileExtension = parts.length < 2 ? "" : parts[1];

        return new VariableMultipartFile(new ByteArrayInputStream(documentEntity.getBody()),
                documentName, documentName, contentType + ";" + fileExtension);

    }
}


