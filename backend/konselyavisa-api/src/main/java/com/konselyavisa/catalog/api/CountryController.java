package com.konselyavisa.catalog.api;

import com.konselyavisa.catalog.service.CountryService;
import com.konselyavisa.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/countries")
public class CountryController {

    private final CountryService countryService;

    public CountryController(CountryService countryService) {
        this.countryService = countryService;
    }

    @GetMapping
    public ApiResponse<List<CountryResponse>> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(countryService.list(includeInactive));
    }

    @GetMapping("/{id}")
    public ApiResponse<CountryResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(countryService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<CountryResponse> create(@Valid @RequestBody CreateCountryRequest request) {
        return ApiResponse.ok(countryService.create(request), "catalog.country.created");
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BUSINESS_ADMIN')")
    public ApiResponse<CountryResponse> update(@PathVariable UUID id, @RequestBody UpdateCountryRequest request) {
        return ApiResponse.ok(countryService.update(id, request), "catalog.country.updated");
    }
}
