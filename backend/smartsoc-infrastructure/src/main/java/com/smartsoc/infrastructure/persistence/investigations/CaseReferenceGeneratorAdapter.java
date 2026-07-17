package com.smartsoc.infrastructure.persistence.investigations;

import com.smartsoc.domain.investigations.CaseReferenceGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Génère {@code CASE-YYYY-NNNN} à partir d'une séquence PostgreSQL : unicité
 * et monotonie garanties par la base même sous concurrence (pas de course
 * possible, contrairement à un max()+1 applicatif).
 */
@Component
@RequiredArgsConstructor
public class CaseReferenceGeneratorAdapter implements CaseReferenceGenerator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String nextReference() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('case_reference_seq')", Long.class);
        return "CASE-%d-%04d".formatted(Year.now().getValue(), next);
    }
}
