package com.konselyavisa.privacy.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.privacy.PrivacyNoticeCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/privacy-notices")
public class PrivacyNoticeController {

    @GetMapping("/current")
    public ApiResponse<PrivacyNoticeResponse> current() {
        return ApiResponse.ok(
                new PrivacyNoticeResponse(PrivacyNoticeCatalog.CURRENT_VERSION, PrivacyNoticeCatalog.texts()));
    }
}
