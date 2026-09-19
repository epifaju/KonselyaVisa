package com.konselyavisa.catalog.service;

import com.konselyavisa.catalog.api.CountryMapper;
import com.konselyavisa.catalog.api.CountryResponse;
import com.konselyavisa.catalog.api.CreateCountryRequest;
import com.konselyavisa.catalog.api.UpdateCountryRequest;
import com.konselyavisa.catalog.domain.Country;
import com.konselyavisa.catalog.persistence.CountryRepository;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.common.i18n.LocalizedText;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CountryService {

    private final CountryRepository countryRepository;
    private final CountryMapper countryMapper;

    public CountryService(CountryRepository countryRepository, CountryMapper countryMapper) {
        this.countryRepository = countryRepository;
        this.countryMapper = countryMapper;
    }

    @Transactional(readOnly = true)
    public List<CountryResponse> list(boolean includeInactive) {
        List<Country> countries =
                includeInactive ? countryRepository.findAll() : countryRepository.findByActiveTrueOrderByIsoCodeAsc();
        return countries.stream().map(countryMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CountryResponse getById(UUID id) {
        return countryMapper.toResponse(getCountry(id));
    }

    @Transactional
    public CountryResponse create(CreateCountryRequest request) {
        LocalizedText.requireDefaultLocale(request.nameI18n(), "error.catalog.i18n_required");
        String iso = request.isoCode().toUpperCase();
        if (countryRepository.existsByIsoCodeIgnoreCase(iso)) {
            throw BusinessException.conflict("error.catalog.country_code_taken");
        }
        Country country = new Country();
        country.setIsoCode(iso);
        country.setNameI18n(new HashMap<>(request.nameI18n()));
        country.setActive(request.active() == null || request.active());
        return countryMapper.toResponse(countryRepository.save(country));
    }

    @Transactional
    public CountryResponse update(UUID id, UpdateCountryRequest request) {
        Country country = getCountry(id);
        if (request.nameI18n() != null) {
            LocalizedText.requireDefaultLocale(request.nameI18n(), "error.catalog.i18n_required");
            country.setNameI18n(new HashMap<>(request.nameI18n()));
        }
        if (request.active() != null) {
            country.setActive(request.active());
        }
        return countryMapper.toResponse(country);
    }

    Country getCountry(UUID id) {
        return countryRepository.findById(id).orElseThrow(() -> BusinessException.notFound("error.catalog.country_not_found"));
    }
}
