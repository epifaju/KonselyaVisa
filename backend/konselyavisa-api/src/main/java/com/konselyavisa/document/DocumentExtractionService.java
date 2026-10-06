package com.konselyavisa.document;

import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.api.SaveDocumentExtractionRequest;
import java.util.UUID;

public interface DocumentExtractionService {

    DocumentResponse saveManual(UUID caseId, UUID documentId, SaveDocumentExtractionRequest request);
}
