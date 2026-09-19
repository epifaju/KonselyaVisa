package com.konselyavisa.catalog.persistence;

import com.konselyavisa.catalog.domain.Country;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, UUID> {

    boolean existsByIsoCodeIgnoreCase(String isoCode);

    Optional<Country> findByIsoCodeIgnoreCase(String isoCode);

    List<Country> findByActiveTrueOrderByIsoCodeAsc();
}
