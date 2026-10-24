package com.konselyavisa.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.catalog.api.CountryMapper;
import com.konselyavisa.catalog.api.CountryResponse;
import com.konselyavisa.catalog.api.CreateCountryRequest;
import com.konselyavisa.catalog.api.UpdateCountryRequest;
import com.konselyavisa.catalog.domain.Country;
import com.konselyavisa.catalog.persistence.CountryRepository;
import com.konselyavisa.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

    @Mock
    private CountryRepository countryRepository;

    @Mock
    private CountryMapper countryMapper;

    private CountryService service;

    @BeforeEach
    void setUp() {
        service = new CountryService(countryRepository, countryMapper);
    }

    @Test
    void listActiveOnlyUsesActiveQuery() {
        Country country = new Country();
        CountryResponse mapped = org.mockito.Mockito.mock(CountryResponse.class);
        when(countryRepository.findByActiveTrueOrderByIsoCodeAsc()).thenReturn(List.of(country));
        when(countryMapper.toResponse(country)).thenReturn(mapped);

        assertThat(service.list(false)).containsExactly(mapped);
        verify(countryRepository, never()).findAll();
    }

    @Test
    void listIncludeInactiveUsesFindAll() {
        when(countryRepository.findAll()).thenReturn(List.of());

        assertThat(service.list(true)).isEmpty();
        verify(countryRepository).findAll();
    }

    @Test
    void createRejectsDuplicateIsoCode() {
        when(countryRepository.existsByIsoCodeIgnoreCase("SN")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateCountryRequest("sn", Map.of("fr", "Sénégal"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.country_code_taken");
        verify(countryRepository, never()).save(any());
    }

    @Test
    void createUppercasesIsoAndDefaultsActiveTrue() {
        when(countryRepository.existsByIsoCodeIgnoreCase("SN")).thenReturn(false);
        when(countryRepository.save(any(Country.class))).thenAnswer(inv -> inv.getArgument(0));
        CountryResponse mapped = org.mockito.Mockito.mock(CountryResponse.class);
        when(countryMapper.toResponse(any(Country.class))).thenReturn(mapped);

        assertThat(service.create(new CreateCountryRequest("sn", Map.of("fr", "Sénégal"), null))).isSameAs(mapped);
        ArgumentCaptor<Country> captor = ArgumentCaptor.forClass(Country.class);
        verify(countryRepository).save(captor.capture());
        assertThat(captor.getValue().getIsoCode()).isEqualTo("SN");
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getNameI18n()).containsEntry("fr", "Sénégal");
    }

    @Test
    void getByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(countryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.country_not_found");
    }

    @Test
    void updateSetsNameAndActiveWhenProvided() {
        UUID id = UUID.randomUUID();
        Country country = new Country();
        country.setId(id);
        country.setIsoCode("SN");
        country.setActive(true);
        country.setNameI18n(Map.of("fr", "Old"));
        when(countryRepository.findById(id)).thenReturn(Optional.of(country));
        CountryResponse mapped = org.mockito.Mockito.mock(CountryResponse.class);
        when(countryMapper.toResponse(country)).thenReturn(mapped);

        assertThat(service.update(id, new UpdateCountryRequest(Map.of("fr", "Nouveau"), false))).isSameAs(mapped);
        assertThat(country.getNameI18n()).containsEntry("fr", "Nouveau");
        assertThat(country.isActive()).isFalse();
    }
}
