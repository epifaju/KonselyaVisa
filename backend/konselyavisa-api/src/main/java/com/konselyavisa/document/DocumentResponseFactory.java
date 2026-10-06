package com.konselyavisa.document;

import com.konselyavisa.document.api.DocumentMapper;
import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.crypto.ExtractedFieldsCrypto;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.identity.CurrentUser;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DocumentResponseFactory {

    private final DocumentMapper documentMapper;
    private final ExtractedFieldsCrypto extractedFieldsCrypto;

    public DocumentResponseFactory(DocumentMapper documentMapper, ExtractedFieldsCrypto extractedFieldsCrypto) {
        this.documentMapper = documentMapper;
        this.extractedFieldsCrypto = extractedFieldsCrypto;
    }

    public DocumentResponse toResponse(CaseDocument document) {
        Map<String, String> fields = Map.of();
        if (!CurrentUser.isSelfScoped() && !CurrentUser.isCompanyWorkspace()) {
            fields = extractedFieldsCrypto.decrypt(document.getExtractedFieldsCipher());
        }
        return documentMapper.toResponse(document).withExtractedFields(fields);
    }
}
